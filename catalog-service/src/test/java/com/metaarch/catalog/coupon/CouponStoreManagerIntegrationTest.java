package com.metaarch.catalog.coupon;

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
class CouponStoreManagerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired StoreRepository stores;

  @Test
  void storeManagerCanCrudOnlyAssignedStoreCouponsAndCannotOverrideScope() throws Exception {
    String ownStore = saveStore("store-" + UUID.randomUUID());
    String otherStore = saveStore("store-" + UUID.randomUUID());
    var manager = manager(ownStore);
    String ownId = create("SM45-OWN", ownStore, manager);
    String otherId = create("SM45-OTHER", otherStore, globalAdmin());
    String globalId = create("SM45-GLOBAL", null, globalAdmin());

    mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON)
        .content(coupon("SM45-UNAUTH", "STORE", ownStore))).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/coupons").with(manager)).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + ownId + "')]").exists())
        .andExpect(jsonPath("$[?(@.id=='" + otherId + "')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.id=='" + globalId + "')]").doesNotExist());
    mvc.perform(put("/api/v1/coupons/{id}", ownId).with(manager).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("SM45-EDITED", "STORE", ownStore))).andExpect(status().isOk())
        .andExpect(jsonPath("$.store").value(ownStore)).andExpect(jsonPath("$.location").value("Hyderabad"));
    mvc.perform(patch("/api/v1/coupons/{id}/status", ownId).with(manager).contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
    mvc.perform(post("/api/v1/coupons/validate").contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"SM45-EDITED\",\"subtotal\":100,\"location\":\"Hyderabad\",\"store\":\"" + ownStore + "\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.discountAmount").value(0));
    mvc.perform(put("/api/v1/coupons/{id}", ownId).with(manager).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("SM45-TAKEOVER", "STORE", otherStore))).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/coupons").with(manager).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("SM45-GLOBAL-OVERRIDE", "GLOBAL", null))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/coupons")).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/coupons/{id}", otherId).with(manager)).andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/coupons/{id}", ownId).with(manager)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/coupons").with(globalAdmin())).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + globalId + "')]").exists());
  }

  private String create(String code, String storeId, org.springframework.test.web.servlet.request.RequestPostProcessor auth) throws Exception {
    String body = mvc.perform(post("/api/v1/coupons").with(auth).contentType(MediaType.APPLICATION_JSON)
        .content(coupon(code, storeId == null ? "GLOBAL" : "STORE", storeId))).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("id").asText();
  }

  private String coupon(String code, String scope, String storeId) throws Exception {
    var request = json.createObjectNode();
    request.put("code", code); request.put("couponName", "Isolated " + code); request.put("discountType", "FIXED");
    request.put("discount", 10); request.put("minimumOrder", 0); request.put("maximumDiscount", 10);
    request.put("startDate", "2026-10-25"); request.put("expiry", "2026-12-31");
    request.put("usageLimit", 5); request.put("perUserLimit", 1); request.put("scope", scope);
    request.put("location", "Hyderabad"); request.put("status", "INACTIVE");
    if (storeId != null) request.put("store", storeId);
    return json.writeValueAsString(request);
  }

  private String saveStore(String id) {
    Store store = new Store(id); store.setName("SM45 " + id); store.setLocation("Hyderabad");
    store.setAddress("Isolated address"); store.setLatitude(new BigDecimal("17.4")); store.setLongitude(new BigDecimal("78.4"));
    stores.saveAndFlush(store); return id;
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor manager(String store) {
    return jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:" + store);
  }
  private org.springframework.test.web.servlet.request.RequestPostProcessor globalAdmin() { return jwt().authorities(() -> "ROLE_GLOBAL_ADMIN"); }
}
