# 03 — Critical Findings

> These seven findings require a decision or a targeted fix **before** any other roadmap item, per the Constitution's "Production First" and "Financial Safety" principles. Each includes business impact, technical evidence, and a recommended immediate action. Full technical detail lives in the corresponding numbered review. These map directly to Epic 1 (Critical Stabilization) in `21-enterprise-roadmap.md`.

---

## CF-1. Claim state machine has two disagreeing sources of truth, and a documented recovery path is unreachable

**Business impact:** A rejected claim that staff believe can be reopened for correction ("REJECTED → APPROVED") cannot actually be reopened — `ClaimStateMachine.validateFinalStateLock()` unconditionally blocks any transition out of `REJECTED`. If this is not the intended behavior, claims are getting permanently stuck when they shouldn't; if it *is* the intended behavior, the documentation actively misleads staff and future engineers into believing a recovery workflow exists.

**Technical evidence:** `ClaimStatus.getValidTransitions()` and `ClaimStateMachine.TRANSITION_MATRIX` list different legal transitions for the same states (e.g. `UNDER_REVIEW → APPROVED` is allowed in the runtime-enforcing class but not in the enum's own documentation of itself).

**Recommended immediate action:** Product/architecture decision within one week: is `REJECTED` meant to be truly terminal, or recoverable? Once decided, make `ClaimStateMachine` (the class that actually executes) the single source of truth and either delete or regenerate the enum's transition table from it, so the two can never diverge again.

---

## CF-2. Visit deletion can hard-delete settled financial claims

**Business impact:** Deleting a Visit record cascades (`cascade = ALL`) to hard-delete every Claim linked to it — including claims already in `SETTLED` status, with real money already paid to a provider. This bypasses every soft-delete, audit, and financial-integrity protection the rest of the system carefully enforces. This is the single most consequential data-integrity risk in the platform.

**Technical evidence:** `VisitService.delete()` calls `repository.deleteById()` with no guard against existing claims; `Visit.claims` and `Visit.eligibilityChecks` are mapped `cascade = ALL`.

**Recommended immediate action:** Convert `VisitService.delete()` to a soft-delete, or (minimum viable fix) add a guard that refuses deletion when any non-DRAFT claim exists. Ship with a regression test proving a Visit with a SETTLED claim cannot be destroyed.

---

## CF-3. Two parallel, unreconciled coverage-calculation engines

**Business impact:** This is a direct violation of the Engineering Constitution's Single Source of Truth principle ("*Every business concept must have one authoritative implementation... If duplicate implementations exist, identify them, document them, recommend consolidation*"). If `benefitpolicy.BenefitPolicyCoverageService` (documented as canonical) and `claim.ruleengine.*` are both live on different code paths, two members with functionally identical policies could receive different coverage percentages, limits, or pre-authorization requirements — an adjudication-fairness and audit-defensibility risk.

**Technical evidence:** Both systems independently implement coverage/limit resolution; `claim.ruleengine.*` has its own admin controller (`ClaimCoverageRuleAdminController`) suggesting active configuration, not dead code.

**Recommended immediate action:** Audit every call site of both services to determine which actually executes in the live claim-approval path. Either document a deliberate division of labor, or migrate all call sites to the canonical service and formally deprecate the other. This is Epic 2's first work item.

---

## CF-4. Possible foreign-key target mismatch on claim pre-authorization linkage

**Business impact:** If confirmed, claims may be linking to the wrong pre-authorization record system-wide, silently corrupting the pre-authorization-to-claim traceability that the "no standalone claim creation" architectural law depends on.

**Technical evidence:** `claims.pre_authorization_id` targets the `preauthorization_requests` table, while in-code comments describe `pre_authorizations` (a different table) as "the real working preauth table."

**Recommended immediate action:** Highest-priority read-only investigation in this entire assessment — trace which table `PreAuthorizationController`'s live approval flow actually writes to, and confirm whether `claims.pre_authorization_id` is pointed correctly. This should be resolved within days, not sprints, because every subsequent pre-authorization/claim finding depends on knowing the answer.

---

## CF-5. Destructive migration recreated payment/payment-audit tables in production history

**Business impact:** If this migration (V66) ran against a populated production database, historical payment-audit records may have been permanently lost — a direct violation of the Constitution's "*Never recommend dropping production tables without explicit approval... never recommend deleting historical records*" principle, and a real problem for a financially-audited system.

**Technical evidence:** Migration V66 performs `DROP TABLE IF EXISTS payment_audit_logs; DROP TABLE IF EXISTS payment_records; CREATE TABLE ...` — a full recreate, following an incomplete column-rename attempt in V63.

**Recommended immediate action:** Confirm with whoever operated the production database at the time whether V66 ran against populated tables, and if so, whether a pre-migration backup exists that can recover the lost audit history. This is a fact-finding action, not a code change.

---

## CF-6. JWT tokens effectively never expire, with no revocation mechanism

**Business impact:** A leaked or stolen JWT (from a compromised device, a logged request, a misconfigured log aggregator) remains a valid bearer credential for approximately ten years. There is no way to invalidate it short of rotating the signing secret for the entire platform. Session-cookie authentication is the system's preferred path and reduces day-to-day exposure, but the JWT fallback is fully live in production.

**Technical evidence:** `application.yml` configures `jwt.expiration: 315360000000` (milliseconds ≈ 10 years); no denylist/blacklist table or check exists in `JwtAuthenticationFilter`.

**Recommended immediate action:** Shorten JWT expiration to a realistic window (hours, not years) and add a minimal revocation mechanism (a denylist table checked by the filter is sufficient — no need for a distributed cache/Redis investment at current scale). Sequence this after CF-1/CF-2 only because it requires coordinated frontend/backend testing of the refresh flow, not because it is less urgent.

---

## CF-7. Hardcoded 20% patient co-pay fallback inside the core financial calculation

**Business impact:** When `patientCoPay` is not explicitly set, `Claim.calculateFields()` silently defaults it to 20% of the net-accepted amount — regardless of what the member's actual benefit policy specifies. This is a magic number substituting for a policy-driven truth inside the one calculation this entire assessment identifies as the system's best-engineered piece of logic. Every other part of `validateFinancialIdentity()` is rigorously derived; this one input is not.

**Technical evidence:** `Claim.calculateFields()`, hardcoded `20%` fallback when `patientCoPay` is null.

**Recommended immediate action:** Before changing behavior, audit existing claims that relied on this fallback to understand historical impact. Then replace the hardcoded value with a lookup against the claim's actual `BenefitPolicyRule.coveragePercent`, with a loud (logged, not silent) fallback only if genuinely no policy data is available.

---

## Summary Table

| # | Finding | Area | Blocking For |
|---|---|---|---|
| CF-1 | Claim state-machine disagreement | Financial/Backend | Any claim-workflow change |
| CF-2 | Visit hard-delete cascade | Database/Financial | Epic 1 first fix |
| CF-3 | Dual coverage engines | Medical/Financial | Epic 2 |
| CF-4 | Possible FK mismatch (pre-auth) | Database | Any pre-auth work |
| CF-5 | Destructive V66 migration | Database/Audit | Compliance sign-off |
| CF-6 | 10-year JWT, no revocation | Security | Security certification |
| CF-7 | Hardcoded 20% co-pay | Financial | Financial audit sign-off |

---

*Continue to [`04-security-review.md`](./04-security-review.md).*
