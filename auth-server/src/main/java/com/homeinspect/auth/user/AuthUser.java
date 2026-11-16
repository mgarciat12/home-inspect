package com.homeinspect.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Also the "sub" of the tokens: it never changes once created. */
    @Column(nullable = false, unique = true, updatable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private boolean enabled = true;

    @Column(name = "must_change_password")
    private boolean mustChangePassword;

    @Column(name = "failed_attempts")
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** TOTP secret, encrypted (see TotpService). */
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled")
    private boolean totpEnabled;

    @Column(name = "totp_last_step")
    private Long totpLastStep;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
