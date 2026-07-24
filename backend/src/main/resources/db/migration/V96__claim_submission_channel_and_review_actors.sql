-- PROVIDER-PORTAL-REVIEW-ROUTING-2: distinguish provider-portal claims from
-- manually-entered (batch) claims, and record who submitted/reviewed a claim.
-- All columns additive and nullable — no existing data or behavior affected.

ALTER TABLE claims ADD COLUMN submission_channel VARCHAR(30);
COMMENT ON COLUMN claims.submission_channel IS 'PROVIDER_PORTAL or MANUAL_ENTRY. NULL for claims created before this column existed.';

ALTER TABLE claims ADD COLUMN submitted_by VARCHAR(255);
COMMENT ON COLUMN claims.submitted_by IS 'Username of the user who called POST /claims/{id}/submit (DRAFT/NEEDS_CORRECTION -> SUBMITTED).';

ALTER TABLE claims ADD COLUMN reviewed_by VARCHAR(255);
COMMENT ON COLUMN claims.reviewed_by IS 'Username of the medical reviewer who approved or rejected the claim.';
