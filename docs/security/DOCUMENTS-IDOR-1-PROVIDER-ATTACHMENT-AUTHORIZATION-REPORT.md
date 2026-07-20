# DOCUMENTS-IDOR-1 — Provider Attachment Download Authorization Fix

**Status:** Fixed, tested, and verified live in the local dev environment.
**Scope:** Claim, Visit, and Pre-Authorization attachment endpoints reachable by `PROVIDER_STAFF`.
**Not committed / not pushed**, per instructions.

---

## 1. Vulnerable endpoints found (before fix)

All three attachment controllers authorized by **role only**. None verified that the
attachment's parent record (claim / visit / pre-authorization) belonged to the
authenticated provider. Any `PROVIDER_STAFF` user who knew or guessed another
facility's numeric attachment id could read or delete it.

| Controller | Endpoint | Ownership check before fix |
|---|---|---|
| `ClaimAttachmentController` | `GET /{claimId}/attachments` (list) | **None** |
| `ClaimAttachmentController` | `GET /{claimId}/attachments/{attachmentId}` (download) | Attachment↔claim consistency only — **no provider check** |
| `ClaimAttachmentController` | `DELETE /{claimId}/attachments/{attachmentId}` (delete) | **None** |
| `ClaimAttachmentController` | `GET /{claimId}/attachments/count` | **None** |
| `VisitAttachmentController` | `GET /{visitId}/attachments` (list) | **None** |
| `VisitAttachmentController` | `GET /{visitId}/attachments/{attachmentId}` (download) | **None** — didn't even check attachment↔visit consistency |
| `VisitAttachmentController` | `DELETE /{visitId}/attachments/{attachmentId}` (delete) | **None** |
| `VisitAttachmentController` | `GET /{visitId}/attachments/count` | **None** |
| `PreAuthorizationController` | `GET /{id}/attachments` (list) | **None** |
| `PreAuthorizationController` | `GET /{id}/attachments/{attachmentId}` (download) | **None** |
| `PreAuthorizationController` | `DELETE /{id}/attachments/{attachmentId}` (delete) | **None** |

Scope of the vulnerability: **download, list, and delete** were all exposed for all
three record types. No separate preview or metadata-only endpoint exists beyond these.

## 2. Safe endpoint found (partially — correcting the task's premise)

The task assumed `ClaimAttachmentController` was already a fully "known-good"
reference pattern. That was only **half true**: its `downloadAttachment` endpoint did
verify `attachment.getClaim().getId().equals(claimId)` (attachment belongs to the
claim in the URL), which the Visit and PreAuthorization controllers did not do at
all. But it never verified the *claim itself* belonged to the caller's own provider —
so it was equally exploitable by a `PROVIDER_STAFF` user who supplied someone else's
`claimId`/`attachmentId` pair. This is stated plainly rather than glossed over: there
was no fully safe attachment endpoint in the codebase before this fix.

Upload endpoints (`POST .../attachments`) were out of scope per the task's own
framing ("read/download/preview/delete"); they are noted here as a follow-up
candidate (a `PROVIDER_STAFF` user could plausibly also upload to another provider's
claim/visit/pre-auth) but were **not modified**, in keeping with "do not modify
PreAuthorization workflow beyond attachment authorization."

## 3. Root cause

Every one of these endpoints was annotated only with `@PreAuthorize(hasAnyRole(...))`
or `isAuthenticated()`. None consulted `ProviderContextGuard` — the primitive this
codebase already uses elsewhere to bind a `PROVIDER_STAFF` user's requests to their
own `providerId`. The parent entity (claim/visit/pre-authorization) was fetched or
implied by the service layer, but its `providerId` was never compared against the
caller's.

## 4. Exact files changed

- `backend/src/main/java/com/waad/tba/modules/claim/controller/ClaimAttachmentController.java`
  - Injected `ClaimRepository`, `ProviderContextGuard`.
  - Added `assertClaimBelongsToCaller(Long claimId)`: loads the `Claim`, calls
    `providerContextGuard.validateProviderAccess(claim.getProviderId())`.
  - Called at the top of `getClaimAttachments`, `downloadAttachment`,
    `deleteAttachment`, `getAttachmentCount`.
