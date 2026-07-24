# CLAIMS-FINANCIAL-LEGACY-PATHS-AUDIT-1 — Legacy/Duplicate Financial Path Audit

**Report only. No code changed. Nothing committed. Nothing pushed.**

## 1. Files searched

Backend: `ClaimMapper`, `CoverageEngineService`, `ClaimReviewService`, `ClaimService`, `CostCalculationService`, `AtomicFinancialService`, `BenefitPolicyCoverageService`, `BenefitPolicyRuleService`, `ProviderSettlementReportService`, `ProviderReportsService`, `ClaimFinancialSummaryService`, `ProviderAccountService` (settlement ledger), `ClaimApiMapper`, `ClaimController`, `BenefitPolicyRuleController`, `BenefitPolicyRuleRepository`.

Frontend: `ClaimReviewWorkspace.jsx`, `ClaimReviewServiceLinesPanel.jsx`, `ClaimReviewFinancialSummary.jsx`, `ProviderAccountsList.jsx`, `ClaimBatchEntry.jsx` + `hooks/useCoverageLogic.js`, `ClaimBatchDetail.jsx`, `ClaimBatchManagement.jsx`, `provider/reports/ProviderClaimsReport.jsx`, `reports/AccountantProfitReport.jsx`, `provider-contracts/*` (view-only, checked for false positives).

Method: grepped for every call site of `batchGetCoveragePercentsByCategory`, `getCoverageForService`, `findBestRuleForService`, `findCoverageForService`, `calculateWeightedCopayFromLines`, `calculateCosts`/`calculateCostsWithAtomicDeductible`, plus a broad sweep for hardcoded `80`/`10%`/discount-reapplication patterns (`* (1 - discount`, `payable * discount`, etc.) across both codebases.

## 2. Duplicated financial paths found

### 2a. Still active — bypassed for money-of-record, but the code itself remains

**`CostCalculationService.calculateWeightedCopayFromLines()`** (`CostCalculationService.java:160-205`) → **`BenefitPolicyCoverageService.batchGetCoveragePercentsByCategory()`** (`BenefitPolicyCoverageService.java:1108-1141`). This is the exact flawed engine identified in CLAIMS-FINANCIAL-SOURCE-OF-TRUTH-1: exact-category-id match only, no mirror/root-category fallback, hardcoded `int coveragePercent = 80; // default` at `CostCalculationService.java:190`.

Still called from:
- `AtomicFinancialService.calculateCostsWithAtomicDeductible()` → `ClaimReviewService.processApprovalAsync()`. **The fix already made this irrelevant for money**: `systemPatientCoPay` is now sourced from `sum(line.patientShare)` whenever lines exist, so `breakdown.patientResponsibility()` (the tainted value) is never read in that case. Only `breakdown.deductibleApplied()` is still read from this breakdown — traced and confirmed **not** affected by the coveragePercent bug (deductible math only depends on `approvedNetBaseAmount` and `annualDeductible`/`deductibleMet`, both independent of coveragePercent).
- `ClaimService.getCostBreakdown()`/`getCostBreakdownDto()` (`ClaimService.java:1052-1079`) — a **read-only preview** endpoint, reachable live at `GET /api/v1/claims/{id}/cost-breakdown` (`ClaimController.java:481-487`). Grepped the entire frontend: **no `.jsx` component calls it** — `claims.service.js` defines `getCostBreakdown()` but it has zero callers in any page/component. Live but currently unused.
- `ClaimService.updateClaim()` (`ClaimService.java:507-511`) and `ClaimService.transitionStatus()` (`ClaimService.java:836-840`) both call `costCalculationService.calculateCosts(claim)` and **only log the result** (`log.info("💰 Cost calculation for approval: {}", ...)`) — the returned `costBreakdown` is never assigned to any claim field. The real `approvedAmount`/`netProviderAmount` for the `updateClaim()` direct-entry path comes from `updateEntityFromDto()` → `processEngineCalculations()` → `calculateClaimTotals()` (verified: pure sum of line values, the correct CLAIMS-FINANCIAL-INTEGRITY-2 path), which runs later in the same method. So this specific call is dead weight (wasted computation, log noise) but **provably does not affect any persisted or displayed value**.
- `ClaimService.transitionStatus()` itself (`ClaimService.java:812-869`): grepped for callers across the entire backend — **zero references from any controller or other service.** This whole method is unreachable dead code.

**Risk for this whole group: LOW.** The flawed lookup is still physically present in the codebase, but every remaining call site either (a) has its tainted output ignored/bypassed, (b) is a read-only preview with no live UI consumer, or (c) is unreachable dead code. None of them can currently write a wrong `approvedAmount`, `netProviderAmount`, or settlement/report payable value.

