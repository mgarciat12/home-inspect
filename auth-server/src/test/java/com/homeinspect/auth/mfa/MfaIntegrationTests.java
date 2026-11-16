package com.homeinspect.auth.mfa;

import com.homeinspect.auth.TestcontainersConfiguration;
import com.homeinspect.auth.user.AuthUserService;
import com.homeinspect.auth.user.AuthUserService.NewUser;
import com.homeinspect.auth.user.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Two-step verification enabled (users choose to activate it). */
@SpringBootTest(properties = "home-inspect.auth.mfa.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MfaIntegrationTests {

    static final String PENDING_SECRET = "com.homeinspect.auth.mfa.MfaController.PENDING_SECRET";
    static final String REDIRECT_URI = "http://app.test:8080/login/oauth2/code/home-inspect";

    @Autowired
    MockMvc mvc;
    @Autowired
    AuthUserService users;

    String createUser(String username) {
        String temporary = users.create(new NewUser(username, "Name " + username, null, Role.INSPECTOR)).temporaryPassword();
        users.changePassword(username, temporary, "Definitive-1" + username.length());
        return "Definitive-1" + username.length();
    }

    String login(MockHttpSession session, String username, String password) throws Exception {
        return mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username).param("password", password))
                .andReturn().getResponse().getRedirectedUrl();
    }

    /** Activates the two-step verification of a logged-in user; returns the secret. */
    String enroll(MockHttpSession session) throws Exception {
        mvc.perform(get("/mfa/setup").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Activar verificación en dos pasos")));
        mvc.perform(get("/mfa/setup/qr").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        String secret = (String) session.getAttribute(PENDING_SECRET);
        mvc.perform(post("/mfa/setup").session(session).with(csrf()).param("code", "000000"))
                .andExpect(status().isOk()); // wrong code: stays on the form
        mvc.perform(post("/mfa/setup").session(session).with(csrf()).param("code", currentCode(secret)))
                .andExpect(redirectedUrl("/?mfaEnabled"));
        return secret;
    }

    static String currentCode(String secret) {
        return Totp.code(secret, Totp.step(Instant.now().getEpochSecond()));
    }

    @Test
    void enrolledUsersMustTypeTheCodeBeforeTheAuthorizationCompletes() throws Exception {
        String password = createUser("mfa.flow");
        MockHttpSession first = new MockHttpSession();
        assertThat(login(first, "mfa.flow", password)).isEqualTo("/");
        String secret = enroll(first);

        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(URI.create(authorizeUrl())).session(session));
        assertThat(login(session, "mfa.flow", password)).isEqualTo("/mfa/verify");

        // No authorization code while the second step is pending
        mvc.perform(get(URI.create(authorizeUrl())).session(session)).andExpect(redirectedUrl("/mfa/verify"));
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/mfa/verify"));

        mvc.perform(post("/mfa/verify").session(session).with(csrf()).param("code", "123456"))
                .andExpect(status().isOk());
        // The code used for the setup was consumed: the next one (inside the accepted drift) is valid
        String code = Totp.code(secret, Totp.step(Instant.now().getEpochSecond()) + 1);
        String resumed = mvc.perform(post("/mfa/verify").session(session).with(csrf()).param("code", code))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(resumed).contains("/oauth2/authorize");
        String toApp = mvc.perform(get(URI.create(resumed)).session(session))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(toApp).startsWith(REDIRECT_URI).contains("code=");

        // The same code cannot be used again
        MockHttpSession replay = new MockHttpSession();
        login(replay, "mfa.flow", password);
        mvc.perform(post("/mfa/verify").session(replay).with(csrf()).param("code", code))
                .andExpect(status().isOk());
    }

    @Test
    void tooManyWrongCodesLockTheAccount() throws Exception {
        String password = createUser("mfa.lock");
        MockHttpSession first = new MockHttpSession();
        login(first, "mfa.lock", password);
        enroll(first);

        MockHttpSession session = new MockHttpSession();
        assertThat(login(session, "mfa.lock", password)).isEqualTo("/mfa/verify");
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/mfa/verify").session(session).with(csrf()).param("code", "000000"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/mfa/verify").session(session).with(csrf()).param("code", "000000"))
                .andExpect(redirectedUrl("/login?locked"));
        assertThat(login(new MockHttpSession(), "mfa.lock", password)).isEqualTo("/login?locked");
    }

    @Test
    void usersWithoutTwoStepVerificationLogInAsBefore() throws Exception {
        String password = createUser("mfa.optional");
        assertThat(login(new MockHttpSession(), "mfa.optional", password)).isEqualTo("/");
    }

    @Test
    void adminApiResetsTheTwoStepVerification() throws Exception {
        String password = createUser("mfa.reset");
        MockHttpSession first = new MockHttpSession();
        login(first, "mfa.reset", password);
        enroll(first);
        long id = users.findByUsername("mfa.reset").orElseThrow().getId();

        String token = JsonPath.read(mvc.perform(post("/oauth2/token")
                        .with(httpBasic("home-inspect-admin-api", "admin-api-secret"))
                        .param("grant_type", "client_credentials").param("scope", "users.admin"))
                .andReturn().getResponse().getContentAsString(), "$.access_token");
        mvc.perform(get("/api/users/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.mfaEnabled").value(true));
        mvc.perform(post("/api/users/{id}/mfa-reset", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(false));
        assertThat(login(new MockHttpSession(), "mfa.reset", password)).isEqualTo("/");
    }

    static String authorizeUrl() throws Exception {
        String verifier = "mfa-code-verifier-that-is-long-enough-for-pkce-0123456789abcdef";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "home-inspect-web")
                .queryParam("scope", "openid profile")
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("state", "s")
                .queryParam("code_challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(digest))
                .queryParam("code_challenge_method", "S256")
                .encode().build().toUriString();
    }
}
