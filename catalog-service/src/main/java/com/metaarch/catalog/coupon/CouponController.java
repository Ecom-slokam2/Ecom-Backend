package com.metaarch.catalog.coupon;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {
  private final CouponService service;
  private final com.metaarch.catalog.config.CatalogAccess access;
  private final boolean redemptionEnabled;
  public CouponController(CouponService service, com.metaarch.catalog.config.CatalogAccess access,
      @org.springframework.beans.factory.annotation.Value("${app.promotions.redemption-enabled:false}") boolean redemptionEnabled) {
    this.service = service; this.access = access; this.redemptionEnabled = redemptionEnabled;
  }

  @GetMapping List<CouponResponse> list(org.springframework.security.core.Authentication auth) {
    List<CouponResponse> rows = service.list();
    if (auth == null || !auth.isAuthenticated()) throw new org.springframework.security.access.AccessDeniedException("Authentication is required.");
    if (access.global(auth)) return rows;
    return rows.stream().filter(row -> access.canManageCoupon(auth, row.id())).toList();
  }

  @PostMapping("/validate") CouponValidationResponse validate(@Valid @RequestBody CouponValidationRequest request) {
    if (!redemptionEnabled) return new CouponValidationResponse(false,
        "Coupon redemption is currently disabled.", request.code().trim().toUpperCase(java.util.Locale.ROOT),
        null, null, null, null, null, java.math.BigDecimal.ZERO.setScale(2));
    return service.validate(request);
  }

  @PostMapping
  ResponseEntity<CouponResponse> create(@Valid @RequestBody CouponRequest request, org.springframework.security.core.Authentication auth) {
    CouponRequest scoped = access.scopeCouponRequest(auth, request);
    if (!access.canCreateCoupon(auth, scoped)) throw new org.springframework.security.access.AccessDeniedException("Coupon must be scoped to your assigned location or store.");
    CouponResponse saved = service.create(scoped);
    return ResponseEntity.created(URI.create("/api/v1/coupons/" + saved.id())).body(saved);
  }

  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageCoupon(authentication, #id) || hasAnyRole('ADMIN','GLOBAL_ADMIN','SUPER_ADMIN')")
  @PutMapping("/{id}") CouponResponse update(@PathVariable UUID id, @Valid @RequestBody CouponRequest request, org.springframework.security.core.Authentication auth) {
    CouponRequest scoped = access.scopeCouponRequest(auth, request);
    if (!access.canCreateCoupon(auth, scoped)) throw new org.springframework.security.access.AccessDeniedException("Coupon must remain scoped to your assigned location or store.");
    return service.update(id, scoped);
  }

  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageCoupon(authentication, #id) || hasAnyRole('ADMIN','GLOBAL_ADMIN','SUPER_ADMIN')")
  @PatchMapping("/{id}/status") CouponResponse setStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
    return service.setStatus(id, body == null ? null : body.get("status"));
  }

  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageCoupon(authentication, #id) || hasAnyRole('ADMIN','GLOBAL_ADMIN','SUPER_ADMIN')")
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id) { service.delete(id); }
}
