# REPORTS-ENGINE-2 — Providers Report Vertical Slice

**Scope:** one production-quality, read-only Providers report proving the full
engine pattern (route → menu → filters → server-side query → pagination → sorting
→ summary → Excel export → Crystal print → RBAC/data-scope).
**Date:** 2026-07-17 · **No commit, no push.**

> **Revision (2026-07-17) — filter/column refinement (per review):**
> - The three text inputs (search / name / code) are replaced by **one provider picker** — a server-backed Autocomplete over registered providers (`providersService.getSelector()`); empty = all. New exact backend filter `providerId`.
> - The **provider code** column is replaced by **رقم العقد** (`activeContractCode`) — the code of the provider's ACTIVE modern contract, via a correlated `MAX(contractCode)` subquery on `ModernProviderContract` (single query, no N+1, deterministic).
> - **Discount rate is not shown** anywhere in the report (never was; confirmed).
> - Backend re-verified: `mvn` compile + `ProviderReportUnitTest` = **3 passed** after the change.

---

## 1. Exact files changed

**Backend — new (`modules/report`, provider module untouched):**
- `dto/ProviderReportRowDto.java` — read-only projection row (+ derived `contractStatus`).
- `dto/ProviderReportFilter.java` — optional server-side filter.
- `dto/ProviderReportSummaryDto.java` — backend aggregate summary.
- `dto/ProviderReportResponseDto.java` — `{ rows, summary, appliedFilters, generatedAt }`.
- `repository/ProviderReportQueryRepository.java` — dynamic JPQL (EntityManager); page/count/summary/export-all + sort allow-list.
- `service/ProviderReportService.java` — scope resolution, derived status, assembly, export bound.
- `export/ProviderReportExcelExporter.java` — SXSSF streaming Excel (Arabic, typed, sanitized).
- `controller/ProviderReportController.java` — `GET /api/v1/reports/providers` + `/export`.
- `src/test/java/.../report/ProviderReportUnitTest.java` — focused tests.

**Frontend — new:**
- `services/api/providers-report.service.js` — `getReport`, `exportReport` (blob).
- `hooks/reports/useProvidersReport.js` — engine adapter + filter→params.
- `pages/reports/providers/{index.jsx, ProvidersReportFilters.jsx, columns.jsx}`.

**Frontend — edited:**
- `routes/MainRoutes.jsx` — flat route `path: 'providers'` under `/reports` (guarded `report_domain_providers`).
- `menu-items/components.jsx` — `مقدمو الخدمة` repointed `/reports/domain/providers` → `/reports/providers`.

**Docs:** this file.

> Also present from Phase A (REPORTS-ENGINE-1): `components/reports/*` engine + `hooks/reports/useReportEngine.js` + `utils/exportUtils.js` sanitization.

---

## 2. Backend endpoint contract

```
GET /api/v1/reports/providers
  query: search,name,code,providerType,city,active,hasActiveContract,hasActivePriceList,
         expired,expiringSoon,contractStartFrom,contractStartTo,contractEndFrom,contractEndTo,
         expiringSoonDays(=30), page(=0), size(=25, max 200), sortBy(=name), sortDir(=asc)
  200 → ApiResponse{ data: { rows:{content[],page,size,totalElements,totalPages},
                            summary:{...}, appliedFilters:{...}, generatedAt } }

GET /api/v1/reports/providers/export      → xlsx (same filters; full filtered result)
  413 (PAYLOAD_TOO_LARGE) when filtered count > 50,000
```
Roles (both): `SUPER_ADMIN, ACCOUNTANT, FINANCE_VIEWER, MEDICAL_REVIEWER, PROVIDER_STAFF`. Others → 403.
Base path standardized on `/api/v1/reports/*`. No new `/api/reports` endpoint added. Legacy `/api/reports/claims/*` left intact & deprecated (not refactored — per scope).

---

## 3. Filters (all server-side)

`search` (name/code/city), `name`, `code`, `providerType` (enum, safe-parsed), `city`, `active`,
`hasActiveContract`, `hasActivePriceList`, `expired`, `expiringSoon`, `contractStart[From|To]`,
`contractEnd[From|To]`, `expiringSoonDays` (default 30, clamped 1–365). No hidden "today only"; default page size 25; explicit `name ASC` default sort.

---

## 4. Row DTO / projection

