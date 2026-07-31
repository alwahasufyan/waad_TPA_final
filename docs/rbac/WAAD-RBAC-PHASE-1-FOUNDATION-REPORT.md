# WAAD-RBAC-PHASE-1-FOUNDATION — Report

**Branch:** `medical-dictionary-remediation`
**Mode:** Audit + implement locally. **Not pushed. Not merged. Not committed** (per ticket instructions — awaiting approval).
**Date:** 2026-07-31

---

## 1. Current RBAC model (before this ticket)

- **Role storage:** `users.user_type`, a plain `VARCHAR(50)` with a `CHECK` constraint — not an enum column, not a separate `roles` table. Single role per user (no multi-role support).
- **Role catalog:** `SystemRole` enum (`backend/src/main/java/com/waad/tba/security/rbac/SystemRole.java`) is the single source of truth: `SUPER_ADMIN, MEDICAL_REVIEWER, ACCOUNTANT, PROVIDER_STAFF, EMPLOYER_ADMIN, DATA_ENTRY, FINANCE_VIEWER`. No `WAAD_ADMIN`.
- **Enforcement:** exclusively `@PreAuthorize("hasRole('...')")` / `hasAnyRole(...)` at the controller layer — 493 annotations across 73 files. No `PermissionEvaluator`, no dynamic permissions table. A prior "Phase 5" simplification deliberately *removed* an earlier dynamic-permissions system in favor of this static, role-only model.
- **Scope:** already implemented, independently of the role model. `users.employer_id` / `users.provider_id` pin `EMPLOYER_ADMIN` / `PROVIDER_STAFF` users to their org; `AuthorizationService` (`backend/src/main/java/com/waad/tba/security/AuthorizationService.java`) resolves and enforces this scope on every relevant query (`resolveEmployerScope`, `resolveProviderScope`, `canAccessMember/Claim/Visit/Provider`, `canModifyClaim`).
- **SUPER_ADMIN protection:** `UserService.delete()` and `UserService.toggleStatus()` already blocked deleting/deactivating a `SUPER_ADMIN` user. **Gap found:** nothing prevented *changing* (demoting) a `SUPER_ADMIN`'s role via `update()` — closed in this ticket.
- **Audit trail:** `UserAuditLog` (table `user_audit_log`) already logs `USER_CREATED/UPDATED/DELETED/ACTIVATED/DEACTIVATED` and had unused `ROLE_CHANGE`/`ROLE_ASSIGNED` constants (no `ROLE_REMOVED` despite the class javadoc claiming it existed — a doc/code drift, now fixed).
- **No `WAAD_ADMIN` role, no permission catalog, no per-user override table** existed anywhere in the codebase or migrations.

## 2. Final role model (this ticket)

`SystemRole` now has **9** values:

| Role | Status |
|---|---|
| `SUPER_ADMIN` | unchanged — full access, cannot be deleted/disabled/demoted by anyone |
| `WAAD_ADMIN` | **new** — operational admin; manages normal users, cannot manage `SUPER_ADMIN` accounts or grant/revoke critical-security permissions |
| `MEDICAL_REVIEWER` | unchanged |
| `ACCOUNTANT` | unchanged |
| `FINANCE_VIEWER` | unchanged (already existed) |
| `PROVIDER_STAFF` | unchanged |
| `EMPLOYER_ADMIN` | unchanged |
| `DATA_ENTRY` | unchanged |
| `BENEFICIARY` | **new, reserved.** Added to the enum and the DB `CHECK` constraint now so a future member-self-service phase doesn't need another migration just to widen the constraint — but it is **not** assignable via `UserCreateDto`/`UserUpdateDto` (regex validation deliberately excludes it), has **no** seeded `role_permissions`, and has no login/route wiring. It is inert until a future ticket picks it up. |

## 3. Scope model

Unchanged from the existing implementation — this was already correctly built and is independent of the role catalog:
- `EMPLOYER_ADMIN` → forced to `user.employerId`
- `PROVIDER_STAFF` → forced to `user.providerId`
- All other roles → unrestricted by scope (role/permission checks still apply)

`WAAD_ADMIN` carries no scope fields (like `SUPER_ADMIN`) — it's an operational role over the whole system, not tied to one employer/provider.

