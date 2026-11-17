package com.homeinspect.app.admin;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.admin.AuthServerUsersClient.RemoteUser;
import com.homeinspect.app.admin.AuthServerUsersClient.RemoteUserWithPassword;
import com.homeinspect.app.user.Role;
import com.homeinspect.app.user.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class AdminUsersWebTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestUsers users;
    @Autowired
    UserProfileService profiles;
    @MockitoBean
    AuthServerUsersClient client;

    static RemoteUser remote(long id, String username, Role role, boolean enabled) {
        return new RemoteUser(id, username, "Name " + username, username + "@example.test", role, enabled,
                false, false, null, false, Instant.now());
    }

    @Test
    void inspectorsCannotOpenTheAdminPanel() throws Exception {
        mvc.perform(get("/admin/users").with(users.login("insp.noadmin", Role.INSPECTOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListsUsersFromTheAuthServer() throws Exception {
        when(client.list()).thenReturn(List.of(remote(1, "admin.list", Role.ADMIN, true),
                remote(2, "maria", Role.INSPECTOR, false)));
        mvc.perform(get("/admin/users").with(users.login("admin.list", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Name maria")))
                .andExpect(content().string(containsString("Disabled")));
    }

    @Test
    void createShowsTheTemporaryPasswordOnce() throws Exception {
        when(client.create(any())).thenReturn(new RemoteUserWithPassword(remote(5, "pepe", Role.INSPECTOR, true), "TempPass123abc"));
        mvc.perform(post("/admin/users").with(users.login("admin.create", Role.ADMIN)).with(csrf())
                        .param("username", "pepe").param("displayName", "Pepe").param("role", "INSPECTOR"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("temporaryPassword", "TempPass123abc"));
    }

    @Test
    void createValidatesTheForm() throws Exception {
        mvc.perform(post("/admin/users").with(users.login("admin.invalid", Role.ADMIN)).with(csrf())
                        .param("username", "").param("displayName", "").param("role", "INSPECTOR"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This field is required")));
        verify(client, never()).create(any());
    }

    @Test
    void adminCannotDisableThemself() throws Exception {
        when(client.get(9)).thenReturn(remote(9, "admin.self", Role.ADMIN, true));
        mvc.perform(post("/admin/users/9/enabled").with(users.login("admin.self", Role.ADMIN)).with(csrf())
                        .param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("error"));
        verify(client, never()).setEnabled(anyLong(), eq(false));
    }

    @Test
    void disabledUserLosesTheirSessionOnTheNextRequest() throws Exception {
        var inspectorLogin = users.login("insp.todisable", Role.INSPECTOR);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/reports").session(session).with(inspectorLogin)).andExpect(status().isOk());

        when(client.get(7)).thenReturn(remote(7, "insp.todisable", Role.INSPECTOR, true));
        when(client.setEnabled(7, false)).thenReturn(remote(7, "insp.todisable", Role.INSPECTOR, false));
        mvc.perform(post("/admin/users/7/enabled").with(users.login("admin.disabler", Role.ADMIN)).with(csrf())
                        .param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(profiles.findBySubject("insp.todisable").orElseThrow().isActive()).isFalse();

        // Same authentication, now stale: the filter ends the session
        mvc.perform(get("/reports").session(session).with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.oidcLogin()
                        .idToken(token -> token.subject("insp.todisable"))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSPECTOR"))))
                .andExpect(redirectedUrl("/"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void authServerErrorsAreShownToTheAdmin() throws Exception {
        when(client.list()).thenThrow(new AdminApiException(null, "admin.error.unavailable", null));
        mvc.perform(get("/admin/users").with(users.login("admin.down", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Could not reach the authorization server")));
    }
}
