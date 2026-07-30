-- PREAUTH-ATTACHMENTS-1
--
-- This branch's migration history never included the schema-reconciliation
-- work that a sibling branch applied as its own V86 (email/otp on
-- password_reset_tokens, request_date TIMESTAMP -> DATE, and the
-- pre_authorization_attachments column rename). The shared local dev
-- database had that reconciliation applied at some point from a different
-- branch's migration run, which is why local dev "worked" — but a genuinely
-- fresh database following only this branch's migrations (V1..V99) would
-- still have the ORIGINAL V17 schema and this migration would fail. Every
-- step below is guarded (IF EXISTS / IF NOT EXISTS / COALESCE) so it is a
-- correct no-op on a database that already has the reconciled shape (like
-- this shared dev DB) and a correct migration on a truly fresh one.

-- ===================== 1) password_reset_tokens =====================
ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS otp   VARCHAR(255);

-- ===================== 2) pre_authorizations.request_date =====================
-- Entity maps request_date as LocalDate (SQL DATE); V17 created it as
-- TIMESTAMP, a JDBC-type mismatch that fails ddl-auto=validate at startup.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pre_authorizations' AND column_name = 'request_date'
          AND data_type <> 'date'
    ) THEN
        ALTER TABLE pre_authorizations
            ALTER COLUMN request_date TYPE date USING request_date::date;
    END IF;
END $$;

-- ===================== 3) pre_authorization_attachments =====================
-- The entity was restructured (renamed/added columns) after V17 created the
-- table with its original, now-legacy column set. Add the columns the
-- current entity maps, backfill from the legacy columns (no-op on an empty
-- fresh DB; preserves data otherwise), enforce NOT NULL, then drop the
-- legacy columns whose own NOT NULLs would otherwise break inserts.
ALTER TABLE pre_authorization_attachments
    ADD COLUMN IF NOT EXISTS pre_authorization_id BIGINT,
    ADD COLUMN IF NOT EXISTS original_file_name   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stored_file_name     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS created_at           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_by           VARCHAR(100);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pre_authorization_attachments' AND column_name = 'preauthorization_request_id'
    ) THEN
        UPDATE pre_authorization_attachments SET
            pre_authorization_id = COALESCE(pre_authorization_id, preauthorization_request_id);
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pre_authorization_attachments' AND column_name = 'file_name'
    ) THEN
        UPDATE pre_authorization_attachments SET
            original_file_name = COALESCE(original_file_name, file_name),
            stored_file_name   = COALESCE(stored_file_name, file_name);
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pre_authorization_attachments' AND column_name = 'uploaded_at'
    ) THEN
        UPDATE pre_authorization_attachments SET
            created_at = COALESCE(created_at, uploaded_at, CURRENT_TIMESTAMP);
    ELSE
        UPDATE pre_authorization_attachments SET
            created_at = COALESCE(created_at, CURRENT_TIMESTAMP);
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pre_authorization_attachments' AND column_name = 'uploaded_by'
    ) THEN
        UPDATE pre_authorization_attachments SET
            created_by = COALESCE(created_by, uploaded_by);
    END IF;
END $$;

ALTER TABLE pre_authorization_attachments
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE pre_authorization_attachments
    ALTER COLUMN pre_authorization_id SET NOT NULL,
    ALTER COLUMN original_file_name   SET NOT NULL,
    ALTER COLUMN stored_file_name     SET NOT NULL,
    ALTER COLUMN created_at           SET NOT NULL;

-- Drop the legacy columns the entity no longer maps (dropping
-- preauthorization_request_id also drops its old FK constraint and index
-- automatically).
ALTER TABLE pre_authorization_attachments
    DROP COLUMN IF EXISTS preauthorization_request_id,
    DROP COLUMN IF EXISTS file_name,
    DROP COLUMN IF EXISTS uploaded_by,
    DROP COLUMN IF EXISTS uploaded_at;

-- Fix the FK direction: it must point at pre_authorizations (the real,
-- core entity), never at the legacy email-inbox table
-- preauthorization_requests. Handles all three states: never-added,
-- added-but-wrong-target (this branch's original mistake), and
-- already-correct (idempotent no-op).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_preauth_att_request'
          AND conrelid = 'pre_authorization_attachments'::regclass
    ) THEN
        ALTER TABLE pre_authorization_attachments
            DROP CONSTRAINT fk_preauth_att_request;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_preauth_att_pre_authorization'
          AND conrelid = 'pre_authorization_attachments'::regclass
    ) THEN
        ALTER TABLE pre_authorization_attachments
            ADD CONSTRAINT fk_preauth_att_pre_authorization
            FOREIGN KEY (pre_authorization_id)
            REFERENCES pre_authorizations(id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_preauth_att_request ON pre_authorization_attachments(pre_authorization_id);

-- ===================== 4) One human-friendly, date-free sequence for all pre-authorizations =====================
CREATE SEQUENCE IF NOT EXISTS pre_authorization_number_seq;

WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at NULLS LAST, id) AS number
    FROM pre_authorizations
)
UPDATE pre_authorizations pa
SET pre_auth_number = 'PA-' || LPAD(ordered.number::text, 6, '0'),
    reference_number = 'PA-' || LPAD(ordered.number::text, 6, '0')
FROM ordered
WHERE pa.id = ordered.id;

SELECT setval(
    'pre_authorization_number_seq',
    GREATEST((SELECT COUNT(*) FROM pre_authorizations), 1),
    true
);
