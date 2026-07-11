# 08 — Backend Review

**Score: 70/100 (B−)** · Reviewed against `.claude/reviews/backend-review.md.txt` and `.claude/standards/clean-architecture.md.txt`, `.claude/standards/domain-driven-design.md.txt`.

---

## Architecture

| Check | Status | Evidence |
|---|---|---|
| Module ownership correct | ✓ | 18 modules under `modules/*`, each owning its own entities/controllers/services/repositories |
| Clean Architecture respected | ⚠ | Standard Spring layered-by-module (entity/controller/service/repository), not strict hexagonal — a reasonable, common choice, not a defect, but worth naming so it isn't mistaken for a full Clean Architecture implementation |
| No circular dependencies | Not exhaustively verified | No evidence of circular module dependencies found in this pass |
| Domain isolation maintained | ⚠ | Mostly good; `claim.ruleengine.*` living inside the `claim` module while overlapping `benefitpolicy`'s canonical responsibility is a domain-isolation violation (CF-3) |
| Business logic not duplicated | ⚠ | The dual coverage engines (CF-3) and dual email services (see below) are the two confirmed instances |

## Domain Layer

| Check | Status | Evidence |
|---|---|---|
| Entities protect invariants | ✓ Strong | `Claim.validateFinancialIdentity()`, `ProviderAccount.assertBalanceInvariant()`, `Member.validateState()` (barcode required for principals), `PreAuthorization`'s guarded transition methods — this is genuinely rich domain modeling, not an anemic model |
| Value Objects immutable | ⚠ | Value-object-shaped concepts (money, percentages, date ranges) are mostly plain primitives validated independently in up to four layers rather than encapsulated types — not a defect for a system this size, but a missed consolidation opportunity |
| Aggregate boundaries respected | ⚠ | The Visit/Claim cascade-ALL relationship creates real ambiguity about which entity is the true aggregate root (see CF-2 and `07-domain-analysis.md` in the prior system-analysis pass) |
| Domain Events used appropriately | ✓ | `ClaimApprovedEvent`/`ClaimReversalEvent`/`ClaimSettledEvent` via `@TransactionalEventListener(phase=AFTER_COMMIT)` — correct Spring pattern, genuinely loose coupling between Claim and Settlement |
| Business rules inside Domain | ✓ (mostly) | Financial/coverage/state-machine rules correctly live in entities/services, not controllers |

**Reject Anemic Domain Models** — per this checklist's own instruction, this codebase should **not** be rejected on this basis. `Claim`, `ProviderAccount`, `PreAuthorization`, and `Member` all carry real behavior and guarded invariants, not just getters/setters.

## Application Layer

| Check | Status | Evidence |
|---|---|---|
| Use Cases have one responsibility | ✓ (mostly) | Service classes are generally well-scoped per module |
| Commands modify state / Queries are read-only | ✓ | No CQRS framework, but the convention is followed informally and consistently |
| Transactions are minimal | ✓ | `@Transactional` used at appropriate granularity |
| Validation occurs before execution | ✓ | Bean Validation + service-layer business validation, consistently ordered |

## Infrastructure

| Check | Status | Evidence |
|---|---|---|
| Repositories persist only | ✓ | Spring Data JPA repositories, no business logic leakage observed |
| External APIs isolated | ✓ | Email sending isolated to dedicated service classes |
| Logging implemented | ✓ | Correlation-ID-based structured logging throughout |
| Retry policy exists | Not assessed | No retry/circuit-breaker infrastructure found for external calls (email SMTP) — low risk at current integration surface, worth revisiting if more external integrations are added |
| Configuration externalized | ✓ | `application.yml`/`application-{profile}.yml`, feature flags DB-first with YAML fallback |

## Database (from the backend's perspective)

Covered fully in `07-database-review.md`. Summary: strong CHECK-constraint discipline, real FK gaps, one confirmed destructive migration.

## APIs

| Check | Status | Evidence |
|---|---|---|
| REST conventions | ✓ (mostly) | 461 endpoints across 58 controllers, generally consistent resource-based paths |
| Validation | ✓ | `@Valid` + `GlobalExceptionHandler` |
| Pagination | ✓ | Present on list endpoints throughout |
| Filtering | ✓ | Query-parameter filtering widely supported |
| Error handling | ⚠ | Two structurally different response envelopes (`ApiResponse` success shape vs. `ApiError` failure shape) — a genuine API-consistency defect per the Constitution's API Principles ("APIs must be predictable, consistent") |
| Versioning | ⚠ | Overwhelmingly `/api/v1/*`, but `PreAuthEmailRequestController` (`/api/preauthorization/...`) and `ReportController` (`/api/reports/...`) omit the version segment |

