# RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1 — Report

**Branch:** `main`
**Mode:** Implemented locally. **Not committed. Not pushed.** Awaiting review, per ticket instructions.
**Date:** 2026-07-31

---

## 1. Executive summary

The legacy `systemadmin.UserManagementController` was audited end-to-end (frontend callers, backend references, git history). Verdict: **partially live, not fully dead** — one of its two endpoints (`PUT /{id}/reset-password`) was actually wired to a real UI action (`UserEdit.jsx`'s "Reset Password" section); the other (`PUT /{id}/toggle`) had zero callers, and six of the eight methods on its backing service were dead code never called by anything.

Per the ticket's preference for **consolidation over duplication**, I did not just patch the legacy controller — I:
1. Added the missing capability (admin password reset) to the real `rbac.UserController`/`UserService` path, with the same `WAAD_ADMIN`-vs-`SUPER_ADMIN` protection pattern already used for delete/deactivate/demote.
2. Repointed the one live frontend caller at the new endpoint.
3. **Deleted** the entire legacy controller, its service, and its three now-unused DTOs — there is no longer a second, competing user-management surface.

Backend compiles clean, all 39 targeted RBAC tests pass (3 new), full suite shows no new regressions (317 tests, same 18 failures + 5 errors as the pre-existing baseline), and the frontend builds and lints clean.

## 2. Audit findings (was it used?)

| Endpoint | Status | Evidence |
|---|---|---|
| `PUT /{id}/toggle` | **Dead** — zero callers | Frontend's `usersService.toggleUserStatus()` calls `PATCH /admin/users/{id}/toggle-status` on `rbac.UserController` instead; nothing in the codebase calls the legacy path. |
| `PUT /{id}/reset-password` | **Live** | `frontend/src/services/rbac/users.service.js` (`resetPassword()`) called `PUT /admin/user-management/{id}/reset-password` — invoked from `UserEdit.jsx`'s dedicated "Reset Password" section handler and from the main "save user" flow's auto-reset-if-filled branch. |

`UserManagementService`'s other six methods (`getAllUsers`, `getUserById`, `searchUsers`, `createUser`, `updateUser`, `deleteUser`) were **never called by anything** — not even by the controller that owned the service. Fully dead code layered under a partially-dead controller.

No other backend class referenced `UserManagementController` or `UserManagementService` (no schedulers, no other services, no tests). `docs/system-analysis/04-api-catalog.md` already documented this exact overlap as known technical debt.

## 3. What I actually did (implementation)

**Consolidated the one live capability into the real path:**
- `UserService.java` (rbac) — new `resetPasswordByAdmin(Long id, String newPassword)`. Same protection shape as `delete()`/`toggleStatus()`/`update()`: if the actor is `WAAD_ADMIN` and the target is `SUPER_ADMIN`, throws `AccessDeniedException`. `SUPER_ADMIN` itself remains unrestricted (it may reset its own or another `SUPER_ADMIN`'s password — resetting isn't the same "de-privilege" concern as delete/deactivate/demote, and there was no reason to block it). Audits via the existing `UserAuditLog.ACTION_PASSWORD_RESET`.
- `UserResetPasswordDto.java` (new, rbac) — `newPassword` field validated with the same `@PasswordPolicy` annotation `UserCreateDto` already uses, so the new endpoint enforces the same password strength rules the legacy one never did (it took a raw, unvalidated string).
- `UserController.java` (rbac) — new `PUT /{id:\d+}/reset-password`, covered by the controller's existing class-level `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN')")` — no separate annotation needed.

**Repointed the frontend:**
- `frontend/src/services/rbac/users.service.js` — `resetPassword()` now calls `PUT /admin/users/{id}/reset-password` (the real path) instead of `/admin/user-management/{id}/reset-password`. No changes needed in `UserEdit.jsx` — it calls `usersService.resetPassword()`, which now transparently hits the safe endpoint.

**Removed the legacy surface entirely** (not deprecated-in-place — fully deleted, since after the repoint nothing calls it and it duplicated a capability the real path now has):
- `backend/src/main/java/com/waad/tba/modules/systemadmin/controller/UserManagementController.java`
- `backend/src/main/java/com/waad/tba/modules/systemadmin/service/UserManagementService.java`
- `backend/src/main/java/com/waad/tba/modules/systemadmin/dto/{UserViewDto,UserCreateDto,UserUpdateDto}.java` (confirmed used only by the two files above — grepped the whole backend tree before deleting)

`systemadmin.service.AuditLogService` (used by the deleted service) was **not** touched — it's a shared audit service used by several other unrelated modules (backup, danger zone, monitoring), confirmed via grep before making any decision about it.

