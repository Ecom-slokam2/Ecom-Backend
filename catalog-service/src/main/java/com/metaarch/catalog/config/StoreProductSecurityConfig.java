package com.metaarch.catalog.config;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class StoreProductSecurityConfig {
  @Bean
  SecurityFilterChain storeProductSecurity(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/coupons/validate").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/public/store-products").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/store-products/**").authenticated()
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/location-products/**").hasRole("LOCATION_ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/store-manager-products/**").hasRole("STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/coupons/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/stores/delivery-zones/*/location").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/stores/*/delivery-zones").authenticated()
            .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/store-products/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/store-products/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/store-products/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/**").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/banners/**", "/api/v1/coupons/**", "/api/v1/location-products/**", "/api/v1/store-manager-products/**", "/api/v1/products/*/variants", "/api/v1/products/*/images", "/api/v1/variants/**", "/api/v1/images/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/location-products/**", "/api/v1/store-manager-products/**", "/api/v1/variants/**", "/api/v1/images/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/location-products/**").hasRole("LOCATION_ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/store-manager-products/**").hasRole("STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/location-products/**").hasRole("LOCATION_ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/store-manager-products/**").hasRole("STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/variants/**", "/api/v1/images/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/stores/**", "/api/v1/banners/**", "/api/v1/coupons/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/stores/**", "/api/v1/banners/**", "/api/v1/coupons/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/banners/**", "/api/v1/coupons/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/stores/**", "/api/v1/banners/**", "/api/v1/coupons/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN", "LOCATION_ADMIN", "STORE_MANAGER")
            .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN")
            .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/**").hasAnyRole("ADMIN", "GLOBAL_ADMIN", "SUPER_ADMIN")
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(authorities())))
        .build();
  }

  @Bean
  JwtDecoder storeProductJwtDecoder(@Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.issuer:org-access}") String issuer) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    MacAlgorithm algorithm = bytes.length >= 64 ? MacAlgorithm.HS512 : bytes.length >= 48 ? MacAlgorithm.HS384 : MacAlgorithm.HS256;
    var key = new SecretKeySpec(bytes, "HmacSHA" + algorithm.getName().substring(2));
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(algorithm).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> authorities() {
    return jwt -> {
      var names = new HashSet<String>();
      for (String claim : java.util.List.of("roles", "authorities")) {
        Object value = jwt.getClaims().get(claim);
        if (value instanceof Collection<?> values) values.forEach(item -> names.add(item.toString()));
      }
      return new JwtAuthenticationToken(jwt, names.stream().map(SimpleGrantedAuthority::new).toList(), jwt.getSubject());
    };
  }
}
