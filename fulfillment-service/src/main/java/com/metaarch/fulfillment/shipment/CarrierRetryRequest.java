package com.metaarch.fulfillment.shipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CarrierRetryRequest(
    @NotBlank @Size(max = 100) String idempotencyKey,
    @NotBlank @Size(max = 100) String deliveryPartnerId
) { }
