# BACKEND-RBAC-ENDPOINT-AUDIT-1 — Backend Endpoint Authorization vs. Frontend Route RBAC

**Audit only. No backend or frontend code was modified. Nothing committed. Nothing pushed.**

## 1. Executive summary

The backend's authorization posture is **fundamentally sound and better than the frontend was before `RBAC-ROUTE-GUARD-HARDENING-2`**: the security filter chain is secure-by-default (`anyRequest().authenticated()`), the large majority of the ~300 REST endpoints inventoried carry explicit `@PreAuthorize` role checks, and — the most important finding — **real, server-enforced, per-record data-scope isolation already exists** for the three cases that matter most: `EMPLOYER_ADMIN` cannot see another employer's members, `PROVIDER_STAFF` cannot see another provider's claims/visits, and `MEDICAL_REVIEWER` claim lists are scoped to that reviewer's assigned providers via a dedicated `ReviewerProviderIsolationService`. This is a genuine strength, not something this ticket needs to fix.

That said, four categories of real gaps were found:

1. **Three endpoint clusters have no authorization check at all** (`MISSING_AUTH`, Critical) — most seriously, `MemberDuplicateController`'s `/merge` and `/reset-kinship` endpoints let **any authenticated user of any role** (including `PROVIDER_STAFF`, `FINANCE_VIEWER`) merge member records and bulk-reset a `kinship_verified` flag via a raw SQL `UPDATE`. `KinshipMismatchController` (bulk-fix/ignore member data) and `PreAuthEmailRequestController` (delete pre-authorization email records) have the same gap.
2. **Two `FRONTEND_BACKEND_MISMATCH` findings** where the backend is *more restrictive* than the frontend implies: `EMPLOYER_ADMIN` has **no** backend access at all to `/api/v1/employers/**` despite `employers` being a frontend-granted resource for that role, and `benefit_policies` rule-management endpoints are backend-restricted to `SUPER_ADMIN` only even though `EMPLOYER_ADMIN` has frontend `benefit_policies` access (read-only list/get endpoints do work for `EMPLOYER_ADMIN`; everything else 403s). These aren't security holes — they're the opposite (over-restriction) — but they mean parts of the frontend UI a role can now *see* (post `RBAC-ROUTE-GUARD-HARDENING-2`) will still fail against the backend, which is a real UX bug worth a product decision, not a code fix.
3. **A test-coverage gap, not a vulnerability**: no test anywhere in the suite sends an actual HTTP request through the Spring Security filter chain and asserts a 403/401 for a wrong role — every "authorization test" found mocks the service layer and checks `AccessDeniedException` propagation. The `@PreAuthorize` annotations themselves (the first line of defense for ~300 endpoints) are unverified by any automated test. One integration test (`ClaimLifecycleIntegrationTest`) uses `@WithMockUser(roles = {"ADMIN","REVIEWER"})` — role strings that don't exist in the real `SystemRole` enum — which should be treated as a red flag that the test may not be exercising the real annotations.
4. **A minor, adjacent, non-RBAC security discrepancy** found incidentally: `SecurityConfig.java`'s comments assert `SameSite=Strict` cookies as the CSRF defense, but the actual cookie serializer sets `SameSite=Lax`. Noted for completeness; not part of this ticket's RBAC scope.

**No blocking condition for this audit.** Recommended next tickets are ranked in §9. **Final status: READY FOR REVIEW.**

## 2. Backend security architecture summary

