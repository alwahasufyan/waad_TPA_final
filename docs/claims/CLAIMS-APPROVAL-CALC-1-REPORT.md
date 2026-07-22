# CLAIMS-APPROVAL-CALC-1 — Retire Risky Generic Review Path for APPROVED

**Branch:** `fix/claims-review-approved-net-consistency` (from clean `main`, includes merged
DOCUMENTS-IDOR-1, CLAIMS-AMOUNT-LABEL-1, CLAIM-REVIEW-SECURITY-1)
**Status:** Fixed, tested. **Not committed. Not pushed.**
**Scope:** `ClaimReviewService.reviewClaim`'s APPROVED-handling branch only. No other calculation,
lifecycle, submission, settlement, or PreAuthorization code touched.

---

## 1. Task 1 — confirm all current callers of `PUT /claims/{id}/review`

Re-verified fresh on this branch (not assumed from the prior audit):

```
grep -rn "updateReview(" frontend/src
→ frontend/src/pages/claims/batches/ClaimBatchDetail.jsx:173
    claimsService.updateReview(claimId, { status: 'NEEDS_CORRECTION', reviewerComment: comment })
```

**Exactly one caller in the entire frontend**, and it is hardcoded to `status: 'NEEDS_CORRECTION'`.
No caller anywhere sends `status: 'APPROVED'` to this endpoint.

## 2. Task 2 — confirm `ClaimBatchDetail.jsx` uses it only for NEEDS_CORRECTION

Confirmed by the same grep above and by reading the call site (a "Suspend" dialog action). This
call is unaffected by this phase's fix — the fix only blocks the `APPROVED` branch;
`NEEDS_CORRECTION` continues to work exactly as before (proven by the new
`reviewClaim_needsCorrection_stillWorks` test, §6).

## 3. Task 3 — confirm the current reviewer page uses the correct endpoints

Re-verified `frontend/src/pages/claims/ClaimViewMedicalReview.jsx`:

```
handleApprove      → claimsService.approve(id, {...})        → POST /claims/{id}/approve
handleReject       → claimsService.reject(id, {...})         → POST /claims/{id}/reject
handleRequestInfo  → claimsService.returnForInfo(id, {...})  → POST /claims/{id}/return-for-info
                     (calling claimsService.startReview(id) first if still SUBMITTED)
```

The live reviewer workspace never calls `PUT /claims/{id}/review` at all, for any status.

## 4. Task 4 — decision on safe treatment for `PUT /review`

