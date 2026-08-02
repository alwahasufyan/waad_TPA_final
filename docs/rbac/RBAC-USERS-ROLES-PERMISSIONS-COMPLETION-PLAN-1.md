# RBAC-USERS-ROLES-PERMISSIONS-COMPLETION-PLAN-1

Gap analysis for closing out `/admin/users`'s remaining tabs (3 و4) plus a
login/logout history ask, per the explicit request to review the whole
Users/Roles/Permissions area and list what's missing before continuing.

Status: **Item A done. Item B (audit log) starting now. Items C/D planned,
not started.**

---

## A. Roles & Permissions matrix — category names/icons (done)

The matrix's 6 category headers (records/network/claims/system/reports/
finance — the DB-level `permissions.group_name` buckets) had no icons and
used labels that didn't quite match the sidebar's own group titles. Fixed:
- Renamed to read closer to the sidebar sections they map to.
- Added the same icon family used in the sidebar/`SystemCategoriesDialog`
  (`PeopleAltIcon`, `LocalHospitalIcon`, `ReceiptLongIcon`, `SettingsIcon`,
  `AssessmentIcon`, `PaymentIcon`).

Note: the catalog's 6 groups are coarser than the sidebar's ~10+ menu
sections (e.g. `network` spans both "الشبكة الطبية" and "مقدمو الخدمة").
A full 1:1 split into more groups is a bigger catalog change (like the
report-domain granularity work done earlier this session) — flagged here,
not done, since it wasn't explicitly asked for.

---

## B. Permission/role change log (Tab 4 — "سجل تغييرات الصلاحيات")

**Investigation result: the write path is already solid.** Every role-
permission-matrix save (`RolePermissionAdminService.updateRolePermissions`)
already writes a `UserAuditLog` row (`ACTION_ROLE_PERMISSIONS_UPDATED`).
**What's missing is only the read side**: no REST endpoint lists these
entries, and the repository has no pagination.

**Building now:**
1. `UserAuditLogRepository` — add a paginated, filterable query (by action,
   date range, actor).
2. New read-only endpoint, `SUPER_ADMIN`/`WAAD_ADMIN` only, under the
   existing `/api/v1/admin/rbac` base (`RolePermissionAdminController`) or
   a new small controller — `GET /audit-log?action=&from=&to=&page=`.
3. Frontend: replace Tab 4's "قريبًا" placeholder with a real paginated
   table (actor, action, target, timestamp, details) in `index.jsx`.

---

## C. Per-user permission overrides (Tab 3 — "الصلاحيات الخاصة للمستخدمين")

**Investigation result:** the hard part (business rules) is already built
and correct — `EffectivePermissionService.createOverride()` already blocks
overrides on SUPER_ADMIN targets, blocks WAAD_ADMIN actors from touching
critical-security permissions, validates GRANT/REVOKE + a mandatory reason,
and already writes to `UserAuditLog`. It is currently **dead code** — no
controller calls it.

**Missing, not yet started:**
- No "revoke an existing override" operation exists even at the service
  layer (today "revoking" would mean creating a new REVOKE-effect row, not
  updating the original row's `revokedAt`/`revokedBy`) — needs a small
  service addition, not just a controller.
- No list endpoint (a user's current overrides, active + historical).
- No DTOs, no controller, no frontend UI (multi-select permission picker +
  reason field + table, per-user, mirroring the role matrix's UX).

**Estimated size:** medium — similar shape to work already done for the
role matrix and reviewer-provider assignment this session, but is its own
ticket (not started in this pass).

---

## D. Login/logout history

**Investigation result:** this is the most incomplete of the three.
- Login already has its own dedicated table, richer than `UserAuditLog`
  alone: `user_login_attempts` (`UserLoginAttemptRepository`) — captures
  every attempt (success AND failure, with IP/user-agent/failure reason),
  written from `AuthenticationEventListener` on every real authentication
  event. No read API exists for it yet.
- **Logout has zero data captured anywhere.** `AuthController.sessionLogout`
  invalidates the session and returns — it writes nothing. There is no
  `ACTION_LOGOUT` constant, no table, nothing to build a "logout history"
  from without new instrumentation first.

**Missing:**
1. Backend: add `ACTION_LOGOUT` + a write call in `sessionLogout` (small).
2. Backend: paginated read endpoint for `user_login_attempts` (+ logout
   events, likely via the same `UserAuditLog` read endpoint from item B
   once that exists, filtered by action).
3. Frontend: a login/logout history view — could be its own tab or a filter
   within the same "سجل التغييرات" screen from item B, to avoid a 5th tab
   for something that's really "activity history" broadly.

**Not started this pass** — largest of the three, and the UI placement
(own tab vs. a filter on item B's screen) is worth confirming before
building, since it changes the frontend shape.

---

## Suggested order for what's left after this pass

1. **B (audit log)** — building now, in this same turn.
2. **C (per-user overrides)** — next, since its hardest part already
   exists and is correct.
3. **D (login/logout)** — last, needs new backend instrumentation first
   (logout tracking doesn't exist at all today) and a placement decision.
