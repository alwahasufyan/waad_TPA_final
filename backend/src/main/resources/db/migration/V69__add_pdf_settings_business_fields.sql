-- V69: Add business_type and tax_number to pdf_company_settings
-- Required for enterprise report header/footer

ALTER TABLE pdf_company_settings
  ADD COLUMN IF NOT EXISTS business_type VARCHAR(255),
  ADD COLUMN IF NOT EXISTS tax_number    VARCHAR(100);
