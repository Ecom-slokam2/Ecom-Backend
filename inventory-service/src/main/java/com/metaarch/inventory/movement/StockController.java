package com.metaarch.inventory.movement;

import java.util.UUID;
import com.metaarch.inventory.common.PageResponse;
import com.metaarch.inventory.inventory.InventoryResponse;
import com.metaarch.inventory.inventory.InventoryService;
import com.metaarch.inventory.inventory.InventoryAccess;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.security.access.prepost.PreAuthorize("T(com.metaarch.sync.PublicApiSecurity).administrator()")
public class StockController {
  private final StockService stock;
  private final StockMovementService movements;
  private final InventoryService inventories;
  private final InventoryAccess access;
  public StockController(StockService stock, StockMovementService movements, InventoryService inventories, InventoryAccess access) { this.stock = stock; this.movements = movements; this.inventories=inventories; this.access=access; }

  @org.springframework.security.access.prepost.PreAuthorize("T(com.metaarch.sync.PublicApiSecurity).administrator() || @inventoryAccess.locationAdmin(authentication) || @inventoryAccess.storeManager(authentication)")
  @PostMapping("/api/v1/inventories/{id}/stock/increase") InventoryResponse increase(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest r, org.springframework.security.core.Authentication auth) { access.requireStore(auth,inventories.find(id).getStoreId()); return stock.increase(id, r); }
  @org.springframework.security.access.prepost.PreAuthorize("T(com.metaarch.sync.PublicApiSecurity).administrator() || @inventoryAccess.locationAdmin(authentication) || @inventoryAccess.storeManager(authentication)")
  @PostMapping("/api/v1/inventories/{id}/stock/decrease") InventoryResponse decrease(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest r, org.springframework.security.core.Authentication auth) { access.requireStore(auth,inventories.find(id).getStoreId()); return stock.decrease(id, r); }
  @org.springframework.security.access.prepost.PreAuthorize("T(com.metaarch.sync.PublicApiSecurity).administrator() || @inventoryAccess.locationAdmin(authentication) || @inventoryAccess.storeManager(authentication)")
  @PostMapping("/api/v1/inventories/{id}/stock/reserve") InventoryResponse reserve(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest r, org.springframework.security.core.Authentication auth) { access.requireStore(auth,inventories.find(id).getStoreId()); return stock.reserve(id, r); }
  @org.springframework.security.access.prepost.PreAuthorize("T(com.metaarch.sync.PublicApiSecurity).administrator() || @inventoryAccess.locationAdmin(authentication) || @inventoryAccess.storeManager(authentication)")
  @PostMapping("/api/v1/inventories/{id}/stock/release") InventoryResponse release(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest r, org.springframework.security.core.Authentication auth) { access.requireStore(auth,inventories.find(id).getStoreId()); return stock.release(id, r); }
  @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
  @GetMapping("/api/v1/inventories/{id}/movements") PageResponse<StockMovementResponse> movements(@PathVariable UUID id, @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable, org.springframework.security.core.Authentication auth) { access.requireStore(auth,inventories.find(id).getStoreId()); return movements.list(id, pageable); }
}
