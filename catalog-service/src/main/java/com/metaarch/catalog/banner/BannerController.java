package com.metaarch.catalog.banner;

import java.net.URI;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/banners")
public class BannerController {
  private final BannerService service;
  private final com.metaarch.catalog.config.CatalogAccess access;
  public BannerController(BannerService service, com.metaarch.catalog.config.CatalogAccess access) { this.service = service; this.access = access; }

  @GetMapping
  List<BannerResponse> list(@RequestParam(name = "all", defaultValue = "false") boolean all, org.springframework.security.core.Authentication auth) {
    List<BannerResponse> rows = service.list(!all);
    if (auth == null || !auth.isAuthenticated() || access.global(auth)
        || (!access.storeManager(auth) && !access.locationAdmin(auth))) return rows;
    return rows.stream().filter(row -> access.canManageBanner(auth, row.id())).toList();
  }

  @GetMapping("/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageBanner(authentication, #id) || hasAnyRole('ADMIN','GLOBAL_ADMIN','SUPER_ADMIN')")
  BannerResponse get(@PathVariable UUID id) { return service.get(id); }

  @PostMapping
  ResponseEntity<BannerResponse> create(@Valid @RequestBody BannerRequest r, org.springframework.security.core.Authentication auth) {
    BannerRequest scoped = access.scopeBannerRequest(auth, r);
    if (!access.canCreateBanner(auth, scoped)) throw new org.springframework.security.access.AccessDeniedException("Banner must belong to your assigned location or store.");
    BannerResponse v = service.create(scoped);
    return ResponseEntity.created(URI.create("/api/v1/banners/" + v.id())).body(v);
  }

  @PutMapping("/{id}")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageBanner(authentication, #id) || hasAnyRole('ADMIN','GLOBAL_ADMIN','SUPER_ADMIN')")
  BannerResponse update(@PathVariable UUID id, @Valid @RequestBody BannerRequest r, org.springframework.security.core.Authentication auth) {
    BannerRequest scoped = access.scopeBannerRequest(auth, r);
    if (!access.canCreateBanner(auth, scoped)) throw new org.springframework.security.access.AccessDeniedException("Banner must remain in your assigned location or store.");
    return service.update(id, scoped);
  }

  @PatchMapping("/{id}/status")
  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageBanner(authentication, #id) || hasAnyRole('ADMIN','GLOBAL_ADMIN','SUPER_ADMIN')")
  BannerResponse setStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
    String raw = body == null ? null : body.get("status");
    return service.setStatus(id, raw == null ? null : BannerStatus.valueOf(raw.trim().toUpperCase()));
  }

  @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageBanner(authentication, #id) || hasAnyRole('ADMIN','GLOBAL_ADMIN','SUPER_ADMIN')")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) { service.delete(id); }
}
