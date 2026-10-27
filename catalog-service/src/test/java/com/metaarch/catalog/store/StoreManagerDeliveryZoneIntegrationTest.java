package com.metaarch.catalog.store;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
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
class StoreManagerDeliveryZoneIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired StoreRepository stores;
  @Autowired StoreDeliveryZoneRepository zones;

  @Test
  void storeManagerCanManageAssignedDeliveryZonesButCannotReadOrWriteAnotherStore() throws Exception {
    String suffix = UUID.randomUUID().toString();
    String assignedId = "sm-zone-assigned-" + suffix;
    String otherId = "sm-zone-other-" + suffix;
    createStore(assignedId, "Hyderabad");
    createStore(otherId, "Chennai");
    var existing = new StoreDeliveryZone();
    existing.setStoreId(assignedId);
    existing.setMaxRadiusKm(new BigDecimal("3.00"));
    existing.setDeliveryCharge(new BigDecimal("20.00"));
    zones.saveAndFlush(existing);

    mvc.perform(get("/api/v1/stores/{id}/delivery-zones", assignedId).with(manager(assignedId)))
      .andExpect(status().isOk()).andExpect(jsonPath("$[0].storeId").value(assignedId));
    mvc.perform(get("/api/v1/stores/{id}/delivery-zones", otherId).with(manager(assignedId)))
      .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/stores/{id}/delivery-zones", assignedId))
      .andExpect(status().isUnauthorized());

    String createBody = zoneRequest(0, 8, 20, 35, 30, 600, 100);
    String created = mvc.perform(post("/api/v1/stores/{id}/delivery-zones", assignedId).with(manager(assignedId))
        .contentType(MediaType.APPLICATION_JSON).content(createBody))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.storeId").value(assignedId))
      .andExpect(jsonPath("$.maxRadiusKm").value(8.0))
      .andExpect(jsonPath("$.deliveryCharge").value(30.0))
      .andExpect(jsonPath("$.methods[0].deliveryType").value("Standard Delivery"))
      .andReturn().getResponse().getContentAsString();
    long zoneId = json.readTree(created).get("id").asLong();

    // Order creation performs this read without forwarding the customer's bearer token.
    // A legacy zone without a mapping resolves from the store's city/location.
    mvc.perform(get("/api/v1/stores/delivery-zones/{zoneId}/location", zoneId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.deliveryZoneId").value(zoneId))
      .andExpect(jsonPath("$.storeId").value(assignedId))
      .andExpect(jsonPath("$.locationId").value("hyderabad"));

    mvc.perform(post("/api/v1/stores/{id}/delivery-zones", otherId).with(manager(assignedId))
        .contentType(MediaType.APPLICATION_JSON).content(createBody))
      .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/stores/{id}/delivery-zones", assignedId).contentType(MediaType.APPLICATION_JSON).content(createBody))
      .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/stores/{id}/delivery-zones", assignedId).with(manager(assignedId))
        .contentType(MediaType.APPLICATION_JSON).content(zoneRequest(4, 4, 20, 35, 30, 600, 100)))
      .andExpect(status().isBadRequest());

    mvc.perform(put("/api/v1/stores/{storeId}/delivery-zones/{zoneId}", assignedId, zoneId).with(manager(assignedId))
        .contentType(MediaType.APPLICATION_JSON).content(zoneRequest(0, 9, 25, 40, 35, 700, 150)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.maxRadiusKm").value(9.0))
      .andExpect(jsonPath("$.deliveryCharge").value(35.0))
      .andExpect(jsonPath("$.methods[0].enabled").value(false));
    mvc.perform(put("/api/v1/stores/{storeId}/delivery-zones/{zoneId}", otherId, zoneId).with(manager(assignedId))
        .contentType(MediaType.APPLICATION_JSON).content(createBody))
      .andExpect(status().isForbidden());

    mvc.perform(get("/api/v1/stores/{id}/delivery-zones", assignedId).with(manager(assignedId)))
      .andExpect(status().isOk()).andExpect(jsonPath("$[1].maxRadiusKm").value(9.0));
    mvc.perform(delete("/api/v1/stores/{storeId}/delivery-zones/{zoneId}", assignedId, zoneId).with(manager(assignedId)))
      .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/stores/{id}/delivery-zones", assignedId).with(manager(assignedId)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void storeManagerCanUpdateOnlyAssignedStoreProfileAndDeliverySettings() throws Exception {
    String suffix = UUID.randomUUID().toString();
    String assignedId = "sm-profile-assigned-" + suffix;
    String otherId = "sm-profile-other-" + suffix;
    createStore(assignedId, "Hyderabad");
    createStore(otherId, "Chennai");

    mvc.perform(put("/api/v1/stores/{id}", assignedId).with(manager(assignedId))
        .contentType(MediaType.APPLICATION_JSON)
        .content(storeRequest(assignedId, "Updated QA store", "Hyderabad", "Test Road 12", "17.401", "78.402", true)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Updated QA store"))
      .andExpect(jsonPath("$.address").value("Test Road 12"))
      .andExpect(jsonPath("$.latitude").value(17.401))
      .andExpect(jsonPath("$.longitude").value(78.402))
      .andExpect(jsonPath("$.deliveryEnabled").value(true));

    mvc.perform(get("/api/v1/stores/{id}", assignedId).with(manager(assignedId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Updated QA store"))
      .andExpect(jsonPath("$.address").value("Test Road 12"))
      .andExpect(jsonPath("$.deliveryEnabled").value(true));

    String defaultZone = mvc.perform(get("/api/v1/stores/{id}/delivery-zones", assignedId).with(manager(assignedId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].maxRadiusKm").value(8.0))
      .andExpect(jsonPath("$[0].methods[*].deliveryType").value(org.hamcrest.Matchers.hasItem("Standard Delivery")))
      .andReturn().getResponse().getContentAsString();
    long defaultZoneId = json.readTree(defaultZone).get(0).get("id").asLong();
    mvc.perform(get("/api/v1/stores/delivery-zones/{zoneId}/location", defaultZoneId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.locationId").value("hyderabad"));

    mvc.perform(put("/api/v1/stores/{id}", otherId).with(manager(assignedId))
        .contentType(MediaType.APPLICATION_JSON)
        .content(storeRequest(otherId, "Cross-store attempt", "Chennai", "Other address", "13.0", "80.0", false)))
      .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/stores/{id}", assignedId).with(manager(assignedId))
        .contentType(MediaType.APPLICATION_JSON)
        .content(storeRequest(assignedId, "Move location attempt", "Chennai", "Other address", "13.0", "80.0", false)))
      .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/stores/{id}", assignedId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(storeRequest(assignedId, "Anonymous attempt", "Hyderabad", "Other address", "17.0", "78.0", false)))
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

  private RequestPostProcessor manager(String storeId) {
    return jwt().authorities(() -> "ROLE_STORE_MANAGER", () -> "SCOPE_STORE:" + storeId);
  }

  private String zoneRequest(int min, int max, int etaMin, int etaMax, int charge, int freeAbove, int minimum) throws Exception {
    return json.writeValueAsString(java.util.Map.of(
      "minRadiusKm", min,
      "maxRadiusKm", max,
      "estimatedDeliveryMin", etaMin,
      "estimatedDeliveryMax", etaMax,
      "deliveryCharge", charge,
      "freeDeliveryAbove", freeAbove,
      "minimumOrder", minimum,
      "status", "Active",
      "methods", java.util.List.of(java.util.Map.of(
        "deliveryType", "Standard Delivery",
        "enabled", max != 9,
        "deliveryCharge", charge,
        "estimatedTime", etaMin + "-" + etaMax + " min",
        "cutoffTime", "18:00",
        "settingsConfiguration", "{}"))));
  }

  private String storeRequest(String id, String name, String location, String address, String latitude, String longitude, boolean deliveryEnabled) throws Exception {
    return json.writeValueAsString(java.util.Map.ofEntries(
      java.util.Map.entry("id", id),
      java.util.Map.entry("name", name),
      java.util.Map.entry("location", location),
      java.util.Map.entry("address", address),
      java.util.Map.entry("latitude", new BigDecimal(latitude)),
      java.util.Map.entry("longitude", new BigDecimal(longitude)),
      java.util.Map.entry("deliveryEnabled", deliveryEnabled),
      java.util.Map.entry("pickupEnabled", true),
      java.util.Map.entry("deliveryRadiusKm", 8),
      java.util.Map.entry("deliveryEtaMinutes", 35),
      java.util.Map.entry("deliveryCharge", 25),
      java.util.Map.entry("freeDeliveryAbove", 499),
      java.util.Map.entry("deliveryModes", "Standard Delivery,Instant Delivery,Express Delivery,Scheduled Delivery,Store Pickup"),
      java.util.Map.entry("active", true)));
  }
}
