# 07 — Domain Analysis

> A Domain-Driven-Design reading of the system as implemented — not as a DDD textbook would design it from scratch, but as it actually exists, so future work can build on real boundaries rather than idealized ones.

---

## 1. Bounded Contexts

The codebase does not use explicit DDD terminology (no `domain`/`application`/`infrastructure` layering, no dedicated "bounded context" packages), but the module boundaries (`backend/src/main/java/com/waad/tba/modules/*`) map reasonably cleanly onto five *de facto* bounded contexts:

| Bounded Context | Modules | Core Concern |
|---|---|---|
| **Master Data** | employer, member, provider, providercontract, medicaltaxonomy | Who exists, who's covered, who provides care, at what price |
| **Coverage** | benefitpolicy, eligibility | What is covered, how much, under what conditions |
| **Clinical/Financial Pipeline** | visit, preauthorization, claim | The actual encounter → adjudication → payable-amount lifecycle |
| **Settlement** | settlement | Money actually owed and paid |
| **Platform** | auth, rbac, audit, report, pdf, dashboard, admin, systemadmin | Cross-cutting concerns serving the above |

The **Clinical/Financial Pipeline** context is the system's true core domain (in DDD terms, its "core subdomain" — the thing that differentiates this TPA platform from a generic CRUD app). **Coverage** is a close second — it's the rules engine that makes adjudication defensible. **Master Data** and **Settlement** are important but more "generic subdomain"-shaped (many TPA/insurance systems need employer/provider/payment management; comparatively few need this system's specific claim state machine and financial-identity guards). **Platform** is textbook "supporting subdomain."

**Context boundary leakage:** the clean five-context picture above is complicated by the confirmed existence of `claim.ruleengine.*` — a coverage/limit rule-engine implementation living *inside* the Clinical/Financial Pipeline context that appears to duplicate responsibilities the Coverage context (`benefitpolicy.BenefitPolicyCoverageService`) already owns and explicitly claims as canonical. This is the single most significant bounded-context violation found in this analysis — either the two are meant to compose (e.g. one drives claim-time enforcement, the other drives visit-time preview) and that division of labor is undocumented, or one is genuinely dead/superseded code that never got removed.

## 2. Aggregates

Using the DDD definition (a cluster of entities/value objects with one aggregate root that guards invariants for the whole cluster):

| Aggregate Root | Members | Invariant(s) Enforced |
|---|---|---|
| **Claim** | ClaimLine (cascade ALL, orphanRemoval), ClaimAttachment | Financial identity (`validateFinancialIdentity()`), architectural rules (visit required, PA-required-lines must link a PreAuthorization), state-machine legality (via `ClaimStateMachine`, external to the entity but tightly coupled) |
| **Visit** | VisitAttachment; *also* transitively owns Claim/EligibilityCheck via cascade ALL (see §Aggregate Boundary Concern below) | Category-before-service selection, provider-ID security enforcement |
| **ProviderContract** | ProviderContractPricingItem (cascade PERSIST/MERGE only — deliberately *not* REMOVE, to preserve pricing history) | "One active contract per provider" (DB-enforced via partial unique index) |
| **BenefitPolicy** | BenefitPolicyRule | Coverage-percent bounds (0–100, DB-enforced), date-range validity |
| **PreAuthorization** | (attachments not confirmed as cascade-owned) | Two-phase reservation (`reservedAmount` never deducts the real limit; only claim approval does), status-guarded amount mutations |
| **ProviderAccount** | AccountTransaction (implicitly, via balance derivation) | `runningBalance == totalApproved − totalPaid` (hard invariant, asserted after every mutation) |
| **Member** | MemberAttribute, MemberDeductible, MemberPolicyAssignment | Principal-must-have-barcode; self-referencing family hierarchy |
| **EligibilityCheck** | (none — a single immutable record) | Append-only; never updated or deleted |

**Aggregate Boundary Concern:** `Visit`'s `cascade = ALL` relationship to both `Claim` and `EligibilityCheck` means Visit is, structurally, the *actual* aggregate root over Claim in Hibernate's eyes — but Claim independently enforces its own strict invariants (financial identity, state machine) as if it were its own aggregate root. This is a **real DDD tension**: two entities both behaving like aggregate roots over the same data, with Visit's cascade behavior (a hard delete of a Visit cascades to hard-delete its Claims) capable of silently violating Claim's own carefully-guarded invariants (e.g., deleting a SETTLED claim's parent Visit bypasses every one of Claim's lifecycle protections). This is the domain-model root cause of the data-integrity risk flagged operationally in `02-business-workflows.md` §5 and `08-technical-debt.md`.

## 3. Business Rules — Where They Actually Live

Business rules in this codebase are enforced at up to **four layers simultaneously**, with varying consistency:

1. **Database CHECK constraints** — the "last line of defense," genuinely used for financial non-negativity, enum validity, and date ranges (see `05-database-analysis.md` §5).
2. **Entity `@PrePersist`/`@PreUpdate` guards** — the dominant pattern for structural/architectural rules (Visit-required-on-Claim, category-before-service, barcode-required-on-principal-member).
3. **Service-layer validation** — the dominant pattern for cross-entity business rules (coverage/limit checks, waiting periods, state-machine legality).
4. **Frontend Yup schemas** — first-line UX validation, not confirmed to be kept in lockstep with layers 1–3 (see `06-ui-ux-audit.md` §4).

This is a defensible strategy (defense-in-depth), and the discipline is real — but it means "where is rule X enforced" doesn't have one consistent answer across the codebase, which raises the cost of confidently changing any given business rule (a change might need updates in up to four places, and missing one produces the exact class of "entity vs. DB mismatch" bug that dominated the V26–V45 migration history).

## 4. Financial Integrity

This is the domain's best-engineered aspect. Three independent, hard-enforced invariants exist:

1. **`Claim.validateFinancialIdentity()`** — re-derives the expected `netPayableAmount` from `requestedAmount`, `patientCoPay`, `appliedDiscountPercent`, `refusedAmount`, and `discountBeforeRejection`, and throws `IllegalStateException` if the persisted value deviates beyond a 0.05 rounding tolerance. Runs on every `@PrePersist`/`@PreUpdate` — not optional, not bypassable via a direct field set.
2. **`ProviderAccount.assertBalanceInvariant()`** — enforces `runningBalance == totalApproved − totalPaid` after every credit/debit/reversal operation.
3. **Database-level double-entry constraints on `account_transactions`** — `balance_after = balance_before ± amount`, direction-checked against `transaction_type`.

The one confirmed weak point in an otherwise strong financial-integrity story: **the hardcoded 20% patient co-pay fallback** in `Claim.calculateFields()` when `patientCoPay` isn't explicitly set. This bypasses the policy's actual configured coverage percentage and is the single clearest instance of a "magic number" undermining an otherwise rule-driven financial system — see `08-technical-debt.md` for severity.

## 5. Medical Integrity

Medical-decision integrity is expressed through:
- **Per-line clinical rejection**: `ClaimLine.rejected` + `rejectionReasonCode` (from a proper lookup table, `claim_rejection_reasons`), distinct from financial rejection categories (`priceExcessRefused`, `limitRefused`).
- **Reviewer scoping**: `medical_reviewer_providers` restricts which providers' claims a given `MEDICAL_REVIEWER` may see/act on — a real clinical-governance control, not just a UI filter (enforced via `ReviewerScopeController`/`MedicalReviewerProviderAssignmentController`).
- **The `NEEDS_CORRECTION` state-machine transition is exclusively `MEDICAL_REVIEWER`-gated** — the one transition in the entire Claim state machine restricted to a single role, signaling the system treats "this claim needs clinical correction" as a privileged clinical judgment distinct from financial review.
- **Immutable medical audit trail** (`medical_audit_logs`, DB-trigger-enforced no-UPDATE) — every state transition and review action is captured with before/after JSONB.

**Gap:** there is no dedicated "medical necessity" or "clinical coding validity" rule engine distinct from the financial coverage engine — medical judgment is captured as free-text rejection reasons and reviewer discretion, not as a structured rule set. This may be entirely appropriate (clinical judgment often resists full codification), but it's worth naming explicitly as a domain boundary: **this system automates financial adjudication rigorously; it supports, rather than automates, medical adjudication.**

## 6. Eligibility

Architecturally the **cleanest** part of the domain — a genuinely pluggable rule-chain (`EligibilityRule` Spring beans, `@Order`-sorted), hard/soft failure semantics correctly separated, and a sound "always audit, even on internal error, but never let the audit write block the actual decision" design. See `03-module-catalog.md` §eligibility for the full rule sequence.

The eligibility rule chain is the best template in this codebase for how the Coverage bounded context *should* be organized — worth using as the reference pattern if `claim.ruleengine.*` is ever consolidated into `benefitpolicy` (see §1 above and `09-improvement-roadmap.md`).

## 7. Benefit Engine

`BenefitPolicyCoverageService` is explicitly documented as the "single source of truth for coverage decisions" and largely earns that title through engineering rigor: DB-side SUM aggregation for limit-usage calculation (a documented performance fix over an earlier in-memory approach), a real category-hierarchy-aware resolution algorithm (parent-category fallback), and four independently-tracked limit scopes (per-category/rule, annual-per-member, lifetime-per-member, per-family).

**Two confirmed gaps between documentation and implementation:**
1. The service's own Javadoc describes a 3-tier priority (`SERVICE_RULE > CATEGORY_RULE > POLICY_DEFAULT`); the actual implementation is 2-tier (service-level rules were removed upstream, per a comment referencing "V228" — evidence of pre-repo history). The doc should be corrected to avoid misleading future maintainers into believing service-level overrides still work.
2. **The "canonical" claim vs. the parallel `claim.ruleengine.*` system** (repeated finding, see §1) — this is the most consequential open question in the entire domain model, because if both systems are live on different code paths, two members with identical policies could receive different coverage decisions depending on which path adjudicated their claim.

**Privilege-sensitive logic embedded in a shared service:** the "internal staff backlog bypass" (ignoring policy effective-date windows for historical data entry) lives directly inside `BenefitPolicyCoverageService`, a service also used for live, real-time coverage decisions. This conflates two different concerns — "what is a member's real-time coverage" and "how do we backfill historical records" — inside one class, which is a coupling risk even though each individual behavior is reasonable in isolation.

## 8. Pricing

Pricing is provider-contract-driven, not manually entered per claim — `ProviderContractPricingItem` is the sole source of `unitPrice` for a claim line, with the contract's `discountPercent` and `discountBeforeRejection` flag snapshotted onto the Claim at approval time (correct immutability-at-decision-time design, consistent with the rest of the domain's snapshot philosophy).

