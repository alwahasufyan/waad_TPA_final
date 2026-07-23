-- CLAIMS-FINANCIAL-INTEGRITY-2
-- Persist the provider-contract-discount split as its own explicit fields on
-- claim_lines, so a discount can never be folded into (or confused with) the
-- refused/final-payable amounts.
--
-- company_share_before_discount: coveragePercent share of requestedTotal,
--   BEFORE the provider contract discount is applied.
-- provider_discount_amount: the discount amount actually applied to this
--   line's company share (0.00 when the provider has no active/enabled
--   contract discount).
--
-- Both nullable/additive — existing rows are unaffected; only newly
-- calculated/recalculated lines populate them.

ALTER TABLE claim_lines
    ADD COLUMN company_share_before_discount NUMERIC(15, 2),
    ADD COLUMN provider_discount_amount NUMERIC(15, 2) DEFAULT 0.00;
