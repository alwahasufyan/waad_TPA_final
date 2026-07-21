# CLAIM-REVIEW-SECURITY-1 — Enforce Reviewer-Provider Isolation

**Branch:** `security/claim-reviewer-provider-isolation` (from clean `main`)
**Status:** Fixed, tested, and verified live in the local dev environment. **Not committed. Not pushed.**
**Scope:** Reviewer-provider isolation only. No calculation, submission, settlement, PreAuthorization, or taxonomy code touched.

---

## 1. Root issue

`ReviewerProviderIsolationService.validateReviewerAccess()` — the canonical, existing isolation
mechanism, already correctly used by `ClaimReviewService.reviewClaim` and `rejectClaim` — was
**not called consistently** across every reviewer-facing claim action. A `MEDICAL_REVIEWER`
whose provider assignments are managed via `medical_reviewer_providers` (enforced elsewhere)
could bypass that assignment entirely by calling certain other endpoints directly, since those
endpoints checked role membership (`@PreAuthorize`) but never checked *which* providers that
specific reviewer is allowed to see.

## 2. Vulnerable endpoints found (before fix)

| Endpoint | Service method | Isolation before fix |
|---|---|---|
| `POST /claims/{id}/start-review` | `ClaimReviewService.startReview` | **None** |
| `POST /claims/{id}/approve` | `ClaimReviewService.requestApproval` | **None** |
| `POST /claims/{id}/return-for-info` | `ClaimService.returnForInfo` | **None** |
| `GET /claims/{id}/cost-breakdown` | `ClaimService.getCostBreakdown`/`getCostBreakdownDto` | **None** — relied solely on `AuthorizationService.canAccessClaim()`, which explicitly returns `true` for *any* `MEDICAL_REVIEWER` regardless of provider assignment (`"REVIEWER can access all claims for review"` — by design, for role-only access, not isolation) |
| `GET/DELETE /claims/{id}/attachments*` (list, download, delete, count) | `ClaimAttachmentController.assertClaimBelongsToCaller` | **None** — DOCUMENTS-IDOR-1 added `ProviderContextGuard.validateProviderAccess`, which is a correct fix for `PROVIDER_STAFF` ownership but is an intentional no-op for reviewers; it does not know about `ReviewerProviderIsolationService` at all |

A `MEDICAL_REVIEWER` not assigned to a provider could, by knowing or guessing a claim ID:
start review on it, approve it, return it for correction, view its cost breakdown, and
list/download/delete its attachments — all without ever being assigned to that provider.

## 3. Safe endpoints found (already correct — used as the reference pattern, not modified)

| Endpoint | Service method | Why it was already safe |
|---|---|---|
| `PUT /claims/{id}/review` | `ClaimReviewService.reviewClaim` | Already calls `validateReviewerAccess` |
| `POST /claims/{id}/reject` | `ClaimReviewService.rejectClaim` | Already calls `validateReviewerAccess` |
| `GET /claims/{id}` | `ClaimService.getClaim` | Already calls `validateReviewerAccess` explicitly, with its own comment: *"MEDICAL REVIEWER ISOLATION: Defensive Validation (Read Access)"* — this corrects an inaccuracy in the earlier CLAIM-REVIEW-SPLIT-1 audit, which had listed this endpoint as having a gap; a closer re-read during this security phase found it was already correctly isolated |
| `GET /claims/inbox/pending`, `GET /claims/inbox/approved` | `ClaimReviewService.getPendingClaims`/`getApprovedClaims` | Already require an explicit `providerId` for isolation-subject users and validate it via `validateReviewerAccess` before querying — confirmed safe, no changes needed |

## 4. Exact authorization rule implemented

Reused the existing `ReviewerProviderIsolationService.validateReviewerAccess(User, Long providerId)`
exactly as-is — **no second isolation model was invented**. Its existing semantics:

- `MEDICAL_REVIEWER`: may act only if `claim.providerId` is among their active assignments in
  `medical_reviewer_providers` (checked via `existsByReviewerIdAndProviderIdAndActiveTrue`).
- `SUPER_ADMIN` / `ADMIN`: `isSubjectToIsolation()` returns `false` — the call is a no-op, access
  remains unrestricted, exactly as before.
- `PROVIDER_STAFF`: unaffected by this change — provider ownership continues to be enforced
  separately by `ProviderContextGuard` (DOCUMENTS-IDOR-1), which is still called first and
  unchanged.
- `DATA_ENTRY` / `INSURANCE_ADMIN` / `EMPLOYER_ADMIN`: unaffected — `isSubjectToIsolation()` only
  returns `true` for `MEDICAL_REVIEWER`, so this fix does not narrow or broaden their existing
  access in any way.

Applied by adding one call — `reviewerIsolationService.validateReviewerAccess(currentUser,
claim.getProviderId())` — immediately after the claim is loaded and the current user resolved,
in each of the five vulnerable methods, *before* any status check, transition, or financial
calculation runs. This mirrors exactly where `reviewClaim`/`rejectClaim` already placed the same
call.

