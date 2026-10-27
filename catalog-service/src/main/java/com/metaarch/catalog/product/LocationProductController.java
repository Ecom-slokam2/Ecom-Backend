package com.metaarch.catalog.product;

import com.metaarch.catalog.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/location-products")
@PreAuthorize("hasRole('LOCATION_ADMIN')")
public class LocationProductController {
  private final ProductService products;

  public LocationProductController(ProductService products) {
    this.products = products;
  }

  @GetMapping
  public PageResponse<ProductResponse> list(@PageableDefault(size = 50, sort = "name") Pageable pageable,
      Authentication authentication) {
    return products.listForLocation(authentication, pageable);
  }

  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody LocationProductRequest request,
      Authentication authentication) {
    ProductResponse created = products.createForLocation(request.product(), request.storeIds(),
        request.available(), authentication);
    return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
  }

  @PutMapping("/{id}")
  public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody LocationProductRequest request,
      Authentication authentication) {
    return products.updateForLocation(id, request.product(), request.storeIds(), request.available(), authentication);
  }

  @PatchMapping("/{id}/availability")
  public ProductResponse setAvailability(@PathVariable UUID id, @Valid @RequestBody AvailabilityRequest request,
      Authentication authentication) {
    return products.setLocationAvailability(id, request.available(), authentication);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, Authentication authentication) {
    products.deleteForLocation(id, authentication);
  }

  public record AvailabilityRequest(@jakarta.validation.constraints.NotNull Boolean available) {}
}
