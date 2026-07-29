# RBAC-ROUTE-GUARD-HARDENING-2 — Implement Real Frontend Route-Level RBAC Using Existing ROLE_RESOURCE_ACCESS

**Status: READY FOR REVIEW — committed locally.** Commit `bbbcba8`, message `fix(rbac): enforce route-level resource guards`. Not pushed. Next step: a short backend-authorization review ticket (`BACKEND-RBAC-ENDPOINT-AUDIT-1`) is needed before this can be treated as a complete fix rather than a frontend-layer improvement — see §10 and §14.

## 1. What was implemented

Following the phased plan from `RBAC-ROUTE-GUARD-HARDENING-1-REPORT.md`:

- **Layer A** — `PermissionGuard`/`RoleGuard` now actually reads and enforces `resource` (against the existing `ROLE_RESOURCE_ACCESS` map) and `allowedRoles`, in addition to `isRouteGuard`. `SUPER_ADMIN` bypass is unchanged and still checked first.
- **Layer B** — every high-risk route cluster listed in the ticket now carries a real `resource` (or, for two cases needing a product decision, a conservative `allowedRoles`/`authOnly` marker) in `MainRoutes.jsx`.
- Routes not (yet) classified are **not silently left as-is** — they're either explicitly marked `authOnly` (deliberately role-agnostic) or, if genuinely unclassified, they trigger a dev-only `console.warn('[RBAC_UNCLASSIFIED_ROUTE] ...')` at render time so they stay visible instead of being forgotten (see §6).
- The two broken `requiredRole` no-op guards were fixed by removing the redundant page-level wrapper (the route-level `resource="provider_accounts"` guard now does the real job — see §4).
- `ClaimBatchDetail.jsx`'s `localStorage`-based role read was replaced with the standard `useAuth()` pattern used everywhere else (§5).
- Permission-denial redirects now go to `/403` instead of `getDefaultRouteForRole()` — see §2 for why.

## 2. `PermissionGuard` before/after

**Before** (`RoleGuard`, unchanged since before this ticket):
```js
const RoleGuard = ({ allowedRoles, isRouteGuard = false, children, fallback = null }) => {
  const { user, authStatus } = useAuth();
  if (authStatus === 'INITIALIZING') return null;
  if (!user) return isRouteGuard ? <Navigate to="/login" replace /> : fallback;
  if (isSuperAdminUser(user)) return children;
  if (!allowedRoles || allowedRoles.length === 0) return children;   // ← the actual bug
  if (allowedRoles.includes(userRole)) return children;
  if (isRouteGuard) return <Navigate to={getDefaultRouteForRole(userRole)} replace />;
  return fallback;
};
```
`resource`/`action` were never destructured — passing them anywhere had zero effect, and since `allowedRoles` was never passed in `MainRoutes.jsx` either, every plain `<PermissionGuard isRouteGuard>` reduced to "any authenticated user."

**After** (`frontend/src/components/PermissionGuard.jsx`):
```js
const RoleGuard = ({ allowedRoles, resource, action = 'view', authOnly = false, isRouteGuard = false, children, fallback = null }) => {
  const { user, authStatus } = useAuth();
  if (authStatus === 'INITIALIZING') return null;
  if (!user) return isRouteGuard ? <Navigate to="/login" replace /> : fallback;
  if (isSuperAdminUser(user)) return children;

  const userRole = getUserRole(user);
  const hasAllowedRoles = Array.isArray(allowedRoles) && allowedRoles.length > 0;
  const hasResource = Boolean(resource);

  if (hasAllowedRoles || hasResource) {
    const roleOk = !hasAllowedRoles || allowedRoles.includes(userRole);
    const resourceOk = !hasResource || hasResourceAccess(userRole, resource);
    if (roleOk && resourceOk) return children;
    return isRouteGuard ? <Navigate to="/403" replace /> : fallback;   // ← real enforcement
  }

  if (authOnly) return children;

  if (import.meta.env.DEV) console.warn('[RBAC_UNCLASSIFIED_ROUTE] ...');
  return children;   // unclassified routes keep pre-hardening behavior, not silently
};
```
`hasResourceAccess(role, resource)` checks the *same* `ROLE_RESOURCE_ACCESS` map (`config/roleAccessMap.js`) already used for menu filtering — `'*'` for `SUPER_ADMIN`-equivalent wildcards, exact resource match otherwise, and **an unmapped role resolves to an empty list and is correctly denied** (fail-closed for classified routes, per requirement §5 of the ticket).

