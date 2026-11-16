package com.homeinspect.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Settings of the authorization server, all of them overridable with environment variables
 * (see application.yml and .env.example).
 */
@Validated
@ConfigurationProperties("home-inspect.auth")
public record AuthProperties(
        @NotBlank String issuer,
        Keys keys,
        Admin admin,
        Clients clients,
        Lockout lockout,
        Mfa mfa) {

    /** JWT signing keys, persisted in a directory (Docker volume). */
    public record Keys(Path dir, String password) {
    }

    /** Administrator created on first start when it does not exist. */
    public record Admin(String username, String password, @DefaultValue("Administrador") String displayName) {
    }

    public record Clients(WebClient web, AdminApiClient adminApi) {
    }

    /** {@code home-inspect-web}: authorization code + PKCE, used by the browser. */
    public record WebClient(String secret, String redirectUri, String postLogoutRedirectUri) {
    }

    /** {@code home-inspect-admin-api}: client credentials, scope users.admin. */
    public record AdminApiClient(String secret) {
    }

    /**
     * Two-step verification with an authenticator app (TOTP). {@code enabled}: users can activate it
     * and it is asked to those who did; {@code required}: every user must set it up at next login.
     */
    public record Mfa(@DefaultValue("false") boolean enabled, @DefaultValue("false") boolean required,
                      @DefaultValue("home-inspect") String issuerName) {

        public boolean active() {
            return enabled || required;
        }
    }

    /** Temporary lock after too many failed logins. */
    public record Lockout(@DefaultValue("5") int maxAttempts, @DefaultValue("15m") Duration duration) {
    }
}
