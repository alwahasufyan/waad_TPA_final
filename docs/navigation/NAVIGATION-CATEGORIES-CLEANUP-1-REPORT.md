# NAVIGATION-CATEGORIES-CLEANUP-1 — Minimal Cleanup of System Categories, Sidebar Duplicates, and Hidden Settings Modules

**Status: READY FOR REVIEW — partially committed.** `frontend/src/menu-items/components.jsx` is committed locally (commit `3c62e2d`, message `fix(navigation): consolidate maintenance menu entries`). `dashboardCategories.js` and `SystemSettingsPage.jsx` remain uncommitted working-tree changes — see §8 for why. Nothing pushed.

## 1. What was changed

Three files, minimal, additive-safe edits — no routes, no components, no backend touched:

### `frontend/src/menu-items/components.jsx`
- Removed the duplicate `kinship-mismatch` node from `group-system-settings` (same URL `/settings/kinship-mismatch` as `maintenance-kinship` under `group-maintenance`, which is kept).
- Removed the duplicate `member-duplicates` node from `group-system-settings` (same URL `/settings/member-duplicates` as `maintenance-duplicates` under `group-maintenance`, which is kept; the two had titles differing by a single character — "دمج السجلات **المتكررة**" vs "**المكررة**" — confirming this was an accidental near-duplicate, not two features).
- Added a comment on the `documents-library` node's `resource: '__hidden_documents'` explaining that this hides Documents from every role including SUPER_ADMIN (the `__hidden_` check runs before the `'*'` wildcard check in `filterMenuItemsByRole`), and that it is being kept hidden intentionally pending a product decision — not touched further, per this ticket's explicit "do not expose Documents automatically" instruction.
- No change to `maintenance-kinship`, `maintenance-duplicates`, or any of the other 3 maintenance-tool entries (`maintenance-backups`, `maintenance-monitoring`, `maintenance-errors`) — all 5 remain under "أدوات الصيانة" (`group-maintenance`) only, as required.

### `frontend/src/config/dashboardCategories.js`
- Removed the dead `superAdminOnly: true` flag from the `maintenance` category group entry (it was never read by `resolveAccessibleModules()` or anywhere else — confirmed by a repo-wide grep before removal, zero other references).
- Added a comment in its place documenting that visibility for this group's tiles is enforced by the real, single mechanism: each tile's `menuId` resolves to a sidebar node with `resource: 'system_settings'`, filtered through `ROLE_RESOURCE_ACCESS`. No second RBAC mechanism was introduced, per the ticket's explicit instruction.

### `frontend/src/pages/settings/SystemSettingsPage.jsx`
- Added explanatory comments directly above the `TabPanel index={99}` (Financial Rule Engine) and `TabPanel index={100}` (SLA / backdated-claims / beneficiary-number-format) blocks, documenting that they are intentionally unreachable today (the visible `<Tabs>` only ever renders indices 0–7; no code path — not the `onChange` handler, not the `?maintenanceTab=` initializer — ever sets `tabValue` to 99 or 100) and that this is by product decision, not a bug to silently fix or delete.
- **No deletion.** Both `TabPanel`s, their imports (`FinancialRuleEngineTab`), and all their form fields are unchanged and still compile.

## 2. What was intentionally NOT deleted

Per the explicit product decision, none of the following were touched, removed, or had their imports stripped:

- **Financial Rule Engine** (`FinancialRuleEngineTab`, `TabPanel index={99}`) — kept, archived/hidden, commented.
- **AI settings** (`AIKeySettingsPage.jsx`) — file untouched; it was already unwired from `SystemSettingsPage.jsx`'s visible tabs in a prior, unrelated, pre-existing uncommitted change in this working tree (confirmed via `git diff` — the `AIKeySettingsPage` import and its `<Tab>`/`<TabPanel>` were already absent before this ticket started). This ticket added no further changes to that file or its wiring.
- **Email settings** (`EmailSettingsPage.jsx`, `EmailSettingsTab.jsx`) — same as AI settings: already unwired from `SystemSettingsPage.jsx` by prior, pre-existing uncommitted work (the `emailSettings` state, the `/admin/settings/email` calls, and the `EmailSettingsTab` import/usage were already removed before this ticket touched the file). Both files still exist on disk, untouched by this ticket.
- **Operational / SLA / beneficiary-numbering settings** (`TabPanel index={100}`) — kept, archived/hidden, commented, exactly like the Financial Rule Engine panel.
- **`/settings` landing page** (`pages/settings/index.jsx`) — not modified. No behavioral change. Documented below (§6) as a separate future cleanup/RBAC item, per the ticket's instruction.
- **`sections/tools/system-settings/*`** (6 orphaned tab components found in the audit) — out of scope for this ticket (not mentioned in its instructions), left untouched.

