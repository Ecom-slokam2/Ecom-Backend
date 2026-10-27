package com.metaarch.order.order;

import com.metaarch.order.exception.DeliveryQuoteVerificationException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogDeliveryQuoteClient {
  private final RestClient restClient;
  private final String baseUrl;

  public CatalogDeliveryQuoteClient(RestClient.Builder builder, @Value("${catalog.service.url:${CATALOG_SERVICE_URL:http://localhost:8089}}") String baseUrl) {
    this.restClient = builder.build();
    this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
  }

  public DeliveryQuote verify(String storeId, BigDecimal customerLatitude, BigDecimal customerLongitude, BigDecimal orderTotal, String deliveryMethod) {
    if (customerLatitude == null || customerLongitude == null) {
      throw new DeliveryQuoteVerificationException("Unable to verify delivery route right now. Please try again.");
    }
    try {
      URI uri = URI.create(baseUrl + "/api/v1/stores/" + storeId + "/delivery-quote");
      DeliveryQuote quote = restClient.get()
        .uri(builder -> builder
          .scheme(uri.getScheme())
          .host(uri.getHost())
          .port(uri.getPort())
          .path(uri.getPath())
          .queryParam("latitude", customerLatitude)
          .queryParam("longitude", customerLongitude)
          .queryParamIfPresent("orderTotal", java.util.Optional.ofNullable(orderTotal))
          .queryParamIfPresent("deliveryMethod", java.util.Optional.ofNullable(deliveryMethod))
          .build())
        .retrieve()
        .body(DeliveryQuote.class);
      if (quote == null || !quote.deliveryAvailable()) {
        String message = quote != null && quote.message() != null && !quote.message().isBlank() ? quote.message() : "Unable to verify delivery route right now. Please try again.";
        throw new DeliveryQuoteVerificationException(message);
      }
      return quote;
    } catch (DeliveryQuoteVerificationException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new DeliveryQuoteVerificationException("Unable to verify delivery route right now. Please try again.", ex);
    }
  }

  public DeliveryZoneLocation resolveLocation(Long deliveryZoneId) {
    if (deliveryZoneId == null) {
      throw new DeliveryQuoteVerificationException("Delivery zone is required for this order.");
    }
    try {
      URI uri = URI.create(baseUrl + "/api/v1/stores/delivery-zones/" + deliveryZoneId + "/location");
      DeliveryZoneLocation location = restClient.get()
        .uri(uri)
        .retrieve()
        .body(DeliveryZoneLocation.class);
      if (location == null || location.locationId() == null || location.locationId().isBlank()) {
        throw new DeliveryQuoteVerificationException("Delivery zone has no authoritative location mapping: " + deliveryZoneId);
      }
      return location;
    } catch (DeliveryQuoteVerificationException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new DeliveryQuoteVerificationException("Unable to resolve delivery zone location right now. Please try again.", ex);
    }
  }

  public CouponPromotion validateCoupon(String code, BigDecimal subtotal, String location, String store) {
    try {
      CouponPromotion promotion=restClient.post().uri(baseUrl+"/api/v1/coupons/validate")
        .body(new CouponValidationRequest(code,subtotal,location,store)).retrieve().body(CouponPromotion.class);
      if(promotion==null || !promotion.valid()) throw new DeliveryQuoteVerificationException(promotion==null?"Coupon validation is unavailable.":promotion.message());
      return promotion;
    } catch(DeliveryQuoteVerificationException ex) { throw ex; }
      catch(Exception ex) { throw new DeliveryQuoteVerificationException("Coupon validation is unavailable. Please retry.",ex); }
  }

  public record CouponValidationRequest(String code, BigDecimal subtotal, String location, String store) { }
  public record CouponPromotion(boolean valid, String message, String code, String couponName,
      String discountType, BigDecimal discount, BigDecimal maximumDiscount, BigDecimal minimumOrder,
      BigDecimal discountAmount) { }

  public CreateOrderItemRequest verifyItem(String storeId, CreateOrderItemRequest item, boolean global, boolean pickup) {
    if (item.productId() == null || item.quantity() < 1 || item.unitPrice() == null || item.unitPrice().signum() <= 0) {
      throw new DeliveryQuoteVerificationException("Invalid order item.");
    }
    try {
      ProductSnapshot product = restClient.get().uri(baseUrl + "/api/v1/products/" + item.productId()).retrieve().body(ProductSnapshot.class);
      if (product == null || !item.productId().equals(product.id()) || !"ACTIVE".equals(product.status())) {
        throw new DeliveryQuoteVerificationException("Product is no longer available.");
      }
      if (global && (!product.globalProduct() || pickup)) {
        throw new com.metaarch.order.exception.ProductNotGlobalEligibleException();
      }
      List<VariantSnapshot> variants = product.variants() == null ? List.of() : product.variants();
      VariantSnapshot variant = variants.stream().filter(v -> v.active() && item.productId().equals(v.productId()) && (item.variantId() == null || item.variantId().equals(v.id()))).findFirst()
          .orElseThrow(() -> new DeliveryQuoteVerificationException("Select an available product variant."));
      if (item.variantId() == null && variants.stream().filter(VariantSnapshot::active).count() != 1) {
        throw new DeliveryQuoteVerificationException("Select a product variant before checkout.");
      }
      BigDecimal price = variant.price();
      if (!global) {
        StoreSnapshot[] stores = restClient.get().uri(baseUrl + "/api/v1/stores").retrieve().body(StoreSnapshot[].class);
        StoreSnapshot store = java.util.Arrays.stream(stores == null ? new StoreSnapshot[0] : stores)
            .filter(candidate -> storeId.equals(candidate.id())).findFirst().orElse(null);
        if (store == null || !store.active() || !"Active".equalsIgnoreCase(store.status()) || (pickup ? !store.pickupEnabled() : !store.deliveryEnabled())) {
          throw new DeliveryQuoteVerificationException("Store is unavailable for the selected fulfilment method.");
        }
        StoreProductSnapshot[] mappings = restClient.get().uri(builder -> builder.scheme(URI.create(baseUrl).getScheme()).host(URI.create(baseUrl).getHost()).port(URI.create(baseUrl).getPort())
            .path("/api/v1/public/store-products").queryParam("storeId", storeId).queryParam("productId", item.productId()).build()).retrieve().body(StoreProductSnapshot[].class);
        StoreProductSnapshot mapping = java.util.Arrays.stream(mappings == null ? new StoreProductSnapshot[0] : mappings)
            .filter(m -> storeId.equals(m.storeId()) && item.productId().equals(m.productId()) && m.available()).findFirst()
            .orElseThrow(() -> new DeliveryQuoteVerificationException("Product is unavailable at the selected store."));
        if (mapping.priceOverride() != null) price = mapping.priceOverride();
      }
      if (price == null || price.signum() <= 0 || item.unitPrice().compareTo(price) != 0) {
        throw new DeliveryQuoteVerificationException("Product price changed. Refresh your cart before checkout.");
      }
      return new CreateOrderItemRequest(product.id(), variant.id(), product.name(), item.quantity(), price);
    } catch (DeliveryQuoteVerificationException error) {
      throw error;
    } catch (Exception error) {
      throw new DeliveryQuoteVerificationException("Unable to verify product pricing. Please retry checkout.", error);
    }
  }

  public record ProductSnapshot(UUID id, String name, String status, boolean globalProduct, List<VariantSnapshot> variants) { }
  public record VariantSnapshot(UUID id, UUID productId, BigDecimal price, boolean active) { }
  public record StoreSnapshot(String id, boolean active, String status, boolean deliveryEnabled, boolean pickupEnabled) { }
  public record StoreProductSnapshot(String storeId, UUID productId, boolean available, BigDecimal priceOverride) { }

  public record DeliveryQuote(
    String storeId,
    BigDecimal storeLatitude,
    BigDecimal storeLongitude,
    BigDecimal customerLatitude,
    BigDecimal customerLongitude,
    boolean deliveryAvailable,
    BigDecimal distanceKm,
    Long routeDistanceMeters,
    Integer routeDurationSeconds,
    Integer routeDurationMinutes,
    String matchedZoneLabel,
    Object routeGeometry,
    Long deliveryZoneId,
    String selectedDeliveryMethod,
    BigDecimal deliveryCharge,
    BigDecimal freeDeliveryAbove,
    boolean freeDeliveryApplied,
    BigDecimal minimumOrder,
    Integer estimatedDeliveryMin,
    Integer estimatedDeliveryMax,
    boolean storePickupAvailable,
    List<Object> methods,
    String message
  ) { }

  public record DeliveryZoneLocation(Long deliveryZoneId, String storeId, String locationId) { }
}
