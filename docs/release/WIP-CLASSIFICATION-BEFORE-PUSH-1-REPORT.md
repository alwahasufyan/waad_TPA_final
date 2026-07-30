# WIP-CLASSIFICATION-BEFORE-PUSH-1 — Classify Remaining Codex Report and Price-List Changes Before Push

**Audit only. No code was modified. Nothing staged. Nothing committed. Nothing pushed.**

## 1. Current branch

`medical-dictionary-remediation`

Other worktrees present in this checkout (not touched, listed for completeness): `D:/waad_dashboard_clean` (`integration/dashboard-light-clean`), `D:/waad_phase1a_clean` (`integration/phase-1a-benefit-rules-import`), `.worktrees/med-dict-recovery-2`, `.worktrees/medical-dictionary-v1-stack`.

## 2. Latest intentional commits (this session)

```
fdc5b86 feat(preauth): wire provider submitted review workflow
dc3597e docs(rbac): record route guard commit notes
bbbcba8 fix(rbac): enforce route-level resource guards
3c62e2d fix(navigation): consolidate maintenance menu entries
69d80d5 fix(provider-contracts): block unresolved pricing items from claims
99451da Merge pull request #13 from alwahasufyan/recovery/provider-portal-claim-submission
375506e docs: add remaining ticket reports for already-committed work
f9e15b8 fix(backend): surface real validation errors, serialize timestamps as UTC
ed2a0a3 refactor(nav): remove duplicated horizontal nav, move audit logs under Reports
22a944f fix(provider): block pre-auth-required services at selection time
```
All five of my own tickets this session (`PROVIDER-PRICE-IMPORT-REVIEW-1`, `NAVIGATION-CATEGORIES-CLEANUP-1`, `RBAC-ROUTE-GUARD-HARDENING-2`, the RBAC docs follow-up, `PREAUTH-REVIEW-WORKFLOW-1`) are cleanly committed. **This report is about everything left over in the working tree that is neither those commits nor something I authored.**

## 3. Executive summary — the one finding that changes the push decision

**The committed `HEAD` state (`fdc5b86`) does not compile on its own.** Verified directly: created a temporary detached worktree from `HEAD` (`git worktree add --detach`, no changes to the real working tree) and ran `mvn -o compile` there — it failed with

```
error: package com.waad.tba.modules.claim.draft.dto does not exist
error: package com.waad.tba.modules.claim.draft.service does not exist
... (ClaimDraftController.java cannot resolve ClaimDraftService/ClaimDraftResponse/ClaimDraftUpsertRequest)
```

Root cause found: `.gitignore` line 71 has a bare rule `draft/` (added under a "sensitive local data" section, clearly intended to ignore some unrelated local scratch/draft folder) which — because it has no path anchor — also matches the real, required backend source package `backend/src/main/java/com/waad/tba/modules/claim/draft/**` (`ClaimDraft.java`, `ClaimDraftService.java`, `ClaimDraftRepository.java`, `ClaimDraftResponse.java`, `ClaimDraftUpsertRequest.java`). These files exist on disk, are referenced by the already-committed `ClaimDraftController.java`, but have **never been tracked by git at all** — confirmed via `git ls-files`/`git ls-tree HEAD` (empty) and `git check-ignore -v` (confirms the match against `.gitignore:71:draft/`).

This is not something introduced by my tickets — `ClaimDraftController.java` and the `.gitignore` rule both predate this session's work. It means **the repository has been in a non-buildable state at `HEAD` for some unknown period**, invisible until someone actually tries a clean checkout, because every local working tree (including this one) already has the untracked `draft/` files sitting on disk and compiles fine locally.

**This blocks any final push decision until it is fixed or explicitly accepted as pre-existing and separately tracked.** See §7.

## 4. Full remaining working-tree inventory, classified

Legend: **A** already-committed (n/a here — this table is only the leftover set) · **B** Codex report work · **C** Codex provider price-list/classification work · **D** Codex/uncommitted settings-navigation rewrite · **E** docs-only report update (mine) · **F** generated/temp/unsafe · **G** unknown/needs decision.

### 4a. Backend — modified, tracked

