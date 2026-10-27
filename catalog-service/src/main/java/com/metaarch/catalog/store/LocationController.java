package com.metaarch.catalog.store;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/locations")
@org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.global(authentication)")
public class LocationController {
  private final StoreService service;
  private final com.metaarch.catalog.config.CatalogAccess access;

  public LocationController(StoreService service, com.metaarch.catalog.config.CatalogAccess access) {
    this.service = service;
    this.access = access;
  }

  @GetMapping
  List<StoreResponse> list(@RequestParam(required = false) Boolean active) {
    return service.listLocations(active);
  }

  @PostMapping
  ResponseEntity<StoreResponse> create(@Valid @RequestBody StoreRequest request, Authentication auth) {
    requireLocationId(request.id());
    if (!access.global(auth)) throw new AccessDeniedException("Only global administrators can manage locations.");
    StoreResponse location = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/locations/" + location.id())).body(location);
  }

  @PutMapping("/{id}")
  StoreResponse update(@PathVariable String id, @Valid @RequestBody StoreRequest request) {
    requireLocationId(id);
    if (!id.equals(request.id())) throw new IllegalArgumentException("Location ID cannot be changed.");
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable String id) {
    requireLocationId(id);
    service.delete(id);
  }

  private void requireLocationId(String id) {
    if (id == null || !id.startsWith("location-")) throw new IllegalArgumentException("Invalid location ID.");
  }
}
