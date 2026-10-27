package com.metaarch.inventory.inventory;

import com.metaarch.sync.CatalogLocationClient;
import com.metaarch.sync.PublicApiSecurity;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("inventoryAccess")
public class InventoryAccess {
  private final CatalogLocationClient catalog;

  public InventoryAccess(CatalogLocationClient catalog) { this.catalog = catalog; }

  public boolean global(Authentication auth) { return PublicApiSecurity.administrator(); }
  public boolean globalAdmin(Authentication auth) { return has(auth, "ROLE_GLOBAL_ADMIN"); }

  public boolean locationAdmin(Authentication auth) { return has(auth, "ROLE_LOCATION_ADMIN") && location(auth) != null; }
  public boolean storeManager(Authentication auth) { return has(auth, "ROLE_STORE_MANAGER") && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().startsWith("SCOPE_STORE:")); }

  public String location(Authentication auth) {
    if (auth == null) return null;
    return auth.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("SCOPE_LOCATION:"))
        .map(a -> a.substring("SCOPE_LOCATION:".length())).filter(s -> !s.isBlank()).findFirst().orElse(null);
  }

  public List<String> allowedStores(Authentication auth) {
    if (globalAdmin(auth)) return List.of("global-store");
    if (global(auth)) return null;
    if (locationAdmin(auth)) return catalog.storesInLocation(location(auth));
    if (auth != null && has(auth, "ROLE_STORE_MANAGER")) return auth.getAuthorities().stream()
        .map(a -> a.getAuthority()).filter(a -> a.startsWith("SCOPE_STORE:")).map(a -> a.substring("SCOPE_STORE:".length())).toList();
    return List.of();
  }

  public boolean canAccessStore(Authentication auth, String storeId) {
    if (globalAdmin(auth)) return "global-store".equalsIgnoreCase(storeId == null ? "" : storeId.trim());
    if (global(auth)) return true;
    if (storeId == null || storeId.isBlank()) return false;
    if (locationAdmin(auth)) return catalog.locationOf(storeId).map(value -> value.equalsIgnoreCase(location(auth))).orElse(false);
    return auth != null && has(auth, "ROLE_STORE_MANAGER") && has(auth, "SCOPE_STORE:" + storeId.trim());
  }

  public void requireStore(Authentication auth, String storeId) {
    if (!canAccessStore(auth, storeId)) throw new org.springframework.security.access.AccessDeniedException("Store is outside your assigned scope.");
  }

  public void requireProductAssignment(Authentication auth, String storeId, java.util.UUID productId) {
    if (globalAdmin(auth) && (!"global-store".equalsIgnoreCase(storeId == null ? "" : storeId.trim())
        || !catalog.hasActiveProductMapping("global-store", productId)))
      throw new org.springframework.security.access.AccessDeniedException("Global Store inventory can be created only for an active Global Store product mapping.");
  }

  private boolean has(Authentication auth, String authority) {
    return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }
}
