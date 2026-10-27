package com.metaarch.fulfillment.shipment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Verifies partner identity and store eligibility against the authoritative Org Access directory. */
@Component("deliveryPartnerEligibility")
public class DeliveryPartnerEligibility {
  private final ShipmentRepository shipments;
  private final RestClient orgAccess;

  public DeliveryPartnerEligibility(ShipmentRepository shipments, RestClient.Builder builder,
      @Value("${ORG_ACCESS_URL:http://localhost:3310}") String orgAccessUrl) {
    this.shipments = shipments;
    this.orgAccess = builder.clone().baseUrl(orgAccessUrl).build();
  }

  @Transactional(readOnly = true)
  public AssignDeliveryPartnerRequest verifiedRequest(Authentication authentication, UUID shipmentId, String partnerId) {
    if (!(authentication instanceof JwtAuthenticationToken token))
      throw new AccessDeniedException("Unable to verify delivery partner eligibility.");
    Shipment shipment = shipments.findById(shipmentId)
        .orElseThrow(() -> new com.metaarch.fulfillment.exception.ResourceNotFoundException("Shipment not found."));
    String storeId = shipment.getFulfillment().getStoreId();

    List<Map<String, Object>> partners;
    try {
      partners = orgAccess.get().uri("/api/delivery-partners")
          .header("Authorization", "Bearer " + token.getToken().getTokenValue())
          .retrieve().body(new ParameterizedTypeReference<>() {});
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Unable to verify delivery partner eligibility.");
    }

    if (partners != null) {
      for (Map<String, Object> partner : partners) {
        if (!partnerId.equals(String.valueOf(partner.get("id")))
            || !storeId.equals(String.valueOf(partner.get("storeId")))
            || !"Active".equalsIgnoreCase(String.valueOf(partner.get("status")))
            || !active(partner.get("active"))) continue;
        String name = String.valueOf(partner.get("name"));
        String phone = nullable(partner.get("mobile"));
        String vehicle = nullable(partner.get("vehicleNumber"));
        return new AssignDeliveryPartnerRequest(partnerId, name, phone, vehicle);
      }
    }
    throw new AccessDeniedException("Delivery partner is not active and assigned to this store.");
  }

  private boolean active(Object value) {
    if (Boolean.TRUE.equals(value)) return true;
    if (value instanceof Number number) return number.intValue() == 1;
    String normalized = String.valueOf(value).trim();
    return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
  }

  private String nullable(Object value) {
    String text = value == null ? "" : String.valueOf(value).trim();
    return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
  }
}
