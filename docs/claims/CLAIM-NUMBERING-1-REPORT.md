# CLAIM-NUMBERING-1 — Official Sequential Claim Reference

Branch: `integration/claims-review-complete-local` (local only — not committed, not pushed).

## 1. Existing Numbering Audit

Before this phase, `claims.claim_number` was assigned in exactly one place —
`ClaimService.createClaim` — as the literal string `"CLM-" + savedClaim.getId()`
(e.g. `CLM-251`). This directly exposed the internal database primary key as the
business-facing reference:

- Not stable/meaningful per provider (no per-provider numbering at all).
- Not safe to hand to providers/members as a "claim number" (leaks PK sequence,
  reveals total claim volume across all providers).
- `ClaimRepository.findByClaimNumber(Long)` was misleadingly named — it actually
  queried `WHERE c.id = :id`, not the `claim_number` string column. Left
  unchanged/unrenamed (out of scope, and unknown callers may depend on it) but
  documented in place with a comment.

Frontend audit (`grep` across `frontend/src`) found the same fictional/legacy
patterns duplicated client-side wherever the backend value was unavailable or a
component reconstructed a display string itself. See Section 11 for the full
list of remaining screens.

## 2. Chosen Official Format

```
CLM-P{providerId, 3 digits, zero-padded}-{sequence, 6 digits, zero-padded}
```

Example: `CLM-P001-000001`.

Rationale: `Provider.code` / `Provider.shortCode` do not exist on the entity
(confirmed absent by direct inspection), so the provider's numeric database id
is the only available stable per-provider key. The provider id is not secret in
the way a claim's global insertion order is, and the id is folded into a fixed
zero-padded field so it displays as a normal-looking business code rather than
a raw untyped number.

## 3. Database Changes / Migration

New migration `V94__provider_claim_sequences.sql`:

```sql
CREATE TABLE provider_claim_sequences (
    provider_id BIGINT PRIMARY KEY,
    next_value  BIGINT NOT NULL DEFAULT 1,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- One row per provider; `next_value` is the next sequence number to hand out.
- Backfills every existing claim's `claim_number` deterministically (Section 5).
- Initializes each provider's `next_value` to continue right after the highest
  sequence assigned during backfill.
- `claims.claim_number` itself is untouched structurally — it was already
  nullable + `UNIQUE` since V19/V37. Only the *values* going forward change.

No other tables, columns, or indexes were added or altered. No existing
migration was modified.

## 4. Sequence / Concurrency Design

`ClaimReferenceService.generateNextReference(Long providerId)`:

1. `ProviderClaimSequenceRepository.ensureRowExists(providerId)` — a native
   `INSERT ... ON CONFLICT (provider_id) DO NOTHING`. Safe if two transactions
   race to create the first row for a brand-new provider: the loser's insert is
   a no-op.
2. `findByIdForUpdate(providerId)` — `@Lock(LockModeType.PESSIMISTIC_WRITE)`,
   i.e. `SELECT ... FOR UPDATE`. The row lock is held until the *caller's*
   transaction commits.
3. Read `next_value`, use it as the sequence, write back `next_value + 1`.
4. Format and return `CLM-P%03d-%06d`.

The method is `@Transactional(propagation = Propagation.MANDATORY)` — it will
throw `IllegalTransactionStateException` if called outside an existing
transaction. This is intentional: it forces the row lock to be scoped to the
caller's (the claim-creation) transaction, not its own short-lived one.
Verified `ClaimService` carries a class-level `@Transactional`, so
`createClaim` always provides that transaction.

This deliberately avoids the unsafe `SELECT MAX(sequence) + 1` anti-pattern:
under that approach, two concurrent transactions computing `MAX+1` from the
same base value before either commits would both compute the same next value
and collide. Here, the second transaction's `SELECT ... FOR UPDATE` blocks
until the first transaction commits (or rolls back), so it always reads the
already-incremented value.

## 5. Backfill Behavior

Existing claims are backfilled with:

```sql
ROW_NUMBER() OVER (PARTITION BY provider_id ORDER BY created_at, id)
```

— ordered by `created_at` then `id` per provider, giving each provider's claims
a stable, reproducible 1..N numbering matching their real creation order. Each
provider's `next_value` is then initialized to `COUNT(*) + 1` for that
provider, so newly created claims continue immediately after the backfilled
range with no gap or collision.

**Verified against the real local dev database** (`tba_waad_system` on
`localhost:5433`, all 9 existing claims, all belonging to provider 1) by
running the migration's SQL inside an explicit `BEGIN; ... ROLLBACK;` block
(dry run, no changes persisted):

```
 id  | provider_id |  claim_number
