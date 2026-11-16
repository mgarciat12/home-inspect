-- Two-step verification (TOTP, RFC 6238). The secret is stored encrypted (AES-GCM).
ALTER TABLE users
    ADD COLUMN totp_secret    TEXT,
    ADD COLUMN totp_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    -- Last accepted time step: a code cannot be used twice
    ADD COLUMN totp_last_step BIGINT;
