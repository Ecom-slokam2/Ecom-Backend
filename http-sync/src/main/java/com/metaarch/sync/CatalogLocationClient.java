package com.metaarch.sync;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Trusted server-to-server lookup for persisted store/location ownership. */
@Component
public class CatalogLocationClient {
  private final RestClient client;

  public CatalogLocationClient(RestClient.Builder builder,
      @Value("${catalog.service.url:${CATALOG_SERVICE_URL:http://localhost:8089}}") String baseUrl) {
    this.client = builder.baseUrl(baseUrl).build();
  }

  public Optional<String> locationOf(String storeId) {
    if (storeId == null || storeId.isBlank()) return Optional.empty();
    try {
      Map<?, ?> row = client.get().uri("/api/v1/stores/{id}", storeId.trim())
          .headers(this::forwardCurrentBearerToken).retrieve().body(Map.class);
      Object location = row == null ? null : row.get("location");
      return location instanceof String value && !value.isBlank() ? Optional.of(value.trim()) : Optional.empty();
    } catch (RestClientException error) {
      return Optional.empty();
    }
  }

  public List<String> storesInLocation(String location) {
    if (location == null || location.isBlank()) return List.of();
    try {
      List<?> rows = client.get().uri("/api/v1/stores").headers(this::forwardCurrentBearerToken).retrieve().body(List.class);
      if (rows == null) return List.of();
      return rows.stream().filter(Map.class::isInstance).map(Map.class::cast)
          .filter(row -> location.equalsIgnoreCase(String.valueOf(row.get("location"))))
          .map(row -> row.get("id")).filter(String.class::isInstance).map(String.class::cast).toList();
    } catch (RestClientException error) {
      return List.of();
    }
  }

  public boolean hasActiveProductMapping(String storeId, java.util.UUID productId) {
    if (storeId == null || storeId.isBlank() || productId == null) return false;
    List<?> rows = client.get().uri(uri -> uri.path("/api/v1/public/store-products")
        .queryParam("storeId", storeId.trim()).queryParam("productId", productId).build())
        .retrieve().body(List.class);
    if (rows == null) return false;
    return rows.stream().filter(Map.class::isInstance).map(Map.class::cast)
        .anyMatch(row -> Boolean.parseBoolean(String.valueOf(row.get("available"))));
  }

  private void forwardCurrentBearerToken(org.springframework.http.HttpHeaders headers) {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
      headers.setBearerAuth(token.getToken().getTokenValue());
    }
  }
}
