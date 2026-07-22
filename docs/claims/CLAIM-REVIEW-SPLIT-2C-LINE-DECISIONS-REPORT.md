# CLAIM-REVIEW-SPLIT-2C — Persist Line-Level Review Decisions

**Status:** Implemented locally, backend-tested, live-verified against a rebuilt local Docker stack. **Not committed, not pushed** — awaiting your review.
**Branch:** `integration/claims-review-complete-local`.

---

## 0. Pre-flight confirmation (per your instructions)

- **CLAIM-REVIEW-SPLIT-2B is committed locally**: `5aa30b2 feat(claims): add reviewer claim inbox`, on top of `434dc94` (CLAIM-NUMBERING-1) and `e27bf90` (CLAIM-REVIEW-SPLIT-2A).
- **Working tree at start**: clean except the untracked Provider Portal recovery items (`.recovery/`, `docs/provider-portal/PROVIDER-PORTAL-WIP-RECOVERY-*.md`) — none of these were touched in this phase.
- Confirmed on branch `integration/claims-review-complete-local` throughout.

---

## 1. Data Model Audit (done first, before writing any code)

Read `ClaimLine.java` directly. It already has: `rejected` (Boolean), `rejectionReason`, `rejectionReasonCode`, `reviewerNotes` — but **no way to distinguish "not yet decided" from "approved"** (both look like `rejected=false`), and **no state for "clarification required"** (neither approval nor rejection). Per your own instruction, this is not "enough" — a new field was needed.

**Also found two pre-existing, unrelated wiring gaps while auditing the data path:** `ClaimMapper.toLineDto()` (entity → internal DTO) never copied `reviewerNotes` or `rejectionReasonCode` from the entity, even though `ClaimApiMapper` already reads both of those fields from the DTO to build the API response. This meant `reviewerNotes` and `rejectionReasonCode` were silently dropped between the entity and the API response for every existing claim, everywhere in the app — not something this phase caused, but directly blocking this phase's own requirement ("Ensure ... rejectionReason, reviewerNotes" reach the frontend). Fixed as a small, additive, read-path-only correction (§4).

## 2. Was a Migration Needed? Yes — `V95`

`ALTER TABLE claim_lines ADD COLUMN reviewer_decision VARCHAR(30);` — nullable, additive, no existing column touched/renamed/dropped. Backfills nothing (every existing row gets `NULL`, correctly meaning "no decision recorded yet"). Verified applied cleanly against the real local dev DB, both as a dry-run (`BEGIN; ... ROLLBACK;`) before touching any real environment, and for real once the backend container was rebuilt:

```
Successfully applied 1 migration to schema "public", now at version v95 (execution time 00:00.441s)
```

New Java enum `LineReviewDecision { APPROVED, REJECTED, CLARIFICATION_REQUIRED }` backs the column via `@Enumerated(EnumType.STRING)`.

## 3. Endpoint Added

**New:** `PUT /api/v1/claims/{claimId}/lines/{lineId}/decision` (no such endpoint existed before — confirmed via a full audit of `ClaimController` and search for any `/lines/{lineId}` route). Request body: `{decision, reason, reviewerNotes}` (`LineDecisionRequest`). Response: the updated line (`ClaimResponse.ClaimLineResponse`, same shape used everywhere else lines are returned).

## 4. Backend Files Changed