**Additional API findings:**
- Recurring dead-role `@PreAuthorize` references (see `04-security-review.md`).
- Deprecated-but-live endpoints remain deployed: `VisitController.GET /all`/`GET /search`, entire `UnifiedSearchControllerDeprecated` class.
- Two live member-import pipelines (`/legacy-import` and `/unified-members/import`) — incremental migration never completed retirement of the old path.
- `ClaimController` and `ReportsController` (claim-scoped) both being large (26 and 9 endpoints respectively) alongside a separate `FinancialReportController` sharing the `/api/v1/reports` base path with the claim module's `ReportsController` — an organizational overlap, not a functional bug.
- Destructive/administrative endpoints (`DELETE /admin/system/reset`, `POST /seed-test-data`) exist as regular REST routes, SUPER_ADMIN-gated — should be confirmed disabled in the production deployment profile.

## Security (Backend)

Fully covered in `04-security-review.md`. Summary reference: RBAC well-structured, JWT expiration is the critical gap.

## Performance

Fully covered in `19-performance-review.md`. Summary reference: strong where it matters (claims indexing, DB-side aggregation), thin elsewhere (`claim_lines` indexing).

## Testing

| Check | Status | Evidence |
|---|---|---|
| Unit Tests | ⚠ | 19 backend test files total — concentrated appropriately on the highest-stakes logic (`ClaimStateMachineTest`, `BenefitPolicyCoverageServiceTest`, `FinancialRuleEngineServiceTest`, `CostCalculationServiceTest`, `CoverageEngineServiceTest`) but thin relative to 597 source files / 461 endpoints |
| Integration Tests | ✓ (partial) | `ClaimLifecycleIntegrationTest` exists — a genuinely good sign that the most critical workflow has *some* end-to-end coverage |
| API Tests | ⚠ | Limited — `BeneficiarySearchControllerTest` is one of the few controller-level tests found |
| Edge Cases | Not independently verified | Would require reading each test file's assertions in detail — recommend as a follow-up |

**Per the Constitution's Testing Principles** ("*Financial Calculations, Medical Rules, Eligibility, Settlement [require] Unit Tests, Integration Tests, Regression Tests*"), the *existence* of tests for Claim state machine, benefit coverage, and cost calculation is the right instinct — the gap is breadth (most modules have zero dedicated tests) rather than complete absence of testing discipline.

## Documentation

| Check | Status | Evidence |
|---|---|---|
| API documented | ⚠ | OpenAPI/Swagger configured (`OpenApiConfig`) and restricted to SUPER_ADMIN — exists but coverage/quality of individual endpoint docs not verified |
| Business rules documented | ✓ (in code) | Extensive, genuinely good inline comments explaining *why* (e.g., the V34 migration's "every column without an entity field is dropped" principle, `ProviderContract`'s "one active contract" invariant comment) |
| Configuration documented | ⚠ | `.env.example` exists and is reasonably annotated; no centralized configuration reference document found |

**Documentation drift findings** (Constitution: "*Outdated documentation is considered a defect*"): the `benefitpolicy` module's Javadoc describes a stale 3-tier coverage priority that no longer matches the 2-tier implementation; `ClaimStateMachine`'s own class Javadoc uses role names (`INSURANCE`/`REVIEWER`/`EMPLOYER`) that don't match the actual constants used in the same file; `ClaimApprovalEventListener`'s inline comments contradict each other about sync-vs-async execution. These are the kind of self-inconsistency the Constitution explicitly calls a defect, not a nice-to-have fix.

---

## Findings Requiring Action

1. **(Critical)** CF-1, CF-2, CF-3, CF-4 all have backend-layer root causes and are the priority.
2. **(High)** Unify the two API response envelopes.
3. **(High)** Fix internal documentation drift (three confirmed instances above) — low effort, removes real risk of a future engineer trusting a comment over the code.
4. **(Medium)** Expand test coverage outward from the already-tested financial/coverage core to Member, Provider, Visit, and Settlement modules.
5. **(Medium)** Consolidate the two email-service implementations (`common.email` vs `core.email`) — one respects dev/prod environment gating, the other does not.
6. **(Low)** Retire confirmed-deprecated endpoints once frontend usage is verified fully migrated.

## Decision

**⚠ Changes Required.** The backend's domain layer is a genuine strength (rich, invariant-protecting entities; correct event-driven module decoupling). The issues found are consistently in the *reconciliation* category (two engines, two envelopes, two email services, stale comments) rather than the *fundamental design* category — exactly the kind of debt the Evolution Policy describes as addressable through consolidation, not replacement.

---

*Continue to [`09-frontend-review.md`](./09-frontend-review.md).*
