-- HYB-0B: Flexible audited operational pricing/classification policy.
-- Additive only: captures complete evidence and rejects stale concurrent writes.

ALTER TABLE provider_contract_pricing_items
    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE price_change_audit
    ADD COLUMN IF NOT EXISTS provider_id BIGINT,
    ADD COLUMN IF NOT EXISTS before_state TEXT,
    ADD COLUMN IF NOT EXISTS after_state TEXT;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'price_change_audit_change_type_check') THEN
        ALTER TABLE price_change_audit DROP CONSTRAINT price_change_audit_change_type_check;
    END IF;
    ALTER TABLE price_change_audit
        ADD CONSTRAINT price_change_audit_change_type_check
        CHECK (change_type IN (
            'PRICE_CORRECTION','ADD_SERVICE','DEACTIVATE_SERVICE','REACTIVATE_SERVICE','CLASSIFICATION_CORRECTION',
            'VERSION_IMPORT','VERSION_RESTORE','PRICE_EDIT','SERVICE_ADDED','SERVICE_DEACTIVATED'
        ));
END $$;

CREATE INDEX IF NOT EXISTS idx_pca_provider ON price_change_audit(provider_id);