`ProviderReportRowDto`: id, code(licenceNumber), name, providerType, city, active, networkStatus,
contractStartDate, contractEndDate, updatedAt, activePriceListCount, activePriceListVersionNo,
+ derived `contractStatus` (ACTIVE/EXPIRING_SOON/EXPIRED/FUTURE/INACTIVE/NONE), `hasActivePriceList`.
Projected via a single JPQL `SELECT new …` on `Provider` with two correlated `PriceListVersion` subqueries — **no entity exposure, no N+1**.

**Scope decision (documented):** contract dates/status come from the **provider-level** `contractStartDate/contractEndDate` fields (authoritative at provider level), not the multi-row `providercontract.ProviderContract` (ambiguous "current contract" selection). Per the task's "do not invent / no N+1" rule, the *providercontract* contract code and cross-module pricing-item count are **intentionally omitted** from this slice.

---

## 5. Summary queries

One aggregate JPQL over the **same filtered set** (pagination aside):
`totalProviders, activeProviders, inactiveProviders, withActiveContracts, withoutActiveContracts,
withActivePriceLists, withoutActivePriceLists, expiredContracts, expiringSoonContracts`
(conditional `SUM(CASE …)` + a correlated price-list count). **Invariant:** `totalProviders == filtered count` (rows-count query shares the identical WHERE builder).

---

## 6. RBAC / data scoping

Enforced in the **query** via `resolveScope()`:
- provider user → `[user.providerId]` (empty ⇒ no rows);
- reviewer subject to isolation → `ReviewerProviderIsolationService.getAllowedProviderIds(user)`;
- SUPER_ADMIN / ACCOUNTANT / FINANCE_VIEWER → no restriction;
- employer admin / others → 403 (excluded from `@PreAuthorize`).
Reuses the **existing** `AuthorizationService` + `ReviewerProviderIsolationService` — no second authorization model. Empty scope list → sentinel `-1` ⇒ zero rows (safe).

FE resource `report_domain_providers` confirmed present in `roleAccessMap.js` (granted to SUPER_ADMIN; menu + route guarded).

---

## 7. Export strategy

Backend `GET …/export` → SXSSF streaming workbook of the **full filtered result** (same filters, bounded to 50,000; over-limit ⇒ 413). Arabic headers, typed numeric/date cells, title + generation time + applied filters, formula-injection sanitization (leading `= + - @ \t \r` → `'`). Frontend triggers a blob download with an Arabic dated filename. The FE never loads the full dataset into memory.

---

## 8. Frontend composition

`ProvidersReportPage` = `ReportShell` → `ReportFilterPanel(ProvidersReportFilters)` → `ReportSummaryCards` → `ReportTable` + `ReportPrintDocument`, driven by `useProvidersReport`→`useReportEngine`. **No operational provider table/actions reused.** Read-only throughout (only Refresh / Export / Print / filter apply-clear).

---

## 9. Route / menu changes

- Route: `/reports/providers` (guard `report_domain_providers`).
- Menu: `مقدمو الخدمة` → `/reports/providers` (was `/reports/domain/providers`). Other report menu items unchanged this task. `/reports/domain/:key` retained as fallback.

---

## 10. Print result

`ReportPrintDocument` (Crystal style): WAAD logo/org, title `تقرير مقدمي الخدمة`, generation date, applied filters, summary totals, read-only RTL table, repeating header, landscape, page-break-safe; no nav/actions/pagination. **Scope note:** print renders the current filtered page (≤ page size, user-raisable to 200); the *complete* dataset is delivered via Excel export (keeps the browser bounded).

---

## 11. Test results

