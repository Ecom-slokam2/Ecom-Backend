package com.metaarch.order.order;

import com.metaarch.sync.PublicApiSecurity;
import java.util.UUID;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@Component("orderAccess")
@Import({PublicApiSecurity.class, com.metaarch.sync.CatalogLocationClient.class})
public class OrderAccess {
  private final OrderRepository orders;
  private final com.metaarch.sync.CatalogLocationClient catalog;
  public OrderAccess(OrderRepository orders, com.metaarch.sync.CatalogLocationClient catalog) { this.orders = orders; this.catalog = catalog; }
  public boolean admin() { return PublicApiSecurity.administrator(); }
  public boolean globalAdmin() {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_GLOBAL_ADMIN"));
  }
  private boolean platformAdmin() {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getAuthorities().stream().anyMatch(authority -> java.util.Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN").contains(authority.getAuthority()));
  }
  public boolean globalAssignment() {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token
        && token.getAuthorities().stream().anyMatch(authority -> java.util.Set.of("ROLE_SUPER_ADMIN", "ROLE_GLOBAL_ADMIN").contains(authority.getAuthority()));
  }
  public boolean reviewModerator() {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token
        && !"customer".equals(token.getToken().getClaimAsString("accountType"))
        && token.getAuthorities().stream().anyMatch(authority -> java.util.Set.of("ROLE_SUPER_ADMIN", "ROLE_GLOBAL_ADMIN").contains(authority.getAuthority()));
  }
  public UUID currentCustomerId() {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token)
        || !"customer".equals(token.getToken().getClaimAsString("accountType"))) return null;
    try { return UUID.fromString(token.getToken().getClaimAsString("customerId")); }
    catch (RuntimeException error) { return null; }
  }
  public boolean customerReviewReader() { return currentCustomerId() != null; }
  public boolean superAdmin() {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token
        && token.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
  }
  public boolean customer(UUID id) { return admin() || PublicApiSecurity.owns(id); }
  public boolean customerOwner(UUID orderId) {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token
        && "customer".equals(token.getToken().getClaimAsString("accountType"))
        && orders.findById(orderId).map(order -> PublicApiSecurity.owns(order.getCustomerId())).orElse(false);
  }
  @org.springframework.beans.factory.annotation.Value("${FULFILLMENT_SERVICE_URL:http://127.0.0.1:3315}")
  private String fulfillmentUrl="http://127.0.0.1:3315";
  private boolean assignedPartner(UUID id) {
    var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token)
        || "customer".equals(token.getToken().getClaimAsString("accountType"))
        || token.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_PARTNER"))) return false;
    String partner=token.getToken().getClaimAsString("deliveryPartnerId");
    if (partner==null || partner.isBlank()) return false;
    var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
    try {
      var shipment=org.springframework.web.client.RestClient.builder().requestFactory(factory).baseUrl(fulfillmentUrl).build().get()
        .uri("/api/v1/shipments/order/"+id).header("Authorization","Bearer "+token.getToken().getTokenValue()).retrieve().body(java.util.Map.class);
      return shipment!=null && id.toString().equals(String.valueOf(shipment.get("orderId"))) && partner.equals(String.valueOf(shipment.get("deliveryPartnerId")));
    } catch (org.springframework.web.client.RestClientException error) { return false; }
  }
  public boolean read(UUID id) { return owner(id) || assignedPartner(id); }
  public boolean owner(UUID id) { return platformAdmin() || orders.findById(id).map(o -> PublicApiSecurity.owns(o.getCustomerId()) || manages(o.getStoreId())).orElse(false); }
  public boolean store(String storeId) { return platformAdmin() || globalAdmin() && (storeId == null || storeId.isBlank() || "global-store".equalsIgnoreCase(storeId.trim())) || manages(storeId); }
  public boolean manage(UUID id) { return platformAdmin() || orders.findById(id).map(o -> manages(o.getStoreId())).orElse(false); }

  public java.util.List<String> locationStores() {
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || !auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LOCATION_ADMIN"))) return java.util.List.of();
    String location = auth.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("SCOPE_LOCATION:"))
        .map(a -> a.substring("SCOPE_LOCATION:".length())).findFirst().orElse(null);
    return catalog.storesInLocation(location);
  }

  private boolean manages(String storeId) {
    if (globalAdmin()) return "global-store".equalsIgnoreCase(storeId == null ? "" : storeId.trim());
    if (PublicApiSecurity.managesStore(storeId)) return true;
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token)
        || "customer".equals(token.getToken().getClaimAsString("accountType"))
        || token.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_LOCATION_ADMIN"))) return false;
    String location = token.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("SCOPE_LOCATION:"))
        .map(a -> a.substring("SCOPE_LOCATION:".length())).findFirst().orElse(null);
    return location != null && catalog.locationOf(storeId).map(location::equalsIgnoreCase).orElse(false);
  }
  public boolean create(UUID id) { return PublicApiSecurity.owns(id); }
}
