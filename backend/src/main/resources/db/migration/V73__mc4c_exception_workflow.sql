-- ============================================================================
-- V73: Medical Classification Engine (MC-4C) — Exception Workflow
-- Design: mc-workflow-design-review.md v1.3 (§6-B E1–E5, §0-C F1–F7)
--
-- 1) price_change_audit — the mandatory full audit for in-place exception
--    edits (E2): old/new value, user, timestamp, reason. Safe because claims
--    snapshot their figures (verified: ClaimLine denormalizes all amounts at
--    claim time); this table keeps the pricingItemId pointer's story
--    reconstructible for auditors.
-- 2) provider_price_list_versions.source_type — IMPORT | PATCH | ROLLBACK
--    (visible to auditors in the Version Dashboard only, D2/F7).
-- 3) exception.edit_mode policy seed = ASK (F6).
-- Additive + idempotent.
-- ============================================================================

CREATE TABLE IF NOT EXISTS price_change_audit (
    id               BIGSERIAL PRIMARY KEY,
    contract_id      BIGINT      NOT NULL,
    version_id       BIGINT,
    pricing_item_id  BIGINT,
    change_type      VARCHAR(30) NOT NULL
                         CHECK (change_type IN ('PRICE_EDIT','SERVICE_ADDED','SERVICE_DEACTIVATED')),
    service_code     VARCHAR(50),
    service_name     VARCHAR(255),
    old_price        NUMERIC(15,2),
    new_price        NUMERIC(15,2),
    reason           VARCHAR(1000) NOT NULL,
    changed_by       VARCHAR(100)  NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pca_contract FOREIGN KEY (contract_id)
        REFERENCES provider_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pca_version FOREIGN KEY (version_id)
        REFERENCES provider_price_list_versions(id) ON DELETE SET NULL,
    CONSTRAINT fk_pca_pricing_item FOREIGN KEY (pricing_item_id)
        REFERENCES provider_contract_pricing_items(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_pca_contract ON price_change_audit(contract_id);
CREATE INDEX IF NOT EXISTS idx_pca_version  ON price_change_audit(version_id);

ALTER TABLE provider_price_list_versions
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'IMPORT';

INSERT INTO classification_settings (setting_key, setting_value, description) VALUES
    ('exception.edit_mode', 'ASK',
     'F6: exception handling — EDIT_CURRENT (in place, audited) | NEW_VERSION (always) | ASK (user chooses; default)')
ON CONFLICT (setting_key) DO NOTHING;
