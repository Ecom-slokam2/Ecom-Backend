package com.metaarch.orgaccess.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users/me")
public class StaffProfileController {
  private final JdbcTemplate jdbc;
  public StaffProfileController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  @GetMapping
  public Map<String,Object> me(Authentication auth) {
    return jdbc.queryForList("""
      SELECT CAST(u.id AS CHAR) AS id,u.username,CASE WHEN r.name='CUSTOMER' THEN c.full_name ELSE COALESCE(NULLIF(u.display_name,''),c.full_name) END AS name,u.email,u.mobile,u.mobile AS phone,
      u.status,(u.status='Active' AND u.enabled AND NOT u.locked) AS active,u.vehicle_type AS vehicleType,
      u.vehicle_number AS vehicleNumber,u.licence_number AS licenceNumber,u.licence_number AS licenseNumber,
      u.address,u.emergency_contact AS emergencyContact,
      u.delivery_latitude AS deliveryLatitude,u.delivery_longitude AS deliveryLongitude,
      ROUND(UNIX_TIMESTAMP(u.delivery_location_updated_at) * 1000) AS deliveryLocationUpdatedAt,
      m.store_id AS storeId,m.store_name AS store,m.store_location AS storeLocation,m.store_address AS storeAddress,
      m.location_id AS location,LOWER(REPLACE(r.name,'_','-')) AS role
      FROM users u LEFT JOIN customers c ON c.user_id=u.id LEFT JOIN memberships m ON m.user_id=u.id
      LEFT JOIN user_roles ur ON ur.user_id=u.id LEFT JOIN roles r ON r.id=COALESCE(m.role_id,ur.role_id)
      WHERE u.username=? ORDER BY CASE r.name WHEN 'SUPER_ADMIN' THEN 1 WHEN 'GLOBAL_ADMIN' THEN 2
      WHEN 'LOCATION_ADMIN' THEN 3 WHEN 'STORE_MANAGER' THEN 4 WHEN 'DELIVERY_PARTNER' THEN 5 ELSE 6 END LIMIT 1
      """,auth.getName()).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found."));
  }
  public record ProfileRequest(@NotBlank @Size(max=180) String name,@NotBlank @Pattern(regexp="[+0-9 ()-]{7,30}") String phone,
      @NotBlank @Email @Size(max=255) String email,@NotBlank @Size(max=40) String vehicleNumber,
      @NotBlank @Size(max=40) String vehicleType,@Size(max=60) String licenseNumber,
      @Size(max=700) String address,@Size(max=30) String emergencyContact) {}
  public record SuperAdminNameRequest(@NotBlank @Size(max=180) String name) {}
  @PutMapping("/profile/name")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','GLOBAL_ADMIN')")
  @Transactional
  public Map<String,Object> updateSuperAdminName(@Valid @RequestBody SuperAdminNameRequest request,Authentication auth) {
    int changed=jdbc.update("UPDATE users SET display_name=? WHERE username=?",request.name().trim(),auth.getName());
    if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found.");
    return me(auth);
  }
  @PutMapping
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  @Transactional
  public Map<String,Object> update(@Valid @RequestBody ProfileRequest request,Authentication auth) {
    if(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=? AND username<>?",Long.class,request.email().trim(),auth.getName())>0)
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already exists.");
    jdbc.update("UPDATE users SET display_name=?,mobile=?,email=?,vehicle_number=?,vehicle_type=?,licence_number=?,address=?,emergency_contact=? WHERE username=?",
      request.name().trim(),request.phone().trim(),request.email().trim(),request.vehicleNumber().trim(),request.vehicleType().trim(),request.licenseNumber(),request.address(),request.emergencyContact(),auth.getName());
    return me(auth);
  }
  public record Availability(@NotNull Boolean active) {}
  @PutMapping("/availability")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  @Transactional
  public Map<String,Object> availability(@Valid @RequestBody Availability request,Authentication auth) {
    jdbc.update("UPDATE users SET status=? WHERE username=?",request.active()?"Active":"Inactive",auth.getName());
    return me(auth);
  }
  public record Position(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,@NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
  @PutMapping("/delivery-location")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  public void position(@Valid @RequestBody Position request,Authentication auth) {
    jdbc.update("UPDATE users SET delivery_latitude=?,delivery_longitude=?,delivery_location_updated_at=CURRENT_TIMESTAMP WHERE username=?",request.latitude(),request.longitude(),auth.getName());
  }
}
