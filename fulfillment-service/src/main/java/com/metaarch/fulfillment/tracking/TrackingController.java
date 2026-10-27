package com.metaarch.fulfillment.tracking;

import java.net.URI;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shipments/{shipmentId}/tracking-events")
public class TrackingController {
  private final TrackingService service;
  public TrackingController(TrackingService service) { this.service = service; }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.updateAssignedShipment(#shipmentId, #request.status())")
  @PostMapping public ResponseEntity<TrackingEventResponse> add(@PathVariable UUID shipmentId, @Valid @RequestBody AddTrackingEventRequest request) { TrackingEventResponse event = service.add(shipmentId, request); return ResponseEntity.created(URI.create("/api/v1/shipments/" + shipmentId + "/tracking-events/" + event.id())).body(event); }
  @org.springframework.security.access.prepost.PreAuthorize("@fulfillmentAccess.shipment(#shipmentId)")
  @GetMapping public List<TrackingEventResponse> history(@PathVariable UUID shipmentId) { return service.history(shipmentId); }
}