`ProviderReportUnitTest` — **Tests run: 3, Failures: 0, Errors: 0** (`mvn -o test -Dtest=ProviderReportUnitTest -DskipTests=false`):
1. sort allow-list accepts known / rejects unknown (covers #5/#6);
2. `providerType` parse is null-safe (invalid/blank/null → null);
3. Excel export builds a valid RTL workbook **and** sanitizes formula-injection (`=…` → `'=…`) (covers #13/#15 structure + sanitization).

**Not executed here (documented gap):** DB-backed tests #1–4, #7–11, #14, #16–17 (filter correctness, summary reconciliation, pagination-invariance, provider/reviewer scoping, export-full-filter, large-export boundary, N+1). Reason: the project sets `<skipTests>true</skipTests>` and there is no configured test datastore (no `src/test/resources`, Postgres-only). These require a live DB / Testcontainers → **CI is the required merge gate**; not claimed as passed.

---

## 12. Build results

- Backend `mvn -o compile` → **BUILD SUCCESS** (0 errors).
- Backend deploy smoke: image rebuilt, container recreated → **healthy** (all report beans wired: controller/service/repository/exporter; app context started). Unauthenticated `GET /api/v1/reports/providers` and `…/export` → **401** (mapped + secured, not 404/500). JPQL execution still needs an authenticated call / CI (see §13).
- Frontend `eslint` (new files) → **0 errors/0 warnings**; `vite build` → **✓ success**.
- `git diff --check` → clean (only LF→CRLF notices).

---

## 13. Environment blockers / smoke test

- Tests skipped by pom default (`skipTests=true`) — overrode with `-DskipTests=false` for the unit test; full DB integration not runnable offline here → **CI gate required** (documented, not claimed as passed).
- Live authenticated smoke test of `/api/v1/reports/providers` was **not** performed (requires real login credentials, which are not materialized). Deployment/wiring smoke (app boots with the new report beans; endpoint mapped & secured) is recorded in the build log; JPQL execution is validated only at runtime by an authenticated call / CI.
- No framework version changes were made for the environment.

---

## 14. Performance observations

- Rows/summary use **one query each** with correlated subqueries — no N+1. Sort is allow-listed. Export is bounded (50k) and streamed (SXSSF, window 100).
- Potential index (NOT added — needs approval per scope): `price_list_version(provider_id, status)` would speed the `hasActivePriceList`/active-version subqueries on large datasets. Reported only.

---

## 15. No-mutation confirmation

No create/update/delete/activate/approve/settlement/upload endpoint exists under
`/api/v1/reports/providers`. The controller is annotated read-only intent; the
service/repository perform only `SELECT`/aggregate queries (`@Transactional(readOnly=true)`).
Frontend page renders no operational actions.

---

## 16. Exact proposed commit file set

```
docs/reports/REPORTS-ENGINE-2-PROVIDERS-VERTICAL-SLICE-REPORT.md
docs/reports/REPORTS-ENGINE-1-AUDIT-AND-PLAN.md            (Revision 2)
backend/src/main/java/com/waad/tba/modules/report/dto/ProviderReportRowDto.java
backend/src/main/java/com/waad/tba/modules/report/dto/ProviderReportFilter.java
backend/src/main/java/com/waad/tba/modules/report/dto/ProviderReportSummaryDto.java
backend/src/main/java/com/waad/tba/modules/report/dto/ProviderReportResponseDto.java
backend/src/main/java/com/waad/tba/modules/report/repository/ProviderReportQueryRepository.java
backend/src/main/java/com/waad/tba/modules/report/service/ProviderReportService.java
backend/src/main/java/com/waad/tba/modules/report/export/ProviderReportExcelExporter.java
backend/src/main/java/com/waad/tba/modules/report/controller/ProviderReportController.java
backend/src/test/java/com/waad/tba/modules/report/ProviderReportUnitTest.java
frontend/src/components/reports/*            (Phase A engine)
frontend/src/hooks/reports/useReportEngine.js
frontend/src/hooks/reports/useProvidersReport.js
frontend/src/services/api/providers-report.service.js
frontend/src/pages/reports/providers/{index.jsx,ProvidersReportFilters.jsx,columns.jsx}
frontend/src/routes/MainRoutes.jsx
frontend/src/menu-items/components.jsx
frontend/src/utils/exportUtils.js            (sanitization)
```

---

## 17. Confirmation — no unrelated scope changed

No changes to: provider CRUD, contracts workflow, price-list upload/versioning, claims,
settlements, PreAuthorization, medical dictionary/classification, taxonomy,
monitoring/backup, or environment files. No DB migration. Claims report **not** refactored.
Only additive report code + the single Providers menu/route repoint.

> Note: this task's diff also carries **pre-existing uncommitted work** from earlier sessions (dashboard UI, header/login, pre-auth 500 fix, etc.). The commit set in §16 is the REPORTS-ENGINE-2 subset only; those unrelated changes should be committed separately.

---

## 18. Recommended next report

**Employers** (`/reports/employers`) — simple, independent, single-source (employer + member-count aggregate), reuses the same engine + this slice's backend pattern; low risk. Then **Beneficiaries**, then refactor **Claims** onto the engine.

---

**REPORTS-ENGINE-2 PROVIDERS REPORT READY FOR CLEAN COMMIT**

*(pending the CI/DB-integration gate for the DB-backed test cases noted in §11 & §13 — these are documented as not-run in this offline environment, not claimed as passed.)*
