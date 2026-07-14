-- TAX-1R: development replacement mode.
--
-- The data removed here belongs to the experimental classification/import
-- catalogue. Claims, settlements, providers and provider contracts are not
-- deleted. Claim-line financial snapshots are intentionally left untouched.

CREATE TEMP TABLE tax1r_official_category_codes (
    code VARCHAR(50) PRIMARY KEY
);

INSERT INTO tax1r_official_category_codes(code) VALUES
 ('CAT-OPT'),('CAT-ROOM'),('CAT-SURGERY'),('CAT-DRUG'),('CAT-MED-SUP'),
 ('CAT-ICU'),('CAT-CCU'),('CAT-PRACT-FEE'),('CAT-ANESTHESIA'),
 ('CAT-SURG-MAT'),('CAT-DIAGNOSTIC'),('CAT-DAY-CARE'),('CAT-DENT-EMERG'),
 ('CAT-AMBULANCE'),('CAT-HOME-NURSING'),('CAT-PHYSIO'),('CAT-IMG-ADV'),
 ('CAT-LAB'),('CAT-IMG-DIAG'),('CAT-TRANSPLANT'),('CAT-PSYCH-DRUG'),
 ('CAT-PSYCH-SESS'),('CAT-ONCOLOGY'),('CAT-DIALYSIS'),('CAT-MAT-NORMAL'),
 ('CAT-MAT-CS'),('CAT-MAT-COMP'),('CAT-DME'),('CAT-DENT-ROUTINE'),
 ('CAT-DENT-PROSTHO'),('CAT-DENT-ORTHO'),('CAT-DENT-IMPLANT'),
 ('CAT-EYE-EXAM');

-- Governed price-list/import data was produced with the superseded taxonomy.
-- Clear children before parents so RESTRICT foreign keys are respected.
DELETE FROM price_change_audit;
DELETE FROM price_list_correction_requests;
DELETE FROM price_list_validation_findings;
DELETE FROM provider_contract_pricing_items;
DELETE FROM provider_price_list_versions;
DELETE FROM catalog_classification_history;
DELETE FROM price_list_imports; -- price_list_import_lines cascade from imports

-- The service catalogue and all learned aliases are taxonomy-dependent.
DELETE FROM ent_service_aliases;
DELETE FROM medical_services;

-- Remove roots/rules tied to the former hierarchy. Official TAX-1 categories
-- are deliberately flat; coverage contexts live in their dedicated table.
DELETE FROM medical_category_roots;
DELETE FROM benefit_policy_template_rules
WHERE medical_category_id IN (
    SELECT id
    FROM medical_categories
    WHERE code NOT IN (SELECT code FROM tax1r_official_category_codes)
);

-- Break legacy self-parent links, then physically remove every non-official
-- category. This is replacement, not soft-disable mode.
UPDATE medical_categories SET parent_id = NULL WHERE parent_id IS NOT NULL;
DELETE FROM medical_categories
WHERE code NOT IN (SELECT code FROM tax1r_official_category_codes);

-- Normalize the surviving official rows seeded by V80.
UPDATE medical_categories
SET parent_id = NULL,
    active = true,
    deleted = false,
    classification_enabled = true,
    updated_at = CURRENT_TIMESTAMP;

-- BEN codes remain a separate financial-benefit catalogue and are never
-- medical categories.
DELETE FROM special_financial_benefits
WHERE code NOT IN (
    'BEN-WORK-INJURY','BEN-EVACUATION','BEN-COMPANION',
    'BEN-FAMILY-TRAVEL','BEN-RARE'
);

UPDATE special_financial_benefits
SET active = true,
    updated_at = CURRENT_TIMESTAMP
WHERE code IN (
    'BEN-WORK-INJURY','BEN-EVACUATION','BEN-COMPANION',
    'BEN-FAMILY-TRAVEL','BEN-RARE'
);

-- Fail Flyway immediately if replacement invariants are ever violated.
DO $$
DECLARE
    category_count INTEGER;
    invalid_category_count INTEGER;
    benefit_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO category_count FROM medical_categories;
    SELECT COUNT(*) INTO invalid_category_count
    FROM medical_categories
    WHERE code NOT IN (SELECT code FROM tax1r_official_category_codes)
       OR code LIKE 'BEN-%'
       OR code IN ('OUTPATIENT','INPATIENT','EMERGENCY','SPECIAL');
    SELECT COUNT(*) INTO benefit_count
    FROM special_financial_benefits
    WHERE active = true;

    IF category_count <> 33 THEN
        RAISE EXCEPTION 'TAX-1R requires exactly 33 medical categories; found %', category_count;
    END IF;
    IF invalid_category_count <> 0 THEN
        RAISE EXCEPTION 'TAX-1R found % non-official medical categories', invalid_category_count;
    END IF;
    IF benefit_count <> 5 THEN
        RAISE EXCEPTION 'TAX-1R requires exactly 5 active financial benefits; found %', benefit_count;
    END IF;
END $$;
