package com.metaarch.orgaccess.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.metaarch.orgaccess.security.DatabaseUserDetailsService;
import com.metaarch.orgaccess.security.JwtTokenProvider;
import com.metaarch.orgaccess.security.OAuth2ProvisioningSuccessHandler;
import com.metaarch.orgaccess.config.SecurityConfig;

@WebMvcTest({UserController.class, StaffProfileController.class})
@org.springframework.test.context.TestPropertySource(properties = {"spring.cloud.config.enabled=false", "spring.cloud.config.import-check.enabled=false"})
@Import(SecurityConfig.class)
class UserControllerTest {
  @Autowired MockMvc mockMvc;
  @MockBean IdentityService identities;
  @MockBean JwtTokenProvider tokens;
  @MockBean DatabaseUserDetailsService users;
  @MockBean OAuth2ProvisioningSuccessHandler oauth2SuccessHandler;
  @MockBean org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN"})
  void superAdminCanUpdateOnlyOwnDisplayNameAndReceivesFreshProfile() throws Exception {
    when(jdbc.update(contains("UPDATE users SET display_name"), eq("Updated Admin"), eq("root-admin"))).thenReturn(1);
    when(jdbc.queryForList(contains("CASE WHEN r.name='CUSTOMER'"), eq("root-admin")))
        .thenReturn(java.util.List.of(java.util.Map.of("id", "1", "username", "root-admin", "name", "Updated Admin", "role", "super-admin")));

    mockMvc.perform(put("/api/users/me/profile/name").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\" Updated Admin \",\"id\":99,\"role\":\"GLOBAL_ADMIN\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Admin"))
        .andExpect(jsonPath("$.role").value("super-admin"));

