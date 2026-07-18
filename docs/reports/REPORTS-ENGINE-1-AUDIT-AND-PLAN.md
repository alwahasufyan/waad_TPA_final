# REPORTS-ENGINE-1 — Audit & Implementation Plan

> ## Revision 2 — APPROVED (2026-07-17), with mandatory modifications
>
> Decisions 1–5 approved (flat routes; unify settlements into tabs; reuse `BeneficiariesReport`/`BenefitPolicyReport`; drop audit report; standardize backend on `/api/v1/reports/*`). Additional binding modifications:
>
> 1. **No metadata layer** — remove/retire `reportRegistry.js`, `reportEngine.js`, the domain registry and `/reports/domain/:key`. Flow is strictly **routes → page → api → print → excel**.
> 2. **Every report is 100% independent** — must NOT reuse operational `ClaimsTable`/`ClaimsFilters`/reviewer workspace. Build lightweight read-only report-only components (`ClaimsReportTable`, `ClaimsReportFilters`, …).
> 3. **Filtering is fully server-side** — never fetch all rows then filter in React.
> 4. **Export = the full filtered result set** (e.g. 213 rows), NOT the current page.
> 5. **Print = Crystal-Reports style** — logo, title, date, applied filters, summary, table, page numbers — not a bare HTML print.
> 6. **No CRUD** — remove every إضافة/تعديل/حذف/اعتماد/رفض/رفع button, even if it comes from a shared component.
> 7. **A real report ENGINE** — all reports share Header/Filters/Summary/Grid/Print/Excel so a new report takes ~1 hour. (Shared *components*, not a metadata registry.)
> 8. **Large reports** — server pagination + **Excel streaming** (no 50k rows in memory).
> 9. **System statistics = Business Intelligence** (claims counts, top provider/employer/service, total reimbursements/approvals) — NOT monitoring.
> 10. **Two new reports added:** `تقرير النشاط اليومي` (Today's Operations: today's claims/approvals, pending, rejected, new members) and `تقرير الأداء` (Operational Performance: avg review/approval time, claims per reviewer, claims per provider).
>
> Final report set (flat routes): claims, beneficiaries, employers, providers, contracts, price-lists, benefit-policies, settlements(tabs), system-statistics(BI), **today-operations (new)**, **performance (new)**. Audit dropped.

**Status:** AUDIT READY FOR APPROVAL — *no implementation performed, nothing committed/pushed.*
**Scope:** Complete the WAAD Reporting Center as a standalone, read-only analytical workspace.
**Date:** 2026-07-17

> This document is **Phase 1 (audit) + the implementation plan only**. No report code was written. A few read-only inspections of existing files were done to produce this inventory.

---

## 0. Executive summary

