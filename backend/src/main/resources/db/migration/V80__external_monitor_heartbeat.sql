-- MON-BKP-LOG-1: External monitor heartbeat tracking on the monitoring settings row.
-- The external monitor is a standalone process; it POSTs a heartbeat so the UI can show liveness.

ALTER TABLE system_monitoring_settings
    ADD COLUMN IF NOT EXISTS last_external_heartbeat_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_external_heartbeat_source VARCHAR(120),
    ADD COLUMN IF NOT EXISTS last_external_heartbeat_status VARCHAR(30);
