package com.metaarch.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CatalogLocationClientTest {
  @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

  @Test void forwardsAuthenticatedLocationAdminTokenToStoreOwnershipLookup() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    var client = new CatalogLocationClient(builder, "http://catalog.test");
    var jwt = Jwt.withTokenValue("fixture-token").header("alg", "HS256").subject("location-admin")
        .claim("roles", java.util.List.of("ROLE_LOCATION_ADMIN")).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
        java.util.List.of(new SimpleGrantedAuthority("ROLE_LOCATION_ADMIN"),
            new SimpleGrantedAuthority("SCOPE_LOCATION:hyderabad"))));

    server.expect(requestTo("http://catalog.test/api/v1/stores/hyd-store"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer fixture-token"))
        .andRespond(withSuccess("{\"location\":\"Hyderabad\"}", MediaType.APPLICATION_JSON));

    assertThat(client.locationOf("hyd-store")).contains("Hyderabad");
    server.verify();
  }
}