**Also improved (message-only, not a new mechanism):** `validateReviewerAccess`'s
`AccessDeniedException` previously carried an English-only message with the numeric reviewer/
provider IDs embedded. Changed to a bilingual message with no IDs (matching
`ProviderContextGuard`'s existing style) — the IDs remain in the server-side `log.warn` line,
unchanged. This is what makes `GlobalExceptionHandler`'s existing `containsArabic()` passthrough
(from DOCUMENTS-IDOR-1) surface a meaningful `messageAr` instead of the generic fallback string.

## 5. Files changed

```
backend/src/main/java/com/waad/tba/modules/claim/service/ClaimReviewService.java
backend/src/main/java/com/waad/tba/modules/claim/service/ClaimService.java
backend/src/main/java/com/waad/tba/modules/claim/service/ReviewerProviderIsolationService.java
backend/src/main/java/com/waad/tba/modules/claim/controller/ClaimAttachmentController.java
backend/src/test/java/com/waad/tba/modules/claim/service/ClaimReviewServiceTest.java
backend/src/test/java/com/waad/tba/modules/claim/controller/ClaimAttachmentControllerAuthorizationTest.java
backend/src/test/java/com/waad/tba/modules/claim/service/ClaimServiceReviewerIsolationTest.java (new)
```

6 files modified, 1 new test file. 133 insertions / 4 deletions total across production + test
code — every production-code change is an additive one-line isolation check (plus the message
edit); no existing line of business logic was deleted or restructured.

## 6. Tests added

- `ClaimReviewServiceTest.java` (existing file, extended):
  - `startReview_reviewerNotAssignedToProvider_shouldThrowAccessDenied`
  - `requestApproval_reviewerAssignedToProvider_isolationCheckedBeforeTransition`
  - `requestApproval_reviewerNotAssignedToProvider_shouldThrowAccessDenied`
- `ClaimServiceReviewerIsolationTest.java` (new):
  - `returnForInfo_reviewerAssignedToProvider_succeeds`
  - `returnForInfo_reviewerNotAssignedToProvider_throwsAccessDenied`
  - `getCostBreakdown_reviewerAssignedToProvider_succeeds`
  - `getCostBreakdown_reviewerNotAssignedToProvider_throwsAccessDenied`
- `ClaimAttachmentControllerAuthorizationTest.java` (existing file, extended for the new
  constructor dependencies, plus new cases):
  - `reviewerAssignedToProviderCanDownloadAttachment`
  - `reviewerNotAssignedToProviderCannotDownloadAttachment`

Every negative test asserts the downstream service/state-machine/repository call was **never**
reached (`verify(..., never())...`) once isolation throws — confirming the check runs before any
side effect, not just before the HTTP response. Existing positive-path tests (`startReview_
shouldTransitionStatus`, `rejectClaim_shouldTransitionToRejected`, `requestApproval_
shouldInitiateAsyncPhase`, and all `ClaimAttachmentControllerAuthorizationTest` DOCUMENTS-IDOR-1
cases) were left untouched and still pass, confirming the new checks don't disturb legitimate
access.

## 7. Test results

```
mvn -o test -Dtest="ClaimReviewServiceTest,ClaimServiceReviewerIsolationTest,ClaimAttachmentControllerAuthorizationTest"
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
  (8 ClaimAttachmentControllerAuthorizationTest, 10 ClaimReviewServiceTest, 4 ClaimServiceReviewerIsolationTest)
```

Full existing suite for `com.waad.tba.modules.claim.**` was also run:

```
Tests run: 53, Failures: 12, Errors: 1
```

All 12 failures + 1 error are in `CostCalculationServiceTest`, `CoverageEngineServiceTest`, and
`ClaimLifecycleIntegrationTest` — the exact same pre-existing, unrelated failures already
documented in `docs/backlog/BL-001-test-suite-recovery.md` and confirmed unrelated to this fix in
the DOCUMENTS-IDOR-1 report (financial-assertion mismatches in cost/coverage tests, and a stray
`CheckLogic.class` causing a duplicate-`@SpringBootConfiguration` Spring context error). None of
the files behind those failures were touched here. Every test that touches claim review,
approval, or attachment authorization passes.

```
git diff --check → clean (only pre-existing LF/CRLF notices, no whitespace errors)
```

## 8. Live verification result

Performed against the running local stack (`waad-local-backend`), rebuilt from the fixed source
and confirmed via `javap` on the extracted deployed `.class` file that
`ClaimAttachmentController`'s new constructor dependencies (`ReviewerProviderIsolationService`,
`AuthorizationService`) were actually present in the deployed jar — not a stale build.

Setup (all local-only, created then removed): a new test user `sectest_reviewer`
(`MEDICAL_REVIEWER`, cloned password hash from the existing local test user `dar`), assigned to
provider 1 (دار الشفاء) only via `medical_reviewer_providers`; an existing `SUBMITTED` claim
(`id=251`) temporarily repointed to provider 51 (الحكمة, a provider the test reviewer is *not*
assigned to) with one test attachment attached, to exercise the negative path without needing to
construct a whole new claim graph.

