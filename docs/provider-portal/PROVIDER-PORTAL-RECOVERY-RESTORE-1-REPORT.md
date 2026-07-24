# PROVIDER-PORTAL-RECOVERY-RESTORE-1 — Restore Report

**Status: implemented locally, backend-tested, live-verified against a rebuilt Docker stack. Not committed, not pushed — awaiting review.**

---

## 1. Starting Branch and New Branch

- Starting branch: `main` (updated — see §2).
- New working branch created: `recovery/provider-portal-claim-submission` (did not already exist, confirmed via `git branch --list` before creating).

## 2. Confirmation `main` Was Updated Before Branching

```
git branch --show-current  → main
git status --short         → (empty except expected untracked recovery items — see §15)
git log --oneline --decorate --max-count=10
  a7892b5 (HEAD -> main, origin/main, origin/HEAD) Merge pull request #12 ...
```

`main` was already fast-forwarded to `origin/main` (`a7892b5`) at the start of this task — confirmed identical, no divergence — before `git checkout -b recovery/provider-portal-claim-submission` was run.

## 3. Confirmation the Claims Review Package Was Preserved

Explicitly verified, not assumed:
- `ClaimReferenceService` (V94, official claim references) — untouched, its own file not modified.
- `ClaimLineRepository`/`LineReviewDecision`/line-decision endpoint (V95, CLAIM-REVIEW-SPLIT-2C) — untouched, own files not modified.
- `ClaimService.java` — the STATUS-1 patch (§9) was applied as a **surgical two-line + one-method insertion** onto the current, already-merged file, specifically to avoid re-introducing the exact "recovered blob predates CLAIM-NUMBERING-1/2C" hazard flagged in the Restore Plan. Confirmed via `mvn` test run (§12) that `claimReferenceService`/line-decision logic still work correctly after the patch.
- Full re-run of the Claims Review test suite (§12) and a live API smoke test (§13) both pass with zero regression.

## 4. Recovered Files Used

From `.recovery/provider-portal-2026-07-20/`, per the Extraction Map:

| Recovered file | Used how |
|---|---|
| `frontend/.../ProviderClaimsSubmission.jsx` | Copied directly (§5) |
| `frontend/.../hooks/useProviderClaimSubmission.js` | Copied directly (§5) — already fully DATA-1-compliant, no further edit needed |
| `frontend/.../components/ServiceLinesPanel.jsx` | Copied directly (§5) |
| `frontend/.../components/ClaimReviewStep.jsx` | Copied directly (§5) |
| `frontend/.../components/ClaimSectionPrimitives.jsx` | Copied directly (§5) |
| `frontend/.../components/ClaimStepTabs.jsx` | Copied directly (§5) |
| `frontend/.../components/ClaimSummaryPanel.jsx` | Copied directly (§5) |
| `frontend/.../components/ClaimWorkspaceFooter.jsx` | Copied directly (§5) |
| `frontend/.../components/MemberContextPanel.jsx` | Copied directly (§5) |
| `backend/.../mapper/ClaimMapper.java` | **Not copied wholesale** — diffed, only the DATA-1 hunk applied (§8) |
| `backend/.../service/ClaimService.java` | **Not copied at all** — diffed, only the STATUS-1 hunk manually re-applied (§9) |
| `backend/.../test/.../ClaimMapperPricingContractTest.java` | Copied directly (§10) |
| `backend/.../test/.../ClaimServiceProviderStatusTest.java` | Copied, then fixed for the current `ClaimService` constructor arity (§10) |
| Reports (`PROVIDER-PORTAL-DATA-1...`, `-STATUS-1...`, `-COMPREHENSIVE-STATUS...`, `-PHASE-3B...`) | Read for context only, not copied/staged — already in `docs/provider-portal/` as untracked recovery docs from the prior recovery phase |

