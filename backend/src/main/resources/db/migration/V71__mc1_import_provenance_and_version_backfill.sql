-- ============================================================================
-- V71: Medical Classification Engine (MC-1) — provenance + Version-1 backfill
--
-- Owner conditions for MC-1 (binding):
--   1) Upload idempotency by file hash  → unique guard per provider+hash for
--      non-terminal imports (partial unique index below).
--   2) Full provenance on every import  → file size, engine/dictionary
--      versions, execution time (columns below; values written by the
--      import job from the engine's result).
--
-- Also: M1 backfill — every contract that already has pricing items gets
-- PriceListVersion #1 (ACTIVE) and its items are tagged, so versioning
-- governance applies from the very first module-published version.
-- Additive + idempotent; no rows deleted or values overwritten.
-- ============================================================================

-- 1) Provenance columns on price_list_imports
ALTER TABLE price_list_imports ADD COLUMN IF NOT EXISTS file_size_bytes    BIGINT;
ALTER TABLE price_list_imports ADD COLUMN IF NOT EXISTS fuzz_engine        VARCHAR(20);
ALTER TABLE price_list_imports ADD COLUMN IF NOT EXISTS dictionary_version VARCHAR(1000);
ALTER TABLE price_list_imports ADD COLUMN IF NOT EXISTS execution_ms       BIGINT;

COMMENT ON COLUMN price_list_imports.dictionary_version IS
    'Engine knowledge provenance: hashes/counts of synonyms, Odoo KB, reference and categories files at run time (JSON)';

-- 2) Idempotency guard: one non-terminal import per (provider, file_hash).
--    FAILED / CANCELLED imports do not block a re-upload of the same file.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pl_import_provider_hash_active
    ON price_list_imports(provider_id, file_hash)
    WHERE file_hash IS NOT NULL AND status NOT IN ('FAILED','CANCELLED');

-- 3) Version-1 backfill for contracts that already carry pricing items
INSERT INTO provider_price_list_versions
        (provider_id, contract_id, version_no, status, effective_from, notes,
         published_at, created_at, updated_at)
SELECT  pc.provider_id, pc.id, 1, 'ACTIVE', pc.start_date,
        'backfill (MC-1): pre-existing pricing items adopted as Version 1',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM    provider_contracts pc
WHERE   EXISTS (SELECT 1 FROM provider_contract_pricing_items pi
                WHERE pi.contract_id = pc.id)
  AND   NOT EXISTS (SELECT 1 FROM provider_price_list_versions v
                    WHERE v.contract_id = pc.id);

UPDATE  provider_contract_pricing_items pi
SET     version_id = v.id
FROM    provider_price_list_versions v
WHERE   v.contract_id = pi.contract_id
  AND   v.version_no  = 1
  AND   pi.version_id IS NULL;
