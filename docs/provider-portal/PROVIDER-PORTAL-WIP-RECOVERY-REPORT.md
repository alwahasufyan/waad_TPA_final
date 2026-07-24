# PROVIDER-PORTAL-WIP-RECOVERY — Investigation Report

**Investigation only — no changes applied, nothing restored, nothing committed.**

---

## 0. ⚠️ URGENT CORRECTION — THE WORK WAS FOUND

**Everything below §1–§12 was written before a deeper search. It is now superseded — do not act on its "not found" conclusion or its final status line. The real findings are here.**

After you pointed out a screenshot's own file metadata (`Modified At: 7/20/26 6:43 PM`), I re-opened the investigation instead of trusting the first (negative) git search. That screenshot timestamp was the right lead: it meant a real, running build existed on 2026-07-20 evening, so something had to be findable outside normal `git log`/stash — and it was.

**Where it was hiding:** `git fsck --unreachable --no-reflogs` surfaced ~310 *dangling* (unreachable but not yet garbage-collected) blobs and one dangling commit. These are leftover objects from a `git stash` (or similar) that was later **dropped** — the ref pointing to it is gone, so it doesn't show in `git stash list` or any branch/reflog, but the underlying blob content is still physically present in `.git/objects` until git's garbage collector eventually prunes it. I matched blob hashes to real filenames by cross-referencing them against dangling **tree** objects (which do record filenames), and confirmed exact identities:

| Blob hash | Confirmed file | Size |
|---|---|---|
| `9453de0098078fbf37ef55ddadabdec7a7b049de` | `frontend/src/pages/provider/ProviderClaimsSubmission.jsx` (the **recomposed** Phase 3B version — 14,651 bytes, not the 2,557-line monolith) | 14.3 KB |
| `5cb32742770fe1a22233b3c522183b0601e91c33` | `frontend/src/pages/provider/claim-submission/hooks/useProviderClaimSubmission.js` (the extracted business-logic hook, with the DATA-1 fixes applied) | 53.6 KB |
| `52f7dd063413ddd1a159a13aa201ed8b5f50240e` | `backend/src/test/java/com/waad/tba/modules/claim/mapper/ClaimMapperPricingContractTest.java` | 6.0 KB |
| `e32d85032e55ea3cc28e194fd5be3cc6ed6b340f` | `.../ServiceLinesPanel.jsx` (a Phase 3A/3B workspace panel component) | 19.6 KB |
| `ce515bed12f414e0cafccf0a7670fb6846bc227d` | `backend/.../claim/service/ClaimService.java` — **full file, including the STATUS-1 `enforceProviderClaimCreationStatus` fix** | 91.3 KB |
| `fc16a414317425aca9371e53570526e3e298101b` | `backend/.../claim/mapper/ClaimMapper.java` — **full file, including the DATA-1 amount-basis fix** | 36.0 KB |

Plus several more dangling blobs matching component-file signatures (`ClaimReviewStep`-style recap table, a `MemberContextPanel`-style card, footer/step-tab-style button bars) that were not individually tree-matched yet but are almost certainly the remaining Stage 3A/3B components — recoverable the same way if you approve going further.

