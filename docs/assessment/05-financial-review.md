# 05 — Financial Review

**Score: 82/100 (B+)** · Reviewed against `.claude/reviews/financial-review.md.txt` and the Constitution's Financial Philosophy ("*Financial data is immutable once approved... every amount must always be explainable*"). This is the highest-scoring area in the assessment.

---

## Financial Calculations

| Check | Status | Evidence |
|---|---|---|
| Pricing resolved correctly | ✓ | `ClaimLine.unitPrice` always sourced from `ProviderContractPricingItem`, never client-entered |
| Approved amount calculated | ✓ | Server-derived, never trusted from request payload |
| Member liability calculated | ⚠ | Correct formula, but co-pay input has the CF-7 hardcoded-fallback defect |
| Employer liability calculated | ✓ | Via `companyDiscountAmount`, always system-calculated |
| Insurance/provider liability calculated | ✓ | `netProviderAmount` derived via the financial-identity formula |
| Totals balanced | ✓ | `Claim.validateFinancialIdentity()` — see below |

**The core formula** (`Claim.calculateFields()`):
```
providerShare = requestedAmount − patientCoPay
if discountBeforeRejection (contract default = true):
    netPayable = (providerShare − providerShare × discount%) − refusedAmount
else:
    netPayable = (providerShare − refusedAmount) − (providerShare − refusedAmount) × discount%
```
This is re-derived and compared against the persisted value on every save, with a 0.05 rounding tolerance, throwing `IllegalStateException` on any larger deviation. **This is exactly the shape of financial guard an enterprise TPA needs** — deterministic, self-checking, not merely UI-validated.

## Pricing

| Check | Status | Evidence |
|---|---|---|
| Correct price source | ✓ | Contract-driven, never manual per-claim entry |
| Contract pricing applied | ✓ | Snapshotted at approval time (`appliedDiscountPercent`, `discountBeforeRejection`) |
| Benefit rules respected | ✓ | Coverage/limit checks delegate to `BenefitPolicyCoverageService` |
| Currency correct | ✓ | Single-currency (LYD) system, consistent with Project Manifest's "Libya First" scope — no multi-currency complexity to verify |
| Exchange rate stored | N/A | Not applicable — single currency |

## Benefit Integration

| Check | Status | Evidence |
|---|---|---|
| Benefit Engine used | ⚠ | Used, but **two** engines exist (`BenefitPolicyCoverageService` vs `claim.ruleengine.*`) — see CF-3 |
| Annual limits respected | ✓ | DB-side SUM aggregation, four independently-tracked limit scopes (category, annual-per-member, lifetime-per-member, per-family) |
| Category limits respected | ✓ | Category-hierarchy-aware resolution with parent-category fallback |
| Co-payment applied | ⚠ | Correct when policy-derived; falls back to a hardcoded 20% otherwise (CF-7) |
| Deductible applied | ✓ | `MemberDeductible` entity, per-member/year tracking |
| Coinsurance applied | ✓ | Via `coveragePercentSnapshot` |

## Financial Integrity

| Check | Status | Evidence |
|---|---|---|
| No negative amounts | ✓ | DB CHECK constraints on `claims`/`claim_lines` for all financial columns (added V44) |
| No duplicate calculations | ⚠ | The dual coverage-engine issue is the concrete risk here |
| No duplicate payments | ✓ | `PaymentService.addPayment()` cannot exceed remaining amount without explicit override+reason |
| Totals reconcile | ✓ | `ProviderAccount.assertBalanceInvariant()`: `runningBalance = totalApproved − totalPaid`, enforced after every mutation, backed by DB-level double-entry CHECK constraints on `account_transactions` |
| Financial snapshot created | ✓ | `ClaimLine.*_snapshot` columns freeze coverage state at claim-creation time — correct immutability-at-decision-time design |

## Settlement

| Check | Status | Evidence |
|---|---|---|
| Eligible for settlement | ✓ | State-machine-gated (`APPROVED`/`BATCHED` → `SETTLED`, `ACCOUNTANT`-only) |
| Settlement batch assigned | ✓ | `ClaimBatch` entity, monthly aggregate model |
| Provider payable correct | ✓ | Derived from `netProviderAmount`, netted against `PaymentRecord`s |
| Adjustment handling | ✓ | `addPayment`/`updatePayment` override-with-reason controls |
| Reversal supported | ✓ | `ClaimReversalEvent` → `ProviderAccount.reverseCredit()` (decreases balance/totalApproved without touching totalPaid — correct ledger semantics) |

## Audit

| Check | Status | Evidence |
|---|---|---|
| Financial version created | ✓ | `@Version` optimistic locking on `Claim` and `ProviderAccount`, explicitly documented to prevent double-deduction/concurrent-update races |
| Audit records written | ✓ | `medical_audit_logs` (immutable), `PaymentAuditLog` (mandatory reason on update/delete) |
| Adjustment history | ✓ | `PaymentAuditLog` old/new amount + reason |
| Approval history | ✓ | `STATUS_CHANGE` audit entries on every claim state transition |

## Security

| Check | Status | Evidence |
|---|---|---|
| Financial permissions | ✓ | `ACCOUNTANT`-exclusive gating on settlement/payment mutations |
| Approval workflow | ✓ | Multi-stage: medical review → financial review → approval → settlement |
| Segregation of duties | ✓ | `NEEDS_CORRECTION` transition is `MEDICAL_REVIEWER`-exclusive; settlement is `ACCOUNTANT`-exclusive — real role separation, not just labels |
| Sensitive financial data protected | ✓ | Standard RBAC-gated access, no evidence of financial data exposed to unauthorized roles |

## Performance

| Check | Status | Evidence |
|---|---|---|
| Bulk calculations supported | ✓ | `CoverageEngineController./calculate-bulk` |
| Efficient queries | ✓ | DB-side SUM aggregation (documented performance fix replacing an earlier O(n) in-memory approach) |
| Batch processing | ✓ | `ClaimBatch`-centric workflow throughout |
| No unnecessary recalculations | ⚠ | `pendingRecalculation` dirty-flag pattern exists and is respected, but the dual coverage-engine question makes "no unnecessary recalculation" hard to fully certify |

---

## Findings Requiring Action

1. **(Critical, CF-7)** Hardcoded 20% patient co-pay fallback — the one confirmed magic number in an otherwise rule-driven financial system.
2. **(Critical, CF-3)** Dual coverage engines — the Constitution's Single Source of Truth principle is not currently satisfied for coverage/limit resolution.
3. **(Medium)** `PaymentService.mapToDto()` swallows employer/provider name-lookup failures silently (`try/catch (Exception ignored)`) — a bad FK would show a placeholder instead of surfacing a real data-integrity error on a financial report.
4. **(Low)** No confirmed automated regression test asserting the financial-identity formula's correctness across edge cases (zero-amount claims, 100%-rejected lines, full-coverage claims) — one exists (`ClaimServiceCoverageDirtyMarkTest`, `CostCalculationServiceTest`) but coverage breadth was not independently verified line-by-line in this pass.

## Decision

**✅ Approve, with Changes Required before the next financial audit cycle.** The financial engine's architecture is sound and should be the reference pattern for every other calculation-bearing module in the platform (see `10-project-dna.md`-equivalent framing in the prior system analysis). The two findings above (CF-7, CF-3) are narrow, well-understood, and fixable without touching the underlying design.

---

*Continue to [`06-medical-review.md`](./06-medical-review.md).*
