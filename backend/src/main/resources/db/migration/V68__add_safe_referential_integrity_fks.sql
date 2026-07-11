-- ============================================================================
-- V68: Stage 1 (D14) — add SAFE, additive foreign-key constraints
--
-- Closes referential-integrity gaps on relational columns that previously had
-- no FK. Scope is deliberately conservative:
--   * Only columns verified to be 100% orphan-free at authoring time.
--   * Only references to master data that uses SOFT-delete (providers,
--     employers) — so ON DELETE RESTRICT never bites in normal operation while
--     still preventing accidental hard-deletion of referenced masters.
--   * EXCLUDED on purpose:
--       - pre_authorizations.* / claims.pre_authorization_id  → see CF-4
--         (FK-target mismatch under separate decision; do NOT touch here).
--       - claim_lines.pricing_item_id  → intentional snapshot decoupling
--         (pricing history must survive pricing-item removal).
--       - users.company_id  → vestigial/unused column.
--
-- Pattern: ADD CONSTRAINT ... NOT VALID (cheap, no full-table scan / no long
-- lock), then VALIDATE CONSTRAINT (SHARE UPDATE EXCLUSIVE lock — does NOT block
-- reads or writes). This is the zero-downtime way to add FKs to large tables.
-- Wrapped in guards so the migration is idempotent and safe to re-run.
-- Non-destructive: adds constraints only; no data or column is dropped/changed.
-- ============================================================================

-- 1) visits.provider_id -> providers(id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_visits_provider'
    ) THEN
        ALTER TABLE visits
            ADD CONSTRAINT fk_visits_provider
            FOREIGN KEY (provider_id) REFERENCES providers(id)
            ON DELETE RESTRICT NOT VALID;
        ALTER TABLE visits VALIDATE CONSTRAINT fk_visits_provider;
    END IF;
END $$;

-- 2) payment_records.employer_id -> employers(id)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'payment_records')
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payment_records_employer') THEN
        ALTER TABLE payment_records
            ADD CONSTRAINT fk_payment_records_employer
            FOREIGN KEY (employer_id) REFERENCES employers(id)
            ON DELETE RESTRICT NOT VALID;
        ALTER TABLE payment_records VALIDATE CONSTRAINT fk_payment_records_employer;
    END IF;
END $$;

-- 3) payment_records.provider_id -> providers(id)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'payment_records')
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payment_records_provider') THEN
        ALTER TABLE payment_records
            ADD CONSTRAINT fk_payment_records_provider
            FOREIGN KEY (provider_id) REFERENCES providers(id)
            ON DELETE RESTRICT NOT VALID;
        ALTER TABLE payment_records VALIDATE CONSTRAINT fk_payment_records_provider;
    END IF;
END $$;
