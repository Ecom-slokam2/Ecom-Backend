package com.metaarch.inventory.inventory;

import java.net.URI;
import java.util.UUID;
import com.metaarch.inventory.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/inventories")
public class InventoryController {
  private final InventoryService service;
  private final InventoryAccess access;
  public InventoryController(InventoryService service, InventoryAccess access) { this.service = service; this.access = access; }

  @org.springframework.security.access.prepost.PreAuthorize("T(com.metaarch.sync.PublicApiSecurity).administrator() || @inventoryAccess.locationAdmin(authentication) || @inventoryAccess.storeManager(authentication)")
  @PostMapping ResponseEntity<InventoryResponse> create(@Valid @RequestBody InventoryRequest r, org.springframework.security.core.Authentication auth) {
    access.requireStore(auth, r.storeId());
    access.requireProductAssignment(auth, r.storeId(), r.productId());
    InventoryResponse v = service.create(r); return ResponseEntity.created(URI.create("/api/v1/inventories/" + v.id())).body(v);
  }
  @org.springframework.security.access.prepost.PreAuthorize("T(com.metaarch.sync.PublicApiSecurity).administrator() || @inventoryAccess.locationAdmin(authentication) || @inventoryAccess.storeManager(authentication)")
  @PutMapping("/{id}") InventoryResponse update(@PathVariable UUID id, @Valid @RequestBody InventoryUpdateRequest r, org.springframework.security.core.Authentication auth) { access.requireStore(auth, service.find(id).getStoreId()); return service.update(id, r); }
  @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
  @GetMapping("/{id}") InventoryResponse get(@PathVariable UUID id, org.springframework.security.core.Authentication auth) { InventoryResponse result=service.get(id); access.requireStore(auth,result.storeId()); return result; }
  @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
  @GetMapping(params = "productId") InventoryResponse getByLookup(@RequestParam UUID productId, @RequestParam(required = false) UUID variantId, @RequestParam String storeId, org.springframework.security.core.Authentication auth) { access.requireStore(auth,storeId); return service.getByLookup(productId, variantId, storeId); }
  @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
  @GetMapping PageResponse<InventoryResponse> list(@RequestParam(required = false) String storeId, @RequestParam(required = false) InventoryStatus status, @PageableDefault(size = 50, sort = "updatedAt") Pageable pageable, org.springframework.security.core.Authentication auth) {
    if(storeId!=null&&!storeId.isBlank()){access.requireStore(auth,storeId);return service.list(storeId,status,pageable);}
    if(access.globalAdmin(auth))return service.list("global-store",status,pageable);
    if(access.global(auth))return service.list(null,status,pageable);
    return service.listStores(access.allowedStores(auth),status,pageable);
  }
}
