-- TAX-0/TAX-1: official classification taxonomy.
-- Non-destructive: historical categories and their FK references are preserved,
-- but only the 33 rows seeded here are eligible for the classification engine.

ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS classification_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_medical_categories_classification_enabled
    ON medical_categories(classification_enabled)
    WHERE classification_enabled = true AND active = true AND deleted = false;

INSERT INTO medical_categories
    (code, name, name_ar, context, parent_id, active, deleted,
     classification_enabled, created_at, updated_at)
VALUES
    ('CAT-OPT','نظارة طبية','نظارة طبية','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-ROOM','الإيواء غرفة خاصة','الإيواء غرفة خاصة','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-SURGERY','العمليات الجراحية','العمليات الجراحية','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DRUG','الدواء','الدواء','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-MED-SUP','المستلزمات الطبية','المستلزمات الطبية','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-ICU','العناية الفائقة','العناية الفائقة','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-CCU','عناية القلب','عناية القلب','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-PRACT-FEE','رسوم الأطباء والجراحين والمستشارين والممارسين','رسوم الأطباء والجراحين والمستشارين والممارسين','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-ANESTHESIA','نفقات التخدير','نفقات التخدير','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-SURG-MAT','المعدات والمواد الجراحية','المعدات والمواد الجراحية','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DIAGNOSTIC','الكشوف التشخيصية','الكشوف التشخيصية','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DAY-CARE','العلاج والرعاية اليومية','العلاج والرعاية اليومية','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DENT-EMERG','علاج الاسنان بالطوارئ للمريض داخل مستشفى','علاج الاسنان بالطوارئ للمريض داخل مستشفى','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-AMBULANCE','الاسعاف المحلي','الاسعاف المحلي','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-HOME-NURSING','التمريض في المنزل أو النقاهة ( بديل الاقامة بعد الخروج )','التمريض في المنزل أو النقاهة ( بديل الاقامة بعد الخروج )','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-PHYSIO','العلاج الطبيعي','العلاج الطبيعي','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-IMG-ADV','التصوير بالرنين المغناطيسي و المقطعي و الطبقي','التصوير بالرنين المغناطيسي و المقطعي و الطبقي','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-LAB','تحاليل و مختبرات','تحاليل و مختبرات','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-IMG-DIAG','اشعة سينية و اشعة تشخيصية','اشعة سينية و اشعة تشخيصية','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-TRANSPLANT','زرع الاعضاء','زرع الاعضاء','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-PSYCH-DRUG','الطب النفسي ( أدوية )','الطب النفسي ( أدوية )','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-PSYCH-SESS','الطب النفسي ( جلسات )','الطب النفسي ( جلسات )','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-ONCOLOGY','علاج الاورام','علاج الاورام','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DIALYSIS','الغسيل الكلوي','الغسيل الكلوي','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-MAT-NORMAL','الولادة الطبيعية','الولادة الطبيعية','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-MAT-CS','الولادة القيصرية','الولادة القيصرية','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-MAT-COMP','مضاعفات الحمل و الولادة','مضاعفات الحمل و الولادة','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DME','الاجهزه و المعدات الطبية و فق تقرير الطبيب المختص','الاجهزه و المعدات الطبية و فق تقرير الطبيب المختص','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DENT-ROUTINE','علاج الاسنان الروتيني ( كشف- خلع- حشو- تنظيف )','علاج الاسنان الروتيني ( كشف- خلع- حشو- تنظيف )','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DENT-PROSTHO','علاج الاسنان ( تركيب )','علاج الاسنان ( تركيب )','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DENT-ORTHO','علاج الاسنان ( تقويم )','علاج الاسنان ( تقويم )','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-DENT-IMPLANT','علاج الاسنان ( زراعة )','علاج الاسنان ( زراعة )','ANY',NULL,true,false,true,NOW(),NOW()),
    ('CAT-EYE-EXAM','كشوف العيون','كشوف العيون','ANY',NULL,true,false,true,NOW(),NOW())
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    name_ar = EXCLUDED.name_ar,
    parent_id = NULL,
    active = true,
    deleted = false,
    classification_enabled = true,
    updated_at = NOW();

