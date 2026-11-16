package com.homeinspect.auth.mfa;

import com.homeinspect.auth.TestcontainersConfiguration;
import com.homeinspect.auth.user.AuthUserService;
import com.homeinspect.auth.user.AuthUserService.NewUser;
import com.homeinspect.auth.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Two-step verification required for everybody. */
@SpringBootTest(properties = "home-inspect.auth.mfa.required=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MfaRequiredTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    AuthUserService users;

    @Test
    void newUsersChangeThePasswordAndThenSetUpTheVerification() throws Exception {
        String temporary = users.create(new NewUser("mfa.required", "Req", null, Role.INSPECTOR)).temporaryPassword();
        MockHttpSession session = new MockHttpSession();
        String next = mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", "mfa.required").param("password", temporary))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(next).isEqualTo("/password/change");

        next = mvc.perform(post("/password/change").session(session).with(csrf())
                        .param("currentPassword", temporary)
                        .param("newPassword", "Required-Pass-1")
                        .param("confirmPassword", "Required-Pass-1"))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(next).isEqualTo("/mfa/setup");
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/mfa/setup"));

        mvc.perform(get("/mfa/setup").session(session));
        String secret = (String) session.getAttribute(MfaIntegrationTests.PENDING_SECRET);
        mvc.perform(post("/mfa/setup").session(session).with(csrf())
                        .param("code", Totp.code(secret, Totp.step(Instant.now().getEpochSecond()))))
                .andExpect(redirectedUrl("/"));
        // Disabling is not allowed when it is required
        mvc.perform(post("/mfa/disable").session(session).with(csrf())
                        .param("password", "Required-Pass-1").param("code", "000000"))
                .andExpect(redirectedUrl("/?mfaError"));
    }
}
