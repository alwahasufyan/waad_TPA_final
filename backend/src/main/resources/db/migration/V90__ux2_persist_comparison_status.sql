ALTER TABLE price_list_import_lines ADD COLUMN IF NOT EXISTS comparison_status VARCHAR(32);
ALTER TABLE price_list_import_lines ADD COLUMN IF NOT EXISTS comparison_reason VARCHAR(500);
ALTER TABLE price_list_import_previews ADD COLUMN IF NOT EXISTS removed_rows INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_price_list_lines_comparison_status ON price_list_import_lines(import_id, comparison_status);