`VisitService.java` was **not** touched (VISIT-BUG-1 is documented as a follow-up, §17, per this ticket's explicit "do not touch Visits except document as follow-up" rule).

## 5. Files Restored Directly (No Modification Needed)

Copied byte-for-byte from `.recovery/` (only path relocated — see §6):
`ProviderClaimsSubmission.jsx`, `useProviderClaimSubmission.js`, `ServiceLinesPanel.jsx`, `ClaimReviewStep.jsx`, `ClaimSectionPrimitives.jsx`, `ClaimStepTabs.jsx`, `ClaimSummaryPanel.jsx`, `ClaimWorkspaceFooter.jsx`, `MemberContextPanel.jsx`, `ClaimMapperPricingContractTest.java`.

Read carefully first to confirm: the recovered `useProviderClaimSubmission.js` **already contains the full DATA-1 fix** (no unsafe `medicalServiceId`/`pricingItemId` fallback anywhere, correct submit payload with all required fields — see §11). No additional DATA-1 code change was needed in the frontend at all.

## 6. Path Correction Found During Restore (Important)

The Extraction Map's own §4 flagged the `components/`/`hooks/` path as **"inferred, not confirmed."** That inference was wrong. The recovered `ProviderClaimsSubmission.jsx` itself imports `./hooks/...`, `./components/...`, and `./constants` **relative to its own location** — and that location is confirmed by `MainRoutes.jsx`'s existing lazy import (`pages/provider/ProviderClaimsSubmission`, unchanged). This means the correct structure has **no `claim-submission/` subfolder** — everything sits flat:

```
frontend/src/pages/provider/
  ProviderClaimsSubmission.jsx
  constants.js
  hooks/useProviderClaimSubmission.js
  components/*.jsx
```

Files were initially placed under a `claim-submission/` subfolder (matching the Restore Plan's inference) and had to be moved up one level after `vite build` failed with `Could not resolve "./components/CustomServiceDialog"` — confirmed the correct structure empirically via the actual import statements and a successful rebuild, not by re-guessing.

## 7. Missing Components — Resolution

The Extraction Map's §3 named 4 missing components (`ClaimContextHeader`, `ClinicalDataPanel`, `AttachmentsPanel`, `ClaimConversationPanel`). Reading the recovered `ProviderClaimsSubmission.jsx`'s actual import list surfaced **two more** not previously flagged: `BlockedAccessPage`, `CustomServiceDialog`, plus a `./constants` module (`LABELS`, `VISIT_TYPE_LABELS`, `MAX_UPLOAD_SIZE_MB`, etc.) that was never in the recovered set either.

`ClaimContextHeader` turned out to be a non-issue — the recovered `MemberContextPanel.jsx`'s own comment explains its data now lives there instead (Phase 3B merged the two).

For the remaining 5 missing pieces, resolution followed the ticket's option (A) then (B): searched the repo first (found none reusable — these are provider-submission-specific), then **rebuilt each faithfully from the still-present pre-Phase-3B monolith** (`ProviderClaimsSubmission.jsx`'s old 2,557-line version, which was never deleted, still had every one of these sections verbatim):

| New file | Source | Notes |
|---|---|---|
| `constants.js` | Old monolith's inline `LABELS`/`VISIT_TYPE_LABELS`/upload constants, copied verbatim (exact same Arabic wording, no new/invented text) | |
| `components/BlockedAccessPage.jsx` | Old monolith's `BlockedAccessPage` component, copied verbatim | |
| `components/CustomServiceDialog.jsx` | Old monolith's custom-service dialog JSX, adapted to receive the hook's props (`onDataChange`, `onSubmit`, etc.) instead of local state | Same validation/payload, zero behavior change |
| `components/ClinicalDataPanel.jsx` | Old monolith's diagnosis/pre-auth section (Row 3), moved into its own component | Same fields, same validation |
| `components/AttachmentsPanel.jsx` | Old monolith's attachments section (Row 4, attachments half only) | Same upload/list/delete flow |
| `components/ClaimConversationPanel.jsx` | Old monolith's chat section (Row 4, chat half) | **Still localStorage-only** — documented as a TODO pointing at the deferred `CLAIM-REVIEW-NOTES-1` item (§17), not a regression — the reviewer-side `ClaimReviewNotesPanel.jsx` is in the identical state today |

No broken imports remain — confirmed by a successful `vite build` (§14).

## 8. DATA-1 Implementation Details

**Backend (`ClaimMapper.java`)** — the recovered blob's diff against the *current* (post-merge) file was re-verified fresh (not assumed from the earlier recovery pass, since `main` had moved since then) and confirmed to be **exactly** the DATA-1 fix, isolated to one method:

```diff
+import com.waad.tba.common.exception.BusinessRuleException;
...
+ boolean isFreeTextAllowed = "GEN-MEDICATION".equals(codeToLookup) || "GEN-MEDICAL-SERVICE".equals(codeToLookup);
+ if (!isFreeTextAllowed && resolvedUnitPrice == null) {
+     throw new BusinessRuleException(..., "تعذر استخدام هذه الخدمة لأن ربطها بسعر العقد غير مكتمل. ...");
+ }
+ BigDecimal amountBasis = resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice;
  Integer quantity = ...;
- BigDecimal lineRequestedTotal = enteredUnitPrice.multiply(...);
+ BigDecimal lineRequestedTotal = amountBasis.multiply(...);
```

Applied as a targeted patch (not a file overwrite) — the current file's already-merged CLAIM-REVIEW-SPLIT-2C changes to the same file (`toLineDto` made `public`, `reviewerDecision`/`reviewerNotes`/`rejectionReasonCode` mapping added) were preserved untouched.

**Frontend (`useProviderClaimSubmission.js`)** — already correct as recovered, no further change needed:
- `fetchAvailableServices`: `medicalServiceId = normalizeId(item.medicalServiceId) || null` (real catalog link only) and `pricingItemId = normalizeId(item.pricingItemId ?? item.id) || null` — never conflated.
- `handleServiceSelect`: explicit guard — if a service has neither identity, refuses selection with the exact Arabic message from the DATA-1 report.
- `handleSubmit` payload: sends `medicalServiceId`, `pricingItemId`, `unitPrice`, `serviceCode`, `serviceName`, `serviceCategoryId`, `serviceCategoryName`, `quantity` per line — **no** `serviceId || normalizeId(selectedService?.id)`-style fallback anywhere (searched, confirmed absent).
- `ServiceLinesPanel.jsx`'s Autocomplete matches by `pricingItemId` first, then `medicalServiceId` — fixes the "selection disappears"/"Unknown Service" bug.

**Live-verified** (§13): a claim line submitted with `pricingItemId:2`, `medicalServiceId:null` produced `requestedAmount:100.00` (the resolved contract price) — not zero.

## 9. STATUS-1 Implementation Details

Recovered blob's `ClaimService.java` predates both CLAIM-NUMBERING-1 and CLAIM-REVIEW-SECURITY-1 (confirmed again from scratch on the current, post-merge file — the file has grown further since the original recovery's own note). Per the ticket's explicit rule, it was **not copied**. Instead, the exact isolated patch was re-applied by hand onto the current file:

```java
// in createClaim, right after validateAndEnforceProviderId(dto, currentUser):
enforceProviderClaimCreationStatus(dto, currentUser);
```

```java
// new method, placed directly after validateAndEnforceProviderId, matching file convention:
void enforceProviderClaimCreationStatus(ClaimCreateDto dto, User currentUser) {
    if (currentUser == null || !authorizationService.isProvider(currentUser)) return;
    if (dto.getStatus() != null && dto.getStatus() != ClaimStatus.DRAFT) {
        log.warn("🚨 PROVIDER_STATUS_OVERRIDE: ...");
    }
    dto.setStatus(ClaimStatus.DRAFT);
}
```

This is Option A from the original STATUS-1 investigation (controller/service-layer enforcement, zero `ClaimMapper` signature change) — provider-created claims are always forced to `DRAFT` regardless of client input; admin/data-entry direct-entry (non-provider roles) is completely untouched, confirmed by both the restored unit test (§10) and a live test (§13, claim `id:601`/`651` both created as `DRAFT`).

**No architectural ambiguity found** — the existing codebase already cleanly distinguishes provider vs. non-provider actors via `authorizationService.isProvider(currentUser)`, so this did not require stopping to ask a clarifying question per the ticket's contingency clause.

## 10. Missing Component Resolution — Tests

- `ClaimMapperPricingContractTest.java` — copied unmodified (constructor arity for `ClaimMapper` was unchanged since the original recovery).
- `ClaimServiceProviderStatusTest.java` — copied, then **fixed**: it constructs `ClaimService` via a fixed-position `new ClaimService(...)` call; `ClaimService` has gained 2 constructor dependencies since the blob was made (`ClaimLineRepository` from 2C, `ClaimReferenceService` from CLAIM-NUMBERING-1) — 26→28 total args. Updated the test's `null`-argument list from 27 to 28 positions (still only `authorizationService` is a real mock; the rest stay `null` since the method under test only touches that one dependency).

## 11. Exact Backend Files Changed

- `backend/src/main/java/com/waad/tba/modules/claim/mapper/ClaimMapper.java` — DATA-1 patch (small, isolated hunk).
- `backend/src/main/java/com/waad/tba/modules/claim/service/ClaimService.java` — STATUS-1 patch (2-line call site + 1 new method).
- **New:** `backend/src/test/java/com/waad/tba/modules/claim/mapper/ClaimMapperPricingContractTest.java`
- **New:** `backend/src/test/java/com/waad/tba/modules/claim/service/ClaimServiceProviderStatusTest.java` (fixed constructor arity)