| Check | Result |
|---|---|
| `start-review` on claim 201 (provider 1, **assigned**) | **200**, transitioned SUBMITTED → UNDER_REVIEW |
| `start-review` on claim 251 (provider 51, **not assigned**) | **403**, `messageAr`: "لا تملك صلاحية الوصول إلى مطالبات هذا المزود / You do not have access to this provider's claims" |
| `approve` on claim 251 (not assigned) | **403**, same messageAr |
| `return-for-info` on claim 251 (not assigned) | **403**, same messageAr |
| `cost-breakdown` on claim 251 (not assigned) | **403**, same messageAr |
| attachment list/download on claim 251 (not assigned) | **403**, same messageAr |
| `SUPER_ADMIN` cost-breakdown / attachment list on claim 251 | **200** — unaffected |

All local-only test data (test reviewer user, provider assignment row, temporary claim-provider
repoint, test attachment) was deleted/reverted afterward; claim 201's status was reverted from
UNDER_REVIEW back to SUBMITTED and claim 251's `provider_id` restored to 1 to leave the dev
database exactly as found.

## 9. Confirmation: no financial calculation changed

Zero edits to `CostCalculationService`, `CoverageEngineService`, `ClaimMapper`'s totals logic, the
`Claim` entity's `calculateFields()`/`validateFinancialIdentity()`, or any DTO field. The one
touch inside `getCostBreakdown` adds an isolation check *after* the existing cost calculation
call site but does not alter what `costCalculationService.calculateCosts(claim)` computes or
returns. `approvedAmount`, `netProviderAmount`, `providerDiscountAmount`, and all line-level
totals are untouched — confirmed by diff (the only lines added are isolation-check calls and
their comments) and by the passing pre-existing financial-identity assertions in
`ClaimReviewServiceTest`/`ClaimStateMachineTest`.

## 10. Confirmation: provider portal submission untouched

No file under `frontend/src/pages/provider/` or any provider-claim-submission backend path
(`ClaimController.submitClaim`, `updateClaimData`, `ClaimMapper`'s direct-entry mapping) was
touched. `PROVIDER_STAFF` behavior is unchanged — `ProviderContextGuard` (DOCUMENTS-IDOR-1) is
still called first in `ClaimAttachmentController.assertClaimBelongsToCaller`, exactly as before;
the new reviewer-isolation call is additive and only triggers (has any effect) for
`MEDICAL_REVIEWER` callers.

## 11. Confirmation: admin behavior unchanged

`SUPER_ADMIN`/`ADMIN` bypass `isSubjectToIsolation()` unconditionally (pre-existing logic, not
modified) — every new isolation check added in this phase is a no-op for these roles. Verified
live in §8 (admin successfully read cost-breakdown and attachments for a claim outside any
reviewer's assignment) and via the untouched, still-passing `reviewerAndAdminAccessRemains
Unaffected` test in `ClaimAttachmentControllerAuthorizationTest`.

## 12. Risks / deferred items

1. **Scope was limited to claims, per the task's explicit endpoint list.** The same class of gap
   (reviewer-role-only checks without provider-assignment isolation) very likely exists in
   `VisitAttachmentController` and `PreAuthorizationController`'s reviewer-accessible paths — both
   were fixed for `PROVIDER_STAFF` ownership in DOCUMENTS-IDOR-1 the same way `ClaimAttachment
   Controller` was, and neither calls `ReviewerProviderIsolationService` either. Not touched here
   — out of scope for this claims-only phase; recommend a follow-up in the same family as this one
   (e.g. `VISIT-REVIEW-SECURITY-1` / `PREAUTH-REVIEW-SECURITY-1`).
2. **`ClaimReviewService.processApprovalAsync`** (the background continuation of `/approve`) does
   not itself call the isolation check — this is intentional: it runs in a separate thread after
   the synchronous `requestApproval` call has already validated isolation and committed the
   `APPROVAL_IN_PROGRESS` transition; by the time the async phase runs, the isolation decision has
   already been made and enforced at the point of the initial HTTP request. No gap introduced.
3. **`getClaim`'s existing isolation check was independently confirmed correct** during this audit
   (see §3) — this corrects an inaccuracy in the earlier CLAIM-REVIEW-SPLIT-1 report, which had
   listed `GET /claims/{id}` as an open gap. That report's other findings (CLAIMS-APPROVAL-CALC-1,
   the missing reviewer inbox, line-level decision persistence) are unaffected by this correction.
4. **`GET /claims/{id}` itself was not modified** in this phase since it was already correct — no
   action needed there.

## 13. Rollback plan

Every change is an additive, single-purpose isolation check (or, in one case, a message-text
edit) with no altered control flow beyond "throw earlier if unauthorized." Rollback is a plain
`git checkout main -- <file>` per file, or a single `git revert` of the phase commit once merged
— no data migration, no schema change, and no coordinated frontend rollback is required since no
frontend file was touched.

---

**Do not commit. Do not push.**

## CLAIM-REVIEW-SECURITY-1 READY FOR REVIEW

STOP.
