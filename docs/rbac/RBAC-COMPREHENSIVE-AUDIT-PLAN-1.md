# RBAC-COMPREHENSIVE-AUDIT-PLAN-1

Users / Roles / Permissions — comprehensive audit & remediation plan
(backend + frontend), requested after discovering that granting a role a
page permission through the Roles & Permissions matrix had no real effect
for pages outside that role's original static resource list.

Status: **PLAN — awaiting approval before implementation.** Nothing below
except the §1 bug fix has been implemented. No commit/push.

---

## §1 — Root cause found & fixed today (already deployed locally)

**Symptom:** granting MEDICAL_REVIEWER extra permissions in the matrix
(e.g. `contracts.read`, `medical_catalog.read`, `employers.read`) had no
visible effect — the corresponding pages stayed hidden.

**Root cause:** `filterMenuItemsByRole()` (menu-items/components.jsx) and
`useDomainAccess()` (reporting/useReportDomainAccess.js) checked the
permission as an **AND** on top of the static `ROLE_RESOURCE_ACCESS`
resource ceiling:

```
if (!allowedResources.includes(resource)) return false;   // hard ceiling
if (permission && hasPermissionSignal) return permissions.includes(permission);
```

So a permission could only ever *narrow* a role's access, never *widen*
it past the resource the role was statically coded to have — exactly the
opposite of what a Roles & Permissions matrix is for. `RoleGuard`
(components/PermissionGuard.jsx), used by the actual `<Route>` guards,
already had the correct precedence (permission wins if it gives a
definite answer, resource is only the fallback for items with no
permission mapped) — the two systems had drifted apart.

