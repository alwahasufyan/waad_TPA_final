# CLAIMS-FINANCIAL-SOURCE-OF-TRUTH-1 — Unify Claim Creation, Approval, Review, and Provider Payment Calculations

**Status: READY FOR REVIEW**

Local only. Nothing pushed. Nothing committed (per standing rule — commit is a separate, explicit step).

## 1. Root cause of 144 vs 135 (CLM-P001-000022)

Two independent, non-shared implementations existed for "what coverage% applies to this line":

- **Claim creation** (`ClaimMapper` → `CoverageEngineService.evaluateLine()` → `BenefitPolicyRuleService.findCoverageForService()`, `backend/.../benefitpolicy/service/BenefitPolicyRuleService.java:161-204`). Does a real 3-step resolution: exact category match → mirror-category fallback → root-category fallback → policy default. For this member/category it correctly found the matched rule: **75%**.
- **Claim approval** (`ClaimReviewService.processApprovalAsync()` → `AtomicFinancialService.calculateCostsWithAtomicDeductible()` → `CostCalculationService.calculateWeightedCopayFromLines()`, `CostCalculationService.java:160-205` → `BenefitPolicyCoverageService.batchGetCoveragePercentsByCategory()`, `BenefitPolicyCoverageService.java:1108-1141`). This method does an **exact-match-only** `HashMap.get()` on category id — no mirror/root-category fallback. For this same category, the lookup missed and silently fell through to the hardcoded `int coveragePercent = 80; // default` (`CostCalculationService.java:190`), producing `patientResponsibility = 200×0.20 = 40` instead of the real `50` (200×0.25).

`ClaimReviewService` then applied the (already-correct) contract-discount logic on top of that wrong patient share: `providerShare = 200 − 40 = 160`, discount 10% = 16, net = **144** — instead of the correct `providerShare = 200 − 50 = 150`, discount 10% = 15, net = **135**.

## 2. Where the 80% fallback happened / where 75% was correctly matched

- **80% fallback (wrong)**: `CostCalculationService.calculateWeightedCopayFromLines()`, line 190-192, via `BenefitPolicyCoverageService.batchGetCoveragePercentsByCategory()`.
- **75% correct match**: `BenefitPolicyRuleService.findCoverageForService()`, consumed by `CoverageEngineService.evaluateLine()` at claim creation, and persisted onto `ClaimLine.coveragePercentSnapshot` / used to compute `ClaimLine.patientShare`/`companyShare`/`companyShareBeforeDiscount` at that time (`ClaimMapper.java:288-333`).

## 3. Canonical method now used (Option 1 — sum authoritative persisted line values)

Rather than touching the risky, settlement-adjacent category-matching code (`BenefitPolicyCoverageService`/`CostCalculationService`), the fix makes **approval trust the already-correct per-line values** computed once at creation time — exactly the ticket's preferred Option 1.

`ClaimReviewService.processApprovalAsync()` (around line 309-330) now sums `patientShare` directly from the claim's live `ClaimLine` rows instead of calling into `CostCalculationService`'s independent (and buggy) category lookup:

```java
BigDecimal systemPatientCoPay;
if (currentLines != null && !currentLines.isEmpty()) {
    systemPatientCoPay = currentLines.stream()
            .map(l -> l.getPatientShare() != null ? l.getPatientShare() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
} else {
    systemPatientCoPay = breakdown.patientResponsibility() != null
            ? breakdown.patientResponsibility() : BigDecimal.ZERO;
}
```

`line.patientShare` is never touched by rejection decisions (only `refusedAmount`/`companyShare`/`providerDiscountAmount` split — see `ClaimService.applyLineFinancialDecision`, from the earlier CLAIM-REVIEW session), so it remains the single source of truth regardless of what happens during review. The fallback to `breakdown.patientResponsibility()` is kept only for the edge case of a claim with no lines (should not occur in practice, defensive only).

`CostCalculationService`/`BenefitPolicyCoverageService.batchGetCoveragePercentsByCategory()` were **not modified** — this keeps the fix minimal and outside the higher-risk settlement-adjacent code the previous ticket (CLAIMS-FINANCIAL-INTEGRITY-2) explicitly flagged as out of scope. The category-matching duplication itself still exists in the codebase (used elsewhere, e.g. deductible-adjacent paths), but no longer affects the approval amount.

## 4. Approval path changes

`backend/src/main/java/com/waad/tba/modules/claim/service/ClaimReviewService.java`:
- `processApprovalAsync()`: `systemPatientCoPay` now sourced from `sum(line.patientShare)` when lines exist (see above). Everything downstream (discount application, `netProviderAmount`, `approvedAmount`, `patientCoPay`, `coPayPercent`, `deductibleApplied`) is **unchanged** — it now simply operates on the correct input.