    verify(jdbc).update(contains("UPDATE users SET display_name"), eq("Updated Admin"), eq("root-admin"));
    verify(jdbc).queryForList(contains("CASE WHEN r.name='CUSTOMER'"), eq("root-admin"));
  }

  @Test
  @WithMockUser(username = "global-admin", authorities = {"ROLE_GLOBAL_ADMIN"})
  void globalAdminCanUpdateOnlyOwnDisplayNameAndReceivesFreshProfile() throws Exception {
    when(jdbc.update(contains("UPDATE users SET display_name"), eq("Updated Global Admin"), eq("global-admin"))).thenReturn(1);
    when(jdbc.queryForList(contains("CASE WHEN r.name='CUSTOMER'"), eq("global-admin")))
        .thenReturn(java.util.List.of(java.util.Map.of("id", "2", "username", "global-admin", "name", "Updated Global Admin", "role", "global-admin")));
    mockMvc.perform(put("/api/users/me/profile/name").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Updated Global Admin\",\"id\":1,\"username\":\"root-admin\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Global Admin"));
    verify(jdbc).update(contains("UPDATE users SET display_name"), eq("Updated Global Admin"), eq("global-admin"));
  }

  @Test
  @WithMockUser(username = "customer", authorities = {"ROLE_CUSTOMER"})
  void customerCannotUpdateAdminName() throws Exception {
    mockMvc.perform(put("/api/users/me/profile/name").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Changed\"}"))
        .andExpect(status().isForbidden());
    verify(jdbc, never()).update(contains("UPDATE users SET display_name"), any(), any());
  }

  @Test
  @WithMockUser(username = "global-admin", authorities = {"ROLE_GLOBAL_ADMIN"})
  void adminNameUpdateRejectsWhitespaceOnlyName() throws Exception {
    mockMvc.perform(put("/api/users/me/profile/name").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"   \"}"))
        .andExpect(status().isBadRequest());
    verify(jdbc, never()).update(contains("UPDATE users SET display_name"), any(), any());
  }

  @Test
  @WithMockUser(authorities = {"ROLE_GLOBAL_ADMIN", "PERM_USER_WRITE"})
  void validatesUserCreationPayload() throws Exception {
    mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
{\"username\":\"x\",\"email\":\"bad\",\"password\":\"short\",\"role\":\"CUSTOMER\"}
"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN", "PERM_USER_WRITE"})
  void superAdminWithUserWriteCanCreateLocationAdmin() throws Exception {
    UserAccount created = new UserAccount();
    created.setUsername("location-admin");
    created.setEmail("location-admin@example.com");
    when(identities.create(any(UserCreateRequest.class), eq("root-admin"))).thenReturn(created);

    mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"username":"location-admin","email":"location-admin@example.com","password":"ValidPass123",
           "role":"LOCATION_ADMIN","organizationId":"metaarch-commerce","location":"hyderabad",
           "assignedBy":"spoofed-client-identity"}
          """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("location-admin"))
        .andExpect(jsonPath("$.email").value("location-admin@example.com"));

    verify(identities).create(any(UserCreateRequest.class), eq("root-admin"));
  }

  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN"})
  void superAdminRoleCanCreateDespiteStalePermissionMapping() throws Exception {
    UserAccount created = new UserAccount();
    created.setUsername("location-admin");
    created.setEmail("location-admin@example.com");
    when(identities.create(any(UserCreateRequest.class), eq("root-admin"))).thenReturn(created);

    mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"username":"location-admin","email":"location-admin@example.com","password":"ValidPass123",
           "role":"LOCATION_ADMIN","organizationId":"metaarch-commerce","location":"hyderabad"}
          """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("location-admin"));

    verify(identities).create(any(UserCreateRequest.class), eq("root-admin"));
  }

  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN"})
  void superAdminCanCreateGlobalAdminWithoutLocationAssignment() throws Exception {
    UserAccount created = new UserAccount();
    created.setUsername("global-admin");
    created.setEmail("global-admin@example.com");
    when(identities.create(any(UserCreateRequest.class), eq("root-admin"))).thenReturn(created);

    mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"username":"global-admin","email":"global-admin@example.com","password":"ValidPass123",
           "role":"GLOBAL_ADMIN","organizationId":"metaarch-commerce"}
          """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("global-admin"));

    verify(identities).create(any(UserCreateRequest.class), eq("root-admin"));
  }

  @Test
  @WithMockUser(username = "global-admin", authorities = {"ROLE_GLOBAL_ADMIN", "PERM_USER_WRITE"})
  void globalAdminCannotProvisionStaffThroughSuperAdminEndpoint() throws Exception {
    mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"username":"location-admin","email":"location-admin@example.com","password":"ValidPass123",
           "role":"LOCATION_ADMIN","organizationId":"metaarch-commerce","location":"hyderabad"}
          """))
        .andExpect(status().isForbidden());

    verify(identities, never()).create(any(UserCreateRequest.class), any());
  }

  @Test
  @WithMockUser(username = "location-admin", authorities = {"ROLE_LOCATION_ADMIN"})
  void locationAdminCannotProvisionAnotherAdministrator() throws Exception {
    mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"username":"other-location-admin","email":"other@example.com","password":"ValidPass123",
           "role":"LOCATION_ADMIN","organizationId":"metaarch-commerce","location":"chennai"}
          """))
        .andExpect(status().isForbidden());
    verify(identities, never()).create(any(UserCreateRequest.class), any());
  }

  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN", "PERM_USER_WRITE"})
  void duplicateMobileIsReturnedAsAnActionableFieldConflict() throws Exception {
    when(identities.create(any(UserCreateRequest.class), eq("root-admin")))
        .thenThrow(new DuplicateUserContactException(java.util.Map.of(
            "mobile", "This mobile number is already registered. Use another mobile number.")));

    mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"username":"location-admin","email":"location-admin@example.com","password":"ValidPass123",
           "mobile":"9876543210","role":"LOCATION_ADMIN","organizationId":"metaarch-commerce","location":"hyderabad"}
          """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.fields.mobile").value("This mobile number is already registered. Use another mobile number."));
  }

  @Test
  @WithMockUser(username = "ordinary-user", authorities = {"PERM_USER_WRITE"})
  void userWritePermissionWithoutAnAdminRoleIsForbidden() throws Exception {
    mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"username":"location-admin","email":"location-admin@example.com","password":"ValidPass123",
           "role":"LOCATION_ADMIN","organizationId":"metaarch-commerce","location":"hyderabad"}
          """))
        .andExpect(status().isForbidden());

    verify(identities, never()).create(any(UserCreateRequest.class), any());
  }

  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN"})
  void superAdminCanEditGlobalAdmin() throws Exception {
    UserAccount updated = new UserAccount(); updated.setUsername("global"); updated.setEmail("global@example.com");
    when(identities.updateStaff(eq(12L), any(UserUpdateRequest.class), eq("root-admin"))).thenReturn(updated);
    mockMvc.perform(put("/api/users/12").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
{"name":"Global","email":"global@example.com","mobile":"12345678","role":"GLOBAL_ADMIN","status":"Active"}
"""))
        .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("global"));
    verify(identities).updateStaff(eq(12L), any(UserUpdateRequest.class), eq("root-admin"));
  }

  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN"})
  void superAdminCanEditLocationAdminInOrganization() throws Exception {
    UserAccount updated = new UserAccount(); updated.setUsername("location"); updated.setEmail("location@example.com");
    when(identities.updateStaff(eq(13L), any(UserUpdateRequest.class), eq("root-admin"))).thenReturn(updated);
    mockMvc.perform(put("/api/users/13").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
{"name":"Location","email":"location@example.com","role":"LOCATION_ADMIN","status":"Active","location":"hyderabad"}
"""))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "global", authorities = {"ROLE_GLOBAL_ADMIN", "PERM_USER_WRITE"})
  void globalAdminCannotEditSuperAdmin() throws Exception {
    mockMvc.perform(put("/api/users/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
{"name":"Root","email":"root@example.com","role":"SUPER_ADMIN","status":"Active"}
"""))
        .andExpect(status().isForbidden());
    verify(identities, never()).updateStaff(any(), any(), any());
  }

  @Test
  @WithMockUser(username = "global", authorities = {"ROLE_GLOBAL_ADMIN", "PERM_USER_WRITE"})
  void globalAdminCannotEditLocationAdminAssignment() throws Exception {
    mockMvc.perform(put("/api/users/13").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name":"Location","email":"location@example.com","role":"LOCATION_ADMIN",
           "status":"Active","location":"chennai"}
          """))
        .andExpect(status().isForbidden());
    verify(identities, never()).updateStaff(any(), any(), any());
  }

  @Test
  @WithMockUser(username = "location", authorities = {"ROLE_LOCATION_ADMIN"})
  void locationAdminCannotUseStaffUpdateEndpoint() throws Exception {
    mockMvc.perform(put("/api/users/42").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
{"name":"Other","email":"other@example.com","role":"LOCATION_ADMIN","status":"Active","location":"other-location"}
"""))
        .andExpect(status().isForbidden());
    verify(identities, never()).updateStaff(any(), any(), any());
  }

  @Test
  @WithMockUser(username = "ordinary", authorities = {"PERM_USER_WRITE"})
  void ordinaryUserCannotUseStaffUpdateEndpoint() throws Exception {
    mockMvc.perform(put("/api/users/42").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
{"name":"Other","email":"other@example.com","role":"GLOBAL_ADMIN","status":"Active"}
"""))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN"})
  void invalidEditFieldsFailValidation() throws Exception {
    mockMvc.perform(put("/api/users/42").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("""
{"name":"","email":"not-an-email","role":"SUPER_ADMIN","status":"Active"}
"""))
        .andExpect(status().isBadRequest());
    verify(identities, never()).updateStaff(any(), any(), any());
  }
  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN"})
  void superAdminCanListStaffForAdminWorkspace() throws Exception {
    when(identities.listStaff("root-admin")).thenReturn(java.util.List.of());
    mockMvc.perform(get("/api/users")).andExpect(status().isOk());
    verify(identities).listStaff("root-admin");
  }

  @Test
  @WithMockUser(username = "root-admin", authorities = {"ROLE_SUPER_ADMIN"})
  void superAdminCanDeleteManagedStaffAccount() throws Exception {
    mockMvc.perform(delete("/api/users/42").with(csrf()))
        .andExpect(status().isNoContent());
    verify(identities).deleteStaff(42L, "root-admin");
  }

  @Test
  @WithMockUser(username = "global-admin", authorities = {"ROLE_GLOBAL_ADMIN"})
  void globalAdminCannotDeleteStaffAccount() throws Exception {
    mockMvc.perform(delete("/api/users/42").with(csrf()))
        .andExpect(status().isForbidden());
    verify(identities, never()).deleteStaff(any(), any());
  }

  @Test
  @WithMockUser(username = "global-admin", authorities = {"ROLE_GLOBAL_ADMIN"})
  void globalAdminCannotListStaff() throws Exception {
    mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
    verify(identities, never()).listStaff(any());
  }

  @Test
  @WithMockUser(username = "location-admin", authorities = {"ROLE_LOCATION_ADMIN"})
  void locationAdminCannotListOrDeleteStaff() throws Exception {
    mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/users/42").with(csrf())).andExpect(status().isForbidden());
    verify(identities, never()).listStaff(any());
    verify(identities, never()).deleteStaff(any(), any());
  }
}
