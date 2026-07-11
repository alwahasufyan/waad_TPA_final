# WAAD TBA Reporting and Financial Settlement Design Review

Date: 2026-07-10
Version: 2.0 (Business-first revision after stakeholder feedback)
Scope: Read-only architecture audit of existing reporting and settlement subsystems (no code changes)

---

## 1. Existing Reports Inventory

### 1.1 Claims Domain

Existing report pages and report-like screens:
- Claims operational report page: /reports/claims
- Claim statement preview/print page: /reports/claims/statement-preview
- Provider claims report page: /provider/reports/claims
- Claim batch detail page can open claim statement preview (report entry flow)

Backend report endpoints:
- GET /api/v1/reports/adjudication
- GET /api/v1/reports/provider-settlement (legacy naming)
- GET /api/v1/reports/provider-settlements (canonical line-level report)
- GET /api/v1/reports/provider-settlements/export/excel
- GET /api/v1/reports/provider-settlements/providers
- GET /api/reports/claims/html
- GET /api/reports/claims/pdf

Findings:
- There are two generations of claims reporting APIs:
  - Legacy/summary style in claim ReportsController.
  - HTML/PDF statement style in report ReportController.
- Frontend uses the canonical provider-settlements endpoint and claim HTML/PDF statement, but not all legacy endpoints.

### 1.2 Pre-Authorization Domain

Existing report pages:
- Provider pre-auth report page: /provider/reports/pre-auth
- Pre-approvals operational report page exists in code: pages/reports/pre-approvals (currently not routed)

Backend report endpoints:
- GET /api/v1/provider/reports/pre-auth
- GET /api/v1/provider/reports/pre-auth/export

Findings:
- Provider pre-auth reporting is active and routed.
- Non-provider pre-approvals report page exists but is currently disconnected from routes/menu.

### 1.3 Visits Domain

Existing report pages:
- Provider visits report page: /provider/reports/visits
- Visits operational report page exists in code: pages/reports/visits (currently not routed)

Backend report endpoints:
- GET /api/v1/provider/reports/visits
- GET /api/v1/provider/reports/visits/export

Findings:
- Provider visits reporting is active and routed.
- Non-provider visits report page exists but is disconnected from routes/menu.

### 1.4 Members / Beneficiaries Domain

Existing report pages:
- BeneficiariesReports page exists in code (single member statement + table + print) but not routed in MainRoutes.

Backend endpoints used by member report flows:
- GET /api/v1/reports/member-statement/{memberId}
- GET /api/v1/unified-members/{memberId}/financial-summary
- GET /api/v1/members/{memberId}/remaining-limit

Findings:
- Member financial-statement reporting APIs exist.
- Main beneficiaries report page appears orphaned from routes/menu.

### 1.5 Employers Domain

Existing report pages:
- Employer dashboard report page exists in code: pages/reports/employer-dashboard (not routed).

Backend data/report endpoints used for employer analytics:
- GET /api/v1/dashboard/summary
- GET /api/v1/claims/financial-summary

Findings:
- Employer analytics capability exists, but the dedicated report page is not wired to routes/menu.

### 1.6 Financial Domain

Existing report pages:
- Provider settlement summary report: /reports/provider-settlement-summary
- Financial consolidation matrix: /reports/financial-consolidation
- Accountant profit report: /reports/accountant-profit
- FinancialReports page exists in code but not routed.

Backend endpoints:
- GET /api/v1/reports/financial-consolidation
- GET /api/v1/reports/company-profit
- GET /api/v1/reports/financial-summary
- GET /api/v1/reports/settlement-summary
- GET /api/v1/claims/financial-summary

Findings:
- Financial reports are split across multiple pages with overlapping concepts.
- Two different financial-summary APIs exist under different modules.

### 1.7 Provider Contracts / Price List / Medical Classification Domain

Existing report pages:
- Classification version comparison dashboard: /classification/versions/:id
- Classification imports/review pages feed the version report workflow

