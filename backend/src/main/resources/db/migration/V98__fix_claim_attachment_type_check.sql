-- DOCUMENTS-INTEGRITY-1
--
-- claim_attachments.attachment_type's CHECK constraint (added in V21) never
-- matched the actual ClaimAttachmentType Java enum (and the Provider Portal
-- upload UI, which offers exactly the enum's values). The constraint allowed
-- ('PRESCRIPTION','LAB_RESULT','XRAY','REFERRAL_LETTER','DISCHARGE_SUMMARY','OTHER')
-- while the real enum is
-- (INVOICE, MEDICAL_REPORT, PRESCRIPTION, LAB_RESULT, XRAY, OTHER).
--
-- Practical effect: uploading a claim attachment tagged "فاتورة" (INVOICE) or
-- "تقرير طبي" (MEDICAL_REPORT) — the two most common attachment types in this
-- system — always failed with a DB constraint-violation 400/500, live-confirmed
-- during this fix's smoke test. REFERRAL_LETTER/DISCHARGE_SUMMARY were never
-- reachable from any UI or enum value, so they are dropped from the allowed set.

ALTER TABLE claim_attachments
    DROP CONSTRAINT IF EXISTS claim_attachments_attachment_type_check;

ALTER TABLE claim_attachments
    ADD CONSTRAINT claim_attachments_attachment_type_check
    CHECK (attachment_type IN ('INVOICE', 'MEDICAL_REPORT', 'PRESCRIPTION', 'LAB_RESULT', 'XRAY', 'OTHER'));