## 4. Permission catalog

New tables (migration `V101__rbac_phase1_foundation.sql`): `permissions`, `role_permissions`, `user_permission_overrides`.

14 permissions across 6 groups (mirrors the frontend's `dashboardCategories.js` `CATEGORY_GROUPS`):

| Group | Permissions |
|---|---|
| records | `beneficiaries.read`, `employers.read` |
| network | `providers.read`, `providers.manage`\*, `contracts.read`, `portal.provider` |
| claims | `claims.read`, `claims.review`\* |
| system | `dashboard.read`, `settings.manage`\* † |
| reports | `reports.medical`, `reports.financial` |
| finance | `settlements.read`, `settlements.approve`\* |

\* = `sensitive`. † = `critical_security` (only `settings.manage` — the one permission `WAAD_ADMIN` cannot grant/revoke via override, per ticket rule 2).

**Seeded role → permission mapping** (`role_permissions`, in the migration): `WAAD_ADMIN` gets all 14 (it needs to operate the system day to day — see rationale in §5); `MEDICAL_REVIEWER` gets 6 (`beneficiaries.read, providers.read, claims.read, claims.review, dashboard.read, reports.medical`); `ACCOUNTANT` gets 7; `FINANCE_VIEWER` gets 3 (read-only); `PROVIDER_STAFF` gets 1 (`portal.provider`); `EMPLOYER_ADMIN` gets 3; `DATA_ENTRY` gets 5. `SUPER_ADMIN` is **not** seeded — `EffectivePermissionService` short-circuits it to "the entire catalog" in code, so it automatically covers any permission added later without a migration. `BENEFICIARY` gets none (reserved).

This mapping is an **initial, approximate catalog** aligned with the existing `frontend/src/config/roleAccessMap.js` resource groupings and `AuthorizationService`'s existing role semantics — it is a read-model for reporting, not yet the enforcement mechanism (see §6 and §9).

## 5. WAAD_ADMIN vs SUPER_ADMIN rules

Enforced in `UserService` (not just documented — see §9 for tests):

1. **SUPER_ADMIN delete/disable** — unchanged, pre-existing, blocked for **any** actor (`delete()`, `toggleStatus()`).
2. **SUPER_ADMIN role-change (demotion) — NEW.** `update()` now blocks changing a `SUPER_ADMIN` user's `userType` to anything else, for **any** actor, not just `WAAD_ADMIN`. Rationale: delete/disable were already closed escalation vectors; silently demoting a `SUPER_ADMIN`'s role via `PUT /users/{id}` was an equally effective one and was open.
3. **WAAD_ADMIN cannot manage SUPER_ADMIN accounts at all — NEW.** `create()` (as SUPER_ADMIN role), `update()`, `delete()`, `toggleStatus()` all reject a `WAAD_ADMIN` actor when the target is (or would become) `SUPER_ADMIN`, throwing `AccessDeniedException`.
4. **WAAD_ADMIN cannot assign SUPER_ADMIN to anyone — NEW.** Covered by the same checks in `create()`/`update()`.
5. **WAAD_ADMIN cannot grant/revoke critical-security permissions — NEW.** `EffectivePermissionService.createOverride()` rejects a `WAAD_ADMIN` actor attempting to create an override (`GRANT` or `REVOKE`) for any permission flagged `critical_security = true` (currently only `settings.manage`), for any target user. Only `SUPER_ADMIN` may do so.
6. **WAAD_ADMIN cannot delete a peer WAAD_ADMIN — NEW, extra hardening beyond the ticket's literal text.** Added so one `WAAD_ADMIN` can't unilaterally remove another; not required by the ticket but a natural extension of "manages normal users and operational roles" (a peer admin isn't a "normal user").
7. `UserController`'s class-level `@PreAuthorize` widened from `hasRole('SUPER_ADMIN')` to `hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN')` — this is what actually lets `WAAD_ADMIN` reach the user-management endpoints at all; the service-layer checks above are what keep it from reaching `SUPER_ADMIN` accounts through them.

**Design note on why `WAAD_ADMIN` is seeded with all 14 permissions in `role_permissions` rather than 13 (excluding `settings.manage`):** the ticket says `WAAD_ADMIN` "manages normal users and operational roles" — which requires operating system settings day to day. The "cannot manage critical security permissions" rule is about `WAAD_ADMIN` not being able to **grant that power to someone else** via an override (privilege escalation vector), not about `WAAD_ADMIN` losing the ability to do its own job. This is documented inline in the migration and in `EffectivePermissionService`.

## 6. Effective permission calculation

`EffectivePermissionService.getEffectivePermissions(User)`:

```
if user.isSuperAdmin(): return ALL permission codes in the catalog
else:
    base = role_permissions WHERE role = user.userType
    for each active (not revoked, not expired) override on user:
        GRANT  → add permission_code to the set
        REVOKE → remove permission_code from the set
    return the resulting set
```

Exposed at `GET /api/v1/admin/users/{id}/effective-permissions` (SUPER_ADMIN/WAAD_ADMIN only, same controller guard as the rest of user management).

**This is an additive read-model, not a new enforcement layer.** `@PreAuthorize`/`hasRole(...)` remains the actual security boundary for every existing endpoint (ticket rule 9) — nothing here changes what any endpoint currently accepts or rejects, except the two changes called out explicitly in §5 point 7 (UserController) and the DTO validation regex widening to accept `WAAD_ADMIN` as a valid `userType` value.

## 7. DB / API changes

**Migration:** `backend/src/main/resources/db/migration/V101__rbac_phase1_foundation.sql`
- Widens `users.user_type` `CHECK` constraint to add `WAAD_ADMIN`, `BENEFICIARY`.
- Creates `permissions`, `role_permissions`, `user_permission_overrides` (full schema and seed data in the file — see inline comments for the rationale of every design choice).
- **Verified against a genuinely fresh database**, not just the shared dev DB (this branch has been burned before by migrations that only worked because of pre-existing shared-DB state — see the `V100` incident in prior session history): spun up a throwaway `postgres:16` container, ran the full Spring Boot app against it. Result: `Successfully applied 95 migrations to schema "public", now at version v101` and `Started TbaWaadApplication in 28.057 seconds`. Container removed after verification.

**New Java files:**
- `security/rbac/SystemRole.java` — extended (WAAD_ADMIN, BENEFICIARY)
- `modules/rbac/entity/{Permission,RolePermission,RolePermissionId,UserPermissionOverride}.java`
- `modules/rbac/repository/{PermissionRepository,RolePermissionRepository,UserPermissionOverrideRepository}.java`
- `modules/rbac/service/EffectivePermissionService.java`
- `modules/rbac/entity/UserAuditLog.java` — added `ACTION_ROLE_REMOVED`, `ACTION_PERMISSION_OVERRIDE_GRANTED`, `ACTION_PERMISSION_OVERRIDE_REVOKED`

**Modified Java files:**
- `modules/rbac/service/UserService.java` — SUPER_ADMIN role-change protection, WAAD_ADMIN restrictions (§5)
- `modules/rbac/controller/UserController.java` — `@PreAuthorize` widened to include `WAAD_ADMIN`; new `GET /{id}/effective-permissions` endpoint
- `modules/rbac/dto/{UserCreateDto,UserUpdateDto}.java` — `userType` regex now accepts `WAAD_ADMIN`

**Not changed:** any of the other 73 files/493 `@PreAuthorize` annotations across the rest of the codebase, `AuthorizationService`, `CustomUserDetailsService`, `SecurityConfig`, `MethodSecurityConfig`, reports implementation, frontend. Per-endpoint review of whether `WAAD_ADMIN` should also reach claims/settlements/provider/etc. endpoints is explicitly Phase 2/3 work (§10) — this ticket only had to make `WAAD_ADMIN` exist and be safe with respect to `SUPER_ADMIN`, not decide its access to every module.

## 8. Tests / build results

- `mvn -o compile` — **success**, zero new warnings beyond one pre-existing-pattern `serial` warning on the new `RolePermissionId` (consistent with dozens of other DTOs/exceptions in this codebase that also lack `serialVersionUID` — not treated as a defect here).
- New test files:
  - `UserServiceTest.java` — expanded from 2 tests to **13**: pre-existing SUPER_ADMIN delete/deactivate protection (regression-pinned), new SUPER_ADMIN role-change protection, new WAAD_ADMIN restrictions (cannot create/update/delete/toggle a SUPER_ADMIN target, cannot assign/promote to SUPER_ADMIN, cannot delete a peer WAAD_ADMIN), and a sanity check that SUPER_ADMIN itself remains unrestricted.
  - `EffectivePermissionServiceTest.java` — **8** tests: SUPER_ADMIN gets the full catalog, ordinary role gets its base set, GRANT/REVOKE override math, expired-override handling, and the WAAD_ADMIN critical-security override restriction (blocked for `settings.manage`, allowed for a non-critical permission, and blocked outright against a SUPER_ADMIN target).
  - `mvn -o test -Dtest=UserServiceTest,EffectivePermissionServiceTest` — **21/21 passing**, 0 failures, 0 errors.
- Full `mvn -o test` (whole backend suite) — run for final confirmation; any pre-existing, unrelated financial-engine test debt (`CostCalculationServiceTest`, `CoverageEngineServiceTest`, etc. — documented in prior session reports as pre-existing and out of scope) is not attributable to this ticket's changes, which touch only the `rbac` module.

## 9. What reports will need from RBAC later

Not implemented in this ticket (explicitly out of scope), but the foundation now exists for:
- Filtering report visibility by `reports.medical` / `reports.financial` permission instead of the current coarse `roleAccessMap.js` resource list.
- A future `GET /effective-permissions` frontend hook to drive which report tabs render, once the frontend actually consumes this endpoint (currently unconsumed — see §10).
- Per-user report access overrides (e.g., temporarily granting an `EMPLOYER_ADMIN` read access to `reports.financial` for one employer's audit) via `user_permission_overrides` without a role change.

## 10. Remaining RBAC Phase 2/3 work

- **Frontend consumption** (ticket rule 10, "should," not "must," for Phase 1): `frontend/src/config/roleAccessMap.js`, `PermissionGuard.jsx`, `constants/rbac.js`, `utils/roleRoutes.js` all need a `WAAD_ADMIN` entry, and ideally should start consuming `GET /effective-permissions` for menu visibility instead of (or alongside) the static `ROLE_RESOURCE_ACCESS` map. Not touched in this ticket — genuinely separate frontend work, and the ticket's report template treats this as forward-looking, not required now.
- **Per-endpoint `@PreAuthorize` review**: decide, module by module, whether `WAAD_ADMIN` should be added alongside `SUPER_ADMIN` on the other 72 controllers (claims, settlements, providers, etc.) — currently `WAAD_ADMIN` can only reach user management. This is a real, deliberate scoping decision per module, not a mechanical find-replace.
- **CRUD API for the permission catalog / overrides**: `EffectivePermissionService.createOverride()` exists as a service method but has no controller endpoint yet (create/list/revoke overrides, admin UI for managing them). Needed before `user_permission_overrides` is usable by anyone other than a future service caller.
- **Actual `@PreAuthorize` consultation of `role_permissions`/overrides**: right now the permission catalog is a parallel read-model; a future phase could decide whether to migrate some `@PreAuthorize` checks to consult `EffectivePermissionService` instead of `hasRole(...)` directly (a bigger, riskier architectural change — deliberately not attempted here).
- **`BENEFICIARY` role activation**: self-service member portal, login flow, scope model (presumably scoped to their own `memberId`), and its own permission set — a full future phase, not started.
- **Role-hierarchy parity**: the frontend already has `RolePrivilegeLevel`/`canModifyRole` (a numeric hierarchy) that the backend has never mirrored; worth deciding in Phase 2 whether the backend needs an equivalent generalized check beyond the hard-coded SUPER_ADMIN/WAAD_ADMIN rules added here.

## 11. No-push confirmation

**Not pushed. Not merged. Not committed.** All changes exist only in the local working tree on `medical-dictionary-remediation`, awaiting review/approval per the ticket's work-mode instructions.

---

## Final status

**WAAD-RBAC-PHASE-1-FOUNDATION — READY FOR REVIEW**
