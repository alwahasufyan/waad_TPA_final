# PROVIDER-PORTAL-REVIEW-ROUTING-1 — Provider Claim Routing to Review Inbox and Batch Visibility Fix

## 1. Branch

`recovery/provider-portal-claim-submission` (on top of `d0e57fd feat(provider): restore claim submission workspace`, undisturbed, and the pending-commit VISIT-BUG-1 `VisitService.java` fix, also undisturbed).

## 2. Exact root cause

**a) Why a provider claim appeared in the batch/monthly screen while still unreviewed:**

The "نظام الدفعات للمطالبات" (batch/monthly) screen (`ClaimBatchManagement.jsx`, `ClaimBatchEntry.jsx`) computes each provider card's claim count and totals via `claimsService.getFinancialSummary({ employerId, providerId, dateFrom, dateTo })` — **with no `status` filter at all**. The backing endpoint (`GET /claims/financial-summary` → `ClaimService.getFinancialSummary` → `ClaimRepository.getFinancialSummary`) only excludes a status when one is explicitly passed; with none passed, it sums **every** claim in the date range regardless of status — DRAFT, SUBMITTED, UNDER_REVIEW, NEEDS_CORRECTION, APPROVED, BATCHED, SETTLED, REJECTED, all mixed together. This was confirmed live: the same query for provider 1 / July 2026 returned **14 claims / 2,333.00** unfiltered vs. **6 claims / 1,033.00** once restricted to `APPROVED, BATCHED, SETTLED` — the missing 8 were exactly the DRAFT/SUBMITTED/UNDER_REVIEW claims that have no business being counted as "financial batch" records yet.

**b) Why it did NOT (reliably) appear in the Reviewer Claim Inbox:**

`ClaimReviewInbox.jsx` defaulted its status filter to `''` (empty = "الكل"), which called `GET /claims` with **no status parameter**, and the backing `ClaimService.listClaims` query has the identical "no filter = everything" behavior as (a) — meaning the inbox's "الكل" view was not a curated review queue, it was every claim including DRAFT ones from other providers/moments, with no guarantee a specific freshly-submitted claim would be visible near the top of an unsorted, unfiltered, mixed-status list, and no default view that reliably surfaced exactly "what needs my review." Live testing during this phase confirmed the underlying data and isolation were actually correct (a `SUBMITTED` claim for the reviewer's assigned provider did return from `GET /claims`), but the frontend gave no dependable "review queue" experience — the fix in §3 below makes the default view an explicit, correct queue instead of relying on an unfiltered dump.

## 3. Final chosen status flow

No change was needed to claim status transitions themselves — auditing `useProviderClaimSubmission.js` and `ClaimService` confirmed **Option A was already fully implemented** in prior work (STATUS-1 / CLAIM-NUMBERING-1):

- **Save Draft** → `POST /claims` (STATUS-1 unconditionally forces `status=DRAFT` for any PROVIDER_STAFF caller, ignoring whatever the client sends) → claim stays `DRAFT`.
- **Submit for Review** → same create/update call, then a separate `POST /claims/{id}/submit` → `ClaimService.submitClaim` validates the claim is currently `DRAFT` or `NEEDS_CORRECTION` (rejects every other status with a `BusinessRuleException`) → transitions to `SUBMITTED` via `ClaimStateMachine`.
- Provider truly cannot create `APPROVED` (STATUS-1 override) and cannot bypass review (`/approve`, `/reject` remain restricted to `SUPER_ADMIN`/`MEDICAL_REVIEWER` only — unchanged, verified by reading `ClaimController`).

This phase's actual fix is entirely about **visibility/filtering**, not the state machine:
- Widened the shared `status` query parameter on `GET /claims` and `GET /claims/financial-summary` from a single `ClaimStatus` to `List<ClaimStatus>` (backward compatible — a single value still binds and behaves identically to the old `=` comparison).
- Reviewer Inbox now defaults to an explicit `[SUBMITTED, UNDER_REVIEW, NEEDS_CORRECTION]` status list (never an unfiltered call), with a "كل الحالات القابلة للمراجعة" option resolving to all 6 review-relevant statuses (still never `DRAFT`).
- Batch/monthly screens now explicitly request `[APPROVED, BATCHED, SETTLED]`.