## 3. Duplicate entries removed

| Removed node | Kept node (same destination) | URL |
|---|---|---|
| `kinship-mismatch` (in `group-system-settings`) | `maintenance-kinship` (in `group-maintenance`) | `/settings/kinship-mismatch` |
| `member-duplicates` (in `group-system-settings`) | `maintenance-duplicates` (in `group-maintenance`) | `/settings/member-duplicates` |

Both destinations, and every route behind them, are unchanged — only the redundant second sidebar registration was removed. A user who previously saw the tool twice (once under "أدوات الصيانة" and once under "إعدادات النظام") now sees it once.

## 4. Confirmation: financial / email / AI / operational modules preserved

Confirmed by direct `git diff` inspection of `SystemSettingsPage.jsx`: the `FinancialRuleEngineTab` import and its `TabPanel` are present, unchanged, and still compile (`npx vite build` succeeded — see §5). No file under `frontend/src/pages/settings/` was deleted; a directory listing before and after this ticket's edits shows the same 13 files (`AIKeySettingsPage.jsx`, `EmailSettingsPage.jsx`, `EmailSettingsTab.jsx`, `FinancialRuleEngineTab.jsx`, `BackupSettingsTab.jsx`, `MonitoringSettingsTab.jsx`, `SystemErrorLogTab.jsx`, etc.) untouched.

## 5. Validation results

```
git status --short   # ran — see below
git diff --check     # ran — only pre-existing CRLF/LF line-ending advisories, no conflict markers/whitespace errors
git diff --stat       # ran — see below
npx vite build        # ran from frontend/ — succeeded, exit 0
npx eslint src/menu-items/components.jsx src/config/dashboardCategories.js src/pages/settings/SystemSettingsPage.jsx
                       # ran — 0 errors, 53 warnings (all pre-existing prettier/formatting and
                       # no-unused-vars warnings already present before this ticket's edits —
                       # none introduced by the 3 comment/removal edits made here)
```

**Important note on `git diff --stat` output for these 3 files:** all three (`menu-items/components.jsx`, `dashboardCategories.js`, `SystemSettingsPage.jsx`) already had substantial **pre-existing, unrelated, uncommitted changes** in the working tree before this ticket started (confirmed in `NAVIGATION-CATEGORIES-FULL-AUDIT-1-REPORT.md` §12, and re-confirmed here via direct diff inspection — e.g. a prior, unrelated change already consolidated a duplicate "Medical Audit Logs" report menu entry, and already unwired the Email/AI settings tabs from `SystemSettingsPage.jsx`). This ticket's edits are layered cleanly on top of that pre-existing state and were verified line-by-line via `git diff` to contain only the intended duplicate-removal and comment additions described in §1 — but **when this work is eventually committed, patch-level staging (`git add -p`) will be required** to separate this ticket's hunks from the unrelated pre-existing ones in the same files, exactly as flagged in the prior ticket's commit-scope rules.

**Manual validation:** not performed — no browser/screenshot automation tool is available in this environment (standing limitation for this session). The `npx vite build` success confirms the app still compiles and all lazy-loaded routes still resolve; the specific manual role-login checks requested in the ticket (SUPER_ADMIN sees maintenance tools once; MEDICAL_REVIEWER sees none; mobile drawer matches desktop) could not be executed end-to-end in a browser, but are logically guaranteed by the code change itself: the removed nodes were the *only* duplicate registrations found, both remaining copies keep the identical `resource: 'system_settings'` gate already used by the surviving `group-maintenance` entries, and mobile/desktop share the same `menu-items/components.jsx` data source (per `NAVIGATION-CATEGORIES-FULL-AUDIT-1-REPORT.md` §6), so a duplicate removed once is removed for both.

