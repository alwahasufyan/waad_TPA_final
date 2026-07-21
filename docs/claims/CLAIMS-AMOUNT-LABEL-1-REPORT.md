# CLAIMS-AMOUNT-LABEL-1 — Unify "المعتمد" Label Across Claim Screens

**Branch:** `fix/claims-approved-amount-labels` (from clean `main`)
**Status:** Labels fixed, verified, ready for review. **Not committed. Not pushed.**
**Scope:** Arabic labels/explanatory text only. No calculation, no calculation-source, no status, no settlement logic changed.

---

## 1. Root issue

The Arabic word "المعتمد" (approved) was used as a standalone label for **two, and in one screen three, numerically different concepts**:

1. The company's coverage/share **before** the provider contract discount is subtracted (e.g. 160).
2. The final net amount **payable to the provider after** the contract discount (e.g. 144).
3. (One screen only) a live, in-progress **sum of currently-selected-for-approval service lines**, gross of co-pay/discount/rejection entirely — not a persisted financial figure at all.

None of these are wrong values in isolation. The confusion is that reviewers and finance saw the same word "المعتمد" attached to whichever of these three a given screen happened to compute, with no way to tell which one they were looking at.

## 2. Audited screens

| Screen | File | Finding |
|---|---|---|
| Claim review page | `frontend/src/components/medical/ClaimReviewPanel.jsx` | "المبلغ المعتمد" bound to `claim.approvedAmount` (intended-final value) |
| Claim detail / medical review | `frontend/src/pages/claims/ClaimViewMedicalReview.jsx` | "الإجمالي المعتمد" / "إجمالي المبلغ الموافق عليه" bound to `selectedApprovedAmount` — a **live, gross, in-progress selection tally**, not the persisted claim amount |
| Batch detail (table + Excel + footer chips) | `frontend/src/pages/claims/batches/ClaimBatchDetail.jsx` | **Core bug** — "المعتمد" column bound to a frontend-only helper `getApprovedAfterDiscount()` that, despite its name, **never subtracts the discount** (= gross − copay only); "المستحق" column (`getDueAfterRefused()`) also never subtracts the discount. The already-computed, genuinely-correct `claim.netProviderAmount` (post-discount) is computed into `totals.paid` but was **never rendered anywhere on this screen** |
| Batch entry (creation form) | `frontend/src/pages/claims/batches/ClaimBatchEntry.jsx` | Line-level table already correctly labels company share "حصة الشركة" (fine, untouched); only its CSV export header "المبلغ المعتمد" (bound to claim-level `approvedAmount`) needed the same wording fix as elsewhere |
| Batch management (KPI cards) | `frontend/src/pages/claims/batches/ClaimBatchManagement.jsx` | "المعتمد" bound to backend `totalApprovedAmount` aggregate (intended-final) |
| Claims report table | `frontend/src/components/reports/claims/ClaimsTable.jsx` | Line-level table already correctly labels "حصة الشركة" (fine, untouched); claim-level column "المبلغ المعتمد" bound to `approvedAmount` needed the fix |
| Claims report export | `frontend/src/pages/reports/claims/index.jsx` | Excel export row "المبلغ المعتمد" bound to `approvedAmount` |
| Provider claim report | `frontend/src/pages/provider/reports/ProviderClaimsReport.jsx` | "المبلغ الموافق" bound to `approvedAmount` |
| Financial reports (settlements tab) | `frontend/src/pages/reports/FinancialReports.jsx` | Already correctly shows `approvedAmount` and `settledAmount` (`netProviderAmount`) as **two separate columns** — a good existing pattern; only relabeled `approvedAmount`'s header/aggregate text for consistency, left `settledAmount`'s "المبلغ المسدد" untouched |
| Settlement — provider account | `frontend/src/pages/settlement/ProviderAccountView.jsx` | "إجمالي المعتمد" bound to `totalApproved`, itself sourced from `Claim.getNetPayableAmount()` (confirmed post-discount) |
| Settlement — provider settlement report | `frontend/src/pages/reports/ProviderSettlementReport.jsx` | Genuinely two-tier problem — see §3 below |
| Settlement Excel export | `frontend/src/utils/settlementExcelExport.js` | "إجمالي المعتمد" header, same `totalApproved` aggregate as ProviderAccountView |
| Print view | `frontend/src/pages/claims/batches/components/BatchPrintReport.jsx` | **Already correct** — uses "صافي"/"Net" wording, not the ambiguous bare "المعتمد". No change needed. |
| Pre-authorization screens (`PreApprovalsList.jsx`, `PreApprovalsTable.jsx`, `reports/pre-approvals/*`) | — | **Out of scope.** These show PreAuthorization amounts, a different business domain that happens to reuse the same Arabic word. Per this phase's Global Rules ("never mix... PreAuthorization... unless the phase explicitly includes them"), left untouched. |