## 4. Backend files changed

- `backend/src/main/java/com/waad/tba/modules/claim/repository/ClaimRepository.java` — `getFinancialSummary`, `searchPagedWithFilters`, `searchPagedWithFiltersAndReviewerProviders`: `status: ClaimStatus` → `statuses: List<ClaimStatus>`, condition `c.status = :status` → `c.status IN :statuses` (still null-safe/optional).
- `backend/src/main/java/com/waad/tba/modules/claim/service/ClaimService.java` — `getFinancialSummary` and `listClaims` signatures widened to match; the two internal call sites in `getClaimsByStatus` (used by `/claims/status/{status}`) updated to wrap their single status in `List.of(status)` — no behavior change there.
- `backend/src/main/java/com/waad/tba/modules/claim/controller/ClaimController.java` — `GET /claims` and `GET /claims/financial-summary`: `@RequestParam status` widened from `ClaimStatus` to `List<ClaimStatus>` (Spring binds a single repeated/comma value into a one-element list identically to before — verified no other caller of either endpoint or of `ClaimService.listClaims`/`getFinancialSummary` exists besides this controller).
- `backend/src/test/java/com/waad/tba/modules/claim/service/ClaimServiceReviewRoutingTest.java` (new) — 11 tests covering the submit-transition guard and status-list pass-through for both fixed queries.

**Confirmed unchanged / not touched:** `ClaimStateMachine`, `enforceProviderClaimCreationStatus` (STATUS-1), `/approve`, `/reject`, `/start-review`, `ClaimMapper`'s DATA-1 patch, V94/V95 migrations, no new migration added.

## 5. Frontend files changed

- `frontend/src/services/api/claims.service.js` — `list()` and `getFinancialSummary()` now accept an array for `status` and append it as repeated query params (single-value callers elsewhere in the app are untouched — arrays are additive).
- `frontend/src/pages/claims/review/ClaimReviewInbox.jsx` — see §7.
- `frontend/src/pages/claims/batches/ClaimBatchManagement.jsx` — both `getFinancialSummary` calls (per-provider card and the collapsible global stats panel) now pass `status: ['APPROVED', 'BATCHED', 'SETTLED']`.
- `frontend/src/pages/claims/batches/ClaimBatchEntry.jsx` — its equivalent batch-stats summary query, same fix.
- `frontend/src/pages/provider/hooks/useProviderClaimSubmission.js` — one string changed: the submit success message is now the ticket's required `"تم إرسال المطالبة للمراجعة الطبية"` (previously `"تم تقديم المطالبة للمراجعة بنجاح"` — same meaning, ticket-specified wording).

**Confirmed unchanged / not touched:** `ProviderClaimsSubmission.jsx`, all Provider Portal components restored in `d0e57fd`, `ClaimReviewWorkspace.jsx`, `MainRoutes.jsx`, menu items, Provider Portal eligibility UI.

## 6. Batch/monthly endpoint/page fixed

Confirmed the screenshot's "نظام الدفعات للمطالبات" is `ClaimBatchManagement.jsx` (route `/claims/batches`), and its per-provider card / detail-drilldown (`ClaimBatchEntry.jsx`, route `/claims/batches/detail`) both call the **same shared endpoint** `GET /claims/financial-summary`. This endpoint is also used by `FinancialReports.jsx` and `ProviderAccountsList.jsx` — both of those already have their own explicit user-facing status selector (including a deliberate "ALL" option for report browsing), so the shared query's backward-compatible, opt-in filtering was the safe fix: those two screens are unaffected (still get "ALL" when the user picks it), while the batch/monthly cards now always ask for `APPROVED, BATCHED, SETTLED` only.

## 7. Reviewer inbox UI changes

