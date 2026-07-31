# WAAD-RBAC-PHASE-2-FRONTEND-INTEGRATION — Report

**Branch:** `main`
**Mode:** Implemented locally. **Not committed. Not pushed.** Awaiting review, per ticket instructions.
**Date:** 2026-07-31

---

## 1. Executive summary

Phase 1 (`01105d8`) added the `WAAD_ADMIN` role, a permission catalog, and `EffectivePermissionService` on the backend, but the frontend didn't know either existed — `WAAD_ADMIN` had no entry in `roleAccessMap.js`, meaning a `WAAD_ADMIN` user would see an empty sidebar and get redirected to `/403` on every resource-guarded route, including `/admin/users` itself.

This phase:
- Added `WAAD_ADMIN` to every frontend role constant/map (`constants/rbac.js`, `roleAccessMap.js`, `roleRoutes.js`), scoped **narrowly and deliberately** to what the backend actually permits it to do today (see §3 — this is the most important decision in this report).
- Wired `/auth/session/me` to return real effective permissions instead of a hardcoded empty list (small, safe backend change).
- Extended `PermissionGuard` with an optional, additive permission-code check that falls back to the existing resource/role logic when no signal is present — zero behavior change for any existing route.
- Updated `/admin/users` (list, details, edit) so `WAAD_ADMIN` can use it, added a read-only "effective permissions" section, and added proactive UI-side blocking (not just reliance on backend 403s) for the four actions `WAAD_ADMIN` must never be able to do to a `SUPER_ADMIN` account.
- Added the 6 requested Arabic labels.

`npx vite build` succeeds, ESLint reports 0 errors on all 9 changed files, and the full backend RBAC test suite (21 tests) still passes after the `/me` change.

## 2. Files changed (9 total)

Backend:
- `backend/src/main/java/com/waad/tba/modules/auth/service/AuthService.java`

Frontend:
- `frontend/src/constants/rbac.js`
- `frontend/src/config/roleAccessMap.js`
- `frontend/src/utils/roleRoutes.js`
- `frontend/src/components/PermissionGuard.jsx`
- `frontend/src/services/rbac/users.service.js`
- `frontend/src/pages/rbac/users/UserDetails.jsx`
- `frontend/src/pages/rbac/users/UsersList.jsx`
- `frontend/src/pages/rbac/users/UserEdit.jsx`

**Not touched** (inspected, no change needed): `frontend/src/routes/MainRoutes.jsx` (the `/admin/users` routes already use `resource="users"` — correct once `roleAccessMap.js` has a `WAAD_ADMIN` entry with `'users'` in it) and `frontend/src/menu-items/components.jsx` (the sidebar's "المستخدمين" item at line ~746 already declares `resource: 'users'` — same reasoning). `UserCreate.jsx` was inspected and needs no change: it already excludes `SUPER_ADMIN` from its role dropdown for **every** actor, which already satisfies "cannot assign SUPER_ADMIN" for `WAAD_ADMIN` too.

## 3. WAAD_ADMIN frontend mapping — and why it's narrow

The ticket says "WAAD_ADMIN should have broad operational access similar to SUPER_ADMIN except critical security/danger-zone areas." I did not implement that literally, and want to be explicit about why, since it's a deliberate deviation from the ticket's suggested shape rather than an oversight.

I checked what Phase 1 actually changed on the backend (re-reading `UserController.java`, then checking every other controller `WAAD_ADMIN` might touch):
- `DashboardController.java` (line 65 etc.): `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")` — **no `WAAD_ADMIN`**.
- `DangerZoneController.java` (line 23): `@PreAuthorize("hasRole('SUPER_ADMIN')")` — **no `WAAD_ADMIN`**, and this is the "danger-zone" distinction the ticket refers to; it already exists and already excludes `WAAD_ADMIN`, entirely on the backend.
- Every other controller I sampled from the Phase 1 audit (claims, settlements, providers, reports, system settings, ...) is unchanged from before Phase 1 — still `SUPER_ADMIN`-only or role-lists that don't include `WAAD_ADMIN`.

Phase 1's own report was explicit about this: *"this ticket only had to make WAAD_ADMIN exist and be safe with respect to SUPER_ADMIN, not decide its access to every module."* Only `UserController` was widened.

Given that, giving `WAAD_ADMIN` broad frontend resources (`dashboard`, `claims`, `settlements`, `system_settings`, ...) would show it menu items and pages whose every API call 403s — a worse UX than not showing them, and precisely the "obvious wrong action" the ticket's own security rules ask the UI to prevent ("Do not rely only on frontend blocking... UI should prevent obvious wrong actions" — that cuts both ways: don't show doors that are actually locked, either).

