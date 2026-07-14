-- DB-FIX-2: reconcile entity <-> schema drift so a fresh DB + Flyway + ddl-auto=validate
-- boots cleanly (prod uses validate). Data-preserving: safe on both empty and populated DBs.
--
-- Two live features whose entities had drifted from their migration-created tables:
--   1) password_reset_tokens: entity added `email` and `otp` (email/OTP reset flow).
--   2) pre_authorization_attachments: entity was restructured (renamed/added columns);
--      the legacy NOT NULL columns it no longer maps would otherwise break inserts.

-- ===================== 1) password_reset_tokens =====================
ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS otp   VARCHAR(255);

-- ===================== 2) pre_authorization_attachments =====================
-- Add the columns the current entity maps.
ALTER TABLE pre_authorization_attachments
    ADD COLUMN IF NOT EXISTS pre_authorization_id BIGINT,
    ADD COLUMN IF NOT EXISTS original_file_name   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stored_file_name     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS created_at           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_by           VARCHAR(100);

-- Backfill from the legacy columns (no-op on an empty fresh DB; preserves data otherwise).
UPDATE pre_authorization_attachments SET
    pre_authorization_id = COALESCE(pre_authorization_id, preauthorization_request_id),
    original_file_name   = COALESCE(original_file_name, file_name),
    stored_file_name     = COALESCE(stored_file_name, file_name),
    created_at           = COALESCE(created_at, uploaded_at, CURRENT_TIMESTAMP),
    created_by           = COALESCE(created_by, uploaded_by);

-- Enforce the constraints the entity declares (all rows are now populated).
ALTER TABLE pre_authorization_attachments
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE pre_authorization_attachments
    ALTER COLUMN pre_authorization_id SET NOT NULL,
    ALTER COLUMN original_file_name   SET NOT NULL,
    ALTER COLUMN stored_file_name     SET NOT NULL,
    ALTER COLUMN created_at           SET NOT NULL;

-- Drop the legacy columns the entity no longer maps (their NOT NULLs would break inserts).
-- Dropping preauthorization_request_id also drops its FK constraint automatically.
ALTER TABLE pre_authorization_attachments
    DROP COLUMN IF EXISTS preauthorization_request_id,
    DROP COLUMN IF EXISTS file_name,
    DROP COLUMN IF EXISTS uploaded_by,
    DROP COLUMN IF EXISTS uploaded_at;

-- Restore referential integrity on the new FK column.
ALTER TABLE pre_authorization_attachments
    ADD CONSTRAINT fk_preauth_att_request
    FOREIGN KEY (pre_authorization_id) REFERENCES preauthorization_requests(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_preauth_att_request ON pre_authorization_attachments(pre_authorization_id);

-- ===================== 3) pre_authorizations.request_date =====================
-- Entity maps request_date as LocalDate (SQL DATE); the table stored it as TIMESTAMP,
-- which is a genuine JDBC-type mismatch that fails ddl-auto=validate. Convert to date.
-- (text<->varchar differences elsewhere are both JDBC Types.VARCHAR and do not fail validate.)
ALTER TABLE pre_authorizations
    ALTER COLUMN request_date TYPE date USING request_date::date;