Backend report/statistics endpoints:
- GET /api/v1/provider-contracts/stats
- GET /api/v1/provider-contracts/{contractId}/pricing/stats
- GET /api/v1/classification/versions/{versionId}/comparison
- GET /api/v1/classification/versions/{versionId}/findings
- GET /api/v1/classification/versions/contract/{contractId}/summary

Findings:
- This is effectively a financial report stream for published provider price lists, but currently outside the reports center.

### 1.8 Dashboard Reports Domain

Dashboard widgets are driven by backend analytics endpoints:
- GET /api/v1/dashboard/summary
- GET /api/v1/dashboard/monthly-trends
- GET /api/v1/dashboard/members-growth
- GET /api/v1/dashboard/cost-by-provider
- GET /api/v1/dashboard/service-distribution
- GET /api/v1/dashboard/recent-activities
- Legacy: /stats, /claims-per-day

Findings:
- Dashboard metrics are a report source but not organized inside a unified reports center.

### 1.9 Audit-Related Reporting

Existing audit/report evidence:
- Payment audit logs endpoint: GET /api/v1/payments/{id}/audit
- Provider account balance verification endpoint: GET /api/v1/provider-accounts/{accountId}/verify-balance
- Financial transaction history in provider account view

Findings:
- Audit data exists but is embedded within operational settlement screens, not in a dedicated audit report structure.

---

## 2. Existing Financial Reports

Existing financial report/screen set currently in production code:
- Provider settlement summary report page (line-level claim settlement view)
- Financial consolidation matrix (employer x months x company share/provider share)
- Accountant profit report (discount profit to company)
- Provider accounts list (settlement claims and summary cards)
- Provider payments list (account-level balances and payment coverage)
- Payments management (monthly employer-provider payment ledger)
- Provider account view (wallet/account transaction ledger with printable/exportable statements)

Observation:
- Business users currently see both report-style financial pages and operation-style settlement pages with overlapping totals and terminology.

---

## 3. Existing Settlement Flow

### 3.1 Concept A: Provider Accounts and Provider Payments

Observed behavior:
- ProviderAccount model keeps running balance ledger per provider (credit on claim approval, debit on payment).
- Transaction history supports verification and audit trail.
- ProviderAccountView supports installment payment and full remaining settlement actions.

This corresponds to:
- Wallet/ledger/accounting concept (continuous balance state).

### 3.2 Concept B: Monthly Settlement Management

Observed behavior:
- PaymentService computes monthly settlement summaries by Employer x Provider x Month from approved/settled claims.
- PaymentRecord persists explicit payment entries against that month combination.
- PaymentsManagement screen edits payment records and audits changes.

This corresponds to:
- Periodic payable management concept (period close/reconcile).

### 3.3 Are A and B actually different concepts?

Conclusion: Yes, they are different and should remain separate conceptually.

- Concept A is a ledger account view (continuous account state).
- Concept B is a period settlement workflow (monthly reconciliation and payment recording).

Current issue:
- Navigation and labels mix these concepts, causing the impression of duplicate modules.

### 3.4 Financial naming proposal (Arabic, business-friendly)

Recommended root section name:
- التسويات والمدفوعات

Recommended sub-names:
- حسابات مقدمي الخدمة          -> for ledger balances and account transactions
- الحسابات المالية             -> accepted alternative label for non-finance users
- التسويات الشهرية             -> for Employer-Provider monthly settlement summaries
- دفعات التسويات               -> for payment execution and periodic settlement disbursements
- سجل دفعات التسويات           -> for payment records and audits

Alternative concise variant:
- الحسابات والمدفوعات

---

## 4. Duplicate Functionality

### 4.1 Duplicate report/settlement user journeys

- ProviderAccountsList and ProviderSettlementReport both present claim-based settlement financial views with exports.
- ProviderPaymentsList and PaymentsManagement both present payment perspectives, but from different aggregation angles.
- Multiple screens include separate print/export implementations for related financial data.

### 4.2 Duplicate/overlapping APIs

