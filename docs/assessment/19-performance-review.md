# 19 — Performance Review

**Score: 65/100 (C)** · Reviewed against `.claude/reviews/performance-review.md.txt` and `.claude/standards/performance-standards.md.txt`.

---

## Queries / Database Performance

| Check | Status | Evidence |
|---|---|---|
| Indexed queries | ✓ (mostly) | `claims` table has 17 targeted composite/partial indexes — genuine, evidenced production tuning |
| No N+1 queries | Not exhaustively verified | No systematic N+1 audit performed; JPA/Hibernate relationship mappings were reviewed for correctness but not profiled for lazy-loading N+1 patterns at runtime |
| Efficient joins | ✓ (where verified) | `BenefitPolicyCoverageService`'s limit-usage calculation was specifically documented as fixed from an O(n) in-memory sum to a DB-side SUM aggregation — direct evidence of a real, measured performance fix |
| Batch operations optimized | ✓ | `ClaimBatch`-centric workflow, bulk Excel import pipelines throughout |

**Gap:** `claim_lines` — a high-volume child table central to financial reporting/reconciliation, with 40+ columns — has only 3 indexes, with no index on `rejected`, `pricing_item_id`, or `applied_category_id`. Given this table's role in report generation (`12-reporting-review.md`), this is the most concrete, checkable database-performance risk identified.

## Application Performance

| Check | Status | Evidence |
|---|---|---|
| Heavy tasks run in background | ⚠ | No confirmed background-job/queue infrastructure for report generation or bulk operations beyond the frontend's own progress-tracking context — see `12-reporting-review.md`'s async-report-generation open question |
| Caching used appropriately | ⚠ | No dedicated caching layer (Redis, Caffeine, etc.) found anywhere in the backend — the Constitution recommends caching only stable reference data (Benefit Plans, Provider Directory, Employer Directory, Procedure/Diagnosis Catalog); none of this appears to be cached today, meaning every request re-reads relatively static reference data from the database |
| Minimal memory usage | Not assessed | Requires runtime profiling, outside this pass's scope |
| No duplicated calculations | ⚠ | The dual coverage-engine issue (CF-3) is a performance concern in addition to a correctness concern — if both engines are genuinely live on different paths, that's redundant computation, not just redundant code |
| Efficient object mapping | Not assessed | DTO mapping patterns were not specifically profiled |

**Finding:** The absence of any caching layer for genuinely stable reference data (medical taxonomy, provider directory, benefit policy structure) is a real, addressable performance opportunity — these are exactly the kind of low-churn, high-read datasets the Constitution names as appropriate caching candidates, and every claim/eligibility check currently re-queries them from PostgreSQL.

## Frontend Performance

| Check | Status | Evidence |
|---|---|---|
| Lazy loading | ✓ | `Loadable(lazy(() => import(...)))` used broadly across the route table (`routes/MainRoutes.jsx`) — a genuinely good, consistently-applied performance pattern |
| Optimized bundles | ✓ (partial) | `vite.config.mjs` has deliberate manual chunking (excel, xlsx, pdfjs, react-pdf, charts, motion, lodash, mui-x, mrt, vendor) — real bundle-splitting engineering effort, not a default Vite config left untouched |
| Image optimization | Not assessed | Not independently verified |
| Skeleton/loading states | ✓ | Widely present per `09-frontend-review.md`/`10-ui-ux-review.md` findings |
| No unnecessary API calls | ⚠ | No React Query/SWR request-deduplication layer exists — direct `useEffect`+Axios calls per page mean there is no automatic caching or deduplication of identical in-flight/recent requests across components |

**Bundle-size risk concentration:** The largest source files (`ProviderClaimsSubmission.jsx` at 2,561 lines, `ClaimBatchEntry.jsx` at 2,085 lines) are large enough to be a measurable contributor to their route's JS bundle size, even with code-splitting in place — decomposition (already recommended in `09`/`11` for maintainability reasons) would likely also yield a real, if secondary, performance benefit.

## Batch Processing

| Check | Status | Evidence |
|---|---|---|
| Bulk operations supported | ✓ | Excel import for members, providers, pricing, medical categories |
| Progress tracking | ✓ | `GlobalImportProgressContext` — a genuine, deliberate UX-performance investment |
| Retry mechanism | Not confirmed | Not verified for bulk import failure/partial-failure handling |
| Error reporting | ✓ | `member_import_errors`, `provider_service_price_import_logs` — dedicated error-detail tables exist |
| Partial failure handling | ✓ (evidenced by dedicated error tables) | The existence of per-row error logging tables strongly suggests partial-failure handling (continue-on-row-error rather than abort-on-first-error) is implemented, though not independently traced line-by-line |

