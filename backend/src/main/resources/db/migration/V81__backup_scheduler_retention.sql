-- BKP-4: automatic backup scheduler + retention purge tracking on the backup settings row.

ALTER TABLE system_backup_settings
    ADD COLUMN IF NOT EXISTS auto_backup_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS auto_backup_type        VARCHAR(40) NOT NULL DEFAULT 'FULL_SYSTEM',
    ADD COLUMN IF NOT EXISTS auto_backup_hour        INTEGER     NOT NULL DEFAULT 2,
    ADD COLUMN IF NOT EXISTS auto_backup_minute      INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_auto_backup_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_auto_backup_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS last_auto_backup_message VARCHAR(500),
    ADD COLUMN IF NOT EXISTS last_purge_at           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_purge_status       VARCHAR(30),
    ADD COLUMN IF NOT EXISTS last_purge_message      VARCHAR(500);
