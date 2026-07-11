# Decision Required — CF-3: Two Parallel Coverage Engines

**Status:** 🟡 **DEFERRED by business decision (Stage 1 close-out).** No implementation. Not scheduled for Stage 2. Revisit when the business chooses to prioritize coverage-engine consolidation. No code changed.
**Severity:** 🔴 Critical (Constitution: *Single Source of Truth*).
**Constitution anchors:** *"Every business concept must have one authoritative implementation… If duplicate implementations exist: Identify them, Document them, Recommend consolidation. Never introduce another implementation."*

---

## Current Implementation

Two independent implementations of coverage/limit resolution coexist in the codebase:

1. **`modules/benefitpolicy/service/BenefitPolicyCoverageService`** — documented in its own header as *"the SINGLE implementation for resolving coverage. All other methods should delegate to this."* Resolves coverage as a **2-tier** algorithm (CATEGORY_RULE → POLICY_DEFAULT), computes annual/lifetime/family/category limit usage via DB-side aggregation, and enforces waiting periods.

2. **`modules/claim/ruleengine/*`** — a separate pluggable rule engine (`FinancialRuleEngineService`, `ClaimCoverageRule`, and rules such as `AmountLimitRule`, `CoveragePercentRule`, `TimesLimitRule`) with its **own admin controller** (`ClaimCoverageRuleAdminController`, `/api/v1/admin/claim-coverage-rules`) and its own persisted configuration/audit tables (`claim_coverage_rules`, `claim_rule_execution_audit`).

Both have dedicated unit tests (`BenefitPolicyCoverageServiceTest`, `FinancialRuleEngineServiceTest`), indicating both are (or were) intentionally maintained.

## Runtime Behavior

- `BenefitPolicyCoverageService` is invoked from the claim create/review/approval and eligibility paths (coverage snapshot, limit and waiting-period validation).
- `claim.ruleengine.*` is administered via its own endpoints and has execution-audit infrastructure, implying it also runs on some path.
- **What is NOT yet confirmed** (and, per your Stage-1 Finalization instruction, was deliberately **not** investigated further): whether both engines execute on the *same* live claim-adjudication path, and if so, which one's result is authoritative when they differ. This confirmation is the first task of the approved consolidation work, not something to guess now.

## Risk

- **Divergent coverage decisions:** if both engines are live on different code paths, two members with functionally identical policies could receive different coverage %, limit, or pre-approval outcomes depending on which path adjudicated their claim — a fairness and audit-defensibility risk in a healthcare TPA.
- **Maintenance drift:** a coverage-rule change made in one engine silently does not apply to the other.
- **Direct Constitution violation:** the Single Source of Truth principle is not currently satisfied for the single most business-critical concept in the platform.

## Business Impact

- **Financial:** potential inconsistent adjudicated amounts across otherwise-identical claims.
- **Medical:** potential inconsistent pre-authorization-requirement or coverage decisions.
- **Regulatory/audit:** "explain this decision" becomes ambiguous if two engines could have produced it.
- **Operational:** low visible impact today (the system runs), but the latent inconsistency compounds with every new coverage rule added to only one engine.

## Available Options

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A. Consolidate onto `BenefitPolicyCoverageService`** (make it the sole engine; migrate/retire `claim.ruleengine.*`) | Aligns with the code's own "canonical" documentation | Restores Single Source of Truth; least surprising | Requires migrating any rules/config currently only in the rule engine; needs a careful call-site audit first |
| **B. Consolidate onto `claim.ruleengine.*`** (make the pluggable engine authoritative; delegate the policy service to it) | The rule engine is more extensible/configurable | Configuration-driven rules (Constitution-favored) | Contradicts the existing "canonical" designation; larger change to the proven policy service |
| **C. Formalize a deliberate division of labor** (e.g., one for real-time preview, one for approval-time enforcement) — only if investigation shows they are intentionally complementary | Minimal code change | Documents intent | Only valid if they genuinely do not overlap; still needs the audit to prove it |

## Recommended Option

**Option A**, contingent on a first-step read-only call-site audit. Rationale: the codebase already designates `BenefitPolicyCoverageService` as canonical and it carries the stronger financial-integrity guarantees (DB-side limit aggregation, four limit scopes, waiting-period enforcement). The recommended sequence:
1. (On approval) Read-only audit of every call site of both engines to establish which is live on the approval path.
2. If `claim.ruleengine.*` is dead or preview-only → deprecate it (Extend → Deprecate → Monitor → Remove per Evolution Policy).
3. If it carries unique live rules → migrate those rules into `BenefitPolicyCoverageService` before retiring it.

## Why This Requires Business Approval

- Consolidating coverage engines can **change adjudicated amounts** for some claims if the two engines currently disagree — a financial-behavior change that must be business-validated, not made unilaterally by engineering (Constitution: *"Never simplify business rules just to produce cleaner code"; "business correctness always wins"*).
- Determining the *intended* canonical engine and the *intended* behavior where they differ is a business/domain decision, not a purely technical one.
- The work touches the platform's most critical calculation and therefore requires explicit sign-off before any change and full regression validation after.
