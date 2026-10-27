package com.metaarch.catalog.store;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.jwt.secret=local-isolated-test-secret-that-is-long-enough-for-hmac-sha256")
@AutoConfigureMockMvc
@Transactional
class StoreLocationAdminIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void locationAdminCreatesOnlyAssignedLocationStoreAndPersistsAddressCoordinatesAndOperations() throws Exception {
    ObjectNode request = store("la04-create-hyderabad", "LA04 disposable store", "Hyderabad");

    mvc.perform(post("/api/v1/stores").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/stores").with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.location").value("Hyderabad"))
        .andExpect(jsonPath("$.address").value("10 Test Road, Hyderabad"))
        .andExpect(jsonPath("$.deliveryEnabled").value(true))
        .andExpect(jsonPath("$.pickupEnabled").value(false))
        .andExpect(jsonPath("$.deliveryRadiusKm").value(7.5))
        .andExpect(jsonPath("$.deliveryEtaMinutes").value(25))
        .andExpect(jsonPath("$.deliveryCharge").value(39.00));

    mvc.perform(get("/api/v1/stores/{id}", "la04-create-hyderabad").with(locationAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latitude").value(17.4250000))
        .andExpect(jsonPath("$.longitude").value(78.4500000))
        .andExpect(jsonPath("$.deliveryModes").value("Standard Delivery,Store Pickup"))
        .andExpect(jsonPath("$.deliveryNotes").value("Disposable integration fixture"));

    ObjectNode outsideLocation = store("la04-create-chennai", "LA04 out of scope store", "Chennai");
    mvc.perform(post("/api/v1/stores").with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(outsideLocation)))
        .andExpect(status().isForbidden());
  }

  @Test
  void locationAdminCanEditAssignedStoreButCannotMoveOrEditAnotherLocationStore() throws Exception {
    ObjectNode initial = store("la05-edit-hyderabad", "LA05 disposable store", "Hyderabad");
    mvc.perform(post("/api/v1/stores").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(initial))).andExpect(status().isCreated());
    ObjectNode chennai = store("la05-edit-chennai", "LA05 other location fixture", "Chennai");
    mvc.perform(post("/api/v1/stores").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(chennai))).andExpect(status().isCreated());

    ObjectNode update = store("la05-edit-hyderabad", "LA05 disposable store updated", "Hyderabad");
    update.put("address", "Updated Test Road, Hyderabad");
    update.put("latitude", 17.4300000);
    update.put("longitude", 78.4600000);
    update.put("pickupEnabled", true);
    update.put("deliveryRadiusKm", 9.0);
    update.put("deliveryEtaMinutes", 30);
    update.put("deliveryCharge", 45.00);
    update.put("freeDeliveryAbove", 600.00);
    update.put("deliveryModes", "Standard Delivery,Express Delivery,Store Pickup");

    mvc.perform(put("/api/v1/stores/{id}", "la05-edit-hyderabad").with(locationAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("LA05 disposable store updated"))
        .andExpect(jsonPath("$.address").value("Updated Test Road, Hyderabad"))
        .andExpect(jsonPath("$.latitude").value(17.4300000))
        .andExpect(jsonPath("$.longitude").value(78.4600000))
        .andExpect(jsonPath("$.pickupEnabled").value(true))
        .andExpect(jsonPath("$.deliveryRadiusKm").value(9.0))
        .andExpect(jsonPath("$.deliveryEtaMinutes").value(30))
        .andExpect(jsonPath("$.deliveryCharge").value(45.0))
        .andExpect(jsonPath("$.freeDeliveryAbove").value(600.0))
        .andExpect(jsonPath("$.deliveryModes").value("Standard Delivery,Express Delivery,Store Pickup"));

    ObjectNode movedStore = store("la05-edit-hyderabad", "LA05 disposable store updated", "Chennai");
    movedStore.put("address", "Must not persist");
    mvc.perform(put("/api/v1/stores/{id}", "la05-edit-hyderabad").with(locationAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(movedStore)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/stores/{id}", "la05-edit-hyderabad").with(locationAdmin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.address").value("Updated Test Road, Hyderabad"));

    mvc.perform(get("/api/v1/stores/{id}", "la05-edit-chennai").with(locationAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/stores/{id}", "la05-edit-chennai").with(locationAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(chennai)))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidStoreAddressCoordinatesAndOperationalValuesAreRejected() throws Exception {
    ObjectNode blankAddress = store("la06-invalid-address", "LA06 invalid address", "Hyderabad");
    blankAddress.put("address", "  ");
    mvc.perform(post("/api/v1/stores").with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(blankAddress))).andExpect(status().isBadRequest());

    ObjectNode invalidLatitude = store("la06-invalid-latitude", "LA06 invalid latitude", "Hyderabad");
    invalidLatitude.put("latitude", 91.0);
    mvc.perform(post("/api/v1/stores").with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(invalidLatitude))).andExpect(status().isBadRequest());

    ObjectNode invalidLongitude = store("la06-invalid-longitude", "LA06 invalid longitude", "Hyderabad");
    invalidLongitude.put("longitude", -181.0);
    mvc.perform(post("/api/v1/stores").with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(invalidLongitude))).andExpect(status().isBadRequest());

    ObjectNode negativeRadius = store("la07-invalid-radius", "LA07 invalid radius", "Hyderabad");
    negativeRadius.put("deliveryRadiusKm", -1.0);
    mvc.perform(post("/api/v1/stores").with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(negativeRadius))).andExpect(status().isBadRequest());

    ObjectNode negativeEta = store("la07-invalid-eta", "LA07 invalid ETA", "Hyderabad");
    negativeEta.put("deliveryEtaMinutes", -1);
    mvc.perform(post("/api/v1/stores").with(locationAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(negativeEta))).andExpect(status().isBadRequest());
  }

  @Test
  void storeManagerRetainsExactStoreUpdatePermissionAndCannotChangeStoreLocation() throws Exception {
    ObjectNode fixture = store("la-manager-exact-store", "Manager assigned store", "Hyderabad");
    mvc.perform(post("/api/v1/stores").with(globalAdmin()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(fixture))).andExpect(status().isCreated());

    ObjectNode update = store("la-manager-exact-store", "Manager updated store", "Hyderabad");
    mvc.perform(put("/api/v1/stores/{id}", "la-manager-exact-store").with(storeManager())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Manager updated store"));

    ObjectNode move = store("la-manager-exact-store", "Manager updated store", "Chennai");
    mvc.perform(put("/api/v1/stores/{id}", "la-manager-exact-store").with(storeManager())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(move)))
        .andExpect(status().isForbidden());

    ObjectNode globalUpdate = store("la-manager-exact-store", "Global admin updated store", "Hyderabad");
    mvc.perform(put("/api/v1/stores/{id}", "la-manager-exact-store").with(globalAdmin())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(globalUpdate)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Global admin updated store"));
  }

  private ObjectNode store(String id, String name, String location) {
    ObjectNode request = json.createObjectNode();
    request.put("id", id);
    request.put("name", name);
    request.put("location", location);
    request.put("address", "10 Test Road, " + location);
    request.put("latitude", 17.4250000);
    request.put("longitude", 78.4500000);
    request.put("provider", "TEST_FIXTURE");
    request.put("providerPlaceId", "fixture-place");
    request.put("deliveryEnabled", true);
    request.put("pickupEnabled", false);
    request.put("deliveryRadiusKm", 7.5);
    request.put("deliveryEtaMinutes", 25);
    request.put("deliveryCharge", 39.00);
    request.put("freeDeliveryAbove", 499.00);
    request.put("deliveryModes", "Standard Delivery,Store Pickup");
    request.put("deliveryNotes", "Disposable integration fixture");
    request.put("status", "Active");
    request.put("active", true);
    return request;
  }

  private RequestPostProcessor locationAdmin() {
    return jwt().authorities(() -> "ROLE_LOCATION_ADMIN", () -> "SCOPE_LOCATION:hyderabad");
  }

  private RequestPostProcessor globalAdmin() {
    return jwt().authorities(() -> "ROLE_GLOBAL_ADMIN");
  }

  private RequestPostProcessor storeManager() {
    return jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:la-manager-exact-store");
  }
}