- **New:** `backend/src/main/resources/db/migration/V95__claim_line_reviewer_decision.sql`
- **New:** `backend/src/main/java/com/waad/tba/modules/claim/entity/LineReviewDecision.java`
- **New:** `backend/src/main/java/com/waad/tba/modules/claim/repository/ClaimLineRepository.java` — see §7, this is the crux of the financial-invariant design.
- **New:** `backend/src/main/java/com/waad/tba/modules/claim/api/request/LineDecisionRequest.java`
- **Modified:** `backend/src/main/java/com/waad/tba/modules/claim/entity/ClaimLine.java` — added `reviewerDecision` field.
- **Modified:** `backend/src/main/java/com/waad/tba/modules/claim/entity/ClaimAuditLog.java` — added `ChangeType.LINE_DECISION` (additive enum constant, nothing removed/renamed).
- **Modified:** `backend/src/main/java/com/waad/tba/modules/claim/dto/ClaimLineDto.java` — added `reviewerDecision` field.
- **Modified:** `backend/src/main/java/com/waad/tba/modules/claim/mapper/ClaimMapper.java` — `toLineDto()` now also maps `reviewerNotes`, `rejectionReasonCode` (the pre-existing gap from §1) and the new `reviewerDecision`; method visibility changed `private` → `public` so it can be reused for this endpoint's response (no behavior change, purely a visibility widening).
- **Modified:** `backend/src/main/java/com/waad/tba/modules/claim/api/response/ClaimResponse.java` — added `reviewerDecision` to the nested `ClaimLineResponse`.
- **Modified:** `backend/src/main/java/com/waad/tba/modules/claim/api/ClaimApiMapper.java` — maps the new field; `toClaimLineResponse()` visibility widened `private` → `public` for the same reason as above.
- **Modified:** `backend/src/main/java/com/waad/tba/modules/claim/controller/ClaimController.java` — new endpoint, `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")` (same pattern as `/approve`/`/reject`).
- **Modified:** `backend/src/main/java/com/waad/tba/modules/claim/service/ClaimService.java` — new `submitLineDecision(...)` method + one new constructor dependency (`ClaimLineRepository`). No other method changed.
- **New test:** `backend/src/test/java/com/waad/tba/modules/claim/service/ClaimServiceLineDecisionTest.java` — 11 tests, all pass (§10).

**Not touched:** `ClaimMapper.processEngineCalculations`, `CostCalculationService`, `AtomicFinancialService`, `ClaimReviewService`, `ClaimStateMachine`, or any file under `preauthorization/`, `visit/`, `settlement/`, `report/`, `medicalclassification/`, `monitoring/`, `backup/`, or provider-submission code.

## 5. Frontend Files Changed

- **Modified:** `frontend/src/services/api/claims.service.js` — new `submitLineDecision(claimId, lineId, data)`.
- **Modified:** `frontend/src/pages/claims/review/components/ClaimReviewServiceLinesPanel.jsx` — banner text replaced with the required `"قرارات مراجعة الخدمات محفوظة على الخادم."`; column header `"الحالة السريعة (مؤقت)"` → `"قرار المراجعة"`; the reason dropdown now shows for **both** REJECT and CLARIFY (previously REJECT-only — CLARIFY needs a reason too, per the backend rule); added a per-line saving spinner (`savingServiceKey`) that replaces the three decision buttons while that specific line's save is in flight; added a `lineDecisionsLocked` prop that disables the buttons independently of the existing, broader `reviewLock`.
- **Modified:** `frontend/src/pages/claims/review/ClaimReviewWorkspace.jsx` — `handleServiceDecision`/`handleServiceReason` now call the backend (previously pure local `setState`); new `persistServiceDecision` helper (loading state, Arabic success/error snackbar, reverts to server truth via `fetchClaim()` on error); the services-seeding effect now initializes each line's local UI state from the **persisted** `reviewerDecision`/`rejectionReason` on load/reload instead of always defaulting to "approve"; new `lineDecisionsLocked` memo (`SUBMITTED`/`UNDER_REVIEW`/`NEEDS_CORRECTION` only — narrower than the existing `reviewLock`, matching the backend's allow-list exactly); `normalizedClaim.services` mapping now carries `reviewerDecision`/`rejected`/`rejectionReason`/`reviewerNotes` through from the API response.

**Not touched:** any provider-submission page, `ClaimBatchEntry`/`ClaimBatchDetail`, `ClaimReviewNotesPanel.jsx` (its own localStorage-only warning is explicitly left as-is, per your instruction — notes persistence is out of scope for this phase), `ClaimReviewDecisionPanel.jsx`, `ClaimReviewActionBar.jsx`.

## 6. Exact Behavior — APPROVED / REJECTED / CLARIFICATION_REQUIRED

| Decision | `reviewerDecision` | `rejected` | `rejectionReason` | Reason required? |
|---|---|---|---|---|
| APPROVED | `APPROVED` | `false` | cleared to `null` (along with `rejectionReasonCode`) | No |
| REJECTED | `REJECTED` | `true` | set from `request.reason` | **Yes** — 400 with `messageAr: "سبب الرفض مطلوب."` if blank |
| CLARIFICATION_REQUIRED | `CLARIFICATION_REQUIRED` | `false` (not a rejection) | set from `request.reason` (the field is reused generically as "the reason for this decision" — documented in the DTO's own javadoc, not renamed since it's the existing column) | **Yes** — 400 with `messageAr: "سبب طلب الاستيضاح مطلوب."` if blank |

