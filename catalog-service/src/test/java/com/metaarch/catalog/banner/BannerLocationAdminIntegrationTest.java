package com.metaarch.catalog.banner;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class BannerLocationAdminIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void locationAdminCanCreateAndReadOnlyAssignedLocationBanners() throws Exception {
    var locationAdmin = locationAdmin();
    mvc.perform(post("/api/v1/banners").contentType(MediaType.APPLICATION_JSON)
        .content(banner("LA15 unauthenticated", "LOCATION", "Hyderabad")))
        .andExpect(status().isUnauthorized());

    String ownBannerId = createBanner("LA15 Hyderabad", "LOCATION", "Hyderabad", locationAdmin);
    String otherBannerId = createBanner("LA15 Chennai", "LOCATION", "Chennai", globalAdmin());
    String globalBannerId = createBanner("LA15 global", "GLOBAL", null, globalAdmin());

    mvc.perform(get("/api/v1/banners/{id}", ownBannerId).with(locationAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("LOCATION"))
        .andExpect(jsonPath("$.locationId").value("Hyderabad"));
    mvc.perform(get("/api/v1/banners/{id}", otherBannerId).with(locationAdmin))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/banners/{id}", globalBannerId).with(locationAdmin))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/banners").param("all", "true").with(locationAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + ownBannerId + "')]").exists())
        .andExpect(jsonPath("$[?(@.id=='" + otherBannerId + "')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.id=='" + globalBannerId + "')]").doesNotExist());
  }

  @Test
  void locationAdminCannotCreateOrMoveBannerOutsideAssignedLocationAndGlobalAdminRetainsAccess() throws Exception {
    var locationAdmin = locationAdmin();
    mvc.perform(post("/api/v1/banners").with(locationAdmin).contentType(MediaType.APPLICATION_JSON)
        .content(banner("LA15 cross-location", "LOCATION", "Chennai")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/banners").with(locationAdmin).contentType(MediaType.APPLICATION_JSON)
        .content(banner("LA15 global scope", "GLOBAL", null)))
        .andExpect(status().isForbidden());

    String ownBannerId = createBanner("LA15 location stays scoped", "LOCATION", "Hyderabad", locationAdmin);
    mvc.perform(put("/api/v1/banners/{id}", ownBannerId).with(locationAdmin)
        .contentType(MediaType.APPLICATION_JSON).content(banner("LA15 moved", "LOCATION", "Chennai")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/banners/{id}", ownBannerId).with(locationAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locationId").value("Hyderabad"));

    String globalBannerId = createBanner("LA15 global admin", "GLOBAL", null, globalAdmin());
    mvc.perform(put("/api/v1/banners/{id}", globalBannerId).with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(banner("LA15 global admin updated", "GLOBAL", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("LA15 global admin updated"));
  }

  @Test
  void locationAdminCanEditAndDisableOnlyAssignedLocationCampaignBanner() throws Exception {
    var locationAdmin = locationAdmin();
    String ownBannerId = createBanner("LA16 Hyderabad campaign", "LOCATION", "Hyderabad", locationAdmin);
    String otherBannerId = createBanner("LA16 Chennai campaign", "LOCATION", "Chennai", globalAdmin());
    String active = "{\"status\":\"ACTIVE\"}";
    String inactive = "{\"status\":\"INACTIVE\"}";

    mvc.perform(put("/api/v1/banners/{id}", ownBannerId).with(locationAdmin)
        .contentType(MediaType.APPLICATION_JSON).content(banner("LA16 edited Hyderabad campaign", "LOCATION", "Hyderabad")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("LA16 edited Hyderabad campaign"));
    mvc.perform(patch("/api/v1/banners/{id}/status", ownBannerId).with(locationAdmin)
        .contentType(MediaType.APPLICATION_JSON).content(active))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    mvc.perform(patch("/api/v1/banners/{id}/status", otherBannerId).with(locationAdmin)
        .contentType(MediaType.APPLICATION_JSON).content(inactive)).andExpect(status().isForbidden());
    mvc.perform(patch("/api/v1/banners/{id}/status", ownBannerId).contentType(MediaType.APPLICATION_JSON).content(inactive))
        .andExpect(status().isUnauthorized());
    mvc.perform(patch("/api/v1/banners/{id}/status", ownBannerId).with(locationAdmin)
        .contentType(MediaType.APPLICATION_JSON).content(inactive))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
    mvc.perform(get("/api/v1/banners/{id}", ownBannerId).with(locationAdmin))
        .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("LA16 edited Hyderabad campaign"))
        .andExpect(jsonPath("$.status").value("INACTIVE"));
  }

  private String createBanner(String title, String scope, String location,
      org.springframework.test.web.servlet.request.RequestPostProcessor auth) throws Exception {
    String body = mvc.perform(post("/api/v1/banners").with(auth).contentType(MediaType.APPLICATION_JSON)
        .content(banner(title, scope, location)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    JsonNode response = json.readTree(body);
    return response.get("id").asText();
  }

  private String banner(String title, String scope, String location) throws Exception {
    var request = json.createObjectNode();
    request.put("title", title);
    request.put("imageUrl", "https://example.invalid/location-admin-qa.png");
    request.put("scope", scope);
    if (location != null) request.put("locationId", location);
    request.put("status", "INACTIVE");
    request.put("sortOrder", 0);
    return json.writeValueAsString(request);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor locationAdmin() {
    return jwt().authorities(() -> "ROLE_LOCATION_ADMIN", () -> "SCOPE_LOCATION:Hyderabad");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor globalAdmin() {
    return jwt().authorities(() -> "ROLE_GLOBAL_ADMIN");
  }
}
