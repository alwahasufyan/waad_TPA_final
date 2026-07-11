-- ============================================================================
-- V72: Medical Classification Engine (MC-2) — review workspace support
--
-- 1) reference_match: the engine's closest reference suggestion, persisted so
--    reviewers see WHAT the engine compared against (and to distinguish
--    "unknown" lines — no match at all — from low-confidence ones).
-- 2) Index on flags for the guard/duplicate queue tabs.
-- Additive + idempotent.
-- ============================================================================

ALTER TABLE price_list_import_lines ADD COLUMN IF NOT EXISTS reference_match VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_pl_lines_flags ON price_list_import_lines(import_id, flags);

-- 3) Fix V70's medical_services.status CHECK to match the actual Java enum
--    MedicalServiceStatus { DRAFT, ACTIVE, ARCHIVED } (V70 guessed
--    INACTIVE/DEPRECATED which the entity can never write, and omitted
--    ARCHIVED which it can).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'medical_services_status_check') THEN
        ALTER TABLE medical_services DROP CONSTRAINT medical_services_status_check;
    END IF;
    ALTER TABLE medical_services
        ADD CONSTRAINT medical_services_status_check
        CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'));
END $$;
