# RBAC-ROUTE-GUARD-HARDENING-1 — Audit + Design for Real Route-Level Permission Enforcement

**Audit + design only. No source files were modified. Nothing committed. Nothing pushed.**

## 1. Executive summary

Route-level RBAC in the frontend is **not enforcing anything today, and in two places it looks like it is while actually being a silent no-op.** `PermissionGuard` (component name `RoleGuard`) only reads `allowedRoles` and `isRouteGuard`; it never reads `resource`/`action`, and **not one call site across all of `MainRoutes.jsx` passes `allowedRoles`** — so every plain `<PermissionGuard isRouteGuard>` (the overwhelming majority of routes) reduces to "any authenticated user may access." Two pages (`ProviderAccountsList.jsx`, `PaymentsManagement.jsx`) go further and pass a prop named `requiredRole` — which `RoleGuard` also doesn't read — creating the *appearance* of a role restriction in the source code that has never actually restricted anything.

The one guard that does real work, `ProviderPortalGuard` (used once, wrapping `/provider/*`), correctly bypasses for a hardcoded staff-role list and correctly gates non-staff behind a feature flag — but it performs no role allow-list for non-staff either; it's a feature-flag gate, not a role gate.

In the absence of real route guards, the system today relies on: (1) menu-level hiding (`filterMenuItemsByRole`, real, but purely visual — doesn't stop direct URL navigation), and (2) at least 16 different page-local, ad-hoc `hasRole`/`user.roles`/`localStorage` checks scattered across components, each independently deciding what to show/hide/block, with no shared enforcement point and no fail-closed default. One of these (`ClaimBatchDetail.jsx`) reads role directly from `localStorage` rather than the auth context — the most fragile pattern found, trivially stale or spoofable by anything that can write to that key.

**This is a real, address-worthy gap — an authenticated user of any role can reach any frontend route by typing its URL** — but its actual severity depends entirely on backend authorization, which this audit did not re-verify endpoint-by-endpoint (out of scope, flagged as a dependency in §11). The frontend guard is UX/defense-in-depth, not the security boundary.

**Recommendation: proceed with the "Preferred approach" design in §8**, a minimal, additive change to `PermissionGuard`/`RoleGuard` that starts actually reading `resource`/`action` against the same `ROLE_RESOURCE_ACCESS` map already used for menus — no new permission model, no backend changes, and it fixes the two broken `requiredRole` call sites for free once real prop reading exists (they'll need to be corrected to `allowedRoles`, not `requiredRole`, as part of the fix).

**No code changes were made in this ticket.** Implementation requires separate, explicit approval per this ticket's own scope.

## 2. Current RBAC architecture (three independent mechanisms, only one real for its intended job)

1. **`filterMenuItemsByRole` + `ROLE_RESOURCE_ACCESS`** (`frontend/src/config/roleAccessMap.js`, `frontend/src/menu-items/components.jsx`) — real, and does what it's for: decides what appears in the sidebar/System Categories. `SUPER_ADMIN: ['*']`; `MEDICAL_REVIEWER`, `ACCOUNTANT`, `PROVIDER_STAFF`, `EMPLOYER_ADMIN`, `DATA_ENTRY`, `FINANCE_VIEWER` each get an explicit resource list. This has zero effect on direct URL navigation.
2. **`PermissionGuard.jsx` (`RoleGuard`)** (`frontend/src/components/PermissionGuard.jsx`) — intended to be the route guard (used ~90 times across `MainRoutes.jsx`). Reads exactly two props: `allowedRoles` (array) and `isRouteGuard` (boolean). Logic (confirmed by direct read):
   ```
   if (!user) return isRouteGuard ? <Navigate to="/login" replace /> : fallback;
   if (isSuperAdminUser(user)) return children;             // SUPER_ADMIN bypass — real
   if (!allowedRoles || allowedRoles.length === 0) return children;  // no roles specified → anyone logged in passes
   if (allowedRoles.includes(userRole)) return children;
   return isRouteGuard ? <Navigate to={getDefaultRouteForRole(userRole)} replace /> : fallback;
   ```
   Since `allowedRoles` is never passed anywhere in `MainRoutes.jsx`, branch 3 (`!allowedRoles`) is the one that always fires — every route just checks "is there a logged-in user," nothing more.
3. **~16 independent page-local ad-hoc role checks** (`hasRole()`, `user.roles`, `user?.role`, one reading `localStorage` directly) — each protects only what it's inline with (a button, a section, a data-fetch), with no shared logic, no consistent fail-closed behavior, and no relationship to `ROLE_RESOURCE_ACCESS` or `PermissionGuard` at all. Full list in §7.

There is also `ProviderPortalGuard` (`frontend/src/components/guards/ProviderPortalGuard.jsx`), a second, *working* guard component, used once for the entire `/provider/*` subtree. It is flag-based (`PROVIDER_PORTAL_ENABLED` from `useSystemConfig()`) with a hardcoded staff-role bypass list (`SUPER_ADMIN, ADMIN, DATA_ENTRY, MEDICAL_REVIEWER, ACCOUNTANT`) — real and functioning, but it answers "is the provider portal on," not "does this specific role belong on this specific provider-portal page." Once past it, the inner `PermissionGuard` instances for each `/provider/*` leaf route are exactly as non-functional as everywhere else.

## 3. Exact reason route-level RBAC is not working

Two independent, compounding facts:

1. `RoleGuard`'s implementation never destructures or references `resource` or `action` anywhere in its body (confirmed, full file read, `frontend/src/components/PermissionGuard.jsx`) — so passing them, as `MainRoutes.jsx` does on ~14 routes (mostly `dashboard`, `settlement/*`, `pre_auth`, `report_*`), has **zero runtime effect**. They read as documentation of intent that was never wired up.
2. `allowedRoles` — the one prop `RoleGuard` *does* read — is never passed by any call site in `MainRoutes.jsx`. Two pages (`ProviderAccountsList.jsx:565`, `PaymentsManagement.jsx:242`) pass `requiredRole` instead of `allowedRoles` — a naming mismatch, not a wiring gap; the intent to restrict was clearly there, the prop name is just wrong, and `RoleGuard` silently ignores unknown props rather than erroring, so this has shipped unnoticed.

Net effect: **every authenticated user, regardless of role, can reach every route protected only by `PermissionGuard`** (which is nearly all of them). The only routes an authenticated user cannot reach today are gated by something else entirely: the `/provider/*` subtree's `ProviderPortalGuard` feature-flag check (for non-staff, when the flag is off), and whatever the backend independently rejects once a page tries to call an API.

## 4. Route inventory table

Full leaf-route inventory of `frontend/src/routes/MainRoutes.jsx` (all routes are children of the root `SidebarLayout`, `path: '/'`, unless noted). "Guard" is the exact component/props as written in source; "Effective enforcement" is what actually happens given §3.

| Route | Component | Guard as written | Effective enforcement |
|---|---|---|---|
| `/dashboard` | `Dashboard` | `PermissionGuard resource="dashboard" action="view" isRouteGuard` | Any authenticated user |
| `/members` | `UnifiedMembersList` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/members/add` | `UnifiedMemberCreate` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/members/:id` | `UnifiedMemberView` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/members/:id/edit` | `UnifiedMemberEdit` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/members/:id/add-dependent` | `AddDependent` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/members/eligibility` | `EligibilityCheck` | `PermissionGuard isRouteGuard` | Any authenticated user; **not linked from any menu** |
| `/members/family-eligibility` | `FamilyEligibilityPage` | `PermissionGuard isRouteGuard` | Any authenticated user; **not linked from any menu** |
| `/employers` | `EmployersList` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/employers/create` | `EmployerCreate` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/employers/edit/:id` | `EmployerEdit` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/employers/:id` | `EmployerView` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/claims/review` | `ClaimReviewInbox` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/claims/:id/medical-review` | `ClaimReviewWorkspace` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/claims/batches` | `ClaimBatchManagement` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/claims/batches/entry` | `ClaimBatchEntry` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/claims/batches/detail` | `ClaimBatchDetail` | `PermissionGuard isRouteGuard` | Any authenticated user; page-internal Suspend/Delete/Hard-Delete buttons additionally gated by a **`localStorage.getItem('userRoles')` read** (§7) — fragile, bypasses auth context |
| `/settlement/provider-accounts` | `ProviderAccountsList` | `PermissionGuard resource="provider_accounts" action="view" isRouteGuard`, **plus an inner `PermissionGuard requiredRole={[...]}`** wrapping the page body | Route: any authenticated user. Page body: also open to any authenticated user — `requiredRole` is not a real prop (§3), the apparent restriction is a no-op |
| `/settlement/provider-payments` | `ProviderPaymentsList` | `PermissionGuard resource="provider_accounts" action="view" isRouteGuard` | Any authenticated user |
| `/settlement/provider-payments/:providerId` | `ProviderAccountView` | `PermissionGuard resource="provider_accounts" action="view" isRouteGuard` | Any authenticated user |
| `/settlement/payments` | `PaymentsManagement` | `PermissionGuard resource="provider_accounts" action="view" isRouteGuard`, **plus an inner `PermissionGuard requiredRole={[...]}`** wrapping the page body | Same no-op pattern as `ProviderAccountsList` above |
| `/providers` | `ProvidersList` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/providers/add` | `ProviderCreate` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/providers/edit/:id` | `ProviderEdit` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/providers/:id` | `ProviderView` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/provider-contracts` | `ProviderContractsList` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/provider-contracts/create` | `ProviderContractCreate` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/provider-contracts/edit/:id` | `ProviderContractEdit` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/provider-contracts/:id` | `ProviderContractView` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/visits` | `VisitsList` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/visits/add` | `VisitCreate` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/visits/edit/:id` | `VisitEdit` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/visits/:id` | `VisitView` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/pre-approvals/email-inbox` | `EmailPreAuthInbox` | `PermissionGuard resource="pre_auth" action="view" isRouteGuard` | Any authenticated user |
| `/pre-approvals` | `PreApprovalsList` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/pre-approvals/dashboard` | `PreAuthDashboard` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/pre-approvals/:id` | `PreApprovalView` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/pre-approvals/:id/audit` | `PreAuthAuditPage` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/classification/imports` | `ClassificationImportsPage` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/classification/imports/:id/review` | `ClassificationReviewPage` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/classification/versions/:id` | `ClassificationVersionPage` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/medical-categories` | `MedicalCategoriesPage` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/medical-categories/add` | `MedicalCategoryCreate` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/medical-categories/edit/:id` | `MedicalCategoryEdit` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/benefit-packages` | `BenefitPackagesList` | `PermissionGuard isRouteGuard` | Any authenticated user; **entire cluster (4 routes) not linked from any menu — ORPHAN_ROUTE** (no `benefit-packages` node anywhere in `menu-items/components.jsx` or `dashboardCategories.js`) |
| `/benefit-packages/create` | `BenefitPackageCreate` | `PermissionGuard isRouteGuard` | Same as above |
| `/benefit-packages/edit/:id` | `BenefitPackageEdit` | `PermissionGuard isRouteGuard` | Same as above |
| `/benefit-packages/view/:id` | `BenefitPackageView` | `PermissionGuard isRouteGuard` | Same as above |
| `/benefit-policies` | `BenefitPoliciesList` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/benefit-policies/create` | `BenefitPolicyCreate` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/benefit-policies/edit/:id` | `BenefitPolicyEdit` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/benefit-policies/:id` | `BenefitPolicyView` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/eligibility` | `EligibilityCheckPage` | `PermissionGuard isRouteGuard` | Any authenticated user; **not linked from any menu** (3rd of the 4 eligibility-page overlaps) |
| `/provider` (layout, wraps all `/provider/*`) | — | `ProviderPortalGuard` wrapping `<Outlet />` | Staff roles (`SUPER_ADMIN, ADMIN, DATA_ENTRY, MEDICAL_REVIEWER, ACCOUNTANT`) always pass; everyone else passes only if `PROVIDER_PORTAL_ENABLED` flag is on — no role allow-list beyond that |
| `/provider`, `/provider/eligibility-check` | `ProviderEligibilityCheck` | `PermissionGuard isRouteGuard` (inner, on top of `ProviderPortalGuard`) | Governed by `ProviderPortalGuard` only; inner guard is a no-op |
| `/provider/visits` | `ProviderVisitLog` | `PermissionGuard isRouteGuard` | Same |
| `/provider/pre-auth-inbox` | `ProviderPreAuthInbox` | `PermissionGuard isRouteGuard` | Same |
| `/provider/claims/submit` | `ProviderClaimsSubmission` | `PermissionGuard isRouteGuard` | Same |
| `/provider/pre-approvals/submit` | `ProviderPreApprovalSubmission` | `PermissionGuard isRouteGuard` | Same |
| `/provider/documents` | `ProviderDocuments` | `PermissionGuard isRouteGuard` | Same |
| `/provider/reports/claims` | `ProviderClaimsReport` | `PermissionGuard isRouteGuard` | Same |
| `/provider/reports/pre-auth` | `ProviderPreAuthReport` | `PermissionGuard isRouteGuard` | Same |
| `/provider/reports/visits` | `ProviderVisitsReport` | `PermissionGuard isRouteGuard` | Same |
| `/companies` | *(redirect)* | Bare `Navigate to="/settings/system"` | **No guard at all** — but harmless, it's a pure redirect to another guarded route |
| `/admin/users` | `AdminUsersList` | `PermissionGuard isRouteGuard` | Any authenticated user — **highest-risk unenforced route**, see §9 |
| `/admin/users/create` | `AdminUserCreate` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/admin/users/:id` | `AdminUserDetails` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/admin/users/:id/edit` | `AdminUserEdit` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/admin/users/medical-audit-logs` | `AdminMedicalAuditLogs` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/settings` | `Settings` (`pages/settings/index.jsx`) | `PermissionGuard isRouteGuard` | Any authenticated user at the route level; page body additionally self-filters its tiles via its own local `hasRole()` (§7) — a 3rd, independent mechanism |
| `/settings/company` | *(redirect)* | Bare `Navigate to="/settings/system"` | No guard — pure redirect |
| `/settings/system` | `SystemSettingsPage` | `PermissionGuard isRouteGuard` | Any authenticated user — **high-risk unenforced route**, see §9 |
| `/settings/facility-price-preparation` | `FacilityPricePreparationPage` | `PermissionGuard isRouteGuard` | Any authenticated user; menu entry already commented out (§ prior audit), route still live |
| `/settings/ai-key` | `AIKeySettingsPage` | `PermissionGuard isRouteGuard` | Any authenticated user; **not linked from any menu today** (tab already unwired from `SystemSettingsPage` per pre-existing change) |
| `/settings/kinship-mismatch` | `KinshipMismatchChecker` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/settings/member-duplicates` | `MemberDuplicatesResolver` | `PermissionGuard isRouteGuard` | Any authenticated user |
| `/profile` | `ProfileOverview` | **None** | Any authenticated user (expected/fine) |
| `/profile/account` | `AccountSettings` | **None** | Any authenticated user (expected — it's the user's own account/password page) |
| `/reports` | `ReportsPage` | `PermissionGuard resource="report_center" action="view" isRouteGuard` | Any authenticated user |
| `/reports/domain/:domainKey` | `ReportsDomainPage` | `PermissionGuard resource="report_center" action="view" isRouteGuard` | Any authenticated user — note: `:domainKey` itself isn't validated against role at the route level either |
| `/reports/domain/providers/report` | `ProvidersReport` | `PermissionGuard resource="report_domain_providers" action="view" isRouteGuard` | Any authenticated user |
| `/reports/domain/audit/report` | `ReportsMedicalAuditLogs` | `PermissionGuard resource="report_domain_audit" action="view" isRouteGuard` | Any authenticated user |
| `/reports/financial-consolidation` | `FinancialConsolidationMatrix` | `PermissionGuard resource="report_domain_financial_settlements" action="view" isRouteGuard` | Any authenticated user |
| `/reports/accountant-profit` | `AccountantProfitReport` | `PermissionGuard resource="report_domain_financial_settlements" action="view" isRouteGuard` | Any authenticated user |
| `/reports/provider-settlement-summary` | `ProviderSettlementReport` | `PermissionGuard resource="report_domain_financial_settlements" action="view" isRouteGuard` | Any authenticated user |
| `/reports/claims` | `ClaimsReport` | `PermissionGuard resource="report_domain_claims" action="view" isRouteGuard` | Any authenticated user |
| `/reports/claims/statement-preview` | `ClaimStatementPreview` | `PermissionGuard resource="claims" action="view" isRouteGuard` | Any authenticated user |
| `/reports/unified` | `ProviderSettlementReport` (2nd route, same component) | `PermissionGuard resource="report_provider_settlement" action="view" isRouteGuard` | Any authenticated user |
| `/documents` | `DocumentsLibrary` | `PermissionGuard isRouteGuard` | Any authenticated user — note the menu entry is hidden from everyone (`__hidden_documents`, per `NAVIGATION-CATEGORIES-CLEANUP-1`), but the **route itself has no such restriction**: any authenticated user who knows/guesses the URL can still open it |
| `/under-development`, `/403`, `/forbidden`, `/404`, `/500`, `/*` | respective error/placeholder pages | **None** | Any authenticated user (expected/fine — informational/error pages) |

## 5. Role access matrix (recommended target state)

Derived from `ROLE_RESOURCE_ACCESS` (the real, working menu map) plus judgment calls for routes with no menu-linked resource, flagged accordingly. "Y" = should be accessible; "N" = should redirect/block; "Y (own)" = accessible but scoped to the user's own data by the page/backend, not a role distinction.

| Resource group (routes) | SUPER_ADMIN | MEDICAL_REVIEWER | PROVIDER_STAFF | EMPLOYER_ADMIN | ACCOUNTANT | DATA_ENTRY | FINANCE_VIEWER |
|---|---|---|---|---|---|---|---|
| `dashboard` | Y | N (redirected per existing `dashboard/index.jsx` logic) | N (redirected) | Y | Y | Y | Y |
| `members` (`/members/*`) | Y | N | N | Y | N | Y | N |
| `employers` (`/employers/*`) | Y | N | N | N | N | Y | N |
| `claims` (`/claims/*`, `reports/claims`, `reports/claims/statement-preview`) | Y | Y | N | N | N | Y | N |
| `pre_auth` (`/pre-approvals/*`) | Y | Y | N | N | N | N | N |
| `provider_accounts`/`settlements` (`/settlement/*`) | Y | N | N | N | Y | N | N |
| `providers` (`/providers/*`) | Y | N | N | N | N | Y | N |
| `provider_contracts` (`/provider-contracts/*`) | Y | N | N | N | N | N | N |
| `visits` (`/visits/*`) | Y | Y (review context) | N (uses `/provider/visits` instead) | N | N | Y | N |
| `medical_catalog` (`/classification/*`, `/medical-categories/*`) | Y | N | N | N | N | Y | N |
| `benefit_policies` (`/benefit-policies/*`) | Y | N | N | Y | N | N | N |
| `benefit-packages` cluster | **Undetermined — orphan, see §6** | — | — | — | — | — | — |
| `users` (`/admin/users/*`) | Y | N | N | N | N | N | N |
| `system_settings` (`/settings/system`, maintenance tools, `/settings/ai-key`, `/settings/facility-price-preparation`) | Y | N | N | N | N | N | N |
| `report_center` + `report_domain_*` (`/reports/*`) | Y | Y (claims/providers/system-analytics domains only, per `ROLE_RESOURCE_ACCESS`) | N | Y (members/employers/benefit-policies domains only) | Y (financial-settlements/audit domains only) | Y (claims domain only) | Y (financial-settlements/audit domains only) |
| `documents` (`/documents`) | Y | Y | N | Y | Y | Y | N |
| `provider_portal` (`/provider/*`) | Y (staff bypass) | Y (staff bypass) | Y | N | Y (staff bypass) | Y (staff bypass) | N |
| `/eligibility`, `/members/eligibility`, `/members/family-eligibility` | **Undetermined — orphan/overlapping pages, needs a product decision on which one is canonical (see §6)** | — | — | — | — | — | — |
| `/profile`, `/profile/account` | Y (all roles — own account) | Y | Y | Y | Y | Y | Y |

## 6. Mismatches between menu visibility and route access

1. **`/settlement/provider-accounts` and `/settlement/payments` appear to have an extra role restriction (`requiredRole` on an inner `PermissionGuard`) that does not actually work** (§3, §4) — the source code reads as more restrictive than the running app actually is. This is the single most misleading finding: a reviewer scanning the source would reasonably believe these two pages are ACCOUNTANT/SUPER_ADMIN/FINANCE-only; they are not.
2. **`/benefit-packages` and its 3 sibling routes are not linked from any menu at all** — not in `menu-items/components.jsx`, not in `dashboardCategories.js`. Either this is a fully deprecated feature with a live, unguarded route (should be confirmed and the route removed or menu-linked), or it's mid-development and intentionally unlinked (should be documented, not left ambiguous).
3. **Three of the four eligibility-related pages (`/members/eligibility`, `/members/family-eligibility`, `/eligibility`) are unlinked** — only `/provider/eligibility-check` is menu-linked. Likely overlapping/legacy variants; needs a product decision on which is canonical (out of scope to decide here, flagged for a follow-up).
4. **`/documents` route has no restriction even though its menu entry is fully hidden** (`__hidden_documents`, per `NAVIGATION-CATEGORIES-CLEANUP-1`) — menu-hiding and route-reachability are two separate facts, and only one of them was addressed by that ticket. If Documents is meant to be inaccessible pending a product decision, the *route* also needs a guard, not just the menu item.
5. **`ClaimBatchDetail.jsx` reads role from `localStorage.getItem('userRoles')`** instead of `useAuth()`'s `user` object — meaning it can silently disagree with what the rest of the app believes the user's role is (e.g., after a role change server-side without a fresh login, or if that key is ever written by anything else). This is the most fragile of the ad-hoc checks and should be prioritized for correction regardless of the broader route-guard fix.
6. **`pages/settings/index.jsx` filters its own tiles by a local `hasRole()`/`roles` array per tile**, independent of both `ROLE_RESOURCE_ACCESS` and any future route-level fix — if route guards are added later without updating this page, its internal tile list could show a tile whose target route is now blocked (or hide one that's actually allowed), a 2-mechanism-drift risk called out already in `NAVIGATION-CATEGORIES-FULL-AUDIT-1-REPORT.md` §1.5.

## 7. Local `hasRole`/ad-hoc RBAC checks found (full list, not just the previously known one)

| File | What it checks | What it guards |
|---|---|---|
| `pages/settings/index.jsx` | Local `hasRole(roles)` against `user.roles`, per-tile `roles: [...]` array | Which Settings dashboard tiles render |
| `pages/rbac/users/UsersList.jsx` | `isSuperAdmin(user)` helper | Row-level UI decisions in the admin users table |
| `pages/visits/VisitView.jsx` | `isProviderStaff` derived from `user.roles`/`user.role` | Branches UI for provider-staff viewers |
| `pages/visits/VisitsList.jsx` | Same `isProviderStaff` pattern | Branches list UI/columns for provider-staff |
| `pages/reports/ProviderSettlementReport.jsx` | `isAdmin` from a hardcoded role array | Gates admin-only report filters/features |
| `pages/provider/hooks/useProviderClaimSubmission.js` | `isSuperAdmin` from `user.roles` | Blocks unlinked/direct claim submission unless SUPER_ADMIN |
| `pages/reports/FinancialReports.jsx` | `isAdmin` from a hardcoded role array (same pattern as ProviderSettlementReport) | Gates admin-only UI |
| `pages/settlement/components/PaymentFormModal.jsx` | `isSuperAdmin = user?.role === 'SUPER_ADMIN'` | Gates a form field/action in the payment modal |
| `pages/profile/ProfileOverview.jsx` | Derives `primaryRole` for display | Cosmetic role badge only — not access control |
| `pages/documents/DocumentsLibrary.jsx` | `canDeleteDocument` from a hardcoded role array | Shows/hides the delete-document action |
| `pages/settings/SystemSettingsPage.jsx` | `isSuperAdmin` from `useMemo` on `user` | Gates SUPER_ADMIN-only sections within the page |
| `pages/claims/batches/ClaimBatchDetail.jsx` | **Reads `localStorage.getItem('userRoles')` directly**, not `useAuth()` | Suspend/Delete/Hard-Delete claim-batch action visibility — see §6.5 |
| `pages/dashboard/index.jsx` | Builds role list from `user`/`localStorage`; redirects MEDICAL_REVIEWER/PROVIDER_STAFF away and suppresses their data fetches | Functions as an in-component route guard, duplicating what a real route guard should do |
| `components/RoleBasedRedirect.jsx` | `primaryRole` → `getDefaultRouteForRole()` | Redirects bare `/` to a role-specific landing page |
| `components/logo/index.jsx` | Role-based home path for the logo click target | Cosmetic navigation only |
| `components/tba/EmployerFilterSelector.jsx` | `isProviderUser` from `user.role`/`user.roles` | Hides/disables the employer filter for provider users |
| `pages/rbac/users/UserEdit.jsx`, `UserDetails.jsx` | Derive/display a user's roles | Admin tooling display/pre-select, not access control |

**Two broken `PermissionGuard` usages (distinct from the ad-hoc-check pattern above — these look like real route/page guards but are silent no-ops):**

| File | Issue |
|---|---|
| `pages/settlement/ProviderAccountsList.jsx:565` | `<PermissionGuard requiredRole={[...]}>` — wrong prop name, `RoleGuard` never reads `requiredRole`, `allowedRoles` stays `undefined`, guard passes everyone |
| `pages/settlement/PaymentsManagement.jsx:242` | Same bug, same effect |

## 8. Recommended implementation plan

Adopting the ticket's own "Preferred approach," expanded with findings from this audit:

1. **Keep `ROLE_RESOURCE_ACCESS` as the single central map.** Do not invent a second permission list for routes — reuse the exact same map already driving menu visibility, so a role's "what can I see" and "what can I open by URL" are always the same answer by construction.
2. **Extend `RoleGuard` to actually read and enforce `resource`/`action`** (action can remain informational/unused for now if no route currently needs more than "can view this resource" — confirmed no route in `MainRoutes.jsx` passes a non-`"view"` action today, so `action` enforcement can be deferred without weakening today's behavior). Also keep `allowedRoles` working exactly as it already does, for the handful of cases where a route-specific role list, not a resource, is the more natural fit (e.g., `/admin/users/*` could be phrased either way).
3. **`SUPER_ADMIN` wildcard bypass stays first, exactly as today** — already correct, do not change its position in the check order.
4. **Every route without a `resource`/`allowedRoles`/explicit public marker must fail closed**, not open, once the new enforcement lands. Concretely: introduce a small, explicit `publicOrAuthOnly` (or similarly named) escape hatch prop for the genuinely role-agnostic routes already identified in §4 (`/profile`, `/profile/account`, error pages, redirects) so they can opt out deliberately and visibly, rather than "no props" silently meaning "open," as it does today.
5. **Any route that ends up with neither a resource/role declaration nor the explicit public marker gets tagged `RBAC_UNCLASSIFIED_ROUTE`** in a follow-up implementation pass and must be triaged before the new enforcement ships — this audit's route table (§4) is the starting checklist; nothing found today has zero classification path (every route maps to a resource group in §5, even if some, like `benefit-packages`, need a product decision on which resource first).
6. **Fix the two broken `requiredRole` call sites** (`ProviderAccountsList.jsx`, `PaymentsManagement.jsx`) as part of the same change — once `allowedRoles` is the correct prop name and actually enforced, these become real restrictions instead of decorative ones. This is a behavior change (these pages currently allow any authenticated user) and should be called out explicitly to stakeholders before it ships, since it will newly block roles that were previously (accidentally) let in.
7. **Retire page-local `hasRole`/ad-hoc checks where they duplicate what the new route guard now does** (e.g., `pages/dashboard/index.jsx`'s redirect-away logic becomes redundant once `dashboard` has a real resource gate with role-appropriate redirects built in) — but only where they are pure duplicates. Checks that gate a button or a delete action *within* an already-open page (e.g., `DocumentsLibrary.jsx`'s `canDeleteDocument`) are a different, finer-grained concern and should stay, since a route guard only decides whether the page opens at all.
8. **Backend authorization remains the real security boundary** — this entire plan is frontend UX/defense-in-depth. No backend endpoint should be assumed newly safe because a frontend route now redirects; this ticket does not re-verify backend `@PreAuthorize`/equivalent coverage per endpoint, and that verification is a prerequisite before treating any of this as a completed security fix, not just a UX one.

## 9. Minimal safe implementation steps (for the follow-up ticket that will actually change code)

1. Add `resource`/`action` reading to `RoleGuard`, defaulting to today's exact behavior (`allowedRoles` unset + `!resource` → open) so the change is a strict superset until each route is explicitly classified — avoids a big-bang behavior flip.
2. Route-by-route, add the correct `resource` (reusing the `ROLE_RESOURCE_ACCESS` keys already defined) or the explicit public marker, starting with the highest-risk routes (§4 flagged, and the ticket's own priority list: `/admin/users`, `/settings/system`, `/settings/*`, `/claims/*`, `/provider-contracts`, `/classification/imports`, `/medical-categories`, `/benefit-policies`, `/reports/*`, `/settlement/*`, `/provider/*`, `/documents`, `/visits`, `/pre-approvals/*`, maintenance tools).
3. Correct the two `requiredRole` → `allowedRoles` (or resource-based equivalent) typos as part of that same pass, not before — doing it before the enforcement lands would have no visible effect and doing it in a separate ticket risks it never actually landing.
4. Resolve the `benefit-packages` orphan-route question (menu-link it, or confirm deprecated and remove/redirect) before assigning it a `resource`, since its correct role list is currently undetermined.
5. Decide the `/documents` route's fate to match its already-hidden menu entry (either guard the route the same way, or reverse the menu-hide decision) — a loose end from `NAVIGATION-CATEGORIES-CLEANUP-1`.
6. Fix `ClaimBatchDetail.jsx`'s `localStorage`-based role read to use `useAuth()` like everything else, independent of the broader route-guard rollout — small, isolated, low-risk correction.
7. Re-audit backend authorization coverage for the routes reclassified as SUPER_ADMIN/ACCOUNTANT/etc.-only in step 2, to confirm the frontend guard isn't the *only* thing standing between an unauthorized role and the underlying data.

## 10. Test matrix by role (to run once implementation lands)

| Role | Must reach | Must be redirected away from |
|---|---|---|
| SUPER_ADMIN | Everything | Nothing |
| MEDICAL_REVIEWER | `/claims/*`, `/pre-approvals/*`, `/reports` + claims/providers/system-analytics domains, `/documents` | `/admin/users`, `/settings/system`, `/settlement/*`, `/employers`, `/provider-contracts`, `/members` |
| PROVIDER_STAFF | `/provider/*` (subject to `PROVIDER_PORTAL_ENABLED`) | Everything else, including `/dashboard` (redirected per existing logic), `/admin/users`, `/settings/*`, `/claims/*` (admin-side), `/reports` (non-provider domains) |
| EMPLOYER_ADMIN | `/members`, `/benefit-policies/*`, `/documents`, `/reports` + members/employers/benefit-policies domains | `/admin/users`, `/settings/system`, `/settlement/*`, `/provider-contracts`, `/claims/*` (review side) |
| ACCOUNTANT | `/settlement/*`, `/documents`, `/reports` + financial-settlements/audit domains | `/admin/users`, `/settings/system`, `/members`, `/provider-contracts` |
| DATA_ENTRY | `/members`, `/employers`, `/providers`, `/claims/*`, `/documents`, `/medical-categories` (per `medical_catalog`), `/reports` + claims domain | `/admin/users`, `/settings/system`, `/settlement/*` |
| FINANCE_VIEWER | `/reports` + financial-settlements/audit domains | `/admin/users`, `/settings/system`, `/members`, `/claims/*`, `/employers`, `/provider-contracts` |

Also explicitly test: an authenticated user with a role not present in `ROLE_RESOURCE_ACCESS` at all fails closed (redirected everywhere, not "granted by default" from an empty allow-list) — the single most important regression to guard against relative to today's behavior, per the "fail closed, not open" design requirement (§8.4).

## 11. Risks

- **Behavior change risk:** once real enforcement lands, any role that was previously (accidentally) able to reach a page it shouldn't will be newly blocked — this is the intended fix, but it must be communicated as a behavior change, not shipped silently, in case any workflow was unknowingly depending on the current open-by-default state.
- **Backend-coverage risk:** this audit is frontend-only; if any backend endpoint lacks its own authorization check, fixing only the frontend route guard gives a false sense of security for that endpoint (§8.8, §9.7).
- **Drift risk between the route guard and page-local ad-hoc checks** if only some are retired (§8.7) — a page could end up more permissive at the route level than its internal buttons/actions suggest, or vice versa, causing confusing UX even if not a security hole.
- **`benefit-packages` and the eligibility-page cluster** carry classification risk (§6.2, §6.3) — assigning them the wrong resource before the product/orphan-route question is resolved could either lock out a feature still in active use or leave an unfinished one reachable.

## 12. What not to change (in this audit ticket, and cautions for the follow-up)

- No code was changed in this ticket.
- `ROLE_RESOURCE_ACCESS` itself (the role→resource definitions) should not be redefined as part of the route-guard fix — reuse it as-is; changing *what* each role can access is a separate business decision, not a side effect of *enforcing* what's already declared.
- `ProviderPortalGuard`'s working staff-bypass/flag logic should not be touched or duplicated — it already does its one job correctly; the follow-up only needs to add real enforcement to the *inner* `PermissionGuard` instances under `/provider/*`, not replace the outer guard.
- Menu-level filtering (`filterMenuItemsByRole`) is unaffected by this plan — it already works and needs no change; the fix is additive at the route layer only.
- Backend permission definitions are out of scope for this ticket per its own instructions — flagged as a dependency (§8.8, §9.7, §11), not something to alter here.

## 13. No-code-change confirmation

No source files, configuration, or migrations were created or modified during this ticket. The only new file is this report (`docs/rbac/RBAC-ROUTE-GUARD-HARDENING-1-REPORT.md`).

## 14. No-push confirmation

Nothing was staged, committed, or pushed.

---

**RBAC-ROUTE-GUARD-HARDENING-1 READY FOR DECISION**