## 3. Field meaning table (backend, existing values only — nothing invented)

| Field | Entity/DTO | Computed by | Before/after provider discount |
|---|---|---|---|
| `requestedAmount` | `Claim` (persisted) | Sum of line requested totals | n/a (gross) |
| `patientCoPay` | `Claim` (persisted) | Member's share | n/a |
| `approvedAmount` | `Claim` (persisted) | **Design intent: post-discount, forced equal to `netProviderAmount`** in every calculation path found except one (see §7) | Intended: **after** |
| `netProviderAmount` | `Claim` (persisted) | `calculateFields()` / `ClaimReviewService.requestApproval` / `ClaimMapper.calculateClaimTotals` — all compute this as gross − copay − discount − refused | **After** (confirmed in every formula read) |
| `providerDiscountAmount` | `ClaimViewDto` (derived in mapper, not persisted) | `(requestedAmount − patientCoPay) × discountPercent / 100` | n/a (this *is* the discount itself) |
| `ClaimLineDto.companyShare` / `.approvedAmount` | Line-level | Both = `finalPayable`, which already has the line's share of the contract discount subtracted in `ClaimMapper` | **After** |
| `ProviderSettlementReportDto.totalApprovedAmount` | Settlement report DTO | `SUM(line.approvedAmount)` — line-level, already post-discount | **After** |
| `ProviderSettlementReportDto.netProviderAmount` | Settlement report DTO | `totalApproved − totalPatientShare` — **does NOT subtract the contract discount at this DTO's level**, despite the name | **Before** (misleading name — see §7) |
| `ProviderSettlementReportDto.actualProviderShare` | Settlement report DTO | `netProviderAmount − contractDiscountAmount` | **After** — this is the true final figure, but it is **not shown on any frontend screen found** |
| `Claim.getNetPayableAmount()` | Entity method | `netProviderAmount` if set, else `approvedAmount` | After (used by `ProviderAccountService` for the actual account credit — the account/settlement totals are correct) |

No field was renamed. No formula was changed. This table only documents what already exists.

## 4. Final label mapping applied

Per the approved business decision (labels only, values unchanged):