- Financial summary overlap:
  - /api/v1/claims/financial-summary
  - /api/v1/reports/financial-summary
- Settlement summary is available under reports API, while payment summaries are under payments API.
- Legacy and canonical provider settlement APIs coexist:
  - /api/v1/reports/provider-settlement (legacy naming)
  - /api/v1/reports/provider-settlements (canonical)

### 4.3 Duplicate or confusing menu items

- Settlement menu contains:
  - مطالبات التسوية لمقدمي الخدمة
  - الدفعات المالية لمقدمي الخدمة
  - إدارة الدفعات والتسديدات
- These are close in wording and likely confusing for non-technical users.

### 4.4 Duplicate exports and rendering paths

- Excel generation exists in:
  - Frontend generic export utility (many report pages)
  - Frontend settlement-specific Excel utility
  - Direct XLSX handling inside some pages
  - Backend Excel endpoints (some not used by current pages)
- PDF/print generation exists in:
  - Backend claim statement PDF endpoint
  - Backend provider visit PDF endpoint
  - Multiple frontend print strategies (useReactToPrint, window.print, opening PDF tab)

---

## 5. Missing Reports (Business-first, prioritized)

### 5.1 Price Lists Reports (independent module, high priority)

Required as a complete reporting module:
1. Latest published version report.
2. Version-to-version comparison report.
3. New services report.
4. Removed services report.
5. Price changes report.
6. Price change impact report (تقرير أثر تغيير الأسعار).
7. Classification changes report.
8. Service count report (totals by category/provider contract).
9. Import run report.
10. Import errors report.
11. Duplicate services report.

Suggested Price Change Impact KPI block:
- Increased-price services count.
- Decreased-price services count.
- Average increase percentage.
- Highest increase.
- Highest decrease.
- New services count.
- Removed services count.
6. Classification changes report.
7. Service count report (totals by category/provider contract).
8. Import run report.
9. Import errors report.
10. Duplicate services report.

Observation:
- Current classification/version endpoints provide foundations for parts of this, but not yet as one complete report module under Reports Center.

### 5.2 Benefit Policies Reports (independent module, high priority)

Required as a complete reporting module:
1. Total policy documents.
2. Total employers covered by policy mappings.
3. Total covered services.
4. Excluded services report.
5. Override services report.
6. Coverage percentage report (% coverage by policy/employer/category).
7. Uncovered requested services report (تقرير الخدمات غير المغطاة).

Observation:
- Benefit reporting capability exists partially in code, but it is not complete, centralized, or consistently surfaced.

### 5.3 Additional cross-domain gaps

1. Top providers by approved cost and by claim count (exportable table, not only dashboard chart).
2. Highest claim cost and outlier claims (per employer/provider/service category).
3. Most used medical services with trend by month.
4. Employer utilization report (members, claims, approved, rejected, remaining limits).
5. Rejected claims reasons report (top reasons, amounts rejected, provider/employer distribution).
6. Settlement aging report (outstanding balance age buckets by provider account).
7. Payment variance report (monthly payable vs paid vs remaining, with exception flags).
8. Pre-auth to claim conversion report (approved pre-auths that resulted in claims vs dropped).

Needs verification:
- Duplicate services/duplicate claims detection report depends on clear duplicate rules in business policy.

---

## 6. Proposed Reports Center Architecture

Objective: one central reports module organized by business perspective first; operational transaction screens stay outside reports.

### 6.1 Proposed top-level structure

Reports Center
- المطالبات
- المستفيدون
- جهات العمل
- مقدمو الخدمة
- العقود
- قوائم الأسعار
- وثائق المنافع
- التسويات المالية
- التدقيق
- إحصائيات النظام

### 6.2 Suggested contents per node

المطالبات
- Operational: مطالبات اليوم, المطالبات المعلقة, المطالبات المرفوضة, متابعة الموافقات المسبقة.
- Analytical: أسباب الرفض, اتجاهات التكلفة, تحليل التحويل من موافقة مسبقة إلى مطالبة.

