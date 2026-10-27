package com.metaarch.catalog.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaarch.catalog.store.Store;
import com.metaarch.catalog.store.StoreRepository;
import com.metaarch.catalog.storeproduct.StoreProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.jwt.secret=local-isolated-test-secret-that-is-long-enough-for-hmac-sha256")
@AutoConfigureMockMvc
@Transactional
class LocationProductAuthorizationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired StoreRepository stores;
  @Autowired StoreProductRepository mappings;

  @BeforeEach
  void stores() {
    stores.save(store("la-product-hyd-1", "Hyderabad"));
    stores.save(store("la-product-hyd-2", "Hyderabad"));
    stores.save(store("la-product-chn-1", "Chennai"));
  }

  @Test
  void locationAdminCreatesEditsAndChangesOnlyLocationOwnedProductAvailability() throws Exception {
    String categoryId = createCategory("la-product-category");
    String payload = locationProduct(categoryId, "Location Product Original", "location-product-original",
        "LA-LOCAL-PRODUCT", "ACTIVE", "la-product-hyd-1", "la-product-hyd-2");
    String createdBody = mvc.perform(post("/api/v1/location-products").with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ownerLocation").value("hyderabad"))
        .andExpect(jsonPath("$.globalProduct").value(false))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andReturn().getResponse().getContentAsString();
    String productId = json.readTree(createdBody).get("id").asText();

    mvc.perform(get("/api/v1/location-products").with(hyderabadAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id=='" + productId + "')]").exists());
    String mappedBody = mvc.perform(post("/api/v1/products").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(product(categoryId, "Mapped Shared Product", "mapped-shared-product", "LA-MAPPED-SHARED", "ACTIVE")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String mappedId = json.readTree(mappedBody).get("id").asText();
    mvc.perform(post("/api/v1/store-products").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"storeId\":\"la-product-hyd-1\",\"productId\":\"" + mappedId + "\",\"available\":true}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/v1/products/{id}", mappedId).with(hyderabadAdmin())).andExpect(status().isOk());
    String globalBody = mvc.perform(post("/api/v1/products").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(product(categoryId, "Unmapped Shared Product", "unmapped-shared-product", "LA-UNMAPPED-SHARED", "ACTIVE")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String globalId = json.readTree(globalBody).get("id").asText();
    mvc.perform(get("/api/v1/products").with(hyderabadAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id=='" + productId + "')]").exists())
        .andExpect(jsonPath("$.content[?(@.id=='" + mappedId + "')]").exists())
        .andExpect(jsonPath("$.content[?(@.id=='" + globalId + "')]").doesNotExist());
    mvc.perform(get("/api/v1/categories/{id}/products", categoryId).with(hyderabadAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id=='" + globalId + "')]").doesNotExist());
    mvc.perform(get("/api/v1/products/{id}", globalId).with(hyderabadAdmin())).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/products/{id}", productId).with(hyderabadAdmin())).andExpect(status().isOk());

    String updated = locationProduct(categoryId, "Location Product Updated", "location-product-updated",
        "LA-LOCAL-PRODUCT", "INACTIVE", "la-product-hyd-1");
    mvc.perform(put("/api/v1/location-products/{id}", productId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(updated))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Location Product Updated"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.globalProduct").value(false));
    mvc.perform(patch("/api/v1/location-products/{id}/availability", productId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.locationAvailable").value(false))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    org.assertj.core.api.Assertions.assertThat(mappings.findByProductId(java.util.UUID.fromString(productId)))
        .hasSize(1).allSatisfy(mapping -> org.assertj.core.api.Assertions.assertThat(mapping.isAvailable()).isFalse());
    mvc.perform(get("/api/v1/location-products").with(hyderabadAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id=='" + productId + "')].locationAvailable").value(false));
    mvc.perform(patch("/api/v1/location-products/{id}/availability", productId)
        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(patch("/api/v1/location-products/{id}/availability", productId).with(chennaiAdmin())
        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}"))
        .andExpect(status().isForbidden());
    mvc.perform(patch("/api/v1/location-products/{id}/availability", productId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":null}"))
        .andExpect(status().isBadRequest());
    mvc.perform(patch("/api/v1/location-products/{id}/availability", productId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.locationAvailable").value(true))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    org.assertj.core.api.Assertions.assertThat(mappings.findByProductId(java.util.UUID.fromString(productId)))
        .hasSize(1).allSatisfy(mapping -> org.assertj.core.api.Assertions.assertThat(mapping.isAvailable()).isTrue());

    mvc.perform(delete("/api/v1/location-products/{id}", productId).with(chennaiAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/location-products/{id}", productId))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/location-products/{id}", productId).with(globalAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/location-products/{id}", productId).with(hyderabadAdmin()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/location-products").with(hyderabadAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id=='" + productId + "')]").doesNotExist());
  }

  @Test
  void locationAdminCanManageVariantsAndImagesForOwnedProductButNotSharedProducts() throws Exception {
    String categoryId = createCategory("la-variant-category");
    String local = mvc.perform(post("/api/v1/location-products").with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(locationProduct(categoryId, "Owned Variant Product",
            "owned-variant-product", "LA-OWNED-VARIANT", "ACTIVE", "la-product-hyd-1")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String localId = json.readTree(local).get("id").asText();
    String variantBody = mvc.perform(post("/api/v1/products/{id}/variants", localId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(variant("LA-OWNED-VARIANT-1")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String variantId = json.readTree(variantBody).get("id").asText();
    String imageBody = mvc.perform(post("/api/v1/products/{id}/images", localId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(image()))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String imageId = json.readTree(imageBody).get("id").asText();
    mvc.perform(post("/api/v1/products/{id}/variants", localId)
        .contentType(MediaType.APPLICATION_JSON).content(variant("LA-ANONYMOUS-VARIANT")))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/products/{id}/images", localId)
        .contentType(MediaType.APPLICATION_JSON).content(image()))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/products/{id}/images", localId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"url\":\"/src/assets/images/logo.png\",\"altText\":\"Invalid local path\",\"displayOrder\":0,\"primaryImage\":true}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/products/{id}/variants", localId).with(hyderabadAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].sku").value("LA-OWNED-VARIANT-1"));
    mvc.perform(get("/api/v1/variants/{id}", variantId).with(hyderabadAdmin())).andExpect(status().isOk());
    mvc.perform(get("/api/v1/products/{id}/images", localId).with(hyderabadAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].url").value("https://example.invalid/location-product-qa.png"));
    mvc.perform(get("/api/v1/images/{id}", imageId).with(hyderabadAdmin())).andExpect(status().isOk());
    mvc.perform(put("/api/v1/variants/{id}", variantId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(variant("LA-OWNED-VARIANT-1")))
        .andExpect(status().isOk());
    mvc.perform(put("/api/v1/images/{id}", imageId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(image()))
        .andExpect(status().isOk());

    String shared = mvc.perform(post("/api/v1/products").with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(product(categoryId, "Shared Product",
            "shared-product", "LA-SHARED-PRODUCT", "ACTIVE")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String sharedId = json.readTree(shared).get("id").asText();
    mvc.perform(post("/api/v1/products/{id}/variants", sharedId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(variant("LA-SHARED-VARIANT")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/products/{id}/images", sharedId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(image()))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/variants/{id}", variantId).with(chennaiAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(variant("LA-CROSS-LOCATION-VARIANT")))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/images/{id}", imageId).with(chennaiAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(image()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/variants/{id}", variantId).with(chennaiAdmin())).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/images/{id}", imageId).with(chennaiAdmin())).andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/variants/{id}", variantId).with(hyderabadAdmin()))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/v1/images/{id}", imageId).with(hyderabadAdmin()))
        .andExpect(status().isNoContent());
  }

  @Test
  void locationAdminCanCreateReadAndRemoveOnlyAssignedLocationProductMappings() throws Exception {
    String categoryId = createCategory("la-mapping-category");
    String local = mvc.perform(post("/api/v1/location-products").with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(locationProduct(categoryId, "Owned Mapping Product",
            "owned-mapping-product", "LA-OWNED-MAPPING", "ACTIVE", "la-product-hyd-1")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String productId = json.readTree(local).get("id").asText();

    mvc.perform(get("/api/v1/store-products").param("productId", productId).with(hyderabadAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].storeId").value("la-product-hyd-1"));
    String mappingBody = "{\"storeId\":\"la-product-hyd-2\",\"productId\":\"" + productId
        + "\",\"available\":true}";
    String createdMapping = mvc.perform(post("/api/v1/store-products").with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(mappingBody))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.storeId").value("la-product-hyd-2"))
        .andReturn().getResponse().getContentAsString();
    String mappingId = json.readTree(createdMapping).get("id").asText();
    mvc.perform(put("/api/v1/store-products/{id}", mappingId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"storeId\":\"la-product-hyd-2\",\"productId\":\"" + productId + "\",\"available\":false}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));

    mvc.perform(get("/api/v1/store-products").param("productId", productId).with(hyderabadAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.storeId=='la-product-hyd-1')]").exists())
        .andExpect(jsonPath("$[?(@.storeId=='la-product-hyd-2' && @.available==false)]").exists())
        .andExpect(jsonPath("$[?(@.storeId=='la-product-chn-1')]").doesNotExist());
    mvc.perform(post("/api/v1/store-products").with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"storeId\":\"la-product-chn-1\",\"productId\":\"" + productId + "\",\"available\":true}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/store-products").contentType(MediaType.APPLICATION_JSON).content(mappingBody))
        .andExpect(status().isUnauthorized());
    mvc.perform(put("/api/v1/store-products/{id}", mappingId).with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"storeId\":\"la-product-chn-1\",\"productId\":\"" + productId + "\",\"available\":false}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/store-products/{id}", mappingId).with(chennaiAdmin()))
        .andExpect(status().isForbidden());

    mvc.perform(delete("/api/v1/store-products/{id}", mappingId).with(hyderabadAdmin()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/store-products").param("productId", productId).with(hyderabadAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].storeId").value("la-product-hyd-1"))
        .andExpect(jsonPath("$[1]").doesNotExist());
  }

  @Test
  void locationAdminCannotCreateForAnotherLocationOrUseGlobalRoutes() throws Exception {
    String categoryId = createCategory("la-cross-category");
    mvc.perform(get("/api/v1/location-products")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/location-products").with(globalAdmin())).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/location-products").with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(locationProduct(categoryId, "Cross Location Product", "cross-location-product",
            "LA-CROSS-PRODUCT", "ACTIVE", "la-product-chn-1")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/location-products").with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(locationProduct(categoryId, "Wrong Role Product", "wrong-role-product",
            "LA-WRONG-ROLE", "ACTIVE", "la-product-hyd-1")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/location-products")
        .contentType(MediaType.APPLICATION_JSON)
        .content(locationProduct(categoryId, "Anonymous Product", "anonymous-product",
            "LA-ANONYMOUS", "ACTIVE", "la-product-hyd-1")))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/products").with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(product(categoryId, "Global Route Product", "global-route-product",
            "LA-GLOBAL-ROUTE", "ACTIVE")))
        .andExpect(status().isForbidden());
  }

  @Test
  void globalAdministratorsCanStillCreateGlobalProductsAndLocationProductsCannotBeMappedAcrossLocations() throws Exception {
    String categoryId = createCategory("la-global-compat-category");
    String local = mvc.perform(post("/api/v1/location-products").with(hyderabadAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(locationProduct(categoryId, "Owned Compatibility Product",
            "owned-compatibility-product", "LA-OWNED-COMPAT", "ACTIVE", "la-product-hyd-1")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String localId = json.readTree(local).get("id").asText();
    mvc.perform(post("/api/v1/store-products").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"storeId\":\"la-product-chn-1\",\"productId\":\"" + localId + "\",\"available\":true}"))
        .andExpect(status().isForbidden());

    mvc.perform(post("/api/v1/products").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(product(categoryId, "Global Compatibility Product", "global-compatibility-product",
            "LA-GLOBAL-COMPAT", "ACTIVE")))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerLocation").doesNotExist());
  }

  private String createCategory(String slug) throws Exception {
    String response = mvc.perform(post("/api/v1/categories").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"" + slug + "\",\"slug\":\"" + slug + "\",\"active\":true}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    JsonNode node = json.readTree(response);
    return node.get("id").asText();
  }

  private Store store(String id, String location) {
    Store store = new Store(id); store.setName(id); store.setLocation(location); store.setAddress("Isolated test store");
    store.setLatitude(new BigDecimal("17.4000000")); store.setLongitude(new BigDecimal("78.4000000"));
    return store;
  }

  private String locationProduct(String categoryId, String name, String slug, String sku, String status, String... storeIds) {
    String ids = java.util.Arrays.stream(storeIds).map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
    return "{\"product\":" + product(categoryId, name, slug, sku, status) + ",\"storeIds\":[" + ids + "],\"available\":true,\"location\":\"Chennai\"}";
  }

  private String product(String categoryId, String name, String slug, String sku, String status) {
    return "{\"categoryId\":\"" + categoryId + "\",\"name\":\"" + name + "\",\"slug\":\"" + slug
        + "\",\"sku\":\"" + sku + "\",\"description\":\"Isolated location product test\","
        + "\"status\":\"" + status + "\",\"globalProduct\":true}";
  }

  private String variant(String sku) {
    return "{\"sku\":\"" + sku + "\",\"name\":\"Disposable variant\",\"attributes\":\"{}\","
        + "\"price\":10.00,\"compareAtPrice\":12.00,\"active\":true}";
  }

  private String image() {
    return "{\"url\":\"https://example.invalid/location-product-qa.png\",\"altText\":\"Disposable image\","
        + "\"displayOrder\":0,\"primaryImage\":true}";
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor hyderabadAdmin() {
    return jwt().authorities(() -> "ROLE_LOCATION_ADMIN", () -> "SCOPE_LOCATION:hyderabad");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor globalAdmin() {
    return jwt().authorities(() -> "ROLE_GLOBAL_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor chennaiAdmin() {
    return jwt().authorities(() -> "ROLE_LOCATION_ADMIN", () -> "SCOPE_LOCATION:chennai");
  }
}