The pricing-item-to-medical-category linkage went through a deliberate normalize-after-denormalize reconciliation (V43: free-text category names back-filled to a proper FK via exact+fuzzy matching, explicitly to avoid a runtime performance cost) — a genuinely well-reasoned trade-off, not schema neglect.

**Gap:** the documented invariant "only ONE active contract per provider" is DB-enforced (partial unique index), but pricing itself doesn't appear to have an equivalent "no overlapping active pricing items for the same category" guard confirmed in this pass — worth a targeted check if contract amendments/renewals ever produce a window where two pricing items for the same category are simultaneously active.

## 9. Settlement

Modeled as **monthly aggregate reconciliation**, not strict per-claim settlement — a deliberate and sensible domain choice for how TPA-to-provider payments actually work in practice (providers get paid periodically for a batch of approved claims, not claim-by-claim). The override/reason-required controls on `PaymentService` (`addPayment`, `updatePayment`, `deletePayment`) are genuine compliance-grade guards, not just UI conveniences.

**Domain-event-driven integration** with Claim (`ClaimApprovedEvent`, `ClaimReversalEvent`) is architecturally correct — Settlement doesn't reach into Claim's internals, it reacts to published events. The one confirmed defect here is *documentation*, not design: `ClaimApprovalEventListener`'s own inline comments contradict each other about sync-vs-async execution (it is, in fact, synchronous, `AFTER_COMMIT`, no `@Async`) — this matters because an accountant approving a claim is, in reality, waiting on the provider-account credit to complete before their HTTP response returns, which should be reflected correctly in the code's own self-documentation.

