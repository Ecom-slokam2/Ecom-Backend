package com.metaarch.catalog.storeproduct;
import java.util.*; import com.metaarch.catalog.exception.*; import com.metaarch.catalog.product.ProductService; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service("storeProductService") public class StoreProductService {
 private final StoreProductRepository mappings; private final ProductService products; private final com.metaarch.catalog.store.StoreRepository stores; public StoreProductService(StoreProductRepository m,ProductService p,com.metaarch.catalog.store.StoreRepository s){mappings=m;products=p;stores=s;}
 @Transactional public StoreProductResponse create(StoreProductRequest r){
  String store=clean(r.storeId());
  if(mappings.existsByStoreIdAndProductId(store,r.productId())) throw new ConflictException("Store-product mapping already exists");
  var product=products.find(r.productId());
  ensureGlobalEligibility(store,product);
  StoreProduct m=new StoreProduct(store,product);
  apply(m,r);
  return StoreProductResponse.from(mappings.save(m));
 }
 @Transactional(readOnly=true) public StoreProductResponse get(UUID id){return StoreProductResponse.from(find(id));}
 @Transactional(readOnly=true) public List<StoreProductResponse> list(String storeId,UUID productId){
  if(storeId!=null&&productId!=null) return mappings.findByStoreId(storeId).stream().filter(m->m.getProduct().getId().equals(productId)&&eligibleForStore(storeId,m.getProduct())).map(StoreProductResponse::from).toList();
  if(storeId!=null) return mappings.findByStoreId(storeId).stream().filter(m->eligibleForStore(storeId,m.getProduct())).map(StoreProductResponse::from).toList();
  if(productId!=null) return mappings.findByProductId(productId).stream().map(StoreProductResponse::from).toList();
  return mappings.findAll().stream().map(StoreProductResponse::from).toList();
 }
 @Transactional(readOnly=true) public List<StoreProductResponse> publicList(String storeId,UUID productId){
  Set<String> activeStoreIds=stores.findByActive(true).stream().map(com.metaarch.catalog.store.Store::getId).collect(java.util.stream.Collectors.toSet());
  return mappings.findAll().stream()
      .filter(m->m.isAvailable()&&("global-store".equalsIgnoreCase(m.getStoreId())
          ? m.getProduct().isGlobalProduct() : activeStoreIds.contains(m.getStoreId())))
      .filter(m->m.getProduct().getStatus()==com.metaarch.catalog.product.ProductStatus.ACTIVE)
      .filter(m->storeId==null||storeId.isBlank()||m.getStoreId().equalsIgnoreCase(storeId))
      .filter(m->productId==null||m.getProduct().getId().equals(productId))
      .map(StoreProductResponse::from).toList();
 }
 @Transactional public StoreProductResponse update(UUID id,StoreProductRequest r){
  StoreProduct m=find(id);
  String store=clean(r.storeId());
  if(mappings.existsByStoreIdAndProductIdAndIdNot(store,r.productId(),id)) throw new ConflictException("Store-product mapping already exists");
  var product=products.find(r.productId());
  ensureGlobalEligibility(store,product);
  m.setStoreId(store);
  m.setProduct(product);
  apply(m,r);
  return StoreProductResponse.from(m);
 }
 @Transactional public void delete(UUID id){mappings.delete(find(id));}
 private StoreProduct find(UUID id){return mappings.findById(id).orElseThrow(()->new ResourceNotFoundException("Store-product mapping not found: "+id));}
 private void apply(StoreProduct m,StoreProductRequest r){m.setStoreName(storeName(r.storeId(),r.storeName()));m.setAvailable(r.available());m.setPriceOverride(r.priceOverride());}
 private String clean(String v){return v.trim();}
 private boolean eligibleForStore(String storeId,com.metaarch.catalog.product.Product product){return !"global-store".equalsIgnoreCase(storeId)||product.isGlobalProduct();}
 private void ensureGlobalEligibility(String storeId,com.metaarch.catalog.product.Product product){
  if(!eligibleForStore(storeId,product))throw new ConflictException("Products must be enabled for global delivery before they can be mapped to the Global Store");
  if(product.getOwnerLocation()!=null){
   var store=stores.findById(storeId).orElseThrow(()->new ResourceNotFoundException("Store not found: "+storeId));
   if(!product.getOwnerLocation().equalsIgnoreCase(store.getLocation()))throw new org.springframework.security.access.AccessDeniedException("Location-owned products can only be mapped within their owning location.");
  }
 }
 private String storeName(String storeId,String storeName){
  String value=storeName==null?"":storeName.trim();
  if(!value.isBlank()) return value;
  return switch(clean(storeId)){case "store-gachibowli" -> "Thejo Gachibowli Store";case "store-hyd-001" -> "Thejo Hyderabad Central";case "store-1" -> "Thejo Sample Store 1";case "store-2" -> "Thejo Sample Store 2";default -> "Store "+clean(storeId);};
 }
}
