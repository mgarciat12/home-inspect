package com.homeinspect.app.admin;

import com.homeinspect.app.user.Role;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Client of the auth-server user administration API ({@code /api/users}). */
@Component
public class AuthServerUsersClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;

    AuthServerUsersClient(RestClient authServerRestClient) {
        this.restClient = authServerRestClient;
    }

    public record RemoteUser(Long id, String username, String displayName, String email, Role role, boolean enabled,
                             boolean mustChangePassword, boolean locked, Instant lockedUntil, Instant createdAt) {
    }

    public record RemoteUserWithPassword(RemoteUser user, String temporaryPassword) {
    }

    public record NewUser(String username, String displayName, String email, Role role) {
    }

    public record UserChanges(String displayName, String email, Role role, Boolean enabled) {
    }

    public List<RemoteUser> list() {
        return call(() -> restClient.get().uri("/api/users").retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(new ParameterizedTypeReference<List<RemoteUser>>() {
                }));
    }

    public RemoteUser get(long id) {
        return call(() -> restClient.get().uri("/api/users/{id}", id).retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(RemoteUser.class));
    }

    public RemoteUserWithPassword create(NewUser user) {
        return call(() -> restClient.post().uri("/api/users").body(user).retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(RemoteUserWithPassword.class));
    }

    public RemoteUser update(long id, UserChanges changes) {
        return call(() -> restClient.put().uri("/api/users/{id}", id).body(changes).retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(RemoteUser.class));
    }

    public RemoteUser setEnabled(long id, boolean enabled) {
        return call(() -> restClient.put().uri("/api/users/{id}/enabled", id).body(Map.of("enabled", enabled)).retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(RemoteUser.class));
    }

    public RemoteUserWithPassword resetPassword(long id) {
        return call(() -> restClient.post().uri("/api/users/{id}/password-reset", id).retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(RemoteUserWithPassword.class));
    }

    private void fail(org.springframework.http.HttpRequest request,
                      org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
        String code = null;
        try {
            byte[] body = response.getBody().readAllBytes();
            if (body.length > 0) {
                // RFC 9457 problem detail with an extra "code" member (message key)
                Map<?, ?> problem = JSON.readValue(body, Map.class);
                Object value = problem.get("code");
                code = value != null ? value.toString() : null;
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            // Not a problem detail: generic error
        }
        throw AdminApiException.from(response.getStatusCode(), code);
    }

    private static <T> T call(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (AdminApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new AdminApiException(null, "admin.error.unavailable", e);
        }
    }
}