`action` is accepted (default `'view'`) but not yet enforced — `ROLE_RESOURCE_ACCESS` is resource-only today and no route in `MainRoutes.jsx` currently declares a non-`'view'` action, so this is forward-compatible, not a gap being papered over.

**Redirect target changed**: denied `isRouteGuard` access now goes to `/403` (unguarded, always reachable — confirmed in `RBAC-ROUTE-GUARD-HARDENING-1-REPORT.md` §4) instead of `getDefaultRouteForRole(userRole)`. That function was found, while building this ticket, to map at least two roles to routes that **don't exist** in `MainRoutes.jsx` (`ACCOUNTANT → /settlement/batches`, `EMPLOYER_ADMIN → /member-portal/family`) — i.e. exactly the "redirect to a page they also cannot access" failure mode this ticket's UX requirement explicitly warned against. `/403` is simple, always valid, and matches the ticket's own stated preference.

## 3. Route resources added/changed in `MainRoutes.jsx`

| Route cluster | Resource / marker applied |
|---|---|
| `/members/*` (list, add, view, edit, add-dependent) | `resource="members"` |
| `/members/eligibility`, `/members/family-eligibility` | `resource="members"` (conservative — unlinked from any menu, see §6) |
| `/eligibility` | `resource="members"` (conservative — unlinked from any menu, see §6) |
| `/employers/*` | `resource="employers"` |
| `/claims/review`, `/claims/:id/medical-review`, `/claims/batches`, `/claims/batches/entry`, `/claims/batches/detail` | `resource="claims"` |
| `/visits/*` | `resource="claims"` (no dedicated `visits` key in `ROLE_RESOURCE_ACCESS`; matches the menu-derived resource for visits) |
| `/providers/*` | `resource="providers"` |
| `/provider-contracts/*` | `resource="provider_contracts"` |
| `/pre-approvals`, `/pre-approvals/dashboard`, `/pre-approvals/:id`, `/pre-approvals/:id/audit` | `resource="pre_auth"` (`/pre-approvals/email-inbox` already had it) |
| `/classification/imports`, `/classification/imports/:id/review`, `/classification/versions/:id`, `/medical-categories/*` | `resource="medical_catalog"` |
| `/benefit-policies/*` | `resource="benefit_policies"` |
| `/benefit-packages/*` | `allowedRoles={['SUPER_ADMIN']}` — **temporary, conservative** (orphan route cluster, not linked from any menu; see §6) |
| `/admin/users/*` (list, create, view, edit, medical-audit-logs) | `resource="users"` |
| `/settings/system`, `/settings/kinship-mismatch`, `/settings/member-duplicates`, `/settings/ai-key`, `/settings/facility-price-preparation` | `resource="system_settings"` |
| `/settings` (bare) | **Left unclassified deliberately** — see §6 |
| `/documents` | `resource="documents"` (see §6 for the reasoning) |
| `/provider/*` (all 9 inner leaf routes) | `resource="provider_portal"` (outer `ProviderPortalGuard` untouched) |
| `/profile`, `/profile/account` | `authOnly` (any authenticated user, own account) |
| `/settlement/*` (4 routes) | Already had `resource="provider_accounts"` before this ticket — unchanged |
| `/reports/*` (11 routes) | Already had `resource` props before this ticket — unchanged, now actually enforced |
| `/dashboard` | Already had `resource="dashboard"` — unchanged, now actually enforced |
| `/companies`, `/settings/company` | Bare redirects, no guard — unchanged (harmless, redirect to another guarded route) |
| `/403`, `/forbidden`, `/404`, `/500`, `/under-development`, catch-all `/*` | No guard — unchanged (informational/error pages) |

