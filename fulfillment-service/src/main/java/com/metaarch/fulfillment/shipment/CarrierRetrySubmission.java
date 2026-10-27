package com.metaarch.fulfillment.shipment;

import java.util.UUID;

public record CarrierRetrySubmission(UUID shipmentId, UUID orderId, String storeId, String carrier, String trackingNumber) { }
