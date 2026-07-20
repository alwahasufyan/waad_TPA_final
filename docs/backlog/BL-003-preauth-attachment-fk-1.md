# Backlog Item BL-003 — PREAUTH-ATTACHMENT-FK-1

**Status:** 📋 Open · Not fixed. **Origin:** Discovered during DOCUMENTS-IDOR-1 live verification.
**Owner:** TBD · **Priority:** Medium-High (blocks real preauth attachment uploads) · **Type:** Engineering / Schema Migration.

---

## Summary

`pre_authorization_attachments.pre_authorization_id` has a foreign key
(`fk_preauth_att_request`) pointing at `preauthorization_requests(id)` — a separate,
apparently legacy table — rather than at `pre_authorizations(id)`, which is the table
the `PreAuthorization` JPA entity (`@Table(name = "pre_authorizations")`) is actually
mapped to.

```sql
-- as found in the local dev database:
FOREIGN KEY (pre_authorization_id) REFERENCES preauthorization_requests(id) ON DELETE CASCADE
```

`preauthorization_requests` was empty in the local dev database at the time of
discovery, while `pre_authorizations` held real rows. This means: as found, uploading
an attachment for any real `PreAuthorization` row would fail the FK constraint unless
a row with the same id coincidentally also exists in `preauthorization_requests`.

## Reproduction

1. Confirm `pre_authorizations` has at least one row (it does, in any environment with
   real preauthorization data).
2. Attempt `INSERT INTO pre_authorization_attachments (pre_authorization_id, ...)
   VALUES (<a real pre_authorizations.id>, ...)`.
3. Fails with `violates foreign key constraint "fk_preauth_att_request" ... Key
   (pre_authorization_id)=(...) is not present in table "preauthorization_requests"`
   — unless a shim row with the same id was separately inserted into
   `preauthorization_requests` (as was done, and then removed, purely to unblock the
   DOCUMENTS-IDOR-1 live check).

## Proposed fix (not implemented here)

Audit the migration history to determine whether `preauthorization_requests` is a
genuinely stale/renamed predecessor of `pre_authorizations` (most likely) or serves
some other current purpose. If stale, add a migration to repoint
`fk_preauth_att_request` at `pre_authorizations(id)` and retire
`preauthorization_requests`, or otherwise reconcile the two tables so the FK matches
the entity that's actually in use.

## Why deferred

Out of scope for DOCUMENTS-IDOR-1 ("do not modify PreAuthorization workflow beyond
attachment authorization"). This is a schema/migration inconsistency, unrelated to the
ownership/IDOR fix, and needs a deliberate migration decision rather than an
opportunistic patch.