`reviewerNotes` is set whenever provided (any decision), left unchanged otherwise — matches "keep reviewerNotes if provided."

## 7. Authorization / Isolation — and the Financial-Invariant Design Decision

**This is the part of the audit you specifically asked me to stop and report on if it turned out line decisions could affect approved totals.** Here is exactly what I found and how it was resolved without needing to touch calculation logic:

**The risk (found before writing any code):** `Claim.java`'s own `@PreUpdate` hook, `calculateFields()`, unconditionally recomputes `requestedAmount`/`refusedAmount` from the lines on **every save of the `Claim` aggregate**, and — whenever the claim isn't yet `APPROVED`/`SETTLED` (i.e. exactly the statuses `SUBMITTED`/`UNDER_REVIEW`/`NEEDS_CORRECTION` this feature operates in) — it also recomputes `approvedAmount`/`netProviderAmount`/`patientCoPay`/`companyDiscountAmount` as a "preview". There is no `ClaimLineRepository` anywhere in the codebase — every existing line mutation happens only via `claimRepository.save(claim)` cascading to its lines. **A naive implementation (load the claim, mutate the line via `claim.getLines()`, save the claim) would have triggered that recompute as an unavoidable side effect of persisting the line change — silently doing exactly what you told me not to do.**

**The fix:** a **new, standalone `ClaimLineRepository`** (`JpaRepository<ClaimLine, Long>`) that fetches and saves the `ClaimLine` directly, never loading/touching the `Claim` entity for writing. `submitLineDecision` loads the parent `Claim` **only to validate it** (existence, provider, status) and to build the audit snapshot — it is never mutated, never saved. Because JPA entity lifecycle callbacks are per-entity, `Claim`'s own `@PreUpdate` simply never fires, since `Claim` is never flushed. Verified this holds, not just reasoned about it:
- Unit test `decision_neverSavesOrMutatesParentClaimFinancialFields` asserts `claim.getApprovedAmount()`/`getNetProviderAmount()` are unchanged and `verify(claimRepository, never()).save(any())`.
- **Live-verified** against the real dev DB: claim `351`'s `approvedAmount`/`netProviderAmount` (`72.00`/`72.00`) were read before and after submitting a REJECTED decision on one of its lines — identical both times.

**Authorization:** `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")` on the controller — same declarative mechanism already used and trusted for `/approve`/`/reject`. Live-verified: the `dar` (`PROVIDER_STAFF`) account got `HTTP 403` calling this endpoint.

**Reviewer-provider isolation:** `reviewerIsolationService.validateReviewerAccess(currentUser, claim.getProviderId())` — the exact same call used by every other reviewer-facing claim endpoint this session. Not independently live-tested against a second provider (same caveat as CLAIM-REVIEW-SPLIT-2B's report: this local dev dataset only has one provider's claims) — covered by the unit test `unassignedReviewer_isBlocked` instead.

**Status locking:** allowed only for `SUBMITTED`/`UNDER_REVIEW`/`NEEDS_CORRECTION` — deliberately narrower than `ClaimStateMachine.HARD_LOCKED_FINAL_STATES` (which only hard-locks `REJECTED`/`SETTLED`), because for *this* feature `APPROVED` and `BATCHED` must also be locked (a reviewer must not look like they're still adjusting line decisions on an already-decided claim). Live-verified: submitting a decision against claim `401` (status `APPROVED`) returned `400` with `messageAr: "لا يمكن تعديل قرار الخدمة في الحالة الحالية للمطالبة (موافق عليه)."`.

**Line-belongs-to-claim:** `ClaimLineRepository.findByIdAndClaimId(lineId, claimId)` — live-verified: submitting against line `13` (which belongs to claim `401`) under claim `351` returned `404` with `messageAr: "بند الخدمة غير موجود ضمن هذه المطالبة."`.

## 8. Audit Behavior

New `ClaimAuditLog.ChangeType.LINE_DECISION` (additive enum constant). Written via the existing `ClaimAuditService.recordChange(claim, ChangeType.LINE_DECISION, actor, comment, beforeClaim)` — the **same claim instance** is passed as both `claim` and `beforeClaim`, since its own fields are provably unchanged; this makes the audit log's before/after claim-level snapshot itself double as evidence the financial invariant held. The line id, previous decision, new decision, and reason/notes are recorded in the audit comment string (the existing audit mechanism's snapshot is claim-level, not line-level, so this is where the line-specific detail lives). Live-verified in the backend logs:
```
📝 Audit recorded: Claim 351 - LINE_DECISION by reviewer_test
```

