-- WAAD-CLAIM-PREAUTH-FK-FIX-1
--
-- claims.pre_authorization_id's foreign key (fk_claim_preauth, added in
-- V19__claims.sql) has always pointed at preauthorization_requests(id) — a
-- legacy table that is otherwise completely unreferenced and unused — instead
-- of pre_authorizations(id), the table the application's own Claim entity has
-- always mapped to (Claim.java: @JoinColumn(name = "pre_authorization_id")
-- on the `preAuthorization` relationship, targeting the PreAuthorization
-- entity / pre_authorizations table). The application layer was never wrong;
-- only this one DB-level constraint was. Discovered while live-verifying the
-- multi-line pre-authorization -> claim conversion flow (WAAD-PREAUTH-MULTI-
-- LINE-1, Phases 1-4) — any claim created with a real pre_authorization_id
-- would fail at insert time with a foreign key violation.
--
-- Safety, verified against the local dev database before writing this
-- migration (dev DB snapshot at time of writing: 32 claims, 0 with a
-- non-null pre_authorization_id; preauthorization_requests: 0 rows;
-- fk_claim_preauth is the only FK anywhere in the schema referencing
-- preauthorization_requests). Because this migration may run against other
-- environments with different data, it does not assume that snapshot — the
-- guard below re-checks for orphans at migration time and aborts loudly
-- (rather than silently corrupting data or leaving claims unreadable) if any
-- existing claims.pre_authorization_id value would not satisfy the corrected
-- constraint.
DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM claims c
    WHERE c.pre_authorization_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM pre_authorizations pa WHERE pa.id = c.pre_authorization_id
      );

    IF orphan_count > 0 THEN
        RAISE EXCEPTION
            'V113 aborted: % claim(s) reference a pre_authorization_id with no matching row in pre_authorizations. '
            'These must be reconciled manually before this FK correction can be applied safely.',
            orphan_count;
    END IF;
END $$;

-- ON DELETE RESTRICT is preserved unchanged from V19 (a pre-authorization
-- that has been converted into a claim must not be deletable) — only the
-- referenced table changes.
ALTER TABLE claims DROP CONSTRAINT IF EXISTS fk_claim_preauth;

ALTER TABLE claims
    ADD CONSTRAINT fk_claim_preauth
    FOREIGN KEY (pre_authorization_id) REFERENCES pre_authorizations(id) ON DELETE RESTRICT;