### 2b. Already fixed in an earlier, separate incident (verified still correct)

`ProviderAccountService.creditOnClaimApproval()`-area code (`ProviderAccountService.java:169-212`) contains its own inline comment documenting a **prior** double-discount bug ("FIX (2026-05-01): REMOVED DOUBLE DEDUCTION") — it previously re-applied contract discount and rejection on top of an already-net `netProviderAmount`. Verified the current code: `amount = companyApprovedShare = claim.getNetPayableAmount()`, used as-is, no re-computation. This is the real settlement-ledger credit path (money that actually moves), and it is correct today. Mentioned here only because it's the same bug *class* recurring a third time in this codebase's history — worth knowing the pattern has bitten this system before CLAIMS-FINANCIAL-INTEGRITY-2 existed.

### 2c. Checked and confirmed clean (no independent recompute)

- `ClaimMapper.calculateClaimTotals()` — pure sum of `ClaimLine` fields (`requestedTotal`, `refusedAmount`, `patientShare`, `companyShare`); this is the correct, already-fixed CLAIMS-FINANCIAL-INTEGRITY-2 path, used at claim creation/direct-entry update. Consistent with the SOURCE-OF-TRUTH-1 principle (sum-of-lines, no re-derivation).
- `ClaimApiMapper` — pure field-to-field DTO mapping, no arithmetic.
- `ProviderReportsService` — reads `netProviderAmount`/`approvedAmount` via SQL `COALESCE`, no recomputation.
- `ClaimFinancialSummaryService` — no discount/coverage arithmetic found.
- `ClaimBatchDetail.jsx` — computes a discount breakdown for display, but sources the **real** `providerDiscountPercent` per claim and derives it from `requestedAmount − patientCoPay` (a pre-discount gross figure), never from an already-net `approvedAmount` — correctly avoids double-discounting. No fix needed.
- `ClaimBatchEntry.jsx` / `useCoverageLogic.js` — the live coverage preview while building a batch entry calls `claimsService.calculateCoverageBulk()`, i.e. the canonical backend `/api/v1/claims/calculate-bulk` endpoint (the same correct engine used at claim creation) — not an independent client calculation. A local `fallbackPercent = policyInfo?.defaultCoveragePercent` is used only if that API call throws/returns nothing, and even then it's a **preview only**: the actual submitted claim is recomputed authoritatively server-side by `ClaimMapper.processEngineCalculations()` regardless of what the client preview showed. Low risk, self-correcting on save.
- `ProviderClaimsReport.jsx`, `AccountantProfitReport.jsx` — display backend-supplied fields directly, no recomputation.
- `ClaimReviewFinancialSummary.jsx`, `ClaimReviewServiceLinesPanel.jsx` — display backend-authoritative line/claim fields only (already audited in SOURCE-OF-TRUTH-1).

## 3. Answers to the 12 questions

