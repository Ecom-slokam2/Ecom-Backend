package com.metaarch.catalog.product;

import com.metaarch.catalog.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store-manager-products")
@org.springframework.security.access.prepost.PreAuthorize("hasRole('STORE_MANAGER')")
public class StoreManagerProductController {
  private final ProductService products;

  public StoreManagerProductController(ProductService products) {
    this.products = products;
  }

  @GetMapping
  public PageResponse<ProductResponse> list(@RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) ProductStatus status, @RequestParam(required = false) String search,
      @PageableDefault(size = 50, sort = "name") Pageable pageable, Authentication authentication) {
    return products.listForStoreManager(authentication, categoryId, status, search, pageable);
  }

  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody StoreManagerProductRequest request,
      Authentication authentication) {
    ProductResponse created = products.createForStoreManager(request.product(), request.available(), authentication);
    return ResponseEntity.created(URI.create("/api/v1/store-manager-products/" + created.id())).body(created);
  }

  @PutMapping("/{id}")
  public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody StoreManagerProductRequest request,
      Authentication authentication) {
    return products.updateForStoreManager(id, request.product(), request.available(), authentication);
  }

  @PatchMapping("/{id}/availability")
  public ProductResponse setAvailability(@PathVariable UUID id, @Valid @RequestBody AvailabilityRequest request,
      Authentication authentication) {
    return products.setStoreManagerProductAvailability(id, request.available(), authentication);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, Authentication authentication) {
    products.deleteForStoreManager(id, authentication);
  }

  public record AvailabilityRequest(@jakarta.validation.constraints.NotNull Boolean available) {}
}
