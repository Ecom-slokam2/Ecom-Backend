package com.metaarch.catalog.product;
import java.util.UUID; import java.util.*; import com.metaarch.catalog.category.CategoryService; import com.metaarch.catalog.common.PageResponse; import com.metaarch.catalog.config.CatalogAccess; import com.metaarch.catalog.exception.*; import com.metaarch.catalog.image.ProductImageRepository; import com.metaarch.catalog.storeproduct.StoreProduct; import com.metaarch.catalog.storeproduct.StoreProductRepository; import com.metaarch.catalog.store.Store; import com.metaarch.catalog.store.StoreRepository; import com.metaarch.catalog.variant.ProductVariantRepository; import org.springframework.data.domain.*; import org.springframework.security.access.AccessDeniedException; import org.springframework.security.core.Authentication; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class ProductService {
 private final ProductRepository products; private final CategoryService categories; private final ProductVariantRepository variants; private final ProductImageRepository images; private final StoreProductRepository storeProducts;
 private final StoreRepository stores; private final CatalogAccess access;
 public ProductService(ProductRepository p,CategoryService c,ProductVariantRepository v,ProductImageRepository i,StoreProductRepository s,StoreRepository stores,CatalogAccess access){products=p;categories=c;variants=v;images=i;storeProducts=s;this.stores=stores;this.access=access;}
 @Transactional public ProductResponse create(ProductRequest r){unique(r.slug(),r.sku(),null);Product p=new Product(categories.find(r.categoryId()),clean(r.name()),clean(r.slug()),clean(r.sku()));apply(p,r);return ProductResponse.from(products.save(p));}
 @Transactional(readOnly=true) public ProductResponse get(UUID id){return ProductResponse.from(find(id));}
 @Transactional(readOnly=true) public PageResponse<ProductResponse> list(UUID categoryId,ProductStatus status,String search,Boolean globalProduct,Pageable pageable){String term=search==null||search.isBlank()?null:search.trim();return PageResponse.from(products.search(categoryId,status,term,globalProduct,pageable),ProductResponse::from);}
 @Transactional(readOnly=true) public PageResponse<ProductResponse> listForLocation(Authentication auth,Pageable pageable){
  return listForLocation(auth,null,null,null,pageable);
 }
 @Transactional(readOnly=true) public PageResponse<ProductResponse> listForLocation(Authentication auth,UUID categoryId,ProductStatus status,String search,Pageable pageable){
  String location=requiredLocation(auth);
  Set<String> storeIds=stores.findAll().stream().filter(store->access.inLocation(auth,store.getLocation())).map(Store::getId).collect(java.util.stream.Collectors.toSet());
  Set<String> queryStores=storeIds.isEmpty()?Set.of("__no_assigned_store__"):storeIds;
  String term=search==null||search.isBlank()?null:search.trim();
  return PageResponse.from(products.searchVisibleInLocation(location,queryStores,categoryId,status,term,pageable),product->{
   boolean available=storeProducts.findByProductId(product.getId()).stream().anyMatch(mapping->storeIds.contains(mapping.getStoreId())&&mapping.isAvailable());
   return ProductResponse.from(product,available);
  });
 }
 @Transactional(readOnly=true) public PageResponse<ProductResponse> listForStore(String storeId,UUID categoryId,ProductStatus status,String search,Pageable pageable){
  String term=search==null||search.isBlank()?null:search.trim();
  Map<UUID,Boolean> availability=new HashMap<>();
  storeProducts.findByStoreId(storeId).forEach(mapping->availability.put(mapping.getProduct().getId(),mapping.isAvailable()));
  Pageable stablePage=PageRequest.of(pageable.getPageNumber(),pageable.getPageSize());
  return PageResponse.from(products.searchMappedToStore(storeId,categoryId,status,term,stablePage),product->ProductResponse.from(product,availability.get(product.getId())));
 }
 @Transactional public ProductResponse createForLocation(ProductRequest request,List<String> requestedStores,Boolean available,Authentication auth){
  String location=requiredLocation(auth); Set<String> selected=validateLocationStores(auth,requestedStores);
  unique(request.slug(),request.sku(),null);
  Product product=new Product(categories.find(request.categoryId()),clean(request.name()),clean(request.slug()),clean(request.sku()));
  apply(product,request);
  product.setOwnerLocation(location); product.setGlobalProduct(false); product.setFulfillmentType(null); product.setDeliveryMethod(null);
  product.setStatus(ProductStatus.ACTIVE); product.setFeatured(false); product.setSpecial(false); product.setNewArrival(false); product.setBestSeller(false);
  products.save(product); replaceLocationMappings(product,selected,Boolean.TRUE.equals(available));
  return ProductResponse.from(product,Boolean.TRUE.equals(available));
 }
 @Transactional(readOnly=true) public PageResponse<ProductResponse> listForStoreManager(Authentication auth,UUID categoryId,ProductStatus status,String search,Pageable pageable){
  String storeId=requiredStoreManagerStore(auth); requireAssignedStore(auth,storeId);
  String term=search==null||search.isBlank()?null:search.trim();
  Map<UUID,Boolean> availability=new HashMap<>();
  storeProducts.findByStoreId(storeId).forEach(mapping->availability.put(mapping.getProduct().getId(),mapping.isAvailable()));
  return PageResponse.from(products.searchOwnedByStore(storeId,categoryId,status,term,pageable),product->ProductResponse.from(product,availability.get(product.getId())));
 }
 @Transactional public ProductResponse createForStoreManager(ProductRequest request,Boolean available,Authentication auth){
  String storeId=requiredStoreManagerStore(auth); Store store=requireAssignedStore(auth,storeId);
  unique(request.slug(),request.sku(),null);
  Product product=new Product(categories.find(request.categoryId()),clean(request.name()),clean(request.slug()),clean(request.sku()));
  apply(product,request); product.setOwnerStoreId(storeId); product.setOwnerLocation(store.getLocation()); product.setStatus(ProductStatus.DRAFT);
  product.setGlobalProduct(false); product.setFeatured(false); product.setSpecial(false); product.setNewArrival(false); product.setBestSeller(false);
  product.setFulfillmentType(null); product.setDeliveryMethod(null);
  products.save(product);
  StoreProduct mapping=new StoreProduct(storeId,product); mapping.setStoreName(store.getName()); mapping.setAvailable(Boolean.TRUE.equals(available)); storeProducts.save(mapping);
  return ProductResponse.from(product,Boolean.TRUE.equals(available));
 }
 @Transactional public ProductResponse updateForStoreManager(UUID id,ProductRequest request,Boolean available,Authentication auth){
  requireManageStoreManagerProduct(auth,id); String storeId=requiredStoreManagerStore(auth); Store store=requireAssignedStore(auth,storeId);
  Product product=find(id); unique(request.slug(),request.sku(),id); product.setCategory(categories.find(request.categoryId()));
  if(request.status()==ProductStatus.ACTIVE && variants.findByProductIdOrderByNameAsc(id).stream().noneMatch(com.metaarch.catalog.variant.ProductVariant::isActive))
   throw new IllegalArgumentException("Add at least one active product variant before publishing this product.");
  product.setSlug(clean(request.slug())); product.setSku(clean(request.sku())); apply(product,request);
  product.setOwnerStoreId(storeId); product.setOwnerLocation(store.getLocation()); product.setGlobalProduct(false);
  product.setFeatured(false); product.setSpecial(false); product.setNewArrival(false); product.setBestSeller(false);
  product.setFulfillmentType(null); product.setDeliveryMethod(null);
  List<StoreProduct> existing=storeProducts.findByProductId(id);
  if(existing.stream().anyMatch(mapping->!storeId.equals(mapping.getStoreId()))) throw new AccessDeniedException("Product has assignments outside your store.");
  StoreProduct mapping=existing.stream().findFirst().orElseGet(()->new StoreProduct(storeId,product));
  mapping.setStoreName(store.getName()); mapping.setAvailable(Boolean.TRUE.equals(available)); storeProducts.save(mapping);
  return ProductResponse.from(product,Boolean.TRUE.equals(available));
 }
 @Transactional public ProductResponse setStoreManagerProductAvailability(UUID id,boolean available,Authentication auth){
  requireManageStoreManagerProduct(auth,id); String storeId=requiredStoreManagerStore(auth);
  StoreProduct mapping=storeProducts.findByProductId(id).stream().filter(item->storeId.equals(item.getStoreId())).findFirst()
      .orElseThrow(()->new ResourceNotFoundException("Product assignment not found for your store."));
  mapping.setAvailable(available); return ProductResponse.from(find(id),available);
 }
 @Transactional public void deleteForStoreManager(UUID id,Authentication auth){
  requireManageStoreManagerProduct(auth,id); String storeId=requiredStoreManagerStore(auth);
  List<StoreProduct> mappings=storeProducts.findByProductId(id);
  if(mappings.stream().anyMatch(mapping->!storeId.equals(mapping.getStoreId()))) throw new AccessDeniedException("Product has assignments outside your store.");
  Product product=find(id); storeProducts.deleteByProductId(id); images.deleteByProductId(id); variants.deleteByProductId(id); products.delete(product);
 }
 @Transactional public ProductResponse updateForLocation(UUID id,ProductRequest request,List<String> requestedStores,Boolean available,Authentication auth){
  requireManageLocationProduct(auth,id); String location=requiredLocation(auth); Set<String> selected=validateLocationStores(auth,requestedStores);
  Product product=find(id); ProductStatus publicationStatus=product.getStatus();
  boolean globalProduct=product.isGlobalProduct(),featured=product.isFeatured(),special=product.isSpecial(),newArrival=product.isNewArrival(),bestSeller=product.isBestSeller();
  String fulfillmentType=product.getFulfillmentType(),deliveryMethod=product.getDeliveryMethod();
  unique(request.slug(),request.sku(),id); product.setCategory(categories.find(request.categoryId())); product.setSlug(clean(request.slug())); product.setSku(clean(request.sku())); apply(product,request);
  product.setOwnerLocation(location); product.setStatus(publicationStatus); product.setGlobalProduct(globalProduct); product.setFeatured(featured); product.setSpecial(special); product.setNewArrival(newArrival); product.setBestSeller(bestSeller); product.setFulfillmentType(fulfillmentType); product.setDeliveryMethod(deliveryMethod);
  replaceLocationMappings(product,selected,Boolean.TRUE.equals(available));
  return ProductResponse.from(product,Boolean.TRUE.equals(available));
 }
 @Transactional public ProductResponse setLocationAvailability(UUID id,boolean available,Authentication auth){
  requireManageLocationProduct(auth,id); String location=requiredLocation(auth); Product product=find(id);
  List<StoreProduct> mappings=storeProducts.findByProductId(id);
  if(mappings.isEmpty())throw new ResourceNotFoundException("Product has no store assignments in this location.");
  for(StoreProduct mapping:mappings){
   Store store=stores.findById(mapping.getStoreId()).orElseThrow(()->new AccessDeniedException("Product assignment references an unknown store."));
   if(!access.inLocation(auth,store.getLocation()))throw new AccessDeniedException("Product assignment is outside your assigned location.");
   mapping.setAvailable(available);
  }
  return ProductResponse.from(product,available);
 }
 @Transactional public void deleteForLocation(UUID id,Authentication auth){
  requireManageLocationProduct(auth,id);
  Product product=find(id);
  if(!requiredLocation(auth).equalsIgnoreCase(product.getOwnerLocation()))throw new AccessDeniedException("Product is outside your assigned location.");
  storeProducts.deleteByProductId(id);images.deleteByProductId(id);variants.deleteByProductId(id);products.delete(product);
 }
 @Transactional public ProductResponse update(UUID id,ProductRequest r){Product p=find(id);unique(r.slug(),r.sku(),id);p.setCategory(categories.find(r.categoryId()));p.setSlug(clean(r.slug()));p.setSku(clean(r.sku()));apply(p,r);return ProductResponse.from(p);}
 @Transactional public void delete(UUID id){Product p=find(id);storeProducts.deleteByProductId(id);images.deleteByProductId(id);variants.deleteByProductId(id);products.delete(p);}
 public Product find(UUID id){return products.findById(id).orElseThrow(()->new ResourceNotFoundException("Product not found: "+id));}
 private String requiredLocation(Authentication auth){String location=access.location(auth);if(!access.locationAdmin(auth)||location==null)throw new AccessDeniedException("A verified Location Admin location is required.");return location;}
 private String requiredStoreManagerStore(Authentication auth){String storeId=access.assignedStoreId(auth);if(storeId==null)throw new AccessDeniedException("A single verified Store Manager store assignment is required.");return storeId;}
 private Store requireAssignedStore(Authentication auth,String storeId){access.requireStore(auth,storeId);return stores.findById(storeId).orElseThrow(()->new ResourceNotFoundException("Assigned store not found: "+storeId));}
 private void requireManageStoreManagerProduct(Authentication auth,UUID id){if(!access.storeManager(auth)||!access.canManageProduct(auth,id))throw new AccessDeniedException("Product is outside your assigned store.");}
 private void requireManageLocationProduct(Authentication auth,UUID id){if(!access.canManageProduct(auth,id))throw new AccessDeniedException("Product is outside your assigned location or is a shared catalog product.");}
 private Set<String> validateLocationStores(Authentication auth,List<String> requestedStores){
  if(requestedStores==null||requestedStores.isEmpty())throw new IllegalArgumentException("Select at least one store in your assigned location.");
  Set<String> selected=new LinkedHashSet<>();
  for(String value:requestedStores){String storeId=value==null?"":value.trim();if(storeId.isBlank()||!selected.add(storeId))throw new IllegalArgumentException("Store selections must be valid and unique.");access.requireStore(auth,storeId);if("global-store".equalsIgnoreCase(storeId))throw new AccessDeniedException("Location products cannot be assigned to the Global Store.");}
  return selected;
 }
 private void replaceLocationMappings(Product product,Set<String> selected,boolean available){
  List<StoreProduct> existing=storeProducts.findByProductId(product.getId());
  for(StoreProduct mapping:existing){Store store=stores.findById(mapping.getStoreId()).orElseThrow(()->new AccessDeniedException("Product assignment references an unknown store."));if(!product.getOwnerLocation().equalsIgnoreCase(store.getLocation()))throw new AccessDeniedException("Product already has an assignment outside its owning location.");}
  storeProducts.deleteAll(existing);storeProducts.flush();
  for(String storeId:selected){Store store=stores.findById(storeId).orElseThrow(()->new ResourceNotFoundException("Store not found: "+storeId));StoreProduct mapping=new StoreProduct(storeId,product);mapping.setStoreName(store.getName());mapping.setAvailable(available);storeProducts.save(mapping);}
 }
 private void apply(Product p,ProductRequest r){p.setName(clean(r.name()));p.setSubcategory(blankOrDefault(r.subcategory(),"Other"));p.setDescription(blank(r.description()));p.setBrand(blank(r.brand()));p.setIngredients(blank(r.ingredients()));p.setSpecifications(blank(r.specifications()));p.setPack(blank(r.pack()));p.setType(blank(r.type()));p.setShelfLife(blank(r.shelfLife()));p.setTags(blank(r.tags()));p.setHighlights(blank(r.highlights()));p.setAboutSource(blank(r.aboutSource()));p.setStatus(r.status());p.setFeatured(Boolean.TRUE.equals(r.featured()));p.setSpecial(Boolean.TRUE.equals(r.special()));p.setNewArrival(Boolean.TRUE.equals(r.newArrival()));p.setBestSeller(Boolean.TRUE.equals(r.bestSeller()));p.setGlobalProduct(Boolean.TRUE.equals(r.globalProduct()));p.setFulfillmentType(Boolean.TRUE.equals(r.globalProduct())?blankOrDefault(r.fulfillmentType(),"global"):blank(r.fulfillmentType()));p.setDeliveryMethod(Boolean.TRUE.equals(r.globalProduct())?blankOrDefault(r.deliveryMethod(),"global"):blank(r.deliveryMethod()));}
 private void unique(String slug,String sku,UUID id){
  boolean ds=id==null?products.existsBySlugIgnoreCase(clean(slug)):products.existsBySlugIgnoreCaseAndIdNot(clean(slug),id);
  boolean dk=id==null?products.existsBySkuIgnoreCase(clean(sku)):products.existsBySkuIgnoreCaseAndIdNot(clean(sku),id);
  if(ds) throw new ConflictException("Product slug already exists");
  if(dk) throw new ConflictException("Product SKU already exists");
 }
 private String clean(String v){return v.trim();} private String blank(String v){return v==null||v.isBlank()?null:v.trim();} private String blankOrDefault(String v,String fallback){String clean=blank(v);return clean==null?fallback:clean;}
}