| File | Status | Class | What the diff does | Safe to push now? | Recommended action |
|---|---|---|---|---|---|
| `backend/.../visit/controller/VisitController.java` | Modified | **G→ visit-isolation feature** | Narrows `PUT`/`DELETE /visits/{id}` from a 4-role list to `PROVIDER_STAFF` only. | Needs review | Commit separately, after review (see §5 cluster "Visit provider-isolation") |
| `backend/.../visit/controller/VisitAttachmentController.java` | Modified | Same cluster | Adds `ReviewerProviderIsolationService`/`AuthorizationService` and a reviewer-isolation check (`DOCTOR`-tagged code as "DOCUMENTS-IDOR-1") before visit-attachment access; adds a backward-compatible constructor for existing tests. | Needs review | Commit separately, after review |
| `backend/.../visit/repository/VisitRepository.java` | Modified | Same cluster | Adds `findAllByProviderIdIn`, `findByProviderIdIn`, `searchByProviderIds`, `searchPagedByProviderIdIn` — provider-scoped query variants, same shape as the `findByStatusInAndReviewerProviders` pattern used in my own `PreAuthorizationRepository` work. | Needs review | Commit separately, after review |
| `backend/.../visit/service/VisitService.java` | Modified | Same cluster | Wires the new repository methods: reviewers get provider-scoped `findAll`/`search`; `update`/`delete` now call a new `assertProviderMutationAccess()` requiring the caller's own provider context to match the visit's provider. | Needs review | Commit separately, after review |
| `backend/.../benefitpolicy/service/BenefitPolicyCoverageServiceTest.java` | Modified | **E (mine, from earlier this session)** | Adds one regression test (`validateAmountLimits_ExactlyExhausted_stillBlocksNewAmount`) from the `DOCTOR-NOTES-BENEFIT-CAPS-REGRESSION-1` ticket, done before tonight's navigation/RBAC/preauth work. | Yes | Commit under its own ticket name if/when approved (was accepted as a report-only ticket; this test was part of that work) |

**Note on `backend/.../preauthorization/controller/PreAuthEmailRequestController.java`**: shown modified in `git status`, but this is **mine** — the `BACKEND-RBAC-FIX-MISSING-AUTH-1` fix (`@PreAuthorize` additions), already fully described and approved in that ticket's report, simply not yet committed (that ticket's commit approval hasn't been given). Not Codex. Listed here for completeness, not a new finding.

Same applies to `KinshipMismatchController.java` and `MemberDuplicateController.java` — both **mine**, same `BACKEND-RBAC-FIX-MISSING-AUTH-1` ticket, awaiting commit approval.

### 4b. Backend — untracked

