package com.metaarch.order.order;

import com.metaarch.order.event.*;
import com.metaarch.sync.SyncAuthentication;
import com.metaarch.sync.SyncEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
  "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
  "spring.datasource.url=jdbc:h2:mem:order_sync_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
  "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
  "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:sync-schema.sql",
  "sync.dispatch-enabled=false", "sync.keys.payment-service=test-payment-key-at-least-32-characters",
  "sync.keys.fulfillment-service=test-fulfillment-key-at-least-32-characters",
  "sync.service=order-service", "sync.routes.order=payment-service,fulfillment-service,notification-service",
  "sync.targets.payment-service=http://127.0.0.1:1", "sync.targets.fulfillment-service=http://127.0.0.1:1", "sync.targets.notification-service=http://127.0.0.1:1"
})
class PaymentHttpIntegrationTest {
  @org.springframework.boot.test.mock.mockito.MockBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
  @org.springframework.boot.test.mock.mockito.MockBean FulfillmentDeliveryClient deliveryClient;
  @org.springframework.boot.test.mock.mockito.MockBean FulfillmentLifecycleVerifier fulfillmentLifecycleVerifier;
  @org.springframework.boot.test.mock.mockito.MockBean ReviewDeliveryVerifier reviewDeliveryVerifier;
  @Autowired OrderRepository orders;
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper mapper;
  @Autowired SyncAuthentication authentication;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrderCreation creation;
  @Autowired OrderService service;
  @org.springframework.boot.test.mock.mockito.MockBean CatalogDeliveryQuoteClient catalog;
  @org.springframework.boot.test.mock.mockito.MockBean com.metaarch.sync.CatalogLocationClient locationCatalog;
  @org.springframework.boot.test.mock.mockito.MockBean CheckoutCartClient cart;