A reporting engine already exists and is **partially** built. The good news: the current architecture is **already namespaced under `/reports/*`** — there are **no report routes that redirect to operational list pages** (the premise's worst case is not present in the registry). The real problem is **incompleteness**:

- The menu points every item to a generic **domain landing page** (`/reports/domain/:key`), a two-hop navigation, and **6 of 10 domains have no active report** behind them (members, employers, providers, contracts, audit, system-analytics are empty; price-lists & benefit-policies are `planned`).
- Only **Claims** and **Financial/Settlement** reports are real, working, read-only pages.
- There is **no shared report shell / filters / table / print-layout** component set — each existing report re-implements its own layout.
- Backend report endpoints are **split across two base paths** (`/api/reports` and `/api/v1/reports`) and three modules.
- Several report pages are **imported but never routed** (orphaned).
- Excel export exists (ExcelJS) but **lacks CSV/formula-injection sanitization**.
- **Audit report decision (per instruction):** an audit log already exists at `/…/medical-audit-logs`. We will **NOT** build `/reports/audit`; we drop it from the Reporting Center and menu, and only improve the existing page separately (out of this task's report set).

**Recommendation:** keep and reuse the working Claims + Financial reports and the ExcelJS/react-to-print patterns; build a **shared report foundation** (shell/filters/table/print/export); repoint the menu to **flat `/reports/<domain>` routes**; add the missing read-only reports domain-by-domain; standardize the backend on **`/api/v1/reports/*`** inside the existing `modules/report` package.

---

## 1. Existing report inventory

Legend for status: **COMPLETE** / **PARTIAL** / **PLACEHOLDER** / **REDIRECTS_TO_OPERATIONAL** / **DUPLICATED** / **MISSING** / **ORPHANED** (imported, not routed).

| # | Report / feature | FE route | Backend endpoint | Status | Export | Print | Permission (FE resource) | Problems | Recommended action |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Reporting Center home | `/reports` | — (static registry) | PARTIAL | – | – | `report_center` | Cards go to `/reports/domain/:key`, many empty | Rebuild as category landing → flat report routes |
| 2 | Domain landing | `/reports/domain/:domainKey` | — | PARTIAL | – | – | `report_center` | Generic two-hop; empty for 6 domains | Deprecate/redirect to flat routes (keep as fallback) |
| 3 | **Claims report** | `/reports/claims` | `useClaimsReport` → claims query; `/api/reports/claims/html`, `/claims/pdf` | **PARTIAL→good base** | Excel (ExcelJS) ✅ | react-to-print ✅ | `report_domain_claims` | Read-only ✅; needs standard shell + server pagination review | Keep; refactor onto shared shell; verify columns/summary vs spec |
| 4 | Claim statement preview | `/reports/claims/statement-preview` | `/api/v1/reports/member-statement/{id}` | PARTIAL | – | ✅ | `claims` | Drill-down/print helper | Keep as read-only detail |
| 5 | Financial consolidation | `/reports/financial-consolidation` | `/api/v1/reports/financial-consolidation` | PARTIAL | via service | ✅? | `report_domain_financial_settlements` | Works; not on shared shell | Fold into `/reports/settlements` family |
| 6 | Accountant profit | `/reports/accountant-profit` | `/api/v1/reports/company-profit` | PARTIAL | – | – | `report_domain_financial_settlements` | Works | Keep under settlements/financial |
| 7 | Provider settlement summary | `/reports/provider-settlement-summary`, `/reports/unified` | `/api/v1/reports/provider-settlement`, `/adjudication` | PARTIAL | Excel (POI backend) ✅ | ✅ | `report_domain_financial_settlements` | Two routes to same page | Consolidate → `/reports/settlements` |
| 8 | Beneficiaries report | (none) | (none dedicated) | **ORPHANED** (`BeneficiariesReports.jsx` imported, not routed) | ExcelJS | react-to-print | `report_domain_members` | No route, no backend report endpoint | Build `/reports/beneficiaries` (reuse page skeleton) |
| 9 | Financial reports (legacy) | (none) | — | ORPHANED (`FinancialReports.jsx`) | – | ✅ | — | Not routed | Evaluate; likely superseded → remove |
| 10 | Visits report | (none) | — | ORPHANED (`VisitsReport`) | – | – | — | Not routed | Out of task scope; leave/remove |
| 11 | Pre-approvals report | (none) | provider preauth exists | ORPHANED (`PreApprovalsReport`) | – | – | `report_pre_approvals` | Not routed | Optional: pre-auth under claims domain |
| 12 | Benefit policy report | (none) | — | ORPHANED (`BenefitPolicyReport`) | – | – | `report_benefit_policy` | Not routed | Build `/reports/benefit-policies` |
| 13 | Employer dashboard | (none) | — | ORPHANED (`EmployerDashboard`) | – | – | — | Not routed | Not a report; ignore |
| 14 | Members report | `/reports/domain/members` (empty) | — | **MISSING** | – | – | `report_domain_members` | No active registry report | Build `/reports/beneficiaries` |
| 15 | Employers report | `/reports/domain/employers` (empty) | — | **MISSING** | – | – | `report_domain_employers` | Empty | Build `/reports/employers` |
| 16 | Providers report | `/reports/domain/providers` (empty) | — | **MISSING** | – | – | `report_domain_providers` | Empty | Build `/reports/providers` |
| 17 | Contracts report | `/reports/domain/contracts` (empty) | — | **MISSING** | – | – | `report_domain_contracts` *(missing in RBAC)* | Empty + no RBAC grant | Build `/reports/contracts` + add RBAC resource |
| 18 | Price-lists report | `/reports/domain/price-lists` (planned) | — | **MISSING** (registry `planned`, route null) | – | – | `report_domain_price_lists` | Planned only | Build `/reports/price-lists` |
| 19 | Benefit policies report | `/reports/domain/benefit-policies` (planned) | — | **MISSING** | – | – | `report_domain_benefit_policies` | Planned only | Build `/reports/benefit-policies` |
| 20 | Settlements report (report-oriented) | via financial routes | `/api/v1/reports/provider-settlement` | PARTIAL | POI | ✅ | `report_domain_financial_settlements` | Fragmented across 3 routes | Unify → `/reports/settlements` |
| 21 | **Audit report** | `/reports/domain/audit` (empty) | Medical audit log services exist | **DROP** (per instruction) | POI (audit) | – | `report_domain_audit` | Already exists at `/…/medical-audit-logs` | **Do NOT build** `/reports/audit`; remove from center+menu |
| 22 | System statistics | `/reports/domain/system-analytics` (empty) | dashboard summary + pre-auth stats exist | **MISSING** | – | – | `report_domain_system_analytics` | Empty | Build read-only `/reports/system-statistics` |
| 23 | Provider portal reports | `/provider/reports/{claims,pre-auth,visits}` | `ProviderReportsController` + `ProviderReportExcelService` (POI) | COMPLETE (provider surface) | POI ✅ | – | `provider_portal` | Separate surface | Leave untouched (not part of admin center) |

---

## 2. Current routes and redirects

- Report namespace: **`/reports`** (children in `MainRoutes.jsx` ~L946–1012): `''`, `domain/:domainKey`, `financial-consolidation`, `accountant-profit`, `provider-settlement-summary`, `claims`, `claims/statement-preview`, `unified`.
- **No operational redirects** found in the report registry or these routes (all targets are `/reports/*` or `/provider/reports/*`). ✅
- Menu (`menu-items/components.jsx`, group `group-reports-center` → collapse `مركز التقارير`) points 10 items to **`/reports/domain/<key>`** (claims, members, employers, providers, contracts, price-lists, benefit-policies, financial-settlements, audit, system-analytics). This is the main correction target (repoint to flat routes; drop audit).

---

## 3. Reusable existing components & patterns

- **Excel:** `utils/exportUtils.js` → `exportToExcel()` using **ExcelJS**, with typed columns (string/number/currency/date), Arabic labels, width inference, and company branding. *Gap:* no CSV/formula-injection sanitization (`=,+,-,@`).
- **Print:** `react-to-print` (`useReactToPrint`) already used in Claims, Beneficiaries (orphan), Financial, ProviderSettlement. *Gap:* no shared `ReportPrintLayout`; batch-only print components exist (`BatchPrintReport.jsx`).
- **Page header:** `components/tba/ModernPageHeader`.
- **FE report API service:** `services/api/reports.service.js` (financial-summary, settlement-summary, financial-consolidation, company-profit, adjudication, provider-settlement, member-statement, summary).
- **Data hooks:** `hooks/useClaimsReport.js` (filters + fetch + status labels), `hooks/useEmployerScope.js` (RBAC scope).
- **Backend Excel (POI):** member/audit/benefit/settlement/taxonomy exporters — reference for streaming/bounded exports.

---

## 4. Backend report endpoints already present

| Module | Class | Base | Endpoints | Notes |
|---|---|---|---|---|
| `modules/report` | `ReportController` | `/api/reports` | `GET /claims/html`, `GET /claims/pdf` | HTML/PDF claim report |
| `modules/report` | `FinancialReportController` | `/api/v1/reports` | `GET /financial-consolidation`, `GET /company-profit` | roles: SUPER_ADMIN/ACCOUNTANT/FINANCE_VIEWER |
| `modules/report` | `ReportDataService` | — | `getClaimReportData(claimIds, onlyRejected, batchCode)` | read projection |
| `modules/report` | `CompanyProfitReportService`, `FinancialConsolidationService`, `PdfExportService` | — | aggregate + PDF | reuse |
| `modules/claim` | `ReportsController` | `/api/v1/reports` | `GET /adjudication`, `GET /provider-settlement`, `GET /member-statement/{id}` | read-only DTOs |
| `modules/claim` | `AdjudicationReportService`, `ProviderSettlementReportService`, `ProviderSettlementExcelExporter` | — | POI export | reuse |
| `modules/provider` | `ProviderReportsController`, `ProviderReportsService`, `ProviderReportExcelService` | provider portal | claims/preauth/visits | separate surface |

**Inconsistency:** base paths split between `/api/reports` and `/api/v1/reports`, and report logic lives in 3 modules. Also backend authorization is **role-based `@PreAuthorize`** while the frontend guards on **resource strings** (`report_domain_*`) — the two must be kept consistent.

---

## 5. Missing reports (to build)

Dedicated read-only report **pages + routes + backend read endpoints**:

1. `/reports/beneficiaries` (members)
2. `/reports/employers`
3. `/reports/providers`
4. `/reports/contracts`
5. `/reports/price-lists`
6. `/reports/benefit-policies`
7. `/reports/settlements` (unify existing financial/settlement pages)
8. `/reports/system-statistics`

Keep/refactor existing: `/reports/claims` (+ statement preview drill-down).
**Drop:** `/reports/audit` (per instruction).

---

## 6. Duplicate / placeholder / orphaned reports

- **Orphaned imports** (imported in `MainRoutes.jsx`, 0 route usages): `BeneficiariesReports`, `FinancialReports`, `VisitsReport`, `PreApprovalsReport`, `BenefitPolicyReport`, `EmployerDashboard`. → reuse `BeneficiariesReports`/`BenefitPolicyReport` skeletons for the new routes; remove genuinely dead ones after confirmation.
- **Duplicate route target:** registry `REP-CLM-001` and `REP-CLM-003` both route to `/reports/claims`.
- **Fragmented settlements:** `/reports/provider-settlement-summary`, `/reports/unified`, `/reports/financial-consolidation`, `/reports/accountant-profit` overlap → consolidate under one `/reports/settlements` with sub-views/tabs.
- **Empty domain pages:** members/employers/providers/contracts/audit/system-analytics.

---

## 7. Menu corrections

Target menu (`group-reports-center`), each item → **flat dedicated route**, RBAC-hidden when unauthorized, Arabic labels/icons preserved:

```
مركز التقارير  → /reports
  المطالبات          → /reports/claims
  المستفيدون         → /reports/beneficiaries
  جهات العمل         → /reports/employers
  مقدمو الخدمة       → /reports/providers
  العقود             → /reports/contracts
  قوائم الأسعار      → /reports/price-lists
  وثائق المنافع      → /reports/benefit-policies
  التسويات المالية   → /reports/settlements
  إحصائيات النظام    → /reports/system-statistics
  (التدقيق — REMOVED from center; use existing /…/medical-audit-logs)
```

Remove the `/reports/domain/<key>` indirection from the menu (keep the domain route as an internal fallback only, or delete after migration).

---

## 8. Proposed route map (frontend)

```
/reports                      → ReportsCenterHome (category landing; cards → flat routes)
/reports/claims               → ClaimsReport (existing, refactor to shared shell)
/reports/beneficiaries        → BeneficiariesReport (new)
/reports/employers            → EmployersReport (new)
/reports/providers            → ProvidersReport (new)
/reports/contracts            → ContractsReport (new)
/reports/price-lists          → PriceListsReport (new)
/reports/benefit-policies     → BenefitPoliciesReport (new)
/reports/settlements          → SettlementsReport (unify financial/settlement)
/reports/system-statistics    → SystemStatisticsReport (new, read-only)
/reports/domain/:domainKey    → (kept as deprecated fallback → redirect to flat)
```
Existing financial sub-routes retained as aliases/tabs during transition.

---

## 9. Backend architecture

Consolidate into the **existing `modules/report`** package (avoid a parallel `modules/reporting`), standardizing on **`/api/v1/reports/*`**:

```
modules/report/
  controller/   ReportController (v1), one endpoint family per report
  service/      one read-only query service per report (reuse existing where present)
  repository/   dedicated @Query projections (no entity exposure)
  dto/          read-only row DTOs + summary DTOs
  projection/   interface projections for aggregates
  export/       Excel (POI) + reuse ProviderSettlementExcelExporter pattern
```

Principles: server-side filtering + pagination; dedicated aggregate queries for totals; **no reuse of mutating operational service methods**; enforce **scope in queries** (employer/provider/reviewer isolation). Migrate `/api/reports/claims/*` to `/api/v1/reports/claims/*` with a temporary alias.

**API shape (per report):**
```
GET /api/v1/reports/<name>          → { rows (paged), summary }
GET /api/v1/reports/<name>/summary  → totals only (optional; may be embedded)
GET /api/v1/reports/<name>/export   → Excel (same filters)
```

---

## 10. Shared frontend foundation (to create)

- `ReportPageShell` — header (title, description, last-refreshed, print/export/refresh buttons) + slots.
- `ReportFilters` — collapsible RTL filter panel (apply/clear + active chips) via `ActiveFilterChips`.
- `ReportSummaryCards` — KPI/totals row.
- `ReportTable` — **read-only**, server-paginated, sortable, sticky header, horizontal scroll, **no row actions**.
- `ReportPrintLayout` — branded RTL print doc (logo, title, filters, date, totals, table; no nav/actions).
- `ReportExportButton` — calls backend `/export` with current filters (or ExcelJS from rows).
- `EmptyReportState` — "لا توجد نتائج تطابق الفلاتر المحددة" + clear-filters.

Reuse `exportToExcel` (add sanitization) and `useReactToPrint`.

---

## 11. Report-by-report specification (fields / filters / summary / source)

For each report the fields/filters/summary follow the task brief. Data source mapping:

- **Claims** (`/reports/claims`, exists): source = claim repository read projection (`ReportDataService`/`useClaimsReport`). Verify columns: claim no, service date, submission date, member, card, employer, provider, summary, gross, member share, covered share, approved, rejected, status. Summary: counts (total/submitted/approved/rejected/needs-correction) + totals (gross/approved/member-share/company-share) from **authoritative backend fields** only; document any derived formula. No fabricated line values.
- **Beneficiaries** (`/reports/beneficiaries`, new): source = member repository. Filters: employer, policy, active, gender, DOB range (if stored), card, name, created, eligibility. Summary: total/active/inactive, by employer/policy.
- **Employers** (`/reports/employers`, new): source = employer repository (+ policy + member count aggregate). Avoid N+1 (use count query).
- **Providers** (`/reports/providers`, new): source = provider repository (+ contract status). Period claim count/approved value **only if efficiently supported** (aggregate query), else omit.
- **Contracts** (`/reports/contracts`, new): source = provider-contract repository + price-list version. Summary: active/expired/expiring-soon/without-active-price-list.
- **Price-lists** (`/reports/price-lists`, new): source = classification/price-list version tables (existing import module read-only). Columns incl. counts (services/new/changed/unchanged/removed). Drill-down = read-only version preview. **No upload/classify/publish.**
- **Benefit-policies** (`/reports/benefit-policies`, new): source = benefit policy repository. Optional read-only detail (categories/limits/percentages/waiting).
- **Settlements** (`/reports/settlements`, unify): source = `AdjudicationReportService` / `ProviderSettlementReportService` / `FinancialConsolidationService`. **No payment/settlement actions.**
- **System-statistics** (`/reports/system-statistics`, new): source = existing dashboard summary + pre-auth stats + counts; read-only only, **no monitoring/backup/danger-zone.**
- **Audit:** **DROPPED** here.

---

## 12. RBAC mapping

Existing resources (in `config/roleAccessMap.js`): `report_center`, `report_domain_claims|members|employers|providers|price_lists|benefit_policies|financial_settlements|audit|system_analytics`, plus `report_claims`, `report_financial`, etc.

- **Gap:** `report_domain_contracts` is **not present** in `roleAccessMap.js` → add it and grant to appropriate roles.
- Keep using `report_domain_*` for menu/route guards. Do **not** invent a broad new scheme; optionally add semantic aliases `VIEW_FINANCIAL_REPORTS`/`VIEW_SYSTEM_STATISTICS` mapped onto the existing financial/system resources if clearer.
- **Backend/RBAC consistency:** backend currently uses **role-based** `@PreAuthorize`. New report endpoints must enforce the **same** role/scope as the frontend resource, and enforce **data scope in queries** (employer admin → own employer; provider staff → own provider; reviewer isolation; financial/audit → admin/finance roles).

---

## 13. Excel export strategy

- Reuse `utils/exportUtils.js` (ExcelJS) for row-based exports; for large/heavy reports use **backend POI streaming** endpoints (`/export`) like the provider-settlement exporter.
- **Must add:** formula-injection sanitization for text starting with `=,+,-,@` (prefix `'`).
- Requirements enforced: same active filters as preview; Arabic headers; stable column order; numeric/date/currency cells keep native types; metadata row (title/time/filters); filename `<report>_<yyyy-mm-dd>.xlsx`; no UI/action text; bounded/streamed for large sets; exclude sensitive fields.

---

## 14. Print strategy

- Standardize on **`react-to-print`** + new `ReportPrintLayout`.
- Every print includes: WAAD logo/branding (from system settings identity), title, selected filters, generation date/time, page numbers if practical, summary totals, read-only table, RTL, print-friendly widths; landscape for very wide reports.
- Exclude nav/sidebar/filter controls/pagination/action buttons.

---

## 15. Exact expected files to change (indicative)

**Frontend (new):**
- `src/components/reports/{ReportPageShell,ReportFilters,ReportTable,ReportSummaryCards,ReportPrintLayout,ReportExportButton,ActiveFilterChips,EmptyReportState}.jsx`
- `src/pages/reports/{beneficiaries,employers,providers,contracts,price-lists,benefit-policies,settlements,system-statistics}/index.jsx`
- `src/hooks/reports/use<Name>Report.js` per report
- `src/services/api/reports.service.js` (extend endpoints)

**Frontend (edit):**
- `src/routes/MainRoutes.jsx` (flat report routes; deprecate `/reports/domain`)
- `src/menu-items/components.jsx` (repoint menu; drop audit)
- `src/pages/reports/index.jsx` (category landing → flat routes)
- `src/reporting/reportRegistry.js` + `reportEngine.js` (align routes/status or retire)
- `src/config/roleAccessMap.js` (add `report_domain_contracts`)
- `src/utils/exportUtils.js` (formula-injection sanitization)

**Backend (new/edit) in `modules/report`:**
- `controller/ReportController` (v1 endpoints per report), new read-only services + repository projections + row/summary DTOs + POI export
- Standardize base path `/api/v1/reports`; alias legacy `/api/reports/claims/*`

**Docs:** this file + a final `REPORTS-ENGINE-1-IMPLEMENTATION-REPORT.md` at the end.

---

## 16. Migration decision

- **No DB schema migration required** for R1 — all reports are **read-only over existing tables**.
- If specific aggregates are slow, add **read-only indexes** in a later, isolated migration (documented, not part of core R1). No entity/schema changes.

---

## 17. Tests

**Backend (focused):** per-report filter correctness; summary reconciles with rows; pagination doesn't change totals; RBAC/scoping blocks cross-employer/provider; export uses same filters; financial totals from authoritative fields; **no mutating endpoint under `/reports`**; date validation; audit excludes secrets (n/a — dropped); export bounds enforced.

**Frontend:** menu routes to `/reports/*` (not operational); filters drive one read-only table; clear filters; print excludes nav/actions; export sends active filters; unauthorized items hidden; RTL usable; empty/error/loading render; **no create/edit/delete/approve controls** present.

---

## 18. Phased implementation

- **Phase A — Foundation:** menu/route correction (flat routes), shared shell/filters/table/print/export, backend base-path standardization + security model, add missing RBAC resource. *(No behavior change to operational modules.)*
- **Phase B — Core:** claims (refactor), beneficiaries, employers, providers, contracts.
- **Phase C — Pricing/Financial:** price-lists, benefit-policies, settlements (unify).
- **Phase D — Governance:** system-statistics. *(Audit dropped.)*
- **Phase E — Verification:** backend focused tests, FE lint/build, Excel samples, print samples, RBAC verification, Docker/local smoke test.

---

## 19. Risks

- **Scope creep into operational modules** — mitigated by read-only projections + no reuse of mutating services (hard rule).
- **N+1 / heavy aggregates** (providers, contracts, system-stats) — mitigate with dedicated aggregate queries + bounded date ranges.
- **RBAC drift** (FE resource vs BE role) — enforce scope in queries; add missing `report_domain_contracts`.
- **Registry vs flat-route dual source of truth** — decide: retire `reportRegistry`/domain pages or keep registry as metadata only. Recommend flat routes as source of truth, registry demoted to metadata.
- **Excel formula injection / sensitive fields** — add sanitization + field allow-lists.
- **Offline build env** — if Maven Central/Node unavailable, document blocker; do not claim pass; CI gate required.

---

## 20. Rollback plan

- All work on a feature branch; **no commit/push until approved**.
- Additive-first: new routes/components/endpoints don't alter operational modules; the legacy `/reports/domain/:key` route stays until the flat routes are verified, so reverting is a menu/route repoint.
- No DB migration → nothing to roll back at the data layer.
- If a report misbehaves, hide its menu item (RBAC/flag) without affecting others.

---

## Open decisions for approval

1. **Registry retirement:** demote `reportRegistry.js`/`reportEngine.js`/domain landing to metadata-only (flat routes become source of truth)? *(Recommended: yes.)*
2. **Settlements unification:** merge `financial-consolidation` + `accountant-profit` + `provider-settlement-summary` + `unified` into `/reports/settlements` with tabs? *(Recommended: yes, keep aliases.)*
3. **Orphan pages:** reuse `BeneficiariesReports.jsx`/`BenefitPolicyReport.jsx` as skeletons; remove truly dead ones (`FinancialReports`, `VisitsReport`, `EmployerDashboard`, `PreApprovalsReport`) after confirmation? 
4. **Audit:** confirmed **dropped** from Reporting Center (improve existing `/…/medical-audit-logs` separately).
5. **Backend base path:** standardize on `/api/v1/reports/*` with temporary alias for `/api/reports/claims/*`? *(Recommended: yes.)*

---

**REPORTS-ENGINE-1 AUDIT READY FOR APPROVAL**