-----+-------------+-----------------
   1 |           1 | CLM-P001-000001
  51 |           1 | CLM-P001-000002
 101 |           1 | CLM-P001-000003
 151 |           1 | CLM-P001-000004
 201 |           1 | CLM-P001-000005
 251 |           1 | CLM-P001-000006
 301 |           1 | CLM-P001-000007
 351 |           1 | CLM-P001-000008
 401 |           1 | CLM-P001-000009
(9 rows)

 provider_id | next_value |        updated_at
-------------+------------+---------------------------
           1 |         10 | 2026-07-22 00:01:55.26762
(1 row)
```

A duplicate-check query (`GROUP BY claim_number HAVING COUNT(*) > 1`) returned
zero rows — no collisions.

## 6. Creation Paths Covered

`ClaimService.createClaim` is the **sole** claim-creation method (confirmed
exhaustively during the earlier audit — no other code path inserts a new
`Claim` row). It now calls:

```java
savedClaim.setClaimNumber(claimReferenceService.generateNextReference(savedClaim.getProviderId()));
savedClaim = claimRepository.save(savedClaim);
```

immediately after the initial insert (so the provider id is known), replacing
the old `"CLM-" + savedClaim.getId()` assignment.

## 7. DTO / API Changes

- `ClaimViewDto.claimNumber` already existed and is unchanged in shape — it now
  simply carries the new format's value instead of the old one.
- New additive method `ClaimService.getClaimByReference(String claimReference)`,
  mirroring the existing `getClaimByNumber(Long)`'s access-control /
  reviewer-provider-isolation pattern, but resolving via the new repository
  method `ClaimRepository.findByOfficialClaimReference(String)` (queries the
  real `claim_number` string column, unlike the misleadingly-named
  `findByClaimNumber(Long)`).
- New additive endpoint: `GET /claims/reference/{claimReference}`, placed
  immediately after the existing (untouched) `GET /claims/number/{claimNumber}`
  endpoint in `ClaimController`.
- No existing endpoint's request/response contract was changed.

## 8. Frontend Display Changes

- `frontend/src/utils/api-validators.js` — `validateClaimNumber` previously
  validated a completely fictional format (`CLM-YYYYMMDD-XXXX`, e.g.
  `CLM-20260101-0001`) that neither the old nor the new backend format ever
  matched. Its only caller in the entire frontend
  (`claims.service.js`'s `getByClaimNumber`) has zero UI callers — confirmed
  dead code, so this was a safe, low-risk fix. Rewritten to validate the real
  `^CLM-P\d{3}-\d{6}$` format.
- `ClaimReviewWorkspace.jsx` already displays `normalizedClaim.claimNumber` in
  its header (`مطالبة رقم ${normalizedClaim.claimNumber}`) and breadcrumb —
  this now shows the real official reference automatically, no change needed.
  Its fallback (`claim.claimNumber || \`CLM-${claim.id || id}\``, line 170) is
  left in place as a last-resort display guard for a null/legacy value; it is
  not business logic and does not affect what gets persisted or searched.

## 9. Search / Filter Support

Added `OR LOWER(c.claimNumber) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))`
to both the query and count query of:

- `ClaimRepository.searchPagedWithFilters`
- `ClaimRepository.searchPagedWithFiltersAndReviewerProviders`

So the future reviewer inbox (and any existing keyword search UI) can already
find claims by their official reference, in addition to whatever fields were
searchable before.

## 10. Tests and Results

**Unit tests** — `ClaimReferenceServiceTest` (new, 8 tests, pure Mockito, no
Spring context):

| Test | Result |
|---|---|
| First claim for a provider returns sequence 1 | PASS |
| Second claim for same provider increments sequence | PASS |
| Sequence is persisted incremented (post-issue value saved) | PASS |
| Two different providers each start independently at 1 | PASS |
| Large provider id / sequence values pad correctly (`CLM-P123-456789`) | PASS |
| `ensureRowExists` is always called before `findByIdForUpdate` (lock ordering) | PASS |
| Null `providerId` throws `IllegalArgumentException`, repository untouched | PASS |
| Missing row after `ensureRowExists` throws `IllegalStateException` | PASS |

