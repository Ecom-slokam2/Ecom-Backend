package com.metaarch.catalog.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaarch.catalog.category.Category;
import com.metaarch.catalog.category.CategoryRepository;
import com.metaarch.catalog.store.Store;
import com.metaarch.catalog.store.StoreRepository;
import com.metaarch.catalog.storeproduct.StoreProductRepository;
import com.metaarch.catalog.variant.ProductVariantRepository;
import com.metaarch.catalog.image.ProductImageRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
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
class StoreManagerProductCrudIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired CategoryRepository categories;
  @Autowired ProductRepository products;
  @Autowired StoreRepository stores;
  @Autowired StoreProductRepository mappings;
  @Autowired ProductVariantRepository variants;
  @Autowired ProductImageRepository images;

  @Test
  void storeManagerCanCreateEditAndDeleteProductsOnlyForTheAssignedStore() throws Exception {
    String suffix = UUID.randomUUID().toString();
    String assignedStoreId = "sm-product-store-" + suffix;
    String otherStoreId = "sm-product-other-" + suffix;
    createStore(assignedStoreId, "Hyderabad");
    createStore(otherStoreId, "Chennai");
    Category category = categories.saveAndFlush(new Category("QA " + suffix, "qa-" + suffix));
    var manager = jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:" + assignedStoreId);
    var otherManager = jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:" + otherStoreId);

    String createdBody = mvc.perform(post("/api/v1/store-manager-products").with(manager)
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(category.getId(), "Manager QA Product " + suffix, "manager-qa-" + suffix,
            "SM-" + suffix.substring(0, 8), "ACTIVE", true)))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.name").value("Manager QA Product " + suffix))
      .andExpect(jsonPath("$.globalProduct").value(false))
      .andExpect(jsonPath("$.locationAvailable").value(true))
      .andReturn().getResponse().getContentAsString();
    UUID productId = UUID.fromString(json.readTree(createdBody).get("id").asText());

    Product saved = products.findById(productId).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(assignedStoreId, saved.getOwnerStoreId());
    org.junit.jupiter.api.Assertions.assertEquals("Hyderabad", saved.getOwnerLocation());
    org.junit.jupiter.api.Assertions.assertEquals(ProductStatus.DRAFT, saved.getStatus());
    org.junit.jupiter.api.Assertions.assertEquals(1, mappings.findByProductId(productId).size());
    org.junit.jupiter.api.Assertions.assertEquals(assignedStoreId, mappings.findByProductId(productId).get(0).getStoreId());

    mvc.perform(put("/api/v1/store-manager-products/{id}", productId).with(manager)
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(category.getId(), "Manager QA Product " + suffix, "manager-qa-" + suffix,
            "SM-" + suffix.substring(0, 8), "ACTIVE", true)))
      .andExpect(status().isBadRequest());

    mvc.perform(get("/api/v1/store-manager-products").with(manager).param("storeId", otherStoreId)
        .param("search", suffix))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content.length()").value(1))
      .andExpect(jsonPath("$.content[0].id").value(productId.toString()));
    mvc.perform(get("/api/v1/store-manager-products").with(otherManager).param("search", suffix))
      .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    mvc.perform(get("/api/v1/products/{id}", productId).with(otherManager))
      .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/store-manager-products").param("search", suffix))
      .andExpect(status().isUnauthorized());

    mvc.perform(put("/api/v1/store-manager-products/{id}", productId).with(manager)
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(category.getId(), "Updated Manager QA Product " + suffix, "updated-manager-qa-" + suffix,
            "SM-" + suffix.substring(0, 8), "DRAFT", false)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Updated Manager QA Product " + suffix))
      .andExpect(jsonPath("$.status").value("DRAFT"))
      .andExpect(jsonPath("$.locationAvailable").value(false));
    mvc.perform(get("/api/v1/products/{id}", productId).with(manager))
      .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Manager QA Product " + suffix));

    String variantBody = json.writeValueAsString(Map.of("sku", "VAR-" + suffix.substring(0, 8), "name", "QA pack",
        "attributes", "{\"pack\":\"250 g\"}", "price", new BigDecimal("12.50"), "active", true));
    String imageBody = json.writeValueAsString(Map.of("url", "https://example.test/" + suffix + ".png",
        "altText", "QA product image", "displayOrder", 0, "primaryImage", true));
    String variantResponse = mvc.perform(post("/api/v1/products/{id}/variants", productId).with(manager)
        .contentType(MediaType.APPLICATION_JSON).content(variantBody)).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String imageResponse = mvc.perform(post("/api/v1/products/{id}/images", productId).with(manager)
        .contentType(MediaType.APPLICATION_JSON).content(imageBody)).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    UUID variantId = UUID.fromString(json.readTree(variantResponse).get("id").asText());
    UUID imageId = UUID.fromString(json.readTree(imageResponse).get("id").asText());
    mvc.perform(put("/api/v1/variants/{id}", variantId).with(manager).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("sku", "VAR-" + suffix.substring(0, 8), "name", "Updated QA pack",
            "price", new BigDecimal("15.00"), "active", true))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated QA pack"));
    mvc.perform(put("/api/v1/images/{id}", imageId).with(manager).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("url", "https://example.test/updated-" + suffix + ".png",
            "altText", "Updated QA image", "displayOrder", 0, "primaryImage", true))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.altText").value("Updated QA image"));
    mvc.perform(put("/api/v1/variants/{id}", variantId).with(otherManager).contentType(MediaType.APPLICATION_JSON)
        .content(variantBody)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/products/{id}/variants", productId).with(otherManager)
        .contentType(MediaType.APPLICATION_JSON).content(variantBody)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/products/{id}/images", productId).with(otherManager)
        .contentType(MediaType.APPLICATION_JSON).content(imageBody)).andExpect(status().isForbidden());

    mvc.perform(put("/api/v1/store-manager-products/{id}", productId).with(manager)
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(category.getId(), "Updated Manager QA Product " + suffix, "updated-manager-qa-" + suffix,
            "SM-" + suffix.substring(0, 8), "ACTIVE", false)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

    mvc.perform(put("/api/v1/store-manager-products/{id}", productId).with(otherManager)
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(category.getId(), "Cross-store update " + suffix, "cross-update-" + suffix,
            "XU-" + suffix.substring(0, 8), "ACTIVE", true)))
      .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/store-manager-products/{id}", productId).with(otherManager))
      .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/store-manager-products/{id}", productId))
      .andExpect(status().isUnauthorized());

    mvc.perform(delete("/api/v1/store-manager-products/{id}", productId).with(manager))
      .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/store-manager-products").with(manager).param("search", suffix))
      .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    org.junit.jupiter.api.Assertions.assertFalse(products.existsById(productId));
    org.junit.jupiter.api.Assertions.assertTrue(mappings.findByProductId(productId).isEmpty());
    org.junit.jupiter.api.Assertions.assertTrue(variants.findByProductIdOrderByNameAsc(productId).isEmpty());
    org.junit.jupiter.api.Assertions.assertTrue(images.findByProductIdOrderByDisplayOrderAsc(productId).isEmpty());

  }

  @Test
  void anonymousProductCreateIsRejected() throws Exception {
    Category category = categories.saveAndFlush(new Category("Anonymous QA " + UUID.randomUUID(), "anonymous-qa-" + UUID.randomUUID()));
    mvc.perform(post("/api/v1/store-manager-products").contentType(MediaType.APPLICATION_JSON)
        .content(request(category.getId(), "Anonymous product", "anonymous-product-" + UUID.randomUUID(),
            "ANON-" + UUID.randomUUID().toString().substring(0, 8), "ACTIVE", true)))
      .andExpect(status().isUnauthorized());
  }

  private void createStore(String id, String location) {
    Store store = new Store(id);
    store.setName("Disposable " + id);
    store.setLocation(location);
    store.setAddress("Test address, " + location);
    store.setLatitude(new BigDecimal("17.4000000"));
    store.setLongitude(new BigDecimal("78.4000000"));
    store.setProvider("TEST_FIXTURE");
    stores.saveAndFlush(store);
  }

  private String request(UUID categoryId, String name, String slug, String sku, String status, boolean available) throws Exception {
    return json.writeValueAsString(Map.of(
      "product", Map.ofEntries(Map.entry("categoryId", categoryId), Map.entry("subcategory", "QA"),
          Map.entry("name", name), Map.entry("slug", slug), Map.entry("sku", sku),
          Map.entry("description", "Isolated Store Manager product fixture"), Map.entry("status", status),
          Map.entry("globalProduct", true), Map.entry("featured", true), Map.entry("special", true),
          Map.entry("newArrival", true), Map.entry("bestSeller", true)),
      "available", available,
      "storeId", "client supplied values are ignored"));
  }
}
