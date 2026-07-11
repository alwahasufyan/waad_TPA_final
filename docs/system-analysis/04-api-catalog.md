# 04 — API Catalog

> Extracted directly from `@RequestMapping`/`@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PreAuthorize` annotations across all 58 backend controllers (`grep`-verified, not sampled). **Total: 461 REST endpoints.** Endpoints are grouped by module and summarized at controller level with representative routes and role gates; exhaustive route-by-route documentation for all 461 endpoints was judged lower-value than an accurate, complete controller-level inventory plus called-out anomalies.

All endpoints require `isAuthenticated()` at minimum (class-level default), except the `auth` module's public routes. Authentication is dual-mode: session cookie (preferred) or JWT bearer (legacy) — see `07-domain-analysis.md`.

---

## Authentication (`auth`)

**`AuthController`** — `/api/v1/auth` — 15 endpoints, mostly public (no `@PreAuthorize`, matches `SecurityConfig` permit-all for this base path):
`POST /session/login`, `GET /session/me`, `POST /session/logout`, `POST /login`, `POST /register`, `GET /me`, `POST /forgot-password`, `POST /reset-password` (OTP flow), `POST /token/forgot-password`, `POST /token/reset-password` (token-link flow), `GET /password-reset-config`, `POST /verify-email`, `POST /resend-verification`, `POST /refresh-token` (`isAuthenticated()`), `PUT /users/me/password` (`isAuthenticated()`).

**Observations:** Two structurally different password-reset UX flows coexist and behave inconsistently on success (see `02-business-workflows.md` §Auth). `/register` has no visible `@PreAuthorize` and no confirmed email-verification gate — worth confirming its real exposure.

---

## RBAC & Users (`rbac`, `systemadmin`)

**`UserController`** — `/api/v1/admin/users` — 10 endpoints, **entire class** `hasRole('SUPER_ADMIN')`: CRUD, search/paginate, toggle-status, provider-lookup.

**`ChangePasswordController`** — `/api/v1/profile` — 2 endpoints, self-service password change.

**`EmailSettingsController`** — `/api/v1/admin/settings/email` — 4 endpoints, `hasAnyRole('ADMIN','SYSTEM_ADMIN','SUPER_ADMIN')`. **⚠️ `ADMIN` and `SYSTEM_ADMIN` are not valid roles** in `SystemRole` (7 valid roles: SUPER_ADMIN, MEDICAL_REVIEWER, ACCOUNTANT, PROVIDER_STAFF, EMPLOYER_ADMIN, DATA_ENTRY, FINANCE_VIEWER) — these checks are dead code in practice; only `SUPER_ADMIN` can ever satisfy them.

**`FeatureFlagController`** — `/api/v1/admin/features` — 7 endpoints, flag CRUD/toggle.

**`ModuleAccessController`** — `/api/v1/admin/modules` — 11 endpoints, module-access CRUD.

**`UserManagementController`** — `/api/v1/admin/user-management` — 2 endpoints (overlaps conceptually with `UserController` — two user-management surfaces, see `08-technical-debt.md`).

**`SystemController`** — `/api/v1/system` — 1 endpoint.

**`SystemAdminController`** — `/api/v1/admin/system` — 3 endpoints, `hasRole('SUPER_ADMIN')`: `DELETE /reset`, `POST /init-defaults`, `POST /seed-test-data` — **destructive/data-seeding operations exposed as live API endpoints**, gated to SUPER_ADMIN only; worth confirming these are disabled or extra-guarded in production deployments (feature-flag or profile-gated).

---

## Audit (`audit`)

**`MedicalAuditLogController`** — `/api/v1/admin/medical-audit-logs` — 4 endpoints, `hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')` at class level: `GET` (list), `GET /export.xlsx`, `GET /export-by-date.xlsx`, `POST /bulk-delete` (`hasRole('SUPER_ADMIN')`, step-up password re-auth required in the service layer).

**Observations:** Full claim audit-trail read access (including Excel export) is granted to any `MEDICAL_REVIEWER`, not just `SUPER_ADMIN` — a broader-than-typical read scope for what's described elsewhere as a "legal-grade" audit log; worth a policy review.

---

## System Settings (`common`)

**`SystemSettingsController`** — `/api/v1/admin/system-settings` — 8 endpoints: `GET /ui-config` (open to `isAuthenticated()`), `GET`/`GET /category/{category}` (`SUPER_ADMIN`,`MEDICAL_REVIEWER`), `GET/PUT /claim-sla-days` (read: both roles; write: `SUPER_ADMIN` only), `POST /claim-sla-days/reset`, `GET /sla-compliance-report`, `PUT /{key}` (`SUPER_ADMIN`).

