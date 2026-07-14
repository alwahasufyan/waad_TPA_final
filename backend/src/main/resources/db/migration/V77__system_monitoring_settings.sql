CREATE TABLE IF NOT EXISTS system_monitoring_settings (
    id BIGINT PRIMARY KEY,
    telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    telegram_bot_token VARCHAR(500),
    telegram_chat_id VARCHAR(120),
    telegram_thread_id VARCHAR(120),
    alert_environment VARCHAR(80) NOT NULL DEFAULT 'local',
    min_interval_seconds INTEGER NOT NULL DEFAULT 300,
    recovery_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(120),
    updated_at TIMESTAMP,
    last_test_at TIMESTAMP,
    last_test_status VARCHAR(30),
    last_test_message VARCHAR(500)
);
