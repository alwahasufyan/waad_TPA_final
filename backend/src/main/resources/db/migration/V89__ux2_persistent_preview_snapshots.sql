CREATE TABLE IF NOT EXISTS price_list_import_previews (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL UNIQUE REFERENCES price_list_imports(id) ON DELETE CASCADE,
    provider_id BIGINT NOT NULL,
    contract_id BIGINT,
    upload_mode VARCHAR(30) NOT NULL DEFAULT 'APPEND_NEW_SERVICES',
    file_hash VARCHAR(64) NOT NULL,
    row_digest VARCHAR(64) NOT NULL,
    source_active_version_id BIGINT,
    target_draft_version_id BIGINT,
    total_rows INTEGER NOT NULL DEFAULT 0,
    blocking_rows INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_by VARCHAR(100),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_price_list_preview_status CHECK (status IN ('ACTIVE','CONSUMED','EXPIRED','INVALIDATED')),
    CONSTRAINT ck_price_list_preview_mode CHECK (upload_mode IN ('APPEND_NEW_SERVICES','NEW_VERSION'))
);
CREATE INDEX IF NOT EXISTS idx_price_list_preview_import ON price_list_import_previews(import_id);