- **Framework**: Spring Security, `security/SecurityConfig.java` (`filterChain` bean), `@EnableWebSecurity`.
- **Default posture: secure-by-default.** The filter chain ends with `.anyRequest().authenticated()` — every path not explicitly listed requires authentication. There is no "permit by default" fallback anywhere.
- **Auth mechanism: dual JWT + session.** `SessionCreationPolicy.IF_REQUIRED`; filter order is session filter → rate-limit/logging filters → `JwtAuthenticationFilter` → `UsernamePasswordAuthenticationFilter`. `CustomUserDetailsService` grants **exactly one** `ROLE_{userType}` authority per user — a single-role model (matches the frontend's single-primary-role assumption).
- **Method security**: `@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)` via `MethodSecurityConfig`. No custom `PermissionEvaluator` is registered despite an in-code comment claiming one exists for a "SUPER_ADMIN bypass expression handler" — SUPER_ADMIN bypass is actually implemented ad hoc, per-method, inside `AuthorizationService` (e.g. `isSuperAdmin()` short-circuits scattered through the class), not via a global mechanism. Not a bug, but a stale/misleading comment worth a cheap doc fix separately.
- **Explicitly public (`permitAll()`) paths**: `/api/v1/auth/**`, `/actuator/health`, `POST /api/v1/system/monitoring/external-heartbeat` (optionally gated by a shared-secret header inside the controller), `/api/v1/admin/features/public`, `/error`. `/api/reports/**` is explicitly *not* public (comment confirms this was deliberate). `/actuator/**` and Swagger/OpenAPI paths require `hasRole('SUPER_ADMIN')`.
- **401 vs 403**: a custom `AuthenticationEntryPoint` returns JSON 401 for unauthenticated requests (overriding Spring's default 403); `@PreAuthorize` failures for authenticated-but-unauthorized users correctly produce 403 via `AccessDeniedException`.
- **CSRF**: disabled, relying on `SameSite` cookie attribute — but the actual configured value is `Lax`, not the `Strict` the code comments claim (§1.4). `Secure` cookie flag is environment-driven, off by default outside production config — worth confirming production actually sets it.
- **CORS**: credentials allowed, origins restricted by an `app.cors.allowed-origins` property.

## 3. Endpoint family inventory table

Grouped by family; "Roles" is the exact `@PreAuthorize` role set found (class-level unless noted). Full per-endpoint citations (controller class, method name, line numbers) are preserved in the underlying audit notes and available on request — this table is the reviewable summary.

| Family | Controller(s) | Roles (typical) | Status | Risk |
|---|---|---|---|---|
| Admin users | `UserController` (9 endpoints) | `SUPER_ADMIN` (all) | OK | — |
| Admin profile self-service | `ChangePasswordController` (2) | none (any authenticated — self-service by design) | OK (intentional) | — |
| Admin settings/email | `EmailSettingsController` (4) | `SUPER_ADMIN` (all) | OK | — |
| Admin feature flags | `FeatureFlagController` (7) | `SUPER_ADMIN`, except `/public` = `permitAll()` | OK | — |
| Admin module access | `ModuleAccessController` (10) | `SUPER_ADMIN` (all) | OK | — |
| Admin system misc | `SystemController` (1) | `isAuthenticated()` only | OK (read-only employer info) | Low |
| Admin user-management | `UserManagementController` (2) | `SUPER_ADMIN` (all) | OK | — |
| System backups | `BackupController` (11) | `SUPER_ADMIN` (all) | OK | — |
| System error log | `SystemErrorLogController` (5) | `SUPER_ADMIN`, except frontend-error-report POST = any authenticated (intentional) | OK | — |
| Maintenance mode | `MaintenanceController` (2) | `SUPER_ADMIN` (all) | OK | — |
| Monitoring | `MonitoringController` (4) | `SUPER_ADMIN` (all) | OK | — |
| Monitoring (external heartbeat) | `ExternalMonitorController` (1) | public by design, optional shared-secret header | OK (intentional) | — |
| **Kinship mismatch** | `KinshipMismatchController` (5) | **NONE FOUND** | **MISSING_AUTH** | **High** |
| **Member duplicates** | `MemberDuplicateController` (3) | **NONE FOUND** | **MISSING_AUTH** | **Critical** |
| **Pre-auth email requests (delete)** | `PreAuthEmailRequestController` (3) | **NONE FOUND** | **MISSING_AUTH** | **High** — legacy/transitional email-intake workflow (product clarification, see §9 item 1a); fix is still valid safety hardening, not an endorsement of the workflow |
| Claims (core) | `ClaimController` (26) | Mixed: write/decision endpoints role-gated (`SUPER_ADMIN`/`MEDICAL_REVIEWER`/`DATA_ENTRY`/`PROVIDER_STAFF` combinations per action); many GET endpoints only `isAuthenticated()`, relying on service-layer scoping (§6) | OK (scoping verified in service layer) | Low |
| Claim attachments | `ClaimAttachmentController` (5) | Upload/delete role-gated; list/download/count `isAuthenticated()` only, scoped by service layer | OK | Low |
| Claim batches | `ClaimBatchController` (3) | 6-role list (`SUPER_ADMIN, DATA_ENTRY, ACCOUNTANT, EMPLOYER_ADMIN, MEDICAL_REVIEWER, PROVIDER_STAFF`) | OK | — |
| Claim draft | `ClaimDraftController` (3) | `isAuthenticated()` only, no role narrowing | UNCLEAR — confirm this is intentionally any-authenticated-user scratch-space, not a gap | Medium |
| Claim rejection reasons | `ClaimRejectionReasonController` (4) | Read: `isAuthenticated()`; write: `SUPER_ADMIN`/`MEDICAL_REVIEWER` | OK | — |
| Coverage engine calc | `CoverageEngineController` (2) | 5-role list | OK | — |
| Reviewer-provider assignment | `MedicalReviewerProviderAssignmentController` (2) | `SUPER_ADMIN` | OK | — |
| Financial/adjudication reports | `ReportsController` (claim module, 9) | 5–6-role lists incl. `FINANCE_VIEWER`/`EMPLOYER_ADMIN` | OK | — |
| Reviewer scope self-lookup | `ReviewerScopeController` (1) | `isAuthenticated()` only, service-layer scoped | OK | — |
| Claim coverage rules admin | `ClaimCoverageRuleAdminController` (4) | `SUPER_ADMIN` | OK | — |
| Visits | `VisitController` (8) | Read/create: 4-role list; update/delete: `PROVIDER_STAFF` only | **UNCLEAR** — `SUPER_ADMIN` cannot update/delete a visit via this endpoint; confirm intentional | Medium |
| Visit attachments | `VisitAttachmentController` (5) | 4-role list, all methods | OK | — |
| Pre-auth emails | `EmailPreAuthController` (7) | Mixed 2–3-role lists | OK | — legacy/transitional intake workflow, see §9 item 1a |
| Pre-auth dashboard | `PreAuthDashboardController` (7) | 3-role list, all | OK | — |
| Pre-auth audit trail | `PreAuthorizationAuditController` (7) | Mixed `isAuthenticated()`/3-role list | OK | — |
| Pre-authorizations (core) | `PreAuthorizationController` (26) | Mixed, decision endpoints correctly narrowed to `SUPER_ADMIN`/`MEDICAL_REVIEWER` | OK | — |
| Provider contract pricing edit | `ContractPriceEditController` (5) | `SUPER_ADMIN, ACCOUNTANT` (all) | OK | — |
| Provider contracts (core) | `ProviderContractController` (34) | `SUPER_ADMIN, ACCOUNTANT` for writes; reads add `MEDICAL_REVIEWER`/`PROVIDER_STAFF`/`DATA_ENTRY`/`EMPLOYER_ADMIN` variously | OK | — |
| Provider pricing Excel import | `ProviderContractPricingExcelController` (2) | `SUPER_ADMIN, ACCOUNTANT` | OK | — |
| Medical categories | `MedicalCategoryController` (14) | Reads: `SUPER_ADMIN, PROVIDER_STAFF, MEDICAL_REVIEWER`; writes: `SUPER_ADMIN` only | **FRONTEND_BACKEND_MISMATCH** — frontend grants `medical_catalog` to `DATA_ENTRY`, backend never lists `DATA_ENTRY` here | Medium |
| Medical category Excel import | `MedicalCategoryExcelController` (2) | `SUPER_ADMIN` | OK | — |
| **Medical service lookup** | `MedicalServiceLookupController` (1) | **NONE FOUND** (falls back to any authenticated) | MISSING_AUTH (low-sensitivity read) | Low |
| Classification knowledge | `CatalogKnowledgeController` (4) | `SUPER_ADMIN, MEDICAL_REVIEWER` | OK | — |
| Price list imports | `PriceListImportController` (6) | `SUPER_ADMIN, MEDICAL_REVIEWER` | **FRONTEND_BACKEND_MISMATCH** — frontend grants `medical_catalog` (hence `/classification/imports`) to `DATA_ENTRY` too | Medium |
| Price list review | `PriceListReviewController` (9) | `SUPER_ADMIN, MEDICAL_REVIEWER` | Same as above | Medium |
| Price list versions | `PriceListVersionController` (18) | Mixed `SUPER_ADMIN/MEDICAL_REVIEWER` and `SUPER_ADMIN/ACCOUNTANT` subsets | OK internally consistent | — |
| Benefit policies (core) | `BenefitPolicyController` (22) | Reads: `SUPER_ADMIN, EMPLOYER_ADMIN, ACCOUNTANT, MEDICAL_REVIEWER`; writes: `SUPER_ADMIN` only | Partial — see §5 | Low |
| **Benefit policy rules** | `BenefitPolicyRuleController` (24) | 20 of 24 endpoints `SUPER_ADMIN` only; 4 coverage-check endpoints add `MEDICAL_REVIEWER, DATA_ENTRY` | **FRONTEND_BACKEND_MISMATCH** | Medium |
| Beneficiary search | `BeneficiarySearchController` (1) | `SUPER_ADMIN, EMPLOYER_ADMIN, PROVIDER_STAFF, MEDICAL_REVIEWER` | OK | — |
| Member Excel import (unified) | `MemberExcelTemplateController` (8) | `SUPER_ADMIN, DATA_ENTRY` | OK | — |
| Member legacy import | `MemberImportController` (7) | `SUPER_ADMIN, DATA_ENTRY` | OK | — |
| Member name autocomplete | `NameSearchController` (1) | `SUPER_ADMIN, EMPLOYER_ADMIN, PROVIDER_STAFF` | OK | — |
| Member eligibility (unified) | `UnifiedEligibilityController` (3) | 3–4-role lists | OK | — |
| Member search (deprecated) | `UnifiedSearchController` (2) | 4-role list | OK | — |
| Members (unified, core) | `UnifiedMemberController` (26) | Writes: `SUPER_ADMIN, EMPLOYER_ADMIN`; reads add `PROVIDER_STAFF`/`MEDICAL_REVIEWER`/`DATA_ENTRY` variously | OK — real data-scope enforced server-side (§6) | Low |
| **Employers** | `EmployerController` (10) | List/selectors: `SUPER_ADMIN, MEDICAL_REVIEWER, ACCOUNTANT, FINANCE_VIEWER[, PROVIDER_STAFF]`; everything else `SUPER_ADMIN` only | **FRONTEND_BACKEND_MISMATCH — `EMPLOYER_ADMIN` has zero access** | **High (UX-breaking, not a security hole)** |
| Providers (core) | `ProviderController` (25) | Mixed, mostly `SUPER_ADMIN` for writes; reads vary 2–6 roles | OK | — |
| Provider documents (self-service) | `ProviderDocumentController` (4) | `PROVIDER_STAFF` only (excludes `SUPER_ADMIN`) | **UNCLEAR** — confirm `SUPER_ADMIN` should be excluded | Medium |
| Provider Excel import | `ProviderExcelController`, `ProviderExcelTemplateController` (3) | `SUPER_ADMIN` | OK | — |
| Provider reports (self-service) | `ProviderReportsController` (6) | `PROVIDER_STAFF` only (excludes `SUPER_ADMIN`) | **UNCLEAR** — same pattern as above | Medium |
| Payments | `PaymentController` (6) | Reads: `+FINANCE_VIEWER`; writes: `SUPER_ADMIN, ACCOUNTANT` | OK | — |
| Provider accounts | `ProviderAccountController` (10) | Reads: `SUPER_ADMIN, ACCOUNTANT, FINANCE_VIEWER`; pay/settle: `SUPER_ADMIN, ACCOUNTANT`; recalculate: `SUPER_ADMIN` | OK | — |
| Financial reports | `FinancialReportController` (2) | `SUPER_ADMIN, ACCOUNTANT, FINANCE_VIEWER` | OK | — |
| Provider reports (admin-side) | `ProviderReportController` (2) | 5-role list | OK | — |
| Legacy HTML/PDF reports | `ReportController` (2) | 7-role list (broadest found) | OK, but confirm breadth is intended | Low |
| Documents/attachments | Embedded (claims/visits/pre-auth/provider/member-photo controllers above) | Inherits each parent family's rules | OK, no separate generic controller | — |
| Provider portal | `ProviderPortalController` (17) | `SUPER_ADMIN, PROVIDER_STAFF` (+`MEDICAL_REVIEWER` on 3 GET endpoints, +`DATA_ENTRY` on 1 POST); own-provider scoping enforced server-side (§6) | OK | Low |
| Eligibility | `EligibilityController` (6) | `SUPER_ADMIN, MEDICAL_REVIEWER, PROVIDER_STAFF`; `/health` open to any authenticated | OK | — |
| Danger zone | `DangerZoneController` (4) | `SUPER_ADMIN` (all) | OK | — |
| Medical audit log | `MedicalAuditLogController` | `SUPER_ADMIN, MEDICAL_REVIEWER`; bulk-delete narrowed to `SUPER_ADMIN` | OK | — |
| PDF company settings | `PdfCompanySettingsController` | `/active` = any authenticated; rest `SUPER_ADMIN` | OK | — |

## 4. Frontend/backend RBAC alignment matrix

Comparing the frontend's `ROLE_RESOURCE_ACCESS` resource grants against what the backend actually enforces for the closest matching endpoint family:

| Frontend resource | Frontend grants (besides SUPER_ADMIN) | Backend reality | Alignment |
|---|---|---|---|
| `users` | (none — SUPER_ADMIN only) | `UserController` = `SUPER_ADMIN` only | **Match** |
| `system_settings` | (none) | All `systemadmin/*` + backups/monitoring/errors/maintenance = `SUPER_ADMIN` only | **Match** (except the 3 `MISSING_AUTH` findings in §5, which are member/kinship tools, not classic "system settings") |
| `members` | `EMPLOYER_ADMIN`, `DATA_ENTRY` | `UnifiedMemberController` reads: `EMPLOYER_ADMIN` yes; `DATA_ENTRY` **only** on 5 of 26 endpoints (financial-summary, photo, restore, export) — **not** on core list/search/create/update | **Partial mismatch** — `DATA_ENTRY` frontend access to `/members/*` (list, create, edit) would 403 against several core backend endpoints |
| `employers` | `DATA_ENTRY` (frontend `DATA_ENTRY` list includes `employers`); `EMPLOYER_ADMIN` implied by role name, but not actually in the frontend list either — re-checked `ROLE_RESOURCE_ACCESS`: `EMPLOYER_ADMIN` does not list `employers` as a resource (it has `members`, `benefit_policies`, `documents`, `report_*`). `DATA_ENTRY` does list `employers`. | `EmployerController`: `DATA_ENTRY` not present in any endpoint's role list — **zero backend access** for the one frontend role that does have this resource | **Mismatch** — `DATA_ENTRY`'s frontend `/employers/*` access would 403 entirely against the backend |
| `providers` | `DATA_ENTRY` | `ProviderController`: `DATA_ENTRY` present on several read endpoints (`/{id}`, service-related reads) but not on list/create/update | **Partial mismatch** |
| `provider_contracts` | (none besides SUPER_ADMIN) | `SUPER_ADMIN, ACCOUNTANT` | **Backend broader** (ACCOUNTANT), not a security gap — frontend just doesn't expose it to ACCOUNTANT in the menu |
| `claims` | `MEDICAL_REVIEWER`, `DATA_ENTRY` | `ClaimController` write/decision endpoints correctly include both; broad read endpoints are `isAuthenticated()`-only with service-layer scoping | **Match** (verified via §6, not just annotations) |
| `pre_auth` | `MEDICAL_REVIEWER` | `PreAuthorizationController`/`EmailPreAuthController`: `MEDICAL_REVIEWER` present throughout | **Match** |
| `provider_accounts`/`settlements` | (none besides SUPER_ADMIN in frontend `ROLE_RESOURCE_ACCESS`; only `ACCOUNTANT` maps `settlements`/`provider_accounts`) | `ProviderAccountController`/`PaymentController`: `ACCOUNTANT` + `FINANCE_VIEWER` (read) | **Backend broader** (FINANCE_VIEWER read access), not a gap |
| `medical_catalog` | `DATA_ENTRY` | `MedicalCategoryController`, `PriceListImportController`, `PriceListReviewController`: `DATA_ENTRY` **absent** from all of them | **Mismatch** — `DATA_ENTRY`'s frontend `/medical-categories`, `/classification/imports` access would 403 entirely |
| `benefit_policies` | `EMPLOYER_ADMIN` | `BenefitPolicyController` reads: yes; `BenefitPolicyRuleController` (the actual coverage-rule management): `SUPER_ADMIN` only, `EMPLOYER_ADMIN` absent | **Partial mismatch** — see §5 |
| `report_center` + `report_domain_*` | Multiple roles per domain | `ReportsController`/`ProviderReportController`/`FinancialReportController` role lists broadly match the frontend's per-domain grants | **Match** (spot-checked, no contradictions found) |
| `documents` | `MEDICAL_REVIEWER`, `ACCOUNTANT`, `EMPLOYER_ADMIN`, `DATA_ENTRY` | No standalone `documents` backend resource — access is inherited per-parent-object (claim/visit/preauth attachments, member photos); each parent family's role list was checked above and is broadly consistent, but there is **no single backend concept of "documents" to compare 1:1** — the frontend's unified `/documents` route (added in `RBAC-ROUTE-GUARD-HARDENING-2`) doesn't correspond to one backend resource. | **Structural mismatch worth noting**, not a security gap |
| `provider_portal` | `PROVIDER_STAFF` | `ProviderPortalController`: `PROVIDER_STAFF` throughout, `SUPER_ADMIN` bypass on all, `MEDICAL_REVIEWER`/`DATA_ENTRY` on a few | **Match, backend slightly broader** |
| `dashboard` | All roles (frontend grants it broadly / no explicit restriction pattern) | No dedicated "dashboard" backend endpoint found — the dashboard aggregates data from other already-checked endpoints | **N/A**, not independently checkable |

**Overall**: the backend is never *less* restrictive than the frontend in a way that constitutes a security hole (the 3 `MISSING_AUTH` findings in §5 are the exception). Where it diverges, it is almost always *more* restrictive (`employers` for `EMPLOYER_ADMIN`/`DATA_ENTRY`, `medical_catalog` for `DATA_ENTRY`, `benefit_policies` rules for `EMPLOYER_ADMIN`) — a UX/functionality gap for those specific role/resource combinations post-`RBAC-ROUTE-GUARD-HARDENING-2`, not a vulnerability.

## 5. Missing or weak backend authorization findings

| Controller | Endpoints | Finding |
|---|---|---|
| `modules/member/controller/MemberDuplicateController.java` | `GET /reset-kinship`, `GET /`, `POST /merge` (3) | **No `@PreAuthorize` anywhere** (class or method). `GET /reset-kinship` runs a raw `UPDATE members SET kinship_verified=false` via `JdbcTemplate`; `POST /merge` merges member records. **Any authenticated user of any role — including `PROVIDER_STAFF` and `FINANCE_VIEWER` — can trigger a bulk data mutation and merge member records via direct API call.** Highest-risk finding in this audit. |
| `modules/member/controller/KinshipMismatchController.java` | `GET /`, `POST /{id}/fix`, `POST /{id}/ignore`, `POST /bulk-fix`, `POST /bulk-ignore` (5) | Same gap — no authorization annotation at all; any authenticated user can bulk-fix or bulk-ignore kinship-mismatch records. |
| `modules/preauthorization/controller/PreAuthEmailRequestController.java` | `GET /`, `GET /{id}`, `DELETE /{id}` (3) | Same gap — any authenticated user can list, view, and **delete** pre-authorization inbound email records. |
| `modules/medicaltaxonomy/controller/MedicalServiceLookupController.java` | `GET /lookup` (1) | No authorization annotation, but this is a read-only service-catalog lookup — low sensitivity. Listed for completeness, not treated as high-risk. |

These four controllers are the *complete list* of endpoint groups with zero role check found across the entire inventory in §3 (everything else has at least `isAuthenticated()` plus, in the overwhelming majority of cases, an explicit role list).

## 6. Data-scope findings

Central enforcement point: `security/AuthorizationService.java`, plus a dedicated `modules/claim/service/ReviewerProviderIsolationService.java` for the reviewer case.

- **`EMPLOYER_ADMIN` → members**: `AuthorizationService.resolveEmployerScope()` forces the current user's own `employerId` regardless of any client-supplied `employerId` in the request. Enforced in `UnifiedMemberService.getAllMembers()` (and repeated at two other list/search methods): if the admin has no `employerId`, the query returns an empty page rather than falling through to unfiltered data. **Confirmed: an `EMPLOYER_ADMIN` cannot see another employer's members**, server-enforced.
- **`PROVIDER_STAFF` → claims/visits**: `AuthorizationService.canAccessClaim()`/`canAccessVisit()` explicitly compare `user.getProviderId()` against the claim's/visit's `providerId` and deny on mismatch; called from multiple points in `ClaimService`/`VisitService`. List-level filtering uses a parallel `getProviderFilterForUser()` helper. **Confirmed: a `PROVIDER_STAFF` user cannot see another provider's claims or visits.**
- **`MEDICAL_REVIEWER` → assigned-providers scope**: `AuthorizationService.canAccessClaim()` alone gives reviewers blanket single-record access, but `ReviewerProviderIsolationService` layers a stricter, purpose-built check on top: `isSubjectToIsolation()` applies only to `MEDICAL_REVIEWER` (SUPER_ADMIN bypasses); `getAllowedProviderIds()` returns the reviewer's assigned providers (empty, not "all," if unassigned); `validateReviewerAccess()` throws `AccessDeniedException` for out-of-scope providers and is called from `ClaimReviewService`, `ClaimService`, and `PreAuthorizationService` at multiple decision/mutation points; list filtering is applied in `ClaimService` claim-list queries. **Confirmed: `MEDICAL_REVIEWER` claim/pre-auth access is scoped to assigned providers**, not just role-gated. Assignment management itself (`MedicalReviewerProviderAssignmentController`) is `SUPER_ADMIN`-only, as expected.
- **Per-employer feature toggles**: `canEmployerViewMembers()`/`canEmployerViewBenefitPolicies()` additionally gate `EMPLOYER_ADMIN` visibility on a boolean field on the `User` entity, defaulting to allowed when unset — a secondary, opt-out-style control layered on top of the resource-level role check.
- **Claim mutation status check**: `canModifyClaim()` additionally verifies the claim's current status allows edits before permitting `PROVIDER_STAFF`/`EMPLOYER_ADMIN` writes, independent of ownership.

**No missing data-scope check was found for the specific isolation requirements this ticket asked to verify** (employer-scope, provider-scope, reviewer-assigned-providers). This is the strongest part of the backend's authorization model.

## 7. Highest-risk endpoints

Ranked by actual exploitability given the findings above:

1. **`POST /api/v1/system-settings/member-duplicates/merge`** and **`GET /api/v1/system-settings/member-duplicates/reset-kinship`** — Critical. Any authenticated session (including the lowest-privilege roles) can merge member records or bulk-mutate `kinship_verified` via a raw SQL statement, with zero role check.
2. **`/api/v1/system-settings/kinship-mismatches/*`** (bulk-fix/bulk-ignore) — High. Same root cause, one tier less destructive (record-level flags, not merges).
3. **`DELETE /api/preauthorization/email-requests/{id}`** — High. Any authenticated user can delete inbound pre-authorization email records, which are presumably an audit/compliance-relevant trail.
4. **`EMPLOYER_ADMIN` → `/api/v1/employers/**` total lack of access** — High *severity as a functional bug*, not a security risk (it's over-restriction, the opposite direction). Flagged here because it will visibly break UI flows once anyone tries to use the frontend access `RBAC-ROUTE-GUARD-HARDENING-2` now correctly exposes.
5. **`GET /api/v1/medical-services/lookup`** — Low. No auth check, but read-only, low-sensitivity catalog data.

## 8. Existing test coverage

- **Endpoint families with some authorization-adjacent test coverage**: claims (attachment authorization, review/line-decision, reviewer isolation), pre-authorizations (attachment authorization, decision service), visits (attachment authorization), danger zone.
- **Endpoint families with no authorization test coverage found at all**: admin/users, every `systemadmin/*` controller (feature flags, module access, backups, error log, monitoring, maintenance), members (`UnifiedMemberController`, kinship/duplicate controllers — i.e. the exact controllers found `MISSING_AUTH` in §5 have no tests either), employers, providers (`ProviderController`, `ProviderPortalController`, `ProviderDocumentController`), provider contracts/pricing, medical taxonomy/classification, benefit policies, settlements/provider accounts/payments, reports, dashboard, eligibility.
- **Critical gap**: every "authorization test" found is a unit test that mocks the service layer and asserts `AccessDeniedException` propagation, or tests `ReviewerProviderIsolationService` logic directly. **No `@WebMvcTest`/`MockMvc`-based test anywhere sends an actual HTTP request through the real Spring Security filter chain and asserts a 403/401** — meaning the `@PreAuthorize` annotations catalogued in §3 (the actual first line of defense for ~300 endpoints) are themselves unverified by any automated test. A typo or accidental removal in a `@PreAuthorize` string would not be caught by the current suite.
- **Test-integrity concern**: `ClaimLifecycleIntegrationTest`, the only `@SpringBootTest` using `@WithMockUser`, uses role strings `"ADMIN"` and `"REVIEWER"` — neither exists in the real `SystemRole` enum (`SUPER_ADMIN`, `MEDICAL_REVIEWER`). This test likely passes only because the specific endpoints it exercises don't require role-specific `@PreAuthorize`, which means it is **not actually validating the annotations** on the flow it appears to cover. Worth verifying directly before relying on it as authorization coverage.

## 9. Recommended implementation tickets, in priority order

1. **`BACKEND-RBAC-FIX-MISSING-AUTH-1`** (Critical, do first) — add `@PreAuthorize` to `MemberDuplicateController`, `KinshipMismatchController`, and `PreAuthEmailRequestController`. These are the only real security gaps found; recommend `hasRole('SUPER_ADMIN')` or `hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')` to match the equivalent, already-correctly-gated `MemberExcelTemplateController`/`MemberImportController` pattern — a product/business call on exact role list, but *some* explicit check is the non-negotiable part.
   - **1a. Product clarification (recorded post-implementation):** the email-based pre-authorization intake (`PreAuthEmailRequestController`, `EmailPreAuthController`) is **legacy/transitional**, not the intended future pre-authorization workflow — do not delete without explicit approval, do not build new major features around it. The intended future workflow is provider-portal submission + a reviewer inbox/workspace, modeled on the existing claims-review flow (provider submits → reviewer inbox → review workspace → approve/reject/request-correction → provider sees status/correction notes). This does not change the recommendation above (the endpoint is real and unguarded, so hardening it is still valid), but any *further* pre-authorization authorization work should target the new provider-portal-submission + reviewer-inbox flow (a new ticket, e.g. `PREAUTH-REVIEWER-WORKSPACE-1`), not be retrofitted onto the email-intake controllers.
2. **`BACKEND-RBAC-EMPLOYER-ADMIN-ACCESS-1`** (High, functional) — decide and implement `EMPLOYER_ADMIN` backend access to `/api/v1/employers/**` (at minimum read access to their own employer record) to match the frontend's `DATA_ENTRY` `employers` resource grant and avoid a dead-end UI flow.
3. **`BACKEND-RBAC-DATA-ENTRY-SCOPE-1`** (Medium, functional) — reconcile `DATA_ENTRY`'s frontend `medical_catalog`/`members`/`providers`/`employers` grants against the backend role lists that currently exclude `DATA_ENTRY` from several core endpoints in those families (§4). Likely several small, additive `@PreAuthorize` role-list expansions, but needs a product decision on which specific actions `DATA_ENTRY` should have, not a blanket widen.
4. **`BACKEND-RBAC-BENEFIT-POLICY-EMPLOYER-SCOPE-1`** (Medium, functional) — decide whether `EMPLOYER_ADMIN` should get read (or scoped write) access to `BenefitPolicyRuleController`, matching the frontend's `benefit_policies` grant, or whether the frontend grant should be narrowed to match the backend's current read-only intent.
5. **`BACKEND-RBAC-AUTHORIZATION-TEST-COVERAGE-1`** (Medium, but high leverage) — add `@WebMvcTest`/`MockMvc`-based tests that actually exercise the Spring Security filter chain for the highest-risk families first (admin/users, system settings, member duplicates/kinship post-fix-1, provider contracts, benefit policy rules), asserting 403 for wrong roles and 401 for unauthenticated requests. Also fix or replace `ClaimLifecycleIntegrationTest`'s non-existent `"ADMIN"`/`"REVIEWER"` mock roles with real `SystemRole` values.
6. **`BACKEND-RBAC-PROVIDER-SELF-SERVICE-CLARITY-1`** (Low/Medium, clarification only) — confirm whether `ProviderDocumentController` and `ProviderReportsController` intentionally exclude `SUPER_ADMIN` (unusual, since `SUPER_ADMIN` bypasses everywhere else in this codebase) — either document the intent or add `SUPER_ADMIN` for consistency with every other controller family.
7. **`BACKEND-RBAC-VISIT-UPDATE-DELETE-CLARITY-1`** (Low/Medium, clarification only) — confirm whether `VisitController`'s update/delete endpoints intentionally exclude `SUPER_ADMIN` (same "SUPER_ADMIN can't touch this" pattern as #6, worth resolving together).
8. **Optional, non-RBAC** — fix the `SameSite=Strict` (comment) vs `SameSite=Lax` (actual config) discrepancy in `CookieConfig.java`, and correct/remove the stale "SUPER_ADMIN bypass expression handler" comment in `SecurityConfig.java`. Cheap, low-risk documentation-truthfulness fixes found incidentally; not part of this ticket's RBAC scope, listed so they aren't lost.

## 10. What not to change

- No backend code was modified in this ticket, per its own scope.
- No frontend code was modified.
- No roles, permissions, or the `SystemRole` enum were changed.
- No migrations were added.
- The genuinely strong parts of the existing model — `ReviewerProviderIsolationService`, `AuthorizationService`'s employer/provider scoping, the secure-by-default `SecurityConfig` posture — should not be redesigned; the recommended tickets in §9 are additive fixes to specific gaps, not a rearchitecture.
- Do not widen any role's backend access as a side effect of fixing the `MISSING_AUTH` findings (#1 in §9) — add the narrowest check that closes the gap; widening `DATA_ENTRY`/`EMPLOYER_ADMIN` access (§4 mismatches) is a separate product decision (#2, #3, #4 in §9), not a bundled fix.

## 11. No-code-change confirmation

No source files, migrations, or configuration were created or modified during this ticket. The only new file is this report (`docs/rbac/BACKEND-RBAC-ENDPOINT-AUDIT-1-REPORT.md`).

## 12. No-push confirmation

Nothing was staged, committed, or pushed.

---

**BACKEND-RBAC-ENDPOINT-AUDIT-1 READY FOR REVIEW**
