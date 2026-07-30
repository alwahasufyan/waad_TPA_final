# DOCTOR-NOTES-BENEFIT-CAPS-REGRESSION-1 — Reproduce Doctor Notes on Benefit Caps, Coverage, Copay, and Service Classification

**Test/report only. No fix code written beyond the regression tests themselves. Nothing pushed. Nothing committed.**

## 1. Extracted doctor notes as structured requirements

The 12 business problems from the ticket, restated generically (company/bank names ignored per instruction):

| # | Requirement |
|---|---|
| 1 | Member annual limit (e.g. 50,000/60,000) must be enforced against prior approved usage; a claim pushing past it must not be silently approved in full. |
| 2 | MRI category cap (e.g. 1,500) must reduce the approved amount and populate cap snapshot fields with a clear refusal reason. |
| 3 | Lab tests / doctor fees must resolve to their **own** category rule, not an unrelated default. |
| 4 | Delivery (natural/C-section) cap (e.g. 4,000) must be enforced. |
| 5 | Physiotherapy cap (e.g. 10,000) must accumulate usage **across multiple claims**, not just within one claim. |
| 6 | General annual cap (e.g. 60,000) must warn/refuse, mirroring #1. |
| 7 | Dental operations cap (e.g. 2,000) must be enforced. |
| 8 | Different dental **services** (not just the category) must be able to carry different coverage percentages — no single generic dental %. |
| 9 | Accommodation/inpatient coverage and surgery coverage are different rules and must never be conflated via fallback. |
| 10 | Admission days (quantity) must multiply into `requestedTotal` before coverage/cap math — no single-day miscalculation. |
| 11 | Provider (dental) price-list import: ambiguous/unknown rows must go to review, not silently enter financial approval. |
| 12 | An already-exhausted cap must refuse a new claim's amount in full, not silently approve it. |

## 2. Clear vs unclear from the ticket's own text

All 12 items were clear enough to reproduce as controlled scenarios — the ticket itself already generalized the doctor's handwriting into concrete, testable business rules (amounts, categories, expected behavior). Nothing was too ambiguous to test. The one genuinely **unclear** item is **#9** (accommodation vs surgery): the ticket doesn't say whether "surgery" is a *sibling* category of accommodation, a *child* of it, or a category that may not exist yet in this environment's real taxonomy at all — this needed a code-level check, not a live-DB check (see §5).

## 3. Test scenarios created and results

New file: `backend/src/test/java/com/waad/tba/modules/claim/service/DoctorNotesBenefitCapsRegressionTest.java` (10 tests), plus one test added to the existing `backend/src/test/java/com/waad/tba/modules/benefitpolicy/service/BenefitPolicyCoverageServiceTest.java`.

`mvn -o test -Dtest="DoctorNotesBenefitCapsRegressionTest,BenefitPolicyCoverageServiceTest"` → **all pass, exit 0.**

| Test | Scenario | Result |
|---|---|---|
| `mriCapExceeded_shouldPersistLimitRefusalAndSnapshots` | #2 | **PASS** — cap enforced, snapshot fields populated, clear Arabic reason |
| `deliveryCapExceeded_shouldApplyCategoryCap` | #4 | **PASS** |
| `physiotherapyCapAccumulation_shouldRejectExcessAcrossClaims` | #5 | **PASS** — simulated prior 9,200/10,000 usage correctly reduces headroom |
| `dentalOperationsCapExceeded_shouldRejectExcess` | #7 | **PASS** |
| `accommodationAndSurgery_shouldUseDifferentRules` | #9 | **PASS** — two categories, two independent rules, no conflation in the engine itself |
| `admissionQuantityDays_shouldAffectRequestedTotal` | #10 | **PASS** — 500 × 5 days = 2,500, no single-day math |
| `admissionQuantityDays_capAppliesToTotalNotSingleDay` | #10 | **PASS** — cap correctly applied to the multiplied total |
| `exhaustedCap_shouldRefuseOrShiftExcess` | #12 | **PASS** — remaining floored at 0, new amount fully refused |
| `dentalCoveragePercent_shouldDifferByCategory` | #8 (category granularity) | **PASS** — two dental *categories* get two different %s |
| `validateAmountLimits_ExactlyExhausted_stillBlocksNewAmount` (added) | #1/#6 edge case | **PASS** — remaining=0 still blocks a new positive amount |

