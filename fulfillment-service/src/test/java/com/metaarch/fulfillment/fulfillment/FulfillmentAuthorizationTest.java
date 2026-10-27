package com.metaarch.fulfillment.fulfillment;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaarch.fulfillment.shipment.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.cloud.config.enabled=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:sync-schema.sql",
  "sync.dispatch-enabled=false", "sync.service=fulfillment-service",
  "sync.routes.fulfillment=notification-service", "sync.targets.notification-service=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class FulfillmentAuthorizationTest {
  @Autowired MockMvc mvc;
  @Autowired FulfillmentRepository fulfillments;
  @Autowired ShipmentRepository shipments;
  @Autowired com.metaarch.fulfillment.event.FulfillmentOrderSnapshotRepository snapshots;
  @Autowired ObjectMapper mapper;
  @MockBean JwtDecoder decoder;
  @MockBean com.metaarch.fulfillment.shipment.DeliveryPartnerEligibility partnerEligibility;
  @MockBean com.metaarch.sync.CatalogLocationClient catalogLocations;
  UUID owner;
  Fulfillment fulfillment;
  Shipment shipment;

  @BeforeEach void setup() {
    owner = UUID.randomUUID();
    fulfillment = fulfillments.save(new Fulfillment(UUID.randomUUID(), owner, "store", FulfillmentType.SHIP_TO_HOME, UUID.randomUUID()));
    shipment = shipments.save(new Shipment(fulfillment, "carrier", UUID.randomUUID().toString(), LocalDate.now().plusDays(1)));
    shipment.assignDeliveryPartner("42", "QA partner", "5550100", "QA-VEH-1");
    shipments.saveAndFlush(shipment);
    reset(decoder);
    reset(partnerEligibility, catalogLocations);
    when(decoder.decode("owner")).thenReturn(token("owner", owner, "customer", "ROLE_CUSTOMER"));
    when(decoder.decode("stranger")).thenReturn(token("stranger", UUID.randomUUID(), "customer", "ROLE_CUSTOMER"));
    when(decoder.decode("admin")).thenReturn(token("admin", UUID.randomUUID(), "staff", "ROLE_ADMIN"));
    when(decoder.decode("global-admin")).thenReturn(token("global-admin", UUID.randomUUID(), "staff", "ROLE_GLOBAL_ADMIN"));
    when(decoder.decode("partner")).thenReturn(Jwt.withTokenValue("partner").header("alg", "HS512")
      .subject("partner").claim("roles", List.of("ROLE_DELIVERY_PARTNER"))
      .claim("deliveryPartnerId", "42").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());
    when(decoder.decode("delivery-proxy")).thenReturn(Jwt.withTokenValue("delivery-proxy").header("alg", "HS512")
      .subject("delivery-operations").claim("roles", List.of("ROLE_DELIVERY_PARTNER"))
      .claim("deliveryPartnerId", "42").claim("deliveryActionOrderId", fulfillment.getOrderId().toString())
      .claim("deliveryActionProxy", true).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(30)).build());
    when(decoder.decode("location-admin")).thenReturn(Jwt.withTokenValue("location-admin").header("alg", "HS512")
      .subject("location-admin").claim("accountType", "staff").claim("roles", List.of("ROLE_LOCATION_ADMIN"))
      .claim("authorities", List.of("ROLE_LOCATION_ADMIN", "SCOPE_LOCATION:Hyderabad")).build());
    when(catalogLocations.locationOf("store")).thenReturn(Optional.of("Hyderabad"));
    when(catalogLocations.locationOf("store-hyd")).thenReturn(Optional.of("Hyderabad"));
    when(catalogLocations.locationOf("store-chn")).thenReturn(Optional.of("Chennai"));
    when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid signature"));
    when(decoder.decode("expired")).thenThrow(new BadJwtException("Expired token"));
  }

  @Test void locationAdminAssignmentUsesVerifiedActiveStorePartnerAndIgnoresClientDetails() throws Exception {
    when(partnerEligibility.verifiedRequest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(shipment.getId()),
        org.mockito.ArgumentMatchers.eq("partner-hyd")))
      .thenReturn(new AssignDeliveryPartnerRequest("partner-hyd", "Verified Partner", "5550100", "QA-VEH-1"));
    when(partnerEligibility.verifiedRequest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(shipment.getId()),
        org.mockito.ArgumentMatchers.eq("partner-chn")))
      .thenThrow(new org.springframework.security.access.AccessDeniedException("Ineligible partner"));

    String path = "/api/v1/shipments/" + shipment.getId() + "/delivery-partner";
    mvc.perform(patch(path).header("Authorization", "Bearer location-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"deliveryPartnerId\":\"partner-hyd\",\"deliveryPartnerName\":\"Untrusted Client Name\",\"deliveryPartnerPhone\":\"000\",\"vehicleNumber\":\"FAKE\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryPartnerName").value("Verified Partner"))
      .andExpect(jsonPath("$.deliveryPartnerVehicleNumber").value("QA-VEH-1"));

    mvc.perform(patch(path).header("Authorization", "Bearer location-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"deliveryPartnerId\":\"partner-chn\",\"deliveryPartnerName\":\"Other Store Partner\",\"deliveryPartnerPhone\":\"5550200\",\"vehicleNumber\":\"OTHER\"}"))
      .andExpect(status().isForbidden());
    mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
        .content("{\"deliveryPartnerId\":\"partner-hyd\",\"deliveryPartnerName\":\"Partner\",\"deliveryPartnerPhone\":\"5550100\",\"vehicleNumber\":\"QA-VEH-1\"}"))
      .andExpect(status().isUnauthorized());
    assertThat(shipments.findById(shipment.getId()).orElseThrow().getDeliveryPartnerId()).isEqualTo("partner-hyd");
    assertThat(shipments.findById(shipment.getId()).orElseThrow().getDeliveryPartnerName()).isEqualTo("Verified Partner");
  }
  private Jwt token(String value, UUID customer, String type, String role) {
    return Jwt.withTokenValue(value).header("alg", "HS512").subject(customer.toString())
      .claim("customerId", customer.toString()).claim("accountType", type).claim("roles", List.of(role))
      .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
  }
  @Test void allReadPathsRequireOwnerOrAdmin() throws Exception {
    for (String path : List.of("/api/v1/fulfillments/" + fulfillment.getId(),
      "/api/v1/fulfillments/order/" + fulfillment.getOrderId(),
      "/api/v1/shipments/order/" + fulfillment.getOrderId(),
      "/api/v1/shipments/" + shipment.getId() + "/tracking-events")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
      mvc.perform(get(path).header("Authorization", "Bearer expired")).andExpect(status().isUnauthorized());
      mvc.perform(get(path).header("Authorization", "Bearer stranger")).andExpect(status().isForbidden());
      mvc.perform(get(path).header("Authorization", "Bearer owner")).andExpect(status().isOk());
      mvc.perform(get(path).header("Authorization", "Bearer admin")).andExpect(status().isOk());
    }
  }
  @Test void ownersCannotChangeStatusButAdminCan() throws Exception {
    String path = "/api/v1/fulfillments/" + fulfillment.getId() + "/status";
    mvc.perform(patch(path).header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON)
      .content("{\"status\":\"ALLOCATED\"}")).andExpect(status().isForbidden());
    mvc.perform(patch(path).header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON)
      .content("{\"status\":\"ALLOCATED\"}")).andExpect(status().isOk());
    mvc.perform(patch("/api/v1/shipments/" + shipment.getId() + "/delivered")
      .header("Authorization", "Bearer owner")).andExpect(status().isForbidden());
  }

  @Test void customersCannotForgeShipmentOrTimeline() throws Exception {
    for (String token : List.of("owner", "stranger")) {
      mvc.perform(patch("/api/v1/shipments/" + shipment.getId() + "/status")
        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"IN_TRANSIT\"}")).andExpect(status().isForbidden());
      mvc.perform(post("/api/v1/shipments/" + shipment.getId() + "/tracking-events")
        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"IN_TRANSIT\",\"location\":\"Forged location\",\"description\":\"Forged update\",\"eventTime\":\"2026-10-11T10:00:00Z\"}"))
        .andExpect(status().isForbidden());
    }
  }

  @Test void assignedPartnerCannotForgeStatusOrTrackingAndOrderBoundProxyCanForwardValidatedActions() throws Exception {
    String statusPath="/api/v1/shipments/"+shipment.getId()+"/status";
    String trackingPath="/api/v1/shipments/"+shipment.getId()+"/tracking-events";
    fulfillment.setStatus(FulfillmentStatus.READY_TO_SHIP);
    fulfillments.saveAndFlush(fulfillment);
    mvc.perform(patch(statusPath).header("Authorization","Bearer partner").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"DELIVERED\"}")).andExpect(status().isForbidden());
    mvc.perform(post(trackingPath).header("Authorization","Bearer partner").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"DELIVERED\",\"location\":\"Store\",\"description\":\"Forged completion\",\"eventTime\":\"2026-10-26T00:00:00Z\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(patch(statusPath).header("Authorization","Bearer delivery-proxy").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"IN_TRANSIT\"}")).andExpect(status().isOk());
    mvc.perform(patch(statusPath).header("Authorization","Bearer delivery-proxy").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"OUT_FOR_DELIVERY\"}")).andExpect(status().isOk());
    mvc.perform(post(trackingPath).header("Authorization","Bearer delivery-proxy").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"OUT_FOR_DELIVERY\",\"location\":\"Store\",\"description\":\"Delivery started after pickup\",\"eventTime\":\"2026-10-26T00:00:00Z\"}"))
        .andExpect(status().isCreated());
    assertThat(shipments.findById(shipment.getId()).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
  }

  @Test void globalAdminCanCreateGlobalFulfillment() throws Exception {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID addressId = UUID.randomUUID();
    snapshots.saveAndFlush(new com.metaarch.fulfillment.event.FulfillmentOrderSnapshot(
        orderId, customerId, "global-store", addressId, FulfillmentType.GLOBAL));
    String body = "{\"orderId\":\"%s\",\"customerId\":\"%s\",\"storeId\":\"global-store\",\"shippingAddressId\":\"%s\",\"fulfillmentType\":\"GLOBAL\"}"
        .formatted(orderId, customerId, addressId);

    mvc.perform(post("/api/v1/fulfillments").header("Authorization", "Bearer global-admin")
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orderId").value(orderId.toString()))
        .andExpect(jsonPath("$.fulfillmentType").value("GLOBAL"));
  }

  @Test void isolatedGlobalOrderPersistsFulfillmentAndShipmentThroughApi() throws Exception {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID addressId = UUID.randomUUID();
    snapshots.saveAndFlush(new com.metaarch.fulfillment.event.FulfillmentOrderSnapshot(
        orderId, customerId, "global-store", addressId, FulfillmentType.GLOBAL));
    String fulfillmentBody = "{\"orderId\":\"%s\",\"customerId\":\"%s\",\"storeId\":\"global-store\",\"shippingAddressId\":\"%s\",\"fulfillmentType\":\"GLOBAL\"}"
        .formatted(orderId, customerId, addressId);

    var createdFulfillment = mvc.perform(post("/api/v1/fulfillments").header("Authorization", "Bearer global-admin")
        .contentType(MediaType.APPLICATION_JSON).content(fulfillmentBody))
        .andExpect(status().isCreated()).andReturn();
    UUID fulfillmentId = UUID.fromString(mapper.readTree(createdFulfillment.getResponse().getContentAsString()).get("id").asText());
    assertThat(fulfillments.findByOrderId(orderId)).isPresent();

    for (String next : List.of("ALLOCATED", "PACKING", "READY_TO_SHIP")) {
      mvc.perform(patch("/api/v1/fulfillments/" + fulfillmentId + "/status")
          .header("Authorization", "Bearer global-admin").contentType(MediaType.APPLICATION_JSON)
          .content("{\"status\":\"" + next + "\"}"))
          .andExpect(status().isOk());
    }

    String shipmentBody = "{\"fulfillmentId\":\"%s\",\"carrier\":\"QA Local\",\"trackingNumber\":\"QA-%s\",\"estimatedDeliveryDate\":\"%s\"}"
        .formatted(fulfillmentId, UUID.randomUUID(), LocalDate.now().plusDays(2));
    var createdShipment = mvc.perform(post("/api/v1/shipments").header("Authorization", "Bearer global-admin")
        .contentType(MediaType.APPLICATION_JSON).content(shipmentBody))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.orderId").value(orderId.toString())).andReturn();
    UUID shipmentId = UUID.fromString(mapper.readTree(createdShipment.getResponse().getContentAsString()).get("id").asText());

    mvc.perform(get("/api/v1/fulfillments/order/" + orderId).header("Authorization", "Bearer global-admin"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_TO_SHIP"));
    mvc.perform(get("/api/v1/shipments/order/" + orderId).header("Authorization", "Bearer global-admin"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CREATED"));
    assertThat(shipments.findByOrderId(orderId)).isPresent();

    mvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status")
        .header("Authorization", "Bearer global-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"IN_TRANSIT\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    mvc.perform(get("/api/v1/fulfillments/order/" + orderId).header("Authorization", "Bearer global-admin"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"));

    mvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status")
        .header("Authorization", "Bearer global-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"OUT_FOR_DELIVERY\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));
    mvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status")
        .header("Authorization", "Bearer global-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"DELIVERED\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
    mvc.perform(get("/api/v1/fulfillments/order/" + orderId).header("Authorization", "Bearer global-admin"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
    assertThat(shipments.findByOrderId(orderId)).isPresent();
  }

  @Test void locationAdminCreatesAndPersistsShipmentLifecycleOnlyForAssignedLocation() throws Exception {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID addressId = UUID.randomUUID();
    snapshots.saveAndFlush(new com.metaarch.fulfillment.event.FulfillmentOrderSnapshot(
        orderId, customerId, "store-hyd", addressId, FulfillmentType.SHIP_TO_HOME));
    UUID otherOrderId = UUID.randomUUID();
    snapshots.saveAndFlush(new com.metaarch.fulfillment.event.FulfillmentOrderSnapshot(
        otherOrderId, customerId, "store-chn", addressId, FulfillmentType.SHIP_TO_HOME));
    String body = "{\"orderId\":\"%s\",\"customerId\":\"%s\",\"storeId\":\"store-hyd\",\"shippingAddressId\":\"%s\",\"fulfillmentType\":\"SHIP_TO_HOME\"}"
        .formatted(orderId, customerId, addressId);
    String otherBody = body.replace(orderId.toString(), otherOrderId.toString()).replace("store-hyd", "store-chn");

    mvc.perform(post("/api/v1/fulfillments").header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    mvc.perform(post("/api/v1/fulfillments").header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content(otherBody)).andExpect(status().isForbidden());
    UUID fulfillmentId = fulfillments.findByOrderId(orderId).orElseThrow().getId();

    mvc.perform(post("/api/v1/shipments").header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content(shipmentRequest(fulfillmentId)))
        .andExpect(status().isConflict()); // PENDING is not eligible for shipment creation.
    mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON).content(shipmentRequest(fulfillmentId)))
        .andExpect(status().isUnauthorized());

    for (String next : List.of("ALLOCATED", "PACKING", "READY_TO_SHIP")) {
      mvc.perform(patch("/api/v1/fulfillments/" + fulfillmentId + "/status")
          .header("Authorization", "Bearer location-admin").contentType(MediaType.APPLICATION_JSON)
          .content("{\"status\":\"" + next + "\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
    }

    String request = shipmentRequest(fulfillmentId);
    var created = mvc.perform(post("/api/v1/shipments").header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.orderId").value(orderId.toString()))
        .andExpect(jsonPath("$.status").value("CREATED")).andReturn();
    UUID shipmentId = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
    var repeated = mvc.perform(post("/api/v1/shipments").header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(shipmentId.toString())).andReturn();
    assertThat(mapper.readTree(repeated.getResponse().getContentAsString()).get("trackingNumber").asText())
        .isEqualTo(shipments.findById(shipmentId).orElseThrow().getTrackingNumber());
    assertThat(shipments.findByOrderId(orderId)).isPresent();

    mvc.perform(get("/api/v1/shipments/order/" + orderId).header("Authorization", "Bearer location-admin"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(shipmentId.toString()));
    mvc.perform(get("/api/v1/shipments/order/" + otherOrderId).header("Authorization", "Bearer location-admin"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/shipments/order/not-a-uuid").header("Authorization", "Bearer location-admin"))
        .andExpect(status().isBadRequest());
    UUID missingOrder = UUID.randomUUID();
    snapshots.saveAndFlush(new com.metaarch.fulfillment.event.FulfillmentOrderSnapshot(
        missingOrder, customerId, "store-hyd", addressId, FulfillmentType.SHIP_TO_HOME));
    mvc.perform(get("/api/v1/shipments/order/" + missingOrder).header("Authorization", "Bearer location-admin"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/shipments/order/" + orderId)).andExpect(status().isUnauthorized());

    mvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status")
        .header("Authorization", "Bearer location-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"IN_TRANSIT\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    assertThat(fulfillments.findById(fulfillmentId).orElseThrow().getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
    mvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status")
        .header("Authorization", "Bearer location-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"DELIVERED\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
    assertThat(shipments.findById(shipmentId).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    assertThat(fulfillments.findById(fulfillmentId).orElseThrow().getStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
    mvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status")
        .header("Authorization", "Bearer location-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"IN_TRANSIT\"}"))
        .andExpect(status().isConflict());
    mvc.perform(patch("/api/v1/shipments/" + shipmentId + "/delivery-partner")
        .header("Authorization", "Bearer location-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"deliveryPartnerId\":\"partner-hyd\",\"deliveryPartnerName\":\"Verified QA Partner\"}"))
        .andExpect(status().isConflict());
  }

  @Test void failedShipmentRetryValidatesPartnerBeforeChangingStateAndPersistsAssignment() throws Exception {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID addressId = UUID.randomUUID();
    snapshots.saveAndFlush(new com.metaarch.fulfillment.event.FulfillmentOrderSnapshot(
        orderId, customerId, "store-hyd", addressId, FulfillmentType.SHIP_TO_HOME));
    String body = "{\"orderId\":\"%s\",\"customerId\":\"%s\",\"storeId\":\"store-hyd\",\"shippingAddressId\":\"%s\",\"fulfillmentType\":\"SHIP_TO_HOME\"}"
        .formatted(orderId, customerId, addressId);
    mvc.perform(post("/api/v1/fulfillments").header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    UUID fulfillmentId = fulfillments.findByOrderId(orderId).orElseThrow().getId();
    for (String next : List.of("ALLOCATED", "PACKING", "READY_TO_SHIP")) {
      mvc.perform(patch("/api/v1/fulfillments/" + fulfillmentId + "/status")
          .header("Authorization", "Bearer location-admin").contentType(MediaType.APPLICATION_JSON)
          .content("{\"status\":\"" + next + "\"}"));
    }
    var created = mvc.perform(post("/api/v1/shipments").header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content(shipmentRequest(fulfillmentId)))
        .andExpect(status().isCreated()).andReturn();
    UUID shipmentId = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
    mvc.perform(patch("/api/v1/shipments/" + shipmentId + "/status")
        .header("Authorization", "Bearer location-admin").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"FAILED\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));

    when(partnerEligibility.verifiedRequest(any(), eq(shipmentId), eq("partner-other")))
        .thenThrow(new org.springframework.security.access.AccessDeniedException("Ineligible test partner"));
    String assignmentPath = "/api/v1/shipments/" + shipmentId + "/delivery-partner";
    mvc.perform(patch(assignmentPath).header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content("{\"deliveryPartnerId\":\"partner-other\",\"deliveryPartnerName\":\"Spoofed\"}"))
        .andExpect(status().isForbidden());
    assertThat(shipments.findById(shipmentId).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.FAILED);
    mvc.perform(patch(assignmentPath).contentType(MediaType.APPLICATION_JSON)
        .content("{\"deliveryPartnerId\":\"partner-hyd\",\"deliveryPartnerName\":\"Spoofed\"}"))
        .andExpect(status().isUnauthorized());

    when(partnerEligibility.verifiedRequest(any(), eq(shipmentId), eq("partner-hyd")))
        .thenReturn(new AssignDeliveryPartnerRequest("partner-hyd", "Verified QA Partner", "5550100", "QA-VEH-1"));
    mvc.perform(patch(assignmentPath).header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content("{\"deliveryPartnerId\":\"partner-hyd\",\"deliveryPartnerName\":\"Spoofed\",\"deliveryPartnerPhone\":\"000\",\"vehicleNumber\":\"FAKE\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CREATED"))
        .andExpect(jsonPath("$.deliveryPartnerName").value("Verified QA Partner"))
        .andExpect(jsonPath("$.deliveryPartnerVehicleNumber").value("QA-VEH-1"));
    var saved = shipments.findById(shipmentId).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(ShipmentStatus.CREATED);
    assertThat(saved.getDeliveryPartnerId()).isEqualTo("partner-hyd");
    assertThat(saved.getDeliveryPartnerName()).isEqualTo("Verified QA Partner");
    assertThat(shipments.findAll().stream().filter(row -> orderId.equals(row.getOrderId())).count()).isEqualTo(1);
    mvc.perform(get("/api/v1/shipments/order/" + orderId).header("Authorization", "Bearer location-admin"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryPartnerId").value("partner-hyd"))
        .andExpect(jsonPath("$.deliveryPartnerName").value("Verified QA Partner"));
    mvc.perform(patch(assignmentPath).header("Authorization", "Bearer location-admin")
        .contentType(MediaType.APPLICATION_JSON).content("{\"deliveryPartnerId\":\"partner-hyd\",\"deliveryPartnerName\":\"Spoofed\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CREATED"));
    assertThat(shipments.findAll().stream().filter(row -> orderId.equals(row.getOrderId())).count()).isEqualTo(1);
  }

  private String shipmentRequest(UUID fulfillmentId) {
    return "{\"fulfillmentId\":\"%s\",\"carrier\":\"Local test adapter\",\"trackingNumber\":\"QA-%s\",\"estimatedDeliveryDate\":\"%s\"}"
        .formatted(fulfillmentId, UUID.randomUUID(), LocalDate.now().plusDays(2));
  }
  @Test void managersAreBoundToPersistedStoreAndSnapshot() throws Exception {
    for (String store : List.of("store", "foreign")) {
      when(decoder.decode(store)).thenReturn(Jwt.withTokenValue(store).header("alg", "HS512").subject("manager")
          .claim("accountType", "staff").claim("authorities", List.of("ROLE_STORE_MANAGER", "SCOPE_STORE:" + store)).build());
    }
    var path = "/api/v1/fulfillments/" + fulfillment.getId();
    mvc.perform(get(path).header("Authorization", "Bearer foreign")).andExpect(status().isForbidden());
    mvc.perform(get(path).header("Authorization", "Bearer store")).andExpect(status().isOk());
    mvc.perform(patch(path + "/status").header("Authorization", "Bearer foreign").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"ALLOCATED\"}")).andExpect(status().isForbidden());
    mvc.perform(patch(path + "/status").header("Authorization", "Bearer store").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"ALLOCATED\"}")).andExpect(status().isOk());
    var id = UUID.randomUUID(); var address = UUID.randomUUID();
    snapshots.save(new com.metaarch.fulfillment.event.FulfillmentOrderSnapshot(id, owner, "store", address, FulfillmentType.SHIP_TO_HOME));
    String body = "{\"orderId\":\"%s\",\"customerId\":\"%s\",\"storeId\":\"store\",\"shippingAddressId\":\"%s\",\"fulfillmentType\":\"SHIP_TO_HOME\"}".formatted(id, owner, address);
    mvc.perform(post("/api/v1/fulfillments").header("Authorization", "Bearer store").contentType(MediaType.APPLICATION_JSON)
        .content(body.replace(owner.toString(), UUID.randomUUID().toString()))).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/fulfillments").header("Authorization", "Bearer store").contentType(MediaType.APPLICATION_JSON)
        .content(body)).andExpect(status().isCreated());
  }
}
