package com.metaarch.catalog.storeproduct;
import java.net.URI; import java.util.*; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.security.access.AccessDeniedException; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/store-products") public class StoreProductController {
 private final StoreProductService service; private final com.metaarch.catalog.config.CatalogAccess access;
 public StoreProductController(StoreProductService s,com.metaarch.catalog.config.CatalogAccess access){service=s;this.access=access;}
 @PostMapping ResponseEntity<StoreProductResponse> create(@Valid @RequestBody StoreProductRequest r,Authentication auth){access.requireStore(auth,r.storeId());StoreProductResponse v=service.create(r);return ResponseEntity.created(URI.create("/api/v1/store-products/"+v.id())).body(v);}
 @GetMapping List<StoreProductResponse> list(@RequestParam(required=false) String storeId,@RequestParam(required=false) UUID productId,Authentication auth){
  if(access.globalAdmin(auth)){if(storeId!=null&&!storeId.isBlank())access.requireStore(auth,storeId);return service.list("global-store",productId);}
  if(auth==null||!auth.isAuthenticated()||access.global(auth))return service.list(storeId,productId);
  if(storeId!=null){access.requireStore(auth,storeId);return service.list(storeId,productId);}
  return service.list(null,productId).stream().filter(row->access.canManageStore(auth,row.storeId())).toList();
 }
 @GetMapping("/{id}") @org.springframework.security.access.prepost.PreAuthorize("@catalogAccess.canManageStore(authentication, @storeProductService.get(#id).storeId()) || @catalogAccess.platformAdmin(authentication)") StoreProductResponse get(@PathVariable UUID id){return service.get(id);}
 @PutMapping("/{id}") StoreProductResponse update(@PathVariable UUID id,@Valid @RequestBody StoreProductRequest r,Authentication auth){access.requireStore(auth,service.get(id).storeId());access.requireStore(auth,r.storeId());return service.update(id,r);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id,Authentication auth){access.requireStore(auth,service.get(id).storeId());service.delete(id);}
}