## 4. `requiredRole` no-op fixes

`pages/settlement/ProviderAccountsList.jsx` and `pages/settlement/PaymentsManagement.jsx` both wrapped their entire page body in `<PermissionGuard requiredRole={[...]}>` — a prop `RoleGuard` never read, making it a decorative no-op (confirmed in `RBAC-ROUTE-GUARD-HARDENING-1-REPORT.md` §7).

Per the ticket's stated preference ("use route-level resource guard and avoid duplicate page-level guard unless it protects a specific section/action"), both wrappers were **removed** rather than fixed to `allowedRoles` — the route-level `resource="provider_accounts"` guard (§3, already in place before this ticket, now actually enforced) covers the whole page, so a duplicate whole-page guard added nothing. The now-unused `PermissionGuard` import was removed from both files. `PaymentsManagement.jsx`'s return statement needed a `<>...</>` fragment added, since removing the wrapper element left two JSX siblings (the main `<Box>` and a conditional `<PaymentDetailsModal>`) with no common parent.

**Behavior change to flag**: `PaymentsManagement.jsx`'s old (non-functional) list named `FINANCE_VIEWER` alongside `SUPER_ADMIN`/`ACCOUNTANT`. `FINANCE_VIEWER` does not have `'provider_accounts'` in `ROLE_RESOURCE_ACCESS` (only financial-settlements/audit report domains). Since the old guard was a no-op, **`FINANCE_VIEWER` — and every other role — already had access to this page before this ticket**; after this fix, only `SUPER_ADMIN`/`ACCOUNTANT` (the roles `ROLE_RESOURCE_ACCESS` actually grants `provider_accounts` to) can reach it. This is the intended tightening, not a regression, but it is a real behavior change worth confirming with the business before this ships (see §9).

## 5. `ClaimBatchDetail.jsx` `localStorage` role fix