المستفيدون
- Operational: كشف المستفيد, حدود المنافع الحالية, حالات الاستحقاق.
- Analytical: استهلاك المنافع حسب الفئة/الشهر, كثافة المطالبات لكل مستفيد.

جهات العمل
- Operational: ملخص مطالبات الجهة, حالة الموافقات والمطالبات.
- Analytical: أعلى جهات العمل تكلفة, اتجاهات الاستخدام والتكلفة.

مقدمو الخدمة
- Operational: مطالبات مقدم الخدمة, الزيارات, الموافقات.
- Analytical: أعلى مقدم خدمة, جودة الأداء, نسب الرفض/القبول.

العقود
- Operational: حالة العقود الفعالة وتواريخها.
- Analytical: أثر العقد على الكلفة والاستخدام.

قوائم الأسعار (module مستقل)
- آخر نسخة منشورة.
- مقارنة نسختين.
- الخدمات الجديدة.
- الخدمات المحذوفة.
- تغير الأسعار.
- تغير التصنيف.
- عدد الخدمات.
- تقرير الاستيراد.
- تقرير الأخطاء.
- تقرير الخدمات المكررة.

وثائق المنافع (module مستقل)
- عدد الوثائق.
- عدد الجهات.
- عدد الخدمات المغطاة.
- الخدمات المستثناة.
- الخدمات Override.
- Coverage %.

التسويات المالية
- Operational: التسويات الشهرية, دفعات التسويات, متابعة الرصيد المتبقي.
- Analytical: التقادم, فروقات المستحق/المدفوع, تحليل الربحية والتجميع المالي.

التدقيق
- سجلات تدقيق الدفعات.
- التعديلات اليدوية وعمليات Override.
- تقارير التحقق من توازن الحسابات.

إحصائيات النظام
- لوحات المؤشرات التنفيذية.
- جداول drill-down قابلة للتصدير.

### 6.3 Principles

- Reports Center contains only read/report outputs.
- Operational actions (add payment, settle remaining, recalculate balances) remain in settlement operations module.
- Separate Operational Reports from Analytical Reports in navigation and ownership.
- Each report has one canonical API contract and one export strategy.

### 6.4 Reports as Assets (architectural principle)

Every report is a managed system asset with mandatory metadata:

1. Report Code.
2. Business title.
3. Description.
4. Owner (business + technical).
5. Version.
6. Domain.
7. Classification (Operational or Analytical).

Example codes:
- REP-CLM-001: المطالبات اليومية
- REP-CLM-002: المطالبات المرفوضة
- REP-FIN-001: التسويات الشهرية
- REP-PRL-001: آخر قائمة أسعار منشورة

Why this matters:
- Permission governance.
- Usage/audit tracking.
- Support operations and incident handling.
- Technical documentation and API lifecycle.

### 6.5 Unified Report Engine (mandatory before implementation)

All new and migrated reports must use one shared framework layer (Report Engine):

1. Unified filter model
- Standard filter panel schema per report type (date range, employer, provider, member, status, category, etc.).

2. Saved filters
- Save, load, rename, and delete personal/shared filter presets with permission controls.

3. Column selection
- Per-report configurable columns with persisted user preference.

4. Export standards
- Excel export using one shared export pipeline and formatting policy.
- PDF export using one shared report template policy.
- Print output from the same report model to prevent rendering divergence.

5. Permission-aware execution
- Report visibility and filter scope enforced by role/resource policy (frontend + backend alignment).

6. Canonical report contract
- One report definition contract: metadata, filters, columns, totals, pagination, and export options.

7. Observability and governance
- Usage telemetry, export telemetry, execution time, and report errors tracked per report key.

9. Single execution path (engine-first)
- Report Center must always call Report Engine first.
- Domain modules (Claims, Members, Employers, Providers, Financial, Benefit, etc.) provide data contracts only.
- Excel/PDF/Print generation must be centralized in Report Engine and must not be implemented independently per module.