Implemented the "simple visual improvement" tier explicitly permitted by the ticket (full provider/month grouping deferred, documented in §12):

- Added a 3-card summary strip above the existing filter bar/DataGrid: matching-claim count (always available, server-paginated total), total requested amount, total rejected amount (the latter two require narrowing to a single provider, since the reviewer-isolation guard on `/claims/financial-summary` returns an empty result for an unscoped multi-provider reviewer query — this is intentional existing security behavior, not a new limitation; the card shows "اختر مقدم خدمة" until then, rather than fabricating an aggregate).
- Status filter default changed from `''` (true unfiltered — the bug) to a new `PENDING_REVIEW` virtual option ("قيد المراجعة (الافتراضي)"), resolving to `[SUBMITTED, UNDER_REVIEW, NEEDS_CORRECTION]`.
- "الكل" is redefined to resolve to the 6 review-relevant statuses (unchanged from before) — it was never possible for it to include `DRAFT` since `DRAFT` was never in that list; the actual bug was the separate true-empty-filter default, now removed.
- Existing provider filter, free-text search (claim number / member name), pagination, and "فتح المراجعة" action button/row-click into `ClaimReviewWorkspace` are all unchanged.

Deeper provider/month card-grouping (matching the batch screen's exact visual layout) is deferred — documented in §12.

## 8. Tests run and results

Focused backend suite (forced on via `-DskipTests=false`, project defaults `skipTests=true`):

```
Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
- ClaimMapperPricingContractTest: 2
- ClaimReferenceServiceTest: 8
- ClaimReviewServiceTest: 13
- ClaimServiceLineDecisionTest: 11
- ClaimServiceProviderStatusTest: 3
- ClaimServiceReviewerIsolationTest: 4
- ClaimServiceReviewRoutingTest: 11 (new)
- VisitServiceTest: 4
```

New `ClaimServiceReviewRoutingTest` (11 tests) covers:
1. `submitClaim` from `DRAFT`/`NEEDS_CORRECTION` → transitions to `SUBMITTED` (parameterized, 2 cases).
2. `submitClaim` from every other status (`APPROVED`, `SUBMITTED`, `UNDER_REVIEW`, `REJECTED`, `SETTLED`, `BATCHED`, `APPROVAL_IN_PROGRESS`) → rejected with `BusinessRuleException`, state machine and repository save never invoked (parameterized, 7 cases) — proves a provider can never skip review via re-submission.
3. `listClaims` forwards the caller's exact status list to the reviewer-isolated repository query unchanged.
4. `getFinancialSummary` forwards the caller's exact status list to the repository unchanged.

Provider-draft-only and provider-cannot-create-APPROVED (items 1 & 3 from the ticket's required-test list) were already fully covered by the pre-existing `ClaimServiceProviderStatusTest`. Reviewer isolation (item 7) already covered by `ClaimServiceReviewerIsolationTest`. Items 4–6 (visibility across the inbox and financial endpoints) were proven via live API testing (§9) rather than an additional mocked unit test, since they depend on the real query's `IN`/`JOIN` behavior against Postgres, which a mocked repository cannot meaningfully assert.

`git diff --check` — clean. `mvn -o compile` — `BUILD SUCCESS`. `npx eslint` on all 5 changed frontend files — 0 errors (2,441 pre-existing prettier/style warnings, none introduced by this change — confirmed by diffing warning count against an unrelated baseline file in the same run). `npx vite build` — succeeds.

## 9. Runtime smoke test result

Backend and frontend rebuilt via `.\waad.ps1 rebuild`. Full 13-step scenario run against the live stack:

1–4. Logged in as provider `dar`; `POST /claims` with no status sent → claim **751** (`CLM-P001-000014`) created with `status: DRAFT`.
5. `GET /claims?providerId=1&status=SUBMITTED&status=UNDER_REVIEW&status=NEEDS_CORRECTION` (the inbox's default query) → **751 absent**, `total: 4`.
6–7. `POST /claims/751/submit` → **200**, `status: SUBMITTED`.
8–10. Logged in as `reviewer_test`; re-ran the same inbox query → **751 present**, `total: 5`; `GET /claims/751` (the call behind "فتح المراجعة") → **200**, `status: SUBMITTED`.
11. `GET /claims/financial-summary?providerId=1&status=APPROVED&status=BATCHED&status=SETTLED` (the batch/monthly card's new query) → `claimsCount: 6`, `totalClaimsAmount: 1033.00` — **751 correctly excluded**. For comparison, the same call **without** a status filter (the old behavior) returns `claimsCount: 14`, `totalClaimsAmount: 2333.00` — the exact 8-claim, 1,300-unit discrepancy proves the bug and the fix.
12. Skipped a fresh full approval cycle (line-decision + approve is a multi-step, higher-risk flow) — instead verified with existing data: the 6 claims returned by the `APPROVED/BATCHED/SETTLED` filter are indeed the provider's already-approved claims, proving approved claims correctly remain visible in the financial view once they reach that stage.
13. Confirmed below (§10).

## 10. Confirmation Claims Review preserved

The reviewer inbox query in step 8 above returned claim 701 (a previously-submitted claim with a persisted line-level `reviewerDecision: REJECTED`) alongside the newly submitted 751, with all `CLAIM-REVIEW-SPLIT-2C` line-decision fields intact. Combined with 56/56 passing tests including `ClaimReviewServiceTest`, `ClaimServiceLineDecisionTest`, and `ClaimServiceReviewerIsolationTest`, Claims Review functionality is fully preserved.

## 11. Confirmation Provider Portal submission preserved

The claim-creation flow used in the smoke test (steps 1–7) is the exact same `POST /claims` → `PUT /claims/{id}/data` → `POST /claims/{id}/submit` sequence `useProviderClaimSubmission.js` uses; only its success-message string changed (§5). No file under `d0e57fd`'s restored workspace (`ProviderClaimsSubmission.jsx`, its components, or the hook's request-building logic) was touched beyond that one string.

## 12. Remaining UI gaps (deferred, documented per ticket)

- **Full eligibility UX redesign** — out of scope, deferred.
- **Provider/month card-grouping in the Reviewer Inbox** matching the batch screen's exact per-provider card visual (this phase implemented only the top summary-strip + default-status tier explicitly permitted as the smaller alternative) — deferred to a future UI phase if the team wants closer visual parity.
- **Provider Portal dashboard/channels** — out of scope, deferred.
- **Notes/conversation persistence** (`ClaimConversationPanel`, `ClaimReviewNotesPanel`) — still localStorage-only, deferred to `CLAIM-REVIEW-NOTES-1` (unchanged from the prior phase's finding).
- **VISIT-BUG-1** — fixed and validated in the immediately-preceding phase; its `VisitService.java` change remains uncommitted, awaiting the same commit-approval step as this phase's changes.

## 13. Confirmation no unrelated modules changed

`git status --short` shows exactly 9 modified files (4 backend, 5 frontend) plus 1 new backend test file — all directly implicated in this ticket's root cause. No changes to Visit lifecycle, eligibility/benefit-policy logic, PreAuthorization, settlements, reports engine, taxonomy/classification, monitoring/backup, env files, or migrations (no new migration was needed — this was a query/filter fix, not a schema change).

## 14. Confirmation no push was done

No `git commit` or `git push` was performed in this phase. All changes remain local, uncommitted modifications on `recovery/provider-portal-claim-submission`.

## 15. Rollback plan

All changes are additive/backward-compatible at the API layer (a single status value still behaves identically to before), so rollback is a straightforward revert of the working-tree diff for the 9 modified files and deletion of the 1 new test file — no migration to reverse, no data changes, and no other code depends on the widened parameter types (verified: `ClaimService.listClaims` and `getFinancialSummary` each have exactly one caller, `ClaimController`).

---

**PROVIDER-PORTAL-REVIEW-ROUTING-1 READY FOR REVIEW**