**Not written as executable tests** (documented via direct code citation instead, per the ticket's fallback instruction):
- **#3** (lab/doctor fees resolving correctly) — this is the same category-resolution mechanism already exercised by every other cap test above; no separate test adds signal.
- **#8, true service-level differentiation** — not testable as a *passing* scenario because the capability doesn't exist (see §4). Writing a test asserting current (broken) behavior would just pin down "V228 removed this," which is already documented in the source itself.
- **#11, ambiguous price-list import row** — requires constructing an Apache POI workbook fixture; not written this pass. The gap is confirmed directly from source (§4) with exact file:line evidence instead.

## 4. Exact current behavior per scenario, and classification

| # | Area | Current behavior | Classification |
|---|---|---|---|
| 1 | Annual limit | **Real, throwing enforcement** in `BenefitPolicyCoverageService.validateAmountLimits()` (real DB sums, not stubs), called from `ClaimReviewService.processApprovalAsync()`. **But only at approval time** — not at claim creation/submission (`ClaimService`/`ClaimMapper.processEngineCalculations()` never call it). Two claims submitted in parallel could both pass creation-time checks and only the first approval would catch the overrun. | **Reproducible gap** (timing, not a silent-approval bug) |
| 2 | MRI cap | Enforced and persisted correctly — confirmed by passing test. | **Already fixed / working** |
| 3 | Lab/doctor fees category resolution | Same category-resolution path as #2, works correctly when a category rule exists. | **Already fixed / working** |
| 4 | Delivery cap | Enforced correctly — confirmed by passing test. | **Already fixed / working** |
| 5 | Physiotherapy accumulation | Real cross-claim JPQL aggregation in `BenefitPolicyRuleService.checkUsageLimit()` (distinct from the known-stub `CostCalculationService.getDeductibleMetThisPeriod()`). Confirmed by passing test. **However**, its status filter (`excludeStatuses` = REJECTED only) differs from item #1's filter (only counts APPROVED/SETTLED/BATCHED) — the two "how much has this member used" queries can disagree. | **Already fixed / working, but flag the filter inconsistency as a reproducible gap** |
| 6 | General annual cap | Same mechanism as #1. | Same as #1 |
| 7 | Dental operations cap | Enforced correctly — confirmed by passing test. | **Already fixed / working** |
| 8 | Dental service-level % | `BenefitPolicyRuleService.validateTargetXor()` **throws** if a rule targets `medicalServiceId` — service-level rules were deliberately removed ("Since V228 all rules are category-level," per source comments in `BenefitPolicyRule.java`/`BenefitPolicyRuleService.java`). `BenefitPolicyRuleRepository.findBestRuleForService()`'s real JPQL has **no serviceId branch at all** despite its name and docstring claiming "service rule > category rule" — that docstring is stale. A pre-existing `BenefitPolicyCoverageServiceTest.getCoverageForService_ServiceRuleMatch` test still asserts a `"SERVICE"` rule-type outcome, but only because it mocks the repository call directly — it does not reflect what the real query can produce today. | **Not implemented (architecturally removed), plus a stale/misleading test and docstring** |
| 9 | Accommodation vs surgery | The engine itself correctly keeps two categories' rules separate — confirmed by passing test. The real risk is at the **taxonomy/data level**: the standard seed categories (`V25`, `V57`) have `CAT-IP-GEN`, `CAT-IP-NURSE`, `CAT-IP-PHYSIO`, `CAT-IP-WORK`, `CAT-IP-PSYCH`, `CAT-IP-MATER`, `CAT-IP-COMPL`, plus standalone `CAT005` (bed accommodation), but **no distinct "Inpatient Surgery" category** — only `CAT015` "جراحة للمريض خارج المستشفى" which is explicitly *outpatient*. If real inpatient surgery claims are mapped to `CAT-IP-GEN`/`CAT005` for lack of a dedicated category, they would share accommodation's rule/cap — which would look exactly like the reported bug from the doctor's side, without being an engine bug. | **Unclear — needs a live taxonomy check in this environment, not just code** |
| 10 | Admission days/quantity | Correct — confirmed by two passing tests (total computed once, cap applied to the multiplied total). | **Already fixed / working** |
| 11 | Dental price-list import ambiguity | `ProviderContractPricingExcelService` (lines ~293-307): when a category code/name can't be matched, `assignedCategory` stays `null` — the row is still **inserted or updated with no error, no review flag, no rejection**. There is no `PENDING_REVIEW`/`requiresReview`/`classificationStatus` concept anywhere in `ProviderContractPricingItem` or its import service today. A pricing item with `medicalCategory = null` later feeds claim submission with nothing for `CoverageEngineService` to resolve a rule against. | **Not implemented (confirmed reproducible gap)** |
| 12 | Cap exhausted | `remaining <= 0` is explicitly checked (not just `< 0`), `maxZero()` guards applied — confirmed by passing test. | **Already fixed / working** |

## 5. Reference branch comparison (`OmarAfoshAhmad/waad_sofyan_final`, branch `provider-price-lists-employer-scope`)

Fetched via a temporary read-only `ref-omar` git remote (removed after inspection) and a codeload tarball. **Not merged, not copied, no code taken** — inspected only to understand intended design.

This branch is a substantially larger, later-stage rebuild than a simple bug fix — three genuinely new subsystems, none of which exist in our current branch at all:

### 5a. Benefit Limit Bucket ledger (directly answers #1, #5, #6, #12 more robustly)

New entities: `BenefitLimitBucket`, `BenefitRuleBucket`, `BenefitBucketConsumption`, plus `BenefitBucketLedgerService`, `BenefitBucketLimitService`, `BenefitBucketClaimEventListener`.

- A **bucket** is a named, reusable limit pool (`amountLimit`/`timesLimit`/`daysLimit`, configurable `periodType`: PER_SERVICE/DAILY/MONTHLY/ANNUAL/MULTI_YEAR_POLICY/POLICY_PERIOD/LIFETIME, `countingMethod`: EACH_LINE/EACH_UNIT/PER_VISIT/PER_DAY, `consumptionBasis`: COMPANY_SHARE or eligible-amount, optional `parentBucket` for hierarchical/shared caps).
- Multiple `BenefitPolicyRule`s can link to the **same** bucket via `BenefitRuleBucket` — this is how "physio 10,000/year shared across several service codes" or "one annual pool shared by several categories" would be modeled, something our current one-`amountLimit`-per-`BenefitPolicyRule` design cannot express.
- `BenefitBucketLedgerService.commitClaim()` runs **after approval**, as an append-only, idempotent ledger (`idempotencyKey` per claim+line+bucket+calculationVersion) — re-validates the bucket's real-time balance (`validateAvailableBalance`) and **throws** if a race condition let two claims exceed the same bucket concurrently, closing exactly the timing gap in our #1 finding (our system's approval-time check reads a live SUM, but has no idempotent commit ledger to prevent a genuine double-approval race).
- `reverseClaim()` neutralizes ledger entries on rejection-after-approval, keeping the running balance correct.
- This directly and more robustly solves #1, #5, #6, #12, and removes the #5 status-filter inconsistency we found (one ledger, one source of truth, instead of two different JPQL aggregate queries with different status filters).