Reference architecture:
Report Center -> Report Engine -> Domain report providers (Claims, Members, Employers, Providers, Financial, Benefit, ...)

8. Minimum report contract (non-optional UI/UX standard)
- Title.
- Short description.
- Last updated timestamp.
- Filters.
- Result grid/list.
- Totals/aggregations.
- Excel.
- PDF.
- Print.
- Help button.

Rule:
- No report may ship with partial capabilities (example: Excel-only or PDF-only) unless explicitly approved as an exception.

### 6.6 Global Print and PDF Identity (system-wide standard, mandatory)

Design decision:
- Use one shared Print/PDF template for the entire system.
- No screen-specific standalone print templates are allowed by default.
- Current claims statement preview path is the reference implementation baseline after print fixes:
  /reports/claims/statement-preview

Standard requirements:
1. One shared print/PDF component/template for all domains.
2. Header and Footer are fully unified across all reports/documents.
3. Header/Footer data is sourced from System Settings, not hardcoded.
4. Any identity change (logo, organization info, styling) is updated once and propagates everywhere.
5. Every PDF/print output must read the following identity fields from System Settings:
  - Institution Name
  - Logo
  - Address
  - Phone
  - Email
  - Website

Unified Header (example fields):
- Organization logo.
- Organization name.
- System name.
- Contact details.
- Address.

Unified Footer (example fields):
- Report generation date/time.
- Printing user.
- Pagination (Page X of Y).
- Copyright/system ownership.

Body rule:
- Report body remains report-specific.
- Header/Footer/visual identity remains shared.

Scope:
- Claims reports.
- Beneficiary reports.
- Employer reports.
- Provider reports.
- Contract reports.
- Price lists reports.
- Benefit policy reports.
- Financial reports.
- Account statements.
- Settlement statements.
- Any future PDF or print output.

Important rule:
- A custom Header/Footer is forbidden unless there is an approved business justification.

### 6.7 Report Registry and onboarding governance (mandatory)

Policy:
- No new report may be implemented as a standalone page outside Report Engine.
- Every new report must be registered in Report Registry first, then rendered through Report Center.

Mandatory registry fields:
1. Report Code (stable).
2. Title.
3. Description.
4. Owner.
5. Version.
6. Domain.
7. Classification (Operational or Analytical).
8. Permission resource key.
9. Data provider/API contract reference.
10. Export/print capabilities profile.

Lifecycle rule:
- Register -> Approve -> Expose in Report Center -> Monitor usage/quality.
- Direct report-page creation without registry entry is not allowed.

### 6.8 Report Code immutability (mandatory)

Policy:
- Report Code is immutable.
- Once assigned (example: REP-CLM-001), Report Code must never change.

This remains true even if:
1. Report title changes.
2. Report menu location changes.
3. Report domain changes.
4. Arabic/English translation changes.

Governance rationale:
- Audit continuity.
- Permission stability.
- Documentation and API traceability.
- Support and user-manual consistency.

Additional rule:
- Report Code must be route-independent.
- Route changes must not trigger Report Code changes.

### 6.9 Report Registry as single source of truth (mandatory)

Policy:
- Report Registry is the single source of truth for report onboarding and wiring.
- Report registration occurs once, then downstream layers are derived from the same entry.

Required chain:
Report Registry -> Menu -> Routing -> Permissions -> Engine -> Print -> Exports

Implementation rule:
- Do not define report route/menu/permission/export contracts independently for the same report in separate places.

### 6.10 Mandatory registry metadata extension

Every report registry entry must define:
1. Data Source (mandatory)
2. Supports Scheduling (mandatory boolean)

Approved Data Source taxonomy:
- Claims
- Members
- Employers
- Provider Contracts
- Provider Price Lists
- Benefit Policies
- Settlements
- Audit
- Analytics

Scheduling policy:
- Scheduler is not implemented in R1/R2.
- Every report must still declare Supports Scheduling (true/false) to avoid future architectural changes.

### 6.11 No direct SQL in reporting layer (mandatory)