1. **Is there now exactly one authoritative backend source for final claim-line financial values?** Yes for the fields that matter for money-of-record: `ClaimLine.patientShare/companyShare/companyShareBeforeDiscount/providerDiscountAmount/refusedAmount` are set once at creation by `CoverageEngineService`/`ClaimMapper`, never independently re-derived elsewhere for persisted values. Claim-level totals are pure sums of these (both in `ClaimMapper.calculateClaimTotals` and now `ClaimReviewService.processApprovalAsync`).
2. **Does approval now avoid duplicate coverage lookup and use persisted line values?** Yes, for `patientCoPay`/`approvedAmount`/`netProviderAmount` — confirmed in §2a.
3. **Are reports reading persisted/API values, or recalculating?** All reports checked read persisted values. The one exception (`ProviderAccountsList.jsx`) was already fixed in SOURCE-OF-TRUTH-1.
4. **Any hardcoded discount values (e.g. 10%) left in active paths?** None found in any *active, money-affecting* path. `CostCalculationService.java:190`'s hardcoded `80` remains present but is bypassed (§2a).
5. **Any places still falling back to policy default coverage (80%) without checking the specific rule first?** Yes — `CostCalculationService.calculateWeightedCopayFromLines()` itself still has this flaw. It is simply no longer consulted for money-of-record (§2a). This is the literal, unresolved root of the bug family; only its blast radius has been contained.
6. **Are `CostCalculationService`/`BenefitPolicyCoverageService` still using their own category matching?** Yes, unchanged, still present in the codebase.
7. **Where used, what fields can they affect?** Only `CostBreakdown.deductibleApplied()` is still consumed downstream (in `processApprovalAsync`), and it's verified independent of the coveragePercent bug. `coPayAmount`/`insuranceAmount`/`coPayPercent`/`patientResponsibility` from this breakdown are computed but no longer used for any persisted claim field when lines are present.
8. **Can those remaining paths affect approvedAmount / netProviderAmount / settlement payable / provider report payable / review UI / eligibility preview only?** Only the last: eligibility/cost-breakdown **preview** (`GET /{id}/cost-breakdown`), and only if it's ever wired to a UI (it currently isn't). None of the others are affected.
9. **Are deductible and insuranceAmount still calculated by an old path?** `deductibleApplied` — yes, by the old `CostCalculationService`, but verified uncoupled from the coveragePercent bug. Note: `getDeductibleMetThisPeriod()`/`getOutOfPocketSpentThisPeriod()` are both hardcoded MVP stubs returning `BigDecimal.ZERO` always (`CostCalculationService.java:222-224, 230-232`) — a **separate, pre-existing limitation** (deductible tracking never accumulates across claims), not part of this bug family, but worth flagging as its own known gap. `insuranceAmount` is computed by the old path but not read anywhere outside `CostBreakdown` itself in the affected flows.
10. **Any frontend-only formulas that can show different numbers from backend authoritative values?** None found beyond what SOURCE-OF-TRUTH-1 already fixed.
11. **Remaining risk level:**
    - **High: none identified.**
    - **Medium:** `GET /{id}/cost-breakdown` endpoint (`ClaimService.getCostBreakdown`) — live, reachable, uses the flawed engine, currently has zero UI consumers. Risk is latent, not active.
    - **Low:** the flawed lookup methods themselves (still exist, used only for the unaffected deductible field); `ClaimService.updateClaim()`/`transitionStatus()`'s discarded/dead `calculateCosts()` calls (wasted computation, no effect); `transitionStatus()` entirely unreachable.
12. **New ticket now, or follow-up after push?** **Follow-up is safe.** Nothing found in this audit can currently produce a wrong `approvedAmount`, `netProviderAmount`, settlement credit, or report payable value. The two verified fixes (SOURCE-OF-TRUTH-1) are sufficient to push now.

## 4. Recommendation

- **Must fix before push:** nothing found.
- **Safe as follow-up (`CLAIMS-COVERAGE-RULE-MATCHING-2`, already recorded):** centralize `BenefitPolicyCoverageService.batchGetCoveragePercentsByCategory()` to reuse `BenefitPolicyRuleService.findCoverageForService()`'s mirror/root-category fallback, so `coPayAmount`/`insuranceAmount`/`coPayPercent`/deductible-adjacent fields become correct too (currently unused for money, but wrong if ever surfaced).
- **Dead code cleanup later (new, low-priority ticket, e.g. `CLAIMS-DEAD-CODE-CLEANUP-1`):**
  - Remove the discarded `costCalculationService.calculateCosts(claim)` calls in `ClaimService.updateClaim()` (line 508) and `ClaimService.transitionStatus()` (line 838) — computed and logged, never used.
  - Remove or wire up `ClaimService.transitionStatus()` — currently has zero callers anywhere in the backend.
  - Decide whether `GET /{id}/cost-breakdown` should be deleted (no UI consumer) or actually built into a UI — if the latter, it must go through the CLAIMS-COVERAGE-RULE-MATCHING-2 fix first.
  - Separately (not part of this bug family): `CostCalculationService.getDeductibleMetThisPeriod()`/`getOutOfPocketSpentThisPeriod()` are permanent MVP stubs returning zero — deductible/out-of-pocket tracking does not actually accumulate across claims. Flag as its own future ticket if deductible policies are in active use.

## 5. Proposed follow-up ticket list

1. `CLAIMS-COVERAGE-RULE-MATCHING-2` — Unify Coverage Rule Matching Across Cost Breakdown Services (already recorded in memory from the previous ticket).
2. `CLAIMS-DEAD-CODE-CLEANUP-1` — Remove discarded cost-calculation calls, unreachable `transitionStatus()`, and decide the fate of the unused `/cost-breakdown` preview endpoint.
3. `CLAIMS-DEDUCTIBLE-TRACKING-1` (only if deductible policies are actually in use) — implement real deductible-met and out-of-pocket-spent tracking across a member's claim history; currently permanently zero.

## 6. Confirmations

- No code was changed during this audit.
- No commit was made.
- No push was done.

---

**CLAIMS-FINANCIAL-LEGACY-PATHS-AUDIT-1 READY FOR DECISION**