  @Test void reviewsRetainAuthenticationOwnershipAndDeliveryGuards() {
    UUID owner = UUID.randomUUID();
    Order order = orders.saveAndFlush(new Order(owner, UUID.randomUUID(), "test-store", UUID.randomUUID().toString()));
    String path = "/api/v1/orders/" + order.getId() + "/review";
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String body = "{\"rating\":5,\"comment\":\"Great service\",\"photoUrls\":[]}";
    assertThat(http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    headers.setBearerAuth(UUID.randomUUID().toString());
    assertThat(http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    headers.setBearerAuth(owner.toString());
    var rejected = http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(rejected.getBody()).contains("A review can be submitted after delivery is complete.");
  }

  @org.junit.jupiter.api.BeforeEach void prepareCreationSchema() {
    org.mockito.Mockito.reset(decoder, deliveryClient, locationCatalog, fulfillmentLifecycleVerifier);
    org.mockito.Mockito.when(reviewDeliveryVerifier.isDelivered(org.mockito.ArgumentMatchers.any())).thenReturn(false);
    org.mockito.Mockito.when(fulfillmentLifecycleVerifier.shipmentIs(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
    org.mockito.Mockito.when(fulfillmentLifecycleVerifier.shipmentHasStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anySet())).thenReturn(false);
    org.mockito.Mockito.when(decoder.decode(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call ->
      org.springframework.security.oauth2.jwt.Jwt.withTokenValue(call.getArgument(0)).header("alg", "HS256")
        .subject("test-owner").claim("customerId", call.getArgument(0).toString())
        .claim("accountType", "admin".equals(call.getArgument(0)) ? "staff" : "customer")
        .claim("roles", java.util.List.of("admin".equals(call.getArgument(0)) ? "ROLE_ADMIN" : "ROLE_CUSTOMER")).build());
    org.mockito.Mockito.when(decoder.decode("location-admin")).thenReturn(
      org.springframework.security.oauth2.jwt.Jwt.withTokenValue("location-admin").header("alg", "HS512")
        .subject("location-admin").claim("accountType", "staff")
        .claim("authorities", java.util.List.of("ROLE_LOCATION_ADMIN", "SCOPE_LOCATION:Hyderabad")).build());
    for (String store : java.util.List.of("test-store", "other-store")) {
      org.mockito.Mockito.when(decoder.decode("manager-" + store)).thenReturn(
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("manager-" + store).header("alg", "HS512")
          .subject("test-manager").claim("accountType", "staff")
          .claim("authorities", java.util.List.of("ROLE_STORE_MANAGER", "SCOPE_STORE:" + store)).build());
    }
    org.mockito.Mockito.when(locationCatalog.locationOf("store-hyd")).thenReturn(java.util.Optional.of("Hyderabad"));
    org.mockito.Mockito.when(locationCatalog.locationOf("store-chn")).thenReturn(java.util.Optional.of("Chennai"));
    jdbc.execute("CREATE TABLE IF NOT EXISTS order_creation_requests(customer_id VARCHAR(36),request_key VARCHAR(128),request_hash VARCHAR(64),order_id VARCHAR(36) UNIQUE,PRIMARY KEY(customer_id,request_key))");
    jdbc.update("DELETE FROM order_creation_requests");
    jdbc.update("DELETE FROM sync_inbox");
    jdbc.update("DELETE FROM sync_outbox");
    org.mockito.Mockito.reset(catalog);
    org.mockito.Mockito.reset(cart);
    org.mockito.Mockito.when(catalog.verifyItem(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean()))
      .thenAnswer(call -> call.getArgument(1));
  }

  @Test void locationAdminCanReadSameLocationOrderDetailsAndItemsOnly() {
    Order same = new Order(UUID.randomUUID(), UUID.randomUUID(), "store-hyd", "HYD-" + UUID.randomUUID().toString().substring(0, 12));
    same.addItem(new OrderItem(UUID.randomUUID(), null, "QA item", 2, new BigDecimal("12.50")));
    same = orders.saveAndFlush(same);
    Order other = new Order(UUID.randomUUID(), UUID.randomUUID(), "store-chn", "CHN-" + UUID.randomUUID().toString().substring(0, 12));
    other.addItem(new OrderItem(UUID.randomUUID(), null, "Other QA item", 1, new BigDecimal("5.00")));
    other = orders.saveAndFlush(other);

    var anonymous = new HttpHeaders();
    String samePath = "/api/v1/orders/" + same.getId();
    String otherPath = "/api/v1/orders/" + other.getId();
    assertThat(http.exchange(samePath, HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(http.exchange(samePath + "/items", HttpMethod.GET, new HttpEntity<>(anonymous), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    var headers = new HttpHeaders();
    headers.setBearerAuth("location-admin");
    assertThat(http.exchange(samePath, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    var items = http.exchange(samePath + "/items", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(items.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(items.getBody()).contains("QA item");
    assertThat(http.exchange(otherPath, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange(otherPath + "/items", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    headers.setBearerAuth("admin");
    assertThat(http.exchange(otherPath, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test void locationAdminCanConfirmOnlyAnEligibleOrderInAssignedLocation() {
    Order same = new Order(UUID.randomUUID(), UUID.randomUUID(), "store-hyd", "HYD-QA-" + UUID.randomUUID().toString().substring(0, 8));
    same.addItem(new OrderItem(UUID.randomUUID(), null, "Disposable authorization QA item", 1, new BigDecimal("10.00")));
    same = orders.saveAndFlush(same);
    Order other = new Order(UUID.randomUUID(), UUID.randomUUID(), "store-chn", "CHN-QA-" + UUID.randomUUID().toString().substring(0, 8));
    other.addItem(new OrderItem(UUID.randomUUID(), null, "Cross-location QA item", 1, new BigDecimal("5.00")));
    other = orders.saveAndFlush(other);

    String samePath = "/api/v1/orders/" + same.getId();
    String otherPath = "/api/v1/orders/" + other.getId();
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    assertThat(http.exchange(samePath + "/status", HttpMethod.PATCH,
      new HttpEntity<>("{\"status\":\"CONFIRMED\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    headers.setBearerAuth("location-admin");
    assertThat(http.exchange(otherPath + "/status", HttpMethod.PATCH,
      new HttpEntity<>("{\"status\":\"CONFIRMED\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange(samePath + "/status", HttpMethod.PATCH,
      new HttpEntity<>("{\"status\":\"DELIVERED\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(http.exchange(samePath + "/status", HttpMethod.PATCH,
      new HttpEntity<>("{\"status\":\"CONFIRMED\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(orders.findById(same.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(orders.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
  }

  private CreateOrderRequest pickupRequest() throws Exception {
    return mapper.readValue("""
      {"customerId":"%s","cartId":"%s","storeId":"test-store",
       "items":[{"productId":"%s","productName":"Test product","quantity":1,"unitPrice":10}],
       "deliveryMethod":"pickup","fulfillmentType":"pickup"}
      """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), CreateOrderRequest.class);
  }
  @Test void managerStatusPersistsAndCustomerReadsItWhileOtherStoreIsDenied() throws Exception {
    var payload = pickupRequest();
    var order = creation.create(UUID.randomUUID().toString(), payload);
    var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth("manager-other-store");
    String path = "/api/v1/orders/" + order.id();
    assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange(path + "/status", HttpMethod.PATCH, new HttpEntity<>("{\"status\":\"CONFIRMED\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    headers.setBearerAuth("manager-test-store");
    assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(http.exchange(path + "/items", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(http.exchange("/api/v1/orders?storeId=test-store", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(http.exchange("/api/v1/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange(path + "/status", HttpMethod.PATCH, new HttpEntity<>("{\"status\":\"CONFIRMED\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(orders.findById(order.id()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    headers.setBearerAuth(payload.customerId().toString());
    assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), OrderResponse.class).getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test void repeatedHttpCreationReturnsSameOrderAndRejectsChangedPayload() throws Exception {
    var payload = pickupRequest();
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Idempotency-Key", UUID.randomUUID().toString());
    var body = mapper.writeValueAsString(payload);
    headers.setBearerAuth(payload.customerId().toString());
    var first = http.postForEntity("/api/v1/orders", new HttpEntity<>(body, headers), OrderResponse.class);
    var retry = http.postForEntity("/api/v1/orders", new HttpEntity<>(body, headers), OrderResponse.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(retry.getBody().id()).isEqualTo(first.getBody().id());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_creation_requests", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class)).isEqualTo(3);
    assertThat(http.postForEntity("/api/v1/orders", new HttpEntity<>(body.replace("test-store", "another-store"), headers), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    headers.remove("Idempotency-Key");
    assertThat(http.postForEntity("/api/v1/orders", new HttpEntity<>(body, headers), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test void concurrentCreationReusesCommittedOrder() throws Exception {
    var payload = pickupRequest();
    String key = UUID.randomUUID().toString();
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> creation.create(key, payload));
      var second = executor.submit(() -> creation.create(key, payload));
      assertThat(second.get().id()).isEqualTo(first.get().id());
      assertThat(creation.create(key, payload).id()).isEqualTo(first.get().id());
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_creation_requests", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class)).isEqualTo(3);
  }

  @Test void differentRetryKeysForSameCartDoNotCreateAnotherOrder() throws Exception {
    var payload=pickupRequest();
    var first=creation.create(UUID.randomUUID().toString(),payload);
    var second=creation.create(UUID.randomUUID().toString(),payload);
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(orders.findByCartId(payload.cartId())).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox",Integer.class)).isEqualTo(3);
  }

  @Test void deniedCartClaimCreatesNoOrderRequestOrDownstreamEvent() throws Exception {
    var payload=pickupRequest(); long before=orders.count();
    org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("Cart owned by another customer"))
      .when(cart).claim(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    var headers=new HttpHeaders();headers.setBearerAuth(payload.customerId().toString());headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Idempotency-Key",UUID.randomUUID().toString());
    assertThat(http.postForEntity("/api/v1/orders",new HttpEntity<>(mapper.writeValueAsString(payload),headers),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(orders.count()).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_creation_requests",Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox",Integer.class)).isZero();
    org.mockito.Mockito.verifyNoInteractions(catalog);
  }

  @Test void missingAuthenticationAndForgedCustomerFailBeforeCartOrOrderWrites() throws Exception {
    var payload=pickupRequest();long before=orders.count();var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Idempotency-Key",UUID.randomUUID().toString());String body=mapper.writeValueAsString(payload);
    assertThat(http.postForEntity("/api/v1/orders",new HttpEntity<>(body,headers),String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    headers.setBearerAuth(UUID.randomUUID().toString());
    assertThat(http.postForEntity("/api/v1/orders",new HttpEntity<>(body,headers),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(orders.count()).isEqualTo(before);org.mockito.Mockito.verifyNoInteractions(cart,catalog);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox",Integer.class)).isZero();
  }

  @Test void catalogFailureRollsBackRequestAndCanRetrySameKey() throws Exception {
    var payload = pickupRequest();
    String key = UUID.randomUUID().toString();
    org.mockito.Mockito.when(catalog.verifyItem(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean()))
      .thenThrow(new IllegalArgumentException("catalog unavailable"));
    assertThatThrownBy(() -> creation.create(key, payload)).isInstanceOf(IllegalArgumentException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_creation_requests", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class)).isZero();
    org.mockito.Mockito.doAnswer(call -> call.getArgument(1)).when(catalog).verifyItem(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
    assertThat(creation.create(key, payload).id()).isNotNull();
  }

  @Test void missingCreationSchemaFailsBeforeOrderOrOutboxWrites() throws Exception {
    var payload = pickupRequest();
    long before = orders.count();
    jdbc.execute("DROP TABLE order_creation_requests");
    try {
      assertThatThrownBy(() -> creation.create(UUID.randomUUID().toString(), payload))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(orders.count()).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class)).isZero();
    } finally { prepareCreationSchema(); }
  }

  @Test void concurrentIdenticalStatusTransitionsEmitOneEvent() throws Exception {
    Order order = orders.save(new Order(UUID.randomUUID(), UUID.randomUUID(), "store", "ORD-CONCURRENT-STATUS"));
    var update = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.updateStatus(order.getId(), update));
      var second = executor.submit(() -> service.updateStatus(order.getId(), update));
      assertThat(first.get().status()).isEqualTo(OrderStatus.CONFIRMED);
      assertThat(second.get().status()).isEqualTo(OrderStatus.CONFIRMED);
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class)).isEqualTo(3);
  }

  @Test void authenticatedDuplicatePaymentUpdatesOrderAndOutboxOnce() throws Exception {
    Order order = orders.save(new Order(UUID.randomUUID(), UUID.randomUUID(), "store-1", "ORD-HTTP"));
    order.setTotalAmount(new BigDecimal("40.00"));
    orders.save(order);
    UUID eventId = UUID.randomUUID();
    var event = new PaymentEvent(eventId, PaymentEventType.PAYMENT_SUCCESS, UUID.randomUUID(), order.getId(),
      order.getCustomerId(), "provider-reference", new BigDecimal("40.00"), "INR", PaymentEventStatus.SUCCESS, Instant.now());
    byte[] body = mapper.writeValueAsBytes(new SyncEnvelope(eventId, "payment-service", "payment", order.getId().toString(), mapper.valueToTree(event)));
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Sync-Source", "payment-service");
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    headers.set("X-Sync-Time", timestamp);
    headers.set("X-Sync-Signature", authentication.sign("payment-service", timestamp, body));
    var request = new HttpEntity<>(body, headers);
    assertThat(http.postForEntity("/internal/events", request, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(http.postForEntity("/internal/events", request, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(orders.findById(order.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_inbox", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class)).isEqualTo(3);
  }

  @Test void missingInternalAuthenticationIsRejected() {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    assertThat(http.postForEntity("/internal/events", new HttpEntity<>("{}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test void rejectedJwtAndCancellationAreSafe() {
    UUID customer = UUID.randomUUID();
    var order = orders.save(new Order(customer, UUID.randomUUID(), "store", "ORD-CANCEL-" + UUID.randomUUID().toString().substring(0, 20)));
    String path = "/api/v1/orders/" + order.getId();
    var headers = new HttpHeaders();
    for (String token : java.util.List.of("invalid", "expired")) {
      org.mockito.Mockito.when(decoder.decode(token)).thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("Rejected token"));
      headers.setBearerAuth(token);
      assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(http.postForEntity(path + "/cancel", new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    headers.setBearerAuth(UUID.randomUUID().toString());
    assertThat(http.postForEntity(path + "/cancel", new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
    headers.setBearerAuth(customer.toString());
    assertThat(http.postForEntity(path + "/cancel", new HttpEntity<>(headers), OrderResponse.class).getBody().status()).isEqualTo(OrderStatus.CANCELLED);
    int events = jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class);
    assertThat(http.postForEntity(path + "/cancel", new HttpEntity<>(headers), OrderResponse.class).getBody().status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class)).isEqualTo(events);
  }

  @Test void ownerIsolationAndPublicStatusProtection() {
    UUID customer = UUID.randomUUID();
    var order = orders.save(new Order(customer, UUID.randomUUID(), "store", "ORD-OWNERSHIP"));
    String path = "/api/v1/orders/" + order.getId();
    assertThat(http.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    var headers = new HttpHeaders();
    headers.setBearerAuth(customer.toString());
    assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    headers.setBearerAuth(UUID.randomUUID().toString());
    assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange(path + "/items", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange("/api/v1/orders/customer/" + customer + "/history", HttpMethod.GET,
      new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    headers.setBearerAuth(customer.toString());
    headers.setContentType(MediaType.APPLICATION_JSON);
    assertThat(http.exchange(path + "/status", HttpMethod.PATCH, new HttpEntity<>("{\"status\":\"CONFIRMED\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange(path + "/payment-status", HttpMethod.PATCH, new HttpEntity<>("{\"paymentStatus\":\"SUCCESS\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(http.exchange("/api/v1/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    headers.setBearerAuth("admin");
    assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(http.exchange("/api/v1/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(http.exchange(path + "/status", HttpMethod.PATCH, new HttpEntity<>("{\"status\":\"CONFIRMED\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(http.exchange(path + "/payment-status", HttpMethod.PATCH, new HttpEntity<>("{\"paymentStatus\":\"SUCCESS\"}", headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private ResponseEntity<String> deliver(Order order, UUID eventId, String signingSource, String storeId) throws Exception {
    var event = new FulfillmentEvent(eventId, "SHIPMENT_DELIVERED", UUID.randomUUID(), order.getId(),
        order.getCustomerId(), storeId, "DELIVERED", UUID.randomUUID(), "DELIVERED", Instant.now());
    byte[] body = mapper.writeValueAsBytes(new SyncEnvelope(eventId, signingSource, "fulfillment", order.getId().toString(), mapper.valueToTree(event)));
    var headers = new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    headers.set("X-Sync-Source", signingSource);headers.set("X-Sync-Time", timestamp);
    headers.set("X-Sync-Signature", authentication.sign(signingSource, timestamp, body));
    return http.postForEntity("/internal/events", new HttpEntity<>(body, headers), String.class);
  }
  @Test void authenticatedDeliveryPersistsAndRetainsCustomerOwnership() throws Exception {
    Order order = orders.saveAndFlush(new Order(UUID.randomUUID(), UUID.randomUUID(), "test-store", UUID.randomUUID().toString()));
    assertThat(deliver(order, UUID.randomUUID(), "fulfillment-service", "test-store").getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(orders.findById(order.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    int count = jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class);
    assertThat(deliver(order, UUID.randomUUID(), "fulfillment-service", "test-store").getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_outbox", Integer.class)).isEqualTo(count);
    var headers = new HttpHeaders();headers.setBearerAuth(order.getCustomerId().toString());
    String path = "/api/v1/orders/" + order.getId();
    assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody()).contains("DELIVERED");
    headers.setBearerAuth(UUID.randomUUID().toString());
    assertThat(http.exchange(path + "/review", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
  @Test void wrongEventSourceOrStoreCannotMarkDelivered() throws Exception {
    Order order = orders.saveAndFlush(new Order(UUID.randomUUID(), UUID.randomUUID(), "test-store", UUID.randomUUID().toString()));
    assertThat(deliver(order, UUID.randomUUID(), "payment-service", "test-store").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(deliver(order, UUID.randomUUID(), "fulfillment-service", "other-store").getStatusCode().is2xxSuccessful()).isFalse();
    assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_inbox", Integer.class)).isZero();
  }

  @Test void legacyReconciliationRequiresOwnerAndVerifiedEvidenceAndKeepsPaymentPending() {
    var customer = UUID.randomUUID();
    var order = orders.saveAndFlush(new Order(customer, UUID.randomUUID(), "test-store", UUID.randomUUID().toString()));
    var evidence = new FulfillmentDeliveryClient.DeliveryEvidence(order.getId(), customer, "test-store", UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    org.mockito.Mockito.when(deliveryClient.verify(order.getId(), "Bearer " + customer)).thenReturn(java.util.Optional.of(evidence));
    String path = "/api/v1/orders/" + order.getId() + "/reconcile-delivery";
    assertThat(http.postForEntity(path, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    var headers = new HttpHeaders();headers.setBearerAuth(UUID.randomUUID().toString());
    assertThat(http.postForEntity(path, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    org.mockito.Mockito.verifyNoInteractions(deliveryClient);
    headers.setBearerAuth(customer.toString());
    var response = http.postForEntity(path, new HttpEntity<>(headers), OrderResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().status()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(response.getBody().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
  }
  @Test void reconciliationDoesNotTrustMismatchedCustomerEvidence() {
    var customer = UUID.randomUUID();
    var order = orders.saveAndFlush(new Order(customer, UUID.randomUUID(), "test-store", UUID.randomUUID().toString()));
    var evidence = new FulfillmentDeliveryClient.DeliveryEvidence(order.getId(), UUID.randomUUID(), "test-store", UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    org.mockito.Mockito.when(deliveryClient.verify(order.getId(), "Bearer " + customer)).thenReturn(java.util.Optional.of(evidence));
    var headers = new HttpHeaders();headers.setBearerAuth(customer.toString());
    assertThat(http.postForEntity("/api/v1/orders/" + order.getId() + "/reconcile-delivery", new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
  }
}