## 4. Why this satisfies "do not create a second competing user-management flow"

Before: two controllers could both act on a user's account (`rbac.UserController` — protected; `systemadmin.UserManagementController` — unprotected), and the frontend's own `usersService` file called one method on each depending on which action you clicked. After: `rbac.UserController`/`UserService` is the **only** user-management surface; `usersService.js` calls exactly one controller for every user-account action (CRUD, toggle-status, reset-password, effective-permissions).

## 5. Tests / build results

- **3 new tests** added to `UserServiceTest.java` (same file/style as the existing Phase 1 SUPER_ADMIN-protection tests):
  - `resetPasswordByAdmin_waadAdminActor_cannotResetSuperAdminPassword` — WAAD_ADMIN → SUPER_ADMIN target → `AccessDeniedException`, no save.
  - `resetPasswordByAdmin_waadAdminActor_canResetOrdinaryUserPassword` — WAAD_ADMIN → ordinary user → succeeds.
  - `resetPasswordByAdmin_superAdminActor_canResetSuperAdminPassword` — SUPER_ADMIN → SUPER_ADMIN target → succeeds (sanity check that only WAAD_ADMIN is restricted, not everyone).
- `mvn -o compile` — clean (confirms nothing else in the backend referenced the deleted files).
- `mvn -o test -Dtest=UserServiceTest,EffectivePermissionServiceTest,WaadAdminControllerAccessAuthorizationTest,WaadAdminSuperAdminProtectionTest` — **39/39 passing** (16 in `UserServiceTest`, up from 13).
- Full `mvn -o test` — **317 tests** (314 + 3 new), **18 failures + 5 errors** — identical to the pre-existing baseline from Phase 1/2/3A, zero new failures attributable to this cleanup.
- `npx eslint src/services/rbac/users.service.js` — 0 issues.
- `npx vite build` — succeeded (only pre-existing chunk-size advisory warnings).

No controller-level MockMvc test was added for the new endpoint specifically — the class-level `@PreAuthorize` it inherits is already exercised by `WaadAdminControllerAccessAuthorizationTest.nonAdminStillCannotAccessAdminEndpoints` (confirms `MEDICAL_REVIEWER` gets 403 on the `UserController` class as a whole), and the actual security-critical logic (the SUPER_ADMIN-target check) lives in `UserService`, which is what the 3 new service-level tests cover directly.

## 6. Manual/browser results

**Not performed** — no browser-automation tool available in this session, consistent with every prior phase. The frontend build succeeded and the code change is a one-line URL swap in an already-tested call path (`UserEdit.jsx`'s reset-password flow was working against the old endpoint; it now points at a new endpoint with an identical request/response shape — `{ newPassword }` in, `ApiResponse<Void>` out, both controllers returned the same shape). Recommend confirming in a browser-capable session: log in as SUPER_ADMIN or WAAD_ADMIN, open a normal user's edit page, use "Reset Password," confirm success; then attempt it (via WAAD_ADMIN) on a SUPER_ADMIN account and confirm a clear rejection rather than a blank page.

## 7. Files changed

**New:**
- `backend/src/main/java/com/waad/tba/modules/rbac/dto/UserResetPasswordDto.java`
- `docs/rbac/RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1-REPORT.md`

**Modified:**
- `backend/src/main/java/com/waad/tba/modules/rbac/service/UserService.java` (new `resetPasswordByAdmin` method)
- `backend/src/main/java/com/waad/tba/modules/rbac/controller/UserController.java` (new endpoint)
- `backend/src/test/java/com/waad/tba/modules/rbac/service/UserServiceTest.java` (3 new tests)
- `frontend/src/services/rbac/users.service.js` (repointed URL)

**Deleted:**
- `backend/src/main/java/com/waad/tba/modules/systemadmin/controller/UserManagementController.java`
- `backend/src/main/java/com/waad/tba/modules/systemadmin/service/UserManagementService.java`
- `backend/src/main/java/com/waad/tba/modules/systemadmin/dto/UserViewDto.java`
- `backend/src/main/java/com/waad/tba/modules/systemadmin/dto/UserCreateDto.java`
- `backend/src/main/java/com/waad/tba/modules/systemadmin/dto/UserUpdateDto.java`

## 8. No-push confirmation

**Not committed. Not pushed.** All changes exist only in the local working tree on `main`, awaiting review per the ticket's work-mode instructions.

---

## Final status

**RBAC-LEGACY-USER-MANAGEMENT-CONTROLLER-CLEANUP-1 — READY FOR REVIEW**