## 6a. Commit-scope note: why only one of the three files was committed

At commit time, patch-level inspection against `HEAD` (not just the working tree) surfaced a real dependency/scope problem, resolved with the user before committing:

- **`menu-items/components.jsx`**: the duplicate-removal edits depend on `group-maintenance` (the sidebar group holding `maintenance-kinship`/`maintenance-duplicates`) existing — but `group-maintenance` itself was not in `HEAD`, only in the pre-existing uncommitted working tree. Since this is a small (7-line), obviously-related, necessary dependency — without it, removing the duplicates would have deleted the only working copies of those two tools from the sidebar — it was included in the commit by explicit user decision. The commit was hand-crafted at the git-object level (`git hash-object` + `git update-index`) to include exactly: the 3 icon imports `group-maintenance` needs, the `group-maintenance` block itself, the `__hidden_documents` comment, and the two duplicate-node removals — and nothing else (confirmed via `git diff --cached` before committing; the file's other pre-existing, unrelated hunks — a `reports-domain-providers` URL correction, a `reports-domain-audit` title/URL consolidation, and a duplicate `medical-audit-logs` node removal — were deliberately left unstaged and remain as uncommitted working-tree changes, unrelated to this ticket).
- **`dashboardCategories.js`**: `HEAD` has an entirely different, older category model (3 groups: `medical_ops`/`network_contracts`/`admin_analysis`; no `maintenance` group; no `superAdminOnly` flag exists at all). This ticket's comment/flag-removal edit only makes sense on top of a large (~194-line), unrelated, pre-existing rewrite that replaced the whole model with the 5-group structure described in §1 of this report. That rewrite is not owned by this ticket. **Per explicit user decision, this file was left uncommitted entirely** — the comment explaining the dead `superAdminOnly` flag remains a working-tree-only change.
- **`SystemSettingsPage.jsx`**: same pattern. In `HEAD`, tabs are indexed 0–11 (12 live tabs, including AI/Email/Financial). The `index={99}`/`index={100}` orphan panels this ticket commented on don't exist without a large (16-hunk), unrelated, pre-existing restructuring that renumbered tabs to 0–7 and unwired the AI/Email tabs. **Per explicit user decision, this file was also left uncommitted entirely.**

**Net effect:** the sidebar duplicate cleanup (§3) is live and committed (`3c62e2d`, `fix(navigation): consolidate maintenance menu entries`). The two archival/documentation comments (§1, `dashboardCategories.js` and `SystemSettingsPage.jsx`) remain as harmless, uncommitted working-tree annotations — they carry no functional change, so there is no urgency, but they should be re-staged and committed together with whatever ticket ends up owning the larger System-Categories-model rewrite and the Settings-tab restructuring, since that is the commit they actually belong to.

## 6. Follow-up RBAC ticket required

Confirmed and unchanged by this cleanup: route-level RBAC (`PermissionGuard`/`RoleGuard` not enforcing `resource`/`action` from `MainRoutes.jsx`) remains a real, separate issue, not touched by this ticket. It is being handled as its own audit/design ticket, **`RBAC-ROUTE-GUARD-HARDENING-1`** (see `docs/rbac/RBAC-ROUTE-GUARD-HARDENING-1-REPORT.md`).

The `/settings` landing page's separate, third RBAC mechanism (`pages/settings/index.jsx`'s local `hasRole` helper) is also left as-is, to be addressed together with the RBAC hardening ticket rather than piecemeal here.

## 7. No-push confirmation

Nothing was pushed. `frontend/src/menu-items/components.jsx` is committed locally only (commit `3c62e2d`). `dashboardCategories.js` and `SystemSettingsPage.jsx` remain uncommitted in the local working tree, deliberately, per §6a.

---

**NAVIGATION-CATEGORIES-CLEANUP-1 READY FOR REVIEW**
