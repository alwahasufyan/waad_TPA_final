# 10 — Project DNA

> **This document is the foundation of the AlfaBeta TPA Engineering Framework (ATEF).**
> Everything in `01`–`09` is analysis. This document is synthesis: what this system *is*, at a level that should survive any individual refactor, feature, or team change.

---

## Why the System Exists

WaadCare/TBA-WAAD exists to let a Third Party Administrator honor its promises to three parties at once — the Employer who pays for coverage, the Member who receives care, and the Provider who delivers it — **without any of them having to trust the TPA's arithmetic on faith.** Every architectural choice this analysis surfaced that looked like "extra effort" (financial-identity re-validation on every claim save, balance-invariant assertions after every account mutation, immutable snapshot columns freezing coverage state at decision time, an insert-only audit log with a DB trigger blocking updates) traces back to this one purpose: **a TPA's product is not software, it's a defensible decision.** The software's job is to make that decision reproducible, auditable, and correct under financial and legal scrutiny, not just fast to compute.

This is a domain where "the claim was approved for 847.50 LYD" must be an answer the system can justify six months later, to an auditor, in the exact same terms it was computed the day it happened — not an answer that shifts because someone changed a coverage percentage in the meantime. That single requirement is the gravitational center of this entire codebase, and it is why the system's engineering is uneven: the modules closest to that requirement (Claim, ProviderAccount, BenefitPolicy, EligibilityCheck) are the most rigorously built modules in the system, and the modules furthest from it (dashboards, admin CRUD) are comparatively simple. **That unevenness is not a flaw to correct uniformly — it is a signal of where the system's real stakes are, and future investment should follow the same gradient.**

## How the System Should Evolve

This system should evolve the way it has actually evolved so far, minus the mistakes: **real production pressure surfaces real gaps, and the team fixes them with the same rigor it applied to the original design** — the V26–V45 migration chain is proof this instinct already exists; it just needs to happen *before* production discovers the gap, not after. Evolution should always:

1. **Preserve invariants before adding features.** If a new feature would make `Claim.validateFinancialIdentity()` or `ProviderAccount.assertBalanceInvariant()` harder to reason about, the feature's design is wrong, not the invariant.
2. **Extend the snapshot/immutability pattern to new financially- or medically-significant state**, rather than inventing a new pattern each time. When in doubt about how to freeze a decision at a point in time, look at how `ClaimLine`'s `*_snapshot` columns or `EligibilityCheck`'s denormalized audit fields did it, and do the same thing.
3. **Reconcile before consolidating, consolidate before adding.** The dual coverage-engine situation (`benefitpolicy` vs. `claim.ruleengine`) is what happens when this order is skipped — a second implementation gets added before the first is fully understood, and now both exist, neither is fully trusted, and un-tangling them is harder than building either one was. New work should default to *extending the existing canonical service*, and treat "I need a new rule engine" as a decision requiring explicit architectural sign-off, not a routine implementation choice.
4. **Grow bounded contexts deliberately, not by module-folder convenience.** `07-domain-analysis.md`'s bounded-context map is not decoration — it's the tool for answering "where does this new logic belong" before writing it, so the system doesn't accumulate a second, third, and fourth parallel implementation of something that already exists.

## What Must NEVER Change

These are not preferences. They are the properties that make this system trustworthy, and changing any of them silently would be a breach of the system's actual purpose, regardless of how the code looks:

- **A claim's approved amount must always be re-derivable from its inputs, and the system must refuse to persist a claim whose stored amount doesn't match that derivation.** (`Claim.validateFinancialIdentity()`)
- **A provider's running balance must always equal total approved minus total paid, with no drift tolerated.** (`ProviderAccount.assertBalanceInvariant()`)
- **Coverage decisions, once made, must never silently change because someone edited a policy or contract afterward.** Snapshot the decision-relevant values at the moment of decision; never re-derive history from current configuration.
- **A medical/financial audit record, once written, must never be silently altered.** Deletion may exist for legitimate exceptional purges (with strong re-authentication), but ordinary mutation must remain structurally impossible, not just discouraged.
- **A Visit remains the mandatory root of every Claim and PreAuthorization.** This is the system's one non-negotiable structural law — "no standalone claim creation" is what makes every claim traceable to a real clinical encounter. Do not add a code path that creates a Claim without a Visit, no matter how convenient a shortcut would be for some future integration.
- **Provider-submitted data can never silently override server-derived identity.** The `ProviderContextGuard` pattern (provider ID always comes from the authenticated session, never trusted from the request body) is a security law, not a convenience — any new provider-facing endpoint must follow it.
- **The Arabic language and RTL layout are not a localization feature bolted onto an English-first system — they are the primary experience.** Any new UI must be designed RTL-first, the way `RTLLayout.jsx` already centralizes it, not retrofitted.
- **Static, auditable role-based access must remain simple enough to reason about.** The 7-role model was a deliberate simplification away from dynamic permissions; do not reintroduce that complexity without an equally deliberate, documented reason.