### 5b. Employer/partner-scoped provider contracts (relevant to #3, #9, #11 root causes)

`ProviderContract` gained a `scope` distinction (comment: *"GLOBAL: provider-wide fallback pricing, not tied to a specific employer. EMPLOYER_SPECIFIC: pricing applies only to one employer/work entity."*) with a real `employer` FK — entirely absent from our current `ProviderContract` entity. This lets the same provider have **different negotiated prices/categories per employer**, which is likely the actual root of some "wrong category/default fallback" complaints: today, one provider price list serves every employer identically.

### 5c. Price-list import classification/review workflow (directly answers #11)

`ProviderContractPricingItem` in the reference branch has fields **entirely missing** from ours: `encounterType`, `requiresReview`, `reviewReason`, `classificationStatus` (`ClassificationStatus` enum), `confidenceLevel` (`ConfidenceLevel` enum), `classificationSource`, `approvedBy`/`approvedAt`, `importedMainCategory`/`importedSubCategory`, plus a `maxContractPrice` ceiling. Neither `ClassificationStatus` nor `ConfidenceLevel` exists anywhere in our codebase (`find` returned nothing) — this whole review/classification concept would need to be ported, not patched in.

There is also a **separate, simpler** `BenefitPolicyRuleExcelService`/`BenefitPolicyRuleExcelController` for importing category-level coverage-rule Excel templates (coverage%, waiting period, pre-approval — **not** amount/times limits, per its own comment *"Limits belong exclusively to linked benefit buckets"*) — this doesn't exist in our system either; today our benefit rules can only be created one-by-one through the UI/API, with no bulk Excel import.

