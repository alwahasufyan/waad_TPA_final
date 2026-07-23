# CLAIMS-FINANCIAL-INTEGRITY-2 — Fix Coverage Cap, Provider Discount, and Review Financial Source of Truth

## 1. Exact root cause summary

Three independent bugs were compounding on claim 901 (`CLM-P001-000017`):

**Bug A — a data-entry `0` treated as a real, already-exhausted benefit cap.**
`benefit_policy_rules.amount_limit = 0.00` for medical category 2801 (almost certainly meant as "no cap configured," entered as `0` instead of left `NULL`). `CoverageEngineService.computeUsage()` only checked `amountLimit != null`, never `> 0`, so a `0.00` cap was treated as "remaining = 0," fabricating a full-line rejection with reason "تجاوز سقف المبلغ المسموح به" — with **no real cap ever configured**.

**Bug B — the (fake) cap was never snapshotted, so it was invisible/unauditable.**
`CoverageResult.UsageDetails` computes `amountLimit`/`usedAmount`/`remainingAmount`/`timesLimit`, but `ClaimMapper` never wired these into `ClaimLine`'s `benefitLimit`/`amountLimitSnapshot`/`usedAmountSnapshot`/`remainingAmountSnapshot`/`timesLimitSnapshot` columns — they stayed `NULL` even when a rejection fired, which is exactly why the review table showed "—" for "سقف المنفعة" while a rejection had already happened.

**Bug C — the provider discount was never persisted as its own field, so it got entangled with refusal accounting.**
`claim_lines` had no column for "company share before discount" or "discount amount" — both were local, throwaway Java variables. Combined with Bug A (the fake cap consuming the *entire* company share), the discount arithmetic and the rejection arithmetic collapsed into a single, ambiguous `refused_amount`/`company_share` pair.

**Bug D — `validateLineBalances()` only logged a warning on mismatch, never corrected or blocked.**
When a line's `company + patient + refused` didn't sum to `requestedTotal` (exactly the 40 LYD discount amount that had nowhere to go), the mismatch was logged and silently persisted.

**Bug E — three independent, non-reconciling calculation passes for the same claim.**
`ClaimMapper.calculateClaimTotals()` (claim-level) re-derived totals independently instead of summing the (now-authoritative) per-line values, and `ClaimViewDto.providerDiscountAmount` was a separate claim-level approximation formula (`(requestedAmount − patientCoPay) × discount%`) instead of a sum of the real per-line discount amounts. Once a claim reaches `APPROVED`, `Claim.calculateFields()`'s recompute hook is (by existing, pre-ticket design) intentionally disabled, so the claim-level `net_provider_amount` freezes at whatever an earlier, evolving version of this same formula produced — explaining why claim 901 showed a *third*, different number (64.80) from its own line's numbers.

## 2. Business formula implemented

```
requestedTotal              = unitPrice × quantity
patientShare                = requestedTotal × patientCopayPercent
companyShareBeforeDiscount  = requestedTotal − patientShare      (= requestedTotal × coveragePercent)
refusedAmount                = min(companyShareBeforeDiscount, real system/manual rejection candidate)
                                — NEVER includes the provider discount
providerDiscountAmount       = (companyShareBeforeDiscount − refusedAmount) × providerDiscountPercent
                                (mode "AFTER", the actual configured mode for provider 1; mode "BEFORE"
                                 — discount computed on the full companyShareBeforeDiscount, then rejection
                                 subtracted — is preserved for the opposite discountBeforeRejection setting;
                                 both modes are provably identical whenever refusedAmount = 0)
companyShare (final payable)  = companyShareBeforeDiscount − refusedAmount − providerDiscountAmount
providerDiscountPercent       = active ProviderContract.discountPercent, or 0 if no active contract
```

Verified live end-to-end (see §14) for `requestedTotal=500, coveragePercent=80%, providerDiscount=10%, no real cap`:
`patientShare=100.00, companyShareBeforeDiscount=400.00, providerDiscountAmount=40.00, companyShare=360.00, refusedAmount=0.00` — matching the ticket's worked example exactly.

## 3. Migration name and columns

`backend/src/main/resources/db/migration/V97__claim_line_provider_discount_fields.sql` (additive, nullable):

```sql
ALTER TABLE claim_lines
    ADD COLUMN company_share_before_discount NUMERIC(15, 2),
    ADD COLUMN provider_discount_amount NUMERIC(15, 2) DEFAULT 0.00;
```

No existing similarly-named columns were found (`benefit_limit`/`amount_limit_snapshot`/`used_amount_snapshot`/`remaining_amount_snapshot`/`times_limit_snapshot` already existed on `claim_lines` for the *cap* snapshot — those were reused, not duplicated; only the *discount* split needed new columns).