- `backend/src/main/java/com/waad/tba/modules/visit/controller/VisitAttachmentController.java`
  - Injected `VisitRepository`, `ProviderContextGuard`.
  - Added `assertVisitBelongsToCaller(Long visitId)` (used by list/count, which only
    have a `visitId`) and `assertVisitAttachmentBelongsToCaller(VisitAttachment)`
    (used by download/delete, which have the attachment loaded already — it reads
    only the lazy-proxy's cached id and delegates to `assertVisitBelongsToCaller` to
    avoid a `LazyInitializationException`, see §8 below).
  - `downloadAttachment` and `deleteAttachment` now also verify
    `attachment.getVisit().getId().equals(visitId)` (attachment↔visit consistency,
    matching the pattern Claims already had) before the ownership check.
- `backend/src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthorizationController.java`
  - Injected `PreAuthorizationRepository`, `ProviderContextGuard`.
  - Added `assertPreAuthorizationBelongsToCaller(Long id)` and
    `assertPreAuthorizationAttachmentBelongsToCaller(PreAuthorizationAttachment)`
    (the attachment entity only stores a raw `preAuthorizationId` long, not a JPA
    relation, so this always does a repository lookup).
  - `downloadAttachment` and `deleteAttachment` now also verify
    `attachment.getPreAuthorizationId().equals(id)` before the ownership check.
