# 09 — Improvement Roadmap

> **Principle: Evolution over Replacement.** Every recommendation below builds on the existing architecture, schema, and workflows. Nothing here proposes a rewrite, a framework migration, or a redesign of a working business process. Item numbers (T1–T44) reference `08-technical-debt.md`.

---

## Immediate (days, not sprints — low risk, high signal)

These are investigations or additive changes that carry near-zero risk of breaking existing behavior, but either close a real gap or resolve a dangerous ambiguity.

1. **Verify the `claims.pre_authorization_id` FK-target question (T7).** Confirm whether claims are actually linking to the correct pre-authorization table. This is a read-only investigation first — if it reveals a live bug, it becomes the single highest-priority code fix in this roadmap.
2. **Confirm whether the V66 payment-table recreation (T5) lost production data.** Check backups/history for any environment where V63/V66 ran against a populated database.
3. **Fix the recurring dead-role-reference bug class (T8).** Grep every `@PreAuthorize` for role names not present in `SystemRole` and correct them — this is a pure find-and-fix with no behavioral ambiguity once each site is checked against its intended access policy.
4. **Add the missing FK constraints identified in the database analysis (T12, T13)**, using `NOT VALID` + background `VALIDATE CONSTRAINT` to avoid locking production tables. Zero application-code changes required.
5. **Correct internal documentation drift** that could actively mislead a maintainer: the `benefitpolicy` 3-tier-vs-2-tier Javadoc (T18), `ClaimStateMachine`'s stale role-name table (T19), the sync/async contradiction in `ClaimApprovalEventListener` (T20), and the `isInsuranceAdmin` misleading name (T21, rename or add a clarifying comment — do not change behavior).
6. **Fix the one hardcoded RTL-breaking style (T37)** and the leftover dead files in the frontend tree (T39) — trivial, zero-risk cleanup.
7. **Decide and document, with the product owner, whether `REJECTED → APPROVED` should be reachable (T4).** This is a product decision disguised as a bug — resolve the ambiguity in writing before touching code.

---

## Short Term (1–2 months — contained, testable changes)

