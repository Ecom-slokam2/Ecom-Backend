package com.metaarch.fulfillment.shipment;

import java.util.List;
import java.util.UUID;

public record CarrierRetryResponse(UUID shipmentId, CarrierRetryOutcome outcome, int attempts, int maxAttempts,
                                   List<CarrierRetryAttemptResponse> history) { }
