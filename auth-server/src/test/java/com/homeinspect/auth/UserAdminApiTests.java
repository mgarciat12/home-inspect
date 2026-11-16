package com.homeinspect.auth;

import com.homeinspect.auth.user.AuthUserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserAdminApiTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    AuthUserService users;

    String adminApiToken() throws Exception {
        String body = mvc.perform(post("/oauth2/token")
                        .with(httpBasic("home-inspect-admin-api", "admin-api-secret"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "users.admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.access_token");
    }

    @Test
    void requiresAToken() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresTheUsersAdminScope() throws Exception {
        mvc.perform(get("/api/users").with(jwt().authorities(() -> "SCOPE_openid")))
                .andExpect(status().isForbidden());
    }

    @Test
    void initialAdministratorIsCreatedWithMandatoryPasswordChange() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + adminApiToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'admin')].role").value("ADMIN"))
                .andExpect(jsonPath("$[?(@.username == 'admin')].mustChangePassword").value(true));
    }

    @Test
    void createEditDisableAndResetPassword() throws Exception {
        String token = "Bearer " + adminApiToken();
        String created = mvc.perform(post("/api/users").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"Ana.Garcia","displayName":"Ana García","email":"ana@example.test","role":"INSPECTOR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("ana.garcia"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isString())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.user.id");

        // Duplicate username
        mvc.perform(post("/api/users").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ana.garcia","displayName":"Otra","role":"INSPECTOR"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("user.error.usernameTaken"));

        mvc.perform(put("/api/users/{id}", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ana García López"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana García López"))
                .andExpect(jsonPath("$.email").value("ana@example.test"));

        mvc.perform(put("/api/users/{id}/role", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(put("/api/users/{id}/enabled", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(jsonPath("$.enabled").value(false));

        String reset = mvc.perform(post("/api/users/{id}/password-reset", id).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String temporary = JsonPath.read(reset, "$.temporaryPassword");
        assertThat(temporary).hasSize(14);
        assertThat(users.get(id).isMustChangePassword()).isTrue();
    }

    @Test
    void theLastActiveAdministratorCannotBeDisabledOrDemoted() throws Exception {
        String token = "Bearer " + adminApiToken();
        // Disable every other administrator first so that "admin" is the only one left
        users.findAll().stream()
                .filter(user -> user.getRole() == com.homeinspect.auth.user.Role.ADMIN && !user.getUsername().equals("admin"))
                .forEach(user -> users.setEnabled(user.getId(), false));
        long adminId = users.findByUsername("admin").orElseThrow().getId();

        mvc.perform(put("/api/users/{id}/enabled", adminId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("user.error.lastAdmin"));
        mvc.perform(put("/api/users/{id}/role", adminId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"INSPECTOR\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + adminApiToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"displayName\":\"\",\"role\":\"INSPECTOR\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/999999").header("Authorization", "Bearer " + adminApiToken()))
                .andExpect(status().isNotFound());
    }
}
