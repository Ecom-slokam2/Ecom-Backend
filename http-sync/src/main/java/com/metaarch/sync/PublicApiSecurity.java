package com.metaarch.sync;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class PublicApiSecurity {
  @Bean SecurityFilterChain publicSecurity(HttpSecurity http) throws Exception {
    return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/api/v1/payments/capabilities",
        "/internal/events", "/internal/provider/payment-webhook", "/api/v1/public/inventory-availability").permitAll()
        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/reviews/products", "/api/v1/reviews/products/**").permitAll()
        .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll())
      .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwt -> {
        Set<String> roles = new HashSet<>();
        for (String claim : List.of("roles", "authorities")) {
          Object value = jwt.getClaims().get(claim);
          if (value instanceof Collection<?> values) values.forEach(v -> roles.add(v.toString()));
        }
        return new JwtAuthenticationToken(jwt, roles.stream().map(SimpleGrantedAuthority::new).toList());
      }))).build();
  }
  @Bean JwtDecoder publicJwtDecoder(@Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.issuer}") String issuer) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32 || issuer.isBlank()) throw new IllegalArgumentException("Approved JWT secret and issuer required");
    MacAlgorithm algorithm = bytes.length >= 64 ? MacAlgorithm.HS512 : bytes.length >= 48 ? MacAlgorithm.HS384 : MacAlgorithm.HS256;
    var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes, "HmacSHA" + algorithm.getName().substring(2)))
      .macAlgorithm(algorithm).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }
  public static boolean administrator() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token) || "customer".equals(token.getToken().getClaimAsString("accountType"))) return false;
    return token.getAuthorities().stream().anyMatch(a -> Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_GLOBAL_ADMIN").contains(a.getAuthority()));
  }
  public static boolean owns(UUID customerId) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token) || customerId == null) return false;
    try { return customerId.equals(UUID.fromString(token.getToken().getClaimAsString("customerId"))); }
    catch (RuntimeException ex) { return false; }
  }
  public static boolean managesStore(String storeId) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token) || storeId == null || storeId.isBlank()
        || "customer".equals(token.getToken().getClaimAsString("accountType"))) return false;
    var authorities = token.getAuthorities().stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
    return authorities.contains("ROLE_STORE_MANAGER") && authorities.contains("SCOPE_STORE:" + storeId);
  }
}