Policy:
- No direct SQL is allowed in report controller/page layers.
- No native-query report implementation shortcuts are allowed in report pages/controllers.

Required backend flow:
Controller -> Service -> Repository

Rationale:
- Maintainability
- Testability
- Performance governance
- Security and query control

---

## 7. Menu Reorganization Proposal

### 7.1 Keep operational modules separate

- Claims and approvals (operations)
- Provider portal (operations)
- Settlement operations (ledger/payments)

### 7.2 Add one clear Reports Center entry

Menu:
- التقارير (Reports Center)
  - المطالبات
  - المستفيدون
  - جهات العمل
  - مقدمو الخدمة
  - العقود
  - قوائم الأسعار
  - وثائق المنافع
  - التسويات المالية
  - التدقيق
  - إحصائيات النظام

### 7.3 Remove or relabel confusing duplicates

- Under settlement operations, use clear names:
  - حسابات مقدمي الخدمة
  - دفعات مقدمي الخدمة
  - التسويات الشهرية
  - سجل دفعات التسويات
- Move all report-only pages under Reports Center.

---

## 8. Financial Naming Proposal

Recommended business language set:

- Settlement Ledger (continuous):
  - حسابات مقدمي الخدمة
    بديل مقبول: الحسابات المالية

- Provider Payment Operations:
  - دفعات مقدمي الخدمة

- Monthly Reconciliation:
  - التسويات الشهرية

- Payment Execution and Register:
  - سجل دفعات التسويات

- Financial Reports area:
  - التقارير المالية والتسويات

Notes:
- Avoid specialized accounting wording that is hard for non-finance users.
- Avoid using the same word التسوية to describe both ledger and monthly payable workflow without qualifier.
- Prefer naming by business object (account, monthly cycle, payment record).

---

## 9. Risks

1. User confusion risk
- Overlapping settlement/report screens with similar labels can lead to wrong workflow usage.

2. Data consistency perception risk
- Different pages aggregate with different filters/sources; users may see “different totals” for seemingly same concept.

3. RBAC visibility mismatch risk
- Frontend resource mapping and backend endpoint role guards are not always aligned semantically.

4. Orphaned screens risk
- Several report pages/components exist but are not reachable from current routes/menu.

5. Technical debt risk
- Legacy and canonical report APIs coexist, increasing maintenance and onboarding complexity.

6. Export governance risk
- Multiple independent Excel/print implementations increase formatting inconsistency and audit risk.

Needs verification:
- Whether disconnected report pages are intentionally parked or unintentionally orphaned.
- Whether some legacy APIs are consumed by external clients outside this frontend.

---

## 10. Recommended Implementation Roadmap (Approved sequence, No immediate coding)

R1: Reports Center foundation only
1. Create Reports Center menu, routing, and permissions.
2. Implement Report Engine foundation (filters, columns, exports, print/PDF, saved filters, report metadata).
3. Do not move or rewrite existing report logic in this step.

R2: Lift-and-shift existing reports
1. Move existing reports into the new center structure as-is.
2. No business logic rewrite during migration.
3. Ensure operational modules remain separate from reporting modules.
4. Do not create new reports in R2.
5. R2 scope is migration and unification only: remove duplication and unify PDF/Excel/Print through the shared framework.

R3: API canonicalization and de-duplication
1. Standardize canonical report APIs.
2. Remove overlaps and legacy duplication safely.
3. Align frontend resource permissions with backend guards.

R4: New report development
1. Build new modules and analytical extensions:
  - Price Lists Reports.
  - Benefit Policies Reports.
  - Additional analytics.
2. Apply Minimum Valuable Reports strategy first in each domain.

### 10.1 Minimum Valuable Reports strategy (MVR)

Do not begin with a large batch of reports. Start with the highest-usage, highest-value subset.

Claims MVR example (first wave):
1. المطالبات اليومية
2. المطالبات المعتمدة
3. المطالبات المرفوضة
4. المطالبات المعلقة

