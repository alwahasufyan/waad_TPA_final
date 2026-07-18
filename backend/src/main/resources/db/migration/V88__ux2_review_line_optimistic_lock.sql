-- MED-DICT-UX-2B: non-destructive optimistic locking for staged review rows.
ALTER TABLE price_list_import_lines
    ADD COLUMN IF NOT EXISTS row_version BIGINT;

UPDATE price_list_import_lines
SET row_version = 0
WHERE row_version IS NULL;

ALTER TABLE price_list_import_lines
    ALTER COLUMN row_version SET DEFAULT 0;

ALTER TABLE price_list_import_lines
    ALTER COLUMN row_version SET NOT NULL;