**Fix applied:** both menu filtering and report-domain filtering now
match `RoleGuard`'s precedence — permission is authoritative when the
session has a signal, resource is only a fallback. Verified against the
real dev DB (MEDICAL_REVIEWER's 15 saved permissions) and rebuilt.
**Please re-test after login — this should now work end to end for menu
visibility and direct-link routing.**

---

## §2 — A more serious gap this incident exposed: enforcement is frontend-only

This needs to be said plainly before going further, because it's exactly
the "الأساس القوي" (strong foundation) you're asking for:

**Right now, granting or revoking a page permission in the matrix only
changes what the frontend shows/hides. It does not change what the
backend API allows.** Every controller is still gated by
`@PreAuthorize(hasAnyRole(...))` — a fixed role list baked into the Java
code at each of the ~80 controllers, untouched by anything in
`role_permissions`.

Two concrete failure modes follow from this:

- **Grant looks broken:** if WAAD_ADMIN grants MEDICAL_REVIEWER
  `contracts.read` via the matrix, the frontend (after §1's fix) now
  shows the Provider Contracts page and its menu item — but if
  `ProviderContractController` was never coded with
  `hasAnyRole(..., 'MEDICAL_REVIEWER')`, the reviewer will click in and
  get a 403 from the API. Visible page, broken page.
- **Revoke doesn't actually revoke:** if a role's backend `@PreAuthorize`
  already includes a role for some resource, revoking the matching
  permission in the matrix only hides the menu/page — a reviewer who
  knows (or guesses) the API URL, or uses the browser devtools / Postman,
  can still call it directly, because the backend never asked the
  permission catalog anything.

This is not something to patch quietly — it's a real architecture
decision with a real size (see §3, Workstream A). Flagging it now so
"per-user overrides" (Phase 3B) isn't built on top of a foundation that
only half-works.

---

## §3 — Proposed workstreams (in priority order)

### Workstream A — Backend permission enforcement (the "strong foundation")
**Ticket:** `WAAD-RBAC-BACKEND-PERMISSION-ENFORCEMENT-1`

Make the permission catalog the **real** authorization boundary for the
27 codes that already exist, not just a frontend read-model, without
regressing anyone's current access:

- Add a Spring `@PreAuthorize("@rbac.can('reports.claims')")`-style bean
  method backed by `EffectivePermissionService` (already computes the
  exact same effective set used by the frontend).
- Apply it **additively** alongside the existing `hasAnyRole(...)` on the
  controllers that map to the 27 catalog permissions (reports.*,
  preauth.read, medical_catalog.read, benefit_policies.read,
  provider_accounts.read, documents.read, plus the original 12) — i.e.
  `hasAnyRole(...) OR @rbac.can(...)`, so today's access never regresses
  the moment this ships, and a matrix **grant** becomes real immediately.
- Making a matrix **revoke** actually revoke access requires flipping the
  role's own `hasAnyRole(...)` entries to also require the permission
  (`hasAnyRole(...) AND @rbac.can(...)`) for that specific
  role+permission pair — this is the part that needs care per-controller
  so a role doesn't accidentally lose access nothing intended to remove.
- Backend test matrix: one authorization test per (role × permission)
  pair that currently exists in `role_permissions`, asserting the API
  itself — not just the menu — respects a grant/revoke.

This is the largest workstream here. Recommend doing it resource-by-resource
(reports first, since that's the newest and best-understood surface),
not as one big-bang change across 80 controllers.

### Workstream B — Reviewer ↔ Provider assignment (your item #1)
**Ticket:** `WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1`

No such link exists in the schema today — `users.provider_id` is a
**single** FK used for PROVIDER_STAFF scoping (one provider per staff
account), not a many-to-many "this reviewer covers these providers"
relationship. This is new, not a UI gap over existing data.

Proposed shape:
- New table `medical_reviewer_provider_assignments(user_id, provider_id,
  assigned_at, assigned_by)` — many-to-many, audited like
  `user_permission_overrides`.
- Backend: `GET/POST/DELETE /api/v1/admin/rbac/reviewers/{userId}/providers`.
- Frontend: in `UserEdit`/`UserDetails` for a MEDICAL_REVIEWER user, a new
  "مقدمو الخدمة المرتبطون" section — multi-select provider picker +
  assigned-providers table (matches your ask: "يمكن رؤية المستخدم في جدول
  مع مقدمي الخدمة المرتبطين به").
- Open question for you: should claim/visit review actually be *scoped*
  by this assignment (a reviewer can only review claims from their
  assigned providers), or is it informational/reporting-only for now?
  That changes whether `AuthorizationService`/claim-review queries need
  to filter by it — please confirm before I design the enforcement side.

### Workstream C — User list filtering (your item #3)
**Ticket:** `WAAD-RBAC-USERS-FILTER-PERFORMANCE-1`

Current `UsersList.jsx` loads **all** users client-side and filters in
JS (confirmed while building the quick-filter chips earlier this
session) — fine at today's scale, won't hold at "400 providers." Needed:
- Move to server-side paginated search: `GET /admin/users?role=&providerId=&employerId=&q=&page=`.
- Provider/employer filter as an async-search `Autocomplete` (type-ahead
  hitting a lightweight `/providers/search?q=` / `/employers/search?q=`
  endpoint), not a full dropdown — matches your "400 providers" concern
  directly.
- Keep the role quick-filter chips already added; add a provider-staff-
  specific "أعضاء مقدم الخدمة: [بحث]" filter next to them.

### Workstream D — Regression coverage for the matrix itself
**Ticket:** `RBAC-PERMISSION-MATRIX-REGRESSION-SUITE-1`

To stop this exact class of bug (matrix says one thing, real access says
another) from recurring, especially before Phase 3B (per-user overrides)
adds even more surface:
- Frontend unit tests for `filterMenuItemsByRole`/`useDomainAccess`
  covering the exact scenario that just broke: permission granted beyond
  the role's static resource list must show the item.
- A small Cypress/Playwright (or existing e2e tool, whichever this repo
  already uses — need to check) smoke test: log in as a seeded
  MEDICAL_REVIEWER test account, toggle a permission via the matrix API,
  reload, assert menu/route state — automates what you just did by hand.

---

## §4 — What I'd suggest doing next

Given Workstream A is genuinely large (touches most controllers) and B
needs a scoping decision from you, I'd suggest:

1. You confirm the priority order above (or reorder it).
2. I start Workstream A narrowly — just the **reports.\*** permissions
   first, since that surface is smallest, newest, and best understood —
   as its own ticket, additive-only (no access removed), with a full
   backend test matrix, before touching any other controller.
3. In parallel/next, Workstream C (user filters) since it's self-contained
   and doesn't depend on a scoping decision.
4. Workstream B waits on your answer to the scoping question above.

No implementation has started on A/B/C/D — awaiting your go-ahead and
priority call.
