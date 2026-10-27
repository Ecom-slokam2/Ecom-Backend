package com.metaarch.catalog.banner;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaarch.catalog.store.Store;
import com.metaarch.catalog.store.StoreRepository;
import java.math.BigDecimal;
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
class BannerStoreManagerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired StoreRepository stores;

  @Test
  void storeManagerCanCrudOnlyAssignedStoreBannersAndCannotOverrideScope() throws Exception {
    String ownStore = saveStore("store-" + UUID.randomUUID(), "Hyderabad");
    String otherStore = saveStore("store-" + UUID.randomUUID(), "Hyderabad");
    var manager = manager(ownStore);
    String ownId = create("SM10 own", ownStore, manager);
    String otherId = create("SM10 other", otherStore, globalAdmin());
    String globalId = createGlobal();

    mvc.perform(get("/api/v1/banners").param("all", "true").with(manager))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + ownId + "')]").exists())
        .andExpect(jsonPath("$[?(@.id=='" + otherId + "')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.id=='" + globalId + "')]").doesNotExist());
    mvc.perform(get("/api/v1/banners/{id}", otherId).with(manager)).andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/banners/{id}", ownId).with(manager).contentType(MediaType.APPLICATION_JSON)
        .content(banner("SM10 edited", "STORE", ownStore)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("SM10 edited"))
        .andExpect(jsonPath("$.locationId").value("Hyderabad"));
    mvc.perform(patch("/api/v1/banners/{id}/status", ownId).with(manager).contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
    mvc.perform(put("/api/v1/banners/{id}", ownId).with(manager).contentType(MediaType.APPLICATION_JSON)
        .content(banner("SM10 takeover", "STORE", otherStore))).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/banners").with(manager).contentType(MediaType.APPLICATION_JSON)
        .content(banner("SM10 global override", "GLOBAL", null))).andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/banners/{id}", otherId).with(manager)).andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/banners/{id}", ownId).with(manager)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/banners/{id}", globalId).with(globalAdmin())).andExpect(status().isOk());
  }

  private String create(String title, String storeId, org.springframework.test.web.servlet.request.RequestPostProcessor auth) throws Exception {
    String body = mvc.perform(post("/api/v1/banners").with(auth).contentType(MediaType.APPLICATION_JSON)
        .content(banner(title, "STORE", storeId))).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("id").asText();
  }

  private String createGlobal() throws Exception {
    String body = mvc.perform(post("/api/v1/banners").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(banner("SM10 global", "GLOBAL", null))).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("id").asText();
  }

  private String banner(String title, String scope, String store) throws Exception {
    var request = json.createObjectNode();
    request.put("title", title); request.put("imageUrl", "https://example.invalid/banner.png");
    request.put("scope", scope); request.put("status", "INACTIVE");
    if (store != null) { request.put("storeId", store); request.put("locationId", "Hyderabad"); }
    return json.writeValueAsString(request);
  }

  private String saveStore(String id, String location) {
    Store store = new Store(id); store.setName("SM10 " + id); store.setLocation(location); store.setAddress("Isolated address");
    store.setLatitude(new BigDecimal("17.4")); store.setLongitude(new BigDecimal("78.4"));
    stores.saveAndFlush(store); return id;
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor manager(String store) {
    return jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:" + store);
  }
  private org.springframework.test.web.servlet.request.RequestPostProcessor globalAdmin() { return jwt().authorities(() -> "ROLE_GLOBAL_ADMIN"); }
}
