# CODEX-WIP-HANDOFF-REPORT-1

## Status

**CODEX-WIP-HANDOFF-REPORT-1 READY FOR CLAUDE**

This is an audit-only handoff. No source code was changed by this ticket, and no files were staged, committed, pushed, merged, or reset.

## Repository snapshot

- Branch: `medical-dictionary-remediation`
- Latest observed commit: `60a6c7b fix(rbac): protect maintenance and preauth email endpoints`
- Working tree: dirty, with multiple independent WIP clusters and generated/untracked artifacts.
- The working tree must not be treated as one release-ready commit.

## Build verification

| Command | Result | Interpretation |
|---|---|---|
| `backend: mvn -o compile` | **Blocked** | Maven offline cache does not contain `org.springframework.boot:spring-boot-starter-parent:3.5.11`; no source compilation result was possible. This is an environment/dependency-cache blocker, not a confirmed Java compile error. |
| `frontend: npx vite build` | **Passed** | Vite transformed 17,804 modules and produced the production bundle. It emitted only the existing large-chunk warning. |

## WIP clusters

### 1. Reports Engine v2

Relevant changes are in `frontend/src/reporting/reportRegistry.js`, `frontend/src/routes/MainRoutes.jsx`, and `frontend/src/menu-items/components.jsx`.

The provider and medical-audit vertical slices were moved to domain routes (`/reports/domain/providers/report` and `/reports/domain/audit/report`), registered as `REP-PRV-001` and `REP-AUD-001`, and guarded by their report resources. The old flat provider route and duplicate medical-audit navigation entry were removed. Frontend build passes. Still required: route smoke tests for allowed/denied roles and confirmation that all report links resolve to the registry route.

### 2. System Categories redesign

`frontend/src/config/dashboardCategories.js` and `frontend/src/components/dashboard/SystemCategoriesDialog.jsx` now define five curated groups: records, claims, reports, settings, and maintenance. Visibility remains based on the RBAC-filtered menu tree; hard-coded role visibility was removed. The model adds claims review/batches, preauthorizations, visits, price lists, and maintenance entries.

Risk: several cards use a shared menu anchor or explicit destination. A visual/RBAC review is required to confirm that every destination is both reachable and appropriate for each role. This cluster should be committed separately from the classification work.

### 3. Settings module reorganization

`frontend/src/pages/settings/SystemSettingsPage.jsx` hides the backup, monitoring, and error-log tabs from the visible tab strip while supporting query-based maintenance entry points. Financial rules and operational settings were moved to unreachable indices 99/100. Email and AI settings were removed from this page’s active load/save flow, but the feature components still exist in the repository.

This is a product decision with functional impact, not only navigation cleanup. Before release, confirm whether email/AI are intentionally owned by other modules and add route-level tests for the maintenance entry points. Do not describe the hidden panels as deleted.

### 4. Provider price-list/classification fixes

Relevant files include `frontend/src/pages/classification/review/index.jsx`, `frontend/src/services/api/classification.service.js`, the contract price-list dialogs/tab, and the classification-engine knowledge files.

Observed fixes:

- UI labels `TRUSTED` and `UNRESOLVED` are translated to API enum values `PENDING_BULK` and `NEEDS_REVIEW`, preventing the 400 errors caused by sending UI decision labels to Spring.
- A duplicate queue tab calls the duplicate review endpoint.
- Approving a needs-review item moves the user to the approved view and prevents approved/rejected rows from showing the approval action.
- Concurrent decisions are guarded by the `saving` flag.
- Adding a contract service now sends `medicalCategoryId`, `basePrice`, `contractPrice`, `currency`, and `notes`; the add button is enabled when a contract exists rather than only when a currently active version exists.
- `official_taxonomy.json` was added because its absence caused the classification engine to fail in the container.
- `odoo_knowledge.json` was regenerated from Odoo `product.product` data; `build_product_kb.py` is the generator and `odoo_knowledge.legacy.json` is a local backup.

The frontend build passes, but this cluster is **not release-ready**: the backend build was not compiled, the generated knowledge base needs semantic sampling/precision review, and the duplicate/approve/publish-to-contract flow needs an authenticated end-to-end test. Do not commit `__pycache__` or the legacy backup. Keep the generated KB and taxonomy file only after reviewing their intended repository status.

### 5. Visit provider isolation and documents

Backend changes add reviewer provider filtering to list, search, detail, and attachment authorization; provider mutation is additionally checked in the service layer. `PUT` and `DELETE` are restricted to `PROVIDER_STAFF`, and the provider ID cannot be changed during an update. Frontend changes hide visit edit/delete controls from non-provider staff and normalize provider/member/service display fields.

This is security-sensitive. Required before release: backend tests for reviewer allowed-provider and denied-provider access, provider staff same-provider mutation, cross-provider mutation denial, document download/upload denial, and super-admin behavior. The compatibility constructor in `VisitAttachmentController` leaves isolation dependencies null for focused legacy tests; verify this does not weaken the production Spring constructor path.

### 6. Dead horizontal navigation cleanup

`HorizontalNavigation.jsx` now returns no displayed groups and retains the old builder in a commented block; `SidebarLayout/index.jsx` removes the desktop horizontal group rendering. This aligns with System Categories being the single launcher, but the commented implementation is technical debt. Frontend build passes. Verify all required destinations remain reachable through the sidebar/System Categories dialog before committing.

### 7. `waad.ps1`

The script now uses a Windows PowerShell 5.1-compatible random-byte API, escapes literal dollar signs when editing environment files, and removes an unused service-list call. This is a small, separable developer/runtime compatibility change. Run the script’s health/start path on the target Windows host and commit separately from application changes.

### 8. Uncommitted documents and test/generated artifacts

The working tree contains untracked reports under benefits, claims, medical-dictionary, navigation, preauthorization, RBAC, release, and visits, plus the untracked `DoctorNotesBenefitCapsRegressionTest.java`. It also contains generated Python `__pycache__` files, the classification KB generator, the legacy KB backup, and the official taxonomy JSON.

These must be triaged individually. Reports should be committed only if they are intended project history; tests should be reviewed and run; generated caches must not be committed. The report created by this ticket is itself intentionally uncommitted.

## Recommended Claude handoff order

1. Restore/prepare Maven dependencies and run backend compile/tests; do not infer backend correctness from the successful frontend build.
2. Split the current worktree into isolated commits: security/visits, classification/provider pricing, navigation/categories/settings, reports, and `waad.ps1`.
3. For classification, run an authenticated matrix: import, trusted/unresolved/duplicate filters, approve, reject, edit, publish to contract, add a new contract service, and reload persistence. Confirm approved rows cannot be approved again and appear only in approved/trusted views as designed.
4. Review Odoo-derived mappings with precision/recall samples and retain only validated mappings; preserve manual-review fallback for ambiguous services.
5. Run role-based smoke tests for report routes, System Categories, settings maintenance routes, reviewer visit isolation, provider-staff mutation, and attachment access.
6. Re-run both builds and only then stage/commit/push the selected clusters.

## Explicit non-completion items

- Backend compile was not completed because the offline Maven parent was unavailable.
- No browser/API end-to-end acceptance was performed by this audit ticket.
- No security or classification precision claim is made from the frontend build alone.
- No cleanup, deletion, staging, commit, push, or merge was performed.
