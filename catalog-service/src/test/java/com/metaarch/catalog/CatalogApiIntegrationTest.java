package com.metaarch.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.http.MediaType; import org.springframework.test.web.servlet.MockMvc;
import com.metaarch.catalog.store.Store; import com.metaarch.catalog.store.StoreRepository; import java.math.BigDecimal;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(properties = "app.jwt.secret=local-isolated-test-secret-that-is-long-enough-for-hmac-sha256") @AutoConfigureMockMvc
class CatalogApiIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired StoreRepository stores;
 @Test void catalogCrudAndValidationRules() throws Exception {
  mvc.perform(post("/api/v1/stores").with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("""
    {"id":"store-client-demo","name":"Client Demo Store","location":"Hyderabad","address":"Ameerpet, Hyderabad, Telangana, India","latitude":17.4375000,"longitude":78.4482000,"provider":"OPENSTREETMAP","providerPlaceId":"demo-place","deliveryEnabled":true,"pickupEnabled":true,"active":true}
    """)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("store-client-demo")).andExpect(jsonPath("$.latitude").value(17.4375000));
  mvc.perform(get("/api/v1/stores").param("active","true")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
  JsonNode category=json.readTree(mvc.perform(post("/api/v1/categories").with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("""
    {"name":"Phones","slug":"phones","description":"Mobile phones","active":true}
    """)).andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value("phones")).andReturn().getResponse().getContentAsString());
  String categoryId=category.get("id").asText();
  JsonNode product=json.readTree(mvc.perform(post("/api/v1/products").with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("""
    {"categoryId":"%s","name":"Example Phone","slug":"example-phone","sku":"PHONE-001","description":"Example","brand":"MetaArch","status":"ACTIVE"}
    """.formatted(categoryId))).andExpect(status().isCreated()).andExpect(jsonPath("$.categoryId").value(categoryId)).andReturn().getResponse().getContentAsString());
  String productId=product.get("id").asText();
  mvc.perform(post("/api/v1/products/{id}/variants",productId).with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("""
    {"sku":"PHONE-001-BLK","name":"Black","price":500.00,"compareAtPrice":400.00,"active":true}
    """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("compareAtPrice must be greater than or equal to price"));
  JsonNode mapping=json.readTree(mvc.perform(post("/api/v1/store-products").with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("""
    {"storeId":"store-client-demo","productId":"%s","available":true,"priceOverride":475.00}
    """.formatted(productId))).andExpect(status().isCreated()).andExpect(jsonPath("$.available").value(true)).andReturn().getResponse().getContentAsString());
  String mappingId=mapping.get("id").asText();
  mvc.perform(get("/api/v1/public/store-products").param("storeId","store-client-demo")).andExpect(status().isOk()).andExpect(jsonPath("$[0].productId").value(productId));
  mvc.perform(get("/api/v1/store-products").param("storeId","store-client-demo")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/store-products/{id}",mappingId).with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.priceOverride").value(475.00));
  mvc.perform(put("/api/v1/store-products/{id}",mappingId).with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("""
    {"storeId":"store-client-demo","productId":"%s","available":false,"priceOverride":450.00}
    """.formatted(productId))).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
  mvc.perform(get("/api/v1/public/store-products").param("storeId","store-client-demo")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
  mvc.perform(get("/api/v1/store-products").with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN")).param("productId",productId)).andExpect(status().isOk()).andExpect(jsonPath("$[0].available").value(false)).andExpect(jsonPath("$[0].priceOverride").value(450.00));
  mvc.perform(post("/api/v1/store-products").with(jwt().authorities(() -> "ROLE_SUPER_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("""
    {"storeId":"store-client-demo","productId":"%s","available":true}
    """.formatted(productId))).andExpect(status().isConflict());
  mvc.perform(delete("/api/v1/store-products/{id}",mappingId).with(jwt().authorities(() -> "ROLE_SUPER_ADMIN"))).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/store-products").with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN")).param("productId",productId)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
  mvc.perform(delete("/api/v1/categories/{id}",categoryId).with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN"))).andExpect(status().isConflict());
  mvc.perform(get("/api/v1/products").param("status","ACTIVE")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
 }

 @Test void mappingWritesRequireAdminOrTheManagerAssignedToTheTargetStore() throws Exception {
  String body="{\"storeId\":\"store-qa\",\"productId\":\"00000000-0000-0000-0000-000000000001\",\"available\":true}";
  mvc.perform(post("/api/v1/store-products").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/store-products").with(jwt().authorities(() -> "ROLE_CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/store-products").with(jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:store-other")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/store-products").with(jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:store-qa")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
 }

 @Test void locationAdminStoreAndMappingAccessIsRestrictedToAssignedLocation() throws Exception {
  stores.save(store("la-hyderabad", "Hyderabad"));
  stores.save(store("la-chennai", "Chennai"));
  var locationAdmin = jwt().authorities(() -> "ROLE_LOCATION_ADMIN", () -> "SCOPE_LOCATION:hyderabad");
  String request = "{\"id\":\"la-created\",\"name\":\"LA Created\",\"location\":\"Hyderabad\",\"address\":\"Test\",\"latitude\":17.4,\"longitude\":78.4,\"deliveryEnabled\":true,\"pickupEnabled\":true,\"active\":true}";
  mvc.perform(post("/api/v1/stores").with(locationAdmin).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
  String crossLocation = request.replace("la-created", "la-created-chennai").replace("LA Created", "LA Created Chennai").replace("Hyderabad", "Chennai");
  mvc.perform(post("/api/v1/stores").with(locationAdmin).contentType(MediaType.APPLICATION_JSON).content(crossLocation)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/stores").with(locationAdmin)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='la-hyderabad')]").exists()).andExpect(jsonPath("$[?(@.id=='la-chennai')]").doesNotExist());
  String mapping = "{\"storeId\":\"la-hyderabad\",\"productId\":\"00000000-0000-0000-0000-000000000001\",\"available\":true}";
  mvc.perform(post("/api/v1/store-products").with(locationAdmin).contentType(MediaType.APPLICATION_JSON).content(mapping)).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/store-products").with(locationAdmin).contentType(MediaType.APPLICATION_JSON).content(mapping.replace("la-hyderabad", "la-chennai"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/store-products").with(locationAdmin).param("storeId", "la-chennai")).andExpect(status().isForbidden());
 }

 private Store store(String id, String location) {
  Store store = new Store(id); store.setName(id); store.setLocation(location); store.setAddress("Test");
  store.setLatitude(new BigDecimal("17.4000000")); store.setLongitude(new BigDecimal("78.4000000"));
  return stores.save(store);
 }

 @Test void rejectsInvalidCategorySlug() throws Exception {
  mvc.perform(post("/api/v1/categories").with(jwt().authorities(() -> "ROLE_GLOBAL_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("""
    {"name":"Bad slug","slug":"Bad Slug","active":true}
    """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.slug").exists());
 }
}
