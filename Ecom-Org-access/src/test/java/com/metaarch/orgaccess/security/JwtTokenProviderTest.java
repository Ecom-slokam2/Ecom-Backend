package com.metaarch.orgaccess.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.metaarch.orgaccess.identity.IdentityService;
import java.time.Instant;
import java.util.List;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JwtTokenProviderTest {
  @Test
  void createsAndReadsAnApplicationToken() {
    JwtProperties properties = properties();
    JwtTokenProvider provider = new JwtTokenProvider(properties, mock(IdentityService.class), mock(JdbcTemplate.class));

    String token = provider.generateToken("alice", List.of("ROLE_CUSTOMER", "PERM_ORDER_READ"));

    assertThat(provider.getUsername(token)).isEqualTo("alice");
    assertThat(provider.getRoles(token)).containsExactly("ROLE_CUSTOMER", "PERM_ORDER_READ");
    assertThat(provider.isCustomerToken(token)).isFalse();
    assertThat(provider.getExpiration(token)).isAfter(Instant.now());
  }

  @Test
  void createsCustomerTokenFromAnEnabledCustomerRecord() {
    JwtProperties properties = properties();
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForMap("SELECT BIN_TO_UUID(id) AS customerId,token_version,enabled,locked FROM app_customers WHERE username=?", "alice"))
        .thenReturn(java.util.Map.of("customerId", "customer-1", "token_version", 2L, "enabled", true, "locked", false));
    JwtTokenProvider provider = new JwtTokenProvider(properties, mock(IdentityService.class), db);

    String token = provider.generateCustomerToken("alice");

    assertThat(provider.getUsername(token)).isEqualTo("alice");
    assertThat(provider.isCustomerToken(token)).isTrue();
    assertThat(provider.getRoles(token)).containsExactly("ROLE_CUSTOMER");
  }

  @Test
  void createsShortLivedOrderBoundDeliveryActionProxyToken() {
    JwtProperties properties=properties();
    JwtTokenProvider provider=new JwtTokenProvider(properties,mock(IdentityService.class),mock(JdbcTemplate.class));
    String token=provider.generateDeliveryActionToken("42","order-1");
    var claims=Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
        .build().parseClaimsJws(token).getBody();
    assertThat(claims.get("deliveryActionProxy",Boolean.class)).isTrue();
    assertThat(claims.get("deliveryPartnerId",String.class)).isEqualTo("42");
    assertThat(claims.get("deliveryActionOrderId",String.class)).isEqualTo("order-1");
    assertThat(claims.getExpiration().toInstant()).isBefore(claims.getIssuedAt().toInstant().plusSeconds(31));
  }

  private static JwtProperties properties() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("0123456789012345678901234567890123456789");
    properties.setExpirationMillis(60_000);
    return properties;
  }
}
