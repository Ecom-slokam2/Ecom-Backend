package com.metaarch.orgaccess.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.security.core.GrantedAuthority;

import com.metaarch.orgaccess.identity.IdentityService;
import com.metaarch.orgaccess.identity.UserAccount;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {
  private static final String ACCOUNT_TYPE = "accountType";
  private static final String CUSTOMER_ACCOUNT = "customer";
  private static final String CUSTOMER_ID = "customerId";
  private static final String ROLES = "roles";
  private static final String TOKEN_VERSION = "tokenVersion";

  private final Key signingKey;
  private final JwtProperties properties;
  private final IdentityService identities;
  private final org.springframework.jdbc.core.JdbcTemplate db;

  public JwtTokenProvider(JwtProperties properties, IdentityService identities, org.springframework.jdbc.core.JdbcTemplate db) {
    this.db=db;
    this.properties = properties;
    this.identities = identities;
    byte[] secretBytes = Objects.requireNonNull(properties.getSecret(), "JWT secret is required")
      .getBytes(StandardCharsets.UTF_8);
    this.signingKey = Keys.hmacShaKeyFor(secretBytes);
  }

  public String generateToken(String username, List<String> roles) {
    Instant now = Instant.now();
    return Jwts.builder().setId(java.util.UUID.randomUUID().toString())
      .setSubject(username)
      .setIssuer(properties.getIssuer())
      .claim(ROLES, roles)
      .setIssuedAt(Date.from(now))
      .setExpiration(Date.from(now.plusMillis(properties.getExpirationMillis())))
      .signWith(signingKey)
      .compact();
  }

  /** Short-lived, order-bound credential used only by Delivery Operations when forwarding
   * a server-validated partner action to Fulfillment. It is never returned to a browser. */
  public String generateDeliveryActionToken(String partnerId, String orderId) {
    Instant now = Instant.now();
    return Jwts.builder().setId(java.util.UUID.randomUUID().toString())
      .setSubject("delivery-operations")
      .setIssuer(properties.getIssuer())
      .claim(ROLES, List.of("ROLE_DELIVERY_PARTNER"))
      .claim("deliveryPartnerId", partnerId)
      .claim("deliveryActionOrderId", orderId)
      .claim("deliveryActionProxy", true)
      .setIssuedAt(Date.from(now))
      .setExpiration(Date.from(now.plusSeconds(30)))
      .signWith(signingKey)
      .compact();
  }

    public String generateToken(UserAccount user) {
    List<String> authorities = identities.authorities(user).stream()
      .map(GrantedAuthority::getAuthority).toList();
    Instant now = Instant.now();
    return Jwts.builder().setId(java.util.UUID.randomUUID().toString()).setSubject(user.getUsername()).setIssuer(properties.getIssuer())
      .claim(TOKEN_VERSION, user.getTokenVersion())
      .claim(CUSTOMER_ID, db.query("SELECT BIN_TO_UUID(id) FROM customers WHERE user_id=?", (rs,n)->rs.getString(1),user.getId()).stream().findFirst().orElse(null))
      .claim("authorities", authorities).claim(ROLES, authorities.stream()
        .filter(value -> value.startsWith("ROLE_")).toList())
      .claim("deliveryPartnerId", authorities.contains("ROLE_DELIVERY_PARTNER") ? String.valueOf(user.getId()) : null)
      .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusMillis(properties.getExpirationMillis())))
      .signWith(signingKey).compact();
    }

  public boolean isCustomerToken(String token) { return CUSTOMER_ACCOUNT.equals(parseClaims(token).get(ACCOUNT_TYPE)); }
  public String generateCustomerToken(String username) {
    var customer=db.queryForMap("SELECT BIN_TO_UUID(id) AS customerId,token_version,enabled,locked FROM app_customers WHERE username=?",username);
    if (!Boolean.TRUE.equals(customer.get("enabled")) || Boolean.TRUE.equals(customer.get("locked"))) throw new org.springframework.security.authentication.DisabledException("Account unavailable");
    Instant now=Instant.now();
    return Jwts.builder().setId(java.util.UUID.randomUUID().toString()).setSubject(username).setIssuer(properties.getIssuer())
      .claim(ACCOUNT_TYPE,CUSTOMER_ACCOUNT).claim(CUSTOMER_ID,customer.get(CUSTOMER_ID)).claim(TOKEN_VERSION,customer.get("token_version"))
      .claim(ROLES,List.of("ROLE_CUSTOMER")).setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusMillis(properties.getExpirationMillis()))).signWith(signingKey).compact();
  }
  public boolean validateToken(String token) {
    Claims claims=parseClaims(token);
    if (CUSTOMER_ACCOUNT.equals(claims.get(ACCOUNT_TYPE))) {
      var rows=db.queryForList("SELECT token_version FROM app_customers WHERE username=? AND enabled=TRUE AND locked=FALSE",claims.getSubject());
      return rows.size()==1 && ((Number)rows.get(0).get("token_version")).longValue()==((Number)claims.get(TOKEN_VERSION)).longValue()
        && db.queryForObject("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash=?",Integer.class,com.metaarch.orgaccess.customer.CustomerAccountController.hash(token))==0;
    }
    UserAccount user=identities.requireUser(claims.getSubject());
    Object version=claims.get(TOKEN_VERSION);
    if ((version instanceof Number number ? number.longValue() : 0)!=user.getTokenVersion()) return false;
    return db.queryForObject("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash=?",Integer.class,com.metaarch.orgaccess.customer.CustomerAccountController.hash(token))==0;
  }

  public java.time.Instant getExpiration(String token) { return parseClaims(token).getExpiration().toInstant(); }

  public String getUsername(String token) {
    return parseClaims(token).getSubject();
  }

  public List<String> getRoles(String token) {
    Object roles = parseClaims(token).get("authorities");
    if (!(roles instanceof List<?>)) roles = parseClaims(token).get(ROLES);
    if (roles instanceof List<?> list) {
      return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
    }
    return Collections.emptyList();
  }

  private Claims parseClaims(String token) {
    return Jwts.parserBuilder()
      .setSigningKey(signingKey)
      .build()
      .parseClaimsJws(token)
      .getBody();
  }
}
