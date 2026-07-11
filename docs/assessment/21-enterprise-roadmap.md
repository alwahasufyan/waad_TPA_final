# 21 — Enterprise Roadmap

> Translates every finding from `01`–`20` into nine sequenced Epics, then into one final, groomable Engineering Backlog. Per the Constitution's Evolution Policy: *"Waad TPA is an evolving enterprise platform. Its strength comes from continuous refinement, not continuous reinvention."* No Epic here proposes a rewrite of any working component.

---

## Epic Overview

| Epic | Name | Priority | Business Value | Technical Risk | Complexity | Depends On | Sprint Order |
|---|---|---|---|---|---|---|---|
| 1 | Critical Stabilization | P0 | Very High | Low (fixes are narrow) | Low–Medium | None | 1 |
| 2 | Financial & Medical Integrity | P0 | Very High | Medium | Medium | Epic 1 | 2 |
| 3 | Operational UX | P1 | High | Low | Medium | Epic 1 | 3–4 |
| 4 | Reporting & Printing | P1 | High | Low | Medium | Epic 2 | 4–5 |
| 5 | Provider Portal Modernization | P1 | Very High | Medium | High | Epic 1, 3 | 5–7 |
| 6 | Medical Classification Engine | P2 | High (scale enabler) | Medium | High | Epic 2 | 6–8 |
| 7 | Notification Center | P2 | High | Low–Medium | Medium | Epic 2 | 7–9 |
| 8 | Mobile Readiness | P2 | Medium–High | Low | Medium | Epic 5 | 8–9 |
| 9 | Enterprise Polish | P3 | Medium | Very Low | Low | All above | 9–10 |

**Priority key**: P0 = must complete before certifying the system audit-ready; P1 = next-quarter value; P2 = greenfield capability, schedule once P0/P1 stable; P3 = continuous background cleanup, never blocks a release.

---

## Epic 1 — Critical Stabilization

**Priority:** P0 · **Business Value:** Very High (prevents data loss, closes a live security exposure, resolves workflow ambiguity staff may already be confused by) · **Technical Risk:** Low (each fix is narrow and well-understood) · **Complexity:** Low–Medium · **Dependencies:** None · **Sprint Order:** 1

**Scope:** CF-1 through CF-7 (`03-critical-findings.md`). Fix the Visit hard-delete cascade, resolve the Claim state-machine ambiguity, verify/confirm the pre-authorization FK question and the V66 data-loss question, shorten JWT expiration, and audit-then-fix the hardcoded co-pay fallback.

**Why first:** every other Epic touches modules whose behavior this Epic clarifies or protects. Building Provider Portal or Notification Center features on top of an unresolved claim state-machine ambiguity would compound the confusion, not just delay fixing it.

---

## Epic 2 — Financial & Medical Integrity

