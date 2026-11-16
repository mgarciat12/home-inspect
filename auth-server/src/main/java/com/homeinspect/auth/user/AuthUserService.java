package com.homeinspect.auth.user;

import com.homeinspect.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Users of the authorization server: administration, password changes and brute-force lockout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthUserService {

    private final AuthUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /** Result of an operation that generates a temporary password (shown once to the administrator). */
    public record UserWithPassword(AuthUser user, String temporaryPassword) {
    }

    public record NewUser(String username, String displayName, String email, Role role) {
    }

    public record UserChanges(String displayName, String email, Role role, Boolean enabled) {
    }

    @Transactional(readOnly = true)
    public List<AuthUser> findAll() {
        return repository.findAllByOrderByDisplayNameAsc();
    }

    @Transactional(readOnly = true)
    public AuthUser get(long id) {
        return repository.findById(id).orElseThrow(UserException::notFound);
    }

    @Transactional(readOnly = true)
    public Optional<AuthUser> findByUsername(String username) {
        return repository.findByUsernameIgnoreCase(username);
    }

    /** Creates a user with a temporary password that must be changed at first login. */
    @Transactional
    public UserWithPassword create(NewUser command) {
        String username = command.username().trim().toLowerCase();
        if (!username.matches("[a-z0-9._-]{3,50}")) {
            throw UserException.invalid("user.error.usernameFormat");
        }
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw UserException.conflict("user.error.usernameTaken");
        }
        String email = normalizeEmail(command.email());
        if (email != null && repository.existsByEmailIgnoreCase(email)) {
            throw UserException.conflict("user.error.emailTaken");
        }
        String temporary = passwordPolicy.temporaryPassword();
        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setDisplayName(command.displayName().trim());
        user.setEmail(email);
        user.setRole(command.role());
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(true);
        repository.save(user);
        log.info("User '{}' created with role {}", username, command.role());
        return new UserWithPassword(user, temporary);
    }

    /** Partial update: null fields are left unchanged. */
    @Transactional
    public AuthUser update(long id, UserChanges changes) {
        AuthUser user = get(id);
        if (StringUtils.hasText(changes.displayName())) {
            user.setDisplayName(changes.displayName().trim());
        }
        if (changes.email() != null) {
            String email = normalizeEmail(changes.email());
            if (email != null && repository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
                throw UserException.conflict("user.error.emailTaken");
            }
            user.setEmail(email);
        }
        if (changes.role() != null && changes.role() != user.getRole()) {
            if (user.getRole() == Role.ADMIN) {
                ensureAnotherActiveAdmin(user);
            }
            user.setRole(changes.role());
            revokeAuthorizations(user);
        }
        if (changes.enabled() != null && changes.enabled() != user.isEnabled()) {
            if (!changes.enabled() && user.getRole() == Role.ADMIN) {
                ensureAnotherActiveAdmin(user);
            }
            user.setEnabled(changes.enabled());
            if (changes.enabled()) {
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
            } else {
                revokeAuthorizations(user);
            }
        }
        return user;
    }

    @Transactional
    public AuthUser setEnabled(long id, boolean enabled) {
        return update(id, new UserChanges(null, null, null, enabled));
    }

    @Transactional
    public AuthUser changeRole(long id, Role role) {
        return update(id, new UserChanges(null, null, role, null));
    }

    /** Generates a new temporary password and forces the user to change it at next login. */
    @Transactional
    public UserWithPassword resetPassword(long id) {
        AuthUser user = get(id);
        String temporary = passwordPolicy.temporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        revokeAuthorizations(user);
        log.info("Password of user '{}' reset by an administrator", user.getUsername());
        return new UserWithPassword(user, temporary);
    }

    /** The user changes their own password (mandatory after a reset). */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AuthUser user = repository.findByUsernameIgnoreCase(username).orElseThrow(UserException::notFound);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw UserException.invalid("password.error.current");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw UserException.invalid("password.error.sameAsCurrent");
        }
        passwordPolicy.check(user.getUsername(), newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
    }

    /** Creates the initial administrator if it does not exist yet. */
    @Transactional
    public void bootstrapAdmin(String username, String password, String displayName) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("ADMIN_USERNAME / ADMIN_PASSWORD not set: no initial administrator created");
            return;
        }
        String normalized = username.trim().toLowerCase();
        if (repository.existsByUsernameIgnoreCase(normalized)) {
            return;
        }
        AuthUser admin = new AuthUser();
        admin.setUsername(normalized);
        admin.setDisplayName(displayName);
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setMustChangePassword(true);
        repository.save(admin);
        log.info("Initial administrator '{}' created (must change the password at first login)", normalized);
    }

    // ---- brute-force protection ----

    @Transactional
    public void loginFailed(String username) {
        repository.findByUsernameIgnoreCase(username).ifPresent(user -> {
            Instant now = clock.instant();
            if (user.isLocked(now)) {
                return;
            }
            int attempts = user.getFailedAttempts() + 1;
            if (attempts >= properties.lockout().maxAttempts()) {
                user.setLockedUntil(now.plus(properties.lockout().duration()));
                user.setFailedAttempts(0);
                log.warn("User '{}' locked for {} after {} failed logins",
                        user.getUsername(), properties.lockout().duration(), attempts);
            } else {
                user.setFailedAttempts(attempts);
            }
        });
    }

    @Transactional
    public void loginSucceeded(String username) {
        repository.findByUsernameIgnoreCase(username).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        });
    }

    // ---- helpers ----

    private void ensureAnotherActiveAdmin(AuthUser user) {
        long activeAdmins = repository.countByRoleAndEnabledTrue(Role.ADMIN);
        boolean countsThisOne = user.isEnabled() && user.getRole() == Role.ADMIN;
        if (activeAdmins - (countsThisOne ? 1 : 0) < 1) {
            throw UserException.conflict("user.error.lastAdmin");
        }
    }

    /** Invalidates refresh / access tokens already issued to the user (disabled, demoted, password reset). */
    private void revokeAuthorizations(AuthUser user) {
        jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE principal_name = ?", user.getUsername());
    }

    private static String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
    }
}
