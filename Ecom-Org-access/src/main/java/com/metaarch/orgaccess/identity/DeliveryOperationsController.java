package com.metaarch.orgaccess.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import com.metaarch.orgaccess.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryOperationsController {
  private static final SecureRandom OTP_RANDOM = new SecureRandom();
  private static final String ASSIGNMENT_ACCEPTED = "Assignment accepted by delivery partner.";
  private static final String DELIVERY_ACCEPTED_AT = "deliveryAcceptedAt";
  private static final String TRACKING_EVENTS_PATH = "/tracking-events";
  private static final String DELIVERY_PARTNER_ID = "deliveryPartnerId";
  private static final String STATUS = "status";
  private static final String DESCRIPTION = "description";
  private static final String EVENT_TIME = "eventTime";
  private static final String FAILED = "FAILED";
  private static final String OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
  private static final String STORE_ID = "storeId";
  private static final String CUSTOMER_ID = "customerId";

  private final StaffProfileController profiles;
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final JwtTokenProvider tokens;
  private final RestClient orders,shipments,notifications;
  @Value("${services.orders.orders-path:/api/v1/orders}")
  private String ordersPath;
  @Value("${services.fulfillment.shipments-order-path:/api/v1/shipments/order/}")
  private String shipmentsOrderPath;
  @Value("${services.fulfillment.shipments-path:/api/v1/shipments/}")
  private String shipmentsPath;
  @Value("${services.orders.order-path:/api/v1/orders/}")
  private String orderPath;
  @Value("${services.notifications.notification-path:/api/v1/notifications}")
  private String notificationPath;
  public DeliveryOperationsController(StaffProfileController profiles, JdbcTemplate jdbc, PasswordEncoder passwords,
      JwtTokenProvider tokens,
      @Value("${services.orders-url:http://localhost:3311}") String orderUrl,
      @Value("${services.fulfillment-url:http://localhost:3315}") String fulfillmentUrl,
      @Value("${services.notifications-url:http://localhost:3317}") String notificationUrl) {
    this.profiles=profiles;this.jdbc=jdbc;this.passwords=passwords;this.tokens=tokens;
    orders=client(orderUrl);shipments=client(fulfillmentUrl);notifications=client(notificationUrl);
  }
  private static RestClient client(String baseUrl) {
    JdkClientHttpRequestFactory factory=new JdkClientHttpRequestFactory(
      java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build());
    factory.setReadTimeout(8000);
    return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).requestInterceptor((request,body,execution) -> {
      var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
      if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
        String bearer=servlet.getRequest().getHeader("Authorization");
        // Preserve a purpose-bound token explicitly set by delivery actions;
        // only forward the browser token for ordinary internal reads.
        if (request.getHeaders().getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION)==null
            && bearer!=null && bearer.startsWith("Bearer ")) request.getHeaders().set("Authorization",bearer);
      }
      return execution.execute(request,body);
    }).build();
  }
  @SuppressWarnings("unchecked")
  private Map<String,Object> get(RestClient client,String path) { return client.get().uri(path).retrieve().body(Map.class); }
  private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
  private Map<String,Object> assigned(UUID orderId,Authentication auth) {
    Map<String,Object> shipment=Objects.requireNonNull(get(shipments,shipmentsOrderPath+orderId));
    if(!String.valueOf(profiles.me(auth).get("id")).equals(String.valueOf(shipment.get(DELIVERY_PARTNER_ID)))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This delivery is not assigned to you.");
    }
    return shipment;
  }
  private String displayStatus(String state) {
    return switch(state) { case "IN_TRANSIT" -> "Picked Up"; case OUT_FOR_DELIVERY -> "Out for Delivery"; default -> state; };
  }
  private Instant instant(Object value) {
    if (value == null) return null;
    try { return Instant.parse(value.toString()); } catch (RuntimeException ignored) { return null; }
  }
  private List<Map<String,Object>> trackingEvents(Object shipmentId) {
    try {
      List<Map<String,Object>> tracking=shipments.get().uri(shipmentsPath+shipmentId+TRACKING_EVENTS_PATH).retrieve().body(List.class);
      return tracking == null ? List.of() : tracking;
    } catch(Exception ex) { return List.of(); }
  }
  private Map<String,Object> acceptedEvent(List<Map<String,Object>> tracking, Map<String,Object> shipment) {
    Instant assignedAt=instant(shipment.get("deliveryAssignedAt"));
    final Instant acceptedAfter=assignedAt == null?Instant.EPOCH:assignedAt;
    return tracking.stream()
      .filter(t -> ASSIGNMENT_ACCEPTED.equals(t.get(DESCRIPTION)))
      .filter(t -> { Instant eventAt=instant(t.get(EVENT_TIME)); return eventAt!=null && !eventAt.isBefore(acceptedAfter); })
      .findFirst().orElse(null);
  }
  private void addDeliveryAcceptedAt(Map<String,Object> row, List<Map<String,Object>> tracking, Map<String,Object> shipment) {
    Map<String,Object> accepted=acceptedEvent(tracking,shipment);
    row.put(DELIVERY_ACCEPTED_AT,accepted==null?null:accepted.get(EVENT_TIME));
  }
  private String deliveryActionToken(Authentication auth, UUID orderId) {
    return tokens.generateDeliveryActionToken(String.valueOf(profiles.me(auth).get("id")),orderId.toString());
  }
  private void changeShipmentStatus(Map<String,Object> shipment, UUID orderId, String status, Authentication auth) {
    shipments.patch().uri(shipmentsPath+shipment.get("id")+"/status")
      .headers(headers -> headers.setBearerAuth(deliveryActionToken(auth,orderId)))
      .body(Map.of(STATUS,status)).retrieve().toBodilessEntity();
  }
  private void recordTracking(Map<String,Object> shipment, UUID orderId, Authentication auth,
      String status, String location, String description) {
    shipments.post().uri(shipmentsPath+shipment.get("id")+TRACKING_EVENTS_PATH)
      .headers(headers -> headers.setBearerAuth(deliveryActionToken(auth,orderId)))
      .body(Map.of(STATUS,status,"location",location,DESCRIPTION,description,EVENT_TIME,Instant.now().toString()))
      .retrieve().toBodilessEntity();
  }
  @GetMapping("/me/orders")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  @SuppressWarnings("unchecked")
  public List<Map<String,Object>> mine(Authentication auth) {
    Map<String,Object> profile=profiles.me(auth);
    if(profile.get(STORE_ID)==null) return List.of();
    List<Map<String,Object>> assignedShipments=shipments.get().uri("/api/v1/shipments/assigned").retrieve().body(List.class);
    if(assignedShipments == null) return List.of();
    List<Map<String,Object>> result=new ArrayList<>();
    for(Map<String,Object> shipment:assignedShipments) {
      if(!String.valueOf(profile.get("id")).equals(String.valueOf(shipment.get(DELIVERY_PARTNER_ID)))) continue;
      Map<String,Object> order=Objects.requireNonNull(get(orders,orderPath+shipment.get("orderId")));
      Map<String,Object> row=new LinkedHashMap<>(order);
      row.put("shipment",shipment);row.put("sourceStore",profile.get("store"));
      row.put("deliveredAt",shipment.get("deliveredAt"));row.put("total",order.get("totalAmount"));
      String state=String.valueOf(shipment.get(STATUS));
      row.put(STATUS,displayStatus(state));
      List<Map<String,Object>> tracking=trackingEvents(shipment.get("id"));
      row.put("trackingEvents",tracking);
      addDeliveryAcceptedAt(row,tracking,shipment);
      try {
        Map<String,Object> customer=jdbc.queryForMap(
          "SELECT c.full_name AS name, u.mobile AS phone FROM customers c JOIN users u ON u.id=c.user_id WHERE c.id=UNHEX(REPLACE(?,'-',''))",
          String.valueOf(order.get(CUSTOMER_ID)));
        row.put("customer",customer);
      } catch(Exception ex) { /* customer not resolvable — the UI falls back to "not provided". */ }
      result.add(row);
    }
    return result;
  }
  @GetMapping("/orders/{orderId}/live-location")
  @PreAuthorize("hasRole('CUSTOMER')")
  public Map<String,Object> customerLiveLocation(@PathVariable UUID orderId) {
    // The Order service enforces ownership from the customer's signed token forwarded by RestClient.
    get(orders,orderPath+orderId);
    Map<String,Object> shipment;
    try { shipment=get(shipments,shipmentsOrderPath+orderId); }
    catch(org.springframework.web.client.RestClientResponseException missing) {
      if(missing.getStatusCode().value()==404) return Map.of("available",false);
      throw missing;
    }
    String state=String.valueOf(shipment.get(STATUS));
    if(!Set.of("IN_TRANSIT",OUT_FOR_DELIVERY).contains(state)) return Map.of("available",false);
    Object partnerId=shipment.get(DELIVERY_PARTNER_ID);
    if(partnerId==null) return Map.of("available",false);
    List<Map<String,Object>> locations=jdbc.queryForList("""
      SELECT u.delivery_latitude AS latitude,u.delivery_longitude AS longitude,
        ROUND(UNIX_TIMESTAMP(u.delivery_location_updated_at) * 1000) AS updatedAt
      FROM users u
      WHERE CAST(u.id AS CHAR)=? AND u.delivery_latitude IS NOT NULL AND u.delivery_longitude IS NOT NULL
      LIMIT 1
      """,String.valueOf(partnerId));
    if(locations.isEmpty()) return Map.of("available",false);
    Map<String,Object> location=new LinkedHashMap<>(locations.getFirst());
    location.put("available",true);
    return location;
  }
  @PutMapping("/orders/{orderId}/partner/{partnerId}")
  @PreAuthorize("hasAnyRole('STORE_MANAGER','LOCATION_ADMIN','GLOBAL_ADMIN','SUPER_ADMIN')")
  public Map<String,Object> assign(@PathVariable UUID orderId,@PathVariable String partnerId,Authentication auth) {
    Map<String,Object> order=Objects.requireNonNull(get(orders,orderPath+orderId));
    // Resolve the partner with a direct query. Calling the DeliveryPartnerController bean here
    // re-triggers its class-level @PreAuthorize on a nested call and can surface as a 403.
    List<Map<String,Object>> rows=jdbc.queryForList("""
      SELECT CAST(u.id AS CHAR) AS id, u.display_name AS name, u.mobile,
        u.vehicle_number AS vehicleNumber, m.store_id AS storeId, u.status AS status,
        (u.enabled AND NOT u.locked AND u.status = 'Active') AS active
      FROM users u
      JOIN memberships m ON m.user_id = u.id
      JOIN roles r ON r.id = m.role_id
      WHERE r.name = 'DELIVERY_PARTNER' AND CAST(u.id AS CHAR) = ?
      """, partnerId);
    if(rows.isEmpty()) throw invalid("Delivery partner not found.");
    if(rows.stream().noneMatch(p -> !"0".equals(String.valueOf(p.get("active"))) && !Boolean.FALSE.equals(p.get("active")))) {
      throw invalid("This delivery partner is not active.");
    }
    Map<String,Object> partner=rows.stream()
      .filter(p -> String.valueOf(order.get(STORE_ID)).equals(String.valueOf(p.get(STORE_ID))))
      .findFirst().orElse(rows.get(0));
    Map<String,Object> shipment=Objects.requireNonNull(get(shipments,shipmentsOrderPath+orderId));
    boolean retry=FAILED.equals(String.valueOf(shipment.get(STATUS)));
    Map<String,Object> body=new HashMap<>();body.put(DELIVERY_PARTNER_ID,partnerId);body.put("deliveryPartnerName",partner.get("name"));body.put("deliveryPartnerPhone",partner.get("mobile"));body.put("vehicleNumber",partner.get("vehicleNumber"));
    shipments.patch().uri(shipmentsPath+shipment.get("id")+"/delivery-partner").body(body).retrieve().toBodilessEntity();
    if(retry) {
      shipments.post().uri(shipmentsPath+shipment.get("id")+TRACKING_EVENTS_PATH).body(Map.of(STATUS,"CREATED","location","Store hub",DESCRIPTION,"Delivery retry: reassigned to "+partner.get("name")+".",EVENT_TIME,Instant.now().toString())).retrieve().toBodilessEntity();
    }
    return get(shipments,shipmentsOrderPath+orderId);
  }
  public record ActionRequest(@NotBlank String action,@Size(max=1000) String reason,@Pattern(regexp="\\d{6}") String otp) {}
  @PostMapping("/me/orders/{orderId}/actions")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  @Transactional(noRollbackFor=ResponseStatusException.class)
  public void action(@PathVariable UUID orderId,@Valid @RequestBody ActionRequest request,Authentication auth) {
    Map<String,Object> shipment=assigned(orderId,auth);
    String id=String.valueOf(shipment.get("id"));
    if ("START".equals(request.action())) {
      shipments.post().uri(shipmentsPath+id+"/start-delivery")
        .headers(headers -> headers.setBearerAuth(deliveryActionToken(auth,orderId))).retrieve().toBodilessEntity();
      return;
    }
    if ("ACCEPT".equals(request.action())) {
      shipments.post().uri(shipmentsPath+id+"/accept-assignment")
        .headers(headers -> headers.setBearerAuth(deliveryActionToken(auth,orderId))).retrieve().toBodilessEntity();
      return;
    }
    String state=String.valueOf(shipment.get(STATUS));
    if(Set.of("DELIVERED","CANCELLED","RETURNED",FAILED).contains(state)) throw invalid("This shipment is already finalized.");
    String next=state;
    String description;
    switch(request.action()) {
      case "ACCEPT" -> description=ASSIGNMENT_ACCEPTED;
      case "PICKUP" -> {
        if (acceptedEvent(trackingEvents(shipment.get("id")),shipment)==null) throw invalid("Accept the assignment before picking up the order.");
        if (!"CREATED".equals(state)) throw invalid("This shipment cannot be picked up in its current state.");
        next="IN_TRANSIT";description="Order picked up by delivery partner.";
      }
      case "START" -> {next=OUT_FOR_DELIVERY;description="Order is out for delivery.";}
      case "CANCEL" -> {if(request.reason()==null||request.reason().isBlank()){throw invalid("A cancellation reason is required.");}next=state.equals(OUT_FOR_DELIVERY)?FAILED:"CANCELLED";description="Delivery cancelled by partner: "+request.reason();}
      case "VERIFY" -> {
        if(!OUT_FOR_DELIVERY.equals(state)){throw invalid("Start delivery before verifying the OTP.");}
        var checks=jdbc.queryForList("SELECT * FROM delivery_verifications WHERE order_id=? AND partner_id=? AND consumed=0 AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",orderId.toString(),Long.valueOf(profiles.me(auth).get("id").toString()));
        if(checks.isEmpty()||((Number)checks.getFirst().get("attempts")).intValue()>=5){throw invalid("Request a new OTP; this code is expired or locked.");}
        jdbc.update("UPDATE delivery_verifications SET attempts=attempts+1 WHERE order_id=?",orderId.toString());
        if(request.otp()==null||!passwords.matches(request.otp(),String.valueOf(checks.getFirst().get("code_hash")))){throw invalid("Incorrect delivery OTP.");}
        next="DELIVERED";description="Delivery completed with customer OTP.";
      }
      default -> throw invalid("Unknown delivery action.");
    }
    if(!next.equals(state)) changeShipmentStatus(shipment,orderId,next,auth);
    recordTracking(shipment,orderId,auth,next,String.valueOf(profiles.me(auth).get("store")),description);
    if(request.action().equals("VERIFY"))jdbc.update("UPDATE delivery_verifications SET consumed=1 WHERE order_id=?",orderId.toString());
  }
  @PostMapping("/me/orders/{orderId}/otp")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  @Transactional
  public void otp(@PathVariable UUID orderId,Authentication auth) {
    if(!OUT_FOR_DELIVERY.equals(assigned(orderId,auth).get(STATUS)))throw invalid("Start delivery before requesting the OTP.");
    if(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_verifications WHERE order_id=? AND issued_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND)",Long.class,orderId.toString())>0)throw invalid("Wait one minute before requesting another OTP.");
    Map<String,Object> order=Objects.requireNonNull(get(orders,orderPath+orderId));
    String code=String.format("%06d",OTP_RANDOM.nextInt(1000000));
    jdbc.update("INSERT INTO delivery_verifications(order_id,partner_id,code_hash,expires_at) VALUES(?,?,?,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 15 MINUTE)) ON DUPLICATE KEY UPDATE partner_id=VALUES(partner_id),code_hash=VALUES(code_hash),expires_at=VALUES(expires_at),issued_at=CURRENT_TIMESTAMP,attempts=0,consumed=0",orderId.toString(),Long.valueOf(profiles.me(auth).get("id").toString()),passwords.encode(code));
    notifications.post().uri(notificationPath).body(Map.of(CUSTOMER_ID,order.get(CUSTOMER_ID),"orderId",orderId.toString(),"notificationType","ORDER_STATUS_UPDATED","channel","PUSH","subject","Your delivery OTP","message","Your delivery OTP is "+code+". It expires in 15 minutes. Share it only when your order arrives.")).retrieve().toBodilessEntity();
  }
}