## 10. Configuration

`common.guard.FeatureGuard` implements a DB-first feature-flag pattern with an `application.yml` fallback and an "internal staff always bypasses flags" escape hatch — a reasonable, standard pattern. `SystemSettingsController` exposes typed, categorized settings (e.g. `claim-sla-days`) with audit-relevant read/write role separation (read: SUPER_ADMIN+MEDICAL_REVIEWER; write: SUPER_ADMIN only).

**SLA configuration is correctly snapshotted onto each Claim at submission time** (`slaDaysConfigured`) rather than looked up live — meaning a later SLA policy change doesn't retroactively alter the due date of claims already in flight. This is the same snapshot-immutability philosophy applied consistently and correctly here.

## 11. Audit

Two structurally different audit systems, as detailed in `03-module-catalog.md` §audit and §systemadmin:
- `modules.audit.AuditLog` (table `medical_audit_logs`) — immutable, insert-only, DB-trigger-enforced, JSONB diffs, correlation IDs — this is genuinely "legal-grade" in design.
- `modules.systemadmin.AuditLog` (table `audit_logs`) — mutable, general-purpose, used for user/role/module-access administration.

**From a DDD lens**, having two differently-scoped audit concerns is not inherently wrong — "clinical/financial decision audit" and "system administration audit" are arguably different domain concerns with different retention/immutability requirements, and could legitimately be two separate aggregates. What's missing is an **explicit statement of that boundary** (currently, the split reads as accidental — same class name in two packages — rather than a deliberate two-context design) and a shared *interface* so that any future module doesn't have to guess which of the two to write to, or forget to write to either.