**Option A (retire APPROVED handling from the generic review path) was chosen** — the option
your instructions explicitly preferred ("Prefer retiring APPROVED handling from generic review
path if safe"), and it is safe here because:

- Task 1 confirms zero live callers send `status: APPROVED` to this endpoint.
- Task 3 confirms the real reviewer workspace already exclusively uses the correct, safe
  `/approve` endpoint (which computes `approvedAmount` and `netProviderAmount` together,
  post-discount, and forces them equal — unchanged by this phase, see §5).
- Retiring outright is simpler and more defensible than either patching the old branch to also
  set `netProviderAmount` (Option B — but that branch never validated a discount, refusal, or
  coverage-limit rule either, so "fixing" it would mean re-implementing a second, parallel
  calculation path next to `/approve`'s, which is exactly the duplication risk your instructions
  warned against) or restricting it to `SUPER_ADMIN` (Option C — still leaves a second,
  differently-calculated approval path reachable, just by a smaller set of roles).

## 5. Task 6 — the correct `/approve` calculation path was not altered

Zero lines changed in `ClaimReviewService.requestApproval` or `processApprovalAsync`,
`CostCalculationService`, `AtomicFinancialService`, `ClaimMapper`, or the `Claim` entity. Diff is
scoped entirely to `reviewClaim`'s `APPROVED` branch (see §6 for the exact change) plus new tests.

## 6. Exact change made

In `ClaimReviewService.reviewClaim`, the branch that used to do:

```java
if (dto.getStatus() == ClaimStatus.APPROVED) {
    if (dto.getApprovedAmount() == null || dto.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessRuleException("APPROVED status requires approved amount > 0");
    }
    claim.setApprovedAmount(dto.getApprovedAmount());   // ← only field touched; netProviderAmount left alone
}
```

now unconditionally rejects an APPROVED target status through this endpoint, before any field is
touched, any state-machine transition is attempted, or anything is saved:

```java
if (dto.getStatus() == ClaimStatus.APPROVED) {
    throw new BusinessRuleException(
            "لا يمكن اعتماد المطالبة عبر مسار المراجعة العام. الرجاء استخدام نقطة اعتماد المطالبات المخصصة.",
            "... / Claims cannot be approved through the generic review endpoint. Use the dedicated claim approval endpoint instead.");
}
```

The `REJECTED`/`NEEDS_CORRECTION` validation branch immediately above, the reviewer-comment
assignment, the state-machine transition call, the save, and every audit call below are untouched
and still run exactly as before for every status other than `APPROVED`.

One residual, harmless piece of dead code was **left as-is, not cleaned up**, per the phase's
narrow-scope instruction: the audit block a few lines below (`if (dto.getStatus() ==
ClaimStatus.APPROVED) { claimAuditService.recordApproval(...); }`) can now only be reached if
`dto.getStatus() == previousStatus == APPROVED` (a no-op call with no status change), since any
attempt to *transition into* APPROVED now throws earlier. This is unreachable-in-practice dead
code, not a live risk — flagged here rather than silently expanding the diff to remove it.

## 7. Tests added

All in `ClaimReviewServiceTest.java` (existing file, extended):

- `reviewClaim_approvedStatus_shouldBeRejected_cannotDivergeAmounts` — proves `PUT /review` with
  `status: APPROVED` throws `BusinessRuleException` with the new Arabic message, and that the
  claim is left completely untouched (`approvedAmount`/`netProviderAmount` still `null`, no
  state-machine transition, no save) — i.e. divergence is now structurally impossible through this
  path.
- `reviewClaim_needsCorrection_stillWorks` — proves the one live caller
  (`ClaimBatchDetail.jsx`'s NEEDS_CORRECTION path) is completely unaffected: reviewer isolation
  still runs, the transition still happens, the reviewer comment is still saved.
- `processApprovalAsync_appliesDiscount_approvedAmountEqualsNetProviderAmount` — exercises the
  *correct* `/approve` calculation path directly (requested=1000, insurance-portion=200 → provider
  share=800, 10% contract discount → net=720) and asserts `approvedAmount` is forced equal to
  `netProviderAmount` (720 == 720), confirming task 7's first requirement.

Existing tests in the same file (`rejectClaim_*`, `startReview_*`, `requestApproval_*`,
`settleClaim_*`, all the CLAIM-REVIEW-SECURITY-1 isolation tests) were re-run unmodified and still
pass — confirming reject behavior, NEEDS_CORRECTION behavior, and reviewer isolation from
CLAIM-REVIEW-SECURITY-1 all remain intact, as required.

## 8. Test results

```
mvn -o test -Dtest="ClaimReviewServiceTest"
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Full `com.waad.tba.modules.claim.**` suite:

```
Tests run: 56, Failures: 12, Errors: 1
```

All 13 non-passing results are the same pre-existing `CostCalculationServiceTest`,
`CoverageEngineServiceTest`, and `ClaimLifecycleIntegrationTest` failures tracked in
`docs/backlog/BL-001-test-suite-recovery.md`, confirmed unrelated to this change (none of those
files were touched, and the same failures were already present before this phase, per
DOCUMENTS-IDOR-1's and CLAIM-REVIEW-SECURITY-1's reports).

```
git diff --check → clean
```

## 9. Files changed

```
backend/src/main/java/com/waad/tba/modules/claim/service/ClaimReviewService.java
backend/src/test/java/com/waad/tba/modules/claim/service/ClaimReviewServiceTest.java
```

2 files, 91 insertions / 4 deletions. No frontend file was touched — `ClaimBatchDetail.jsx`
needed no change since it never sent `status: APPROVED` to begin with (Task 2).

## 10. Migrations

None. No schema, entity field, or DTO change.

## 11. Screenshots

Not applicable — backend-only change, no UI touched.

## 12. Known blockers

None for this phase. For the roadmap as a whole: CLAIM-REVIEW-SPLIT-2A (next phase) can now
safely branch from `main` once this phase is merged, with `PUT /review` fully closed for APPROVED.

## 13. Rollback plan

Single-method change with no data/schema impact. Rollback is `git checkout main --
backend/src/main/java/com/waad/tba/modules/claim/service/ClaimReviewService.java` (and drop the
corresponding test additions), or a plain `git revert` of the phase commit once merged.

## 14. Confirmation no unrelated modules changed

`git status --short` shows exactly the 2 files in §9. No `ClaimMapper.java`, `ClaimService.java`,
`CostCalculationService.java`, `CoverageEngineService.java`, settlement, PreAuthorization, Visit,
taxonomy/classification, or env file was touched.

---

**Do not commit. Do not push.**

## CLAIMS-APPROVAL-CALC-1 READY FOR REVIEW

STOP.
