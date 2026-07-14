-- BKP-4 / Danger Zone hardening: Telegram OTP for production destructive ops + maintenance mode.

CREATE TABLE IF NOT EXISTS danger_zone_otp (
    id            BIGSERIAL PRIMARY KEY,
    operation     VARCHAR(20)  NOT NULL,          -- RESTORE / RESET
    username      VARCHAR(150) NOT NULL,
    code_hash     VARCHAR(128) NOT NULL,          -- SHA-256 hash; never the plaintext code
    environment   VARCHAR(40),
    created_at    TIMESTAMP    NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    consumed      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_danger_zone_otp_lookup
    ON danger_zone_otp(username, operation, consumed, created_at DESC);

CREATE TABLE IF NOT EXISTS system_maintenance_mode (
    id          BIGINT PRIMARY KEY,
    enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    reason      VARCHAR(500),
    updated_by  VARCHAR(150),
    updated_at  TIMESTAMP
);

INSERT INTO system_maintenance_mode (id, enabled, updated_at)
VALUES (1, FALSE, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