1. **Convert `VisitService.delete()` to soft-delete, or add a guard blocking deletion when non-DRAFT claims exist (T2).** This is the highest-priority *behavioral* fix in the entire register — it directly protects financial/audit data. Should ship with a regression test proving a Visit with a SETTLED claim cannot be hard-deleted.
2. **Replace the hardcoded 20% co-pay fallback with a policy-derived default (T6).** Requires care: audit existing claims that relied on the fallback before changing behavior going forward, to avoid silently changing historical financial expectations.
3. **Reconcile `ClaimStatus.getValidTransitions()` with `ClaimStateMachine.TRANSITION_MATRIX` (T4).** Once the product decision from Immediate item 7 is made, pick one class as the single source of truth (recommend `ClaimStateMachine`, since it's what actually executes) and either delete the enum's transition table or regenerate it from the state machine so the two can never diverge again.
4. **Add regression tests around the reconciled state machine and the financial-identity formula** — this is the cheapest place to start closing the test-coverage gap (T14), since it's the highest-stakes, most-already-understood logic in the system, and a single well-designed `ClaimStateMachineTest` (one already exists — extend it) pays for itself immediately.
5. **Add a CI-level schema/entity consistency check** (e.g., boot a test context against a freshly-migrated database and let Hibernate's schema validator catch mismatches) — directly prevents a recurrence of the V26–V45 fix-chain pattern (see `05-database-analysis.md` §9). This is a process investment, not a code change, and is one of the highest-leverage items in this entire roadmap relative to its cost.
6. **Shorten JWT expiration to a realistic window and add a minimal revocation mechanism (T1)** — e.g., a short-lived JWT (hours, not years) plus reliance on the already-preferred session-cookie path for long-lived sessions, or a simple denylist table checked by the JWT filter. This is security-critical but should be sequenced after the state-machine work above only because it requires coordinated frontend/backend testing of the refresh flow — not because it's less important.
7. **Resolve the OTP-vs-token password-reset inconsistency (T22)** by having the OTP flow also call `unlockAccount()` on success — a small, well-contained fix.
8. **Extract sub-sections of the 3–4 largest frontend form pages into presentational components (T15).** Start with `ProviderClaimsSubmission.jsx` (2,561 lines) since it's both the largest file and the highest-traffic provider-facing workflow — pure refactor, no visual or behavioral change, done incrementally with visual regression checks after each extraction.
9. **Add indexes to `claim_lines` (T27)** for `rejected`, `pricing_item_id`, `applied_category_id` — low-risk, immediately measurable performance benefit on a hot reporting table.

---

## Medium Term (1–2 quarters — coordinated, cross-cutting work)

1. **Resolve the dual coverage-engine question (T3).** This is the single most consequential architectural reconciliation in the codebase. Recommended approach: audit every call site of both `BenefitPolicyCoverageService` and `claim.ruleengine.*` to map which code paths actually execute in production, then either (a) formally document a deliberate division of labor if both are genuinely needed, or (b) migrate all call sites to the canonical service and deprecate/retire the rule-engine package. Given the Eligibility module's rule-chain design is the cleanest coverage-related code in the system (`07-domain-analysis.md` §6), consider it the reference pattern if any consolidation/refactor is undertaken.
2. **Resolve the Visit/Claim aggregate-boundary tension (T2, revisited architecturally).** Beyond the immediate soft-delete fix, decide explicitly (and document) which entity is the true aggregate root for cascade/lifecycle purposes, and align the cascade configuration accordingly.
3. **Build a `PreAuthorizationStateMachine` analogous to `ClaimStateMachine` (T17)**, and consider whether Member and ProviderContract lifecycles warrant the same treatment — bringing consistent engineering rigor to lifecycles that currently rely on scattered entity guards.
4. **Unify the two audit-log systems behind one interface (T11)**, or explicitly rename them to remove the shared-class-name ambiguity if both are intentionally kept as separate concerns (per `07-domain-analysis.md` §11, this is a legitimate design if made deliberate rather than accidental).
5. **Consolidate the two email-service implementations (T10)** onto the environment-safe pattern (`common.email`'s dev/prod gating), migrating the OTP and claim-notification call sites off `core.email.EmailService`.
6. **Standardize the soft-delete pattern (T16)** across the schema — adopt the full `deleted`/`deleted_at`/`deleted_by` triad as the standard for any financially/legally significant table, starting with reconciling `claims`' two independent lifecycle flags (`active` and `deleted_at`) with an explicit CHECK constraint or a single consolidated flag.
7. **Move toward one unified API response envelope (T9)**, additively: introduce a new shape (or extend `ApiResponse` to also carry error-shaped fields) and migrate endpoints incrementally, keeping both shapes valid during the transition so no client integration breaks.
8. **Consolidate the three frontend "unified/generic" table components (T33)** into one configurable component, migrating pages incrementally — no visible behavior change if done with care, but a meaningful long-term maintainability win.
9. **Retire the confirmed-deprecated API surfaces (T24, T25)** once frontend usage is confirmed to have fully migrated off them — `VisitController`'s deprecated endpoints, `UnifiedSearchControllerDeprecated`, and the legacy member-import pipeline.
10. **Expand automated test coverage systematically**, module by module, prioritized by financial/medical stakes: Claim (extend existing tests), Settlement/ProviderAccount balance invariants, BenefitPolicyCoverageService (extend existing tests), Eligibility rule chain, then outward to Member/Provider/Visit. Target meaningful coverage of every state-machine transition and every financial calculation path before expanding to full CRUD coverage.

---

## Long Term (2+ quarters — strategic, foundational investment)

1. **Formalize the bounded-context map produced in `07-domain-analysis.md` §1** as living architecture documentation, and use it going forward to evaluate where new features belong — this prevents future instances of the T3-style parallel-implementation problem before they happen, rather than only cleaning them up after the fact.
2. **Introduce value objects for the domain's recurring primitives** (money amounts, percentages, date ranges) where they currently exist as validated-in-five-places primitives (`07-domain-analysis.md` §13) — not a full DDD rewrite, but a targeted, incremental introduction starting with `CoveragePercent` (0–100, currently checked independently in the DB, entity annotations, and possibly frontend Yup) as a proof of concept.
3. **Establish a documented API-versioning and REST-naming convention** and apply it going forward (all new/changed endpoints under `/api/v1/`, consistent plural-resource naming) — retrofit the two known exceptions (T23) opportunistically rather than in a dedicated sweep.
4. **Build a lightweight internal "ATEF conformance" checklist** derived directly from this documentation set (state machines have a single source of truth, financial mutations have an assertable invariant, soft-delete follows the standard triad, every module writes to the unified audit interface) — apply it as a review gate for new modules, turning this analysis from a one-time snapshot into an ongoing engineering standard.
5. **Periodically re-run this same analysis** (or a scoped subset of it) as the system evolves, to catch documentation/implementation drift early rather than letting it accumulate into another V26–V45-scale reconciliation event.

---

## What This Roadmap Deliberately Does Not Recommend

- **No framework migration.** Spring Boot 3.5/Java 21 and React 19/Vite are current, well-supported choices — there is no technical justification for moving off either.
- **No rewrite of the Claim state machine, coverage engine, or settlement logic from scratch.** Every recommendation above is a reconciliation, consolidation, or hardening of what exists, because the underlying design (financial-identity guards, balance invariants, snapshot immutability, domain events) is sound and should be preserved, not replaced.
- **No database re-platforming.** PostgreSQL with Flyway is appropriate for this workload; the schema's issues are consistency/completeness issues, not a wrong-technology problem.
- **No wholesale RBAC redesign.** The static 7-role model is a deliberate, documented simplification (removal of dynamic permissions) that appears to be working; the fixes recommended above are about correcting stale references to it, not replacing it with something more complex.

---

*Continue to [`10-project-dna.md`](./10-project-dna.md) — the foundational document for the AlfaBeta TPA Engineering Framework.*
