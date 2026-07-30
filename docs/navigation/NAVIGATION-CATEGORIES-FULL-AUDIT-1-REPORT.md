# NAVIGATION-CATEGORIES-FULL-AUDIT-1 — System Categories / Sidebar / Mobile Menu / Maintenance Tools Full Audit

**Audit only. No source files, migrations, or configuration were modified. Nothing staged, committed, or pushed.**

## 1. Executive summary

The navigation stack (System Categories dialog, desktop sidebar, mobile drawer) is **more consistent than the doctor's notes suspected**: System Categories and the sidebar share one data source with no dead `menuId` references, and — contrary to the "mobile menu is a separate hardcoded legacy list" hypothesis — desktop and mobile render from the **exact same** `menu-items/components.jsx` tree through the same `DrawerContent` component. There is no second mobile-only navigation data source to reconcile.

That said, five real defects were found, ranked by risk:

1. **Route-level RBAC is dead code system-wide** (Critical, but pre-existing and out of scope to fix here — see §11). `PermissionGuard` (internally `RoleGuard`) never reads the `resource`/`action` props scattered across `MainRoutes.jsx`; only `allowedRoles`/`isRouteGuard` do anything. Menu-level visibility is correctly gated by a separate, real mechanism (`filterMenuItemsByRole` + `ROLE_RESOURCE_ACCESS`), so hidden-from-menu items are still reachable by typing the URL directly, with only whatever the page's own internal checks provide (often none, or a third, independent, ad-hoc `hasRole` check as in `pages/settings/index.jsx`).
2. **Two dead `TabPanel`s in `SystemSettingsPage.jsx`** (index `99` "قواعد التغطية المالية"/`FinancialRuleEngineTab" and index `100`, an SLA/beneficiary-numbering panel). The `<Tabs>` component only ever renders 8 real tabs (indices 0–7, confirmed by direct read of the `<Tab>` list at [SystemSettingsPage.jsx:622-629](../../frontend/src/pages/settings/SystemSettingsPage.jsx)); nothing ever sets `tabValue` to 99 or 100 — not the `onChange` handler, not the `?maintenanceTab=` query-param initializer. These two panels' content — including a whole "Financial Rule Engine" tab — is unreachable through the UI.
3. **`resource: '__hidden_documents'` hides "Documents Library" from every role, including SUPER_ADMIN** — a new finding this pass, not previously documented. `filterMenuItemsByRole`'s `__hidden_` check ([menu-items/components.jsx:66](../../frontend/src/menu-items/components.jsx)) runs *before* the `'*'` wildcard check, so the SUPER_ADMIN bypass never applies to this one item. Likely intentional (comment says "Hidden per user request") but worth confirming, since it means literally nobody can reach Documents from the menu today.
4. **Two real sidebar duplicates** — the same destination registered twice under different `id`s: `maintenance-kinship`/`kinship-mismatch` (both → `/settings/kinship-mismatch`) and `maintenance-duplicates`/`member-duplicates` (both → `/settings/member-duplicates`, titles differing by one character: "دمج السجلات **المتكررة**" vs "دمج السجلات **المكررة**").
5. **A 4th, separate, orphaned Settings landing page** at bare `/settings` (`pages/settings/index.jsx`), reachable only from the profile-avatar dropdown (not from System Categories or the sidebar), using its own third independent hardcoded-role RBAC helper (`hasRole` reading `user.roles` directly) — a different mechanism from both `RoleGuard` and `filterMenuItemsByRole`.

None of these are financial/data-integrity bugs — they are UI reachability, redundancy, and access-control-consistency issues. Nothing here requires an emergency fix. **Final status: READY FOR REVIEW** (see §14).

## 2. Current architecture (as it actually is, not as assumed)

- **System Categories** (`frontend/src/config/dashboardCategories.js`): `CATEGORY_GROUPS` (5 groups: السجلات الأساسية, المطالبات والموافقات, التقارير, الإعدادات, أدوات الصيانة [`superAdminOnly: true`]) and `DASHBOARD_MODULES` (20 entries). Each module resolves its visibility and URL via `resolveAccessibleModules()`, which looks up `menuId` in the **already role-filtered** sidebar tree (`findMenuNodeById`) and falls back to an explicit `destination` for the URL when the resolved node's own URL isn't the one wanted (e.g. group nodes). Every `menuId` referenced by the 20 modules resolves to a real node in `menu-items/components.jsx` — **no dead references found**.
- **`group-maintenance`'s `superAdminOnly: true` flag is dead/unenforced** — nothing in `dashboardCategories.js`, `SystemCategoriesDialog.jsx`, or `resolveAccessibleModules()` reads this flag. Actual visibility for the 5 maintenance tiles comes entirely from the real gate: their `menuId`s resolve to sidebar nodes with `resource: 'system_settings'`, so any role with `'system_settings'` in `ROLE_RESOURCE_ACCESS` (today: only SUPER_ADMIN, since no other role lists it) sees them — coincidentally the same practical result as the dead flag, but for the wrong reason. If `system_settings` is ever granted to another role, the flag will not stop the maintenance tiles from appearing.
- **Sidebar / mobile menu single source of truth**: `frontend/src/menu-items/components.jsx` (805 lines, the only file under `menu-items/`) exports `menuItem` (raw tree with `resource`/`action` per node) and `filterMenuItemsByRole(items, role)`, which filters against the static `ROLE_RESOURCE_ACCESS` map in `frontend/src/config/roleAccessMap.js`.
- **Desktop and mobile are NOT separate.** `frontend/src/layout/Dashboard/Drawer/index.jsx` renders one `Drawer` component in `permanent` (desktop) or `temporary` (mobile, below the `md` breakpoint) variant — both pass the **same** `DrawerContent`, which imports `menu-items/components` directly. There is no mobile-only menu data file. `frontend/src/layout/Dashboard/Header/HeaderContent/HorizontalNavigation.jsx` does have its own independent duplicate-list-building logic, but it is dead code: its `displayGroups` memo is short-circuited to always return `[]` (confirmed by an existing in-code comment disabling it), so it renders nothing and cannot cause a mobile/desktop mismatch.
- **RBAC is enforced in three independent places, only one of which is real for routes:**
  1. `filterMenuItemsByRole` + `ROLE_RESOURCE_ACCESS` (`config/roleAccessMap.js`) — real, drives what appears in the sidebar/System Categories.
  2. `PermissionGuard.jsx` (component name `RoleGuard`) — only reads `allowedRoles: string[]` and `isRouteGuard: boolean`. Confirmed by direct read ([PermissionGuard.jsx](../../frontend/src/components/PermissionGuard.jsx)): the props `resource`/`action` that `MainRoutes.jsx` passes throughout are never destructured or referenced anywhere in the component. Since no route in `MainRoutes.jsx` passes `allowedRoles`, `RoleGuard` in practice only ever does its `isRouteGuard` → "redirect if logged out" job; it does not restrict routes by role at all today. Direct URL navigation is not blocked by role for any authenticated user.
  3. `pages/settings/index.jsx`'s own local `hasRole(roles)` helper reading `user.roles` — a third, independent, page-local mechanism, only protecting what's rendered inside that one page.
- **`ROLE_RESOURCE_ACCESS`** (`frontend/src/config/roleAccessMap.js`, read in full this pass): `SUPER_ADMIN: ['*']`; `MEDICAL_REVIEWER`: claims, pre_auth, approvals_dashboard, documents, report_center + 4 report-domain resources; `ACCOUNTANT`: settlements, provider_accounts, documents, report_center + 3 report-domain resources; `PROVIDER_STAFF`: `['provider_portal']` only; `EMPLOYER_ADMIN`: members, benefit_policies, documents, report_center + 3 report-domain resources; `DATA_ENTRY` and `FINANCE_VIEWER` also defined but not one of this ticket's 4 requested roles.
- **`provider_portal` visibility has a runtime gate independent of role**: even for a role that lists `provider_portal`, `isAllowed()` also requires a `PROVIDER_PORTAL_ENABLED` flag read from `sessionStorage['__sys_config__']` — *unless* the role is literally `PROVIDER_STAFF`, which always sees it. This means SUPER_ADMIN could fail to see the Provider Portal menu group if that runtime flag isn't set, independent of the RBAC map.

## 3. Full navigation inventory (System Categories tiles → sidebar node → resource → per-role visibility)

Visibility computed directly from `ROLE_RESOURCE_ACCESS` (§2), for the ticket's 4 requested roles. "Y*" = visible but requires the `PROVIDER_PORTAL_ENABLED` runtime flag for non-PROVIDER_STAFF roles.

| # | Label (Arabic) | System Categories id | Sidebar `menuId`/leaf | Route | Component exists | Imports OK | Resource | SUPER_ADMIN | REVIEWER (MEDICAL_REVIEWER) | PROVIDER (PROVIDER_STAFF) | EMPLOYER_ADMIN | Dup/Obsolete | Keep/Hide/Remove |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | لوحة التحكم | `dashboard` | `dashboard` | `/dashboard` | Yes | Yes | `dashboard` | Y | N | N | N | — | Keep |
| 2 | المستفيدون | `members` | `group-members`→`members-list` | `/members` | Yes | Yes | `members` | Y | N | N | Y | — | Keep |
| 3 | جهات العمل | `employers` | `group-employers`→`employers-list` | `/employers` | Yes | Yes | `employers` | Y | N | N | N | — | Keep |
| 4 | مقدمو الخدمات | `providers` | `group-providers`→`providers-list` | `/providers` | Yes | Yes | `providers` | Y | N | N | N | — | Keep |
| 5 | العقود | `contracts` | `provider-contracts` | `/provider-contracts` | Yes | Yes | `provider_contracts` | Y | N | N | N | — | Keep |
| 6 | الخدمات الطبية | `medical-services` | `medical-categories` | `/medical-categories` | Yes | Yes | `medical_catalog` | Y | N | N | N | — | Keep |
| 7 | المطالبات والموافقات | `claims` | `group-claims-approvals`→`claims-report` | `/reports/claims` | Yes | Yes | `claims` | Y | Y | N | N | — | Keep |
| 8 | مراجعة المطالبات | `claims-review` | `claims-review-inbox` | `/claims/review` | Yes | Yes | `claims` | Y | Y | N | N | — | Keep |
| 9 | دفعات المطالبات | `claims-batches` | `claims-batches` | `/claims/batches` | Yes | Yes | `claims` | Y | Y | N | N | — | Keep |
| 10 | الموافقات المسبقة | `preauth` | `email-preauth-requests` | `/pre-approvals/email-inbox` | Yes | Yes | `pre_auth` | Y | Y | N | N | — | Keep |
| 11 | الزيارات | `visits` | resolves via `group-claims-approvals` | `/visits` | Yes | Yes | `claims` | Y | Y | N | N | — | Keep |
| 12 | مركز التقارير | `reports` | `group-reports-center` | `/reports` | Yes | Yes | `report_center` | Y | Y | N | Y | — | Keep |
| 13 | وثائق المنافع | `benefit-policies` | `benefit-policies` | `/benefit-policies` | Yes | Yes | `benefit_policies` | Y | N | N | Y | — | Keep |
| 14 | المستخدمون والصلاحيات | `users` | `users-management` | `/admin/users` | Yes | Yes | `users` | Y | N | N | N | — | Keep |
| 15 | إعدادات النظام | `settings` | `system-configuration` | `/settings/system` | Yes | Yes | `system_settings` | Y | N | N | N | — | Keep |
| 16 | قوائم الأسعار | `price-lists` | `classification-imports` | `/classification/imports` | Yes | Yes | `medical_catalog` | Y | N | N | N | — | Keep |
| 17 | تصحيح بيانات المستفيدين (tile) | `maintenance-kinship` | `maintenance-kinship` | `/settings/kinship-mismatch` | Yes | Yes | `system_settings` | Y | N | N | N | **Duplicate of #22** | See §7 |
| 18 | دمج السجلات المتكررة (tile) | `maintenance-duplicates` | `maintenance-duplicates` | `/settings/member-duplicates` | Yes | Yes | `system_settings` | Y | N | N | N | **Duplicate of #23** | See §7 |
| 19 | النسخ الاحتياطي والاستعادة | `maintenance-backups` | `maintenance-backups` | `/settings/system?maintenanceTab=backup` | Yes | Yes | `system_settings` | Y | N | N | N | — | Keep |
| 20 | التنبيهات والمراقبة | `maintenance-monitoring` | `maintenance-monitoring` | `/settings/system?maintenanceTab=monitoring` | Yes | Yes | `system_settings` | Y | N | N | N | — | Keep |
| 21 | سجل أخطاء النظام | `maintenance-errors` | `maintenance-errors` | `/settings/system?maintenanceTab=errors` | Yes | Yes | `system_settings` | Y | N | N | N | — | Keep |
| 22 | تصحيح بيانات المستفيدين (settings group) | *(not a System Categories tile)* | `kinship-mismatch` | `/settings/kinship-mismatch` | Yes | Yes | `system_settings` | Y | N | N | N | **Duplicate of #17** | Remove one |
| 23 | دمج السجلات المكررة (settings group) | *(not a System Categories tile)* | `member-duplicates` | `/settings/member-duplicates` | Yes | Yes | `system_settings` | Y | N | N | N | **Duplicate of #18** | Remove one |
| 24 | التسويات المالية (3 items) | *(not a System Categories tile)* | `group-settlement` | `/settlement/*` | Yes | Yes | `settlements`/`provider_accounts` | Y | N | N | N | — | Keep (not surfaced as a tile — a real gap, see §8) |
| 25 | بوابة مقدم الخدمة (7 items) | *(not a System Categories tile)* | `group-provider-portal` | `/provider/*` | Yes | Yes | `provider_portal` | Y* | N | Y | N | — | Keep (runtime-flag-gated, see §2) |
| 26 | مكتبة الوثائق | *(not a System Categories tile)* | `documents-library` | `/documents` | Yes | Yes | `__hidden_documents` | **N (hidden from all, incl. SUPER_ADMIN)** | N | N | N | — | Confirm intent — see §1.3 |
| 27 | إدارة التصنيفات | *(not a System Categories tile — overlaps #6)* | `medical-categories` | `/medical-categories` | Yes | Yes | `medical_catalog` | Y | N | N | N | Same node as #6 | Keep |

**Off-menu pages (reachable only by direct URL, not from System Categories, sidebar, or mobile drawer):**

| Page | Route | Component exists | Linked from | Notes |
|---|---|---|---|---|
| Settings landing (`pages/settings/index.jsx`) | `/settings` (exact) | Yes, real route in `MainRoutes.jsx` | Profile-avatar dropdown only | 4th, independent Settings entry point with its own local `hasRole` check (§1.5) |
| Member eligibility check | (routed, see `MainRoutes.jsx:256`) | Yes | Not found in sidebar/System Categories | One of 4 eligibility-related pages found; only the provider one (`/provider/eligibility-check`, item #25's children) is menu-linked |
| Standalone eligibility check page (`pages/eligibility/EligibilityCheckPage.jsx`) | (routed, see `MainRoutes.jsx:727`) | Yes | Not found in sidebar/System Categories | Same overlap as above |
| Email settings (`EmailSettingsPage.jsx`/`EmailSettingsTab.jsx`) | none found | Yes (files exist) | **Zero references anywhere** (`grep` confirmed) | Fully orphaned — dead files, no route at all |
| Facility price preparation (`FacilityPricePreparationPage.jsx`) | routed but menu entry commented out | Yes | Explicitly commented out in `menu-items/components.jsx:776-784` with a note: superseded by "قوائم أسعار المرافق", "route kept until the M3 regression gate passes, then deleted" | Intentional, already flagged for future removal by a prior ticket |
| `sections/tools/system-settings/*` (6 Tab components) | none | Yes (files exist) | **Zero imports anywhere** (`grep` confirmed) | Fully orphaned legacy parallel implementation, superseded by the tabs actually used in `SystemSettingsPage.jsx` |

## 4. Broken pages found

- **`SystemSettingsPage.jsx` `TabPanel index={99}`** ("قواعد التغطية المالية" / `FinancialRuleEngineTab`) — **CATEGORY_BROKEN_ITEM**. Confirmed unreachable: the `<Tabs>` block only renders 8 `<Tab>` elements (indices 0–7, [SystemSettingsPage.jsx:622-629](../../frontend/src/pages/settings/SystemSettingsPage.jsx)), `onChange` can only ever set `tabValue` to one of those, and the `?maintenanceTab=` initializer only maps to 0/5/6/7. Nothing in the file sets 99 or 100.
- **`SystemSettingsPage.jsx` `TabPanel index={100}`** (SLA days / backdated-claims-months / beneficiary-number-format panel) — **CATEGORY_BROKEN_ITEM**, same root cause as above.
- No **BROKEN_ROUTE** or **MISSING_COMPONENT** found anywhere in `MainRoutes.jsx` — every lazy import resolved to a real file (confirmed by the explore pass and spot-verified for the settings/eligibility cluster above); `npx vite build` (§12) succeeded with zero errors, which would have failed on a missing lazy-loaded module.
- No **ORPHAN_ROUTE** in the sense of "route exists, component missing" — the orphans found (`EmailSettingsPage`/`EmailSettingsTab`, `sections/tools/system-settings/*`) are the opposite: real, working components with **no route at all**, just dead files.

## 5. Maintenance tools audit

All 5 maintenance tiles (`maintenance-kinship`, `maintenance-duplicates`, `maintenance-backups`, `maintenance-monitoring`, `maintenance-errors`) resolve to real, working destinations:

- `maintenance-kinship` / `maintenance-duplicates` → dedicated standalone pages (`KinshipMismatchChecker.jsx`, `MemberDuplicatesResolver.jsx`) — confirmed to exist and be routed.
- `maintenance-backups` / `maintenance-monitoring` / `maintenance-errors` → `/settings/system?maintenanceTab=backup|monitoring|errors`, which correctly maps to tab indices 5/6/7 in `SystemSettingsPage.jsx` (confirmed by direct read of the initializer logic and the corresponding `BackupSettingsTab.jsx`/`MonitoringSettingsTab.jsx`/`SystemErrorLogTab.jsx` components at those indices) — **these three ARE reachable**, unlike the two dead 99/100 panels, because they're wired through the query-param initializer.
- The `group-maintenance`'s `superAdminOnly: true` flag is unenforced (§2) — today's practical effect is identical to the real `system_settings`-resource gate (only SUPER_ADMIN has it), but that's coincidental, not a designed safeguard.
- The real duplication is at the **sidebar** level, not the maintenance-tools destinations themselves: `maintenance-kinship`/`kinship-mismatch` and `maintenance-duplicates`/`member-duplicates` are the same two destinations registered twice under `group-maintenance` and `group-system-settings` respectively (§3, §7).

## 6. Mobile menu mismatch audit

**Finding: there is no mismatch, because there is no separate mobile menu.** This directly disproves the doctor's-notes hypothesis that a "MOBILE_LEGACY_ITEM"-style separate hardcoded list exists:

- `layout/Dashboard/Drawer/index.jsx` renders one `Drawer`, switching only the MUI `variant` prop (`permanent` above the `md` breakpoint, `temporary` below it) — both variants render the identical `DrawerContent`.
- `DrawerContent`'s `Navigation` subcomponent imports `menu-items/components` directly — the same file and the same `filterMenuItemsByRole` call used for desktop.
- `HorizontalNavigation.jsx` (in the header, used for a horizontal/mobile-ish nav bar) has its own separate list-flattening logic, but its `displayGroups` `useMemo` is hardcoded to return `[]` — confirmed dead by direct inspection, with an existing code comment noting it was intentionally disabled. It contributes nothing to what a mobile user actually sees.
- **No MOBILE_LEGACY_ITEM tag applies to anything found.**

## 7. Duplicate / obsolete items

| Item | Duplicate of | Root cause | Recommendation |
|---|---|---|---|
| `maintenance-kinship` (in `group-maintenance`) | `kinship-mismatch` (in `group-system-settings`) | Same URL (`/settings/kinship-mismatch`) registered under two different parent groups with two different `id`s and slightly different English titles | Remove one registration (recommend keeping the `group-maintenance` copy, since it's the one exposed as a System Categories tile via `maintenance-kinship`'s `menuId`, and dropping the `group-system-settings` copy which duplicates it without adding a distinct tile) |
| `maintenance-duplicates` (in `group-maintenance`) | `member-duplicates` (in `group-system-settings`) | Same URL (`/settings/member-duplicates`); Arabic titles differ by a single character ("المتكررة" vs "المكررة") — almost certainly an unintentional near-duplicate, not two features | Same as above — remove the `group-system-settings` copy |
| `FacilityPricePreparationPage` route | superseded by `classification-imports` ("قوائم أسعار المرافق") | Already documented in-code as intentionally hidden pending an "M3 regression gate," route kept temporarily | No new action — already tracked; do not touch without checking that gate's status first |
| `EmailSettingsPage.jsx` / `EmailSettingsTab.jsx` | none — just dead | Zero references anywhere, no route | Candidate for deletion in a future cleanup ticket (not this one — "do not delete modules blindly") |
| `sections/tools/system-settings/*` (6 files) | superseded by the tabs actually used in `SystemSettingsPage.jsx` | Zero imports anywhere | Same — candidate for deletion in a future cleanup ticket, not this one |
| `pages/settings/index.jsx` | not a duplicate of an existing page, but functionally redundant with `/settings/system` | A 4th distinct settings entry point, linked only from the profile menu, with its own separate RBAC check | Needs a product decision (merge into System Categories' settings tile, or keep as a distinct "quick links" landing page) — not a code fix on its own |

## 8. Phased minimal implementation plan (not executed in this ticket)

**Phase 0 — decisions needed before any code change** (product/business, not engineering):
- Confirm whether "Documents Library" being hidden from every role (including SUPER_ADMIN) is intentional (§1.3). If not, this is a 1-line fix (change the resource string).
- Confirm whether `pages/settings/index.jsx` should be merged into the System Categories settings flow, kept as-is, or retired (§1.5, §7).
- Confirm the two dead `TabPanel`s (§4) should be wired into the visible `<Tabs>` list, or removed entirely as unfinished/abandoned work.

**Phase 1 — zero-risk cleanup (once approved):**
- Remove the duplicate `kinship-mismatch`/`member-duplicates` entries from `group-system-settings` (§7) — purely additive-safe removal since `maintenance-kinship`/`maintenance-duplicates` already cover the same destinations.
- Remove the dead `superAdminOnly` flag from `dashboardCategories.js`'s maintenance group (cosmetic; document the real gate — `system_settings` resource — in its place) OR wire it in properly if a stricter guarantee is wanted independent of the resource map.

**Phase 2 — requires a decision from Phase 0:**
- Either wire the 99/100 `TabPanel`s into the visible tab list (adds 2 real tabs) or delete their dead code, depending on whether "Financial Rule Engine" and the SLA/beneficiary-numbering panel are meant to ship.
- Fix or confirm the `__hidden_documents` resource string.

**Phase 3 — larger, cross-cutting, explicitly out of scope for a "minimal" plan:**
- Wire real `resource`/`action`-based checks into `PermissionGuard`/`RoleGuard` (or add a second, real guard) so direct-URL access actually enforces the same restrictions as menu visibility — this is a genuine security-hardening item but touches every route in `MainRoutes.jsx` and is a much bigger, separate ticket, not a navigation-cleanup one.
- Delete the confirmed-orphaned files (`EmailSettingsPage.jsx`/`EmailSettingsTab.jsx`, `sections/tools/system-settings/*`) — safe based on this audit's grep, but "do not delete modules blindly" per this ticket's scope; recommend a short confirmation pass (e.g. a build+search re-check) in whatever ticket actually performs the deletion.

## 9. Exact list of files likely to change (future phases only — nothing changed in this ticket)

- `frontend/src/menu-items/components.jsx` — remove/adjust duplicate `kinship-mismatch`/`member-duplicates` nodes; possibly adjust `documents-library`'s resource string.
- `frontend/src/config/dashboardCategories.js` — remove or properly wire the dead `superAdminOnly` flag.
- `frontend/src/pages/settings/SystemSettingsPage.jsx` — either add 2 `<Tab>` entries for indices 99/100 or delete those `TabPanel` blocks and their now-unused imports (`FinancialRuleEngineTab`, the SLA/beneficiary form fields).
- `frontend/src/pages/settings/index.jsx` — depends entirely on the Phase 0 product decision; could range from no change to a full retirement.
- `frontend/src/pages/settings/EmailSettingsPage.jsx`, `EmailSettingsTab.jsx`, `frontend/src/sections/tools/system-settings/*` — deletion candidates in a dedicated cleanup ticket only.
- `frontend/src/components/PermissionGuard.jsx` and `frontend/src/routes/MainRoutes.jsx` — only in the separate, larger Phase 3 RBAC-hardening ticket; not part of a minimal navigation cleanup.

## 10. Risk level per issue

| Issue | Risk if left unfixed |
|---|---|
| Route-level RBAC dead (`PermissionGuard`/`RoleGuard`) | **Medium-high** — a logged-in user of any role can reach any route by URL; mitigated today only by menu-level hiding and (usually) backend authorization on the underlying API calls, which this audit did not re-verify per-endpoint |
| Two dead `TabPanel`s (99/100) | **Low** — unreachable UI, no data-integrity impact, just wasted/confusing code |
| `__hidden_documents` blocking Documents for everyone | **Low-medium** — if unintentional, it's a usability regression (a feature nobody can reach), not a security issue |
| Sidebar duplicates (kinship/duplicates) | **Low** — cosmetic/confusing, not a functional break |
| Orphaned files (`EmailSettings*`, `sections/tools/system-settings/*`) | **Low** — dead code, no runtime impact, just maintenance burden |
| 4th orphaned Settings page with its own RBAC helper | **Low-medium** — a 3rd RBAC mechanism increases the chance of an inconsistent fix landing in only 2 of the 3 places in the future |
| `superAdminOnly` dead flag | **Low** — currently coincides with the real gate; risk is latent (breaks only if `system_settings` is ever granted to a second role without also updating this flag's intended meaning) |

## 11. What was NOT touched (per ticket scope)

- No dashboard redesign.
- No new pages created.
- No modules deleted.
- No backend changes (nothing in `backend/` was read for enforcement changes; the RBAC gaps documented here are entirely a frontend/routing finding).
- No RBAC definitions changed (`ROLE_RESOURCE_ACCESS`, `PermissionGuard`/`RoleGuard` behavior — described, not modified).
- Nothing pushed, nothing committed.

## 12. Validation commands run

```
git status --short         # ran — output below
git diff --stat            # ran — output below
npx vite build (frontend/)  # ran — succeeded, exit 0, in 42.07s
```

`npx vite build` completed cleanly with no errors (only the pre-existing "chunks larger than 1000 kB" advisory warning, unrelated to this audit). This confirms every lazy-loaded route/component referenced in `MainRoutes.jsx` still resolves correctly — no BROKEN_ROUTE/MISSING_COMPONENT was introduced or pre-existing at the build level.

`git status --short` / `git diff --stat` show only **pre-existing, unrelated uncommitted work already in the working tree from prior tickets/sessions** (visit module changes, classification/contract-price UI, `waad.ps1`, the `PROVIDER-PRICE-IMPORT-REVIEW-1-REPORT.md` §7 addition, various `docs/` and `tools/classification-engine/` files) — **none of it was touched or added by this audit ticket**, and no ESLint run was needed since this ticket changed zero source files.

## 13. No-code-change / no-commit / no-push confirmation

- No source files, configuration, or migrations were created or modified during this ticket.
- The only new file is this report (`docs/navigation/NAVIGATION-CATEGORIES-FULL-AUDIT-1-REPORT.md`).
- Nothing was staged, committed, or pushed.

## 14. Final status

No blocking condition was found — the issues above are real but none prevent the current navigation from functioning; they are candidates for future, separately-approved tickets per §8.

**NAVIGATION-CATEGORIES-FULL-AUDIT-1 READY FOR REVIEW**
