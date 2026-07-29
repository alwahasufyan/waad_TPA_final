-- PROVIDER-PRICE-IMPORT-REVIEW-1: flag provider contract pricing items whose
-- medical category could not be resolved at import time, so they can be held
-- for review instead of silently entering claim/financial approval with no
-- coverage rule to match against.

ALTER TABLE provider_contract_pricing_items
    ADD COLUMN requires_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN review_reason VARCHAR(500);

CREATE INDEX idx_pricing_requires_review
    ON provider_contract_pricing_items (requires_review)
    WHERE requires_review = TRUE;

-- Backfill: any existing row with no resolved category is retroactively
-- unresolved classification and must not remain silently usable.
UPDATE provider_contract_pricing_items
SET requires_review = TRUE,
    review_reason = 'لم يتم تحديد تصنيف طبي لهذه الخدمة (بيانات مستوردة قبل تفعيل المراجعة)'
WHERE medical_category_id IS NULL
  AND active = TRUE;