**Priority:** P0 · **Business Value:** Very High (Constitution's Single Source of Truth principle, directly) · **Technical Risk:** Medium (requires call-site auditing before any code change) · **Complexity:** Medium · **Dependencies:** Epic 1 · **Sprint Order:** 2

**Scope:** Reconcile the dual coverage engines (D3), add the missing financial/medical FK constraints (D13, D14), standardize soft-delete on `claims` and `visits` (D17, tied to CF-2's fix), close the two audit-log-system ambiguity (D12), unify the two email-service implementations (D11), fix internal documentation drift across the financial/medical core (D23).

**Why second:** this Epic hardens exactly the modules this assessment identified as the system's highest-value core (`05-financial-review.md`, `06-medical-review.md`). Every point of score improvement here directly serves the Constitution's Financial and Medical Philosophy.

---

## Epic 3 — Operational UX

**Priority:** P1 · **Business Value:** High (daily productivity for the largest user population: claim officers, medical reviewers, accountants) · **Technical Risk:** Low · **Complexity:** Medium · **Dependencies:** Epic 1 · **Sprint Order:** 3–4

**Scope:** Decompose the largest monolithic frontend files (D16), add keyboard row-navigation to `ClaimBatchEntry.jsx` (D49), consolidate the three parallel table components (D34), fix dead-role `@PreAuthorize` references and add auth rate limiting (D8, D9), retire deprecated API/UI surfaces (D27), clean up dead frontend code (D44).

**Why here:** this is the highest-leverage Epic for the internal staff who use the system all day, per the UI/UX Constitution's Primary Design Goal — and it makes every subsequent frontend Epic (5, 8, 9) safer to execute because the codebase will already be partially decomposed.

---

## Epic 4 — Reporting & Printing

**Priority:** P1 · **Business Value:** High (every report/PDF is, per the Constitution, "an official business document") · **Technical Risk:** Low · **Complexity:** Medium · **Dependencies:** Epic 2 (reports must read from a reconciled financial/coverage model) · **Sprint Order:** 4–5

**Scope:** Fix the silent PDF font-failure risk (D21) and add a CI smoke test for Arabic PDF rendering, confirm/implement async generation for large reports (D35), add watermark and signature/stamp-area support (D36), standardize report headers/footers, fix `ReportController`'s error-handling bypass (D32), confirm print/PDF visual parity.

---

## Epic 5 — Provider Portal Modernization

**Priority:** P1 · **Business Value:** Very High (primary external-facing data-entry surface; per the Visit-Centric architectural law, this is where most visits/claims actually originate) · **Technical Risk:** Medium (external-facing, higher blast radius for regressions) · **Complexity:** High · **Dependencies:** Epic 1, Epic 3 (shared decomposition patterns) · **Sprint Order:** 5–7

**Scope:** Full 5-phase modernization roadmap from `11-provider-portal-review.md` — stabilize (tests + decomposition of `ProviderClaimsSubmission.jsx`), responsive foundation, consolidate duplicated eligibility-check logic, close capability gaps (submission-status summary, evaluate offline support), ergonomic polish.

---

## Epic 6 — Medical Classification Engine

**Priority:** P2 · **Business Value:** High, specifically as a **scale enabler** — this Epic directly removes the developer-time bottleneck on onboarding new providers · **Technical Risk:** Medium (new capability, but built additively per `17-medical-classification-review.md`'s phased plan) · **Complexity:** High · **Dependencies:** Epic 2 (taxonomy/coverage model should be settled first) · **Sprint Order:** 6–8

**Scope:** Full 4-phase plan from `17-medical-classification-review.md` — fuzzy matching with confidence scoring (pg_trgm-based), human-review queue UI, alias-table learning loop, retirement of the offline Python-script workflow.

---

## Epic 7 — Notification Center

**Priority:** P2 · **Business Value:** High (closes a real, currently-mock-UI capability gap; leverages existing domain-event infrastructure) · **Technical Risk:** Low–Medium (the hard part — domain events — already exists and works) · **Complexity:** Medium · **Dependencies:** Epic 2 (event sources should be stable) · **Sprint Order:** 7–9

**Scope:** Immediate mock-bell disconnection/stopgap, then the full requirements from `16-notification-review.md` — persisted per-user notifications, in-app center UI, event-driven triggers off the existing `ClaimApprovedEvent`/`ClaimReversalEvent`/`ClaimSettledEvent` infrastructure, role-appropriate trigger mapping, polling-based delivery for v1.

---

## Epic 8 — Mobile Readiness

**Priority:** P2 · **Business Value:** Medium–High, concentrated specifically in the Provider Portal · **Technical Risk:** Low · **Complexity:** Medium · **Dependencies:** Epic 5 (responsive work is safer post-decomposition) · **Sprint Order:** 8–9

**Scope:** Tablet-responsive layout for Provider Portal screens (starting from the files that already have partial breakpoint usage), stacked/card fallback for `ClaimBatchEntry.jsx`'s wide table, verify/extend to `ClaimViewMedicalReview.jsx`, explicit product decision on whether true phone-width support is in scope.

---

## Epic 9 — Enterprise Polish

**Priority:** P3 · **Business Value:** Medium (compounding, low-urgency correctness/consistency) · **Technical Risk:** Very Low · **Complexity:** Low · **Dependencies:** All above (this is intentionally a rolling cleanup Epic, not a blocking gate) · **Sprint Order:** 9–10, continuous thereafter

**Scope:** All remaining 🟢 Low-severity items from `20-technical-debt.md` (D40–D50) — i18n data-source consolidation, dead-code removal, database sequence cleanup, module-naming disambiguation, date-library consolidation, and similar low-risk, high-cumulative-value cleanup.

---

# Final Deliverable — Official Engineering Backlog

> Every item includes Priority, Business Impact, Technical Impact, Estimated Risk, Recommended Sprint, and Status. Status is `Not Started` for all items as of this assessment (v1.0) — this document is the baseline for tracking going forward.

## Epic 1 — Critical Stabilization

| # | Item | Priority | Business Impact | Technical Impact | Risk | Sprint | Status |
|---|---|---|---|---|---|---|---|
| 1.1 | Decide REJECTED terminality; reconcile `ClaimStatus` vs `ClaimStateMachine` | P0 | Very High — workflow trust | High — core state machine | Low | 1 | Not Started |
| 1.2 | Fix `VisitService.delete()` hard-delete cascade | P0 | Very High — data loss prevention | Medium | Low | 1 | Not Started |
| 1.3 | Verify `claims.pre_authorization_id` FK target | P0 | Very High — traceability | Low (investigation) | Low | 1 | Not Started |
| 1.4 | Confirm V66 migration data-loss scope | P0 | High — compliance | Low (investigation) | Low | 1 | Not Started |
| 1.5 | Shorten JWT expiration + add revocation | P0 | Very High — security | Medium | Medium | 1 | Not Started |
| 1.6 | Audit and fix hardcoded 20% co-pay fallback | P0 | High — financial accuracy | Medium | Medium | 1 | Not Started |

## Epic 2 — Financial & Medical Integrity

| # | Item | Priority | Business Impact | Technical Impact | Risk | Sprint | Status |
|---|---|---|---|---|---|---|---|
| 2.1 | Audit and reconcile dual coverage engines | P0 | Very High — adjudication fairness | High | Medium | 2 | Not Started |
| 2.2 | Add missing FK constraints (pre-auth, visits, claim_lines, payments) | P1 | High — data integrity | Low (additive migration) | Low | 2 | Not Started |
| 2.3 | Standardize soft-delete on `claims`/`visits` | P1 | High — audit trail | Medium | Low | 2 | Not Started |
| 2.4 | Unify or clearly separate the two audit-log systems | P1 | Medium — compliance clarity | Medium | Low | 2 | Not Started |
| 2.5 | Consolidate two email-service implementations | P1 | Medium — operational safety | Medium | Low | 2 | Not Started |
| 2.6 | Fix stale documentation across financial/medical core | P2 | Low — maintainer trust | Low | Low | 2 | Not Started |

## Epic 3 — Operational UX

| # | Item | Priority | Business Impact | Technical Impact | Risk | Sprint | Status |
|---|---|---|---|---|---|---|---|
| 3.1 | Fix dead-role `@PreAuthorize` references | P1 | Medium — security clarity | Low | Low | 3 | Not Started |
| 3.2 | Add rate limiting to `/auth/*` | P1 | High — security | Medium | Low | 3 | Not Started |
| 3.3 | Decompose `ClaimBatchEntry.jsx` | P1 | High — change safety | High | Medium | 3 | Not Started |
| 3.4 | Add keyboard row-navigation to batch entry | P1 | Medium — productivity | Medium | Low | 4 | Not Started |
| 3.5 | Consolidate three unified table components | P2 | Medium — maintainability | High | Medium | 4 | Not Started |
| 3.6 | Retire deprecated API/UI surfaces | P2 | Low — cleanliness | Low | Low | 4 | Not Started |
| 3.7 | Unify the two API response envelopes | P2 | Medium — integration clarity | Medium | Low | 4 | Not Started |

## Epic 4 — Reporting & Printing

| # | Item | Priority | Business Impact | Technical Impact | Risk | Sprint | Status |
|---|---|---|---|---|---|---|---|
| 4.1 | Fix silent PDF Arabic-font failure mode | P0 | High — document integrity | Low | Low | 4 | Not Started |
| 4.2 | Add CI smoke test for Arabic PDF rendering | P1 | Medium — regression prevention | Low | Low | 4 | Not Started |
| 4.3 | Confirm/implement async generation for large reports | P1 | Medium — usability | Medium | Medium | 5 | Not Started |
| 4.4 | Add watermark support | P2 | Medium — document legitimacy | Medium | Low | 5 | Not Started |
| 4.5 | Add signature/stamp-area support | P2 | Medium — document legitimacy | Medium | Low | 5 | Not Started |
| 4.6 | Standardize report header/footer component | P2 | Medium — consistency | Medium | Low | 5 | Not Started |
| 4.7 | Fix `ReportController` error-handling bypass | P2 | Low — consistency | Low | Low | 5 | Not Started |

## Epic 5 — Provider Portal Modernization

| # | Item | Priority | Business Impact | Technical Impact | Risk | Sprint | Status |
|---|---|---|---|---|---|---|---|
| 5.1 | Add test coverage for claims-submission/visit-registration flows | P1 | High — regression safety | High | Medium | 5 | Not Started |
| 5.2 | Decompose `ProviderClaimsSubmission.jsx` | P1 | High — change safety | High | Medium | 5 | Not Started |
| 5.3 | Introduce responsive layout (Phase 2) | P1 | High — field usability | High | Medium | 6 | Not Started |
| 5.4 | Consolidate duplicated eligibility-check logic | P2 | Medium — consistency | Medium | Low | 6 | Not Started |
| 5.5 | Add provider-facing submission-status summary view | P2 | Medium — provider trust | Medium | Low | 7 | Not Started |
| 5.6 | Evaluate offline-queue support | P3 | Low–Medium (data-dependent) | High | Medium | 7 | Not Started |

## Epic 6 — Medical Classification Engine

| # | Item | Priority | Business Impact | Technical Impact | Risk | Sprint | Status |
|---|---|---|---|---|---|---|---|
| 6.1 | Build fuzzy-matching + confidence scoring (pg_trgm) | P2 | High — onboarding scale | High | Medium | 6 | Not Started |
| 6.2 | Build human-review queue UI | P2 | High — removes dev bottleneck | High | Medium | 7 | Not Started |
| 6.3 | Confirm/populate `ServiceAlias` as learning-loop foundation | P2 | Medium — long-term accuracy | Medium | Low | 7 | Not Started |
| 6.4 | Retire offline classification scripts | P3 | Low — cleanliness | Low | Low | 8 | Not Started |

## Epic 7 — Notification Center

| # | Item | Priority | Business Impact | Technical Impact | Risk | Sprint | Status |
|---|---|---|---|---|---|---|---|
| 7.1 | Disconnect/hide mock notification bell | P1 | High — user trust | Low | Low | 3 (independent, can start early) | Not Started |
| 7.2 | Build `Notification` entity + persistence | P2 | High — foundation | Medium | Low | 7 | Not Started |
| 7.3 | Build in-app notification center UI | P2 | High — core value | Medium | Low | 8 | Not Started |
| 7.4 | Wire domain events to notification triggers | P2 | High — core value | Medium | Low | 8 | Not Started |
| 7.5 | Wire Provider Portal claim-status into notifications | P3 | Medium — provider UX | Low | Low | 9 | Not Started |

## Epic 8 — Mobile Readiness

| # | Item | Priority | Business Impact | Technical Impact | Risk | Sprint | Status |
|---|---|---|---|---|---|---|---|
| 8.1 | Tablet-responsive layout for Provider Portal core screens | P2 | High — field usability | High | Medium | 8 | Not Started |
| 8.2 | Tablet-responsive fallback for `ClaimBatchEntry.jsx` table | P2 | Medium | Medium | Low | 9 | Not Started |
| 8.3 | Verify/extend responsiveness to `ClaimViewMedicalReview.jsx` | P3 | Medium | Medium | Low | 9 | Not Started |
| 8.4 | Product decision on phone-width scope | P3 | Low (clarity only) | None | None | 8 | Not Started |

## Epic 9 — Enterprise Polish

| # | Item | Priority | Business Impact | Technical Impact | Risk | Sprint | Status |
|---|---|---|---|---|---|---|---|
| 9.1 | Consolidate two i18n data sources | P3 | Low | Low | Low | 9 | Not Started |
| 9.2 | Migrate hardcoded menu labels into locale files | P3 | Low | Low | Low | 9 | Not Started |
| 9.3 | Fix hardcoded RTL-breaking style | P3 | Low | Low | Low | 9 | Not Started |
| 9.4 | Resolve/restore hidden Documents Library menu entry | P3 | Low | Low | Low | 9 | Not Started |
| 9.5 | Remove dead frontend scaffold/mock files | P3 | Low | Low | Low | 9 | Not Started |
| 9.6 | Drop unused `member_seq` database sequence | P3 | Low | Low | Low | 10 | Not Started |
| 9.7 | Resolve `admin`/`systemadmin` module naming overlap | P3 | Low | Medium | Low | 10 | Not Started |
| 9.8 | Consolidate repeated date-range CHECK logic | P3 | Low | Low | Low | 10 | Not Started |
| 9.9 | Verify `PdfCompanySettingsController` pre-login branding path | P3 | Low | Low | Low | 10 | Not Started |
| 9.10 | Consolidate `dayjs`/`date-fns` to one library | P3 | Low | Low | Low | 10 | Not Started |
| 9.11 | Add caching layer for stable reference data | P2 | Medium — performance | Medium | Low | 9 | Not Started |
| 9.12 | Add missing indexes to `claim_lines` | P2 | Medium — reporting performance | Low | Low | 9 | Not Started |
| 9.13 | Introduce CI schema/entity consistency check | P2 | Medium — prevents future churn | Medium | Low | 9 | Not Started |

---

## How to Use This Backlog

1. **Sprints 1–2 (Epic 1–2) are non-negotiable P0 work** and should be staffed first, per the Constitution's Decision Priority ("Business Rules... Business correctness always wins").
2. **Sprints 3–5 (Epic 3–4) can run partially in parallel** with the tail end of Epic 2 once the financial/medical core is stable, since they touch largely disjoint code paths (frontend UX, reporting/printing).
3. **Epic 5 (Provider Portal) should not start its responsive-layout work (5.3) until its own decomposition work (5.2) is done** — this sequencing is deliberate and reflected in the sprint numbers above.
4. **Epics 6–8 are genuinely parallelizable against each other** once their shared dependency (Epic 2) is complete, since Medical Classification, Notification Center, and Mobile Readiness touch almost entirely non-overlapping code.
5. **Epic 9 has no completion deadline** — per the Constitution's Technical Debt principle, it should run continuously in the background, with items pulled into whichever sprint has spare capacity, rather than being scheduled as a discrete phase.

## Final Note

Per the Constitution's Final Engineering Principle: *"Every improvement must leave the project in a better state than before."* Every item in this backlog satisfies that test individually — none require any other item to be done first except where explicitly noted as a dependency, and none require touching working business logic beyond what's named. This backlog is the roadmap the Executive Summary promised: a sequenced, risk-ranked, dependency-aware path from a 69/100 core-viability platform to a materially stronger one, built entirely through evolution of what already works.

---

*End of Waad TPA Enterprise Assessment v1.0.*
