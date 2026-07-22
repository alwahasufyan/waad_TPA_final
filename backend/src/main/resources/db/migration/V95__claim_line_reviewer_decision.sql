-- V95: CLAIM-REVIEW-SPLIT-2C — persist reviewer line-level decisions
--
-- ClaimLine already has `rejected` (boolean), `rejection_reason`,
-- `rejection_reason_code`, `reviewer_notes` — but no way to distinguish "not
-- yet decided" from "approved" (both currently look like rejected=false), and
-- no tri-state to represent "clarification required" (which is neither an
-- approval nor a rejection). This migration adds one small, nullable,
-- additive column to close that gap. No existing column is altered, dropped,
-- or renamed; no existing data is touched (all existing rows get NULL,
-- meaning "no reviewer decision recorded yet", which is correct for every
-- pre-existing claim line).

ALTER TABLE claim_lines
    ADD COLUMN reviewer_decision VARCHAR(30);

COMMENT ON COLUMN claim_lines.reviewer_decision IS
    'CLAIM-REVIEW-SPLIT-2C: reviewer''s line-level decision — APPROVED, REJECTED, or CLARIFICATION_REQUIRED. NULL means no decision recorded yet. Display/audit only — does not participate in claim-level financial calculation at save time.';
