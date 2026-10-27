package com.metaarch.catalog.coupon;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaarch.catalog.store.Store;
import com.metaarch.catalog.store.StoreRepository;
import java.math.BigDecimal;
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
class CouponLocationAdminIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired StoreRepository stores;

  @Test
  void locationAdminCanCreateEditAndDisableOnlyLocationCoupons() throws Exception {
    saveStore("store-hyd", "Hyderabad");
    saveStore("store-chn", "Chennai");
    mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON).content(coupon("LA30-UNAUTH", "LOCATION", "Hyderabad", null)))
        .andExpect(status().isUnauthorized());

    String createdBody = mvc.perform(post("/api/v1/coupons").with(locationAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(coupon("LA30-LOCATION", "LOCATION", "Hyderabad", null)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.scope").value("LOCATION"))
        .andExpect(jsonPath("$.location").value("Hyderabad")).andExpect(jsonPath("$.status").value("INACTIVE"))
        .andReturn().getResponse().getContentAsString();
    String id = json.readTree(createdBody).get("id").asText();

    mvc.perform(get("/api/v1/coupons").with(locationAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());

    mvc.perform(put("/api/v1/coupons/{id}", id).with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("LA30-EDITED", "LOCATION", "Hyderabad", null)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("LA30-EDITED"));
    mvc.perform(put("/api/v1/coupons/{id}", id).with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("LA30-CROSS", "LOCATION", "Chennai", null))).andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/coupons/{id}", id).with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("LA30-STORE", "STORE", "Hyderabad", "store-hyd"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("STORE")).andExpect(jsonPath("$.store").value("store-hyd"));
    mvc.perform(put("/api/v1/coupons/{id}", id).with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("LA30-CROSS-STORE", "STORE", "Hyderabad", "store-chn"))).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/coupons").with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("LA31-STORE", "STORE", "Hyderabad", "store-hyd")))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.scope").value("STORE"));
    mvc.perform(post("/api/v1/coupons").with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("LA31-UNKNOWN-STORE", "STORE", "Hyderabad", "not-a-store")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/coupons").with(locationAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.code=='LA31-STORE')]").exists());
    mvc.perform(patch("/api/v1/coupons/{id}/status", id).with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

    mvc.perform(post("/api/v1/coupons").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(coupon("LA31-GLOBAL", "STORE", "Hyderabad", "store-hyd")))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.scope").value("STORE"));
  }

  private void saveStore(String id, String location) {
    Store store = new Store(id);
    store.setName("Coupon fixture " + id);
    store.setLocation(location);
    store.setAddress("Isolated test address");
    store.setLatitude(new BigDecimal("17.4000000"));
    store.setLongitude(new BigDecimal("78.4000000"));
    stores.saveAndFlush(store);
  }

  private String coupon(String code, String scope, String location, String store) throws Exception {
    var request = json.createObjectNode();
    request.put("code", code);
    request.put("couponName", "Isolated coupon " + code);
    request.put("description", "Location Admin authorization QA");
    request.put("discountType", "FIXED");
    request.put("discount", 10);
    request.put("minimumOrder", 0);
    request.put("maximumDiscount", 10);
    request.put("startDate", "2026-10-25");
    request.put("expiry", "2026-12-31");
    request.put("usageLimit", 5);
    request.put("perUserLimit", 1);
    request.put("scope", scope);
    request.put("location", location);
    if (store != null) request.put("store", store);
    request.put("status", "INACTIVE");
    return json.writeValueAsString(request);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor locationAdmin() {
    return jwt().authorities(() -> "ROLE_LOCATION_ADMIN", () -> "SCOPE_LOCATION:Hyderabad");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor globalAdmin() {
    return jwt().authorities(() -> "ROLE_GLOBAL_ADMIN");
  }
}