Claims second wave (after MVR adoption):
1. أعلى تكلفة
2. أكثر الخدمات
3. تحليل الأمراض

Rule:
- Apply the same MVR-first approach to all other domains before expanding into advanced analytical packs.

---

## RBAC Visibility Recommendation Matrix (Target)

System Admin (SUPER_ADMIN)
- Full access to all report domains.

Finance (ACCOUNTANT)
- Full financial/settlement/audit reports.
- Read access to claims/pre-auth reports needed for reconciliation.

Finance Viewer (FINANCE_VIEWER)
- Read-only financial/settlement/audit reports.
- No payment edit actions.

Medical Reviewer (MEDICAL_REVIEWER)
- Claims/pre-auth/visits/medical-classification analytical reports.
- Read-only financial summaries where medically relevant.

Employer Admin (EMPLOYER_ADMIN)
- Employer-scoped members, utilization, claims summary, benefit policy reports.
- No cross-employer visibility.

Provider Staff (PROVIDER_STAFF)
- Provider-scoped provider portal reports only.
- Optional provider settlement statement (read-only) if business approves.

Data Entry (DATA_ENTRY)
- Minimal operational reports only (no sensitive financial rollups).

---

## Technical Review Snapshot (Requested)

### Unused or partially unused report pages/components (frontend)
- pages/reports/pre-approvals (exists, not routed)
- pages/reports/visits (exists, not routed)
- pages/reports/benefit-policy (exists, not routed)
- pages/reports/BeneficiariesReports (exists, not routed)
- pages/reports/FinancialReports (exists, not routed)
- pages/reports/employer-dashboard (exists, not routed)
- pages/reports/ComingSoonReport (exists, appears unused)

### Unused or partially unused report APIs (backend/frontend mapping)
- /api/v1/reports/adjudication appears not used by active routed pages.
- /api/v1/reports/provider-settlement (legacy path) appears superseded by /provider-settlements.
- /api/v1/reports/provider-settlements/export/excel exists, while current provider settlement page exports client-side.
- reportsService methods for getFinancialSummary/getSettlementSummary/getSummary/getAdjudicationReport/getMemberStatement are defined but not referenced by active report pages.

### Potential dead/legacy service methods
- claimsService.getProviderSettlementReport uses /reports/provider-settlement/{providerId}, but no active page reference found.
- claimsService.getSettlementSummary defined but no active caller found.
- providerPaymentsService.getConfirmedBatches/createPayment target /settlements/payments/* endpoints; matching backend endpoints were not found in this codebase.

Needs verification:
- External consumers may call some of these endpoints even if frontend does not.

---

Final note
This review intentionally stops at architecture and design analysis as requested. No implementation, no schema change, and no API change are included in this document.

---

## 11. Final Approval Status

Status:
- Document approved for execution kickoff after this Version 2 update.

Execution guardrails:
1. Keep ongoing operations stable during migration.
2. Implement in approved order (R1 then R2 then R3 then R4).
3. Enforce unified Report Engine and Global Print/PDF Identity from the start to avoid rework.

Approved baseline checklist:
- Reports as Assets.
- Report Registry.
- Report Engine as the only execution layer.
- Business-first Reports Center.
- Operational/Analytical separation.
- Global Print/PDF Identity.
- Unified Filters.
- Unified Export.
- Unified PDF.
- Unified Print Identity.
- Global Header/Footer.
- Price List Reports.
- Benefit Reports.
- Financial Reports.
- Provider Reports.
- Claims Reports.
- RBAC-aware Reports.
- Report Code.
- Report Version.
- Minimum Valuable Reports strategy (MVR-first).

Baseline statement:
This document is the official baseline and architecture freeze for the WAAD Reporting Framework. It is the official architecture for all current and future reporting features in WAAD. Any new report, print layout, export, or reporting feature must comply with this architecture unless an approved architectural decision supersedes it.

R2 freeze rule:
During R2, no architectural modifications to this baseline are allowed unless a real technical blocker is identified and approved.
