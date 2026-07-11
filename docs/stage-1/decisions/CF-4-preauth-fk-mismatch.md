# Decision Required — CF-4: Claim → Pre-Authorization Foreign-Key Target Mismatch

**Status:** 🟡 **DEFERRED by business decision (Stage 1 close-out).** No implementation. Not scheduled for Stage 2. The FK-target mismatch remains documented and known; revisit with an approved, data-verified migration plan when prioritized. No code/schema changed.
**Severity:** 🔴 Critical (potential latent data-linkage bug + production-data migration risk).
**Constitution anchors:** *"Protect production data. Prefer additive migrations. Avoid destructive changes. Never modify code you do not understand."*

---

## Current Implementation (confirmed by direct inspection)

- The operational entity `modules/preauthorization/entity/PreAuthorization` maps to table **`pre_authorizations`** (`@Table(name = "pre_authorizations")`).
- The claims schema (`V19__claims.sql:76`) defines the foreign key:
  ```sql
  CONSTRAINT fk_claim_preauth FOREIGN KEY (pre_authorization_id)
      REFERENCES preauthorization_requests(id) ON DELETE RESTRICT
  ```
  i.e. `claims.pre_authorization_id` references a **different** table, **`preauthorization_requests`**, not `pre_authorizations`.
- Both tables exist in the schema. In-code comments elsewhere describe `pre_authorizations` as "the real working preauth table."

So the JPA relationship (`Claim.preAuthorization` → `PreAuthorization` → table `pre_authorizations`) and the database FK constraint (`pre_authorization_id` → `preauthorization_requests`) point at **two different tables**.

## Runtime Behavior

- **Not yet fully traced** (and, per your instruction, not investigated beyond documentation): exactly how `Claim.preAuthorization` is mapped and populated, and what values currently live in `claims.pre_authorization_id` in production.
- Two behavioral possibilities, both concerning:
  - If the two tables share an ID space (e.g. one is a view/legacy mirror of the other), the mismatch may be currently "working by accident" but is fragile.
  - If they have independent ID spaces, then either claim inserts that set `pre_authorization_id` would fail the FK, or the JPA-loaded `preAuthorization` resolves to the wrong/null record — silently corrupting the claim↔pre-auth traceability that the "no standalone claim" architectural law depends on.
- The prevalence of claims with a non-null `pre_authorization_id` in production determines the real-world blast radius (unknown pending investigation).

## Risk

- **Latent linkage corruption:** claims may reference the wrong (or no) pre-authorization record.
- **Migration risk of the fix itself:** correcting an FK target touches a live column on the financial `claims` table; done carelessly it could fail on existing data or break inserts.
- **CF-3 interaction:** pre-authorization requirement is sourced from benefit-policy rules (see CF-3); the two should be reasoned about together.

## Business Impact

- **Medical/authorization integrity:** pre-authorization status validation may be checking the wrong record.
- **Audit/traceability:** the claim→pre-auth chain (a core compliance guarantee) may be unreliable.
- **Financial:** indirect — pre-auth reservations relate to member limit consumption.

## Available Options

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A. Point the FK at `pre_authorizations`** (the operational table the entity uses) | Aligns DB constraint with the JPA mapping | Fixes the mismatch at its source | Requires verifying/migrating existing `pre_authorization_id` values to valid `pre_authorizations.id`; production-data migration with careful validation + rollback plan |
| **B. Consolidate the two pre-auth tables** (if `preauthorization_requests` is legacy) | Removes the root ambiguity | Cleanest long-term | Larger scope; must confirm neither table is independently in use; potentially destructive → needs explicit approval |
| **C. Document as intentional** (if investigation proves the two tables are deliberately linked and consistent) | No change | Lowest risk if truly a non-issue | Only valid if data proves the linkage is correct and stable |

## Recommended Option

A **scoped, read-only investigation first**, then most likely **Option A** with a carefully-staged, reversible migration:
1. (On approval) Confirm how `Claim.preAuthorization` is mapped and count production claims with non-null `pre_authorization_id`, and whether those IDs resolve in `pre_authorizations` vs `preauthorization_requests`.
2. Author an additive, validated, reversible migration to correct the FK target (backfill/verify data first; add the corrected constraint with `NOT VALID` + `VALIDATE` after data is confirmed clean).
3. Full regression on the pre-auth → claim journey before/after.

## Why This Requires Business Approval

- Any fix **alters a foreign key on the live `claims` table** — the Constitution explicitly requires protecting production data and forbids destructive/breaking migrations without explicit approval.
- The correct resolution depends on facts about **production data and business intent** (are both tables in use? which is authoritative?) that only the business/operators can confirm.
- Getting this wrong could break claim submission or corrupt clinical linkage — far too high-stakes for an unapproved Stage-1 edit. This is precisely why it was **not** touched during Stage 1 and is surfaced here instead.
