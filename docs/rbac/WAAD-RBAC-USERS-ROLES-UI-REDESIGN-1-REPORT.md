# WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1 — Report

**Branch:** `main`
**Mode:** Implemented locally. **Not committed. Not pushed.** Awaiting review, per ticket instructions.
**Date:** 2026-07-31

---

## 1. Executive summary

`/admin/users` is now a tabbed shell with two fully-real, working tabs and two honestly-labeled placeholders. Tab 1 (المستخدمون) reuses the existing, already real-data-backed user list/detail/edit pages from Phases 1–3A unchanged. Tab 2 (الأدوار والصلاحيات) is genuinely new: a role/permission matrix backed by three new backend endpoints (permission catalog, role summaries, per-role permission CRUD) that read and write the real `permissions`/`role_permissions` tables from Phase 1 — nothing in it is mocked or locally-only state. Tabs 3 and 4 (user overrides, audit log) have no backend endpoint to read/write yet, so they render a disabled placeholder card naming the exact follow-up ticket, per the ticket's explicit instruction not to fake functionality.

The reference `users.tsx` was used for layout ideas only (tabs, grouped permission cards, copy-from-role, select-all/remove-all, effective-permission chips) — its `SEED_USERS`, `ROLES`, and `PERMISSION_GROUPS` static data model was not copied; every list, count, and permission shown comes from a real API call.

11 new backend tests added, all passing. Full suite: 328 tests, same 18 failures + 5 errors as the established pre-existing baseline (zero new regressions). Frontend builds and lints clean (0 errors on all changed files).

## 2. What UI was redesigned

- **`frontend/src/pages/rbac/users/index.jsx`** — rewritten from a bare `export default UsersList` re-export into the tabbed shell described above, at the *same* `/admin/users` route (no new route created, per the ticket's instruction).
- **`frontend/src/pages/rbac/users/RolePermissionsMatrix.jsx`** (new) — the Tab 2 content: left-hand role cards (display name, permission count, assigned-user count, editable/protected/reserved badges) and a right-hand grouped permission matrix (checkboxes, group select-all/remove-all, copy-permissions-from-another-role, dirty-state tracking, sticky save bar showing affected user count).
- Tabs 3 and 4 are inline `ComingSoonPanel` components in `index.jsx` — an `Alert` with the exact Arabic message the ticket specified, plus the follow-up ticket name, not a fake form.

**Not touched, deliberately**: `UsersList.jsx`, `UserDetails.jsx`, `UserEdit.jsx`, `UserCreate.jsx` — all already real-data-driven from prior phases (search/filter/role badges/active-toggle/effective-permissions/reset-password/SUPER_ADMIN protections all already work, verified again in this ticket by reading them, not by assumption). See §5 for why Tab 1 wasn't rebuilt as a from-scratch inline master-detail panel like the reference sketch.

## 3. What real APIs are used

**Already existing (Phases 1–3A, reused unchanged by Tab 1):**
- `GET/POST/PUT/DELETE /api/v1/admin/users` (+ `/search`, `/paginate`, `/{id}/toggle-status`) — `UserController`/`UserService`.
- `GET /api/v1/admin/users/{id}/effective-permissions` — `EffectivePermissionService`.
- `PUT /api/v1/admin/users/{id}/reset-password` — added in the (separately uncommitted) `RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1` ticket; already wired into `UserEdit.jsx`.

**New in this ticket (Tab 2), backend `RolePermissionAdminController` at `/api/v1/admin/rbac`, guarded by the same `hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN')` as user management:**
- `GET /permissions/grouped` — the 14-entry Phase 1 permission catalog, grouped by `group_name`. Returns `code`, `groupName`, `labelAr`, `labelEn`, `sensitive`, `criticalSecurity` per permission — the frontend uses the backend's own Arabic labels, not a hardcoded copy.
- `GET /roles` — one summary per `SystemRole` enum value: display name, live permission count (`role_permissions` row count, or the full catalog size for SUPER_ADMIN), live user count (`UserRepository.countByUserType`, new repository method), `editable` flag (false only for SUPER_ADMIN), `reserved` flag (true only for BENEFICIARY).
- `GET /roles/{role}/permissions` — a role's current permission codes (SUPER_ADMIN synthesizes "the entire catalog", matching `EffectivePermissionService`'s own logic — not a separate, potentially-divergent code path).
- `PUT /roles/{role}/permissions` — replaces a role's permission set. New service `RolePermissionAdminService.updateRolePermissions()`.

## 4. Which features are active

- Full user list/search/filter/role-badges/active-toggle (Tab 1, pre-existing).
- User detail view with effective permissions display (Tab 1, pre-existing).
- User create/edit, including reset-password and role assignment with existing SUPER_ADMIN-role-exclusion (Tab 1, pre-existing).
- Full role/permission matrix: view any role's permissions, toggle individual permissions, select/remove all in a group, copy an entire role's permission set from another role, save with a real `PUT` call, dirty-state warning, affected-user-count display (Tab 2, **new, fully functional**).

## 5. Which features are read-only/disabled due to missing backend endpoint

