package com.metaarch.catalog.store;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {
  private final StoreService service;
  private final StoreDeliveryService deliveryService;
  private final com.metaarch.catalog.config.CatalogAccess access;
  public StoreController(StoreService service, StoreDeliveryService deliveryService, com.metaarch.catalog.config.CatalogAccess access) {
    this.service = service; this.deliveryService = deliveryService; this.access = access;
  }

  @PostMapping
  ResponseEntity<StoreResponse> create(@Valid @RequestBody StoreRequest request, Authentication auth) {
    if (!access.canCreateStore(auth, request.location())) throw new org.springframework.security.access.AccessDeniedException("Store must belong to your assigned location.");
    StoreResponse store = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/stores/" + store.id())).body(store);
  }

  @GetMapping
  List<StoreResponse> list(@RequestParam(required = false) Boolean active, Authentication auth) {
    List<StoreResponse> rows = service.list(active);
    if (auth == null) return rows;
    if (access.globalAdmin(auth)) return rows.stream().filter(store -> "global-store".equalsIgnoreCase(store.id())).toList();
    if (!access.locationAdmin(auth) && !access.storeManager(auth)) return rows;
    return rows.stream().filter(store -> access.canManageStore(auth, store.id())).toList();
  }

  @GetMapping("/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canReadStore(authentication, #id)")
  StoreResponse get(@PathVariable String id) { return service.get(id); }

  @PutMapping("/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageStore(authentication, #id)")
  StoreResponse update(@PathVariable String id, @Valid @RequestBody StoreRequest request, Authentication auth) {
    if (!access.canUpdateStore(auth, id, request.location())) throw new org.springframework.security.access.AccessDeniedException("Store must remain in your assigned location.");
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageStore(authentication, #id)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable String id) { service.delete(id); }

  @GetMapping("/{id}/delivery-zones")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canReadStore(authentication, #id)")
  List<StoreDeliveryZoneResponse> deliveryZones(@PathVariable String id) { return deliveryService.listZones(id); }

  @GetMapping("/delivery-zones/{zoneId}/location")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canReadDeliveryZone(authentication, #zoneId)")
  DeliveryZoneLocationResponse deliveryZoneLocation(@PathVariable Long zoneId) { return deliveryService.getZoneLocation(zoneId); }

  @PostMapping("/{id}/delivery-zones")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageStore(authentication, #id)")
  ResponseEntity<StoreDeliveryZoneResponse> createDeliveryZone(@PathVariable String id, @Valid @RequestBody StoreDeliveryZoneRequest request) {
    StoreDeliveryZoneResponse zone = deliveryService.createZone(id, request);
    return ResponseEntity.created(URI.create("/api/v1/stores/" + id + "/delivery-zones/" + zone.id())).body(zone);
  }

  @PutMapping("/{storeId}/delivery-zones/{zoneId}")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageStore(authentication, #storeId)")
  StoreDeliveryZoneResponse updateDeliveryZone(@PathVariable String storeId, @PathVariable Long zoneId, @Valid @RequestBody StoreDeliveryZoneRequest request) {
    return deliveryService.updateZone(storeId, zoneId, request);
  }

  @DeleteMapping("/{storeId}/delivery-zones/{zoneId}")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageStore(authentication, #storeId)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteDeliveryZone(@PathVariable String storeId, @PathVariable Long zoneId) { deliveryService.deleteZone(storeId, zoneId); }

  @GetMapping("/{id}/delivery-quote")
  DeliveryQuoteResponse deliveryQuote(
      @PathVariable String id,
      @RequestParam java.math.BigDecimal latitude,
      @RequestParam java.math.BigDecimal longitude,
      @RequestParam(required = false) java.math.BigDecimal orderTotal,
      @RequestParam(required = false) String deliveryMethod) {
    return deliveryService.quote(id, latitude, longitude, orderTotal, deliveryMethod);
  }
}