- `backend/src/main/java/com/waad/tba/common/error/GlobalExceptionHandler.java`
  - `handleAccessDenied` now passes the thrown `AccessDeniedException`'s own message
    through as `messageAr` when it already contains Arabic text (same
    `containsArabic()` fallback pattern already used by every other handler in this
    file), instead of always emitting the generic fallback string. This is what
    makes `ProviderContextGuard`'s existing bilingual message
    ("لا يمكن الوصول إلى بيانات مقدم خدمة آخر / Cannot access another provider's
    data") actually reach the client on a 403. Plain Spring-Security-authored
    `AccessDeniedException`s with no Arabic text are unaffected and still get the
    generic fallback — this change cannot leak anything it didn't already log.

No claim financial logic, settlement logic, taxonomy/classification code, or
provider portal UX was touched.

## 5. Authorization model after the fix

For every affected endpoint, in order:
1. Spring Security role check (unchanged, existing `@PreAuthorize`).
2. Attachment loaded (or, for list/count, the parent record is loaded directly).
3. Attachment-belongs-to-parent-id-in-URL check (data integrity — pre-existed for
   Claims, added for Visits and PreAuthorizations) → **404** on mismatch, so a
   guessed attachment id under the wrong parent id looks identical to a
   non-existent one.
4. `ProviderContextGuard.validateProviderAccess(parent.getProviderId())` → **403**
   if the caller is a `PROVIDER_STAFF` user whose own `providerId` doesn't match.
   This call is a no-op for `SUPER_ADMIN` / `MEDICAL_REVIEWER` (and any other
   non-provider role), so reviewer/admin access is unchanged.
5. Only if all of the above pass does the controller reach the attachment service.

This mirrors the task's Phase 3 preference: enforcement reuses the existing shared
`ProviderContextGuard` primitive rather than three bespoke duplicated checks, and the
new helper methods are narrow and named per the task's own suggestion
(`assertVisitAttachmentBelongsToProvider`-equivalent, etc.).

**Note on `messageAr` wording**: the task's preferred exact string was
"لا تملك صلاحية الوصول إلى هذا المرفق." Reusing `ProviderContextGuard` as-is (per
Phase 3's explicit preference for reuse over duplication) means the client instead
sees that guard's own existing message. This is a deliberate trade-off, not an
oversight — changing it here would mean the shared guard's copy would need to be
attachment-specific, which conflicts with its general-purpose use across the rest of
the app.

## 6. Tests and results

18 new authorization unit tests added (task asked for 14; extra coverage added for
the reviewer/no-op and consistency-check paths), all passing:

- `backend/src/test/java/com/waad/tba/modules/claim/controller/ClaimAttachmentControllerAuthorizationTest.java` (6 tests)
- `backend/src/test/java/com/waad/tba/modules/visit/controller/VisitAttachmentControllerAuthorizationTest.java` (7 tests)
- `backend/src/test/java/com/waad/tba/modules/preauthorization/controller/PreAuthorizationAttachmentAuthorizationTest.java` (5 tests)

Covered per module: provider downloads own attachment (200); provider blocked from
another provider's attachment (`AccessDeniedException`/403, service never reached);
list stays scoped; reviewer/admin access unaffected; guessed/non-existent id returns
safe not-found without leaking; provider blocked from deleting another provider's
attachment; provider can still delete their own (Visit case, following existing
service rules).

```
mvn -o test -Dtest="ClaimAttachmentControllerAuthorizationTest,VisitAttachmentControllerAuthorizationTest,PreAuthorizationAttachmentAuthorizationTest"
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Full existing suite for the three affected modules (`claim.**`, `visit.**`,
`preauthorization.**`) was also run. All pre-existing tests touching attachments,
claim state, and pre-authorization decisions pass unchanged, including
`PreAuthorizationDecisionControllerContractTest` and
`PreAuthorizationServiceDecisionTest`. 12 failures and 1 error appeared in that run,
all in `CostCalculationServiceTest`, `CoverageEngineServiceTest`, and
`ClaimLifecycleIntegrationTest` — none of these files, or the financial/coverage
services they test, were touched by this fix. They are pre-existing, unrelated
issues (financial rounding/coverage-cap assertions, and a duplicate
`@SpringBootConfiguration` Spring context error caused by a stray `CheckLogic.class`
left in `target/test-classes`), out of scope for DOCUMENTS-IDOR-1 and not introduced
by it.

## 7. Live security check

Performed against the running local stack (`waad-local-backend`, rebuilt from the
fixed source and confirmed via `javap` on the extracted deployed `.class` files that
the new helper methods were actually present in the jar — not a stale build).

Two real providers already existed locally (دار الشفاء `id=1`, الحكمة `id=51`). One
local-only test user was created for provider 51 (`dar51`, cloned from the existing
`dar` user's password hash) purely to exercise both sides of the check; it and all
test attachment/pre-authorization rows created for this check were **deleted again
afterward** — nothing persists in the dev database from this test.

| Check | Result |
|---|---|
| Provider 1 (`dar`) downloads own claim attachment | Not blocked (reaches the download service; 404 because no physical file backs this synthetic test row — confirms the auth gate was passed, not tripped) |
| Provider 51 (`dar51`) downloads provider 1's claim attachment by id | **403**, `messageAr`: "لا يمكن الوصول إلى بيانات مقدم خدمة آخر / Cannot access another provider's data" |
| Provider 51 downloads provider 1's visit attachment by id | **403** (see §8 — this uncovered and fixed a `LazyInitializationException` along the way) |
| Provider 51 downloads provider 1's pre-authorization attachment by id | **403** |
| Provider 51 lists provider 1's visit attachments | **403** |
| `SUPER_ADMIN` lists provider 1's claim / pre-authorization attachments | 200 — unaffected |
| `SUPER_ADMIN` downloads provider 1's visit attachment by id | Not blocked (404, no physical file — same as above) |

No cross-provider download succeeded at any point; every unauthorized attempt
received 403 without revealing whether the id existed under a different provider.

## 8. A bug this fix surfaced and corrected: `LazyInitializationException`

The first live pass against `VisitAttachmentController` failed with a 500
(`LazyInitializationException: could not initialize proxy [...Visit#1] - no
session`). The initial implementation of `assertVisitAttachmentBelongsToCaller` read
`attachment.getVisit().getProviderId()` directly off the attachment's lazy `@ManyToOne`
relation — but by the time the controller runs there is no open Hibernate session, so
anything beyond the proxy's cached id throws. Fixed by reading only
`attachment.getVisit().getId()` (safe on an uninitialized proxy) and re-fetching the
`Visit` through `VisitRepository` in `assertVisitBelongsToCaller`, which runs its own
transaction. Corresponding unit tests were updated to stub `visitRepository.findById`
for the download/delete paths, and the fix was confirmed live afterward.

**Separately observed, not fixed (out of scope):** `GET /visits/{id}/attachments`
(the list endpoint) throws the same class of error today when serializing the raw
`VisitAttachment` list to JSON, because Jackson touches the same lazy `visit` field
during serialization, outside any request-scoped session. This is pre-existing
behavior — my change doesn't touch what that endpoint returns or how it's
serialized — that simply had never been exercised before because `visit_attachments`
was otherwise empty in this local database. It only surfaced once this task's live
check inserted a row to test with. Left unfixed as it's a data-serialization defect
unrelated to attachment authorization and outside this task's stated scope.

**Also observed, not fixed (out of scope):** `pre_authorization_attachments.pre_authorization_id`
has a foreign key constraint pointing at a different, seemingly legacy table
(`preauthorization_requests`) rather than at `pre_authorizations` — the table the
`PreAuthorization` JPA entity is actually mapped to. This looks like a stale
migration artifact; as found, it would block real preauth-attachment uploads in this
local database unless a same-id row also happens to exist in
`preauthorization_requests`. Flagged here for the team's awareness; not touched, per
"do not modify PreAuthorization workflow beyond attachment authorization."

## 9. Backward compatibility

- All previously-working legitimate calls (a provider viewing their own attachments,
  a reviewer/admin viewing any provider's) behave exactly as before.
- Response status codes for "attachment/claim/visit/pre-auth not found" are
  unchanged (404) for Claims; for Visits and PreAuthorizations, a nonexistent
  attachment id now returns 404 via an explicit catch instead of falling through to
  a generic 500/400 in a couple of edge cases — this is a strict improvement, not a
  breaking change, and was verified not to alter the *success* path in any way.
- No API contracts, request/response DTOs, or routes changed.
- No frontend changes were required — see §10.

## 10. Frontend link check (Phase 6)

Inspected `ProviderDocuments.jsx` and the four attachment-related frontend API
service files (`claims.service.js`, `files.service.js`, `pre-approvals.service.js`,
`visits.service.js`). None of them construct or guess attachment ids client-side:

- `ProviderDocuments.jsx` fetches its list from `GET /api/v1/provider/documents`,
  which is already provider-scoped server-side
  (`ProviderContextGuard.getRequiredProviderIdStrict()` in
  `ProviderDocumentController`, confirmed by reading that controller). Its
  `resolveDocumentUrl()` only rewrites the origin/`/api/v1/` prefix of the
  `downloadUrl` the backend already returned for that provider's own document — it
  never substitutes or increments an id.
- The four `*.service.js` files are thin wrappers that take `claimId`/`visitId`/
  `preAuthId`/`attachmentId` as parameters supplied by the calling page (which in
  turn got them from an already-scoped parent view) and call the exact same routes
  this fix now secures.

No frontend changes were necessary. The security boundary is correctly enforced
server-side, and the frontend does not rely on hiding links as a control.

## 11. Confirmation: no unrelated modules changed

`git status` for this task's edits touches exactly four files:
`GlobalExceptionHandler.java`, `ClaimAttachmentController.java`,
`VisitAttachmentController.java`, `PreAuthorizationController.java`, plus three new
test files. No claim financial/settlement code, no taxonomy/classification code, and
no provider portal UX files were modified. (Other modified files visible in
`git status` — `ClaimMapper.java`, `ClaimService.java`, `VisitService.java`,
`compose.local.yaml`, various frontend files — are pre-existing uncommitted changes
from earlier work this session, untouched by this task.)

**Not committed. Not pushed**, per instructions.

---

## DOCUMENTS-IDOR-1 FIX VERIFIED — PROVIDER ATTACHMENT ACCESS SECURED

STOP.
