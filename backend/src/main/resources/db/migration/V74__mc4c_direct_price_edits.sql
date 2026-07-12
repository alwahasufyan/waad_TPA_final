-- ============================================================================
-- V74: MC-4C Simplification — direct (version-less) audited price edits
--
-- APPROVED BUSINESS RULE (2026-07-12): individual service edits (price
-- correction, add service, deactivate service, classification/code correction)
-- MUST update the ACTIVE price list directly and MUST NOT create a new
-- price-list version. A new version is created ONLY on full import or on
-- restoring an archived version.
--
-- This migration widens price_change_audit into the single audit backbone for
-- all six operation types. Additive + idempotent. No data deleted.
-- ============================================================================

-- 1) Widen operation-type vocabulary (keep legacy values for old rows/flow).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'price_change_audit_change_type_check') THEN
        ALTER TABLE price_change_audit DROP CONSTRAINT price_change_audit_change_type_check;
    END IF;
    ALTER TABLE price_change_audit
        ADD CONSTRAINT price_change_audit_change_type_check
        CHECK (change_type IN (
            -- new MC-4C direct operations
            'PRICE_CORRECTION','ADD_SERVICE','DEACTIVATE_SERVICE','CLASSIFICATION_CORRECTION',
            'VERSION_IMPORT','VERSION_RESTORE',
            -- legacy (pre-simplification exception flow) — retained for history
            'PRICE_EDIT','SERVICE_ADDED','SERVICE_DEACTIVATED'
        ));
END $$;

-- 2) Generic before/after columns for non-price changes (code/category/status).
ALTER TABLE price_change_audit ADD COLUMN IF NOT EXISTS old_value VARCHAR(500);
ALTER TABLE price_change_audit ADD COLUMN IF NOT EXISTS new_value VARCHAR(500);

-- 3) version_id is now optional in spirit (direct edits reference the active
--    version but never create one). Column already nullable — no change needed.

COMMENT ON TABLE price_change_audit IS
    'MC-4C audit backbone: PRICE_CORRECTION | ADD_SERVICE | DEACTIVATE_SERVICE | CLASSIFICATION_CORRECTION | VERSION_IMPORT | VERSION_RESTORE. Direct edits update the active list in place — no new version.';