### 5d. What was NOT changed in the reference branch (confirms our #8 finding is not unique to us)

The reference branch's `BenefitPolicyRuleExcelService` only imports **category**-level rows — it never reintroduces service-level coverage percentages. The "V228, category-only" architectural decision is preserved in the reference branch too. So #8 (true per-service dental %) is **not solved by porting the reference branch as-is** — it would need a genuinely new design decision (e.g. a service-level override table sitting *on top of* category rules) in either codebase.

## 6. Affected backend files/classes

- `benefitpolicy/service/BenefitPolicyCoverageService.java` — `validateAmountLimits()` (annual limit, timing gap).
- `benefitpolicy/service/BenefitPolicyRuleService.java` — `checkUsageLimit()` (accumulation, status-filter inconsistency), `validateTargetXor()` (service-level block).
- `benefitpolicy/repository/BenefitPolicyRuleRepository.java` — `findBestRuleForService()` (stale docstring, no service-id branch).
- `claim/service/CoverageEngineService.java` — `computeUsage()`/`evaluateLine()` (cap math — confirmed working).
- `claim/service/ClaimMapper.java` — `processEngineCalculations()` (persists cap results — confirmed working).
- `claim/service/ClaimReviewService.java` — `processApprovalAsync()` (only call site of the annual-limit check).
- `claim/service/CostCalculationService.java` — unrelated hardcoded-zero stubs (`getDeductibleMetThisPeriod`/`getOutOfPocketSpentThisPeriod`), previously documented in `CLAIMS-FINANCIAL-LEGACY-PATHS-AUDIT-1-REPORT.md`; not part of the cap-accumulation path (that's real, in `BenefitPolicyRuleService`).
- `providercontract/service/ProviderContractPricingExcelService.java` — silent-null-category import gap.
- `providercontract/entity/ProviderContractPricingItem.java` — missing classification/review fields entirely.
- `providercontract/entity/ProviderContract.java` — missing employer-scope concept entirely.

## 7. Affected frontend pages

Not modified or deeply inspected this pass (backend-first per the ticket's test strategy), but the following would need corresponding UI once backend work lands:
- Benefit Policy/Rules UI — would need bucket configuration if #1/§5a is ported.
- Provider Contract Pricing UI / price-list import screen — would need a review queue if #11/§5c is ported.
- Claims Review workspace — coverage KPI cards already show `amountLimit`/`usedAmount`/`remainingAmount` (from earlier CLAIM-REVIEW-FOLLOWUP-1 work this session) and would automatically benefit from more accurate bucket-based numbers without further UI change.

## 8. DB tables/fields involved

Existing (confirmed working): `benefit_policy_rules` (amount_limit/times_limit columns), `benefit_policies` (annual_limit), `medical_categories`, `claim_lines` (limitRefused, usedAmountSnapshot family), `claims`.

New, if §5 is ported: `benefit_limit_buckets`, `benefit_rule_buckets`, `benefit_bucket_consumption` (reference migration `V84__full_benefit_classification_cutover.sql` + `V86`/`V88`/`V98` follow-ups — **not directly reusable**, our migration history diverged long before V84; would need fresh migrations numbered to fit our own sequence, past our current `V98`), plus new columns on `provider_contracts` (employer_id, scope) and `provider_contract_pricing_items` (classification_status, confidence_level, requires_review, review_reason, etc.).

## 9. Financial risk level per issue

| # | Issue | Risk |
|---|---|---|
| 1/6 | Annual limit enforced only at approval, not submission | **Medium** — real money is still blocked before any payment, just later than ideal; a determined double-submission race is the only way to exploit the timing gap, and the reference branch's idempotent ledger would close it. |
| 2, 4, 7, 10, 12 | Cap math itself | **None currently** — confirmed correct by passing tests. |
| 5 | Cap-accumulation status-filter inconsistency | **Low-Medium** — could cause a claim to appear blocked by one check and not another, confusing but not a silent-overpayment bug. |
| 8 | Dental service-level % | **Medium** — a real, deliberate architectural gap; if the business genuinely needs per-procedure dental %, using one category-level % for all of them either under- or over-pays every dental claim in that category. |
| 9 | Accommodation vs surgery taxonomy gap | **Medium-High if real** — needs a live DB check; if inpatient surgery truly has no dedicated category, its cap/coverage silently rides on accommodation's rule today. |
| 11 | Ambiguous price-list import rows | **High** — a mis-imported/uncategorized service can reach claim submission and pricing with zero coverage-rule resolution, an unpredictable financial outcome, with no review gate today. |

## 10. Recommended implementation tickets (not implemented, for later approval)

1. **`BENEFIT-CAP-LEDGER-1`** — Port a benefit-bucket-style consumption ledger (adapted from the reference branch's `BenefitLimitBucket`/`BenefitRuleBucket`/`BenefitBucketConsumption` design, re-numbered into our own migration sequence) to close the annual-limit timing gap and unify the two divergent usage-accumulation status filters into one source of truth.
2. **`PROVIDER-PRICE-IMPORT-REVIEW-1`** — Add a classification/review workflow to `ProviderContractPricingItem`/`ProviderContractPricingExcelService` (adapted from the reference branch's `ClassificationStatus`/`ConfidenceLevel`/`requiresReview` fields) so ambiguous or unmatched-category rows are held for review instead of silently entering financial approval with `medicalCategory = null`.
3. **`PROVIDER-CONTRACT-EMPLOYER-SCOPE-1`** — Evaluate whether provider price lists genuinely need per-employer scoping (port the reference branch's `ProviderContract.scope`/`employer` design) — likely the real fix underlying multiple "wrong default/category" complaints.
4. **`TAXONOMY-INPATIENT-SURGERY-1`** — Live-DB investigation (not code): confirm whether this environment's `medical_categories` has a distinct inpatient-surgery category separate from bed accommodation; if not, decide whether to add one or accept the shared rule as intended.
5. **`DENTAL-SERVICE-LEVEL-COVERAGE-1`** (only if the business confirms it's actually required) — design a service-level override mechanism sitting on top of category rules, since neither this branch nor the reference branch supports it today; this is a genuinely new capability, not a "restore what was removed" fix.
6. Minor cleanup: fix the stale `findBestRuleForService()` docstring/comment (claims service-level priority that no longer exists) and the misleading `getCoverageForService_ServiceRuleMatch` test name/assertions in `BenefitPolicyCoverageServiceTest.java` — both describe behavior the real code can no longer produce.

## 11. Confirmations

- No production/business logic code was changed — only two test files were added/extended (`DoctorNotesBenefitCapsRegressionTest.java`, new; `BenefitPolicyCoverageServiceTest.java`, one test added).
- The reference branch was inspected read-only via a temporary git remote and a downloaded tarball, both removed after use — nothing from it was copied into this codebase.
- No push was done.

---

**DOCTOR-NOTES-BENEFIT-CAPS-REGRESSION-1 READY FOR REVIEW**