## 5. Report/payment double-discount fixes (two separate spots found and fixed)

### 5a. Backend: `ProviderSettlementReportService.generateReport()`

`backend/src/main/java/com/waad/tba/modules/claim/service/ProviderSettlementReportService.java` (lines ~193-215). `netProvider` is the sum of each claim's `netProviderAmount`, which is **already** net of that claim's contract discount. The old code re-applied `discountPercent` to `netProvider` a second time to compute `actualProviderShare` — the real field used for provider payment reconciliation.

Fixed: `actualProviderShare = netProvider` (as-is); `contractDiscountAmount` is now a display-only figure derived by **grossing** the net figure back up (`netProvider / (1 − discount%)  − netProvider`), never subtracted again.

### 5b. Frontend: `ProviderAccountsList.jsx` (claims/payments report screen)

`frontend/src/pages/settlement/ProviderAccountsList.jsx`. Two bugs:
- `getFacilityShareAmount()` re-applied `discount%` to `row.approvedAmount` (already net) to compute "نصيب المرفق" — double discount.
- `getCompanyShareAmount()` used a **hardcoded `COMPANY_SHARE_PERCENT = 10`** constant instead of the claim's real discount percent — for any claim with a discount ≠ 10%, "حصة الشركة" + "نصيب المرفق" didn't even sum back to "المستحق".