UPDATE medical_categories
SET classification_enabled = false,
    updated_at = NOW()
WHERE code NOT IN (
 'CAT-OPT','CAT-ROOM','CAT-SURGERY','CAT-DRUG','CAT-MED-SUP','CAT-ICU','CAT-CCU',
 'CAT-PRACT-FEE','CAT-ANESTHESIA','CAT-SURG-MAT','CAT-DIAGNOSTIC','CAT-DAY-CARE',
 'CAT-DENT-EMERG','CAT-AMBULANCE','CAT-HOME-NURSING','CAT-PHYSIO','CAT-IMG-ADV',
 'CAT-LAB','CAT-IMG-DIAG','CAT-TRANSPLANT','CAT-PSYCH-DRUG','CAT-PSYCH-SESS',
 'CAT-ONCOLOGY','CAT-DIALYSIS','CAT-MAT-NORMAL','CAT-MAT-CS','CAT-MAT-COMP',
 'CAT-DME','CAT-DENT-ROUTINE','CAT-DENT-PROSTHO','CAT-DENT-ORTHO',
 'CAT-DENT-IMPLANT','CAT-EYE-EXAM'
);

CREATE TABLE IF NOT EXISTS medical_category_allowed_contexts (
    category_id BIGINT NOT NULL,
    context VARCHAR(20) NOT NULL,
    PRIMARY KEY (category_id, context),
    CONSTRAINT fk_medical_category_allowed_context
        FOREIGN KEY (category_id) REFERENCES medical_categories(id) ON DELETE CASCADE,
    CONSTRAINT chk_medical_category_allowed_context
        CHECK (context IN ('OUTPATIENT','INPATIENT','EMERGENCY','SPECIAL'))
);

ALTER TABLE price_list_import_lines
    ADD COLUMN IF NOT EXISTS coverage_context VARCHAR(20);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_price_list_line_coverage_context') THEN
        ALTER TABLE price_list_import_lines ADD CONSTRAINT chk_price_list_line_coverage_context
            CHECK (coverage_context IS NULL OR coverage_context IN
                   ('OUTPATIENT','INPATIENT','EMERGENCY','SPECIAL'));
    END IF;
END $$;

DELETE FROM medical_category_allowed_contexts
WHERE category_id IN (SELECT id FROM medical_categories WHERE classification_enabled = true);

