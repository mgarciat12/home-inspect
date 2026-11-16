package com.homeinspect.auth.api;

import com.homeinspect.auth.user.AuthUser;
import com.homeinspect.auth.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request / response bodies of the user administration API. */
final class UserDtos {

    private UserDtos() {
    }

    record UserResponse(Long id, String username, String displayName, String email, Role role,
                        boolean enabled, boolean mustChangePassword, boolean locked, Instant lockedUntil,
                        boolean mfaEnabled, Instant createdAt) {

        static UserResponse of(AuthUser user, Instant now) {
            return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                    user.getRole(), user.isEnabled(), user.isMustChangePassword(), user.isLocked(now),
                    user.isLocked(now) ? user.getLockedUntil() : null, user.isTotpEnabled(), user.getCreatedAt());
        }
    }

    /** Returned once when a temporary password is generated (creation or reset). */
    record UserWithPasswordResponse(UserResponse user, String temporaryPassword) {
    }

    record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(max = 150) String displayName,
            @Email @Size(max = 255) String email,
            @NotNull Role role) {
    }

    /** Partial update: omitted (null) fields are not changed. An empty e-mail removes it. */
    record UpdateUserRequest(
            @Size(max = 150) String displayName,
            @Email @Size(max = 255) String email,
            Role role,
            Boolean enabled) {
    }

    record EnabledRequest(@NotNull Boolean enabled) {
    }

    record RoleRequest(@NotNull Role role) {
    }
}
