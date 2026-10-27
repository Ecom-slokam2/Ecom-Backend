package com.metaarch.catalog.product;
import java.util.*;
import com.metaarch.catalog.category.Category; import com.metaarch.catalog.common.AuditedEntity; import com.metaarch.catalog.image.ProductImage; import com.metaarch.catalog.variant.ProductVariant; import jakarta.persistence.*;
@Entity @Table(name="products",uniqueConstraints={@UniqueConstraint(name="uk_products_slug",columnNames="slug"),@UniqueConstraint(name="uk_products_sku",columnNames="sku")})
public class Product extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="category_id",nullable=false) private Category category;
 @Column(length=80) private String subcategory = "Other";
 @Column(nullable=false,length=180) private String name; @Column(nullable=false,length=200) private String slug; @Column(nullable=false,length=100) private String sku;
 @Column(columnDefinition="TEXT") private String description; @Column(length=120) private String brand;
 @Column(columnDefinition="TEXT") private String ingredients; @Column(columnDefinition="TEXT") private String specifications;
 @Column(length=80) private String pack; @Column(length=80) private String type; @Column(name="shelf_life", length=80) private String shelfLife;
 @Column(columnDefinition="TEXT") private String tags; @Column(columnDefinition="TEXT") private String highlights; @Column(name="about_source", columnDefinition="TEXT") private String aboutSource;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ProductStatus status=ProductStatus.DRAFT;
 @Column(nullable=false) private boolean featured=false; @Column(nullable=false) private boolean special=false;
 @Column(name="new_arrival",nullable=false) private boolean newArrival=false; @Column(name="best_seller",nullable=false) private boolean bestSeller=false;
 @Column(name="global_product",nullable=false) private boolean globalProduct=false; @Column(name="fulfillment_type",length=40) private String fulfillmentType; @Column(name="delivery_method",length=80) private String deliveryMethod;
 @Column(name="owner_location",length=160) private String ownerLocation;
 @Column(name="owner_store_id",length=100) private String ownerStoreId;
 @OneToMany(mappedBy="product",fetch=FetchType.LAZY) private List<ProductVariant> variants=new ArrayList<>();
 @OneToMany(mappedBy="product",fetch=FetchType.LAZY) private List<ProductImage> images=new ArrayList<>();
 protected Product(){} public Product(Category c,String n,String s,String k){category=c;name=n;slug=s;sku=k;}
 public UUID getId(){return id;} public Category getCategory(){return category;} public void setCategory(Category v){category=v;} public String getName(){return name;} public void setName(String v){name=v;}
 public String getSubcategory(){return subcategory;} public void setSubcategory(String v){subcategory=v;}
 public String getSlug(){return slug;} public void setSlug(String v){slug=v;} public String getSku(){return sku;} public void setSku(String v){sku=v;} public String getDescription(){return description;}
 public void setDescription(String v){description=v;} public String getBrand(){return brand;} public void setBrand(String v){brand=v;} public ProductStatus getStatus(){return status;} public void setStatus(ProductStatus v){status=v;}
 public String getIngredients(){return ingredients;} public void setIngredients(String v){ingredients=v;} public String getSpecifications(){return specifications;} public void setSpecifications(String v){specifications=v;}
 public String getPack(){return pack;} public void setPack(String v){pack=v;} public String getType(){return type;} public void setType(String v){type=v;} public String getShelfLife(){return shelfLife;} public void setShelfLife(String v){shelfLife=v;}
 public String getTags(){return tags;} public void setTags(String v){tags=v;} public String getHighlights(){return highlights;} public void setHighlights(String v){highlights=v;} public String getAboutSource(){return aboutSource;} public void setAboutSource(String v){aboutSource=v;}
 public boolean isFeatured(){return featured;} public void setFeatured(boolean v){featured=v;} public boolean isSpecial(){return special;} public void setSpecial(boolean v){special=v;}
 public boolean isNewArrival(){return newArrival;} public void setNewArrival(boolean v){newArrival=v;} public boolean isBestSeller(){return bestSeller;} public void setBestSeller(boolean v){bestSeller=v;}
 public boolean isGlobalProduct(){return globalProduct;} public void setGlobalProduct(boolean v){globalProduct=v;} public String getFulfillmentType(){return fulfillmentType;} public void setFulfillmentType(String v){fulfillmentType=v;} public String getDeliveryMethod(){return deliveryMethod;} public void setDeliveryMethod(String v){deliveryMethod=v;}
 public String getOwnerLocation(){return ownerLocation;} public void setOwnerLocation(String v){ownerLocation=v;}
 public String getOwnerStoreId(){return ownerStoreId;} public void setOwnerStoreId(String v){ownerStoreId=v;}
 public List<ProductVariant> getVariants(){return Collections.unmodifiableList(variants);} public List<ProductImage> getImages(){return Collections.unmodifiableList(images);}
}
