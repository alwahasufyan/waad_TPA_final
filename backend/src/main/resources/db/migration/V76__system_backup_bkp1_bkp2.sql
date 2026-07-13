CREATE TABLE IF NOT EXISTS system_backup_settings (
    id BIGINT PRIMARY KEY,
    local_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    local_display_name VARCHAR(150) NOT NULL DEFAULT 'المسار المحلي الأساسي',
    local_path VARCHAR(1000) NOT NULL,
    retention_days INTEGER NOT NULL DEFAULT 30,
    updated_by VARCHAR(120),
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS system_backup_jobs (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(1000),
    file_size BIGINT,
    checksum VARCHAR(128),
    manifest_path VARCHAR(1000),
    note TEXT,
    created_by VARCHAR(120),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    error_message TEXT,
    environment VARCHAR(40),
    git_commit VARCHAR(80),
    encrypted BOOLEAN NOT NULL DEFAULT FALSE,
    destination_path VARCHAR(1000),
    backup_format VARCHAR(30),
    warnings TEXT
);

CREATE INDEX IF NOT EXISTS idx_system_backup_jobs_started_at ON system_backup_jobs(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_system_backup_jobs_status ON system_backup_jobs(status);