- **SUPER_ADMIN's row in the matrix is permanently read-only** — not "missing an endpoint," but architecturally correct: `EffectivePermissionService` hardcodes SUPER_ADMIN to "the entire catalog" in code, not a stored row, so there is nothing in `role_permissions` to edit. The UI shows all 14 permissions checked with a lock icon and an explanatory `Alert`, and the backend independently rejects any attempt to edit it (`AccessDeniedException`) even if the UI were bypassed.
- **BENEFICIARY's row is shown as "reserved"** — zero permissions, matrix area shows a warning explaining it's a future phase, matching Phase 1's decision to leave it unassignable.
- **Tab 3 (الصلاحيات الخاصة للمستخدمين)** — fully disabled placeholder. `user_permission_overrides` table and `EffectivePermissionService`'s override-merging logic exist (Phase 1), but there is no controller exposing create/list/revoke for an individual override yet (`EffectivePermissionService.createOverride()` is a service method with no endpoint, confirmed in the Phase 1 report too). Follow-up: **WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI**.
- **Tab 4 (سجل تغييرات الصلاحيات)** — fully disabled placeholder. `user_audit_log` rows are genuinely written on every relevant action (including the new `ROLE_PERMISSIONS_UPDATED` action added in this ticket), but no controller reads them back for display. Follow-up: **WAAD-RBAC-PERMISSION-AUDIT-UI-1**.
- **Tab 1 is not an inline master-detail panel** like the `users.tsx` reference sketch — it reuses the existing separate list/detail/edit *pages* (navigation-based) rather than merging them into one in-page panel. This was a deliberate scope decision given the size of this ticket: those pages are already real-data-driven, already tested across three prior phases (effective-permissions display, SUPER_ADMIN protection, reset-password), and rebuilding them as an inline panel is a UI-layout change with no functional gain, at real risk of regressing already-working, already-verified behavior. If a true inline merge is wanted, recommend it as its own follow-up (e.g. **WAAD-RBAC-USERS-INLINE-PANEL-UI-1**) so it can be reviewed and tested in isolation from this ticket's real new work (the permission matrix).

## 6. SUPER_ADMIN/WAAD_ADMIN protections

**In the new Roles & Permissions matrix (Tab 2):**
- SUPER_ADMIN's permission set cannot be edited by anyone — enforced both in the UI (checkboxes disabled, no save bar rendered for that role) and in the backend (`RolePermissionAdminService.updateRolePermissions()` throws `AccessDeniedException` for `role == SUPER_ADMIN` regardless of actor).
- A WAAD_ADMIN actor cannot toggle any permission flagged `critical_security` (currently only `settings.manage`) for *any* role, including WAAD_ADMIN's own row — mirrored in the UI (that checkbox is disabled with a lock icon + tooltip for a WAAD_ADMIN viewer) and enforced backend-side (compares the requested set against the current set for exactly the critical-security codes; any difference → 403), matching the exact restriction already built into `EffectivePermissionService.createOverride()` in Phase 1.
- A 403 from the save call is caught and shown as a specific Arabic message ("لا يمكنك تعديل هذه الصلاحية — قد تكون صلاحية حساسة محمية أو تتعلق بمدير النظام الأعلى"), not a blank page or raw stack trace.

**In Tab 1 (unchanged, re-verified from prior phases):** SUPER_ADMIN cannot be deleted/deactivated/demoted/reassigned by a WAAD_ADMIN actor; the existing Arabic protected-message pattern (`RbacUiLabels.superAdminProtected`) is shown consistently across the list/detail/edit pages, as documented in the Phase 2/3A reports.

## 7. Permission matrix behavior

Exactly as specified in the ticket: group headers show `selected/total` counts with a select-all/remove-all toggle; individual checkboxes toggle one permission; a "copy permissions from another role" dropdown fetches that role's live permission set and stages it (does not save until "حفظ صلاحيات الدور" is clicked); dirty state is computed by comparing the staged set against the last-loaded set and gates both the "تراجع" (revert) and save buttons; the save bar shows how many users are assigned to the role being edited, so an admin can see the blast radius before committing.

## 8. Effective permissions display

Unchanged from Phase 2 — `UserDetails.jsx`'s existing `EffectivePermissionsDisplay` component (calls `GET /admin/users/{id}/effective-permissions`) is still the per-user effective-permissions view, reachable from Tab 1 by opening a user's detail page. This ticket did not need to touch it; it already renders real per-user data with the same Arabic labels/critical-security badges introduced in Phase 2.

## 9. Tests / build results

