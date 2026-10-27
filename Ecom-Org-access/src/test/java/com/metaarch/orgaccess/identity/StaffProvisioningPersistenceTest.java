package com.metaarch.orgaccess.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.metaarch.orgaccess.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest(properties = {
    "spring.cloud.config.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.sql.init.mode=never"
})
@Import(IdentityService.class)
@TestPropertySource(properties = "spring.profiles.active=test")
class StaffProvisioningPersistenceTest {
  @Autowired UserAccountRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired RoleRepository roles;
  @Autowired AuditLogRepository auditLogs;
  @Autowired JdbcTemplate jdbc;
  @Autowired IdentityService identities;
  @MockBean PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS customer_contact_claims (contact VARCHAR(255) PRIMARY KEY, user_id BIGINT NOT NULL)");
    when(passwordEncoder.encode("ValidPass123")).thenReturn("test-only-hash");
  }

  @Test
  void persistsIndependentGlobalAndLocationAdminAssignments() {
    Role globalRole = new Role();
    globalRole.setName("GLOBAL_ADMIN");
    roles.save(globalRole);
    Role locationRole = new Role();
    locationRole.setName("LOCATION_ADMIN");
    roles.save(locationRole);

    UserAccount global = identities.create(new UserCreateRequest("qa-global-admin", "qa-global@example.test",
        "ValidPass123", "GLOBAL_ADMIN", "QA Global Admin", null, "Active", null, null,
        null, null, null, null, null, null, null, null, null, null, null, null), "qa-super-admin");
    UserAccount location = identities.create(new UserCreateRequest("qa-location-admin", "qa-location@example.test",
        "ValidPass123", "LOCATION_ADMIN", "QA Location Admin", null, "Active", null, "hyderabad",
        null, null, null, null, null, null, null, null, null, null, null, null), "qa-super-admin");

    UserAccount storedGlobal = users.findByUsernameIgnoreCase("qa-global-admin").orElseThrow();
    Membership globalAssignment = memberships.findByUserId(storedGlobal.getId()).getFirst();
    UserAccount storedLocation = users.findByUsernameIgnoreCase("qa-location-admin").orElseThrow();
    Membership locationAssignment = memberships.findByUserId(storedLocation.getId()).getFirst();

    assertThat(global.getId()).isNotNull();
    assertThat(storedGlobal.getRoles()).extracting(Role::getName).containsExactly("GLOBAL_ADMIN");
    assertThat(globalAssignment.getOrganizationId()).isEqualTo("metaarch-commerce");
    assertThat(globalAssignment.getLocationId()).isNull();
    assertThat(globalAssignment.getStoreId()).isNull();
    assertThat(globalAssignment.getAssignedBy()).isEqualTo("qa-super-admin");

    assertThat(location.getId()).isNotNull();
    assertThat(storedLocation.getRoles()).extracting(Role::getName).containsExactly("LOCATION_ADMIN");
    assertThat(locationAssignment.getOrganizationId()).isEqualTo("metaarch-commerce");
    assertThat(locationAssignment.getLocationId()).isEqualTo("hyderabad");
    assertThat(locationAssignment.getStoreId()).isNull();
    assertThat(locationAssignment.getAssignedBy()).isEqualTo("qa-super-admin");
    assertThat(auditLogs.count()).isEqualTo(2);
  }
}