Fixed:
- `getFacilityShareAmount(row) = getPayableAmount(row)` (no re-discount).
- Renamed the misleading "حصة الشركة" (company's share) column to **"خصم العقد"** (contract discount) throughout (column header, print template, Excel export, summary card) — it was never actually a company-share concept, it was the discount amount, mislabeled.
- New `getContractDiscountAmount(row)` grosses the real per-row `providerDiscountPercent`/`getDiscountPercent(row)` back up from the already-net payable amount — dynamic, never hardcoded.
- The page-level "totals" footer's aggregate discount figure is summed from the currently-loaded page of claims (labeled "(الصفحة الحالية)") since the backend's `/financial-summary` aggregate endpoint has no discount-total field to source an exact filtered-range total from; `facilityShare` total is simply `payable` (already correct globally).

No hardcoded discount percent remains in either changed file.

## 6. Review UI

`frontend/src/pages/claims/review/components/ClaimReviewServiceLinesPanel.jsx` and `ClaimReviewFinancialSummary.jsx` already displayed backend-authoritative fields directly (`companyShareBeforeDiscount`, `providerDiscountAmount`, `companyShare`, `patientShare`, `refusedAmount`, `coveragePercent`) — no change needed there; verified during this ticket's audit.

One real bug found and fixed: `ClaimReviewWorkspace.jsx`'s `selectedApprovedAmount` (the client-side "محدد للاعتماد (غير محفوظ)" preview shown before "تمت المراجعة" is pressed) summed `service.totalAmount` (gross requested) instead of `service.companyShare` (the real net-of-discount company amount) — overstating the preview (e.g. 200 instead of 135) inconsistent with what approval would actually produce. Fixed to sum `service.companyShare` (falling back to `totalAmount` only if a line has no company-share data yet).

The previous "المعتمد" (top KPI) vs. the table's "حصة الشركة" mismatch (144 vs 135) required **no separate UI fix** — since both values are backend-sourced fields, they now agree automatically because the backend itself was fixed (§3).

## 7. Files changed

Backend:
- `backend/src/main/java/com/waad/tba/modules/claim/service/ClaimReviewService.java`
- `backend/src/main/java/com/waad/tba/modules/claim/service/ProviderSettlementReportService.java`
- `backend/src/test/java/com/waad/tba/modules/claim/service/ClaimReviewServiceTest.java` (new test added)
- `backend/src/test/java/com/waad/tba/modules/claim/service/ProviderSettlementReportServiceTest.java` (new file)

Frontend:
- `frontend/src/pages/claims/review/ClaimReviewWorkspace.jsx`
- `frontend/src/pages/settlement/ProviderAccountsList.jsx`

## 8. Tests added and results

New tests (all passing):
- `ClaimReviewServiceTest.processApprovalAsync_usesLinePatientShare_notCostCalculationServiceFallback` — reproduces the exact 200/75%/10% ticket scenario with a deliberately wrong `CostBreakdown` (as the buggy fallback would produce, `patientResponsibility=40`) mocked in, and asserts the real result (`patientCoPay=50`, `netProviderAmount=135`, `approvedAmount=135`) — proving the line values win, not the fallback.
- `ProviderSettlementReportServiceTest.generateReport_doesNotDoubleDiscount_actualProviderShareEqualsNetProviderAmount` — asserts `actualProviderShare=135` (not `121.50`, the double-discounted figure) and `contractDiscountAmount=15` (grossed-up correctly).
- `ProviderSettlementReportServiceTest.generateReport_zeroDiscount_noDiscountAmountAndFullShare` — 0% discount case.

Full backend test suite (`mvn -o test`, no `-Dtest` filter): **all pass**, including the pre-existing `processApprovalAsync_appliesDiscount_approvedAmountEqualsNetProviderAmount` test (a claim with no lines set — correctly falls back to `breakdown.patientResponsibility()`, confirming the no-lines edge case still works), `ClaimMapperFinancialIntegrityTest`, `CoverageEngineServiceCapTest`, `ClaimServiceLineDecisionTest`, `ClaimServiceReviewRoutingTest`, attachment authorization tests, and all others.

`mvn -o compile`: clean.
Frontend `npx eslint` on changed files: 0 errors (pre-existing formatting warnings only, unrelated to this change).
Frontend `npx vite build`: succeeds.

## 9. Runtime smoke test (local Docker, live)

Used the existing local dev claim **CLM-P001-000021** (id 1101, memberId 3115, "عادل خليل بالعيد الصابري"), which already carried the exact 200/75%/10% scenario from claim creation and had never been through `/approve`:

1. `GET /api/v1/claims/1101` (pre-approval, status SUBMITTED): line shows `coveragePercent=75`, `companyShareBeforeDiscount=150.00`, `providerDiscountAmount=15.00`, `companyShare=135.00`, `patientShare=50.00` — confirms creation-time engine already correct (unchanged by this fix).
2. `POST /api/v1/claims/1101/approve` as `reviewer_test` (MEDICAL_REVIEWER) — claim transitions to APPROVED.
3. `GET /api/v1/claims/1101` (post-approval): **`approvedAmount=135.00`, `netProviderAmount=135.00`, `patientCoPay=50.00`** — the correct values, not the buggy 144.00/40.00.
4. `GET /api/v1/reports/provider-settlements?providerId=1&claimNumber=1101`: **`netProviderAmount=135.00`, `actualProviderShare=135.00`, `contractDiscountAmount=15.00`** — confirms no double-discount in the real settlement report endpoint (135, not 121.50).

No fresh claim could be created in this smoke test session (`POST /api/v1/claims` requires a `visitId`, and creating a new visit was out of scope for this verification pass) — the existing claim above already carried the exact ticket scenario end-to-end, so it served as an equally valid, real proof.

## 10. Confirmations

- No hardcoded discount percent remains in any changed file (`ProviderAccountsList.jsx`'s `COMPANY_SHARE_PERCENT = 10` constant was removed entirely; `ProviderSettlementReportService.java` already sourced `discountPercent` dynamically from the active contract, only the *application* of it was wrong, now fixed).
- Provider discount is fully dynamic everywhere touched in this ticket.
- Review UI uses backend authoritative fields only (audited, one real bug found in the *client-side pre-approval preview* and fixed — see §6).
- Claim-level totals (`approvedAmount`/`netProviderAmount`) are pure sums of authoritative line values for the approval path (§3); `ProviderSettlementReportService`'s aggregate totals already summed per-claim authoritative fields and required no change beyond the discount fix.
- No production touched. No push. No PR opened. Nothing committed.

## 11. Rollback plan

All changes are additive/corrective within existing methods — no schema/migration changes, no new endpoints. To roll back: revert the four changed files (`ClaimReviewService.java`, `ProviderSettlementReportService.java`, `ClaimReviewWorkspace.jsx`, `ProviderAccountsList.jsx`) and delete the two new/extended test files. No data migration or cleanup is needed since no persisted data shape changed — only the *calculation* used at approval/report time.

## 12. Known, explicitly deferred item (not part of this ticket)

`CostCalculationService.calculateWeightedCopayFromLines()` / `BenefitPolicyCoverageService.batchGetCoveragePercentsByCategory()` still contain the flawed exact-match-only category lookup and the hardcoded 80% fallback. It is no longer *consulted* for the approval amount (bypassed per §3), but it is still used elsewhere (deductible-adjacent cost breakdown fields: `coPayPercent`, `insuranceAmount`, `deductibleApplied`). If those fields are ever surfaced or relied upon directly in the future, the same mirror/root-category fallback gap could still produce a wrong 80%-vs-real-rule mismatch there. Centralizing this into one canonical rule-matching method (as the ticket's "Allowed" section permits but does not mandate) was deliberately not done in this pass, to keep the fix minimal and low-risk, per Option 1.