**New backend tests (11):**
- `RolePermissionAdminServiceTest` (8): role summaries mark SUPER_ADMIN non-editable and BENEFICIARY reserved; editing SUPER_ADMIN's permissions is rejected; unknown role/unknown permission code rejected; WAAD_ADMIN actor blocked from toggling a critical-security permission; WAAD_ADMIN actor allowed to change non-critical permissions; SUPER_ADMIN actor allowed to change critical-security permissions (sanity check that only WAAD_ADMIN is restricted); SUPER_ADMIN's `getRolePermissionCodes` returns the full catalog.
- `RolePermissionAdminControllerAuthorizationTest` (3): real `@PreAuthorize` enforcement — WAAD_ADMIN can read roles; MEDICAL_REVIEWER gets 403 reading roles; PROVIDER_STAFF gets 403 reading the permission catalog. Same minimal-context MockMvc pattern already used by `KinshipMismatchControllerAuthorizationTest`/`WaadAdminControllerAccessAuthorizationTest` — no full `@SpringBootTest`, no DB.
- All 11 passing; targeted RBAC suite (this ticket + Phases 1–3A + the uncommitted legacy-cleanup ticket) — **50/50 passing**.
- Full `mvn -o test` — **328 tests** (317 + 11 new), **18 failures + 5 errors** — identical to the established pre-existing baseline, zero new failures.
- `mvn -o compile` — clean.
- `npx eslint` on all new/changed frontend files — **0 errors** (3 pre-existing-style Prettier formatting nits, no functional issues).
- `npx vite build` — succeeded (only pre-existing chunk-size advisory warnings).

## 10. Browser results

**Not performed** — no browser-automation tool available in this session, consistent with every prior phase. The user previously requested a container rebuild after Phase 3A to test in-browser (confirmed working at that point); the same `waad.ps1 rebuild` step would need to run again to pick up this ticket's frontend/backend changes before a browser check. Recommend the ticket's manual test matrix (SUPER_ADMIN opens the matrix and edits a role; WAAD_ADMIN attempts to toggle `settings.manage` and sees it disabled; MEDICAL_REVIEWER/PROVIDER_STAFF/EMPLOYER_ADMIN still cannot reach `/admin/users` at all) once rebuilt.

## 11. Remaining follow-ups

- **WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI** — CRUD endpoints for `user_permission_overrides` (create/list/revoke a per-user exception), then Tab 3 becomes real.
- **WAAD-RBAC-PERMISSION-AUDIT-UI-1** — a read endpoint over `user_audit_log` (filterable by user/action/date), then Tab 4 becomes real.
- **WAAD-RBAC-USERS-INLINE-PANEL-UI-1** (optional, not required) — if an inline master-detail panel (matching the `users.tsx` reference layout exactly, replacing navigation to `/admin/users/:id` and `/admin/users/:id/edit`) is still wanted, recommend it as its own reviewable ticket rather than folding it into this one.
- Consider whether the permission catalog itself should grow beyond the current 14 entries (the ticket's suggested group list included several groups — "الموافقات المسبقة", "الزيارات والمستندات", "التصنيف وقوائم الأسعار", "بوابة المستفيد" — that don't exist in the Phase 1 catalog yet). Not invented here, per the ticket's explicit "do not invent unsaved permissions" instruction; a future ticket should decide whether/how to extend the catalog before the UI can show those groups.

## 12. Files changed

**This ticket only** (the separately-reported, still-uncommitted `RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1` ticket's files — `UserController.java`, `UserService.java`, `UserServiceTest.java`, `users.service.js`, `UserResetPasswordDto.java`, and the 5 deleted legacy files — are tracked separately in its own report and are not part of this list):

**New:**
- `backend/src/main/java/com/waad/tba/modules/rbac/controller/RolePermissionAdminController.java`
- `backend/src/main/java/com/waad/tba/modules/rbac/service/RolePermissionAdminService.java`
- `backend/src/main/java/com/waad/tba/modules/rbac/dto/{PermissionDto,PermissionGroupDto,RoleSummaryDto,UpdateRolePermissionsRequestDto}.java`
- `backend/src/test/java/com/waad/tba/modules/rbac/service/RolePermissionAdminServiceTest.java`
- `backend/src/test/java/com/waad/tba/modules/rbac/controller/RolePermissionAdminControllerAuthorizationTest.java`
- `frontend/src/pages/rbac/users/RolePermissionsMatrix.jsx`
- `frontend/src/services/rbac/rolePermissions.service.js`
- `docs/rbac/WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1-REPORT.md`

**Modified:**
- `backend/src/main/java/com/waad/tba/modules/rbac/entity/UserAuditLog.java` (added `ACTION_ROLE_PERMISSIONS_UPDATED`)
- `backend/src/main/java/com/waad/tba/modules/rbac/repository/UserRepository.java` (added `countByUserType`)
- `frontend/src/pages/rbac/users/index.jsx` (rewritten as the tabbed shell)

## 13. No-push confirmation

**Not committed. Not pushed.** All changes exist only in the local working tree on `main`, awaiting review per the ticket's work-mode instructions. `git add .` was not used.

---

## Final status

**WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1 — PARTIALLY READY — explain**

Ready for review and, I'd argue, ready to ship: the two active tabs (Users, Roles & Permissions) are fully real, tested, and functional. Marked "partially ready" rather than fully "ready" only because two of the four requested tabs are intentionally inert placeholders pending backend work that's explicitly out of this ticket's authorized scope (per the ticket's own instructions for tabs 3/4), and because Tab 1 reuses existing pages via navigation rather than the fully-inlined master-detail panel the reference sketch showed (§5) — both are documented, deliberate scope decisions, not gaps I missed.
