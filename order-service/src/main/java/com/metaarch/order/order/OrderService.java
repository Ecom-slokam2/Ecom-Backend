package com.metaarch.order.order;

import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.metaarch.order.event.*;
import com.metaarch.order.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
  private static final String GLOBAL = "global";
  private static final String PICKUP = "pickup";
  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final OrderEventPublisher events;
  private final CatalogDeliveryQuoteClient catalogDeliveryQuoteClient;
  private final GlobalStoreAssignmentCatalogClient assignmentCatalog;
  private final GlobalStoreAssignmentInventoryClient assignmentInventory;
  private final FulfillmentLifecycleVerifier fulfillmentLifecycle;

  public OrderService(OrderRepository orders, OrderItemRepository items, OrderEventPublisher events, CatalogDeliveryQuoteClient catalogDeliveryQuoteClient,
      GlobalStoreAssignmentCatalogClient assignmentCatalog, GlobalStoreAssignmentInventoryClient assignmentInventory,
      FulfillmentLifecycleVerifier fulfillmentLifecycle) {
    this.orders = orders;
    this.items = items;
    this.events = events;
    this.catalogDeliveryQuoteClient = catalogDeliveryQuoteClient;
    this.assignmentCatalog = assignmentCatalog;
    this.assignmentInventory = assignmentInventory;
    this.fulfillmentLifecycle = fulfillmentLifecycle;
  }

  @Transactional
  public OrderResponse create(CreateOrderRequest request) {
    Order order = build(request, null, true);
    Order saved = orders.save(order);
    events.publish(OrderEventType.ORDER_CREATED, saved);
    return OrderResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public OrderResponse preview(CreateOrderRequest request) {
    return OrderResponse.from(build(request, null, true));
  }

  @Transactional(readOnly = true)
  OrderResponse previewWithoutPromotion(CreateOrderRequest request) {
    return OrderResponse.from(build(request, null, false));
  }

  @Transactional
  OrderResponse createWithVerifiedPromotion(CreateOrderRequest request, String couponCode, BigDecimal discount) {
    Order saved=orders.save(build(request,new VerifiedPromotion(couponCode,discount),false));
    events.publish(OrderEventType.ORDER_CREATED,saved);
    return OrderResponse.from(saved);
  }

  CatalogDeliveryQuoteClient.CouponPromotion validateCouponForCheckout(String code,BigDecimal subtotal,String location,String store) {
    return catalogDeliveryQuoteClient.validateCoupon(code,subtotal,location,store);
  }

  private Order build(CreateOrderRequest request, VerifiedPromotion verifiedPromotion, boolean validateCoupon) {
    if (isGlobal(request) && isPickup(request)) throw new IllegalArgumentException("Global orders are delivery only");
    List<CreateOrderAddressRequest> addresses = normalizedAddresses(request);
    if (requiresShippingAddress(request)) ensureShippingAddress(addresses);
    Order order = new Order(request.customerId(), request.cartId(), request.storeId().trim(), nextOrderNumber());
    if (request.paymentStatus() != null && request.paymentStatus() != PaymentStatus.PENDING) throw new IllegalArgumentException("New orders must await payment confirmation");
    Set<String> identities = new HashSet<>();
    request.items().forEach(item -> {
      CreateOrderItemRequest verified = catalogDeliveryQuoteClient.verifyItem(order.getStoreId(), item, isGlobal(request), isPickup(request));
      if (!identities.add(verified.productId() + ":" + verified.variantId())) throw new IllegalArgumentException("Duplicate order item");
      order.addItem(new OrderItem(verified.productId(), verified.variantId(), verified.productName(), verified.quantity(), money(verified.unitPrice())));
    });
    String couponCode=trimToNull(request.couponCode());
    if(verifiedPromotion!=null) {
      order.setCouponCode(trimToNull(verifiedPromotion.code()));
      order.setDiscountAmount(money(verifiedPromotion.discount()));
    } else if(validateCoupon && couponCode!=null) {
      var promotion=catalogDeliveryQuoteClient.validateCoupon(couponCode,itemSubtotal(order),request.promotionLocation(),order.getStoreId());
      order.setCouponCode(promotion.code());
      order.setDiscountAmount(money(promotion.discountAmount()));
    }
    applyVerifiedDeliverySnapshot(order, request, itemSubtotal(order));
    resolveDeliveryZoneLocation(order, request);
    order.setHandlingFee(new BigDecimal("5.00"));
    addresses.forEach(address -> order.addAddress(new OrderAddress(address.addressType(), address.addressLine1().trim(), trimToNull(address.addressLine2()), address.city().trim(), address.state().trim(), address.country().trim(), address.postalCode().trim(), trimToNull(address.recipientName()), trimToNull(address.recipientPhone()))));
    calculate(order);
    return order;
  }

  @Transactional(readOnly = true)
  public OrderResponse get(UUID orderId) {
    return OrderResponse.from(find(orderId));
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> list(String storeId) {
    List<Order> rows = storeId == null || storeId.isBlank()
        ? orders.findAllByOrderByCreatedAtDesc()
        : orders.findByStoreIdOrderByCreatedAtDesc(storeId.trim());
    return rows.stream().map(OrderResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> listStores(java.util.Collection<String> storeIds) {
    if (storeIds == null || storeIds.isEmpty()) return List.of();
    return orders.findByStoreIdInOrderByCreatedAtDesc(storeIds).stream().map(OrderResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> getByCustomer(UUID customerId) {
    return orders.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(OrderResponse::from).toList();
  }

  @Transactional
  public OrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request) {
    Order order = orders.findLockedById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    if (request.status() == OrderStatus.SHIPPED && order.getStatus() == OrderStatus.SHIPPED
        && !fulfillmentLifecycle.shipmentHasStatus(orderId, Set.of("IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED")))
      throw new InvalidOrderStateException("A persisted shipment is required for an order marked shipped");
    if (request.status() == OrderStatus.SHIPPED && order.getStatus() != OrderStatus.SHIPPED && !fulfillmentLifecycle.shipmentIs(orderId, "IN_TRANSIT"))
      throw new InvalidOrderStateException("An in-transit shipment is required before marking an order shipped");
    if (request.status() == OrderStatus.DELIVERED && !fulfillmentLifecycle.shipmentIs(orderId, "DELIVERED"))
      throw new InvalidOrderStateException("A delivered shipment is required before marking an order delivered");
    if (order.getStatus() == request.status()) return OrderResponse.from(order);
    transition(order, request.status());
    events.publish(OrderEventType.ORDER_STATUS_UPDATED, order);
    return OrderResponse.from(order);
  }

  @Transactional
  public OrderResponse assignGlobalStore(UUID orderId, String storeId) {
    Order order = orders.findLockedById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    if (!isGlobalOrder(order)) throw new IllegalArgumentException("Only Global Store orders can be approved in Global Fulfillment");
    if (!"global-store".equalsIgnoreCase(storeId == null ? "" : storeId.trim()))
      throw new IllegalArgumentException("Global orders are fulfilled by the India-wide Global Store and cannot be assigned to a local store.");
    if (order.getGlobalAssignedStoreId() != null || "Approved".equalsIgnoreCase(order.getGlobalApproval()))
      throw new ConflictException("This global order has already been assigned");
    GlobalStoreAssignmentCatalogClient.Store store;
    try { store = assignmentCatalog.store("global-store"); }
    catch (org.springframework.web.client.HttpClientErrorException.NotFound error) { throw new IllegalArgumentException("The India-wide Global Store is not configured"); }
    if (store == null || !store.active() || !"Active".equalsIgnoreCase(store.status()))
      throw new IllegalArgumentException("The India-wide Global Store is inactive");
    if (order.getItems().isEmpty()) throw new IllegalArgumentException("Global order has no items to assign");
    for (OrderItem item : order.getItems()) {
      GlobalStoreAssignmentInventoryClient.Inventory stock;
      try { stock = assignmentInventory.inventory(item.getProductId(), item.getVariantId(), "global-store"); }
      catch (org.springframework.web.client.HttpClientErrorException.NotFound error) { throw new ConflictException("Global Store inventory does not have stock for every order item"); }
      if (stock == null || !store.id().equals(stock.storeId()) || !"ACTIVE".equalsIgnoreCase(stock.status()) || stock.availableQuantity() < item.getQuantity())
        throw new ConflictException("Global Store does not have enough active stock for every order item");
    }
    order.setGlobalAssignedStoreId("global-store");
    order.setGlobalAssignedAt(Instant.now());
    order.setGlobalApproval("Approved");
    order.setStatus(OrderStatus.CONFIRMED);
    events.publish(OrderEventType.ORDER_STATUS_UPDATED, order);
    return OrderResponse.from(order);
  }

  @Transactional(readOnly = true)
  public List<GlobalStoreAssignmentOption> globalStoreAssignmentOptions(UUID orderId) {
    Order order = find(orderId);
    if (!isGlobalOrder(order)) throw new IllegalArgumentException("Only Global Store orders can be assigned to a stocked store");
    List<GlobalStoreAssignmentOption> options = new ArrayList<>();
    GlobalStoreAssignmentCatalogClient.Store store;
    try { store = assignmentCatalog.store("global-store"); }
    catch (org.springframework.web.client.HttpClientErrorException.NotFound error) { return options; }
    if (store == null || !store.active() || !"Active".equalsIgnoreCase(store.status())) return options;
      List<GlobalStoreAssignmentOption.ItemAvailability> availability = new ArrayList<>();
      boolean allAvailable = !order.getItems().isEmpty();
      for (OrderItem item : order.getItems()) {
        GlobalStoreAssignmentInventoryClient.Inventory stock;
        try { stock = assignmentInventory.inventory(item.getProductId(), item.getVariantId(), "global-store"); }
        catch (org.springframework.web.client.HttpClientErrorException.NotFound missing) { stock = null; }
        int quantity = stock == null ? 0 : stock.availableQuantity();
        boolean available = stock != null && "ACTIVE".equalsIgnoreCase(stock.status()) && quantity >= item.getQuantity();
        availability.add(new GlobalStoreAssignmentOption.ItemAvailability(item.getProductName(), item.getQuantity(), quantity, available));
        allAvailable &= available;
      }
      if (allAvailable) options.add(new GlobalStoreAssignmentOption("global-store", availability));
    return options;
  }

  private boolean isGlobalOrder(Order order) {
    return "global-store".equalsIgnoreCase(order.getStoreId());
  }

  @Transactional
  public OrderResponse cancel(UUID orderId) {
    Order order = orders.findLockedById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    if (order.getStatus() == OrderStatus.CANCELLED) return OrderResponse.from(order);
    if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) throw new InvalidOrderStateException("Shipped or delivered orders cannot be cancelled");
    order.setStatus(OrderStatus.CANCELLED);
    events.publish(OrderEventType.ORDER_CANCELLED, order);
    return OrderResponse.from(order);
  }

  @Transactional
  public OrderResponse updatePaymentStatus(UUID orderId, PaymentStatus paymentStatus) {
    throw new InvalidOrderStateException("Payment status requires authenticated verified payment confirmation");
  }

  @Transactional
  public void applyVerifiedPayment(PaymentEvent event) {
    Order order = orders.findLockedById(event.orderId()).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    if (event.paymentId() == null || event.customerId() == null || !event.customerId().equals(order.getCustomerId())
        || event.amount() == null || event.amount().compareTo(order.getTotalAmount()) != 0 || !"INR".equals(event.currency())) {
      throw new InvalidOrderStateException("Payment confirmation does not match order");
    }
    PaymentStatus next = switch (event.eventType()) {
      case PAYMENT_SUCCESS -> PaymentStatus.SUCCESS;
      case PAYMENT_FAILED -> PaymentStatus.FAILED;
      case PAYMENT_REFUNDED -> PaymentStatus.REFUNDED;
      case PAYMENT_INITIATED -> PaymentStatus.PENDING;
    };
    if (!next.name().equals(event.status().name())) throw new InvalidOrderStateException("Payment event status mismatch");
    PaymentStatus current = order.getPaymentStatus();
    if (current == next || (current.isPaid() && next.isPaid())) return;
    if (current == PaymentStatus.REFUNDED || (current.isPaid() && next != PaymentStatus.REFUNDED)
        || (next == PaymentStatus.REFUNDED && !current.isPaid()) || next == PaymentStatus.PENDING) {
      throw new InvalidOrderStateException("Invalid verified payment transition");
    }
    order.setPaymentStatus(next);
    events.publish(OrderEventType.ORDER_STATUS_UPDATED, order);
  }

  @Transactional
  public void applyVerifiedDelivery(FulfillmentEvent event) {
    if (!"SHIPMENT_DELIVERED".equals(event.eventType()) || !"DELIVERED".equals(event.shipmentStatus())
        || !"DELIVERED".equals(event.fulfillmentStatus()) || event.eventId() == null
        || event.orderId() == null || event.fulfillmentId() == null || event.shipmentId() == null
        || event.occurredAt() == null) {
      throw new InvalidOrderStateException("Delivery confirmation is incomplete");
    }
    applyVerifiedShipmentDelivery(new FulfillmentDeliveryClient.DeliveryEvidence(event.orderId(), event.customerId(), event.storeId(), event.fulfillmentId(), event.shipmentId(), event.occurredAt()));
  }
  @Transactional
  public void applyVerifiedShipmentDelivery(FulfillmentDeliveryClient.DeliveryEvidence evidence) {
    if (evidence.orderId() == null || evidence.fulfillmentId() == null || evidence.shipmentId() == null || evidence.deliveredAt() == null)
      throw new InvalidOrderStateException("Delivery confirmation is incomplete");
    Order order = orders.findLockedById(evidence.orderId()).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    if (evidence.customerId() == null || !evidence.customerId().equals(order.getCustomerId()) || evidence.storeId() == null || !evidence.storeId().equals(order.getStoreId()))
      throw new InvalidOrderStateException("Delivery confirmation does not match order");
    if (order.getStatus() == OrderStatus.CANCELLED)
      throw new InvalidOrderStateException("Cancelled orders cannot be marked delivered");
    if (order.getStatus() == OrderStatus.DELIVERED) return;
    // Authenticated fulfillment delivery is authoritative even if intermediate
    // order status updates were missed. Payment status is intentionally untouched.
    order.setStatus(OrderStatus.DELIVERED);
    events.publish(OrderEventType.ORDER_STATUS_UPDATED, order);
  }

  @Transactional(readOnly = true)
  public List<OrderItemResponse> getItems(UUID orderId) {
    if (!orders.existsById(orderId)) throw new ResourceNotFoundException("Order not found: " + orderId);
    return items.findByOrderId(orderId).stream().map(OrderItemResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<OrderHistoryResponse> getHistory(UUID customerId) {
    return orders.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(OrderHistoryResponse::from).toList();
  }

  private Order find(UUID id) {
    return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
  }

  private void transition(Order order, OrderStatus next) {
    OrderStatus current = order.getStatus();
    if (current == next) return;
    if (current == OrderStatus.CANCELLED || current == OrderStatus.DELIVERED) throw new InvalidOrderStateException("Finalized orders cannot change status");
    if (next == OrderStatus.CANCELLED) throw new InvalidOrderStateException("Use the cancel order API to cancel an order");
    if (current == OrderStatus.CREATED && EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING).contains(next)) order.setStatus(next);
    else if (current == OrderStatus.CONFIRMED && EnumSet.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED).contains(next)) order.setStatus(next);
    else if (current == OrderStatus.PROCESSING && next == OrderStatus.SHIPPED) order.setStatus(next);
    else if (current == OrderStatus.SHIPPED && next == OrderStatus.DELIVERED) order.setStatus(next);
    else throw new InvalidOrderStateException("Invalid order status transition from " + current + " to " + next);
  }

  private void calculate(Order order) {
    order.getItems().forEach(OrderItem::recalculateTotal);
    BigDecimal itemsTotal = order.getItems().stream().map(OrderItem::getTotalPrice).reduce(ZERO, BigDecimal::add);
    BigDecimal total=itemsTotal.add(order.getDeliveryCharge() == null ? ZERO : order.getDeliveryCharge()).add(order.getHandlingFee()).subtract(order.getDiscountAmount()==null?ZERO:order.getDiscountAmount());
    if(total.signum()<0) throw new IllegalArgumentException("Promotion discount exceeds the order amount.");
    order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
  }

  private void applyVerifiedDeliverySnapshot(Order order, CreateOrderRequest request, BigDecimal orderSubtotal) {
    order.setFulfillmentType(resolveFulfillmentType(request));
    order.setGlobalApproval(resolveGlobalApproval(request));
    if (isGlobal(request)) {
      applyGlobalDeliverySnapshot(order, request);
      return;
    }
    if (isPickup(request)) {
      applyRequestDeliverySnapshot(order, request);
      order.setDeliveryCharge(ZERO);
      order.setDeliveryZoneId(null);
      return;
    }
    CatalogDeliveryQuoteClient.DeliveryQuote quote = catalogDeliveryQuoteClient.verify(
      order.getStoreId(),
      request.customerLatitude(),
      request.customerLongitude(),
      orderSubtotal,
      request.deliveryMethod());
    order.setDeliveryZoneId(quote.deliveryZoneId());
    order.setCalculatedDistanceKm(scaleOrNull(quote.distanceKm(), 2));
    order.setRouteDistanceMeters(quote.routeDistanceMeters());
    order.setRouteDurationSeconds(quote.routeDurationSeconds());
    order.setRouteDurationMinutes(quote.routeDurationMinutes());
    order.setMatchedZoneLabel(trimToNull(quote.matchedZoneLabel()));
    order.setCustomerLatitude(scaleOrNull(quote.customerLatitude() == null ? request.customerLatitude() : quote.customerLatitude(), 7));
    order.setCustomerLongitude(scaleOrNull(quote.customerLongitude() == null ? request.customerLongitude() : quote.customerLongitude(), 7));
    order.setStoreLatitude(scaleOrNull(quote.storeLatitude() == null ? request.storeLatitude() : quote.storeLatitude(), 7));
    order.setStoreLongitude(scaleOrNull(quote.storeLongitude() == null ? request.storeLongitude() : quote.storeLongitude(), 7));
    order.setDeliveryMethod(trimToNull(quote.selectedDeliveryMethod()));
    order.setDeliveryCharge(quote.deliveryCharge() == null ? ZERO : money(quote.deliveryCharge()));
    order.setEstimatedDeliveryMin(quote.estimatedDeliveryMin());
    order.setEstimatedDeliveryMax(quote.estimatedDeliveryMax());
    order.setFreeDeliveryAbove(scaleOrNull(quote.freeDeliveryAbove(), 2));
    order.setFreeDeliveryApplied(quote.freeDeliveryApplied());
    order.setScheduledDeliveryDate(request.scheduledDeliveryDate());
    order.setScheduledDeliverySlot(trimToNull(request.scheduledDeliverySlot()));
    order.setPickupDetails(trimToNull(request.pickupDetails()));
  }

  private void resolveDeliveryZoneLocation(Order order, CreateOrderRequest request) {
    if (order.getDeliveryZoneId() == null) {
      if (isGlobal(request) || isPickup(request)) return;
      throw new DeliveryQuoteVerificationException("Delivery zone is required for this order.");
    }
    CatalogDeliveryQuoteClient.DeliveryZoneLocation location = catalogDeliveryQuoteClient.resolveLocation(order.getDeliveryZoneId());
    order.setLocationId(location.locationId().trim());
  }

  private boolean hasDeliverySnapshot(CreateOrderRequest request) {
    return request.deliveryCharge() != null
      || request.deliveryZoneId() != null
      || request.calculatedDistanceKm() != null
      || request.routeDistanceMeters() != null
      || request.routeDurationSeconds() != null
      || request.routeDurationMinutes() != null
      || request.matchedZoneLabel() != null
      || request.estimatedDeliveryMin() != null
      || request.estimatedDeliveryMax() != null;
  }

  private void applyRequestDeliverySnapshot(Order order, CreateOrderRequest request) {
    order.setDeliveryZoneId(request.deliveryZoneId());
    order.setCalculatedDistanceKm(scaleOrNull(request.calculatedDistanceKm(), 2));
    order.setRouteDistanceMeters(request.routeDistanceMeters());
    order.setRouteDurationSeconds(request.routeDurationSeconds());
    order.setRouteDurationMinutes(request.routeDurationMinutes());
    order.setMatchedZoneLabel(trimToNull(request.matchedZoneLabel()));
    order.setCustomerLatitude(scaleOrNull(request.customerLatitude(), 7));
    order.setCustomerLongitude(scaleOrNull(request.customerLongitude(), 7));
    order.setStoreLatitude(scaleOrNull(request.storeLatitude(), 7));
    order.setStoreLongitude(scaleOrNull(request.storeLongitude(), 7));
    order.setDeliveryMethod(trimToNull(request.deliveryMethod()));
    order.setDeliveryCharge(request.deliveryCharge() == null ? ZERO : money(request.deliveryCharge()));
    order.setEstimatedDeliveryMin(request.estimatedDeliveryMin());
    order.setEstimatedDeliveryMax(request.estimatedDeliveryMax());
    order.setFreeDeliveryAbove(scaleOrNull(request.freeDeliveryAbove(), 2));
    order.setFreeDeliveryApplied(request.freeDeliveryApplied());
    order.setScheduledDeliveryDate(request.scheduledDeliveryDate());
    order.setScheduledDeliverySlot(trimToNull(request.scheduledDeliverySlot()));
    order.setPickupDetails(trimToNull(request.pickupDetails()));
  }

  private void applyGlobalDeliverySnapshot(Order order, CreateOrderRequest request) {
    order.setDeliveryZoneId(null);
    order.setCalculatedDistanceKm(scaleOrNull(request.calculatedDistanceKm(), 2));
    order.setRouteDistanceMeters(request.routeDistanceMeters());
    order.setRouteDurationSeconds(request.routeDurationSeconds());
    order.setRouteDurationMinutes(request.routeDurationMinutes());
    order.setMatchedZoneLabel(trimToNull(request.matchedZoneLabel()));
    order.setCustomerLatitude(scaleOrNull(request.customerLatitude(), 7));
    order.setCustomerLongitude(scaleOrNull(request.customerLongitude(), 7));
    order.setStoreLatitude(scaleOrNull(request.storeLatitude(), 7));
    order.setStoreLongitude(scaleOrNull(request.storeLongitude(), 7));
    order.setDeliveryMethod(GLOBAL);
    order.setDeliveryCharge(ZERO);
    order.setEstimatedDeliveryMin(request.estimatedDeliveryMin());
    order.setEstimatedDeliveryMax(request.estimatedDeliveryMax());
    order.setFreeDeliveryAbove(scaleOrNull(request.freeDeliveryAbove(), 2));
    order.setFreeDeliveryApplied(false);
    order.setScheduledDeliveryDate(request.scheduledDeliveryDate());
    order.setScheduledDeliverySlot(trimToNull(request.scheduledDeliverySlot()));
    order.setPickupDetails(trimToNull(request.pickupDetails()));
  }

  private BigDecimal itemSubtotal(Order order) {
    order.getItems().forEach(OrderItem::recalculateTotal);
    return order.getItems().stream().map(OrderItem::getTotalPrice).reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal scaleOrNull(BigDecimal value, int scale) {
    return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
  }

  private void ensureShippingAddress(List<CreateOrderAddressRequest> addresses) {
    if (addresses.stream().noneMatch(address -> address.addressType() == AddressType.SHIPPING)) {
      throw new IllegalArgumentException("At least one shipping address is required");
    }
  }

  private List<CreateOrderAddressRequest> normalizedAddresses(CreateOrderRequest request) {
    List<CreateOrderAddressRequest> addresses = new ArrayList<>();
    if (request.addresses() != null) addresses.addAll(request.addresses());
    if (request.shippingAddress() != null) addresses.add(withType(request.shippingAddress(), AddressType.SHIPPING));
    if (request.billingAddress() != null) addresses.add(withType(request.billingAddress(), AddressType.BILLING));
    if (addresses.isEmpty() && requiresShippingAddress(request)) throw new IllegalArgumentException("At least one shipping address is required");
    return addresses;
  }

  private boolean requiresShippingAddress(CreateOrderRequest request) {
    return !isPickup(request);
  }

  private boolean isPickup(CreateOrderRequest request) {
    String method = request.deliveryMethod() == null ? "" : request.deliveryMethod().trim().toLowerCase(Locale.ROOT);
    String fulfillment = request.fulfillmentType() == null ? "" : request.fulfillmentType().trim().toLowerCase(Locale.ROOT);
    return method.equals(PICKUP) || method.equals("store-pickup") || method.equals("store pickup") || fulfillment.equals(PICKUP) || fulfillment.equals("store_pickup");
  }

  private boolean isGlobal(CreateOrderRequest request) {
    String method = request.deliveryMethod() == null ? "" : request.deliveryMethod().trim().toLowerCase(Locale.ROOT);
    String fulfillment = request.fulfillmentType() == null ? "" : request.fulfillmentType().trim().toLowerCase(Locale.ROOT);
    String storeId = request.storeId() == null ? "" : request.storeId().trim().toLowerCase(Locale.ROOT);
    return method.equals(GLOBAL) || fulfillment.equals(GLOBAL) || fulfillment.equals("india_wide") || storeId.equals("global-store");
  }

  private String resolveFulfillmentType(CreateOrderRequest request) {
    if (request.fulfillmentType() != null && !request.fulfillmentType().isBlank()) return request.fulfillmentType().trim();
    if (isGlobal(request)) return GLOBAL;
    if (isPickup(request)) return PICKUP;
    return "nearby";
  }

  private String resolveGlobalApproval(CreateOrderRequest request) {
    return isGlobal(request) ? "Pending" : "Not Required";
  }

  private CreateOrderAddressRequest withType(CreateOrderAddressRequest address, AddressType fallbackType) {
    return new CreateOrderAddressRequest(
      address.addressType() == null ? fallbackType : address.addressType(),
      address.addressLine1(),
      address.addressLine2(),
      address.city(),
      address.state(),
      address.country(),
      address.postalCode(),
      address.recipientName(),
      address.recipientPhone());
  }

  private String nextOrderNumber() {
    String orderNumber;
    do {
      orderNumber = "ORD-" + ORDER_DATE.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    } while (orders.existsByOrderNumber(orderNumber));
    return orderNumber;
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private record VerifiedPromotion(String code,BigDecimal discount) { }
}