| Old label (ambiguous) | New label | Screens |
|---|---|---|
| "المعتمد" / "المبلغ المعتمد" (bound to a value that is **pre-discount**) | "حصة الشركة قبل الخصم" | `ClaimBatchDetail.jsx` (column, Excel header, footer chip) |
| "المستحق" (bound to a value that subtracts refused but **not** the discount) | "المستحق قبل خصم العقد" | `ClaimBatchDetail.jsx` (column, footer chip); Excel `paid` column → "المستحق للمزود قبل خصم العقد" |
| "المبلغ المعتمد" / "المعتمد" / "المبلغ الموافق" (bound to a value that is **intended to be post-discount/final**) | "المعتمد النهائي" | `ClaimReviewPanel.jsx`, `ClaimBatchEntry.jsx` (export), `ClaimBatchManagement.jsx`, `ClaimsTable.jsx`, `reports/claims/index.jsx`, `ProviderClaimsReport.jsx`, `FinancialReports.jsx` (column + export dict + 2 aggregate cards) |
| "إجمالي المعتمد" | "إجمالي المعتمد النهائي" | `ProviderAccountView.jsx`, `settlementExcelExport.js` |
| "صافي المستحق للمرفق" / "مستحق للمرفق" / "المبلغ المعتمد (Net)" (bound to the settlement DTO's pre-discount `netProviderAmount`) | "المستحق للمرفق قبل خصم العقد" (for the pre-discount field) / "المعتمد النهائي (Net)" (for the genuinely post-discount `totalApprovedAmount`/line `approvedAmount`) | `ProviderSettlementReport.jsx` (screen card, Excel summary card, per-line and per-claim export rows) |
| "الإجمالي المعتمد" / "إجمالي المبلغ الموافق عليه" (bound to a **live selection tally**, not a persisted amount) | "إجمالي الخدمات المختارة للاعتماد" | `ClaimViewMedicalReview.jsx` (2 of 3 occurrences — see §6 for the one left unchanged) |

## 5. Exact files changed

```
frontend/src/components/medical/ClaimReviewPanel.jsx
frontend/src/components/reports/claims/ClaimsTable.jsx
frontend/src/pages/claims/ClaimViewMedicalReview.jsx
frontend/src/pages/claims/batches/ClaimBatchDetail.jsx
frontend/src/pages/claims/batches/ClaimBatchEntry.jsx
frontend/src/pages/claims/batches/ClaimBatchManagement.jsx
frontend/src/pages/provider/reports/ProviderClaimsReport.jsx
frontend/src/pages/reports/FinancialReports.jsx
frontend/src/pages/reports/ProviderSettlementReport.jsx
frontend/src/pages/reports/claims/index.jsx
frontend/src/pages/settlement/ProviderAccountView.jsx
frontend/src/utils/settlementExcelExport.js
```

12 files, 31 lines changed (31 insertions / 31 deletions — every change is a same-line string replacement, no lines added or removed structurally). No backend files touched.

## 6. Before/after label examples

```
ClaimBatchDetail.jsx column headers
  Before: المعتمد            (= gross − copay, i.e. BEFORE discount)
          المستحق             (= gross − copay − refused, still BEFORE discount)
  After:  حصة الشركة قبل الخصم
          المستحق قبل خصم العقد

ClaimReviewPanel.jsx summary card
  Before: المبلغ المعتمد: 144.00
  After:  المعتمد النهائي: 144.00

ClaimViewMedicalReview.jsx live selection banner
  Before: الإجمالي المعتمد: 200.00
  After:  إجمالي الخدمات المختارة للاعتماد: 200.00

ProviderSettlementReport.jsx summary card
  Before: صافي المستحق للمرفق: 160.00   (DTO field is actually pre-discount)
  After:  المستحق للمرفق قبل خصم العقد: 160.00
```

**One instance deliberately left unchanged:** `ClaimViewMedicalReview.jsx` line 1046-1048 ("المبلغ الموافق عليه", bound to `selectedApprovedAmount || normalizedClaim.approvedAmount || 0`) is a fallback expression that shows the live selection tally *while actively reviewing* but falls back to the persisted final `approvedAmount` once a decision exists. Since the same label legitimately covers two different, correct meanings depending on component state, and neither of the two candidate relabels ("إجمالي الخدمات المختارة" or "المعتمد النهائي") would be accurate in both states, this one was left as a neutral, non-"المعتمد"-worded label. Flagged here rather than silently forced into the general pattern.

## 7. Root-cause finding NOT fixed — flagged separately, per instructions

**A live investigation of `Claim.approvedAmount`'s actual value in different backend code paths found this is not purely a display/label problem in every case:**

- `ClaimReviewService.requestApproval` (POST `/claims/{id}/approve`, the split-phase async approval flow) and `ClaimMapper.calculateClaimTotals` (direct-entry creation) both compute `approvedAmount` and `netProviderAmount` together and set them **equal**, post-discount. In these paths, "160 vs 144" cannot happen — both fields hold 144.
- `ClaimReviewService.reviewClaim` (PUT `/claims/{id}/review`, the older "generic review action") does `claim.setApprovedAmount(dto.getApprovedAmount())` — setting `approvedAmount` **directly from the reviewer's raw input** — and **never touches `netProviderAmount` in that method at all**. If a claim is approved through this path, `approvedAmount` could hold the reviewer's pre-discount figure (160) while `netProviderAmount` retains whatever value it held before (stale, zero, or from an earlier calculation) — a genuine data-path inconsistency, not merely a label issue.
- Separately, `ProviderSettlementReportDto.netProviderAmount` is defined at the DTO level as **pre-discount** (`totalApproved − totalPatientShare`, no discount subtracted), the opposite of what the `Claim` entity's field of the same name means (post-discount). The DTO's true post-discount figure, `actualProviderShare`, is computed but **never surfaced on any frontend screen** found in this audit.

**Per this phase's explicit instruction — "if a calculation mismatch is discovered, stop and report it separately before changing math" — neither of these was touched.** They are flagged here as candidates for a separate, dedicated investigation (suggested name: `CLAIMS-APPROVAL-CALC-1`), which should determine: (a) whether `PUT /claims/{id}/review` is still reachable/used for approvals in practice or is legacy, and if reachable, whether it should be updated to also compute `netProviderAmount` consistently; (b) whether `ProviderSettlementReportDto.netProviderAmount` should be renamed or its true final `actualProviderShare` surfaced on the settlement report screen.

## 8. Verification that values did not change

- Every edit in this phase is a same-line Arabic string replacement inside an already-existing `label`/`header`/`title`/`headerName`/Typography text node. No JSX structure, no field binding, no formula, no function, no default value, no sort/filter logic, and no conditional branch was touched.
- `git diff --stat`: 12 files, 31 insertions / 31 deletions — the insertion count exactly equals the deletion count on every file, consistent with pure line-for-line text swaps.
- Manually re-checked each edited binding (`claim.approvedAmount`, `claim.netProviderAmount`, `getApprovedAfterDiscount(claim)`, `getDueAfterRefused(claim)`, `totals.covered`, `reportData.netProviderAmount`, `reportData.totalApprovedAmount`, `summaryData.totalApprovedAmount`) to confirm none of the underlying JavaScript expressions were altered — only their adjacent label text.

## 9. Tests / lint / build results

```
git diff --check         → clean (only pre-existing LF/CRLF notices, no whitespace errors)
npx eslint <12 files>     → 0 errors, 3716 warnings (all pre-existing prettier-style warnings, none introduced by this change)
npx vite build            → ✓ built in 1m 20s, no errors
```

No backend files were touched in this phase, so no backend test run was required (the task's own rule: "run focused backend tests if DTO/report changes are made" — none were made; all changes are Arabic strings inside frontend JSX/JS files).

Checklist from the task:
1. Claim list label correct — ✅ (`ClaimsTable.jsx`, `reports/claims/index.jsx`)
2. Claim detail label correct — ✅ (`ClaimViewMedicalReview.jsx`)
3. Batch detail label correct — ✅ (`ClaimBatchDetail.jsx`, core fix)
4. Review page label correct — ✅ (`ClaimReviewPanel.jsx`)
5. Print/export labels correct where affected — ✅ (Excel exports in `ClaimBatchDetail.jsx`, `ClaimBatchEntry.jsx`, `ProviderSettlementReport.jsx`, `settlementExcelExport.js`; print view `BatchPrintReport.jsx` was already correct, untouched)
6. No financial value changed — ✅ (§8)
7. No claim status changed — ✅ (no status-related code touched)
8. No settlement value changed — ✅ (only settlement *screen labels* changed; `ProviderAccountService`/settlement crediting logic untouched)
9. No backend calculation changed — ✅ (zero backend files modified)

## 10. Unresolved risks

1. **(Flagged, not fixed — see §7)** `ClaimReviewService.reviewClaim`'s generic review path can leave `approvedAmount`/`netProviderAmount` genuinely divergent for a claim, independent of any label. Recommend a dedicated follow-up ticket.
2. **(Flagged, not fixed — see §7)** `ProviderSettlementReportDto.netProviderAmount` naming collides in meaning with the `Claim` entity field of the same name (one is pre-discount, the other post-discount). Recommend a naming/exposure review as part of the same follow-up.
3. `ClaimBatchDetail.jsx`'s helper functions `getApprovedAfterDiscount()` and `getDueAfterRefused()` are misleadingly named in code (neither actually applies the discount despite the former's name implying it does). Left unchanged in this phase since renaming a function is unrelated to the label-only task and touches more call sites; flagged for a small follow-up cleanup.
4. `ClaimBatchDetail.jsx` computes `totals.paid` (the genuinely correct, post-discount total) but never displays it anywhere on that screen. No new UI element was added in this phase (out of scope for a label-only fix — adding a visible total is a UI content change, not a label rename), but this is the most direct way to give reviewers visibility into the true final total on this specific screen. Recommend as a small, explicit follow-up (not a calculation change — the value is already computed correctly, only needs a `<Chip>`/column to display it).
5. `frontend/src/locales/ar.js` (`approvedAmount: 'المبلغ المعتمد'`) and `frontend/src/utils/exportUtils.js` (shared label dictionary) were **found but deliberately not edited** — both are generic, shared dictionaries reused across claims, pre-authorizations, and settlements/accounting screens (confirmed via usage search: `exportUtils.js` is imported by `PreAuthAuditPage.jsx`, `AccountantProfitReport.jsx`, `pages/reports/pre-approvals/index.jsx`, `PaymentsManagement.jsx`, `ProviderAccountsList.jsx`, among others). Editing them risks silently relabeling unrelated PreAuthorization/accounting screens, which this phase's Global Rules explicitly forbid mixing in. Left untouched; flagged for a scoped follow-up if the team wants full consistency there too.

## 11. Rollback plan

Every change in this phase is an isolated, single-line Arabic string literal edit with no logic dependency. Rollback is a plain `git checkout main -- <file>` per file, or `git revert` of the phase commit once merged — no migration, no data change, no cache invalidation, and no coordinated backend/frontend rollback is required since zero backend files were touched.

## 12. Confirmation: no unrelated module changed

`git status --short` for this phase shows exactly the 12 files listed in §5 — no claim financial/calculation service, no settlement service, no PreAuthorization file, no provider portal submission flow, no taxonomy/classification file, and no monitoring/backup/real-environment file was touched. Pre-existing unrelated WIP from earlier work (`ClaimMapper.java`, `ClaimService.java`, `VisitService.java`, `compose.local.yaml`, various provider-portal frontend files) was stashed before this branch was created from clean `main` and is **not part of this branch or this diff**.

---

**Do not commit. Do not push.**

## CLAIMS-AMOUNT-LABEL-1 READY FOR REVIEW

STOP.
