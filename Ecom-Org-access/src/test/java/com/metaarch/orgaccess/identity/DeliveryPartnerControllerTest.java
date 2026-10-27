package com.metaarch.orgaccess.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class DeliveryPartnerControllerTest {
  @Test void managerCannotListOrUpdateAnotherStoresPartners() {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    UserAccountRepository users=mock(UserAccountRepository.class);
    MembershipRepository memberships=mock(MembershipRepository.class);
    Role role=new Role(); role.setName("STORE_MANAGER");
    Membership scope=new Membership(); scope.setRole(role); scope.setStoreId("store-a");
    when(memberships.findByUserUsernameIgnoreCase("manager")).thenReturn(List.of(scope));
    when(jdbc.queryForList(anyString())).thenReturn(List.of(
      Map.of("id","1","storeId","store-a","location","city"),
      Map.of("id","2","storeId","store-b","location","city")));
    var controller=new DeliveryPartnerController(jdbc,users,memberships,mock(RoleRepository.class),mock(PasswordEncoder.class));
    var auth=new UsernamePasswordAuthenticationToken("manager","",List.of(new SimpleGrantedAuthority("ROLE_STORE_MANAGER")));
    assertEquals(List.of("1"),controller.list(auth).stream().map(row -> row.get("id")).toList());
    ArgumentCaptor<String> query=ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForList(query.capture());
    assertTrue(query.getValue().contains("u.delivery_latitude AS deliveryLatitude"));
    assertTrue(query.getValue().contains("u.delivery_longitude AS deliveryLongitude"));
    assertTrue(query.getValue().contains("ROUND(UNIX_TIMESTAMP(u.delivery_location_updated_at) * 1000) AS deliveryLocationUpdatedAt"));
    var request = new DeliveryPartnerController.StatusRequest(false);
    assertThrows(ResponseStatusException.class,() -> controller.status(2L,request,auth));
    verifyNoInteractions(users);
  }
  @Test void managerWithoutStoreCannotCreatePartner() {
    var memberships=mock(MembershipRepository.class);
    when(memberships.findByUserUsernameIgnoreCase("manager")).thenReturn(List.of());
    var users=mock(UserAccountRepository.class);
    var controller=new DeliveryPartnerController(mock(JdbcTemplate.class),users,memberships,mock(RoleRepository.class),mock(PasswordEncoder.class));
    var auth=new UsernamePasswordAuthenticationToken("manager","",List.of(new SimpleGrantedAuthority("ROLE_STORE_MANAGER")));
    assertThrows(ResponseStatusException.class,() -> controller.create(null,auth));
    verifyNoInteractions(users);
  }

  @Test void locationAdminOnlyReceivesPartnerLocationsWithinAssignedLocation() {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    MembershipRepository memberships=mock(MembershipRepository.class);
    Role role=new Role(); role.setName("LOCATION_ADMIN");
    Membership scope=new Membership(); scope.setRole(role); scope.setLocationId("location-a");
    when(memberships.findByUserUsernameIgnoreCase("location-admin")).thenReturn(List.of(scope));
    when(jdbc.queryForList(anyString())).thenReturn(List.of(
      Map.of("id","1","storeId","store-a","location","location-a","deliveryLatitude",17.4,"deliveryLongitude",78.4),
      Map.of("id","2","storeId","store-b","location","location-b","deliveryLatitude",12.9,"deliveryLongitude",80.2)));
    var controller=new DeliveryPartnerController(jdbc,mock(UserAccountRepository.class),memberships,mock(RoleRepository.class),mock(PasswordEncoder.class));
    var auth=new UsernamePasswordAuthenticationToken("location-admin","",List.of(new SimpleGrantedAuthority("ROLE_LOCATION_ADMIN")));

    var partners=controller.list(auth);
    assertEquals(List.of("1"),partners.stream().map(row -> row.get("id")).toList());
    assertEquals(17.4,partners.getFirst().get("deliveryLatitude"));
    assertEquals(78.4,partners.getFirst().get("deliveryLongitude"));
  }

  @Test void superAdminCanCreatePartnerForSelectedStore() {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    UserAccountRepository users=mock(UserAccountRepository.class);
    MembershipRepository memberships=mock(MembershipRepository.class);
    RoleRepository roles=mock(RoleRepository.class);
    Role partnerRole=new Role(); partnerRole.setName("DELIVERY_PARTNER");
    when(memberships.findByUserUsernameIgnoreCase("super")).thenReturn(List.of());
    when(users.existsByUsernameIgnoreCase("demo.courier")).thenReturn(false);
    when(users.existsByEmailIgnoreCase("demo.courier@example.test")).thenReturn(false);
    when(roles.findByName("DELIVERY_PARTNER")).thenReturn(java.util.Optional.of(partnerRole));
    when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
      UserAccount user=invocation.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(user,"id",42L);
      return user;
    });
    when(memberships.saveAndFlush(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id","42","storeId","store-demo","location","hyderabad")));
    var controller=new DeliveryPartnerController(jdbc,users,memberships,roles,mock(PasswordEncoder.class));
    var auth=new UsernamePasswordAuthenticationToken("super","",List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    var request=new DeliveryPartnerController.CreateRequest("Demo Courier", "+91 00000 00001", "demo.courier",
      "demo.courier@example.test", "DemoOnly!2026", "Bike", "DEMO-001", "DEMO-LIC-001", "Demo address",
      true, "store-demo", "Demo Store", "Kukatpally", "Demo store address", "Hyderabad");

    var response=controller.create(request,auth);
    assertEquals("42",response.get("id"));
    ArgumentCaptor<Membership> assignment=ArgumentCaptor.forClass(Membership.class);
    verify(memberships).saveAndFlush(assignment.capture());
    assertEquals("metaarch-commerce",assignment.getValue().getOrganizationId());
    assertEquals("hyderabad",assignment.getValue().getLocationId());
    assertEquals("store-demo",assignment.getValue().getStoreId());
    assertEquals("Kukatpally",assignment.getValue().getStoreLocation());
  }

  @Test void locationAdminCannotCreatePartnerOutsideAssignedLocation() {
    MembershipRepository memberships=mock(MembershipRepository.class);
    Role role=new Role(); role.setName("LOCATION_ADMIN");
    Membership scope=new Membership(); scope.setRole(role); scope.setLocationId("hyderabad");
    when(memberships.findByUserUsernameIgnoreCase("location-admin")).thenReturn(List.of(scope));
    UserAccountRepository users=mock(UserAccountRepository.class);
    var controller=new DeliveryPartnerController(mock(JdbcTemplate.class),users,memberships,mock(RoleRepository.class),mock(PasswordEncoder.class));
    var auth=new UsernamePasswordAuthenticationToken("location-admin","",List.of(new SimpleGrantedAuthority("ROLE_LOCATION_ADMIN")));
    var request=new DeliveryPartnerController.CreateRequest("Demo Courier", "+91 00000 00001", "demo.courier",
      "demo.courier@example.test", "DemoOnly!2026", "Bike", "DEMO-001", "DEMO-LIC-001", "Demo address",
      true, "store-other", "Other Store", "Bengaluru", "Other store address", "Bengaluru");

    assertThrows(org.springframework.security.access.AccessDeniedException.class,() -> controller.create(request,auth));
    verifyNoInteractions(users);
  }
}
