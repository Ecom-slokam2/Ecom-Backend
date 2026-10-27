package com.metaarch.order.order;

import java.net.URI;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
  private final OrderService service;
  private final OrderCreation creation;
  private final OrderReviewService reviews;
  private final OrderAccess access;
  public OrderController(OrderService service, OrderCreation creation, OrderReviewService reviews, OrderAccess access) { this.service = service; this.creation = creation; this.reviews = reviews; this.access=access; }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.create(#request.customerId())")
  @PostMapping public ResponseEntity<OrderResponse> create(@RequestHeader(value="Idempotency-Key", required=false) String key, @Valid @RequestBody CreateOrderRequest request) { OrderResponse order = creation.create(key, request); return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.store(#storeId) || hasRole('LOCATION_ADMIN')")
  @GetMapping public List<OrderResponse> list(@RequestParam(required = false) String storeId) {
    if(storeId!=null&&!storeId.isBlank()) { if(!access.store(storeId)) throw new org.springframework.security.access.AccessDeniedException("Store is outside your assigned scope."); return service.list(storeId); }
    if(access.globalAdmin()) return service.list("global-store");
    var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_LOCATION_ADMIN"))) return service.listStores(access.locationStores());
    return service.list(null);
  }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.read(#orderId)")
  @GetMapping("/{orderId}") public OrderResponse get(@PathVariable UUID orderId) { return service.get(orderId); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.globalAssignment()")
  @PostMapping("/{orderId}/global-store-assignment") public OrderResponse assignGlobalStore(@PathVariable UUID orderId, @Valid @RequestBody GlobalStoreAssignmentRequest request) { return service.assignGlobalStore(orderId, request.storeId()); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.globalAssignment()")
  @GetMapping("/{orderId}/global-store-assignment-options") public List<GlobalStoreAssignmentOption> globalStoreAssignmentOptions(@PathVariable UUID orderId) { return service.globalStoreAssignmentOptions(orderId); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.customer(#customerId)")
  @GetMapping("/customer/{customerId}") public List<OrderResponse> getByCustomer(@PathVariable UUID customerId) { return service.getByCustomer(customerId); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.manage(#orderId)")
  @PatchMapping("/{orderId}/status") public OrderResponse updateStatus(@PathVariable UUID orderId, @Valid @RequestBody UpdateOrderStatusRequest request) { return service.updateStatus(orderId, request); }
  @org.springframework.security.access.prepost.PreAuthorize("false")
  @PatchMapping("/{orderId}/payment-status") public OrderResponse updatePaymentStatus(@PathVariable UUID orderId, @Valid @RequestBody UpdateOrderPaymentStatusRequest request) { return service.updatePaymentStatus(orderId, request.normalizedPaymentStatus()); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.owner(#orderId)")
  @PostMapping("/{orderId}/cancel") public OrderResponse cancel(@PathVariable UUID orderId) { return service.cancel(orderId); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.owner(#orderId)")
  @GetMapping("/{orderId}/items") public List<OrderItemResponse> getItems(@PathVariable UUID orderId) { return service.getItems(orderId); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.customerOwner(#orderId)")
  @GetMapping("/{orderId}/review") public ResponseEntity<OrderReviewResponse> getReview(@PathVariable UUID orderId, @RequestParam(required=false) UUID productId) { return (productId == null ? reviews.get(orderId) : reviews.get(orderId,productId)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.customerOwner(#orderId)")
  @GetMapping("/{orderId}/reviews") public List<OrderReviewResponse> getReviews(@PathVariable UUID orderId) { return reviews.getForOrder(orderId); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.customerOwner(#orderId)")
  @PostMapping("/{orderId}/review") public OrderReviewResponse saveReview(@PathVariable UUID orderId, @Valid @RequestBody OrderReviewRequest request) { return reviews.save(orderId,request); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.customerOwner(#orderId)")
  @PostMapping("/{orderId}/reviews") public OrderReviewResponse saveProductReview(@PathVariable UUID orderId, @Valid @RequestBody OrderReviewRequest request) { return reviews.save(orderId,request); }
  @org.springframework.security.access.prepost.PreAuthorize("@orderAccess.customer(#customerId)")
  @GetMapping("/customer/{customerId}/history") public List<OrderHistoryResponse> getHistory(@PathVariable UUID customerId) { return service.getHistory(customerId); }
}
