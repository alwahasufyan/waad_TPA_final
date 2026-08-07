-- WAAD-PREAUTH-SINGLE-CONVERSION-GUARD-1
--
-- Defense-in-depth backstop for the pessimistic-lock fix in
-- ClaimService.createClaim(): even with the application-level lock, this
-- guarantees at the database level that at most one ACTIVE claim can ever
-- reference the same pre_authorization_id.
--
-- Scoped to active = true (a partial index), not an unconditional unique
-- constraint, so it stays compatible with the existing soft-delete/restore
-- lifecycle: deleteClaim() only ever flips active to false (the row, and
-- its pre_authorization_id, is retained for audit/restore — see
-- ClaimService.deleteClaim/restoreClaim), so a soft-deleted claim's row
-- must not permanently "occupy" its pre_authorization_id slot forever, and
-- restoreClaim() flips the SAME row's active back to true (not a new row),
-- which never conflicts with itself. hardDeleteClaim() removes the row
-- entirely, which also naturally frees the slot either way.
--
-- Verified against the current dataset before adding this constraint: zero
-- pre_authorization_id values are shared by more than one active claim.
CREATE UNIQUE INDEX IF NOT EXISTS uq_claims_active_pre_authorization
    ON claims (pre_authorization_id)
    WHERE pre_authorization_id IS NOT NULL AND active = true;