## 9. Financial Invariant — Confirmation

- `approvedAmount`, `netProviderAmount`, `patientCoPay`, `requestedAmount`, `providerDiscountPercent`/`companyDiscountAmount`: **not read or written anywhere in `submitLineDecision`**, confirmed by code inspection, by the dedicated unit test, and by a live before/after comparison against the real dev DB (§7).
- The only path that ever sets `approvedAmount`/`netProviderAmount` remains `POST /claims/{id}/approve` (`ClaimReviewService`) — untouched by this phase.
- **No conflict/business-requirement discovery to escalate** — the risk was architectural (an unintended side effect of the existing `Claim` entity's save hook), not a business requirement that line decisions should affect totals. It was resolved with the standalone-repository technique above; nothing here required your separate approval to proceed.

## 10. Tests and Results

**Backend — `ClaimServiceLineDecisionTest`** (new, 11 tests, all pass):

```
mvn -o -DskipTests=false -Dtest="ClaimServiceLineDecisionTest" test
Tests run: 11, Failures: 0, Errors: 0
```

1. `approve_assignedReviewer_persistsDecisionAndClearsRejection` — ✅
2. `reject_withReason_persistsRejectedAndReason` — ✅
3. `clarificationRequired_withReason_persistsWithoutMarkingRejected` — ✅
4. `unassignedReviewer_isBlocked` — ✅
5. `lineNotBelongingToClaim_throwsNotFound` — ✅ (line-must-belong-to-claim)
6. `terminalClaimStatus_isLocked` — ✅
7. `rejectWithoutReason_throwsBusinessRuleException` — ✅
8. `clarificationWithoutReason_throwsBusinessRuleException` — ✅
9. `decision_neverSavesOrMutatesParentClaimFinancialFields` — ✅ (the financial-invariant proof test)
10. `auditEvent_isWritten` — ✅
11. `reload_returnedDtoReflectsPersistedDecision` — ✅

(Provider-staff-is-blocked is enforced identically to `/approve`/`/reject` via `@PreAuthorize` — not re-unit-tested at the service layer since the service has no role check of its own to test; confirmed instead live, §7/§11.)

**Regression check on the wider claim test suite** — ran `com.waad.tba.modules.claim.service.*Test` + `*.mapper.*Test` (62 tests): 12 pre-existing failures (`CostCalculationServiceTest`, `CoverageEngineServiceTest` — deductible/coverage-limit math, zero relation to this phase's files) + 1 pre-existing error (`ClaimLifecycleIntegrationTest`, the already-documented `CheckLogic.java` duplicate-`@SpringBootConfiguration` issue from earlier phases). **Verified these are not a regression**, not just assumed: stashed every file this phase touched, re-ran the same two failing test classes against the unmodified codebase — **identical 12 failures, same expected/actual values**. Restored the stash afterward; confirmed via `git status` that nothing was lost.

```
git diff --check                → only CRLF/LF autocrlf notices, no whitespace errors
npx eslint (5 changed frontend files) → 0 errors (pre-existing/formatting-only warnings)
npx vite build                  → succeeds, ClaimReviewWorkspace/ClaimReviewServiceLinesPanel chunks rebuilt
```

## 11. Browser/API Smoke Test Result

No browser-automation tool is available in this environment (same limitation as every other phase this session) — verified instead by rebuilding and redeploying the real local Docker stack (`.\waad.ps1 rebuild`, both backend and frontend) and driving the exact HTTP calls, plus inspecting the deployed JS bundle directly.

**Test account:** `reviewer_test` / `Admin@123` (`MEDICAL_REVIEWER`, assigned to provider 1). **Sample claim:** id `351` (`CLM-P001-000007`, status `SUBMITTED`, provider 1), lines `11` and `12`.

| Check | Result |
|---|---|
| V95 migration applies | ✅ `Successfully applied 1 migration to schema "public", now at version v95` |
| Locked status (claim `401`, `APPROVED`) rejects with Arabic message | ✅ `"لا يمكن تعديل قرار الخدمة في الحالة الحالية للمطالبة (موافق عليه)."` |
| REJECTED with reason on claim `351` line `12` persists | ✅ response `reviewerDecision:"REJECTED"`, `rejected:true`, reason/notes set |
| Claim-level financial fields unchanged after the above | ✅ `approvedAmount`/`netProviderAmount` = `72.00`/`72.00`, identical before and after |
| CLARIFICATION_REQUIRED on line `11` persists, not marked rejected | ✅ `reviewerDecision:"CLARIFICATION_REQUIRED"`, `rejected:false` |
| Reject without reason → Arabic 400 | ✅ `"سبب الرفض مطلوب."` |
| Provider staff (`dar`) blocked | ✅ `HTTP 403` |
| Line not belonging to claim → 404 Arabic | ✅ `"بند الخدمة غير موجود ضمن هذه المطالبة."` |
| Reload (`GET /claims/351`) shows persisted decisions | ✅ both lines' `reviewerDecision`/`rejected`/`rejectionReason` correct on a fresh fetch |
| Audit event written | ✅ `📝 Audit recorded: Claim 351 - LINE_DECISION by reviewer_test` (×2, one per decision) |
| Deployed frontend bundle contains the new banner text | ✅ `grep` inside the running container's built JS found `"قرارات مراجعة الخدمات محفوظة على الخادم."` |
| No hardcoded/temporary artifacts left over | ✅ old "مؤقتة ومحلية فقط" banner text no longer present in source or bundle |

**Not independently re-verified in this pass** (relies on 2A/2B already having confirmed it): that `ClaimReviewWorkspace` itself opens and renders without a blank screen in an actual browser click-through — the underlying `GET /claims/{id}` data call was exercised repeatedly above and returns correctly-shaped data, and the component code paths touched here are the same ones already smoke-tested in 2A/2B, but no new full browser walkthrough was performed specifically for this phase.

**Test data created during this smoke test, left in place (local dev DB only, harmless):** claim `351`'s line `12` now has a persisted `REJECTED` decision (reason "تجاوز حدود المنفعة", notes "راجعت التغطية مرتين") and line `11` has `CLARIFICATION_REQUIRED` (reason "يرجى تقديم تقرير طبي اضافي") — same pattern as prior phases' local test data (e.g. the `reviewer_test` account itself, seed pricing items). Not committed to any migration, not pushed anywhere.

## 12. Provider Visibility Follow-up

Not built in this phase (correctly out of scope — "do not build full provider portal UX"). The provider-side claim detail screens were not inspected for whether they already surface `reviewerDecision`/`rejectionReason` per line; **flagging as a follow-up to check**, not implemented or assumed either way.

## 13. Rollback Plan

- **Backend:** revert the 8 modified files + delete the 4 new files (migration, enum, repository, request DTO, test). The migration only adds a nullable column — reverting it (`ALTER TABLE claim_lines DROP COLUMN reviewer_decision;`) is safe and loses only the reviewer-decision metadata itself, nothing else.
- **Frontend:** revert the 3 modified files. No new frontend files were created.
- No claim-level financial data was ever written by this feature, so there is nothing financial to roll back regardless.

## 14. Confirmation: No Unrelated Modules Changed

`git status` for this phase's diff touches only: `backend/.../claim/*` (entity, dto, mapper, api, controller, service, repository, migration, test) and `frontend/src/pages/claims/review/*` + `frontend/src/services/api/claims.service.js`. Zero files under `preauthorization/`, `visit/`, `settlement/`, `report/`, `medicalclassification/`, `monitoring/`, `backup/`, `pages/provider/`, `pages/claims/batches/`, or any env file.

## 15. Confirmation: No Push Was Done

All work is local, uncommitted, on `integration/claims-review-complete-local`. No `git commit`, `git push`, or PR created for this phase. `origin/main` unchanged.

---

**CLAIM-REVIEW-SPLIT-2C READY FOR REVIEW**

STOP.
