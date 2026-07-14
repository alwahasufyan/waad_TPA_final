ALTER TABLE system_monitoring_settings
    ADD COLUMN IF NOT EXISTS automatic_monitoring_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS check_interval_seconds INTEGER NOT NULL DEFAULT 300,
    ADD COLUMN IF NOT EXISTS disk_warning_percent INTEGER NOT NULL DEFAULT 80,
    ADD COLUMN IF NOT EXISTS disk_critical_percent INTEGER NOT NULL DEFAULT 90,
    ADD COLUMN IF NOT EXISTS max_backup_age_hours INTEGER NOT NULL DEFAULT 72,
    ADD COLUMN IF NOT EXISTS repeated_error_threshold INTEGER NOT NULL DEFAULT 10,
    ADD COLUMN IF NOT EXISTS repeated_error_window_minutes INTEGER NOT NULL DEFAULT 15,
    ADD COLUMN IF NOT EXISTS alert_cooldown_seconds INTEGER NOT NULL DEFAULT 1800,
    ADD COLUMN IF NOT EXISTS last_auto_check_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_auto_check_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS last_auto_check_message VARCHAR(500);

CREATE TABLE IF NOT EXISTS system_monitoring_alert_state (
    alert_key VARCHAR(80) PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    severity INTEGER NOT NULL DEFAULT 0,
    first_detected_at TIMESTAMP,
    last_detected_at TIMESTAMP,
    last_sent_at TIMESTAMP,
    recovered_at TIMESTAMP,
    last_summary VARCHAR(1000),
    alert_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS system_monitoring_error_events (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    status_code INTEGER NOT NULL,
    method VARCHAR(20),
    path VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_system_monitoring_error_events_occurred_at
    ON system_monitoring_error_events(occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_system_monitoring_alert_state_updated_at
    ON system_monitoring_alert_state(updated_at DESC);
