package com.metaarch.fulfillment.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.metaarch.fulfillment.fulfillment.Fulfillment;
import com.metaarch.fulfillment.fulfillment.FulfillmentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeliveryPartnerEligibilityTest {
  @Test
  void trustsOnlyActivePartnerAssignedToShipmentStoreAndUsesDirectoryDetails() {
    var repository = mock(ShipmentRepository.class);
    var fulfillment = new Fulfillment(UUID.randomUUID(), UUID.randomUUID(), "store-hyd", FulfillmentType.SHIP_TO_HOME, UUID.randomUUID());
    var shipment = new Shipment(fulfillment, "QA carrier", "QA-TRACK-1", LocalDate.now().plusDays(1));
    UUID shipmentId = UUID.randomUUID();
    org.springframework.test.util.ReflectionTestUtils.setField(shipment, "id", shipmentId);
    when(repository.findById(shipmentId)).thenReturn(Optional.of(shipment));

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(ExpectedCount.times(4), requestTo("http://org-access-test/api/delivery-partners"))
        .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer location-admin-test"))
        .andRespond(withSuccess("""
          [
            {"id":"partner-hyd","name":"Authoritative Hyderabad Partner","mobile":"5550100","vehicleNumber":"QA-VEH-1","storeId":"store-hyd","status":"Active","active":1},
            {"id":"partner-chn","name":"Chennai Partner","mobile":"5550200","vehicleNumber":"QA-VEH-2","storeId":"store-chn","status":"Active","active":true},
            {"id":"partner-inactive","name":"Inactive Partner","mobile":"5550300","vehicleNumber":"QA-VEH-3","storeId":"store-hyd","status":"Inactive","active":false}
          ]
          """, MediaType.APPLICATION_JSON));
    var eligibility = new DeliveryPartnerEligibility(repository, builder, "http://org-access-test");
    var token = Jwt.withTokenValue("location-admin-test").header("alg", "HS256").subject("locationadmin").build();
    var authentication = new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_LOCATION_ADMIN"),
        new SimpleGrantedAuthority("SCOPE_LOCATION:Hyderabad")));

    AssignDeliveryPartnerRequest verified = eligibility.verifiedRequest(authentication, shipmentId, "partner-hyd");
    assertThat(verified.deliveryPartnerName()).isEqualTo("Authoritative Hyderabad Partner");
    assertThat(verified.deliveryPartnerPhone()).isEqualTo("5550100");
    assertThat(verified.vehicleNumber()).isEqualTo("QA-VEH-1");
    assertThatThrownBy(() -> eligibility.verifiedRequest(authentication, shipmentId, "partner-chn"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> eligibility.verifiedRequest(authentication, shipmentId, "partner-inactive"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> eligibility.verifiedRequest(authentication, shipmentId, "unknown-partner"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    server.verify();
  }
}