**`FileController`** — `/api/v1/files` — 6 endpoints: upload/download/preview/exists (`isAuthenticated()`), `DELETE` (`hasRole('SUPER_ADMIN')`).

---

## Employer (`employer`)

**`EmployerController`** — `/api/v1/employers` — 10 endpoints: standard CRUD + search + toggle-active.

---

## Member (`member`)

**`UnifiedMemberController`** — `/api/v1/unified-members` — 24 endpoints (largest surface in this module): CRUD, dependents management, card/barcode operations, search variants.

**`BeneficiarySearchController`** — no explicit base path — 1 endpoint (likely a top-level search route).

**`NameSearchController`** — `/api/v1/members` — 1 endpoint.

**`UnifiedSearchController`** — `/api/v1/members-deprecated` — 2 endpoints, **class named `UnifiedSearchControllerDeprecated`** in the extracted inventory (base path itself says `-deprecated`) — confirmed dead/legacy surface still live in the codebase.

**`UnifiedEligibilityController`** — `/api/v1/members` — 3 endpoints, member-scoped eligibility.

**`MemberImportController`** — `/api/v1/members/legacy-import` — 7 endpoints (note "legacy" in the path itself, alongside `MemberExcelTemplateController`'s modern `/api/v1/unified-members/import` — two import surfaces, old and new, both live).

**`MemberExcelTemplateController`** — `/api/v1/unified-members/import` — 8 endpoints.

**`MemberDuplicateController`** — `/api/v1/system-settings/member-duplicates` — 3 endpoints.

**`KinshipMismatchController`** — `/api/v1/system-settings/kinship-mismatches` — 5 endpoints.

**Observations:** The `/api/v1/system-settings/*` base path used for member-duplicate and kinship-mismatch endpoints is a surprising namespace choice for what are fundamentally *member data-quality* tools, not system configuration — a minor API-surface organization inconsistency.

---

## Provider (`provider`)

**`ProviderController`** — `/api/v1/providers` — **32 endpoints, the single largest controller in the system**: CRUD, search, network/contract-related lookups, document associations.

**`ProviderPortalController`** — `/api/v1/provider` — 17 endpoints — the dedicated provider-facing surface (distinct base path, singular `/provider` vs. plural `/providers` for the admin surface).

**`ProviderDocumentController`** — `/api/v1/provider/documents` — 4 endpoints.

**`ProviderExcelController`** — `/api/v1/providers` — 1 endpoint.

**`ProviderExcelTemplateController`** — `/api/v1/providers/import` — 2 endpoints.

**`ProviderReportsController`** — `/api/v1/provider/reports` — 6 endpoints (provider-scoped claims/pre-auth/visits reports).

---

## Provider Contract (`providercontract`)

**`ProviderContractController`** — `/api/v1/provider-contracts` — **31 endpoints**, second-largest controller: CRUD + lifecycle transitions (activate/suspend/terminate) + pricing-item management.

**`ProviderContractPricingExcelController`** — `/api/v1/provider-contracts` — 2 endpoints (bulk pricing import/export).

---

## Medical Taxonomy (`medicaltaxonomy`)

**`MedicalCategoryController`** — `/api/v1/medical-categories` — 14 endpoints. Read endpoints: `hasAnyRole('SUPER_ADMIN','PROVIDER_STAFF','MEDICAL_REVIEWER')` — **notably excludes `EMPLOYER_ADMIN`, `ACCOUNTANT`, `FINANCE_VIEWER`, `DATA_ENTRY`** from reading the category tree, which may block those roles from resolving category/service names in their own claim/report views (flagged in `03-module-catalog.md`). Write endpoints: `SUPER_ADMIN` only. `GET /{categoryId}/services` is documented in code as "the ONLY way to retrieve services for selection" — an architecturally load-bearing endpoint.

**`MedicalCategoryExcelController`** — `/api/v1/medical-categories/import` — 2 endpoints.

---

## Benefit Policy (`benefitpolicy`)

**`BenefitPolicyController`** — `/api/v1/benefit-policies` — 22 endpoints: reads gated to `hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')`; all writes and lifecycle transitions (activate/deactivate/suspend/cancel/restore/hard-delete) are `hasRole('SUPER_ADMIN')` exclusively — the strictest write-gating pattern in the API surface, consistent with this module's "canonical source of coverage truth" status.

**`BenefitPolicyRuleController`** — `/api/v1/benefit-policies/{policyId}` — **24 endpoints**, most under `hasRole('SUPER_ADMIN')`, except the coverage-check/usage/bulk-check endpoints (`hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER','DATA_ENTRY')`) which are the ones actually invoked during live coverage resolution.

---

## Eligibility (`eligibility`)

**`EligibilityController`** — `/api/v1/eligibility` — 6 endpoints, single-member and family checks.

---

## Visit (`visit`)

**`VisitController`** — `/api/v1/visits` — 8 endpoints: `GET /all` (deprecated), CRUD, `GET /search` (deprecated), paginated `GET /`, `GET /count`. Most endpoints `hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER','PROVIDER_STAFF','DATA_ENTRY')`.

**`VisitAttachmentController`** — `/api/v1/visits` — 5 endpoints, attachment CRUD.

**Observations:** Two endpoints explicitly marked deprecated (`GET /all`, `GET /search`) remain live — candidates for removal once confirmed unused by the frontend (see `08-technical-debt.md`).

---

## Pre-Authorization (`preauthorization`)

**`PreAuthorizationController`** — `/api/v1/pre-authorizations` — **27 endpoints**, the largest surface in this module: CRUD, approve/reject/return-for-info, expiry maintenance.

**`PreAuthDashboardController`** — `/api/v1/pre-authorizations/dashboard` — 8 endpoints.

**`PreAuthorizationAuditController`** — `/api/v1/pre-authorizations` — 7 endpoints (shares base path with the main controller — route-collision risk if paths aren't carefully distinct; not confirmed as an actual conflict, but worth a lint pass).

**`EmailPreAuthController`** — `/api/v1/pre-auth/emails` — 7 endpoints (note: `/pre-auth/` here vs. `/pre-authorizations/` above — inconsistent path segment naming for the same domain concept).

**`PreAuthEmailRequestController`** — `/api/preauthorization/email-requests` — 3 endpoints. **⚠️ This is the only controller in the entire inventory whose base path lacks the `/v1` version segment** (`/api/preauthorization/...` vs. everything else's `/api/v1/...`) — an API-versioning inconsistency.

---

## Claim (`claim`) — the largest functional domain

**`ClaimController`** — `/api/v1/claims` — **26 endpoints**: create/update/review/submit/approve/reject/return-for-info, soft-delete/restore/hard-delete, search, member/pre-auth/status-scoped lookups, financial summary.

**⚠️ Confirmed dead-role reference:** `DELETE /{id}` and `PUT /{id}/restore` use `hasAnyRole('SUPER_ADMIN', 'INSURANCE_ADMIN', ...)` — `INSURANCE_ADMIN` is not a valid `SystemRole`. Same pattern as the `EmailSettingsController` finding above — this is a **recurring class of bug** (stale role references in `@PreAuthorize` that silently fall back to SUPER_ADMIN-only behavior), not an isolated incident. See `08-technical-debt.md` for the consolidated list.

**`ClaimBatchController`** — `/api/v1/claim-batches` — 3 endpoints (current batch get/create, list) — broad role access (all 6 non-viewer roles).

**`ClaimAttachmentController`** — `/api/v1/claims` — 5 endpoints, shares base path with `ClaimController`.

**`ClaimDraftController`** — `/api/v1/claims/draft` — 3 endpoints, autosave.

**`ClaimRejectionReasonController`** — `/api/v1/claim-rejection-reasons` — 4 endpoints, lookup-table CRUD.

**`CoverageEngineController`** — `/api/v1/claims` — 2 endpoints: `POST /calculate`, `POST /calculate-bulk` — the live entry point into the coverage/financial calculation engine, broadly accessible (`SUPER_ADMIN, ACCOUNTANT, MEDICAL_REVIEWER, PROVIDER_STAFF, EMPLOYER_ADMIN`).

**`ClaimCoverageRuleAdminController`** — `/api/v1/admin/claim-coverage-rules` — 4 endpoints — administration of the **parallel** `claim.ruleengine` coverage system flagged in `03-module-catalog.md`/`07-domain-analysis.md`.

**`MedicalReviewerProviderAssignmentController`** — `/api/v1/admin/medical-reviewers` — 2 endpoints, `hasRole('SUPER_ADMIN')`: get/set a reviewer's assigned providers.

**`ReviewerScopeController`** — `/api/v1/reviewers` — 1 endpoint: `GET /my-providers` (self-scoped, any authenticated reviewer).

**`ReportsController`** (claim-scoped) — `/api/v1/reports` — 9 endpoints: adjudication, provider-settlement, member-statement, summary, financial-summary, settlement-summary, provider-settlements (+providers, +Excel export) — `hasAnyRole('SUPER_ADMIN','ACCOUNTANT','FINANCE_VIEWER','EMPLOYER_ADMIN','MEDICAL_REVIEWER'[,'PROVIDER_STAFF' on some])`.

**Observations — path collision risk:** `ReportsController` (claim module) and `FinancialReportController` (report module) **both** map to base path `/api/v1/reports` — Spring will resolve these fine as long as sub-paths don't collide, but having two differently-purposed controllers share one base path across two different modules is an organizational smell worth flagging for future contributors.

---

## Settlement (`settlement`)

**`ProviderAccountController`** — `/api/v1/provider-accounts` — 11 endpoints.

**`PaymentController`** — `/api/v1/payments` — 6 endpoints: add/update/delete (soft) payment, monthly summaries — `ACCOUNTANT`-gated for mutations per the business rules in `02-business-workflows.md`.

---

## Reporting & PDF (`report`, `pdf`, `dashboard`)

**`ReportController`** — `/api/reports` (no `/v1`, another versioning inconsistency) — 2 endpoints: `GET /claims/html`, `GET /claims/pdf` — broadly accessible (essentially all roles).

**`FinancialReportController`** — `/api/v1/reports` — 2 endpoints: `/financial-consolidation`, `/company-profit` — `hasAnyRole('SUPER_ADMIN','ACCOUNTANT','FINANCE_VIEWER')`.

**`PdfCompanySettingsController`** — `/api/v1/pdf/settings` — 8 endpoints, `hasAnyRole` varies; class-level `isAuthenticated()` noted as potentially conflicting with the need to serve branding pre-login (see `03-module-catalog.md`).

**`DashboardController`** — `/api/v1/dashboard` — 8 endpoints: summary, monthly-trends, members-growth, cost-by-provider, service-distribution, recent-activities, legacy `/stats`, `/claims-per-day` — broadly accessible to all authenticated roles. Legacy endpoints use an `X-Employer-ID` header for scoping (see `02-business-workflows.md` §10 for the open security question).

---

## API-Wide Observations

1. **Recurring dead-role-reference bug class**: `INSURANCE_ADMIN` (ClaimController, ×2), `ADMIN`/`SYSTEM_ADMIN` (EmailSettingsController, ×4 identical checks) appear in `@PreAuthorize` but do not exist in `SystemRole`. In `hasAnyRole(...)` expressions that also include `SUPER_ADMIN`, this is cosmetically harmless (SUPER_ADMIN still works) but misleading to read; **any endpoint whose `@PreAuthorize` lists *only* a non-existent role would be permanently unreachable** — worth a full grep-based audit against the `SystemRole` enum as a cheap, high-value cleanup (see `08-technical-debt.md` / `09-improvement-roadmap.md`).
2. **API versioning inconsistency**: the overwhelming majority of endpoints live under `/api/v1/*`, but `PreAuthEmailRequestController` (`/api/preauthorization/email-requests`) and `ReportController` (`/api/reports`) omit the version segment entirely.
3. **Deprecated-but-live endpoints**: `VisitController.GET /all`, `VisitController.GET /search`, and the entire `UnifiedSearchControllerDeprecated` class (`/api/v1/members-deprecated`) are explicitly named/commented as deprecated yet remain deployed and reachable.
4. **461 endpoints across 58 controllers** is a large surface for a system with ~19 automated backend test files — the ratio itself is a data point for `08-technical-debt.md`'s test-coverage finding.
5. **Two live import pipelines per major entity** (Member: legacy-import + unified import; likely similar for Provider given `ProviderExcelController`/`ProviderExcelTemplateController`) — suggests incremental migration to newer import mechanisms without full retirement of the old ones.
6. **Destructive/administrative endpoints exist as regular REST routes** (`DELETE /api/v1/admin/system/reset`, `POST /seed-test-data`) rather than being confined to a CLI/ops-only tool — acceptable if genuinely SUPER_ADMIN-only and disabled/absent in production builds, but worth an explicit confirmation this isn't reachable in the deployed prod profile.

---

*Continue to [`05-database-analysis.md`](./05-database-analysis.md) for the schema this API surface reads and writes.*