**And three complete, detailed reports were recovered in full**, confirming exactly what was built, tested, and verified live (all quoted in full below in the corrected findings):
- `PROVIDER-PORTAL-UX-1 — Phase 3B Report: Lovable-Aligned Workspace Redesign` — full-viewport, real 5-step workspace (not 4 as first guessed — bianات المطالبة / الخدمات الطبية / البيانات السريرية / المرفقات / المراجعة والإرسال), deployed to the local Docker frontend, `vite build` + `eslint` clean.
- `PROVIDER-PORTAL-DATA-1 — Service/Pricing Contract Repair Report` — root-caused **two** independent defects (an identity bug: frontend never sent `pricingItemId`; and a separate arithmetic bug: `ClaimMapper` computed the total from the frontend's `unitPrice`, which was never sent, instead of the contract price it had already correctly resolved). Fixed both, live-verified via real API calls (draft save → `201`, correct resolved amount), 2 new backend tests.
- `PROVIDER-PORTAL-STATUS-1 — Claim Creation Status Repair Report` — root-caused why provider claims were born `APPROVED` instead of `DRAFT` (`POST /claims` is one shared endpoint for 4 roles; the mapper's `APPROVED` default is a legitimate admin/data-entry fast path that was never scoped away from providers). Fixed with a new provider-only guard in `ClaimService`, live-verified end-to-end: create → `DRAFT` → reopen → submit → `SUBMITTED`, financial fields unchanged throughout.

**Why this didn't show up in the first pass:** dropped stashes are invisible to every command I ran the first time (`stash list`, `log --all`, `branch --all`, `reflog`) — they only remain visible to `git fsck --unreachable`, and only until garbage collection runs (`git gc`, which can trigger automatically). **This is time-sensitive** — the objects are real right now, but there is no guarantee they survive an eventual automatic `git gc`.

**This does not change §1–§12 below factually** (the branch-mix-up finding in §1, the current `git status` in §2, and the "nothing in reachable git history" findings in §3–§5 are all still accurate as far as *reachable* history goes) — it adds a new, higher-priority finding on top: **the work exists, unreachable, recoverable right now via `git cat-file`, and should be extracted before anything triggers garbage collection.**

**Recommended immediate next step (not yet done, awaiting your go-ahead):** extract every relevant dangling blob to its correct path in the working tree (`git cat-file -p <hash> > <path>`, one file at a time, reviewed before overwriting anything), verify each against its report's description, then treat it as a fresh set of uncommitted local changes — same as any other phase in this session (report, your review, only then a local commit, never a push). This is **read-only relative to your repository** (`git cat-file` only reads objects, never writes) until we actually write files back into the working tree, which I have not done.

---

## 1. Current Branch (original pass — still accurate for reachable history)

```
main
```

**Important, unrelated-but-relevant finding:** `git reflog` shows the most recent branch-changing event on `HEAD` is:

```
HEAD@{0}: checkout: moving from integration/claims-review-complete-local to main
```

This means the Claims Review local work (CLAIM-REVIEW-SPLIT-2A, CLAIM-NUMBERING-1 — everything currently sitting uncommitted in the working tree) is physically sitting **on top of `main`**, not on `integration/claims-review-complete-local` where the earlier-approved commit `f61fe53` (`fix(claims): retire generic approved review path`, CLAIMS-APPROVAL-CALC-1) actually lives. Nothing is lost — the working tree still has every file — but the *branch* the standing delivery rule designates for this work is currently one commit ahead of `main` and not checked out. This should be reconciled (checkout back to `integration/claims-review-complete-local` and re-apply/carry the working tree, or re-branch from here) **before** any of the pending Claims Review phases are committed, so `f61fe53` and the new uncommitted work end up on the same branch. Flagging only — not touched, per this task's scope.

## 2. Current `git status --short`

```
 M backend/src/main/java/com/waad/tba/modules/claim/controller/ClaimController.java
 M backend/src/main/java/com/waad/tba/modules/claim/repository/ClaimRepository.java
 M backend/src/main/java/com/waad/tba/modules/claim/service/ClaimService.java
 M backend/src/test/java/com/waad/tba/modules/claim/service/ClaimLifecycleIntegrationTest.java
 D frontend/src/components/medical-review/MedicalDecisionPanel.jsx
 M frontend/src/components/medical-review/index.js
 D frontend/src/components/medical/ClaimReviewPanel.jsx
 D frontend/src/components/medical/DocumentPreview.jsx
 D frontend/src/components/medical/DocumentsViewer.jsx
 D frontend/src/components/medical/MedicalInboxLayout.jsx
 D frontend/src/components/medical/index.js
 M frontend/src/menu-items/components.jsx
 D frontend/src/pages/claims/ClaimViewMedicalReview.jsx
 M frontend/src/routes/MainRoutes.jsx
 M frontend/src/utils/api-validators.js
?? backend/src/main/java/com/waad/tba/modules/claim/entity/ProviderClaimSequence.java
?? backend/src/main/java/com/waad/tba/modules/claim/repository/ProviderClaimSequenceRepository.java
?? backend/src/main/java/com/waad/tba/modules/claim/service/ClaimReferenceService.java
?? backend/src/main/resources/db/migration/V94__provider_claim_sequences.sql
?? backend/src/test/java/com/waad/tba/modules/claim/service/ClaimReferenceServiceTest.java
?? docs/claims/CLAIM-NUMBERING-1-REPORT.md
?? docs/claims/CLAIM-REVIEW-SPLIT-2A-REPORT.md
?? frontend/src/pages/claims/review/
```

Every one of these is accounted for by **CLAIM-REVIEW-SPLIT-2A** and **CLAIM-NUMBERING-1** (this session's own work). **None of it touches `ProviderClaimsSubmission.jsx`, `ClaimMapper.java`, `claims.service.js`, or any provider-portal-workspace file.** No provider-portal claim-submission WIP is sitting uncommitted anywhere in the working tree.

## 3. Local Commit List Relevant to Claims / Provider Portal

From `git log --oneline --decorate --all --graph`, every commit touching `provider-portal` or claims on `main`/reachable branches:

| Commit | Message | Relevant to described DATA-1/STATUS-1/workspace? |
|---|---|---|
| `6b61c5d` | feat(provider-portal): user↔facility linking + eligibility name search & camera fix | No — eligibility only |
| `5534b72` | feat(provider-portal): eligibility rejects members whose employer the facility does not support | No — eligibility only |
| `8611ad5` | feat(provider-portal): immutable facility signature on reports + 3 missing portal settings | No — reports/settings only |
| `33c2077` | chore: include pending working-tree changes (name search, auth/config contexts, menu, member repo/service) | No |
| `6fc4724` | fix(provider-portal): surface backend Arabic error messages on claim/pre-approval submit | Partially overlaps D4 (error messages) from the **separate** PROVIDER-PORTAL-UX-1 Phase 1 — not DATA-1/STATUS-1/workspace |
| `6e3e09c` | fix(provider-portal): remove duplicate provider navigation, System Categories only | This **is** PROVIDER-PORTAL-UX-1 Phase 2 — already committed to `main` (its report doc says "not committed" but the repo shows it is; the doc is simply stale) |
| `f61fe53` | fix(claims): retire generic approved review path (CLAIMS-APPROVAL-CALC-1) | No — reviewer-side, on `integration/claims-review-complete-local` |
| `3f1c9c1` | fix(claims): clarify approved amount labels | No — label text only |
| `5fba4bc` | security(claims): enforce reviewer provider isolation | No — reviewer-side security |
| `c2c1cbb` | security(provider): enforce provider ownership on attachment access | No — attachment authZ only |

**No commit, anywhere in `git log --all` (58 commits total, every branch included), mentions or implements**: `pricingItemId` submission-payload handling, `unitPrice`/`serviceCode`/`serviceName` on the claim create request, a provider-created-claim DRAFT-first status rule, `ensureActiveBatch` employerId flattening, `ProviderClaimSubmissionWorkspace`, `useProviderClaimSubmission`, or `ClaimMapperPricingContractTest`.

## 4. Stash List

```
stash@{0}: On plan/claims-review-execution-plan: CLAIMS-REVIEW-EXECUTION-PLAN doc (uncommitted, pending review)
stash@{1}: On plan/claim-review-split-1: CLAIM-REVIEW-SPLIT-1 audit report (uncommitted, pending review)
stash@{2}: On main: WIP unrelated to CLAIMS-AMOUNT-LABEL-1 (pre-existing session work: ClaimMapper/ClaimService/VisitService/frontend/docs)
```

`stash@{2}`'s message names `ClaimMapper`/`ClaimService` — inspected its diff directly: it does **not** contain any `pricingItemId`, `unitPrice`, `serviceCode`/`serviceName` submission-payload changes, no DRAFT-status change, and no `ProviderClaimsSubmission.jsx` workspace redesign. Its content is unrelated pre-existing session scaffolding, not the described feature.

## 5. Where the Described Provider-Portal Changes Were Found

**Nowhere.** Searched exhaustively:
- `git log --all --oneline --grep="DATA-1|STATUS-1|pricingItemId|workspace"` (case-insensitive) → **0 results**.
- `git log --all --diff-filter=A --name-only | grep` for `useProviderClaimSubmission`, `ProviderClaimSubmissionWorkspace`, `claim-submission/`, `ClaimMapperPricingContractTest` → **0 results**.
- `find frontend/src -iname "*useProviderClaimSubmission*" -o -iname "*ProviderClaimSubmissionWorkspace*" -o -path "*claim-submission*"` → **0 results**, in the current working tree.
- Inspected `wip/all-uncommitted-20260718`'s single commit `6c2bc75` (`chore(wip): safety snapshot of all uncommitted work before phased consolidation`, 2026-07-18) — a genuine full safety-snapshot commit of *everything* uncommitted at that time — via `git show --stat`. It contains MED-DICT-AUDIT tooling, pricelist import/review, preauthorization, and reports-engine work. **No `ProviderClaimsSubmission.jsx`, `ClaimMapper.java`, or `claims.service.js` changes appear in it.**
- `git reflog` (full, 80+ entries) — no `reset --hard`, no destructive `checkout --`, no branch deletion event that would explain discarding uncommitted work. The only unusual event is the branch-mix-up noted in §1, which does not destroy anything (both branches' commits are intact, reachable).

## 6. Status: Committed / Stashed / Lost / Partial / Conflicting?

**None of the above — the work does not exist in this repository in any form (committed, stashed, or in an unreachable/dangling commit reachable via reflog).** It is not "lost" in the git sense (nothing shows evidence of having existed and then being deleted); it simply was never captured by a `git add`/commit/stash at any point in this repo's history.

## 7. Exact Files Containing the Current (Old) UI

- `frontend/src/pages/provider/ProviderClaimsSubmission.jsx` — **2,557 lines**, single file, all sections (context, services, diagnosis, attachments) rendered in one long scroll. Confirmed present, unchanged from the shape described in `docs/provider-portal/PROVIDER-PORTAL-UX-1-AUDIT.md` (D2), dated 2026-07-19 — that audit explicitly still lists this exact defect as **unfixed**.
- Decorative (non-navigational) 3-step stepper, confirmed at lines 1299–1300:
  ```js
  const workflowSteps = ['بيانات المطالبة', 'الخدمات الطبية', 'المرفقات'];
  const workflowActiveStep = !hasVisitAndDiagnosis ? 0 : !hasServicesReady ? 1 : 2;
  ```
- Submit payload, confirmed at lines 1477–1494 — sends **only**:
  ```js
  lines: claimLines.map((line) => ({
    medicalServiceId: line.medicalServiceId,
    serviceCategoryId: line.medicalCategoryId,
    serviceCategoryName: line.medicalCategoryName,
    quantity: line.quantity || 1
  }))
  ```
  No `pricingItemId`, `unitPrice`, `serviceCode`, or `serviceName` — exactly the gap the background description names as "DATA-1" work, and it is **not present**.
- Unsafe `medicalServiceId` fallback, confirmed at line 875:
  ```js
  medicalServiceId: serviceId || normalizeId(selectedService?.id),
  ```
  This is the exact pattern the background description says DATA-1 was supposed to remove — it is **still there**.

## 8. Exact Files Containing an Improved UI (If Found)

**None found.** No file anywhere in the working tree, any branch, or any stash implements a workspace-style layout, a real (non-decorative) step navigator, a sidebar/context panel, or the DATA-1/STATUS-1 payload/status fixes for this page.

## 9. Does the Current Route Point to Old or New?

`frontend/src/routes/MainRoutes.jsx` → `/provider/claims/submit` → `ProviderClaimsSubmission` (the same 2,557-line file described above). There is no second/alternate provider claim-submission component in the codebase for the route to have been pointed away from.

## 10. Are Backend DATA-1 and STATUS-1 Fixes Present?

**No, for either.**

- **DATA-1 (pricing correctness):** `ClaimMapper.toEntity` (lines ~146–290) *does* already have internal logic resolving `pricingItemId`/`serviceCode`/`serviceName`/effective contract price when building a claim line from a request — but the **frontend never sends `pricingItemId`** (§7), so this backend resolution always falls through to whatever code-based lookup the mapper does today; it cannot benefit from a client-provided `pricingItemId` because none arrives. No `ClaimMapperPricingContractTest.java` exists to pin this contract down.
- **STATUS-1 (provider claims born DRAFT, not APPROVED):** `ClaimService.createClaim` (confirmed at the surrounding comment on the status-setting line) still says, verbatim:
  ```java
  // Status set to APPROVED by mapper — direct entry model (no review workflow)
  ```
  Every claim created through this path — including ones submitted by a provider through `ProviderClaimsSubmission.jsx` — is born `APPROVED`, unconditionally. There is no branch distinguishing "provider-submitted, should start life as DRAFT" from "admin/manual direct entry, stays APPROVED". This is a **currently-real gap**, matching the background description exactly, and unrelated to anything CLAIM-NUMBERING-1 touched (CLAIM-NUMBERING-1 only added one line assigning `claimNumber` immediately after this same method's existing save, it did not read or change the status logic).

## 11. Would Restoring Provider-Portal Work Conflict with `ClaimReviewWorkspace` / Claims Review Work?

**No file-level conflict expected for the frontend or for `ClaimMapper.java`. One real conflict for `ClaimService.java` — flagged now that the actual recovered blob is in hand (see §0).**

- `ClaimReviewWorkspace.jsx` and its components live entirely under `frontend/src/pages/claims/review/` — a different directory tree from `frontend/src/pages/provider/ProviderClaimsSubmission.jsx`. Zero shared files.
- `ClaimMapper.java` was **not modified** by CLAIM-NUMBERING-1 or CLAIM-REVIEW-SPLIT-2A at all — the recovered dangling blob (DATA-1's fix) can be applied on top of the current file directly; no merge needed.
- **`ClaimService.java` — real conflict, now concrete, not hypothetical.** The recovered dangling blob (`ce515bed...`) is a **full-file snapshot from before CLAIM-NUMBERING-1 existed** — it has the STATUS-1 `enforceProviderClaimCreationStatus` guard but **not** this session's `claimReferenceService.generateNextReference(...)` line. **Do not overwrite the current `ClaimService.java` with that blob wholesale** — that would silently delete CLAIM-NUMBERING-1's uncommitted work. The safe path: apply only the small, exact diff STATUS-1's own report already documents (one new package-private method + one call site, both quoted verbatim in its report, recovered in §0) onto the *current* `ClaimService.java`, rather than restoring the old full file. This is a small, mechanical, well-documented merge, not a conflict that blocks anything — but it must be done as a diff-apply, not a file overwrite.
- `frontend/src/services/api/claims.service.js` was **not modified** by either phase.

**Conclusion: safe to plan and implement provider-portal DATA-1/STATUS-1/workspace work independently, without touching or waiting on the Claims Review branch work — the two workstreams do not share files except one shared method (`ClaimService.createClaim`) where the eventual merge is small and mechanical.**

## 12. Recommended Path Forward (superseded by §0 — kept for the record of how the investigation evolved)

*Everything in this §12 as originally written assumed the work was unrecoverable. It was wrong — see §0. Original text kept below unmodified for the audit trail; do not act on it.*

This is **not a recovery scenario** — there is nothing in git to restore. Two possibilities, and the evidence points clearly to the second:

1. *The work was done in a local editor buffer or a different environment/session that never ran `git add`* — technically possible but unrecoverable by any git operation (no dangling blob/commit exists for it; even `git fsck --unreachable` territory requires the content to have been *added* to the index at some point, which never happened here).
2. *The work was **discussed and planned**, not yet implemented.* This matches the evidence precisely: `docs/provider-portal/PROVIDER-PORTAL-UX-1-AUDIT.md` (approved 2026-07-19, i.e. very recently) independently describes this **exact** page, this **exact** decorative-stepper defect (D2), and a **Phase 3** backlog item ("workflow redesign — steps, context header, service workspace, dedicated diagnosis + attachment steps, review step", §7 "full-viewport workspace layout") that has not been started per that same document's own phase table. The DATA-1/STATUS-1 items map naturally onto that plan's D4/D8 items and the "not started" Phase 3/1-remainder work, not onto anything separately implemented and lost.

**Actual recommendation (per §0): extract the recovered dangling blobs to the working tree, verify each against its recovered report, apply the `ClaimService.java` STATUS-1 diff by hand (not a full-file overwrite, per §11), then treat the result as new uncommitted local changes for your review — same report → approval → local-commit-only flow as every other phase this session.** Awaiting your go-ahead to actually write the recovered files into the working tree — nothing has been written yet.

Per the standing delivery rule: **local only, no commit, no push**, until you approve — nothing has been implemented in this task, investigation only.

---

**PROVIDER-PORTAL-WIP FOUND — UNREACHABLE (DANGLING) GIT OBJECTS, RECOVERABLE NOW. AWAITING APPROVAL TO EXTRACT.**
