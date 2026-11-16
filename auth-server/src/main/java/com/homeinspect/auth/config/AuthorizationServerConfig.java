package com.homeinspect.auth.config;

import com.homeinspect.auth.keys.SigningKeyStore;
import com.homeinspect.auth.user.AuthUser;
import com.homeinspect.auth.user.AuthUserService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Authorization Server beans: clients, authorizations and consents in PostgreSQL, persistent
 * signing keys, fixed issuer and the {@code roles} claim.
 */
@Configuration(proxyBeanMethods = false)
class AuthorizationServerConfig {

    static final String ROLES_CLAIM = "roles";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(AuthProperties properties) {
        // Fixed issuer: the browser and the app container must use this exact URL
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcOperations jdbcOperations,
                                                    RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbcOperations, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcOperations jdbcOperations,
                                                                  RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, clients);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(AuthProperties properties) {
        JWKSet keys = new SigningKeyStore(properties.keys().dir(), properties.keys().password()).loadOrCreate();
        return new ImmutableJWKSet<>(keys);
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Adds the user's roles ({@code ADMIN}, {@code INSPECTOR}) to the access token and the ID token,
     * and name / e-mail to the ID token (also returned by the userinfo endpoint).
     * <p>
     * Roles are read from the database, not from the login session, so a role change applies to
     * the next token; a disabled (or deleted) user gets no token at all, even with a live session.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(AuthUserService users) {
        return context -> {
            if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                return;
            }
            boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
            boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
            if (!idToken && !accessToken) {
                return;
            }
            Authentication principal = context.getPrincipal();
            AuthUser user = users.findByUsername(principal.getName())
                    .filter(AuthUser::isEnabled)
                    .orElseThrow(() -> new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "User disabled", null)));
            // Mutable ArrayList: the claims are stored by the JDBC authorization service and read back
            // by the userinfo endpoint; Jackson's allowlist rejects immutable List.of(...) collections
            context.getClaims().claim(ROLES_CLAIM, new ArrayList<>(List.of(user.getRole().name())));
            if (idToken && context.getAuthorizedScopes().contains(OidcScopes.PROFILE)) {
                context.getClaims().claim("name", user.getDisplayName());
                context.getClaims().claim("preferred_username", user.getUsername());
                if (user.getEmail() != null) {
                    context.getClaims().claim("email", user.getEmail());
                }
            }
        };
    }
}