## What Should ALWAYS Improve

- **The gap between documented behavior and actual behavior.** Every stale comment found in this analysis (the 3-tier vs. 2-tier coverage priority, the state-machine transition-table disagreement, the sync/async contradiction) represents a moment where someone's mental model of the system silently diverged from the system itself. Closing that gap — not adding new abstraction — is the highest-value, lowest-risk category of ongoing work.
- **Test coverage on the modules closest to the system's actual purpose.** Every new claim-financial or coverage-decision code path added without a corresponding test is debt against the system's core promise (reproducible, defensible decisions), not just ordinary technical debt.
- **The ratio of "rules enforced in one clearly-owned place" to "rules enforced independently in four places."** The system currently defends its invariants in up to four layers (DB CHECK, entity guard, service validation, frontend schema) with no single layer treated as authoritative — this is safe but expensive to change correctly. Moving toward one authoritative layer per rule, with the others as defense-in-depth *derived from* it rather than independently maintained, should be a standing goal.
- **Consistency of soft-delete, audit, and error-response conventions.** Not because inconsistency is currently causing visible harm, but because every new inconsistency compounds the cost of the next person's "which pattern do I follow" decision.

## Development Philosophy

Build for the auditor who reads this system's output six months from now, not for the developer who writes it today. Every decision the system makes on behalf of the business — a coverage percentage, an approved amount, a rejected claim line — should be reconstructable from stored data alone, without needing to know what the configuration looked like at query time. When a feature request would make that harder, push back on the feature's design, not on the principle.

## Architecture Philosophy

Modules are bounded contexts in practice, even where not named as such — respect that boundary before crossing it. When a new capability seems to require touching two contexts (e.g., a new coverage rule that also needs claim-line awareness), the correct move is almost always to extend the *owning* context's canonical service and have the other context call it — not to build a parallel implementation "for now" that becomes permanent by default, which is exactly how this system ended up with two coverage engines. Prefer Spring's existing domain-event backbone (`@TransactionalEventListener(phase=AFTER_COMMIT)`) for any new cross-module side effect — it is already proven, already correctly handles transaction-commit semantics, and keeps modules loosely coupled the way Settlement's dependency on Claim already demonstrates works well.

## Business Philosophy

The Employer, Member, and Provider are not abstract "tenants" or "accounts" — they are three distinct parties with genuinely different, sometimes competing interests, and the system's job is to be a fair, transparent referee between them, not an advocate for any one side. Every business rule (coverage limits, waiting periods, discount timing, co-pay calculation) exists because some real negotiated agreement between the TPA and these parties made it necessary — when a rule looks arbitrary in code, the right instinct is to go find out what real-world agreement it encodes before changing or removing it, not to assume it's incidental complexity.

## Financial Philosophy

Money calculations are never "close enough." The 0.05 rounding-tolerance check in `Claim.validateFinancialIdentity()` is the right shape of leniency — a tiny, explicit, intentional tolerance for floating-point/rounding reality, not an open-ended "trust the input" policy. Any new financial calculation added to this system should follow the same shape: compute independently, compare against the stored/submitted value, and throw rather than silently accept a mismatch. Magic-number fallbacks (like the flagged 20% co-pay default) are the financial philosophy's one confirmed violation in this codebase — they should be treated as bugs, not as acceptable defaults, precisely because they substitute a guess for a derivable, policy-driven truth.

## Medical Philosophy

The system automates financial adjudication; it *supports* medical adjudication. This is a deliberate and correct division of labor — clinical judgment (is this service medically appropriate, does this rejection reason genuinely apply) resists full codification, and the system correctly leaves final medical judgment to the `MEDICAL_REVIEWER` role rather than trying to encode clinical necessity rules that would be both incomplete and dangerous to over-trust. What the system *should* keep doing is making that human judgment auditable, scoped (reviewer-to-provider assignment), and structurally distinct from financial approval — never blur the line between "a person decided this was medically appropriate" and "a formula calculated this was financially payable."

## UI Philosophy

The interface should match how the people who actually use it work, not how a generic admin-panel template assumes they work. The Provider Portal's separation from the admin console, the barcode/QR-scanning integration for front-desk eligibility checks, and the bulk-Excel-import-with-progress-tracking for back-office data entry are all examples of the UI adapting to real operational rhythm rather than forcing operational rhythm to adapt to a generic CRUD grid. New UI work should keep asking "who is actually sitting at this screen, and what are they trying to get through quickly" before reaching for a standard table-plus-form pattern.

