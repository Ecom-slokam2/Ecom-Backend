package com.metaarch.fulfillment.shipment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.metaarch.fulfillment.fulfillment.Fulfillment;
import com.metaarch.fulfillment.fulfillment.FulfillmentRepository;
import com.metaarch.fulfillment.fulfillment.FulfillmentType;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:eligibility_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:sync-schema.sql",
    "sync.dispatch-enabled=false",
    "sync.service=fulfillment-service",
    "sync.routes.fulfillment=notification-service",
    "sync.targets.notification-service=http://127.0.0.1:1"
})
@AutoConfigureMockMvc
class DeliveryPartnerEligibilityHttpTest {
  private static final AtomicReference<String> lastAuthorization = new AtomicReference<>();
  private static final HttpServer directory = startDirectory();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("ORG_ACCESS_URL", () -> "http://127.0.0.1:" + directory.getAddress().getPort());
  }

  @Autowired MockMvc mvc;
  @Autowired FulfillmentRepository fulfillments;
  @Autowired ShipmentRepository shipments;
  @Autowired CarrierRetryAttemptRepository retryAttempts;
  @MockBean JwtDecoder decoder;
  @MockBean com.metaarch.sync.CatalogLocationClient catalogLocations;
  @MockBean CarrierRetryAdapter retryAdapter;
  @MockBean CarrierRetryDelay retryDelay;
  private Shipment shipment;

  @BeforeEach
  void setup() {
    shipments.deleteAll();
    retryAttempts.deleteAll();
    fulfillments.deleteAll();
    lastAuthorization.set(null);
    UUID customer = UUID.randomUUID();
    Fulfillment fulfillment = fulfillments.saveAndFlush(new Fulfillment(UUID.randomUUID(), customer,
        "qa-la27-hyd-store", FulfillmentType.SHIP_TO_HOME, UUID.randomUUID()));
    shipment = shipments.saveAndFlush(new Shipment(fulfillment, "QA Local", "QA-LA27-" + UUID.randomUUID(),
        LocalDate.now().plusDays(2)));

    reset(decoder, catalogLocations, retryAdapter, retryDelay);
    when(decoder.decode("hyd-admin")).thenReturn(locationToken("hyd-admin", "qa-location-admin", "Hyderabad"));
    when(decoder.decode("chn-admin")).thenReturn(locationToken("chn-admin", "qa-chennai-admin", "Chennai"));
    when(decoder.decode("bad-token")).thenThrow(new BadJwtException("Invalid test token"));
    when(catalogLocations.locationOf("qa-la27-hyd-store")).thenReturn(Optional.of("Hyderabad"));
  }

  @Test
  void carrierRetryPersistsSuccessAndIdempotentReplayForLocationAdmin() throws Exception {
    shipment.setStatus(ShipmentStatus.FAILED);
    shipments.saveAndFlush(shipment);
    when(retryAdapter.supportsIdempotencyKey()).thenReturn(true);
    when(retryAdapter.submit(any(), any(), eq("qa-retry-success"))).thenReturn(new CarrierSubmissionResult(202));
    String path = "/api/v1/shipments/" + shipment.getId() + "/carrier-retry";
    String body = "{\"idempotencyKey\":\"qa-retry-success\",\"deliveryPartnerId\":\"qa-la27-partner-hyd\"}";

    mvc.perform(post(path).header("Authorization", "Bearer hyd-admin").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SUCCEEDED"))
        .andExpect(jsonPath("$.attempts").value(1)).andExpect(jsonPath("$.history[0].carrierStatus").value(202));
    mvc.perform(post(path).header("Authorization", "Bearer hyd-admin").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SUCCEEDED"));

    verify(retryAdapter, times(1)).submit(any(), any(), eq("qa-retry-success"));
    org.assertj.core.api.Assertions.assertThat(retryAttempts.countByShipmentId(shipment.getId())).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(shipments.findById(shipment.getId()).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.CREATED);
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path.replace("carrier-retry", "carrier-attempts"))
            .header("Authorization", "Bearer hyd-admin"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].outcome").value("SUCCEEDED"));
  }

  @Test
  void carrierRetryRetriesOnlyTransientFailuresAndStopsAtThreeAttempts() throws Exception {
    shipment.setStatus(ShipmentStatus.FAILED);
    shipments.saveAndFlush(shipment);
    when(retryAdapter.supportsIdempotencyKey()).thenReturn(true);
    when(retryAdapter.submit(any(), any(), eq("qa-retry-transient")))
        .thenReturn(new CarrierSubmissionResult(503))
        .thenReturn(new CarrierSubmissionResult(429))
        .thenReturn(new CarrierSubmissionResult(202));
    String path = "/api/v1/shipments/" + shipment.getId() + "/carrier-retry";
    String body = "{\"idempotencyKey\":\"qa-retry-transient\",\"deliveryPartnerId\":\"qa-la27-partner-hyd\"}";
    mvc.perform(post(path).header("Authorization", "Bearer hyd-admin").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SUCCEEDED"))
        .andExpect(jsonPath("$.attempts").value(3)).andExpect(jsonPath("$.history.length()").value(3));
    verify(retryAdapter, times(3)).submit(any(), any(), eq("qa-retry-transient"));
    verify(retryDelay).beforeAttempt(2);
    verify(retryDelay).beforeAttempt(3);

    retryAttempts.deleteAll();
    shipment.setStatus(ShipmentStatus.FAILED);
    shipments.saveAndFlush(shipment);
    when(retryAdapter.submit(any(), any(), eq("qa-retry-exhausted"))).thenReturn(new CarrierSubmissionResult(503));
    String exhausted = "{\"idempotencyKey\":\"qa-retry-exhausted\",\"deliveryPartnerId\":\"qa-la27-partner-hyd\"}";
    mvc.perform(post(path).header("Authorization", "Bearer hyd-admin").contentType(MediaType.APPLICATION_JSON).content(exhausted))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("EXHAUSTED"));
    mvc.perform(post(path).header("Authorization", "Bearer hyd-admin").contentType(MediaType.APPLICATION_JSON).content(exhausted))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("EXHAUSTED"));
    verify(retryAdapter, times(3)).submit(any(), any(), eq("qa-retry-exhausted"));
  }

  @Test
  void carrierRetryDoesNotRetryPermanentOrAmbiguousFailuresAndEnforcesScope() throws Exception {
    shipment.setStatus(ShipmentStatus.FAILED);
    shipments.saveAndFlush(shipment);
    when(retryAdapter.supportsIdempotencyKey()).thenReturn(true);
    when(retryAdapter.submit(any(), any(), eq("qa-retry-permanent"))).thenReturn(new CarrierSubmissionResult(400));
    when(retryAdapter.submit(any(), any(), eq("qa-retry-unknown"))).thenThrow(new CarrierRetryException(504, "TIMEOUT", true));
    String path = "/api/v1/shipments/" + shipment.getId() + "/carrier-retry";
    String permanent = "{\"idempotencyKey\":\"qa-retry-permanent\",\"deliveryPartnerId\":\"qa-la27-partner-hyd\"}";
    mvc.perform(post(path).header("Authorization", "Bearer hyd-admin").contentType(MediaType.APPLICATION_JSON).content(permanent))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PERMANENT_FAILURE"));
    verify(retryAdapter, times(1)).submit(any(), any(), eq("qa-retry-permanent"));

    // A permanent failure is terminal for the shipment; use a fresh fixture state for timeout ambiguity.
    retryAttempts.deleteAll();
    shipment.setStatus(ShipmentStatus.FAILED);
    shipments.saveAndFlush(shipment);
    String unknown = "{\"idempotencyKey\":\"qa-retry-unknown\",\"deliveryPartnerId\":\"qa-la27-partner-hyd\"}";
    mvc.perform(post(path).header("Authorization", "Bearer hyd-admin").contentType(MediaType.APPLICATION_JSON).content(unknown))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("UNKNOWN"));
    mvc.perform(post(path).header("Authorization", "Bearer hyd-admin").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"new-key-after-unknown\",\"deliveryPartnerId\":\"qa-la27-partner-hyd\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("UNKNOWN"));
    verify(retryAdapter, times(1)).submit(any(), any(), eq("qa-retry-unknown"));

    mvc.perform(post(path).header("Authorization", "Bearer chn-admin").contentType(MediaType.APPLICATION_JSON).content(unknown))
        .andExpect(status().isForbidden());
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(unknown)).andExpect(status().isUnauthorized());
    verify(retryAdapter, never()).submit(any(), any(), eq("new-key-after-unknown"));

    retryAttempts.deleteAll();
    retryAttempts.saveAndFlush(new CarrierRetryAttempt(shipment.getId(), "crashed-operation", 1));
    mvc.perform(post(path).header("Authorization", "Bearer hyd-admin").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"qa-retry-after-crash\",\"deliveryPartnerId\":\"qa-la27-partner-hyd\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("UNKNOWN"));
    verify(retryAdapter, never()).submit(any(), any(), eq("qa-retry-after-crash"));
  }

  @Test
  void locationAdminAssignmentLoadsLazyStorePersistsAndRejectsCrossScopeAndAnonymousRequests() throws Exception {
    String path = "/api/v1/shipments/" + shipment.getId() + "/delivery-partner";
    String request = "{\"deliveryPartnerId\":\"qa-la27-partner-hyd\",\"deliveryPartnerName\":\"untrusted\",\"deliveryPartnerPhone\":\"000\",\"vehicleNumber\":\"forged\"}";

    mvc.perform(patch(path).header("Authorization", "Bearer hyd-admin")
            .contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deliveryPartnerId").value("qa-la27-partner-hyd"))
        .andExpect(jsonPath("$.deliveryPartnerName").value("QA Hyderabad Partner"));
    org.assertj.core.api.Assertions.assertThat(lastAuthorization.get()).isEqualTo("Bearer hyd-admin");
    org.assertj.core.api.Assertions.assertThat(shipments.findById(shipment.getId()).orElseThrow().getDeliveryPartnerId())
        .isEqualTo("qa-la27-partner-hyd");

    String crossStorePartner = "{\"deliveryPartnerId\":\"qa-la27-partner-chn\",\"deliveryPartnerName\":\"Chennai\"}";
    mvc.perform(patch(path).header("Authorization", "Bearer hyd-admin")
            .contentType(MediaType.APPLICATION_JSON).content(crossStorePartner))
        .andExpect(status().isForbidden());
    mvc.perform(patch(path).header("Authorization", "Bearer chn-admin")
            .contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isForbidden());
    mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isUnauthorized());

    org.assertj.core.api.Assertions.assertThat(shipments.findById(shipment.getId()).orElseThrow().getDeliveryPartnerId())
        .isEqualTo("qa-la27-partner-hyd");
  }

  private static Jwt locationToken(String value, String subject, String location) {
    String role = "ROLE_LOCATION_ADMIN";
    return Jwt.withTokenValue(value).header("alg", "HS512").subject(subject)
        .claim("accountType", "staff").claim("roles", List.of(role))
        .claim("authorities", List.of(role, "SCOPE_LOCATION:" + location)).build();
  }

  private static HttpServer startDirectory() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/api/delivery-partners", exchange -> {
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] body = "[{\"id\":\"qa-la27-partner-hyd\",\"name\":\"QA Hyderabad Partner\",\"mobile\":\"9000000027\",\"vehicleNumber\":\"QA-27\",\"storeId\":\"qa-la27-hyd-store\",\"status\":\"Active\",\"active\":true}]"
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
      });
      server.start();
      return server;
    } catch (Exception error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  @AfterAll
  static void stopDirectory() {
    directory.stop(0);
  }
}