```
mvn -o test -DskipTests=false -Dtest=ClaimReferenceServiceTest
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**Integration test** — added 3 methods to the existing
`ClaimLifecycleIntegrationTest` (`@SpringBootTest`, real claim-creation flow
through `ClaimService.createClaim`):

- `fullClaimLifecycle_shouldSucceed` — added assertions that the created
  claim's `claimNumber` matches `^CLM-P\d{3}-\d{6}$` and is not the old
  `"CLM-" + id` format.
- `secondClaimForSameProvider_incrementsSequenceAndKeepsUniqueReference` (new)
  — two claims for the same provider get distinct, sequential references.
- `claimsForDifferentProviders_eachSequenceStartsAtOneIndependently` (new) —
  two claims for two different providers each get `...-000001`.

**Known pre-existing blocker (not introduced by this phase):**
`backend/src/test/java/com/waad/tba/CheckLogic.java` is a leftover ad-hoc debug
script (a `public static void main`, not an actual test) that carries its own
`@SpringBootApplication` annotation. Its presence under `src/test/java` causes
Spring Boot's `@SpringBootTest` bootstrapper to find **two**
`@SpringBootConfiguration`-annotated classes (`CheckLogic` and
`TbaWaadApplication`) and fail with `IllegalStateException` before any test
context loads. This affects **every** `@SpringBootTest` in the project
(confirmed: it is the only other file with `@SpringBootTest` besides the one
touched here, `DropIndexTest`, and it fails identically). This predates this
phase — `CheckLogic.java` was not created or modified by CLAIM-NUMBERING-1 —
and fixing it is outside this phase's authorized scope (touching unrelated
test infrastructure). **Flagging as a recommended follow-up**, since it
currently blocks running any Spring-context integration test in the whole
backend, not just the one added here.

Because of this, `ClaimLifecycleIntegrationTest` could not be executed end to
end in this session. In its place, the actual concurrency-relevant unit
(`ClaimReferenceService`) was fully unit-tested (Section 10 table above), and
the migration's backfill/collision behavior was verified directly against the
real local dev Postgres database in an explicit dry-run transaction that was
rolled back afterward (Section 5) — together these cover format correctness,
per-provider independence, sequence progression, backfill determinism, and
absence of duplicates, which are the properties the integration test would
otherwise have exercised.

**Other verification:**

```
mvn -o compile                    → BUILD SUCCESS (707 files, no new warnings/errors)
npx eslint (changed frontend files) → 0 errors, 2 pre-existing prettier warnings
npx vite build                    → built successfully (ClaimReviewWorkspace-*.js chunk present)
git diff --check                  → only CRLF/LF autocrlf notices, no whitespace errors
```

## 11. Known Remaining Screens Not Updated

Per this phase's own scope note ("do not over-update all reports unless
necessary — document remaining screens"), the following frontend locations
still independently construct a `"CLM-" + id`-style fallback string and were
**not** touched in this phase (they are display-only fallbacks used when a
real `claimNumber` isn't already present in the data they're given, not
sources of truth):

- `frontend/src/pages/settlement/ProviderAccountsList.jsx` (3 locations)
- `frontend/src/pages/documents/DocumentsLibrary.jsx:158`
- `frontend/src/pages/claims/batches/components/BatchPrintReport.jsx:36`

Recommended follow-up: once this phase is merged and claims consistently carry
a real `claimNumber` from the backend, these fallbacks become dead code and
can be simplified/removed in a small dedicated cleanup — out of scope here to
avoid touching settlement/batch/document screens in a numbering-only phase.

## 12. Rollback Plan

- The change is purely additive at the schema level (`CREATE TABLE`, no
  `ALTER`/`DROP` on `claims`), plus a data backfill of the `claim_number`
  column that was already nullable.
- To roll back: drop `provider_claim_sequences` and, if desired, restore prior
  `claim_number` values from a pre-migration backup (the migration does not
  delete any other data, only overwrites `claim_number` string values).
- Reverting the code (`ClaimService`, `ClaimReferenceService`,
  `ClaimReferenceServiceTest`, repository/controller additions, frontend
  validator) is a plain revert of this phase's diff — no other module's files
  are touched.

## 13. Confirmation: No Financial Calculation Changed

`grep` and manual review confirm `claimReferenceService` and
`ClaimReferenceService` are referenced only in the claim-number assignment
block in `createClaim` (Section 6). No pricing, coverage, `CostCalculationService`,
`AtomicFinancialService`, approval, or settlement logic was read, written, or
reordered. The claim's `requestedAmount` assertion in the existing lifecycle
test (`isEqualByComparingTo("120.00")`) is unchanged and still passes through
the same code path as before.

## 14. Confirmation: No PreAuthorization / Visit Changes

No file under `modules/preauthorization` or `modules/visit` was created,
modified, or deleted in this phase. `grep` for `ClaimReferenceService` and
`provider_claim_sequences` outside `modules/claim` returns no matches.

## 15. Confirmation: No Push Was Done

All work in this phase is local, uncommitted, on branch
`integration/claims-review-complete-local`. No `git commit`, `git push`, or PR
was created for CLAIM-NUMBERING-1. Per the standing "CHANGE OF DELIVERY RULE",
this remains true until the user explicitly approves this phase and later
says "PUSH CLAIMS REVIEW WORK" once all Claims Review phases are complete.

---

**CLAIM-NUMBERING-1 READY FOR REVIEW**