## Concurrency

| Check | Status | Evidence |
|---|---|---|
| Optimistic locking where appropriate | ✓ Strong | `@Version` on `Claim` (explicitly documented to prevent double-deduction races), `ProviderAccount`, `PreAuthorization`, `Visit` — this is real, correctly-targeted concurrency protection on exactly the entities where a race condition would have financial consequences |
| No race conditions | ✓ (for the entities with `@Version`) | |
| Duplicate processing prevented | ✓ | `PaymentService.addPayment()`'s remaining-amount validation prevents duplicate/excess payment |
| Safe parallel execution | Not assessed | No evidence of unsafe shared mutable state found, but not exhaustively verified |

## Caching

Per the Constitution's explicit guidance ("Cache only stable reference data... Do NOT cache Claims, Financial calculations, Settlement status unless invalidation strategy exists") — the current state (no caching anywhere) is **safe** (no risk of stale financial data) but **leaves a real, low-risk performance opportunity on the table** for exactly the data category the Constitution says is safe to cache (medical taxonomy, provider directory).

## Monitoring

| Check | Status | Evidence |
|---|---|---|
| Application logs | ✓ | Structured, correlation-ID-based logging |
| Error logs | ✓ | `GlobalExceptionHandler` comprehensive coverage |
| Slow query logs | Not confirmed | No evidence of slow-query logging configuration found |
| Performance metrics | ⚠ | Spring Boot Actuator is present and locked to SUPER_ADMIN (a good security choice), but whether metrics are actively exported to any monitoring system (Prometheus, etc.) was not confirmed |
| Health checks | ✓ | `docker-compose.yml` health checks configured for db/backend containers |

## Scalability

| Check | Status | Evidence |
|---|---|---|
| Stateless services | ⚠ | Session-cookie-based auth (the preferred path) implies server-side session state — this is a reasonable, common trade-off for the current single-instance deployment shape, but would need sticky-session or shared-session-store consideration before horizontal scaling |
| Horizontal scaling supported | Not assessed | `docker-compose.yml` deploys single instances per service with resource limits, not a scaled/clustered topology — appropriate for current scale, not yet tested for horizontal growth |
| Background workers scalable | N/A | No background worker infrastructure exists yet to evaluate |
| Database growth considered | ⚠ | The `claim_lines` indexing gap (above) is the concrete near-term database-growth risk |

## Performance Risks Identified

1. Thin indexing on `claim_lines` relative to its reporting/reconciliation load.
2. No caching layer for stable reference data (taxonomy, provider directory, benefit policy structure).
3. Unconfirmed synchronous generation of potentially-large reports (Financial Consolidation, Beneficiaries) — see `12-reporting-review.md`.
4. Large, undecomposed frontend files contributing to bundle size on the highest-traffic routes.
5. Session-based auth's implication for future horizontal scaling — not an immediate risk at current scale, but worth planning for before the platform needs to scale beyond a single backend instance.

---

## Findings Requiring Action

1. **(High)** Add the missing indexes to `claim_lines` (shared finding with `07-database-review.md`).
2. **(Medium)** Introduce a caching layer for stable reference data (medical taxonomy, provider directory, benefit policy structure) — low risk per the Constitution's own guidance, real performance upside.
3. **(Medium)** Confirm/implement asynchronous generation for the largest reports (shared finding with `12-reporting-review.md`).
4. **(Low)** Profile for N+1 query patterns systematically, particularly in claim-listing and report-generation code paths.
5. **(Low)** Plan session-state strategy ahead of any future horizontal-scaling initiative.

## Decision

**✅ Approve, with Changes Required.** Performance engineering has clearly happened where the team judged it mattered most (claims indexing, the documented O(n)-to-SQL-aggregation fix in benefit calculation, frontend bundle chunking, optimistic locking on financially-sensitive entities) — this is targeted, evidence-driven investment, not neglect. The gaps are in areas that haven't yet needed attention at current scale (caching, `claim_lines` indexing, async reporting) rather than signs of a system straining under its current load.

---

*Continue to [`20-technical-debt.md`](./20-technical-debt.md).*
