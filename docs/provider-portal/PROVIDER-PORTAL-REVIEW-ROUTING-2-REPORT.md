# PROVIDER-PORTAL-REVIEW-ROUTING-2 — Batch Exclusion by Origin, Actor Tracking, Reviewer KPI Cards

Self-authored follow-up ticket, per explicit user approval in this session ("نعم، اكتب تذكرة الآن وابدأ"). Scope confirmed by the user's three answers:
1. Claim origin distinguished via a new explicit field (not inferred from existing data).
2. The batch/monthly **claims list** itself (not just the summary totals fixed in ROUTING-1) must exclude Provider Portal claims.
3. A full ticket covering createdBy/submittedBy/reviewedBy tracking and the reviewer-inbox KPI redesign.

Branch: `recovery/provider-portal-claim-submission` (still local, nothing pushed).

## 1. How reviewer→provider assignment works today (direct answer to your question)

- Backend: `PUT /api/v1/admin/medical-reviewers/{id}/providers` (SUPER_ADMIN only), backed by `MedicalReviewerProvider` entity and `MedicalReviewerProviderAssignmentService`.
- **There is no admin UI page for this.** `frontend/src/services/api/medical-reviewers.service.js` only exposes `getMyProviders()` (used by the reviewer's own inbox) — nothing calls `getAssignments`/`updateAssignments`.
- `reviewer_test`'s assignment to provider 1 in this dev environment was set directly via API/DB in an earlier session, not through any screen.
- There is also a leftover dev hack, `ReviewerAssignmentFixer.java` (a `CommandLineRunner`), that hardcodes every provider onto a user named `'nada'` at every backend startup. This is not a real mechanism and should not be relied on.
- **This remains a gap** — building the actual admin assignment screen is out of scope for this ticket (not requested) and is listed as a follow-up in §12.

## 2. Root cause (recap, now fully closed)

ROUTING-1 fixed the batch/monthly screen's **summary totals** (`getFinancialSummary`) to exclude non-financial statuses. But the actual **claims list grid** shown in your screenshot (`ClaimBatchDetail.jsx`, route `/claims/batches/detail`) calls the generic `GET /claims` with no status **or origin** filter at all — so a Provider Portal claim still appeared as a row in that grid (with a "عرض" action button) even while still DRAFT/SUBMITTED, indistinguishable from a manually-entered paper claim. There was no way to tell them apart before this ticket, because no field recorded how a claim entered the system.

## 3. Data model changes

New migration `V96__claim_submission_channel_and_review_actors.sql` — 3 additive, nullable columns on `claims`:

- `submission_channel VARCHAR(30)` — `PROVIDER_PORTAL` or `MANUAL_ENTRY` (new enum `SubmissionChannel`). NULL for every claim created before this migration (never backfilled/guessed, per your explicit choice).
- `submitted_by VARCHAR(255)` — username who called `POST /claims/{id}/submit`.
- `reviewed_by VARCHAR(255)` — username who approved or rejected the claim.

Also fixed a latent gap: `Claim.createdBy` has existed as a column since before this session but was **never populated anywhere in the codebase** — every claim had `createdBy: null`. Now set at creation.

## 4. Where each field is set

- **`createdBy` + `submissionChannel`** — `ClaimService.createClaim`, immediately after `claimMapper.toEntity(...)`: `submissionChannel = PROVIDER_PORTAL` when the caller is a provider (same check `enforceProviderClaimCreationStatus` already uses), else `MANUAL_ENTRY` (covers the batch-entry "إضافة مطالبة" manual path, used by MEDICAL_REVIEWER/admin roles).
- **`submittedBy`** — `ClaimService.submitClaim`, before the DRAFT/NEEDS_CORRECTION → SUBMITTED transition.
- **`reviewedBy`** — `ClaimReviewService.requestApproval` (Phase 1 of the split-phase approve — the synchronous half that still has the real authenticated user, before the async Phase 2 loses the security context) and `ClaimReviewService.rejectClaim`.

## 5. Backend files changed

- `backend/src/main/resources/db/migration/V96__claim_submission_channel_and_review_actors.sql` (new)
- `backend/src/main/java/com/waad/tba/modules/claim/entity/SubmissionChannel.java` (new enum)
- `backend/src/main/java/com/waad/tba/modules/claim/entity/Claim.java` — 3 new fields
- `backend/src/main/java/com/waad/tba/modules/claim/service/ClaimService.java` — sets `createdBy`/`submissionChannel` at creation, `submittedBy` at submit; `listClaims` gained an `excludeChannel` parameter via a new overload (the old 12-arg signature is preserved and delegates to it with `null`, so no other caller anywhere in the codebase needed to change)
- `backend/src/main/java/com/waad/tba/modules/claim/service/ClaimReviewService.java` — sets `reviewedBy` in `requestApproval` and `rejectClaim`
- `backend/src/main/java/com/waad/tba/modules/claim/repository/ClaimRepository.java` — `searchPagedWithFilters` and `searchPagedWithFiltersAndReviewerProviders` gained an `excludeChannel` parameter (`c.submissionChannel IS NULL OR c.submissionChannel <> :excludeChannel` — legacy untagged claims stay visible, only explicitly-tagged Provider Portal claims are hidden)
- `backend/src/main/java/com/waad/tba/modules/claim/controller/ClaimController.java` — `GET /claims` gained `excludeChannel` query param
- `backend/src/main/java/com/waad/tba/modules/claim/dto/ClaimViewDto.java`, `mapper/ClaimMapper.java`, `api/response/ClaimResponse.java`, `api/ClaimApiMapper.java` — read-only passthrough of the 3 new fields
- `backend/src/test/java/com/waad/tba/modules/claim/service/ClaimServiceReviewRoutingTest.java` — 2 new tests (`submitClaim` sets `submittedBy`; `listClaims` forwards `excludeChannel` verbatim)
- `backend/src/test/java/com/waad/tba/modules/claim/service/ClaimReviewServiceTest.java` — 2 new assertions added to the existing `rejectClaim_shouldTransitionToRejected` and `requestApproval_reviewerAssignedToProvider_isolationCheckedBeforeTransition` tests, confirming `reviewedBy` is set

**Not touched:** `ClaimStateMachine`'s transition rules, settlement calculations, `ClaimMapper`'s DATA-1 patch, V94/V95, PreAuthorization, taxonomy/classification, monitoring/backup, env files.

## 6. Frontend files changed

- `frontend/src/services/api/claims.service.js` — `list()` already forwarded arbitrary params verbatim (from ROUTING-1), so `excludeChannel` needed no service-layer change
- `frontend/src/pages/claims/batches/ClaimBatchDetail.jsx` — **the actual page behind your screenshot** — its claims-list query now sends `excludeChannel: 'PROVIDER_PORTAL'`
- `frontend/src/pages/claims/batches/ClaimBatchEntry.jsx` — same fix applied to its equivalent list query for consistency (a related manual-entry screen using the same pattern)
- `frontend/src/pages/claims/review/ClaimReviewInbox.jsx` — full KPI-card redesign (§7) + 3 new grid columns: `أنشأها` (createdBy), `أرسلها للمراجعة` (submittedBy), `راجعها/قرر بشأنها` (reviewedBy)
- `frontend/src/pages/claims/review/ClaimReviewWorkspace.jsx` — a small chip strip under the page header showing createdBy (with a "(بوابة مقدم الخدمة)" tag when Provider Portal), submittedBy, reviewedBy — only rendered when at least one is present

## 7. Reviewer Inbox KPI redesign

Implemented per-provider cards matching the batch/monthly card visual (rounded card, provider name, claim-count banner, colored stat rows), placed above the existing filter bar + grid:

- One card per provider from `medicalReviewersService.getMyProviders()`.
- Each card shows 3 breakdown rows with counts **and** amounts: `قيد المراجعة` (SUBMITTED+UNDER_REVIEW+NEEDS_CORRECTION), `معتمدة` (APPROVED+BATCHED+SETTLED), `مرفوضة` (REJECTED).
- Clicking a card toggles the `providerFilter` (click again to clear), instantly filtering the grid below — no new backend endpoint: each card reuses the same `getFinancialSummary` call already fixed and tested in ROUTING-1, called 3× (once per status group) per card.

**Deferred, not built:** a true "رفض جزئي" (partial rejection) breakdown chip. Computing it correctly requires aggregating claim-*line* level `reviewerDecision` values (from CLAIM-REVIEW-SPLIT-2C) across a whole claim to classify "some lines rejected, not all" — that's a materially different, line-level aggregate query that doesn't exist yet anywhere in the codebase. Building it well deserves its own small ticket rather than a rushed addition here; flagged for a future phase.

## 8. Tests run and results

```
Tests run: 57, Failures: 0, Errors: 0, Skipped: 0
(56 from ROUTING-1 + VISIT-BUG-1, plus:)
- ClaimServiceReviewRoutingTest: 12 (2 new: submittedBy set on submit; excludeChannel forwarded verbatim)
- ClaimReviewServiceTest: 13 (2 existing tests extended with reviewedBy assertions)
```

`createdBy`/`submissionChannel` population at creation was **not** covered by a new mocked unit test — `ClaimService.createClaim` has ~15 collaborators (visit/provider/pre-auth repositories, architectural guard, batch service, etc.); a meaningful mock setup for it would be disproportionate to a 2-line addition, so this was verified live instead (§9), consistent with the "smallest safe option" instruction from earlier tickets this session.

`git diff --check` — clean. `mvn -o compile` — `BUILD SUCCESS`. `npx eslint` on all 6 changed frontend files — 0 errors. `npx vite build` — succeeds.

## 9. Runtime smoke test result

Full stack rebuilt (`.\waad.ps1 rebuild`, both images). Live scenario:

1. **Create as provider `dar`** → claim 851, `status: DRAFT`, `createdBy: dar`, `submissionChannel: PROVIDER_PORTAL`.
2. **Submit** → `status: SUBMITTED`, `submittedBy: dar`.
3. **Batch-entry claims list** (`GET /claims?...&excludeChannel=PROVIDER_PORTAL`) → claim 851 **absent**, `total: 15`. Same call **without** the filter → claim 851 **present**, `total: 16`. Exact proof of the fix.
4. **Reviewer inbox** (`status=SUBMITTED,UNDER_REVIEW,NEEDS_CORRECTION`) → claim 851 present.
5. **Reviewer `reviewer_test` approves** (`start-review` → `approve`) → response shows `status: APPROVED`, `reviewedBy: reviewer_test`.
6. **Direct DB read** confirms all 4 fields persisted together: `created_by=dar, submitted_by=dar, reviewed_by=reviewer_test, submission_channel=PROVIDER_PORTAL`.
7. **Batch financial summary re-checked** — `claimsCount` went from 6 → 7 once claim 851 became APPROVED, correctly moving from "excluded" to "included" the moment it became financially relevant.

## 10. Pre-existing bug found during testing (NOT fixed — out of scope, flagged only)

While testing the reject path for `reviewedBy`, `POST /claims/{id}/reject` returned **HTTP 500** ("Financial inconsistency: net payable is negative (-10.00)", thrown by `Claim.validateFinancialIdentity()` during an audit-triggered mid-transaction flush) — for **both** the newly-created claim 851 **and** a completely untouched, pre-existing claim (201, created in an earlier session, never touched by any code in this ticket). This proves the bug is pre-existing and unrelated to PROVIDER-PORTAL-REVIEW-ROUTING-2 — it affects the reject workflow whenever a claim has `providerDiscountPercent=10%` + `discountBeforeRejection=true` (the default contract discount shape used throughout this dev database). Per this session's standing rule, **not fixed here** — documented only, as a candidate for its own dedicated ticket. The **approve** path (used for the `reviewedBy` verification in §9) is unaffected.

## 11. Confirmation Claims Review / Provider Portal preserved

- `reviewer_test`'s inbox, isolation, and line-decision history remain intact (57/57 tests, including all pre-existing `ClaimReviewServiceTest` cases).
- Provider Portal claim submission flow (`d0e57fd`) untouched beyond the one string already changed in ROUTING-1.
- `ClaimBatchManagement.jsx`'s per-provider summary cards (fixed in ROUTING-1) continue to work; `ClaimBatchDetail.jsx`'s underlying claims grid now correctly matches those summary totals (both exclude Provider Portal claims consistently).

## 12. Remaining gaps (deferred, documented)

- **Reviewer→provider assignment admin UI** — does not exist; only a backend endpoint. Recommended as the next follow-up given you asked about it directly.
- **"رفض جزئي" (partial rejection) KPI breakdown** — needs line-level aggregation, deferred (§7).
- **Pre-existing reject-path 500** (§10) — needs its own investigation ticket.
- Full eligibility UX redesign, Provider Portal dashboard/channels, notes/conversation persistence — unchanged from prior phases' findings, still deferred.

## 13. Confirmation no unrelated modules changed

`git status --short`: 17 modified + 3 new files, all directly implicated (Claim entity/service/repository/controller/mapper/DTOs, 5 frontend claim-review/batch pages, 2 test files, 1 migration, 1 new enum). No changes to Visit, eligibility, PreAuthorization, settlements, reports engine, taxonomy, monitoring/backup, env files.

## 14. Confirmation no push was done

No `git commit` or `git push`. All changes remain local, uncommitted.

## 15. Rollback plan

Revert the working-tree diff for the 17 modified files, delete the 3 new files (`SubmissionChannel.java`, `V96__...sql`, `ClaimServiceReviewRoutingTest.java` changes are additive so the whole file can be deleted or reverted to its ROUTING-1 state). No data migration needs reversing beyond dropping the 3 new nullable columns (no other code depends on them existing). `listClaims`'s new 13-arg overload is additive — the original 12-arg signature every other caller uses is untouched.

---

**PROVIDER-PORTAL-REVIEW-ROUTING-2 READY FOR REVIEW**