INSERT INTO medical_category_allowed_contexts(category_id, context)
SELECT c.id, v.context
FROM (VALUES
 ('CAT-OPT','OUTPATIENT'),('CAT-ROOM','INPATIENT'),
 ('CAT-SURGERY','OUTPATIENT'),('CAT-SURGERY','INPATIENT'),
 ('CAT-DRUG','OUTPATIENT'),('CAT-DRUG','INPATIENT'),
 ('CAT-MED-SUP','OUTPATIENT'),('CAT-MED-SUP','INPATIENT'),
 ('CAT-ICU','INPATIENT'),('CAT-CCU','INPATIENT'),
 ('CAT-PRACT-FEE','OUTPATIENT'),('CAT-PRACT-FEE','INPATIENT'),
 ('CAT-ANESTHESIA','OUTPATIENT'),('CAT-ANESTHESIA','INPATIENT'),
 ('CAT-SURG-MAT','OUTPATIENT'),('CAT-SURG-MAT','INPATIENT'),
 ('CAT-DIAGNOSTIC','OUTPATIENT'),('CAT-DIAGNOSTIC','INPATIENT'),
 ('CAT-DAY-CARE','INPATIENT'),('CAT-DENT-EMERG','INPATIENT'),
 ('CAT-AMBULANCE','EMERGENCY'),('CAT-HOME-NURSING','SPECIAL'),
 ('CAT-PHYSIO','OUTPATIENT'),('CAT-PHYSIO','INPATIENT'),
 ('CAT-IMG-ADV','OUTPATIENT'),('CAT-IMG-ADV','INPATIENT'),
 ('CAT-LAB','OUTPATIENT'),('CAT-LAB','INPATIENT'),
 ('CAT-IMG-DIAG','OUTPATIENT'),('CAT-IMG-DIAG','INPATIENT'),
 ('CAT-TRANSPLANT','INPATIENT'),
 ('CAT-PSYCH-DRUG','OUTPATIENT'),('CAT-PSYCH-DRUG','INPATIENT'),
 ('CAT-PSYCH-SESS','OUTPATIENT'),('CAT-PSYCH-SESS','INPATIENT'),
 ('CAT-ONCOLOGY','OUTPATIENT'),('CAT-ONCOLOGY','INPATIENT'),
 ('CAT-DIALYSIS','OUTPATIENT'),('CAT-DIALYSIS','INPATIENT'),
 ('CAT-MAT-NORMAL','INPATIENT'),('CAT-MAT-CS','INPATIENT'),
 ('CAT-MAT-COMP','OUTPATIENT'),('CAT-MAT-COMP','INPATIENT'),
 ('CAT-DME','OUTPATIENT'),('CAT-DENT-ROUTINE','OUTPATIENT'),
 ('CAT-DENT-PROSTHO','OUTPATIENT'),('CAT-DENT-ORTHO','OUTPATIENT'),
 ('CAT-DENT-IMPLANT','OUTPATIENT'),('CAT-EYE-EXAM','OUTPATIENT')
) AS v(code, context)
JOIN medical_categories c ON c.code = v.code
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS special_financial_benefits (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    name_ar VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_special_financial_benefit_code CHECK (code LIKE 'BEN-%')
);

INSERT INTO special_financial_benefits(code, name, name_ar, active) VALUES
 ('BEN-WORK-INJURY','تكلفة اصابات العمل','تكلفة اصابات العمل',true),
 ('BEN-EVACUATION','الاخلاء الطبي','الاخلاء الطبي',true),
 ('BEN-COMPANION','تكلفة شخص مرافق واحد للشخص الذي تم اخلاءه','تكلفة شخص مرافق واحد للشخص الذي تم اخلاءه',true),
 ('BEN-FAMILY-TRAVEL','تكلفة السفر لاحد افراد عائلة المؤمن عليه في حالة الاخلاء','تكلفة السفر لاحد افراد عائلة المؤمن عليه في حالة الاخلاء',true),
 ('BEN-RARE','خدمات نادرة','خدمات نادرة',true)
ON CONFLICT (code) DO UPDATE SET
 name = EXCLUDED.name, name_ar = EXCLUDED.name_ar, active = true, updated_at = NOW();

ALTER TABLE ent_service_aliases
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE ent_service_aliases
    ADD COLUMN IF NOT EXISTS quarantine_reason VARCHAR(500);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_service_alias_review_status') THEN
        ALTER TABLE ent_service_aliases ADD CONSTRAINT chk_service_alias_review_status
            CHECK (review_status IN ('APPROVED','NEEDS_REVIEW','QUARANTINED'));
    END IF;
END $$;

UPDATE ent_service_aliases a
SET review_status = 'QUARANTINED',
    quarantine_reason = 'TAX-1: alias points to a legacy/non-official classification'
FROM medical_services s
LEFT JOIN medical_categories c ON c.id = s.category_id
WHERE a.medical_service_id = s.id
  AND COALESCE(c.classification_enabled, false) = false;

CREATE INDEX IF NOT EXISTS idx_service_aliases_auto_match
    ON ent_service_aliases(active, review_status)
    WHERE active = true AND review_status = 'APPROVED';
