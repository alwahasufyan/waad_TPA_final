ALTER TABLE price_list_import_lines ADD COLUMN IF NOT EXISTS decision_level VARCHAR(20);
ALTER TABLE price_list_import_lines ADD COLUMN IF NOT EXISTS evidence_json TEXT;
ALTER TABLE price_list_import_lines ADD COLUMN IF NOT EXISTS confidence_reason VARCHAR(1000);
CREATE INDEX IF NOT EXISTS idx_price_list_lines_decision_level ON price_list_import_lines(import_id, decision_level);