Replaced:
```js
const currentUserRole = (() => {
  try {
    const rolesStr = localStorage.getItem('userRoles');
    if (rolesStr) { const roles = JSON.parse(rolesStr); return Array.isArray(roles) ? roles[0] : ''; }
  } catch { /* ignore */ }
  return '';
})();
```
with a `useAuth()`-based read matching the pattern used everywhere else in the app (`components/PermissionGuard.jsx`'s `getUserRole`):
```js
const { user: currentUser } = useAuth();
const currentUserRole = (() => {
  if (!currentUser) return '';
  if (currentUser.role) return currentUser.role;
  if (Array.isArray(currentUser.roles) && currentUser.roles.length > 0) {
    const r = currentUser.roles[0];
    return typeof r === 'string' ? r : r?.name || '';
  }
  return '';
})();
```
This closes the specific fragility called out in the audit (`localStorage` can silently disagree with the auth context, e.g. after a server-side role change without a fresh login). The `canSuspend`/`canDelete`/`canHardDelete` derivations that consume `currentUserRole` were not touched — same logic, now fed a trustworthy role.

## 6. Remaining unclassified / conservatively-classified routes

Per the ticket's requirement #6 ("routes not yet classified should be reported as RBAC_UNCLASSIFIED_ROUTE, not silently forgotten"):

- **`/settings` (bare `pages/settings/index.jsx`)** — deliberately left with no `resource`/`allowedRoles`. This page runs its own separate, third RBAC mechanism (a local `hasRole()` filtering its tiles, using role names like `'ADMIN'` that don't exist in `ROLE_RESOURCE_ACCESS`). Assigning it a resource here would have been a guess that could conflict with that page's own logic; reconciling the two needs a product decision, not a route-classification guess. It carries a comment in `MainRoutes.jsx` explaining the deferral and pointing at this report.
- **`/benefit-packages/*`** — an orphan route cluster (confirmed not linked from any menu in either `NAVIGATION-CATEGORIES-FULL-AUDIT-1` or `RBAC-ROUTE-GUARD-HARDENING-1`). Per the ticket's own guidance ("Do not guess silently... Recommended temporary protection: SUPER_ADMIN only"), classified as `allowedRoles={['SUPER_ADMIN']}` — the conservative option, not `resource="benefit_policies"`, since opening an unreviewed, possibly-deprecated feature to `EMPLOYER_ADMIN` (who has `benefit_policies`) without product confirmation felt like the riskier guess.
- **`/members/eligibility`, `/members/family-eligibility`, `/eligibility`** — assigned `resource="members"` per the ticket's own suggestion ("member eligibility routes likely resource=members"), but the underlying overlap between these three pages and `/provider/eligibility-check` (the only menu-linked one) is unresolved and explicitly deferred, exactly as the ticket instructed.
- **Everything else in `MainRoutes.jsx`** now has either a real `resource`, an `allowedRoles` list, or an `authOnly` marker. If a future route is added without one of these three, the new dev-console `RBAC_UNCLASSIFIED_ROUTE` warning (§1, §2) will surface it immediately in local development rather than it silently defaulting to open.

## 7. Role test matrix (for manual verification once running locally)

Same matrix as `RBAC-ROUTE-GUARD-HARDENING-1-REPORT.md` §10, now backed by real enforcement:

| Role | Must reach | Must be redirected to /403 from |
|---|---|---|
| SUPER_ADMIN | Everything | Nothing |
| MEDICAL_REVIEWER | `/claims/*`, `/pre-approvals/*`, `/reports` (claims/providers/system-analytics domains), `/documents` | `/admin/users`, `/settings/system`, `/settlement/*`, `/employers`, `/provider-contracts`, `/members` |
| PROVIDER_STAFF | `/provider/*` (subject to `PROVIDER_PORTAL_ENABLED`) | `/dashboard` (still redirected by its own pre-existing in-page logic, unrelated to this ticket), `/admin/users`, `/settings/*`, `/claims/*`, `/reports` (non-provider domains), `/members`, `/employers` |
| EMPLOYER_ADMIN | `/members`, `/benefit-policies/*`, `/documents`, `/reports` (members/employers/benefit-policies domains) | `/admin/users`, `/settings/system`, `/settlement/*`, `/provider-contracts`, `/claims/*` |
| ACCOUNTANT | `/settlement/*`, `/documents`, `/reports` (financial-settlements/audit domains) | `/admin/users`, `/settings/system`, `/members`, `/provider-contracts` |
| DATA_ENTRY | `/members`, `/employers`, `/providers`, `/claims/*`, `/documents`, `/medical-categories`, `/reports` (claims domain) | `/admin/users`, `/settings/system`, `/settlement/*` |
| FINANCE_VIEWER | `/reports` (financial-settlements/audit domains) | `/admin/users`, `/settings/system`, `/members`, `/claims/*`, `/employers`, `/provider-contracts`, **and now `/settlement/*` (see §4 behavior change)** |
| An unmapped/unknown role | `/profile`, `/profile/account`, error pages | Everything with a `resource`/`allowedRoles` guard (fails closed, per §2) |

**Not executed in this ticket**: live browser/role-login verification. No browser/screenshot automation tool is available in this environment (a standing limitation for this session, noted in every prior report). The matrix above is guaranteed by the code (`ROLE_RESOURCE_ACCESS` lookups are deterministic and were spot-checked by hand against `config/roleAccessMap.js`), and `npx vite build` confirms the app still compiles with these guards in place, but an actual per-role click-through was not performed.

## 8. Build/eslint results

- `npx vite build` (from `frontend/`): **succeeded, exit 0**, after one fix (see below). Confirms every route still resolves and no lazy-loaded module broke.
  - First attempt failed: removing `<PermissionGuard>` from `PaymentsManagement.jsx` left two JSX sibling elements under `return (...)` with no common parent (`esbuild` error `Expected ")" but found "{"`). Fixed by wrapping in a `<>...</>` fragment (§4).
- `npx eslint src/components/PermissionGuard.jsx src/routes/MainRoutes.jsx src/pages/settlement/ProviderAccountsList.jsx src/pages/settlement/PaymentsManagement.jsx src/pages/claims/batches/ClaimBatchDetail.jsx`: **0 errors**, 1365 warnings — all `prettier/prettier` formatting/indentation warnings and pre-existing `no-unused-vars` warnings on report-page imports unrelated to this ticket's edits (confirmed by inspecting the warning list; no `no-unused-vars`, `no-undef`, or hook-rule warnings were introduced by these changes). The high warning count reflects that these files (especially `MainRoutes.jsx`) were not already prettier-formatted before this ticket, consistent with the same pattern already noted in `NAVIGATION-CATEGORIES-CLEANUP-1-REPORT.md` §5 — not a new problem introduced here.

## 9. Behavior changes by role

- **`ACCOUNTANT`/`SUPER_ADMIN` continue to reach `/settlement/*`** — unchanged (they already had `resource="provider_accounts"` at the route level before this ticket; it just wasn't enforced).
- **Every other role is now actually blocked from routes it was never supposed to reach** — this is the entire point of the ticket, but it is a real, user-visible change from "any authenticated user can open anything by URL" to "menu visibility and URL access agree." Any workflow that was unknowingly relying on the old open-by-default behavior will now see a redirect to `/403` where it previously didn't.
- **`FINANCE_VIEWER` loses (previously accidental, never-enforced) access to `/settlement/payments`** — see §4. This is the one specific, nameable behavior change worth flagging to the business before shipping, since the old code's role list *named* `FINANCE_VIEWER` even though it never worked.
- **`/documents`** is now gated by `resource="documents"` (MEDICAL_REVIEWER, ACCOUNTANT, EMPLOYER_ADMIN, DATA_ENTRY, SUPER_ADMIN) instead of being open to any authenticated user — matches those roles' already-declared `ROLE_RESOURCE_ACCESS` permissions; PROVIDER_STAFF and FINANCE_VIEWER newly lose direct-URL access (they never had a menu entry for it either, since Documents is fully hidden from the menu per `NAVIGATION-CATEGORIES-CLEANUP-1`).
- **`/benefit-packages/*`** goes from fully open to `SUPER_ADMIN`-only — a deliberately conservative, temporary restriction of an orphan feature (§6), not a considered product decision about who should ultimately have access.

## 10. Backend authorization caveat

Unchanged from the audit ticket's conclusion: **this is a frontend route guard, not a security boundary.** No backend endpoint should be assumed newly safe because a frontend route now redirects to `/403` — this ticket did not add, remove, or verify any `@PreAuthorize`/equivalent backend annotation, and none of the backend module was touched (`backend/` has zero changes in this ticket's diff). Re-auditing backend authorization coverage for the newly-restricted routes (especially `/admin/users/*`, `/settings/system`, `/settlement/*`) remains a prerequisite before treating this as a complete security fix rather than a UX/defense-in-depth improvement.

## 11. Follow-up cleanup list for local page-level `hasRole` checks

Per the audit's finding of ~16 ad-hoc role checks (`RBAC-ROUTE-GUARD-HARDENING-1-REPORT.md` §7), only the single highest-risk one (`ClaimBatchDetail.jsx`'s `localStorage` read, §5) was fixed in this ticket, as instructed ("Do not remove all local checks blindly... Record them as follow-up"). Recorded here, unchanged from the audit:

- `pages/settings/index.jsx` — local `hasRole()` tile filter, using non-existent role names (`'ADMIN'`); tied to the deferred `/settings` classification decision (§6).
- `pages/rbac/users/UsersList.jsx`, `UserEdit.jsx`, `UserDetails.jsx` — role-derivation/display helpers, not access control; low priority.
- `pages/visits/VisitView.jsx`, `VisitsList.jsx` — `isProviderStaff` UI-branching checks; candidates to retire once/if a `useHasRole` hook usage (now resource-aware, §1) is threaded through them.
- `pages/reports/ProviderSettlementReport.jsx`, `FinancialReports.jsx` — duplicate `isAdmin` hardcoded-role-array pattern.
- `pages/settlement/components/PaymentFormModal.jsx` — `isSuperAdmin` field-level gate.
- `pages/documents/DocumentsLibrary.jsx` — `canDeleteDocument` action-level gate; legitimate to keep even after route hardening, since it's finer-grained than "can this page open at all."
- `pages/settings/SystemSettingsPage.jsx` — `isSuperAdmin` section-level gate within an already-guarded page.
- `pages/dashboard/index.jsx` — role-based redirect-away logic for MEDICAL_REVIEWER/PROVIDER_STAFF; now partially redundant with the real `resource="dashboard"` route guard, but left untouched per the ticket's explicit "do not rewrite all page-local hasRole checks in this ticket" instruction.
- `pages/provider/hooks/useProviderClaimSubmission.js`, `components/RoleBasedRedirect.jsx`, `components/logo/index.jsx`, `components/tba/EmployerFilterSelector.jsx` — lower-priority, cosmetic or workflow-guard uses, unchanged.

None of these block this ticket; they're recorded so they aren't lost, per the ticket's own instruction.

## 12. Files changed

- `frontend/src/components/PermissionGuard.jsx` — real `resource`/`allowedRoles` enforcement, `/403` redirect, `RBAC_UNCLASSIFIED_ROUTE` dev warning, `useHasRole` extended to accept a resource.
- `frontend/src/routes/MainRoutes.jsx` — `resource`/`allowedRoles`/`authOnly` added across ~50 route entries (§3), plus explanatory comments at each conservative/deferred decision point.
- `frontend/src/pages/settlement/ProviderAccountsList.jsx` — removed no-op `requiredRole` guard and its now-unused import.
- `frontend/src/pages/settlement/PaymentsManagement.jsx` — removed no-op `requiredRole` guard, its now-unused import, added a `<>...</>` fragment wrapper.
- `frontend/src/pages/claims/batches/ClaimBatchDetail.jsx` — replaced `localStorage`-based role read with `useAuth()`.

No backend files, migrations, or configuration were changed.

## 13. No-push confirmation

Nothing was pushed. Commit `bbbcba8` is local only.

## 14. Commit-scope note: `MainRoutes.jsx` patch-level staging

Like `menu-items/components.jsx` in `NAVIGATION-CATEGORIES-CLEANUP-1`, `frontend/src/routes/MainRoutes.jsx` had a real entanglement issue at commit time: a pre-existing, unrelated, uncommitted change had already split the single `/reports/medical-audit` route into `/reports/domain/providers/report` and `/reports/domain/audit/report` (removing the separate flat `/reports/providers` route in the process). That restructuring is not part of this ticket. It was hand-crafted out of the commit at the git-object level (`git hash-object` + `git update-index`, same technique as the navigation cleanup commit): the staged blob contains exactly this ticket's `resource`/`allowedRoles`/`authOnly` additions and comments, verified hunk-by-hunk against `HEAD` before committing, with that one unrelated route-path change deliberately left out and still sitting as an uncommitted working-tree change (visible in `git status --short` after this commit). The other four files (`PermissionGuard.jsx`, `ProviderAccountsList.jsx`, `PaymentsManagement.jsx`, `ClaimBatchDetail.jsx`) had no such entanglement and were staged normally with `git add`.

## 15. Next step (not started)

Per direction: expanding/relaxing any of the restrictions introduced in this ticket is explicitly out of scope until a short backend-authorization review — **`BACKEND-RBAC-ENDPOINT-AUDIT-1`** — confirms the backend independently enforces the same boundaries (§10). This frontend-layer fix is accepted as complete and valuable on its own, but is not being treated as the final word on access control. Awaiting explicit ticket instructions to begin `BACKEND-RBAC-ENDPOINT-AUDIT-1`.

---

**RBAC-ROUTE-GUARD-HARDENING-2 READY FOR REVIEW**
