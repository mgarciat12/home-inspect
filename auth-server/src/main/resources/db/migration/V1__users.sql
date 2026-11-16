-- Users of the authorization server (schema "auth", created by Flyway)
CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    username             VARCHAR(100) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    display_name         VARCHAR(150) NOT NULL,
    email                VARCHAR(255),
    role                 VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'INSPECTOR')),
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts      INTEGER      NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_email_uq ON users (lower(email)) WHERE email IS NOT NULL;
