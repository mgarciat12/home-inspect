package com.homeinspect.auth.config;

import com.homeinspect.auth.user.AuthUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.UUID;

/**
 * On every start: registers (or updates) the two OAuth2 clients with the secrets and URLs from the
 * environment, and creates the initial administrator if missing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class StartupSeeder implements ApplicationRunner {

    static final String WEB_CLIENT_ID = "home-inspect-web";
    static final String ADMIN_API_CLIENT_ID = "home-inspect-admin-api";
    static final String USERS_ADMIN_SCOPE = "users.admin";

    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final AuthUserService users;

    @Override
    public void run(ApplicationArguments args) {
        seedWebClient();
        seedAdminApiClient();
        AuthProperties.Admin admin = properties.admin();
        users.bootstrapAdmin(admin.username(), admin.password(), admin.displayName());
    }

    private void seedWebClient() {
        AuthProperties.WebClient web = properties.clients().web();
        Assert.hasText(web.secret(), "WEB_CLIENT_SECRET is required");
        Assert.hasText(web.redirectUri(), "WEB_REDIRECT_URI is required");
        RegisteredClient client = RegisteredClient.withId(existingId(WEB_CLIENT_ID))
                .clientId(WEB_CLIENT_ID)
                .clientName("home-inspect")
                .clientSecret(passwordEncoder.encode(web.secret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(web.redirectUri())
                .postLogoutRedirectUri(web.postLogoutRedirectUri())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                // Trusted first-party client: PKCE mandatory, no consent screen
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofHours(12))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
        clients.save(client);
        log.info("OAuth2 client '{}' registered (redirect URI {})", WEB_CLIENT_ID, web.redirectUri());
    }

    private void seedAdminApiClient() {
        AuthProperties.AdminApiClient adminApi = properties.clients().adminApi();
        Assert.hasText(adminApi.secret(), "ADMIN_API_CLIENT_SECRET is required");
        RegisteredClient client = RegisteredClient.withId(existingId(ADMIN_API_CLIENT_ID))
                .clientId(ADMIN_API_CLIENT_ID)
                .clientName("home-inspect user administration")
                .clientSecret(passwordEncoder.encode(adminApi.secret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(USERS_ADMIN_SCOPE)
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(5)).build())
                .build();
        clients.save(client);
        log.info("OAuth2 client '{}' registered", ADMIN_API_CLIENT_ID);
    }

    /** Keeps the same internal id so that saving updates the existing row. */
    private String existingId(String clientId) {
        RegisteredClient existing = clients.findByClientId(clientId);
        return existing != null ? existing.getId() : UUID.randomUUID().toString();
    }
}