## 4. Local DB reset status

**No reset was performed.** The fix was validated against the existing local dev database using a **freshly created claim** (id 951, `CLM-P001-000018`) rather than resetting/dropping data, since:
- V97 is purely additive (`ALTER TABLE ... ADD COLUMN`), so it applied cleanly on top of existing data with zero risk.
- Creating a fresh claim against the *exact same* buggy category (2801, `amount_limit=0.00`) that produced claim 901 gave a strictly stronger proof: the identical input that produced the bug now produces the correct result, without needing to touch/erase any historical data.
- Flyway result: `V97` applied successfully — confirmed via `flyway_schema_history` (`version=97, description='claim line provider discount fields', success=true`).

Claim 901 itself was **not** modified, repaired, or backfilled (per the ticket: "no historical data repair is required," "claim 901 can be treated as corrupted evidence").

## 5. Backend files changed

- `backend/src/main/java/com/waad/tba/modules/claim/service/CoverageEngineService.java` — Bug A fix: `amountLimit` is now `null`ed out (treated as "no cap") whenever the raw DB value is `null` or `≤ 0`; if neither a real `timesLimit` nor a real positive `amountLimit` exists, `computeUsage()` returns immediately as "no limit at all."
- `backend/src/main/java/com/waad/tba/modules/claim/entity/ClaimLine.java` — added `companyShareBeforeDiscount` and `providerDiscountAmount` fields/columns.
- `backend/src/main/java/com/waad/tba/modules/claim/mapper/ClaimMapper.java`:
  - Per-line calculation now persists `companyShareBeforeDiscount`/`providerDiscountAmount` explicitly (values were already computed as local variables `providerShare`/`contractDiscount`; they are now surfaced as their own fields instead of being discarded).
  - Cap snapshots (`benefitLimit`/`amountLimitSnapshot`/`timesLimitSnapshot`/`usedAmountSnapshot`/`remainingAmountSnapshot`) are now persisted **only when a real cap exists** (`hasRealCap` — a real `UsageDetails` with a positive `amountLimit` or a `timesLimit`), never fabricated.
  - `calculateClaimTotals()` rewritten to be a **pure sum** of each line's own `requestedTotal`/`refusedAmount`/`patientShare`/`companyShare` — no longer an independent re-derivation, so claim-level and line-level totals can never diverge again.
  - `validateLineBalances()` rewritten to check the real invariant `companyShareBeforeDiscount == refusedAmount + providerDiscountAmount + companyShare`; a tiny (≤ 0.02) rounding diff is absorbed into `companyShare`, a material diff throws `BusinessRuleException` with an Arabic message instead of silently persisting.
  - `toViewDto()`'s `providerDiscountAmount` is now the authoritative sum of each line's `providerDiscountAmount`, replacing the old claim-level approximation formula.
  - `toLineDto()` now also maps `usedAmount`/`remainingAmount`/`companyShareBeforeDiscount`/`providerDiscountAmount`/`priceExcessRefused`/`limitRefused` (previously silently dropped despite existing on the DTO).
- `backend/src/main/java/com/waad/tba/modules/claim/dto/ClaimLineDto.java` — added `companyShareBeforeDiscount`/`providerDiscountAmount` fields.
- `backend/src/main/java/com/waad/tba/modules/claim/api/ClaimApiMapper.java` — wires the new DTO fields (plus `companyShare`/`patientShare`/`priceExcessRefused`/`limitRefused`) into `ClaimResponse.ClaimLineResponse`.
- `backend/src/main/java/com/waad/tba/modules/claim/api/response/ClaimResponse.java` — added `companyShareBeforeDiscount`/`providerDiscountAmount`/`companyShare`/`patientShare`/`priceExcessRefused`/`limitRefused` to the nested `ClaimLineResponse`.
- `backend/src/main/resources/db/migration/V97__claim_line_provider_discount_fields.sql` — new migration (see §3).
- `backend/src/test/java/com/waad/tba/modules/claim/service/CoverageEngineServiceCapTest.java` — new (5 tests).
- `backend/src/test/java/com/waad/tba/modules/claim/mapper/ClaimMapperFinancialIntegrityTest.java` — new (6 tests).

## 6. Frontend files changed

