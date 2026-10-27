package com.metaarch.inventory.inventory;

import java.util.UUID;

public record PublicInventoryAvailabilityResponse(UUID productId, UUID variantId, String storeId, boolean available) { }
