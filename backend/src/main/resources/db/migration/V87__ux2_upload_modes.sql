-- MED-DICT-UX-2: explicit append/new-version upload intent.
-- Non-destructive: historical imports default to append-only behavior.
ALTER TABLE price_list_imports
    ADD COLUMN IF NOT EXISTS upload_mode VARCHAR(30);

UPDATE price_list_imports
SET upload_mode = 'APPEND_NEW_SERVICES'
WHERE upload_mode IS NULL;

ALTER TABLE price_list_imports
    ALTER COLUMN upload_mode SET DEFAULT 'APPEND_NEW_SERVICES';

ALTER TABLE price_list_imports
    ALTER COLUMN upload_mode SET NOT NULL;

ALTER TABLE price_list_imports
    ADD CONSTRAINT ck_price_list_import_upload_mode
    CHECK (upload_mode IN ('APPEND_NEW_SERVICES', 'NEW_VERSION'));