## Performance Philosophy

Optimize the paths that are both hot and expensive to get wrong — the DB-side SUM aggregation replacing an O(n) in-memory limit calculation in `BenefitPolicyCoverageService`, and the 17-index investment in the `claims` table, are both examples of performance work applied exactly where it matters (a claims-adjudication system that's slow at computing "has this member exceeded their limit" is not just slow, it's a business risk). Performance work on cosmetic or rarely-hit paths should stay secondary to correctness work in the same area — a fast wrong answer is worse than a slow right one in this domain.

## Security Philosophy

Defense-in-depth is already the system's instinct in the places that matter most (ProviderContextGuard refusing to trust client-supplied provider IDs, the custom 401 entry point, Actuator/Swagger locked to SUPER_ADMIN, redacted internal exception messages) — that instinct should be applied uniformly, not just where it happened to be built first. The one place this philosophy was not followed through — JWT expiration — is the clearest evidence that "the pattern exists, apply it everywhere" needs to be an explicit, ongoing discipline rather than an assumption. Every new authenticated endpoint should be built asking "what happens if the caller lies about who they are," the same question `ProviderContextGuard` already answers correctly for provider-scoped requests.

## Arabic UX Philosophy

Arabic-first is not a mode the application supports — it is the application's native language, and English is the accommodation. RTL direction must always be derived consistently from language (never independently toggleable, exactly as `RTLLayout.jsx` already enforces), and any new UI text should be authored in Arabic first, with English as the translation, not the reverse. The business vocabulary itself (Employer, Member, Provider, coverage, settlement) should read as naturally correct to a Libyan TPA operator as it does to an English-speaking engineer reading the code — this dual-fluency in the domain language is a real asset this system already has and should never be allowed to erode into "Arabic labels on an English mental model."

## Printing Philosophy

Printed and exported output represents the system's decisions leaving its own audit trail and entering someone else's filing cabinet — treat every PDF/print/Excel export with the same "this must be reproducible and correct" standard applied to the underlying data. A silently-broken Arabic font in a printed claim statement is not a cosmetic bug; it is the system failing at the exact moment its output needs to be trusted by someone outside the system (a provider, an auditor, an employer's finance department).

## PDF Philosophy

PDF generation should fail loudly, never silently. The current embedded-font approach for Arabic (Cairo via `IDENTITY_H`) is the right technical choice; the philosophy going forward is that any failure in that pipeline (missing font, template error, data-fetch failure) should produce a clear, logged, actionable error — never a document that looks superficially fine but silently misrepresents the data (wrong glyphs, missing amounts, stale branding).

## Reporting Philosophy

Reports are read-only projections of the same audited, snapshot-immutable truth the transactional system already guarantees — a report should never become a second, independently-computed version of a number the transactional system already owns. When a report needs "the approved amount," it should read the Claim's own stored, validated `approvedAmount`, not recompute a shadow value using report-specific logic that could drift from the source of truth over time.

## Configuration Philosophy

Configuration (feature flags, SLA days, system settings) should be changeable by the business without a deployment, but changing it must never retroactively rewrite history. The SLA-snapshot pattern (`Claim.slaDaysConfigured` frozen at submission time) is the model: configuration governs *future* behavior from the moment it changes, and every already-in-flight or completed record keeps the configuration that was actually in effect when it was created. Any new configurable value that affects a financial or coverage calculation should follow this same snapshot-at-decision-time discipline, not a live-lookup-at-read-time shortcut.

---

## Closing Statement

This system's DNA is **defensibility under scrutiny.** Every strength this analysis found — the financial-identity guard, the balance invariant, the immutable audit trail, the snapshot columns, the domain-event backbone, the pluggable eligibility rule chain — exists to answer one question convincingly, for any claim, at any point in the future: *"prove it."* Every weakness this analysis found — the two coverage engines, the state-machine documentation drift, the hardcoded co-pay fallback, the schema churn scars — represents a moment where that discipline slipped under real-world time pressure, not a moment where the team didn't know better.

The AlfaBeta TPA Engineering Framework, built on this document, should hold the system to the standard it has already proven it can meet in its best modules, and treat every gap identified in `08-technical-debt.md` as a **return to that standard**, not a departure into something new. Evolve the system by making more of it look like `Claim.validateFinancialIdentity()` and `EligibilityEngineServiceImpl`'s rule chain — not by replacing what's there with something unfamiliar.