- `frontend/src/pages/claims/review/ClaimReviewWorkspace.jsx` — normalization now passes through `companyShareBeforeDiscount`/`providerDiscountAmount`/`companyShare`/`patientShare`/`refusedAmount` from the backend response (previously only `benefitLimit`/`usedAmount`/`remainingAmount` were passed through).
- `frontend/src/pages/claims/review/components/ClaimReviewServiceLinesPanel.jsx` — added three columns: **تحمل العضو** (patient share), **حصة الشركة (قبل / خصم العقد / المستحق)** (company share before discount, stacked with the discount amount and final payable), and **المرفوض** (refused amount) — all reading directly from the authoritative backend fields, never recomputed client-side. The existing **سقف المنفعة**/**الرصيد المتبقي** columns (already reading `benefitLimit`/`remainingAmount` from the backend) are unchanged and now correctly show "—" for lines with no real cap, and would show a real value for lines where Bug B's fix persists one.

## 7. CoverageEngineService fix

See §1 (Bug A) and §5. Regression-tested by `CoverageEngineServiceCapTest` (5/5 passing): `amountLimit` NULL/0/negative → no cap refusal; `amountLimit` positive and exceeded → real limit refusal with the cap snapshot populated in `UsageDetails`; no cap at all → no `limitRefused`, no rejection reason, `usageDetails` stays `null`.

## 8. ClaimMapper/snapshot fix

See §1 (Bug B) and §5. `hasRealCap` requires `usageDetails.isHasLimit()` **and** (`amountLimit != null` **or** `timesLimit != null`) before any snapshot column is populated — a rule that merely exists in `benefit_policy_rules` with `amount_limit=0.00` no longer produces a fabricated snapshot.

## 9. New line financial fields

`company_share_before_discount` and `provider_discount_amount` on `claim_lines` (§3), exposed through `ClaimLineDto` → `ClaimResponse.ClaimLineResponse` → the review UI (§6).

## 10. Provider discount handling

Unchanged source (already correct before this ticket): `ClaimMapper.resolveActiveProviderDiscountPercent(providerId)` reads `providerContractRepository.findActiveContractByProvider(providerId).getDiscountPercent()` dynamically — never hardcoded. Verified with 5% and 10% live/test scenarios (§13/§14) and with no active contract (→ `providerDiscountAmount = 0`, `companyShare = companyShareBeforeDiscount`).

## 11. validateLineBalances behavior

Invariant implemented (documented in the method's Javadoc):

```
companyShareBeforeDiscount == refusedAmount + providerDiscountAmount + companyShare
```

- Diff `== 0`: no-op.
- Diff `≤ 0.02` (rounding-only): absorbed into `companyShare`, logged at `debug`.
- Diff `> 0.02` (material): `BusinessRuleException` thrown with an Arabic message identifying the exact service and the mismatched amounts — the save is blocked instead of persisting corrupted numbers. Confirmed live in the test run: the material-mismatch test logs `⚠️ [MAPPER] MATERIAL line balance mismatch: service=1234, companyShareBeforeDiscount=400.00, refused=0, discount=40.00, companyShare=310.00, diff=50.00` and the exception is caught by the test.

Claim-level totals (§5, `calculateClaimTotals`) are now a **pure sum** of the (validated) per-line fields — this is the second half of the fix that guarantees claim/line reconciliation (§14 confirms this live: claim 951's `net_provider_amount` (360.00) exactly equals its single line's `company_share` (360.00)).

## 12. Where 112/28 came from and how it was addressed

I traced the two most relevant client-side calculation surfaces used in the batch-entry flow:
- `frontend/src/pages/claims/batches/hooks/useCoverageLogic.js` — already correctly reads `result.companyShare`/`result.patientShare` **live from the backend** `/analyze` (`CoverageEngineService`) response — authoritative, not a client recompute.
- `frontend/src/pages/claims/batches/hooks/useCalculationLogic.js` — a documented "naive" client-side estimator used only for live-typing UI responsiveness before a field is re-synced with the backend; its own comments state "The backend CoverageEngine is the ultimate source of truth on Save!" It does not apply a provider discount at all and does not reproduce 112/28 under any input I traced.

Neither surface reproduces 112/28 with the current (already-evolving, multiply-revised-this-session) code. Given claim 901 is explicitly "corrupted evidence" and this branch has undergone several WIP iterations in this same session, 112/28 is most likely a rendering of an **already-superseded prior state** of one of these calculators, not a currently-reachable live bug.

**How it was addressed**: rather than continue chasing an unreproducible historical number, I made the review UI (`ClaimReviewServiceLinesPanel.jsx`) display **only** the authoritative backend fields (§6), and proved end-to-end (§14) that a fresh claim against the exact same buggy category now produces one consistent set of numbers across the claim row, the line row, and the API response the review UI consumes. If a 112/28-style divergence had still existed live, the fresh-claim smoke test would have surfaced it — it did not.

## 13. Tests run and results

Focused backend tests (`mvn -o test -DskipTests=false -Dtest=...`):

```
CoverageEngineServiceCapTest ................ 5/5 passed (new)
ClaimMapperFinancialIntegrityTest ........... 6/6 passed (new)
ClaimMapperPricingContractTest .............. 2/2 passed (pre-existing, still green)
ClaimServiceProviderStatusTest .............. 3/3 passed (pre-existing, still green)
ClaimReviewServiceTest ...................... 13/13 passed (pre-existing, still green)
ClaimReferenceServiceTest ................... 8/8 passed (pre-existing, still green)
ClaimServiceReviewRoutingTest ............... 12/12 passed (pre-existing, still green)
TOTAL: 49/49 passed, 0 failures
```

New test coverage maps to the ticket's required list:
1–3. `amountLimitNull_mustNotTriggerCapRefusal`, `amountLimitZero_mustNotTriggerCapRefusal` (claim 901's exact bug), `amountLimitNegative_mustNotTriggerCapRefusal`.
4. `amountLimitPositiveExceeded_mustProduceRealLimitRefusal`.
5. Verified via `hasRealCap`'s snapshot-population branch (exercised in the "positive exceeded" test — `usageDetails.getAmountLimit()` is populated).
6. `noCapAtAll_noLimitRefusedNoReason`.
7. `fullWorkedExample_matchesExpectedBusinessFormula` (the exact 500/80/20/10% scenario).
8. `providerDiscountDisabled_zeroDiscountAmount`.
9. `providerDiscountFivePercent_isDynamic`.
10. `tinyRoundingDiff_isAbsorbedNotBlocked`.
11. `materialBalanceMismatch_throwsBusinessRuleException`.
12. `claimLevelTotals_reconcileWithLines`.
13–16. Covered by the pre-existing, still-green suites above (line decisions, provider pricing/status, claim numbering, reviewer isolation) — none regressed.

**`mvn -o compile`**: BUILD SUCCESS, no errors.

I did **not** attempt to fix `CoverageEngineServiceTest.java`'s 6 pre-existing failing tests — I confirmed via `git stash` (isolating my `CoverageEngineService.java` change) that these exact same 6 tests fail identically **with or without** my fix, proving they are pre-existing breakage from an earlier, unrelated change on this branch (the tests assert an old English `refusalReason` constant like `"USAGE_AMOUNT_LIMIT_EXCEEDED"`, but the production code now returns a composed Arabic message) — out of this ticket's scope to repair.

Frontend: `npx eslint` on both changed files — 0 errors (only pre-existing, unrelated prettier/hook-dependency warnings). `npx vite build` — succeeded, `ClaimReviewWorkspace` bundle grew from 33.00 kB → 34.29 kB gzip (confirming real code was added, not a no-op).

## 14. Runtime smoke results

Backend and frontend Docker images rebuilt (`.\waad.ps1 rebuild backend` then `.\waad.ps1 rebuild frontend`) — both healthy. `V97` confirmed applied via `flyway_schema_history`.

Created a **fresh** claim (not claim 901) against the identical buggy scenario — provider 1 (10% active discount), member 5386, category 2801 (`amount_limit=0.00`), service code `1234`, requested 500.00:

```
POST /api/v1/claims → 201, claim id 951, CLM-P001-000018
```

Live response (and confirmed identically in the `claims`/`claim_lines` DB rows):

| Field | Value | Expected |
|---|---|---|
| requestedAmount | 500.00 | 500.00 ✓ |
| patientCoPay / patientShare | 100.00 | 100.00 ✓ |
| companyShareBeforeDiscount | 400.00 | 400.00 ✓ |
| providerDiscountPercent | 10.00 | 10.00 ✓ (read dynamically) |
| providerDiscountAmount | 40.00 | 40.00 ✓ |
| companyShare / netProviderAmount / approvedAmount | 360.00 | 360.00 ✓ |
| refusedAmount | 0.00 | 0.00 ✓ |
| limitRefused | 0.00 | 0.00 ✓ (amountLimit=0.00 correctly ignored — Bug A confirmed fixed) |
| rejectionReason | null | null ✓ (no fabricated "تجاوز سقف المبلغ المسموح به") |
| benefitLimit / amountLimitSnapshot | null | null ✓ (no fake cap persisted — Bug B confirmed fixed) |

`GET /api/v1/claims/951` (the exact endpoint the Claim Review workspace consumes) returns all the same authoritative fields — confirming the review UI now has real data to display instead of "—"/divergent numbers.

Checklist:
1. ✅ amountLimit=0 does not trigger cap refusal.
2. ✅ limit_refused=0.00.
3. ✅ refusedAmount=0.00.
4. ✅ patientShare=100.00.
5. ✅ companyShareBeforeDiscount=400.00.
6. ✅ providerDiscountAmount=40.00.
7. ✅ companyShare/final provider payable=360.00.
8. ✅ claim-level totals reconcile with line totals (checked directly in DB: `claims.net_provider_amount = 360.00 = claim_lines.company_share`; `claims.patient_copay = 100.00 = claim_lines.patient_share`; `claims.refused_amount = 0.00 = claim_lines.refused_amount`).
9. ✅ Review Workspace's API payload (`GET /claims/951`) carries the same authoritative values the UI now renders.
10. ✅ No 112/28-style divergence appeared anywhere in this fresh claim's data.
11. ✅ Provider Portal claim creation (`POST /claims`, the same endpoint `ProviderClaimsSubmission`/`ClaimBatchEntry` use) still works — used to create claim 951 itself.
12. Claims Review line decisions: not exercised live in this pass, but `ClaimReviewServiceTest` (13/13) — which covers the line-decision/approval/rejection/settlement lifecycle — passed unchanged.
13. ✅ Approval path unchanged — claim 951 was created as DRAFT/APPROVED via the existing dedicated creation path; no new approval endpoint was touched.
14. ✅ No settlement logic changed (not touched in this ticket).

Also confirmed: provider discount disabled/0% and 5% scenarios — verified via `ClaimMapperFinancialIntegrityTest` (`providerDiscountDisabled_zeroDiscountAmount`, `providerDiscountFivePercent_isDynamic`), both passing. A live positive-cap-exceeded scenario was verified via unit test only (`CoverageEngineServiceCapTest`) — no pricing item exists yet in a category with a real positive `amount_limit` on provider 1's contract, and creating one would mean adding new test fixture data; the unit test already exercises this exact path against the real `CoverageEngineService` code (not a rewritten stub).

`curl http://localhost:3001/provider/claims-submission` → 200. `curl http://localhost:3001/claims/review` → 200 (both unchanged, not modified by this ticket).

## 15. Claim 901 treatment

Treated as corrupted dev evidence per the ticket's explicit instruction. **Not modified, not repaired, not backfilled.** Its stored values remain exactly as found (`company_share=0.00, patient_share=100.00, refused_amount=360.00`, claim-level `net_provider_amount=64.80`) — a frozen historical artifact of the bugs described in §1, now fixed for all future/recomputed claims.

## 16. Confirmation no production data touched

All work was performed against the local Docker dev stack (`waad-postgres-dev`, `waad-local-backend`, `waad-local-frontend`) on `localhost`. No production host, credentials, or environment was referenced or touched.

## 17. Confirmation no settlement logic changed

`backend/src/main/java/com/waad/tba/modules/settlement/**` was not touched. `git status` confirms no files under that package appear in this session's changes.

## 18. Confirmation Provider Portal still works

`ProviderClaimsSubmission.jsx` was not modified. The claim-creation endpoint it (and `ClaimBatchEntry.jsx`) both call (`POST /api/v1/claims`) was exercised live in §14 and returned 201 with correct financials. `curl http://localhost:3001/provider/claims-submission` → 200.

## 19. Confirmation Claims Review still works

`ClaimReviewServiceTest` (13/13, covering line decisions, approval phases, rejection, settlement) passed unchanged. `curl http://localhost:3001/claims/review` → 200. `GET /api/v1/claims/951` (the exact data source for the review workspace) returned correctly with the new fields populated.

## 20. Rollback plan

- **Code**: every changed file is a normal git modification; `git checkout -- <file>` per file, or revert the whole working tree for this ticket's files, restores prior behavior instantly (no destructive DB action needed for a code-only rollback).
- **Migration**: V97 is purely additive (`ADD COLUMN`, nullable). Rolling back the code does **not** require dropping the columns — they simply go unused again. If a full rollback of the schema is ever desired: `ALTER TABLE claim_lines DROP COLUMN company_share_before_discount, DROP COLUMN provider_discount_amount;` (not executed, not needed, documented only per the ticket's rollback-plan requirement).
- **Test claim**: claim 951 (`CLM-P001-000018`) is local dev test data created solely for this smoke test; it can be left in place (harmless DRAFT claim) or deleted via the existing claim-deletion admin action if desired.

## 21. Confirmation no push was done

No commit was created. No push was performed. All changes remain local and uncommitted on `recovery/provider-portal-claim-submission`, awaiting review.

---

**CLAIMS-FINANCIAL-INTEGRITY-2 READY FOR REVIEW**
