package com.metaarch.orgaccess.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class StaffProfilePersistenceTest {
  @Test
  void superAndGlobalAdminNameUpdatesPersistOnlyToAuthenticatedAccountsAndFreshReadsReturnThem() {
    var dataSource = new DriverManagerDataSource("jdbc:h2:mem:staff-profile;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    var jdbc = new JdbcTemplate(dataSource);
    createSchema(jdbc);
    jdbc.update("INSERT INTO roles(id,name) VALUES (1,'SUPER_ADMIN')");
    jdbc.update("INSERT INTO users(id,username,email,mobile,status,enabled,locked) VALUES (1,'root-admin','root@example.test','9876543210','Active',TRUE,FALSE)");
    jdbc.update("INSERT INTO users(id,username,email,mobile,status,enabled,locked,display_name) VALUES (2,'other-admin','other@example.test','9876543211','Active',TRUE,FALSE,'Other Admin')");
    jdbc.update("INSERT INTO users(id,username,email,mobile,status,enabled,locked) VALUES (3,'global-admin','global@example.test','9876543212','Active',TRUE,FALSE)");
    jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (1,1)");
    jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (2,1)");
    jdbc.update("INSERT INTO roles(id,name) VALUES (2,'GLOBAL_ADMIN')");
    jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (3,2)");
    var auth = new UsernamePasswordAuthenticationToken("root-admin", "",
        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    var existingNameAuth = new UsernamePasswordAuthenticationToken("other-admin", "",
        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    var globalAuth = new UsernamePasswordAuthenticationToken("global-admin", "",
        List.of(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")));
    var controller = new StaffProfileController(jdbc);

    assertThat(controller.me(existingNameAuth).get("name")).isEqualTo("Other Admin");
    assertThat(controller.me(auth).get("name")).isNull();
    var saved = controller.updateSuperAdminName(new StaffProfileController.SuperAdminNameRequest("Root Admin"), auth);
    var refreshed = controller.me(auth);

    assertThat(saved.get("name")).isEqualTo("Root Admin");
    assertThat(refreshed.get("name")).isEqualTo("Root Admin");
    var globalSaved = controller.updateSuperAdminName(new StaffProfileController.SuperAdminNameRequest("Global Admin"), globalAuth);
    assertThat(globalSaved.get("name")).isEqualTo("Global Admin");
    assertThat(controller.me(globalAuth).get("name")).isEqualTo("Global Admin");
    assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE username='other-admin'", String.class)).isEqualTo("Other Admin");
  }

  private void createSchema(JdbcTemplate jdbc) {
    jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(190),email VARCHAR(255),mobile VARCHAR(30),display_name VARCHAR(180),status VARCHAR(30),enabled BOOLEAN,locked BOOLEAN,vehicle_type VARCHAR(40),vehicle_number VARCHAR(40),licence_number VARCHAR(60),address VARCHAR(700),emergency_contact VARCHAR(30),delivery_latitude DOUBLE,delivery_longitude DOUBLE,delivery_location_updated_at TIMESTAMP)");
    jdbc.execute("CREATE TABLE customers(user_id BIGINT,full_name VARCHAR(180))");
    jdbc.execute("CREATE TABLE memberships(user_id BIGINT,store_id VARCHAR(80),store_name VARCHAR(180),store_location VARCHAR(160),store_address VARCHAR(700),location_id VARCHAR(120),role_id BIGINT)");
    jdbc.execute("CREATE TABLE user_roles(user_id BIGINT,role_id BIGINT)");
    jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY,name VARCHAR(80))");
  }
}