## 12. Domain Events

Confirmed event types: `ClaimApprovedEvent`, `ClaimReversalEvent`, `ClaimSettledEvent` (listener referenced but not deep-read in this pass). All consumed via `@TransactionalEventListener(phase = AFTER_COMMIT)` — the correct Spring pattern for "only act on this event if the originating transaction actually committed," which is important here specifically because these events trigger financial account mutations that must never fire against a rolled-back claim approval.

**This is a real, working domain-event backbone** — a positive structural signal, since it means Settlement's dependency on Claim is already loosely coupled (event-driven) rather than a direct service-to-service call. If the system ever needs to add new claim-approval side effects (e.g., a notification, a downstream export), the event infrastructure to do so cleanly already exists and doesn't need to be built from scratch.

## 13. DDD Compliance — Overall Assessment

| DDD Concept | Present? | Assessment |
|---|---|---|
| Bounded contexts | Implicit (module boundaries) | Reasonably clean at the module level; one confirmed context-boundary violation (`claim.ruleengine` vs. `benefitpolicy`) |
| Aggregate roots with enforced invariants | Yes, strongly for Claim/ProviderAccount/BenefitPolicy | Real invariant enforcement, not just naming — a genuine strength |
| Aggregate boundary clarity | Partially | Visit/Claim cascade relationship creates a real boundary tension (§2) |
| Domain events | Yes | Correctly used for Claim→Settlement integration; a strong, reusable pattern |
| Value objects | Largely absent | Most "value-object-shaped" concepts (money amounts, percentages, date ranges) are plain primitives with validation scattered across layers rather than encapsulated types — not a defect per se (many pragmatic Java/Spring codebases skip this), but a missed opportunity for centralizing e.g. "a coverage percentage is always 0–100" in one reusable type instead of five independent CHECK constraints (`05-database-analysis.md` §5) |
| Ubiquitous language | Yes, strongly | Arabic/English domain terms are consistent between code, comments, database, and UI — Employer, Member, Provider, Visit, Claim, Coverage, Settlement mean the same thing everywhere in this codebase, which is a real and valuable form of DDD compliance often missing in less disciplined systems |
| Explicit application/domain/infrastructure layering | No | Standard Spring layered-by-module structure (entity/controller/service/repository), not a strict hexagonal/clean-architecture separation — a reasonable, common choice for a Spring Boot monolith of this size, not a criticism |

**Overall**: this domain model is **substantively DDD-informed without being DDD-orthodox** — it gets the things that matter most for a financial/medical adjudication system right (aggregate invariants, snapshot immutability, domain events, ubiquitous language) while skipping the more ceremonial DDD patterns (explicit value objects, hexagonal layering) that would add process overhead without necessarily improving correctness for a system of this size. The recommendation for ATEF is **not** to retrofit full DDD ceremony, but to close the two concrete structural gaps identified here (the Visit/Claim aggregate-boundary tension, and the dual coverage-engine ambiguity) — these are the places where the *absence* of a clear boundary has already produced observable inconsistencies, not places where DDD purity is missing for its own sake.

---

*Continue to [`08-technical-debt.md`](./08-technical-debt.md) for a consolidated, severity-ranked list of every issue surfaced across this analysis.*
