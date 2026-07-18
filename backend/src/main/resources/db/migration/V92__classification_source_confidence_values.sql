DO $$
DECLARE c RECORD;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint
           WHERE conrelid = 'price_list_import_lines'::regclass
             AND pg_get_constraintdef(oid) LIKE '%classification_source%'
  LOOP
    EXECUTE format('ALTER TABLE price_list_import_lines DROP CONSTRAINT IF EXISTS %I', c.conname);
  END LOOP;
END $$;
ALTER TABLE price_list_import_lines ADD CONSTRAINT price_list_import_lines_classification_source_check
CHECK (classification_source IS NULL OR classification_source IN (
  'HINT','REFERENCE','KNOWLEDGE_BASE','RULE','DEFAULT','WAAD_RULE','LAB_EXACT',
  'APPROVED_ALIAS','OFFICIAL_KNOWLEDGE','PROVIDER_HISTORY','FACILITY_DATASET',
  'ODOO_DATASET','NONE'
));