**Decision:** `WAAD_ADMIN`'s `ROLE_RESOURCE_ACCESS` entry is `['users']` — the one resource Phase 1 actually backs. Its default post-login landing route (`roleRoutes.js`) is `/admin/users`, not `/dashboard` (which it cannot open). This is documented inline in both files with a pointer to widen them "only in lockstep with widening the corresponding backend `@PreAuthorize`" — i.e., every future resource grant to `WAAD_ADMIN` should be paired with confirming (or making) the matching backend change first. This is flagged as the single largest piece of Phase 3 work (§10).

Also updated for `WAAD_ADMIN` in `constants/rbac.js`:
- `SystemRole.WAAD_ADMIN = 'WAAD_ADMIN'`, `RoleDisplayNames.WAAD_ADMIN = { ar: 'مدير وعد', en: 'WAAD Admin' }`, `RolePrivilegeLevel.WAAD_ADMIN = 900` (below `SUPER_ADMIN`'s 999, above everything else).
- `isAdminRole(role)` now also returns true for `WAAD_ADMIN`.
- `getAssignableRoles(currentRole)`: `WAAD_ADMIN` can now assign every role except `SUPER_ADMIN` (was previously `[]` — `WAAD_ADMIN` didn't exist, so this path was unreachable; now it mirrors ticket rule 2, "manages normal users and operational roles").
- `canModifyRole(WAAD_ADMIN, SUPER_ADMIN)` was already `false` under the existing privilege-level formula once `WAAD_ADMIN`'s level (900) is below `SUPER_ADMIN`'s (999) — no special-case code needed.

`BENEFICIARY` was deliberately **not** added anywhere on the frontend — it's a reserved, backend-inert future role (not assignable via `UserCreateDto`/`UserUpdateDto` yet), so there's nothing for the frontend to do with it yet.

## 4. /me effectivePermissions status: DONE (small, safe backend change)

Per the ticket's "Preferred" option. `LoginResponse.UserInfo.permissions` already existed as a field but was hardcoded to `List.of()` in both `AuthService.getUserInfo()` (session path, backs `/auth/session/me`) and `AuthService.getCurrentUser()` (JWT path). Both now call:

```java
List<String> permissions = List.copyOf(effectivePermissionService.getEffectivePermissions(user));
```

`EffectivePermissionService` was constructor-injected into `AuthService` (no circular dependency — it only depends on repositories and `UserSecurityService`). This is the exact same read-model from Phase 1; nothing about authorization changed — `@PreAuthorize`/`hasRole(...)` is still the enforced boundary, this only populates a field the frontend can now read. Verified: `mvn -o compile` clean, and the full `UserServiceTest`/`EffectivePermissionServiceTest` suite (21 tests) still passes (this doesn't directly test `AuthService`, but confirms `EffectivePermissionService`'s contract, which `AuthService` now depends on, is unchanged and correct).

No `AuthServiceTest` exists in the codebase to extend; adding one was judged out of scope for a "small, safe" wiring change with no branching logic of its own (it's a single method call whose behavior is already covered by `EffectivePermissionServiceTest`).

## 5. PermissionGuard changes

Added an optional `permission` prop to `RoleGuard` (`PermissionGuard.jsx`'s actual implementation) and a new `hasEffectivePermission(user, code)` helper:

- Returns `true`/`false` when `user.permissions` (now populated by §4) is a non-empty array — a definite answer.
- Returns `null` when `user.permissions` is absent/empty (e.g., a session cached from before this phase shipped) — signals "no data, fall back."

In `RoleGuard`, a `true`/`false` result from `hasEffectivePermission` is used directly (short-circuits before the resource/role check); a `null` result falls through to the pre-existing resource/role logic unchanged. **No route in `MainRoutes.jsx` currently passes a `permission` prop**, so this is purely additive infrastructure — every existing guard's behavior is provably unchanged (confirmed by reading the diff: the only new code paths are gated behind `if (permission)`, which is never true today). Also added `useHasPermission(code)`, mirroring `useHasRole`, for non-JSX checks (e.g. conditionally showing a button) — not yet consumed anywhere, prepared for Phase 3.

`SUPER_ADMIN` still bypasses everything first, unchanged. Unknown/unmapped roles still fail closed on resource/role checks, unchanged.

## 6. User management UI changes

- **`UsersList.jsx`**: added `useAuth()` to read the current actor; the existing "toggle active" button was already disabled for any `SUPER_ADMIN` target regardless of actor (pre-existing, kept as-is — it's actually more conservative than the ticket requires, which is fine). Added: the **Edit** button is now also disabled, with a tooltip reading "غير مسموح بتعديل مدير النظام الأعلى", specifically when the current actor is `WAAD_ADMIN` and the row's target is `SUPER_ADMIN`.
- **`UserEdit.jsx`**: after loading the target user, if the current actor is `WAAD_ADMIN` and the target is `SUPER_ADMIN`, the page renders a blocking `Alert` (with a lock icon) instead of the edit form — the user never sees a pointless form that would 403 on submit. As defense-in-depth (race conditions, direct API calls, a future code path that reaches `handleSubmit` some other way), the submit error handler now also maps any `403` response to the same Arabic message instead of surfacing the backend's raw (English) `AccessDeniedException` text.
- **`UserDetails.jsx`**: added the same warning banner (view-only context, so it's informational rather than blocking — there's nothing to block on a read-only page) plus the new effective-permissions section (§7).
- Role-assignment protection: `UserEdit.jsx`'s `Step2Roles` component already disabled the `SUPER_ADMIN` checkbox/option for **every** actor (pre-existing, not part of this phase) — this already satisfies "WAAD_ADMIN cannot assign SUPER_ADMIN role" and needed no change. `UserCreate.jsx` already excludes `SUPER_ADMIN` from its role dropdown entirely, same effect.
- **Graceful 403 handling**: covered above for `UserEdit.jsx`. `UsersList.jsx`'s toggle-status action cannot actually reach a `WAAD_ADMIN`-vs-`SUPER_ADMIN` 403 through the UI at all, since the toggle button is already disabled for any `SUPER_ADMIN` target regardless of actor — so there was no blank-page/raw-error risk to fix there.

## 7. Effective permissions display

`users.service.js` gained `getEffectivePermissions(id)` → `GET /admin/users/{id}/effective-permissions`.

`UserDetails.jsx` gained a new `MainCard` titled "صلاحيات فعلية" containing `EffectivePermissionsDisplay`, which fetches and renders the codes as chips:
- Each code is looked up in a new `PermissionLabels` map (`constants/rbac.js`) for its Arabic label — this map is a **display-only mirror** of the `label_ar` values already seeded in `V101__rbac_phase1_foundation.sql` (not a second source of truth for access; the effective-permissions endpoint returns bare codes, so some client-side label lookup is unavoidable for a readable UI). Unmapped codes fall back to the raw code string, never invented text.
- Codes in `CriticalSecurityPermissions` (currently just `settings.manage`, mirroring the backend's `critical_security` flag) render as a filled red chip with a lock icon and a "صلاحية حساسة" tooltip.
- An info banner explains: "صلاحيات موروثة من الدور — لا توجد بعد واجهة لإدارة الاستثناءات الفردية (صلاحيات خاصة)؛ هذه القائمة للعرض فقط حالياً."

Per the ticket, since override CRUD doesn't exist yet, this is **read-only**. Follow-up ticket, as requested: **WAAD-RBAC-PHASE-3-USER-OVERRIDES-UI**.

## 8. SUPER_ADMIN protection in UI

Summarized from §6: `WAAD_ADMIN` cannot, from the UI, edit (blocked page + disabled button), delete (no delete UI exists at all today, see note below), deactivate (pre-existing disabled toggle, applies to all actors), or assign the `SUPER_ADMIN` role (pre-existing dropdown/checkbox exclusion, applies to all actors) to/for a `SUPER_ADMIN` account. All of these mirror backend rules already enforced in `UserService.java` (Phase 1) — the UI changes are prevention of "obvious wrong actions," not a replacement for that enforcement.

**Note on delete**: `usersService.deleteUser(id)` exists in the API service layer but **no button in the current UI calls it** — `UsersList.jsx` only has view/edit/toggle actions. So there was no delete-related UI to add a `WAAD_ADMIN` guard to; nothing was changed here because there was nothing to change.

## 9. Validation results

- `git status --short` / `git diff --check` / `git diff --stat` — all run; scope confirmed as exactly the 9 files in §2, diff totals 320 insertions / 20 deletions, `--check` reports only benign CRLF-on-next-touch warnings (no actual conflict markers).
- `npx vite build` — **succeeded** (only pre-existing chunk-size advisory warnings, unrelated to this change).
- `npx eslint` on all 9 changed files — **0 errors**, 35 warnings, all either pre-existing (unused imports/vars in files I only partially touched, missing-dependency hints in `useEffect` calls that predate this change) or pure Prettier formatting-style nits on lines I didn't author. One real issue caught and fixed during this pass: a duplicate `LockIcon` import in `UserEdit.jsx` (it already imported that icon for the password-reset section) — removed my duplicate.
- Backend: `mvn -o compile` clean; `mvn -o test -Dtest=UserServiceTest,EffectivePermissionServiceTest` — **21/21 passing**, confirming the `/me` change's dependency (`EffectivePermissionService`) still behaves correctly.

## 10. Browser results

**Not performed** — no browser-automation tool is available in this session. Everything above was verified at the code/build/test level (compile, lint, unit tests, and a manual trace of every new conditional against the actual data shapes returned by the relevant backend endpoints). If a browser-capable session is available, the ticket's manual test matrix (SUPER_ADMIN/WAAD_ADMIN/MEDICAL_REVIEWER/PROVIDER_STAFF/EMPLOYER_ADMIN scenarios listed in the ticket) should be run before merging, the same way earlier RBAC work in this repo was browser-verified by a separate session.

## 11. Remaining Phase 3 work

- **Widen `WAAD_ADMIN`'s backend `@PreAuthorize`, module by module** (dashboard, claims, settlements, providers, reports, system settings — in whatever order the product wants), and widen `roleAccessMap.js`'s `WAAD_ADMIN` entry **in lockstep, never ahead of the backend change**. This is the direct continuation of what this report's §3 deliberately did not do yet.
- **`WAAD-RBAC-PHASE-3-USER-OVERRIDES-UI`**: CRUD for `user_permission_overrides` — currently `EffectivePermissionService.createOverride()` has no controller endpoint at all (noted in the Phase 1 report too), so there's a backend gap to close before a create/revoke override UI has anything to call.
- **`useHasPermission`/`permission` prop are unused today** — once specific routes/components have a natural 1:1 permission code (e.g. `claims.review` on the claim-approve button), wire them in; this phase only prepared the mechanism.
- Consider whether `SystemSettingsPage.jsx`'s hard `if (!isSuperAdmin) return <Alert>...</Alert>` (7 call sites gating that whole page) should be loosened for `WAAD_ADMIN` once `settings.manage`-backed endpoints are actually opened to it on the backend — deliberately not touched in this phase since it wasn't in the ticket's explicit file list and mixes routine settings with backend-confirmed-dangerous actions (backup restore/reset) that must stay `SUPER_ADMIN`-only.

## 12. No-push confirmation

**Not committed. Not pushed.** All changes exist only in the local working tree on `main`, awaiting review per the ticket's work-mode instructions. `git add .` was never used — every file was staged/verified individually against the expected scope (once approved for commit).

---

## Final status

**WAAD-RBAC-PHASE-2-FRONTEND-INTEGRATION — READY FOR REVIEW**

(Note: "READY," not "fully broad WAAD_ADMIN access" — see §3 for why the scope is narrower than the ticket's suggested phrasing, and why that's the safer reading of "Do not rely only on frontend blocking... UI should prevent obvious wrong actions.")