No migration added or modified — confirmed not required (STATUS-1/DATA-1 are pure code fixes, no schema change).

## 12. Exact Frontend Files Changed

- `frontend/src/pages/provider/ProviderClaimsSubmission.jsx` — **replaced** (old 2,557-line monolith → Phase 3B recomposed workspace).
- **New:** `frontend/src/pages/provider/constants.js`
- **New:** `frontend/src/pages/provider/hooks/useProviderClaimSubmission.js`
- **New:** `frontend/src/pages/provider/components/{ServiceLinesPanel,ClaimReviewStep,ClaimSectionPrimitives,ClaimStepTabs,ClaimSummaryPanel,ClaimWorkspaceFooter,MemberContextPanel,BlockedAccessPage,CustomServiceDialog,ClinicalDataPanel,AttachmentsPanel,ClaimConversationPanel}.jsx` (12 files)

Nothing under `frontend/src/pages/claims/review/**` touched. No route or menu file touched (confirmed — `MainRoutes.jsx`'s existing lazy import already pointed at the right path).

## 13. Tests Run and Results

```
git diff --check                                          → clean (only CRLF/LF autocrlf notices)

mvn -o compile                                             → BUILD SUCCESS, 0 errors

mvn -o test -Dtest="ClaimMapperPricingContractTest,ClaimServiceProviderStatusTest"
  Tests run: 5, Failures: 0, Errors: 0

mvn -o test -Dtest="ClaimReviewServiceTest,ClaimReferenceServiceTest,ClaimServiceLineDecisionTest,
                     ClaimServiceReviewerIsolationTest,ClaimMapperPricingContractTest,ClaimServiceProviderStatusTest"
  Tests run: 41, Failures: 0, Errors: 0   ← full combined Claims Review + Provider Portal regression pass

npx eslint (all changed/new frontend files)                → 0 errors (82 pre-existing-style prettier warnings,
                                                                auto-fixed to 3 harmless pre-existing unused-var
                                                                warnings in the recovered hook, not authored here)

npx vite build                                              → succeeds; ProviderClaimsSubmission-*.js (57.91 kB) chunk built
```

## 14. Runtime Smoke Test Result

Local Docker stack rebuilt (`.\waad.ps1 rebuild`, both images) — confirmed no new Flyway migration ran (already at `v95`, as expected — this phase adds no migration). Test account: `dar` (`PROVIDER_STAFF`, provider `1`, "دار الشفاء").

| # | Check | Result |
|---|---|---|
| 1–2 | Claim submission page opens, modern workspace | ✅ Confirmed structurally: correct chunk built and deployed; `MainRoutes.jsx` route unchanged and already pointed at the right file |
| 3 | Member/visit context loads | ✅ `GET /visits/19` returns full visit/member data consumed by `MemberContextPanel` |
| 4 | Service selector loads provider contract pricing | ✅ `GET /provider/my-contract/services` returns 3 services with `contractPrice` |
| 5–6 | Add service line, correct name/code | ✅ Submitted line with `pricingItemId:2`, service resolved correctly |
| 7 | No "Unknown Service" | ✅ `serviceName:"طبيب"` stored and returned correctly (verified in DB directly, not just the API echo) |
| 8 | Payload includes `pricingItemId`/`unitPrice`/`serviceCode`/`serviceName` | ✅ Confirmed via the hook's `handleSubmit` code (§8) and a live submission using that exact shape |
| 9 | Saved claim `requestedAmount > 0` | ✅ `requestedAmount: 100.00` (claim id `651`) |
| 10 | Provider-created claim is DRAFT, not APPROVED | ✅ `status: "DRAFT"` (claims `601` and `651`, both created with no `status` field sent, exactly like the real hook) |
| 11 | Official claim reference generated | ✅ `claimNumber: "CLM-P001-000012"` |
| 12 | Claims Review still works | ✅ `/claims?search=CLM-P001-000012` (reviewer inbox query) finds it; `GET /claims/651` (workspace data) returns `200`; line-decision endpoint correctly rejects the DRAFT claim with `"لا يمكن تعديل قرار الخدمة في الحالة الحالية للمطالبة (مسودة)."` — proving 2C's own status-lock logic is intact and unaffected |
| 13 | No V94/V95 regression | ✅ No new migration ran; `claim_number` generation and `reviewer_decision` column both function correctly in the same live test |

**One test artifact worth noting, not a defect:** an early manual test attempt (typing Arabic directly into an inline `curl -d` argument in this shell) corrupted the Arabic text to `????` on write — traced and confirmed to be a **Windows console codepage encoding issue in my own manual test method**, not a backend/database bug. Re-tested via a UTF-8 file payload (`--data-binary @file`) and confirmed `طبيب` stores and round-trips correctly. Documenting this so it isn't mistaken for a real defect if re-tested casually the same way.

**Not independently re-verified:** an actual browser click-through (no browser-automation tool available in this environment, consistent with every other phase this session) — verified instead via the same rebuilt-Docker-stack-plus-real-HTTP-calls method used throughout this session.

## 15. Untracked File Status

`.recovery/provider-portal-2026-07-20/` — **not committed, not staged**, confirmed via `git status --short` at every checkpoint in this task. Preserved as-is for any future reference.

The 3 `docs/provider-portal/PROVIDER-PORTAL-WIP-RECOVERY-*.md` files remain untracked, unchanged, not staged — read for context only per the ticket's instruction.

## 16. Confirmation No Unrelated Modules Changed

`git status --short` for this entire task's diff:
```
 M backend/.../claim/mapper/ClaimMapper.java
 M backend/.../claim/service/ClaimService.java
 M frontend/src/pages/provider/ProviderClaimsSubmission.jsx
?? backend/src/test/.../claim/mapper/ClaimMapperPricingContractTest.java
?? backend/src/test/.../claim/service/ClaimServiceProviderStatusTest.java
?? frontend/src/pages/provider/components/
?? frontend/src/pages/provider/constants.js
?? frontend/src/pages/provider/hooks/
```
(plus the pre-existing untracked `.recovery/` and provider-portal recovery docs, unchanged from before this task started).

Zero files under `preauthorization/`, `visit/` (VisitService.java deliberately not touched — §17), `settlement/`, `report/`, `medicalclassification/`, `monitoring/`, `backup/`, `frontend/src/pages/claims/review/**`, any route/menu file, or any `.env*` file.

## 17. Known Remaining Provider Portal Gaps

- **VISIT-BUG-1** (deferred, per this ticket's own instruction): `VisitService.findById()` is still `@Transactional(readOnly = true)` while calling an audit-log insert inside it — confirmed still present in the current codebase (`grep` on the live file). This causes `GET /visits/{id}` to fail under Postgres's read-only transaction enforcement in the specific case where that code path is hit. The recovered fix is a one-line, zero-side-effect change (`readOnly = true` → removed), already verified safe in the earlier recovery pass — **not applied in this task**, flagged here as a clean, independent follow-up.
- **`ClaimConversationPanel`** remains localStorage-only (§7) — matches the reviewer-side `ClaimReviewNotesPanel.jsx`'s identical current state; both are the responsibility of the deferred `CLAIM-REVIEW-NOTES-1` phase.
- The 3 `unidentified/blob-*-classification-service-*` and 3 `unidentified/blob-*-lazyimports-*` files in `.recovery/` remain unexamined — the Extraction Map already recommended they're likely out of scope (unrelated module / possible `MainRoutes.jsx` snapshots); not revisited here.
- No live browser click-through was performed (environment limitation, disclosed in §14) — recommend a manual pass before merging to `main`.

## 18. Rollback Plan

- **Backend:** revert the 2 small patches (`ClaimMapper.java`, `ClaimService.java` — each a few added lines) and delete the 2 new test files. Both patches are additive/isolated; reverting them restores the exact pre-restore behavior with no side effects on Claims Review.
- **Frontend:** delete `frontend/src/pages/provider/constants.js`, `hooks/`, `components/`, and restore `ProviderClaimsSubmission.jsx` from the previous commit (`git checkout main -- frontend/src/pages/provider/ProviderClaimsSubmission.jsx`) to return to the pre-Phase-3B monolith. No other file depends on the new structure (route already pointed at the same file path before and after).
- No migration was added, so there is nothing to roll back at the database level.
- `.recovery/` remains available locally regardless of any rollback decision.

## 19. Recommendation for Commit

All verification passed (backend tests, compile, frontend lint/build, live Docker smoke test, Claims Review regression check) with no blockers found. Recommend proceeding to review and, if approved, a local commit (still no push) scoped to exactly the files in §16 — same phase-by-phase, local-only discipline as every other phase this session.

---

**PROVIDER-PORTAL-RECOVERY-RESTORE-1 READY FOR REVIEW**

STOP.
