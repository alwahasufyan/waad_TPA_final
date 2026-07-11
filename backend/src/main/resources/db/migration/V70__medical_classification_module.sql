-- ============================================================================
-- V70: Medical Classification Engine (MC-0) — schema foundation
--
-- Plan: docs/plans/provider-price-list-classification-module-plan.md (v1.2)
-- Scope (additive only, idempotent, no data touched):
--   0) medical_services + ent_service_aliases — these entities exist in code
--      (MedicalService.java, ServiceAlias.java) but their tables were never
--      created by any migration (verified against dev DB). Created here to
--      match the entities exactly; aliases get the A2 extension columns
--      (source/weight) used by the classification learning loop.
--   1) catalog_classification_history       — mapping/classification audit
--   2) price_list_imports                   — one row per uploaded file
--   3) price_list_import_lines              — staging (script JSON lands here)
--   4) provider_price_list_versions         — immutable version backbone
--   5) price_list_validation_findings       — Financial Validation Engine (A10)
--   6) price_list_correction_requests       — Provider Portal feedback
--   7) classification_settings (+ seeds)    — thresholds / engine config
--   8) provider_contract_pricing_items.version_id — the ONLY existing-table
--      change in the whole module (nullable FK; backfill happens in MC-1)
--
-- NOT touched: claims, claim_lines, benefit_policy_*, Benefit Engine paths
-- (A3), and the Python script folder (A9).
-- ============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- 0a) medical_services — matches MedicalService.java (dormant until now)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS medical_services (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','DEPRECATED')),
    name          VARCHAR(200) NOT NULL,
    category_id   BIGINT,
    specialty_id  BIGINT,
    description   VARCHAR(500),
    base_price    NUMERIC(10,2),
    requires_pa   BOOLEAN      NOT NULL DEFAULT true,
    name_ar       VARCHAR(255),
    name_en       VARCHAR(255),
    cost          NUMERIC(15,2),
    is_master     BOOLEAN      NOT NULL DEFAULT false,
    deleted       BOOLEAN      NOT NULL DEFAULT false,
    deleted_at    TIMESTAMP,
    deleted_by    BIGINT,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_medical_services_code UNIQUE (code),
    CONSTRAINT fk_medical_services_category FOREIGN KEY (category_id)
        REFERENCES medical_categories(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_medical_services_category ON medical_services(category_id);
CREATE INDEX IF NOT EXISTS idx_medical_services_status   ON medical_services(status);
CREATE INDEX IF NOT EXISTS idx_medical_services_name     ON medical_services(name);

-- ────────────────────────────────────────────────────────────────────────────
-- 0b) ent_service_aliases — matches ServiceAlias.java (+ A2 extensions)
--     Entity uses SEQUENCE generator "ent_service_alias_seq" allocationSize 50.
-- ────────────────────────────────────────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS ent_service_alias_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS ent_service_aliases (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('ent_service_alias_seq'),
    medical_service_id  BIGINT       NOT NULL,
    alias_text          VARCHAR(255) NOT NULL,
    locale              VARCHAR(10)  NOT NULL DEFAULT 'ar',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(255),
    -- A2 extension columns (learning loop provenance) — nullable on purpose;
    -- the ServiceAlias entity does not map them yet (added in MC-6).
    source              VARCHAR(30),
    weight              NUMERIC(5,2),

    CONSTRAINT fk_service_alias_service FOREIGN KEY (medical_service_id)
        REFERENCES medical_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_service_aliases_service ON ent_service_aliases(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_service_aliases_text    ON ent_service_aliases(alias_text);

-- ────────────────────────────────────────────────────────────────────────────
-- 1) catalog_classification_history — every mapping/classification decision
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS catalog_classification_history (
    id                      BIGSERIAL PRIMARY KEY,
    medical_service_id      BIGINT      NOT NULL,
    category_id_old         BIGINT,
    category_id_new         BIGINT,
    change_source           VARCHAR(20) NOT NULL
                                CHECK (change_source IN ('IMPORT_REVIEW','ADMIN','MIGRATION')),
    import_line_id          BIGINT,
    confidence_at_decision  NUMERIC(5,1),
    changed_by              VARCHAR(100),
    created_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_class_history_service FOREIGN KEY (medical_service_id)
        REFERENCES medical_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_class_history_service ON catalog_classification_history(medical_service_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 2) price_list_imports — aggregate root of one uploaded provider file
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS price_list_imports (
    id                  BIGSERIAL PRIMARY KEY,
    provider_id         BIGINT       NOT NULL,
    contract_id         BIGINT,
    channel             VARCHAR(20)  NOT NULL DEFAULT 'PRICE_LIST',
    file_name           VARCHAR(500) NOT NULL,
    file_hash           VARCHAR(64),
    file_storage_path   VARCHAR(1000),
    provider_type_hint  VARCHAR(20),
    status              VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED'
                            CHECK (status IN ('UPLOADED','PROCESSING','CLASSIFIED',
                                              'IN_REVIEW','REVIEW_COMPLETE','PUBLISHED',
                                              'FAILED','CANCELLED')),
    engine_version      VARCHAR(50),
    threshold_config    TEXT,
    total_lines         INTEGER      NOT NULL DEFAULT 0,
    known_services      INTEGER      NOT NULL DEFAULT 0,
    unknown_services    INTEGER      NOT NULL DEFAULT 0,
    low_confidence      INTEGER      NOT NULL DEFAULT 0,
    duplicates          INTEGER      NOT NULL DEFAULT 0,
    approved_count      INTEGER      NOT NULL DEFAULT 0,
    rejected_count      INTEGER      NOT NULL DEFAULT 0,
    error_message       TEXT,
    uploaded_by         VARCHAR(100),
    uploaded_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pl_import_provider FOREIGN KEY (provider_id)
        REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pl_import_contract FOREIGN KEY (contract_id)
        REFERENCES provider_contracts(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_pl_imports_provider ON price_list_imports(provider_id);
CREATE INDEX IF NOT EXISTS idx_pl_imports_status   ON price_list_imports(status);

-- ────────────────────────────────────────────────────────────────────────────
-- 3) price_list_import_lines — staging (unknown services live ONLY here, A6)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS price_list_import_lines (
    id                     BIGSERIAL PRIMARY KEY,
    import_id              BIGINT       NOT NULL,
    row_no                 INTEGER,
    source_sheet           VARCHAR(255),
    -- raw payload
    raw_name               VARCHAR(500) NOT NULL,
    raw_name_alt           VARCHAR(500),
    raw_code               VARCHAR(100),
    raw_price              NUMERIC(15,2),
    raw_category_text      VARCHAR(255),
    -- engine result
    normalized_name        VARCHAR(500),
    matched_service_id     BIGINT,
    matched_service_code   VARCHAR(50),
    suggested_main_category VARCHAR(255),
    suggested_category_id  BIGINT,
    suggested_sub_label    VARCHAR(255),
    confidence_score       NUMERIC(5,1),
    match_method           VARCHAR(30),
    classification_source  VARCHAR(20)
                               CHECK (classification_source IS NULL OR classification_source
                                      IN ('HINT','REFERENCE','KNOWLEDGE_BASE','RULE','DEFAULT')),
    engine_reason          VARCHAR(500),
    flags                  VARCHAR(255),
    -- queue state (A4/A5): bands control visibility, never approval
    review_status          VARCHAR(20)  NOT NULL DEFAULT 'NEEDS_REVIEW'
                               CHECK (review_status IN ('PENDING_BULK','NEEDS_REVIEW',
                                                        'APPROVED','REJECTED')),
    -- reviewer decision
    final_service_id       BIGINT,
    final_category_id      BIGINT,
    final_price            NUMERIC(15,2),
    approved_by            VARCHAR(100),
    approved_at            TIMESTAMP,
    approval_mode          VARCHAR(20)
                               CHECK (approval_mode IS NULL OR approval_mode
                                      IN ('INDIVIDUAL','BULK_REMAINING')),
    reviewer_note          VARCHAR(1000),
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pl_line_import FOREIGN KEY (import_id)
        REFERENCES price_list_imports(id) ON DELETE CASCADE,
    CONSTRAINT fk_pl_line_matched_service FOREIGN KEY (matched_service_id)
        REFERENCES medical_services(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pl_line_suggested_category FOREIGN KEY (suggested_category_id)
        REFERENCES medical_categories(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pl_line_final_service FOREIGN KEY (final_service_id)
        REFERENCES medical_services(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pl_line_final_category FOREIGN KEY (final_category_id)
        REFERENCES medical_categories(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_pl_lines_import_status ON price_list_import_lines(import_id, review_status);
CREATE INDEX IF NOT EXISTS idx_pl_lines_matched       ON price_list_import_lines(matched_service_id);

-- Late FK: classification history → staging line (both tables now exist)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_class_history_import_line') THEN
        ALTER TABLE catalog_classification_history
            ADD CONSTRAINT fk_class_history_import_line
            FOREIGN KEY (import_line_id) REFERENCES price_list_import_lines(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- ────────────────────────────────────────────────────────────────────────────
-- 4) provider_price_list_versions — immutable after activation
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS provider_price_list_versions (
    id                BIGSERIAL PRIMARY KEY,
    provider_id       BIGINT      NOT NULL,
    contract_id       BIGINT      NOT NULL,
    version_no        INTEGER     NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                          CHECK (status IN ('DRAFT','ACTIVE','SUPERSEDED','ARCHIVED')),
    source_import_id  BIGINT,
    effective_from    DATE,
    effective_to      DATE,
    approved_by       VARCHAR(100),
    approved_at       TIMESTAMP,
    published_by      VARCHAR(100),
    published_at      TIMESTAMP,
    notes             VARCHAR(2000),
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_pl_version_per_contract UNIQUE (contract_id, version_no),
    CONSTRAINT fk_pl_version_provider FOREIGN KEY (provider_id)
        REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pl_version_contract FOREIGN KEY (contract_id)
        REFERENCES provider_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pl_version_import FOREIGN KEY (source_import_id)
        REFERENCES price_list_imports(id) ON DELETE SET NULL
);

-- At most one ACTIVE version per contract (partial unique index)
CREATE UNIQUE INDEX IF NOT EXISTS uq_pl_version_one_active
    ON provider_price_list_versions(contract_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_pl_versions_contract ON provider_price_list_versions(contract_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 5) price_list_validation_findings — Financial Validation Engine (A10)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS price_list_validation_findings (
    id               BIGSERIAL PRIMARY KEY,
    version_id       BIGINT,
    import_id        BIGINT,
    line_ref         BIGINT,
    line_ref_type    VARCHAR(20)
                         CHECK (line_ref_type IS NULL OR line_ref_type
                                IN ('IMPORT_LINE','PRICING_ITEM')),
    finding_type     VARCHAR(40) NOT NULL
                         CHECK (finding_type IN ('PRICE_SPIKE_VS_PREVIOUS','PRICE_DROP_VS_PREVIOUS',
                                                 'OUTLIER_VS_CATALOG_COST','OUTLIER_VS_CATEGORY_NORM',
                                                 'ZERO_OR_NEGATIVE_PRICE','SUSPICIOUS_ROUNDING',
                                                 'TOTAL_VALUE_SWING','DUPLICATE_PRICE_CONFLICT')),
    severity         VARCHAR(10) NOT NULL
                         CHECK (severity IN ('BLOCKER','WARNING','INFO')),
    old_price        NUMERIC(15,2),
    new_price        NUMERIC(15,2),
    change_percent   NUMERIC(8,2),
    reference_value  NUMERIC(15,2),
    message          VARCHAR(1000),
    status           VARCHAR(10) NOT NULL DEFAULT 'OPEN'
                         CHECK (status IN ('OPEN','RESOLVED','WAIVED')),
    resolved_by      VARCHAR(100),
    resolved_at      TIMESTAMP,
    waiver_note      VARCHAR(1000),
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pl_finding_version FOREIGN KEY (version_id)
        REFERENCES provider_price_list_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_pl_finding_import FOREIGN KEY (import_id)
        REFERENCES price_list_imports(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pl_findings_version ON price_list_validation_findings(version_id, status);
CREATE INDEX IF NOT EXISTS idx_pl_findings_import  ON price_list_validation_findings(import_id, status);

-- ────────────────────────────────────────────────────────────────────────────
-- 6) price_list_correction_requests — Provider Portal feedback (read-only portal)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS price_list_correction_requests (
    id               BIGSERIAL PRIMARY KEY,
    provider_id      BIGINT       NOT NULL,
    pricing_item_id  BIGINT,
    request_type     VARCHAR(30)  NOT NULL
                         CHECK (request_type IN ('WRONG_PRICE','WRONG_NAME',
                                                 'MISSING_SERVICE','OTHER')),
    message          VARCHAR(2000),
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                         CHECK (status IN ('OPEN','IN_REVIEW','RESOLVED','REJECTED')),
    resolved_by      VARCHAR(100),
    resolution_note  VARCHAR(2000),
    resolved_at      TIMESTAMP,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pl_correction_provider FOREIGN KEY (provider_id)
        REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pl_correction_pricing_item FOREIGN KEY (pricing_item_id)
        REFERENCES provider_contract_pricing_items(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_pl_corrections_provider ON price_list_correction_requests(provider_id, status);

-- ────────────────────────────────────────────────────────────────────────────
-- 7) classification_settings — key/value config (thresholds, engine)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS classification_settings (
    id             BIGSERIAL PRIMARY KEY,
    setting_key    VARCHAR(100)  NOT NULL,
    setting_value  VARCHAR(1000) NOT NULL,
    description    VARCHAR(500),
    updated_by     VARCHAR(100),
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_classification_setting_key UNIQUE (setting_key)
);

INSERT INTO classification_settings (setting_key, setting_value, description) VALUES
    ('engine.python.path',                 '',    'Absolute path to the Python interpreter (venv). Empty = python on PATH'),
    ('engine.script.dir',                  '',    'Absolute path to the classification script folder. Empty = configured per environment'),
    ('engine.timeout.seconds',             '600', 'Max seconds for one classification run before the process is killed'),
    ('queue.high_confidence.min_score',    '85',  'Score >= this and no flags => PENDING_BULK (hidden; Approve Remaining). Below => NEEDS_REVIEW'),
    ('review.auto_approval.enabled',       'false', 'HARD-DISABLED in Phase 1 (A4). Reserved for a future, separate decision'),
    ('validation.price_spike.warn_percent',  '30',  'A10: price change vs previous version beyond +/- this % => WARNING'),
    ('validation.price_spike.block_percent', '100', 'A10: price change vs previous version beyond +/- this % => BLOCKER'),
    ('validation.outlier.catalog_cost_factor','5',  'A10: price > factor x catalog cost => WARNING'),
    ('validation.total_swing.warn_percent',  '25',  'A10: version total value swing vs previous version beyond this % => WARNING')
ON CONFLICT (setting_key) DO NOTHING;

-- ────────────────────────────────────────────────────────────────────────────
-- 8) provider_contract_pricing_items.version_id — the only existing-table change
--    Nullable now; MC-1 backfills every contract's items as Version 1.
-- ────────────────────────────────────────────────────────────────────────────
ALTER TABLE provider_contract_pricing_items
    ADD COLUMN IF NOT EXISTS version_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pricing_item_version') THEN
        ALTER TABLE provider_contract_pricing_items
            ADD CONSTRAINT fk_pricing_item_version
            FOREIGN KEY (version_id) REFERENCES provider_price_list_versions(id)
            ON DELETE RESTRICT NOT VALID;
        ALTER TABLE provider_contract_pricing_items VALIDATE CONSTRAINT fk_pricing_item_version;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pricing_version_id ON provider_contract_pricing_items(version_id);
