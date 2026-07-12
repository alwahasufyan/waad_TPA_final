-- ============================================================================
-- V75: MC-6 Lite — WAAD approved service dictionary / learning-loop closure
--
-- ent_service_aliases.source/weight already exist (V70) but were never mapped
-- onto the entity ("added in MC-6" per that migration's own comment). This
-- migration adds the one missing piece — a soft-disable flag — so a bad/typo
-- alias can be deactivated without deleting audit history, and backfills
-- `source` for the aliases already written by the MC-2 learning loop.
--
-- Additive + idempotent. No existing catalog data is deleted or overwritten.
-- ============================================================================

ALTER TABLE ent_service_aliases ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX IF NOT EXISTS idx_service_aliases_active ON ent_service_aliases(active);

-- Backfill provenance for aliases written before this column existed on the
-- entity: created_by carries "<user> (REVIEWER_DECISION)" from CatalogKnowledgeService.
UPDATE ent_service_aliases
SET source = 'REVIEWER_DECISION'
WHERE source IS NULL AND created_by LIKE '%REVIEWER_DECISION%';

COMMENT ON COLUMN ent_service_aliases.source IS
    'Provenance: REVIEWER_DECISION (MC-2 review approval) | ADD_SERVICE (MC-4C add-service linked to a catalog service) | MANUAL (admin-entered via the knowledge endpoints) | ODOO_MIGRATION | SYNONYM_FILE';
COMMENT ON COLUMN ent_service_aliases.active IS
    'Soft-disable: false = alias no longer used for auto-matching, kept for audit.';