| File | Status | Class | What it is | Safe to push now? | Recommended action |
|---|---|---|---|---|---|
| `backend/.../modules/medicalclassification.rar` | Untracked | **F — unsafe** | A `.rar` binary archive sitting directly inside a Java module source directory (`backend/src/main/java/com/waad/tba/modules/`). Not a source file, wrong location, no plausible reason to be there. | **No** | Do not add to git; investigate origin and delete from the working tree once confirmed unneeded (out of this audit's scope to delete — flagging only) |
| `backend/.../claim/service/DoctorNotesBenefitCapsRegressionTest.java` | Untracked | **E (mine)** | The 10-test regression suite from `DOCTOR-NOTES-BENEFIT-CAPS-REGRESSION-1`, written earlier this session. | Yes, once that ticket is approved for commit | Commit under its own ticket name if/when approved |
| `backend/.../member/controller/KinshipMismatchControllerAuthorizationTest.java` | Untracked | **E (mine)** | `BACKEND-RBAC-FIX-MISSING-AUTH-1` test file. | Yes, once approved | Commit together with the `BACKEND-RBAC-FIX-MISSING-AUTH-1` controller changes |
| `backend/.../member/controller/MemberDuplicateControllerAuthorizationTest.java` | Untracked | **E (mine)** | Same ticket. | Yes, once approved | Same |
| `backend/.../preauthorization/controller/PreAuthEmailRequestControllerAuthorizationTest.java` | Untracked | **E (mine)** | Same ticket. | Yes, once approved | Same |

### 4c. Frontend — modified, tracked (Reports Engine v2 cluster — Codex)

These three files share one coherent, in-progress feature, identifiable by the `REPORTS-ENGINE-2` tag already visible in code comments and by cross-referencing route/resource names:

| File | Class | What the diff does |
|---|---|---|
| `frontend/src/routes/MainRoutes.jsx` (leftover portion only — my `PREAUTH-REVIEW-WORKFLOW-1` portion is already committed and excluded from this diff) | **C — Reports Engine v2** | Splits the old single `/reports/domain/medical-audit` + separate `/reports/providers` routes into `/reports/domain/providers/report` and `/reports/domain/audit/report`, consolidating both under the unified domain-report URL scheme. |
| `frontend/src/menu-items/components.jsx` (leftover portion only — my two `PREAUTH-REVIEW-WORKFLOW-1` menu entries are already committed and excluded) | Same cluster | Renames the `reports-domain-providers` URL to match the new route, renames `reports-domain-audit`'s title to "سجل التدقيق الطبي", and removes the now-redundant standalone `medical-audit-logs` settings-menu entry (consolidated into the Reports domain entry above). |
| `frontend/src/reporting/reportRegistry.js` | Same cluster | Adds two new registry entries (`REP-PRV-001` Provider Report, `REP-AUD-001` Medical Audit) pointing at the new `/reports/domain/providers/report` / `/reports/domain/audit/report` routes — this is the piece that makes the route rename intentional/complete rather than incidental. |

This cluster reads as **complete and coherent** (route + menu + registry all agree with each other), not half-finished. **Recommendation: commit as its own change once reviewed**, e.g. `feat(reports): consolidate provider and audit reports under unified domain routes` — do not mix into any of my five commits, and do not discard.

### 4d. Frontend — modified, tracked (System Categories redesign cluster — Codex)

| File | Class | What the diff does |
|---|---|---|
| `frontend/src/config/dashboardCategories.js` | **D** | Full rewrite from the old 3-group model (`medical_ops`/`network_contracts`/`admin_analysis`) to a new 5-group model (`records`/`claims`/`reports`/`settings`/`maintenance`), 20 modules, new `resolveAccessibleModules()` helper. |
| `frontend/src/components/dashboard/SystemCategoriesDialog.jsx` | Same cluster | Rewritten to consume `CATEGORY_GROUPS`/`resolveAccessibleModules` from the file above (was previously building tiles directly from the raw sidebar tree). Confirmed coupled — this component does not work with the old `dashboardCategories.js` shape and vice versa. |

**Recommendation**: this pair must be committed **together** (they only make sense as one unit) once reviewed — likely `feat(dashboard): redesign System Categories into five grouped sections`. Do not split across commits.

### 4e. Frontend — modified, tracked (Settings module reorg — Codex, incomplete-looking)

| File | Class | What the diff does |
|---|---|---|
| `frontend/src/pages/settings/SystemSettingsPage.jsx` | **D — needs review, looks incomplete** | Renumbers visible tabs 0–7, unwires the AI Settings and Email Settings tabs entirely (imports/state/API calls removed), leaves two now-orphaned `TabPanel`s at indices 99/100 (Financial Rule Engine, SLA/beneficiary settings) that no `<Tab>` ever activates. I already annotated these two panels with explanatory comments in `NAVIGATION-CATEGORIES-CLEANUP-1` (committed), but the underlying restructuring that created this state is **not mine** and remains uncommitted. |

**Recommendation**: needs manual review before committing — unlike the other two clusters, this one has a visible loose end (two dead panels, two fully-unwired-but-not-deleted features) that suggests it may be mid-refactor rather than finished. Do not push as-is without confirming intent (are AI/Email settings meant to stay removed from the UI, or is this WIP?).

### 4f. Frontend — modified, tracked (Provider price-list / classification cluster — Codex)

| File | Class | What the diff does |
|---|---|---|
| `frontend/src/components/classification/ContractPriceEditDialogs.jsx` | **C** | Fixes the "Add Service" dialog's request payload to match the backend's actual `ProviderContractPricingItemCreateDto` field names (`medicalCategoryId`/`basePrice`/`contractPrice`/`currency`/`notes` instead of the old, wrong `categoryId`/`price`/`reason`) — a real bug fix, not cosmetic. |
| `frontend/src/components/classification/ContractPriceListTab.jsx` | Same cluster | Changes the "Add Service" button's disabled condition from `!active` to `!contractId` — lets the dialog open for any existing contract regardless of active/inactive status. |
| `frontend/src/pages/classification/review/index.jsx` | Same cluster (medical classification review, adjacent) | Fixes a UI-tab-to-backend-`ReviewStatus`-enum mapping bug, adds a "duplicates" review queue tab, adds double-submit protection, and auto-switches tabs after an approval. |
| `frontend/src/services/api/classification.service.js` | Same cluster | Adds the missing `getUnifiedReviewLines()` service method the page above now calls. |

**Recommendation**: this reads as a coherent, plausible bug-fix batch for the provider price-list/classification workspace. **Commit as its own change once reviewed** — e.g. `fix(classification): align contract pricing payload and review-queue status mapping` — do not mix with my navigation/RBAC/preauth commits.

### 4g. Frontend — modified, tracked (dead horizontal-nav / mobile-menu cleanup — Codex)

| File | Class | What the diff does |
|---|---|---|
| `frontend/src/layout/Dashboard/Header/HeaderContent/HorizontalNavigation.jsx` | **D** | Short-circuits `displayGroups` to always return `[]` (with the old logic commented out below), so this component renders nothing. |
| `frontend/src/layout/SidebarLayout/index.jsx` | Same cluster | Removes the JSX block that rendered `displayGroups` as desktop nav buttons — the dead code above no longer has a caller. |

This matches (and appears to extend) the already-committed `ed2a0a3 refactor(nav): remove duplicated horizontal nav, move audit logs under Reports` — likely a continuation/cleanup pass that didn't get included in that commit. **Recommendation**: commit together as a small follow-up, e.g. `refactor(nav): finish disabling the duplicate horizontal navigation bar` — low risk, but not mine to decide alone since it touches the same area as a commit I didn't author.

### 4h. Backend/frontend — other

| File | Status | Class | What it is | Safe to push? | Recommendation |
|---|---|---|---|---|---|
| `waad.ps1` | Modified | **E (mine)** | The `RandomNumberGenerator.Fill()`/`Set-EnvValue` `$`-escaping fixes and dead-code removal from the very first ticket of this session. | Yes, once approved | Commit under its own small ticket, e.g. `fix(scripts): harden waad.ps1 secret generation for Windows PowerShell 5.1` |
| `tools/classification-engine/odoo_knowledge.json` | Modified | **C — generated data, needs review** | A ~69,000-line diff to a generated medical-classification knowledge-base JSON (built by `build_product_kb.py`/`build_odoo_kb.py`). Not hand-written; a regenerated data artifact. | **Needs review** | Confirm this is the *intended*, *current* knowledge base before pushing — a diff this large in a generated data file is easy to accidentally push stale/wrong data. Recommend the price-list/classification ticket owner (Codex or whoever runs the regeneration) confirm freshness, not a blind commit. |

## 5. Untracked docs — reports belonging to tickets I did not run

These are real, substantive reports for tickets with names I have never seen in this session — i.e., **other work (Codex) done in parallel**, not garbage:

| File | Ticket (inferred from filename) |
|---|---|
| `docs/navigation/DASHBOARD-CATEGORIES-NAV-CLEANUP-1-REPORT.md` | `DASHBOARD-CATEGORIES-NAV-CLEANUP-1` — likely documents the System Categories redesign in §4d |
| `docs/visits/VISITS-DOCUMENTS-ADMIN-REVIEWER-FIX-1-REPORT.md` | `VISITS-DOCUMENTS-ADMIN-REVIEWER-FIX-1` — likely documents the Visit provider-isolation cluster in §4a |
| `docs/medical-dictionary/MEDICAL-DICTIONARY-SYSTEM-AUDIT-1-REPORT.md` + 3 `.xlsx` data files | `MEDICAL-DICTIONARY-SYSTEM-AUDIT-1` — likely related to the classification/price-list work in §4f and the `odoo_knowledge.json` regeneration |
| `docs/benefits/BENEFIT-PRICING-GOVERNANCE-NEXT-STEPS-1-REPORT.md` | **Mine** — already-accepted planning report from earlier this session, just never committed |

**Recommendation**: do not read these as "leftover clutter" — they are the paper trail for the Codex clusters identified in §4c–§4g above. Whoever owns those tickets should commit their own reports alongside their own code changes, in their own commit(s) — not bundled into mine, and not discarded.

## 6. Untracked docs — mine, from earlier tickets this session

| File | Status |
|---|---|
| `docs/claims/DOCTOR-NOTES-BENEFIT-CAPS-REGRESSION-1-REPORT.md` | Mine, report-only ticket, never asked to be committed |
| `docs/navigation/NAVIGATION-CATEGORIES-CLEANUP-1-REPORT.md` | Mine — I only committed the code change (`3c62e2d`), not this report itself |
| `docs/navigation/NAVIGATION-CATEGORIES-FULL-AUDIT-1-REPORT.md` | Mine, audit-only ticket |
| `docs/rbac/BACKEND-RBAC-ENDPOINT-AUDIT-1-REPORT.md` | Mine, audit-only ticket |
| `docs/rbac/BACKEND-RBAC-FIX-MISSING-AUTH-1-REPORT.md` | Mine, implementation done, commit not yet approved |
| `docs/rbac/RBAC-ROUTE-GUARD-HARDENING-1-REPORT.md` | Mine, audit-only ticket |
| `docs/preauthorization/PREAUTH-REVIEW-WORKFLOW-AUDIT-1-REPORT.md` | Mine — I committed the *implementation* report (`PREAUTH-REVIEW-WORKFLOW-1-REPORT.md`) but never this earlier audit report |

None of these are unsafe to push (they're documentation only); they're simply not yet committed because no commit was requested for them specifically.

## 7. Files unsafe to push / should never be committed

| File/path | Why |
|---|---|
| `backend/src/main/java/com/waad/tba/modules/medicalclassification.rar` | Binary archive in the wrong location; no reason to be tracked. |
| `tools/classification-engine/__pycache__/` | Python bytecode cache — must never be committed. |
| `"للمرافق معالجة اكسيل  سكربت/"` (entire directory) | Contains a full Python **virtualenv** (`.venv/`), duplicate copies of the same scripts already in `tools/classification-engine/`, and several large `.xlsx`/data files. This looks like a personal scratch working copy accidentally left inside the repo root, not an intentional part of the project. **Should not be added to git under any circumstances** (a committed `.venv` is a common source of huge, broken, platform-specific commits). |
| `tools/classification-engine/*.xlsx`, `odoo_knowledge.legacy.json`, `official_taxonomy.json` | Reference/data files, likely legitimately part of the classification tooling's inputs, but already covered by `.gitignore`'s `*.xlsx`/`*.xls` rule (intentional) — leave untracked, do not force-add. |

**None of the above are currently staged and none will be swept in by a `git add -A`-style push** since nothing has been staged in this session beyond the five already-committed tickets. Flagged here purely so they're never accidentally added later.

## 8. Recommended push strategy

1. **Do not push yet.** The committed `HEAD` (`fdc5b86`) does not compile stand-alone due to the `.gitignore` `draft/` bug (§3) — pushing now would hand a broken build to anyone who does a clean checkout, completely independent of anything in this session's five tickets.
2. **Fix the `.gitignore` bug first, as its own tiny commit**, before anything else: anchor the `draft/` rule (e.g. to whatever specific local folder it was meant to protect, or remove it if that folder no longer exists) and `git add` the real `backend/.../claim/draft/**` source files it was accidentally hiding. This needs explicit approval and is not something I did in this audit-only ticket.
3. **Push only the five already-committed, self-contained tickets** (`69d80d5`, `3c62e2d`, `bbbcba8`, `dc3597e`, `fdc5b86`) once #2 is resolved — they are independently clean, already validated (build+tests passed at each commit), and do not depend on any of the uncommitted Codex clusters.
4. **Do not bundle any of the Codex clusters (§4c–§4g) into this push.** Each is coherent enough to deserve its own reviewed commit, ideally by whoever actually wrote it (Codex/another session), on its own branch or as separate commits after review — not mixed with my tickets, and not discarded. Suggested groupings if/when that review happens:
   - `feat(reports): consolidate provider and audit reports under unified domain routes` (§4c)
   - `feat(dashboard): redesign System Categories into five grouped sections` (§4d)
   - Settings module reorg (§4e) — **needs a product decision first** (is AI/Email settings removal intentional?), not just a commit
   - `fix(classification): align contract pricing payload and review-queue status mapping` (§4f)
   - `refactor(nav): finish disabling the duplicate horizontal navigation bar` (§4g)
   - Visit provider-isolation cluster (§4a) — a real security feature, deserves its own careful review + tests before commit, similar treatment to my own RBAC tickets
5. **My own remaining uncommitted work** (§4a note, §6) — `BACKEND-RBAC-FIX-MISSING-AUTH-1` (implemented, tested, awaiting commit approval), `DOCTOR-NOTES-BENEFIT-CAPS-REGRESSION-1` test, `waad.ps1` fixes, various audit-only reports — can be committed independently at any time once approved; none of it is required for the five-ticket push in #3.

## 9. No-change / no-commit / no-push confirmation

- No source files, configuration, or `.gitignore` were modified during this audit.
- Nothing was staged (`git add` was not run against any of the files discussed above).
- Nothing was committed.
- Nothing was pushed.
- The temporary worktrees created to test buildability (`git worktree add --detach`) were both removed (`git worktree remove --force`) after use; `git worktree list` confirms only the pre-existing worktrees remain. The main working tree was never touched, reset, or stashed.

---

**WIP-CLASSIFICATION-BEFORE-PUSH-1 BLOCKED — committed `HEAD` does not compile without uncommitted files (`.gitignore`'s `draft/` rule accidentally excludes the real `claim/draft` backend package required by the already-committed `ClaimDraftController.java`); fix that first, then re-run this classification's push recommendation (§8) before pushing anything.**
