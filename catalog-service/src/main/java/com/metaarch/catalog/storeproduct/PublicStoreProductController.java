package com.metaarch.catalog.storeproduct;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/store-products")
public class PublicStoreProductController {
  private final StoreProductService storeProducts;

  public PublicStoreProductController(StoreProductService storeProducts) {
    this.storeProducts = storeProducts;
  }

  @GetMapping
  public List<StoreProductResponse> list(
      @RequestParam(required = false) String storeId,
      @RequestParam(required = false) UUID productId) {
    return storeProducts.publicList(storeId, productId);
  }
}
