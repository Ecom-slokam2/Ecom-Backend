package com.metaarch.fulfillment.shipment;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {
  private final ShipmentService service;
  private final DeliveryPartnerEligibility partnerEligibility;
  private final CarrierRetryService carrierRetryService;
  public ShipmentController(ShipmentService service, DeliveryPartnerEligibility partnerEligibility, CarrierRetryService carrierRetryService) { this.service = service; this.partnerEligibility = partnerEligibility; this.carrierRetryService = carrierRetryService; }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.partnerId() != null")
  @GetMapping("/assigned") public java.util.List<ShipmentResponse> assigned() {
    var token=(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken)org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return service.assigned(token.getToken().getClaimAsString("deliveryPartnerId"));
  }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.manage(#request.fulfillmentId())")
  @PostMapping public ResponseEntity<ShipmentResponse> create(@Valid @RequestBody CreateShipmentRequest request) { ShipmentResponse shipment = service.create(request); return ResponseEntity.created(URI.create("/api/v1/shipments/" + shipment.id())).body(shipment); }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.order(#orderId)")
  @GetMapping("/order/{orderId}") public ShipmentResponse getByOrder(@PathVariable UUID orderId) { return service.getByOrder(orderId); }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.updateAssignedShipment(#shipmentId, #request.status())")
  @PatchMapping("/{shipmentId}/status") public ShipmentResponse updateStatus(@PathVariable UUID shipmentId, @Valid @RequestBody UpdateShipmentStatusRequest request) { return service.updateStatus(shipmentId, request); }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.manageShipment(#shipmentId)")
  @PatchMapping("/{shipmentId}/delivery-partner") public ShipmentResponse assignDeliveryPartner(@PathVariable UUID shipmentId, @Valid @RequestBody AssignDeliveryPartnerRequest request, org.springframework.security.core.Authentication authentication) {
    AssignDeliveryPartnerRequest verified = partnerEligibility.verifiedRequest(authentication, shipmentId, request.deliveryPartnerId());
    return service.assignDeliveryPartner(shipmentId, verified);
  }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.manageShipment(#shipmentId)")
  @PostMapping("/{shipmentId}/carrier-retry")
  public CarrierRetryResponse retryCarrier(@PathVariable UUID shipmentId, @Valid @RequestBody CarrierRetryRequest request, org.springframework.security.core.Authentication authentication) {
    AssignDeliveryPartnerRequest verified = partnerEligibility.verifiedRequest(authentication, shipmentId, request.deliveryPartnerId());
    return carrierRetryService.retry(shipmentId, verified, request.idempotencyKey());
  }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.shipment(#shipmentId)")
  @GetMapping("/{shipmentId}/carrier-attempts")
  public java.util.List<CarrierRetryAttemptResponse> carrierAttempts(@PathVariable UUID shipmentId) { return carrierRetryService.history(shipmentId); }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.manageShipment(#shipmentId)")
  @PatchMapping("/{shipmentId}/delivered") public ShipmentResponse markDelivered(@PathVariable UUID shipmentId) { return service.markDelivered(shipmentId); }
}
