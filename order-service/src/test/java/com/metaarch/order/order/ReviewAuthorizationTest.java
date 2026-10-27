package com.metaarch.order.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ReviewAuthorizationTest {
  private final OrderAccess access = new OrderAccess(org.mockito.Mockito.mock(OrderRepository.class), org.mockito.Mockito.mock(com.metaarch.sync.CatalogLocationClient.class));

  @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

  @Test void onlyGlobalAndSuperAdminsCanModerateReviews() {
    authenticate("staff", "ROLE_SUPER_ADMIN");
    assertThat(access.reviewModerator()).isTrue();
    assertThat(access.superAdmin()).isTrue();
    authenticate("staff", "ROLE_GLOBAL_ADMIN");
    assertThat(access.reviewModerator()).isTrue();
    assertThat(access.superAdmin()).isFalse();
    authenticate("staff", "ROLE_LOCATION_ADMIN");
    assertThat(access.reviewModerator()).isFalse();
    authenticate("customer", "ROLE_SUPER_ADMIN");
    assertThat(access.reviewModerator()).isFalse();
  }

  private void authenticate(String accountType, String role) {
    Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("test-subject")
      .issuer("http://test").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
      .claim("accountType", accountType).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
  }
}
