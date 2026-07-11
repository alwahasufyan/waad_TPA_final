# Reporting Framework - R2 Migration Matrix

Date: 2026-07-11
Status: Matrix Updated and Approved for Group 1 Start
Scope: True report artifacts only

---

## Rules

1. Matrix-first: no report migration starts before this matrix is approved.
2. Migration-only: no new reports, no business-logic changes.
3. Group gating: each priority group requires explicit approval before next group.
4. Only Artifact Type = Report is in scope for migration to Reports Center.
5. Artifact Types Workspace, Dashboard, Operational, Document stay in their domain modules.

---

## Priority Group 1 - Claims Reports

| Artifact Type | Report Code | Domain | Data Source | Old Route | New Route | Old API | New API | Legacy Components | Shared Components | Print -> Global Print | Export -> Shared Export Engine | Supports Scheduling | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Report | REP-CLM-001 | Claims | Claims | /reports/claims | /reports/domain/claims (mapped item) | /api/reports/claims/html, /api/reports/claims/pdf | Same API (engine-governed contract) | pages/reports/claims/index.jsx | reporting/reportEngine + registry-governed views | Claim statement print path -> global identity template | Existing Excel path -> shared export profile | true | In Progress |
| Report | REP-CLM-003 | Claims | Claims | /reports/claims/statement-preview | /reports/domain/claims (mapped item) | /api/reports/claims/pdf | Same API (engine-governed contract) | pages/reports/claims/ClaimStatementPreview.jsx | reporting/reportEngine + shared print model | Statement preview print -> global identity template | N/A or shared export if enabled | true | In Progress |
| Report | REP-CLM-004 | Claims | Claims | /provider/reports/claims | /reports/domain/claims (provider-scoped mapped item) | /api/v1/provider/reports/claims | Same API (engine-governed contract) | pages/provider/reports/ProviderClaimsReport.jsx | reporting/reportEngine + shared report page shell | Existing provider print -> global identity template | Existing export -> shared export profile | true | In Progress |

---

## Priority Group 2 - Financial Reports

| Artifact Type | Report Code | Domain | Data Source | Old Route | New Route | Old API | New API | Legacy Components | Shared Components | Print -> Global Print | Export -> Shared Export Engine | Supports Scheduling | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Report | REP-FIN-001 | Financial Settlements | Settlements | /reports/provider-settlement-summary | /reports/domain/financial-settlements (mapped item) | /api/v1/reports/provider-settlements | Same API (canonical through engine) | pages/reports/ProviderSettlementReport.jsx | reporting/reportEngine + registry mapping | Existing print -> global identity template | Existing export -> shared export profile | true | Queued |
| Report | REP-FIN-002 | Financial Settlements | Settlements | /reports/financial-consolidation | /reports/domain/financial-settlements (mapped item) | /api/v1/reports/financial-consolidation | Same API (engine-governed contract) | pages/reports/FinancialConsolidationReport.jsx | reporting/reportEngine + shared filters | Existing print -> global identity template | Existing export -> shared export profile | true | Queued |
| Report | REP-FIN-003 | Financial Settlements | Settlements | /reports/accountant-profit | /reports/domain/financial-settlements (mapped item) | /api/v1/reports/company-profit | Same API (engine-governed contract) | pages/reports/AccountantProfitReport.jsx | reporting/reportEngine + shared filters | Existing print -> global identity template | Existing export -> shared export profile | true | Queued |

---

## Priority Group 3 - Members Reports

| Artifact Type | Report Code | Domain | Data Source | Old Route | New Route | Old API | New API | Legacy Components | Shared Components | Print -> Global Print | Export -> Shared Export Engine | Supports Scheduling | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Report | REP-MEM-001 | Members | Members | (orphan) pages/reports/BeneficiariesReports | /reports/domain/members (mapped item) | /api/v1/reports/member-statement/{memberId}, /api/v1/unified-members/{memberId}/financial-summary | Same API (engine-governed contract) | pages/reports/BeneficiariesReports.jsx | reporting/reportEngine + registry mapping | Existing member print -> global identity template | Existing export -> shared export profile | true | Queued |

---

## Priority Group 4 - Employers Reports

No confirmed true report artifact is currently routed for migration in this group.
Status: Queued (await report-artifact confirmation only, no dashboard migration).

---

## Priority Group 5 - Providers Reports

| Artifact Type | Report Code | Domain | Data Source | Old Route | New Route | Old API | New API | Legacy Components | Shared Components | Print -> Global Print | Export -> Shared Export Engine | Supports Scheduling | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Report | REP-PRV-001 | Providers | Claims | /provider/reports/visits | /reports/domain/providers (mapped item) | /api/v1/provider/reports/visits | Same API (engine-governed contract) | pages/provider/reports/ProviderVisitsReport.jsx | reporting/reportEngine + shared report shell | Existing print -> global identity template | Existing export -> shared export profile | true | Queued |
| Report | REP-PRV-002 | Providers | Claims | /provider/reports/pre-auth | /reports/domain/providers (mapped item) | /api/v1/provider/reports/pre-auth | Same API (engine-governed contract) | pages/provider/reports/ProviderPreAuthReport.jsx | reporting/reportEngine + shared report shell | Existing print -> global identity template | Existing export -> shared export profile | true | Queued |

---

## Priority Group 6 - Contracts Reports

No confirmed true report artifact is currently mapped in this group.
Status: Queued (operational contract views are excluded from Reports Center migration).

---

## Priority Group 7 - Price Lists Reports

No existing true report page is currently in scope.
Status: Queued (workspaces remain in Medical Classification; no new report creation in R2).

---

## Priority Group 8 - Benefit Policies Reports

No confirmed true report artifact is currently mapped for migration.
Status: Queued (policy management/operational pages remain in domain module).

---

## Priority Group 9 - Audit Reports

No dedicated true report page is currently mapped for migration.
Status: Queued (embedded operational audit views remain in their domain module).

---

## Priority Group 10 - Analytics Reports

No true report artifact is currently in scope for migration.
Status: Queued (dashboard widgets are excluded from Reports Center migration).

---

## Out of Scope (Non-Report Artifacts)

| Artifact Type | Artifact Name | Current Location | Decision |
|---|---|---|---|
| Workspace | VersionComparisonDashboard | pages/classification/VersionComparisonDashboard.jsx | Keep in Medical Classification module (not migrated to Reports Center) |
| Workspace | VersionReviewWorkspace | pages/classification/VersionReviewWorkspace.jsx | Keep in Medical Classification module (not migrated to Reports Center) |
| Dashboard | Dashboard Summary / Monthly Trends / Cost by Provider widgets | dashboard views/widgets | Keep in Dashboard module (not migrated to Reports Center) |
| Operational | Provider contract operational view | pages/provider-contracts/ProviderContractView.jsx | Keep in Contracts module |
| Operational | Provider account/payment operational views | settlement/payment pages | Keep in Settlement module |
| Document | Policy management/document administration pages | policy/benefit management pages | Keep in domain module |

---

## Status Legend

- Queued: Not started yet.
- In Progress: Migration active for this report.
- Migrated: Migration complete and validated.
- Approved: Group approved for moving to next group.

---

## Execution Gate

Do not start migration for Group N+1 before Group N is marked Approved.
