package com.homeinspect.auth;

import com.homeinspect.auth.user.AuthUserService;
import com.homeinspect.auth.user.AuthUserService.NewUser;
import com.homeinspect.auth.user.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end OAuth2 / OIDC flows through MockMvc against a real PostgreSQL (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthServerIntegrationTests {

    static final String REDIRECT_URI = "http://app.test:8080/login/oauth2/code/home-inspect";
    static final String CODE_VERIFIER = "s3cr3t-code-verifier-that-is-long-enough-for-pkce-0123456789";

    @Autowired
    MockMvc mvc;
    @Autowired
    AuthUserService users;
    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void discoveryDocumentUsesTheConfiguredIssuer() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://auth.test:9000"))
                .andExpect(jsonPath("$.end_session_endpoint").value("http://auth.test:9000/connect/logout"));
    }

    @Test
    void loginPageIsRenderedInSpanish() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Iniciar sesión")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void authorizationCodeWithPkceIssuesIdTokenWithRoles() throws Exception {
        String password = createUserWithPassword("inspector.flow", Role.INSPECTOR);
        MockHttpSession session = new MockHttpSession();

        // 1. Not logged in: the authorization request is saved and the browser goes to /login
        mvc.perform(get(URI.create(authorizeUrl())).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // 2. Login resumes the authorization request
        String resumed = login(session, "inspector.flow", password);
        assertThat(resumed).contains("/oauth2/authorize");

        // 3. The authorization endpoint redirects to the app with a code (no consent screen)
        String code = authorizationCode(session, resumed);

        // 4. The app exchanges the code with its secret and the PKCE verifier
        MvcResult token = mvc.perform(post("/oauth2/token")
                        .with(httpBasic("home-inspect-web", "web-secret"))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", CODE_VERIFIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andReturn();

        Jwt idToken = jwtDecoder.decode(JsonPath.read(token.getResponse().getContentAsString(), "$.id_token"));
        assertThat(idToken.getSubject()).isEqualTo("inspector.flow");
        assertThat(idToken.getClaimAsStringList("roles")).containsExactly("INSPECTOR");
        assertThat(idToken.getClaimAsString("name")).isEqualTo("Inspector inspector.flow");
        assertThat(idToken.getClaimAsString("email")).isEqualTo("inspector.flow@example.test");

        String accessTokenValue = JsonPath.read(token.getResponse().getContentAsString(), "$.access_token");
        Jwt accessToken = jwtDecoder.decode(accessTokenValue);
        assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("INSPECTOR");

        // 5. The app reads the userinfo endpoint (the stored authorization must deserialize)
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + accessTokenValue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("inspector.flow"))
                .andExpect(jsonPath("$.name").value("Inspector inspector.flow"));
    }

    @Test
    void authorizationWithoutPkceIsRejected() throws Exception {
        String password = createUserWithPassword("inspector.nopkce", Role.INSPECTOR);
        MockHttpSession session = new MockHttpSession();
        login(session, "inspector.nopkce", password);
        // PKCE is mandatory for the web client: the request is refused and no code is issued
        mvc.perform(get("/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", "home-inspect-web")
                        .param("scope", "openid profile")
                        .param("redirect_uri", REDIRECT_URI)
                        .param("state", "xyz")
                        .session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void iconRequestedByTheBrowserDoesNotReplaceTheSavedAuthorizationRequest() throws Exception {
        String password = createUserWithPassword("inspector.icon", Role.INSPECTOR);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(URI.create(authorizeUrl())).session(session).accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrl("/login"));

        // Safari asks for the home screen icon while the login page is shown
        mvc.perform(get("/apple-touch-icon.png").session(session).accept(MediaType.IMAGE_PNG, MediaType.ALL))
                .andExpect(status().isNotFound());
        mvc.perform(get("/apple-touch-icon-precomposed.png").session(session).accept(MediaType.ALL))
                .andExpect(status().isNotFound());
        // Neither does any other non-page request
        mvc.perform(get("/js/missing.js").session(session).accept(MediaType.ALL));

        assertThat(login(session, "inspector.icon", password)).contains("/oauth2/authorize");
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeTheAuthorizationCompletes() throws Exception {
        String temporary = users.create(new NewUser("inspector.temp", "Temp", null, Role.INSPECTOR)).temporaryPassword();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(URI.create(authorizeUrl())).session(session)).andExpect(status().is3xxRedirection());

        String afterLogin = login(session, "inspector.temp", temporary);
        assertThat(afterLogin).isEqualTo("/password/change");

        // The authorization endpoint is blocked until the password is changed
        mvc.perform(get(URI.create(authorizeUrl())).session(session))
                .andExpect(redirectedUrl("/password/change"));

        // Wrong confirmation keeps the user on the form
        mvc.perform(post("/password/change").session(session).with(csrf())
                        .param("currentPassword", temporary)
                        .param("newPassword", "NewPassword-123")
                        .param("confirmPassword", "Different-123"))
                .andExpect(status().isOk());

        MvcResult changed = mvc.perform(post("/password/change").session(session).with(csrf())
                        .param("currentPassword", temporary)
                        .param("newPassword", "NewPassword-123")
                        .param("confirmPassword", "NewPassword-123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String resumed = changed.getResponse().getRedirectedUrl();
        assertThat(resumed).contains("/oauth2/authorize");
        assertThat(authorizationCode(session, resumed)).isNotBlank();
        assertThat(users.findByUsername("inspector.temp").orElseThrow().isMustChangePassword()).isFalse();
    }

    @Test
    void accountIsLockedAfterFiveFailedLogins() throws Exception {
        String password = createUserWithPassword("inspector.locked", Role.INSPECTOR);
        for (int i = 0; i < 5; i++) {
            assertThat(login(new MockHttpSession(), "inspector.locked", "wrong-password")).isEqualTo("/login?error");
        }
        // Even the right password is refused while locked
        assertThat(login(new MockHttpSession(), "inspector.locked", password)).isEqualTo("/login?locked");
    }

    @Test
    void noTokenIsIssuedOnceTheUserIsDisabledEvenWithALiveSession() throws Exception {
        String password = createUserWithPassword("inspector.revoked", Role.INSPECTOR);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(URI.create(authorizeUrl())).session(session));
        String code = authorizationCode(session, login(session, "inspector.revoked", password));

        users.setEnabled(users.findByUsername("inspector.revoked").orElseThrow().getId(), false);

        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("home-inspect-web", "web-secret"))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", CODE_VERIFIER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        String password = createUserWithPassword("inspector.disabled", Role.INSPECTOR);
        users.setEnabled(users.findByUsername("inspector.disabled").orElseThrow().getId(), false);
        assertThat(login(new MockHttpSession(), "inspector.disabled", password)).isEqualTo("/login?disabled");
    }

    // ---- helpers ----

    /** Creates a user and replaces the temporary password with a definitive one. */
    String createUserWithPassword(String username, Role role) {
        String temporary = users.create(new NewUser(username, "Inspector " + username,
                username + "@example.test", role)).temporaryPassword();
        String password = "Definitive-" + username.length() + "x";
        users.changePassword(username, temporary, password);
        return password;
    }

    String login(MockHttpSession session, String username, String password) throws Exception {
        return mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    String authorizationCode(MockHttpSession session, String authorizeUrl) throws Exception {
        String redirect = mvc.perform(get(URI.create(authorizeUrl)).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith(REDIRECT_URI);
        UriComponents uri = UriComponentsBuilder.fromUriString(redirect).build();
        assertThat(uri.getQueryParams().getFirst("state")).isEqualTo("state-1");
        return uri.getQueryParams().getFirst("code");
    }

    static String authorizeUrl() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(CODE_VERIFIER.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "home-inspect-web")
                .queryParam("scope", "openid profile")
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("state", "state-1")
                .queryParam("nonce", "nonce-1")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .encode().build().toUriString();
    }
}
