package com.metaarch.inventory.inventory;

import com.metaarch.sync.CatalogLocationClient;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public/inventory-availability")
public class PublicInventoryAvailabilityController {
  private final InventoryService inventories;
  private final CatalogLocationClient catalog;

  public PublicInventoryAvailabilityController(InventoryService inventories, CatalogLocationClient catalog) {
    this.inventories = inventories;
    this.catalog = catalog;
  }

  @GetMapping
  public PublicInventoryAvailabilityResponse get(@RequestParam UUID productId,
      @RequestParam(required = false) UUID variantId, @RequestParam String storeId) {
    try {
      if (!catalog.hasActiveProductMapping(storeId, productId))
        return new PublicInventoryAvailabilityResponse(productId, variantId, storeId, false);
    } catch (org.springframework.web.client.RestClientException error) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Product availability is temporarily unavailable.");
    }
    return new PublicInventoryAvailabilityResponse(productId, variantId, storeId,
        inventories.isPubliclyAvailable(productId, variantId, storeId));
  }
}
