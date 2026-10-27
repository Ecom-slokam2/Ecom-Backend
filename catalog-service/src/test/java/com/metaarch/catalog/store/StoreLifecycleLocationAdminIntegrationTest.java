package com.metaarch.catalog.store;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class StoreLifecycleLocationAdminIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void locationAdminCanChangeAssignedStoreStatusAndCannotChangeAnotherLocation() throws Exception {
    createStore("la08-hyderabad", "Hyderabad");
    createStore("la08-chennai", "Chennai");
    var locationAdmin = locationAdmin();

    mvc.perform(put("/api/v1/stores/{id}", "la08-hyderabad").contentType(MediaType.APPLICATION_JSON)
        .content(store("la08-hyderabad", "Hyderabad", "Inactive")))
        .andExpect(status().isUnauthorized());

    mvc.perform(put("/api/v1/stores/{id}", "la08-hyderabad").with(locationAdmin)
        .contentType(MediaType.APPLICATION_JSON).content(store("la08-hyderabad", "Hyderabad", "Inactive")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Inactive"))
        .andExpect(jsonPath("$.active").value(false));

    mvc.perform(get("/api/v1/stores/{id}", "la08-hyderabad").with(locationAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Inactive"))
        .andExpect(jsonPath("$.active").value(false));

    mvc.perform(put("/api/v1/stores/{id}", "la08-chennai").with(locationAdmin)
        .contentType(MediaType.APPLICATION_JSON).content(store("la08-chennai", "Chennai", "Inactive")))
        .andExpect(status().isForbidden());
  }

  @Test
  void locationAdminCanDeleteOnlyAnAssignedStore() throws Exception {
    createStore("la09-hyderabad", "Hyderabad");
    createStore("la09-chennai", "Chennai");
    var locationAdmin = locationAdmin();

    mvc.perform(delete("/api/v1/stores/{id}", "la09-hyderabad")).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/stores/{id}", "la09-chennai").with(locationAdmin))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/stores/{id}", "la09-hyderabad").with(locationAdmin))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/stores/{id}", "la09-hyderabad").with(globalAdmin()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/stores/{id}", "la09-chennai").with(locationAdmin))
        .andExpect(status().isForbidden());
  }

  private void createStore(String id, String location) throws Exception {
    mvc.perform(post("/api/v1/stores").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(store(id, location, "Active"))).andExpect(status().isCreated());
  }

  private String store(String id, String location, String state) throws Exception {
    return json.writeValueAsString(java.util.Map.of(
        "id", id,
        "name", id,
        "location", location,
        "address", "10 QA Road, " + location,
        "latitude", 17.425,
        "longitude", 78.45,
        "status", state,
        "active", state.equals("Active")));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor locationAdmin() {
    return jwt().authorities(() -> "ROLE_LOCATION_ADMIN", () -> "SCOPE_LOCATION:Hyderabad");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor globalAdmin() {
    return jwt().authorities(() -> "ROLE_GLOBAL_ADMIN");
  }
}
