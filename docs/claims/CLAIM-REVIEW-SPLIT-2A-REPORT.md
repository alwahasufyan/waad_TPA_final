# CLAIM-REVIEW-SPLIT-2A — Claim Review Workspace Core

**Branch:** `integration/claims-review-complete-local` (local-only, per the new delivery rule —
not pushed)
**Status:** Implemented. **Not committed yet** (awaiting your approval, per workflow).
**Scope:** Frontend structural extraction of the reviewer claim screen only. No backend change,
no calculation change, no line-level persistence (that's CLAIM-REVIEW-SPLIT-2C), no new endpoint.

---

## 1. What changed

The 1,249-line monolith `frontend/src/pages/claims/ClaimViewMedicalReview.jsx` was extracted into
a dedicated workspace with the exact component architecture requested:

```
frontend/src/pages/claims/review/
  ClaimReviewWorkspace.jsx                    (orchestrator: state, effects, handlers)
  components/
    ClaimReviewContextHeader.jsx              (identity, prev/next nav, member/policy/diagnosis)
    ClaimReviewServiceLinesPanel.jsx           (service-line table + local decision buttons)
    ClaimReviewDecisionPanel.jsx               (conditional rejection-reason textarea)
    ClaimReviewFinancialSummary.jsx            (CLAIMS-AMOUNT-LABEL-1-compliant cost summary)
    ClaimReviewAttachmentsViewer.jsx           (read-only wrapper around UnifiedAttachmentViewer)
    ClaimReviewNotesPanel.jsx                  (draft banner + claim conversation, local-only)
    ClaimReviewActionBar.jsx                   (sticky approve/reject/request-info bar)
    SectionCard.jsx, InfoRow.jsx               (small shared internal helpers, extracted as-is)
```

`UnifiedAttachmentViewer` and `MedicalReviewLayout` are reused unmodified, exactly as instructed.

The route `/claims/:id/medical-review` **continues to resolve to the same URL** — only the
component it renders changed, in `frontend/src/routes/MainRoutes.jsx` (import + element swapped
from `ClaimViewMedicalReview` to `ClaimReviewWorkspace`).

## 2. Behavior preserved exactly

Every piece of state, every `useEffect`, every handler, and every call to
`claimsService.getById/approve/reject/returnForInfo/startReview` was carried over verbatim from
the original file into `ClaimReviewWorkspace.jsx` — same dependency arrays, same conditions, same
error messages, same `localStorage` keys (`claim-review-draft-{id}`, `claim-review-chat-{id}`).
No new endpoint is called; no existing endpoint call was altered.

**One pre-existing bug fixed during the extraction, not introduced by it:** `handleRequestInfo`'s
`useCallback` in the original file had two dependency arrays back-to-back (a dead leftover from
an earlier edit — flagged in CLAIM-REVIEW-SPLIT-1's audit). The extracted version has the single,
correct dependency array (`[id, reviewLock, ensureClaimUnderReview, draftStorageKey, navigate,
enqueueSnackbar]`), which is what the second (dead) array in the original already listed — so the
effective behavior is unchanged, only the leftover dead code was dropped.

## 3. Financial labels (CLAIMS-AMOUNT-LABEL-1 compliance)

`ClaimReviewFinancialSummary.jsx` was rebuilt (not just moved) to close a gap the previous
monolith had:

- "إجمالي المطالبة" (was "المبلغ المطالب به") — same field, clearer label.
- "حصة العضو" (was "التحمل") — same field, matches the approved label set exactly.
- The ambiguous line that used to show `selectedApprovedAmount || normalizedClaim.approvedAmount`
  under one label ("المبلغ الموافق عليه" — deliberately left ambiguous in CLAIMS-AMOUNT-LABEL-1
  because it mixed two different concepts, see that report §6) is now **two distinct,
  correctly-labeled states**:
  - Once the claim has an actual persisted `approvedAmount > 0` (a real decision exists): shown as
    **"المعتمد النهائي"**.
  - Before that (still under review, nothing persisted yet): shown as **"إجمالي الخدمات المختارة
    للاعتماد (مؤقت، غير محفوظ بعد)"**, with an explanatory caption that this total is derived from
    the reviewer's own in-progress, unsaved line selections.

No bare "المعتمد" appears anywhere in the new workspace.

## 4. Rule 9 — local-only line decisions and notes clearly labeled as temporary

Per your explicit instruction ("If reviewer line buttons remain local-only, clearly label them as
temporary or remove them until persisted in Phase 4"), rather than removing the line-level
APPROVE/REJECT/CLARIFY buttons (they still feed the composed rejection reason sent to
`/reject` and the running total), `ClaimReviewServiceLinesPanel.jsx` now shows an explicit info
banner: *"قرارات الموافقة/الرفض/الاستيضاح لكل خدمة أدناه مؤقتة ومحلية فقط ولم يتم حفظها على
الخادم بعد — سيتم تفعيل الحفظ الفعلي لكل خدمة في مرحلة لاحقة."* The column header itself is also
marked "(مؤقت)". Similarly, `ClaimReviewNotesPanel.jsx` now carries a warning banner making
explicit that the claim conversation is `localStorage`-only and not yet visible to a second
reviewer or the provider (closed in CLAIM-REVIEW-NOTES-1).

## 5. Dead files deleted (re-confirmed unreachable in this pass, not merely assumed)

Re-verified fresh via `grep` (not reused from the earlier audit) that none of these had any
importer anywhere in the frontend except each other and their own barrel `index.js`:

```
frontend/src/components/medical/ClaimReviewPanel.jsx
frontend/src/components/medical/MedicalInboxLayout.jsx
frontend/src/components/medical/DocumentPreview.jsx
frontend/src/components/medical/DocumentsViewer.jsx
frontend/src/components/medical/index.js          (whole directory — nothing imported the barrel itself either)
frontend/src/components/medical-review/MedicalDecisionPanel.jsx
frontend/src/pages/claims/ClaimViewMedicalReview.jsx   (superseded by the new route target)
```

`frontend/src/components/medical-review/index.js` was **edited, not deleted** — it still exports
`UnifiedAttachmentViewer` and `MedicalReviewLayout` (both actively used), only the
`MedicalDecisionPanel` export line was removed.

One care point: `components/medical/DocumentPreview.jsx` and `DocumentsViewer.jsx` share part of
their name with unrelated, **live** files (`components/tba/documents/DocumentPreview.jsx`,
`components/documents/DocumentPreviewPanel.jsx`) — verified precisely via full-path grep that only
the `medical/` versions were deleted; the `tba/documents/` ones (used by `PreApprovalView.jsx`,
`ProviderDocuments.jsx`) were not touched.

## 6. Rules compliance checklist

1. Route `/claims/:id/medical-review` continues working — confirmed, only the rendered component changed. ✅
2. No provider draft/autosave logic in the workspace — confirmed via grep, zero references to `submitClaim`, provider `saveDraft`, or any `pages/provider/*` import. ✅
3. No provider-entry actions (add service, save draft-and-submit, upload provider attachment) — none exist in the new tree; the "save draft" here is the reviewer's own local notes cache, unchanged from before. ✅
4. CLAIMS-AMOUNT-LABEL-1 labels used exactly — see §3. ✅
5. No bare ambiguous "المعتمد" — confirmed by design in §3. ✅
6. Attachments read-only for reviewer — `ClaimReviewAttachmentsViewer.jsx` passes no upload/delete handler to `UnifiedAttachmentViewer`, identical to the original. ✅
7. Existing approve/reject/request-info endpoints remain, unchanged — see §2. ✅
8. No line-level persistence added — confirmed, deferred to CLAIM-REVIEW-SPLIT-2C. ✅
9. Local-only buttons/notes clearly labeled as temporary — see §4. ✅

## 7. Tests / checks performed

```
npx eslint <10 new/changed files>   → 0 errors, 12 warnings (pre-existing prettier-style only)
npx vite build                       → succeeded in 40.4s, produced a distinct
                                        ClaimReviewWorkspace-*.js chunk (38.74 kB)
git diff --check                     → clean
```

Rebuilt and redeployed the local `waad-local-frontend` container; confirmed via
`docker exec ... ls assets/` that the genuinely-built `ClaimReviewWorkspace-*.js` chunk is served
by the running container (not a stale build).

**Not performed — flagged honestly rather than claimed:** this environment has no browser
automation tool available, so the following checklist items from your task were **not** visually
verified in an actual browser session: opening a submitted claim end-to-end, clicking
start-review/approve/reject/needs-correction and observing the UI respond, confirming RTL layout
and usability at 1366×768. What *was* verified: the production build compiles and bundles
successfully (which would fail on any broken import, JSX syntax error, or missing prop wiring),
lint is clean, and every handler/effect was traced line-by-line against the original file to
confirm identical logic. **Recommend a manual smoke test in a browser before this phase is
considered fully done**, using the already-running local stack (`http://localhost:3001`).

## 8. Files changed

```
Modified:
  frontend/src/routes/MainRoutes.jsx
  frontend/src/components/medical-review/index.js

Deleted:
  frontend/src/pages/claims/ClaimViewMedicalReview.jsx
  frontend/src/components/medical/ClaimReviewPanel.jsx
  frontend/src/components/medical/MedicalInboxLayout.jsx
  frontend/src/components/medical/DocumentPreview.jsx
  frontend/src/components/medical/DocumentsViewer.jsx
  frontend/src/components/medical/index.js
  frontend/src/components/medical-review/MedicalDecisionPanel.jsx

New:
  frontend/src/pages/claims/review/ClaimReviewWorkspace.jsx
  frontend/src/pages/claims/review/components/ClaimReviewContextHeader.jsx
  frontend/src/pages/claims/review/components/ClaimReviewServiceLinesPanel.jsx
  frontend/src/pages/claims/review/components/ClaimReviewDecisionPanel.jsx
  frontend/src/pages/claims/review/components/ClaimReviewFinancialSummary.jsx
  frontend/src/pages/claims/review/components/ClaimReviewAttachmentsViewer.jsx
  frontend/src/pages/claims/review/components/ClaimReviewNotesPanel.jsx
  frontend/src/pages/claims/review/components/ClaimReviewActionBar.jsx
  frontend/src/pages/claims/review/components/SectionCard.jsx
  frontend/src/pages/claims/review/components/InfoRow.jsx
```

No backend file touched. No migration. No screenshots (no browser session available — see §7).

## 9. Known blockers

None for merging this phase's structure. The recommended manual browser smoke test (§7) should
happen before or shortly after this local commit, ideally before CLAIM-REVIEW-SPLIT-2B builds on
top of this workspace.

## 10. Rollback plan

`git revert` of this phase's commit restores `ClaimViewMedicalReview.jsx`, the `components/medical/`
directory, and `MedicalDecisionPanel.jsx` exactly as they were, and reverts the route back to the
old component — a clean single-commit rollback with no data/schema involvement.

## 11. Confirmation no unrelated modules changed

`git status --short` shows exactly the files in §8 — all under `frontend/src/pages/claims/`,
`frontend/src/components/medical*`, and `frontend/src/routes/MainRoutes.jsx`. No backend file, no
PreAuthorization file, no Visit file, no settlement file, no taxonomy/classification file, and no
provider-submission file was touched.

---

## Browser Smoke Test Repair

**Status:** Concrete defensive bug found and fixed; error boundary added as a safety net. **I do
not have a browser automation tool in this environment**, so I could not myself execute the
mandatory manual smoke test (opening the page, clicking through, confirming no console error) —
that still requires your own browser check. Reporting honestly below rather than claiming a
verified fix.

### 1. Root cause of the blank page

**No JavaScript error log, network trace, or reproduction was available to me** — the frontend
container's access logs (checked in full, not just the tail) show **zero requests ever made for
the `ClaimReviewWorkspace-*.js` chunk**, meaning the route was either never actually opened in the
current container's lifetime, or the failure happened before any distinguishing network activity
occurred. I do not have a way to open a browser or read `console` output directly in this
environment.

Given that constraint, I did an exhaustive **static** investigation instead:
- Re-verified every import path, alias (`components/tba`, `components/medical-review`,
  `services/api`, `utils/formatters`), and named export against its actual source file — all
  correct (and already proven resolvable, since `vite build` performs static import analysis and
  would fail on a bad path).
- Re-verified every prop passed from `ClaimReviewWorkspace.jsx` to each child component against
  that child's destructured parameter list — all match.
- Fetched a real claim (`GET /api/v1/claims/401`) and its attachments (`GET
  /api/v1/claims/401/attachments`) directly via the API to confirm the actual data shape the
  workspace would receive in production — both matched what the extraction assumed.
- Confirmed the app already has a page-level error boundary (`PageErrorBoundary`, wrapping
  `<Outlet/>` in `layout/Dashboard/index.jsx`) that should catch a thrown render error and show a
  visible error card, not a blank screen — meaning if the reported blank page really was an
  uncaught JS exception, it should have been visible as an error card, not literally blank. This
  doesn't rule out a crash, but it means "blank" is more likely one of: (a) a stale browser cache
  from before the rebuild, (b) a still-loading state that never resolves, or (c) a genuine crash
  that somehow occurs before/outside that boundary's coverage.

**One concrete defect found and fixed during this pass** (in `ClaimReviewFinancialSummary.jsx`):
`normalizedClaim.claimedAmount` and `normalizedClaim.copayAmount` were accessed **without optional
chaining**, inconsistent with the `normalizedClaim?.approvedAmount` used two lines above them in
the same file. `ClaimReviewWorkspace.jsx` does guard against a null `normalizedClaim` before
rendering any children on the *initial* render, but if `claim` state were ever reset to `null`
after a later re-render (e.g. a failed refetch), this component would throw
`Cannot read properties of null (reading 'claimedAmount')` on that subsequent render — a real gap,
not present in the very first paint but a genuine latent crash risk matching your debugging
instruction to check "no child component assumes non-null claim before data loads." This is the
best concrete, evidence-based candidate I could locate for a rendering crash in this component
tree.

### 2. Exact error from browser console

**None available** — no browser console access in this environment. This section cannot be filled
in until you (or a session with browser access) reproduces the issue and shares the exact error
text and stack trace.

### 3. Files changed to fix it

```
frontend/src/pages/claims/review/ClaimReviewWorkspace.jsx               (wrapped in PageErrorBoundary)
frontend/src/pages/claims/review/components/ClaimReviewFinancialSummary.jsx  (null-safe property access)
frontend/src/pages/claims/review/components/ClaimReviewContextHeader.jsx    (early-return guard if normalizedClaim is falsy)
frontend/src/pages/claims/review/components/ClaimReviewServiceLinesPanel.jsx (Array.isArray guard on services)
frontend/src/pages/claims/review/components/ClaimReviewAttachmentsViewer.jsx (Array.isArray guard on attachments)
frontend/src/pages/claims/review/components/ClaimReviewNotesPanel.jsx        (Array.isArray guard on chatMessages)
```

### 4. Before/after behavior

Before: `ClaimReviewFinancialSummary` would throw if `normalizedClaim` were falsy on a re-render;
no error boundary was specific to this page (only the generic Dashboard-level one); several child
components assumed array/object shapes without a defensive fallback.

After: every child component tolerates a missing/null claim, a non-array `services`/`attachments`/
`chatMessages`, and a missing/zero money field without throwing. The workspace is additionally
wrapped in its own `PageErrorBoundary` (reusing the app's existing mechanism, same one used by the
Dashboard layout) with `pageName="مراجعة المطالبة"` — if anything still throws, the user will now
see a labeled error card with a retry button and an error ID (logged to `sessionStorage`) instead
of a blank screen, which will make any *remaining* issue immediately diagnosable from the error ID
alone.

### 5. Manual smoke test result

**Not performed by me — no browser tool available.** Rebuilt and redeployed the local
`waad-local-frontend` container with these fixes (confirmed via `docker exec ... ls assets/` that
a freshly-hashed `ClaimReviewWorkspace-CdEPj1Mq.js` chunk is being served, not a stale build).
Server-side checks re-run clean: `npx eslint` (0 errors), `npx vite build` (succeeded). **Please
retest in your browser** at `http://localhost:3001/claims/401/medical-review` (or any real
submitted claim ID) and report back either "it works" or the exact error card / console message
now shown.

### 6. Whether any deleted file had to be restored

**No.** Re-confirmed all six deletions from CLAIM-REVIEW-SPLIT-2A are still correctly unreferenced
(`components/medical/*`, `components/medical-review/MedicalDecisionPanel.jsx`,
`pages/claims/ClaimViewMedicalReview.jsx`) — none of them were the cause, and none needed restoring.

### 7. Confirmation no backend changes

Confirmed — `git status --short` for this repair pass shows only the 6 frontend files in §3. No
`backend/` file touched.

### 8. Confirmation no new features added

Confirmed — no new endpoint, no inbox, no line-level persistence, no notes persistence. Every
change in this repair is either a null/array guard or the error-boundary wrapper; no new visible
functionality was added.

### 9. Confirmation CLAIM-REVIEW-SPLIT-2B was not started

Confirmed — no inbox file, no new route, no new component outside the repair list in §3 was created.

---

## Scope Correction — Independent Review Page

Re-verified the extraction against the corrected scope (independent reviewer workspace, no reuse
of manual-entry/provider-submission/batch logic beyond visual reference).

### 1. Confirmation manual claim entry was not touched

`git status --short` shows zero files under any manual-claim-entry path modified, and
`ClaimReviewWorkspace.jsx`/its components contain **zero references** to manual-entry service
calls or components (re-checked via grep for `ClaimBatch`, `claim-batches.service`,
`calculateCoverageBulk`, `saveDraft`, `submitClaim`, `addService`, `removeService` across
`frontend/src/pages/claims/review/` — no matches).

### 2. Confirmation provider claim submission was not touched

`ProviderClaimsSubmission.jsx` (the provider-side monolith) was never opened, imported, or
referenced by anything in this phase — confirmed by the same grep above (zero matches) and by
`git status` (file absent from the diff). The reviewer workspace's draft/notes logic is its own
independent `localStorage` mechanism (`claim-review-draft-{id}`, `claim-review-chat-{id}`),
architecturally unrelated to the provider's server-backed draft (`GET/PUT /claims/draft?batchId=`).

### 3. Confirmation batch entry/detail logic was not touched

`ClaimBatchEntry.jsx` and `ClaimBatchDetail.jsx` are **absent from `git status --short`** for this
entire phase — neither file was opened for editing, and neither was imported by the new workspace.

### 4. Confirmation batch UI was used only as visual reference if used

**Not used at all, not even as visual reference.** The new workspace's table/card/section styling
was extracted directly from the *original* `ClaimViewMedicalReview.jsx` (its own pre-existing
`SectionCard`/`InfoRow`/table markup), not copied or adapted from any batch page. No batch-specific
component, class name, or layout pattern was referenced.

### 5. Exact route to test the review page

```
http://localhost:3001/claims/401/medical-review
```

### 6. Test claim id and role

- **Claim ID:** `401` — real local-dev claim, provider 1 (دار الشفاء), status `SUBMITTED`,
  1 service line, 0 attachments (confirmed via direct API call: `GET /api/v1/claims/401` → 200,
  `GET /api/v1/claims/401/attachments` → 200, `[]`).
- **Test account (local-only, created for this manual test):**
  - Username: `reviewer_test`
  - Password: `Admin@123`
  - Role: `MEDICAL_REVIEWER`
  - Assigned to provider 1 (دار الشفاء) via `medical_reviewer_providers`, so reviewer-provider
    isolation (CLAIM-REVIEW-SECURITY-1) — if that build is deployed — will not block access to
    this specific claim.
  - This is a genuinely new local database row (not a shared/production-like credential), left in
    place (not deleted) specifically so you can log in and test yourself; let me know if you'd
    like it removed afterward.
- Other SUBMITTED claims also available under the same provider if you want a second example:
  `201`, `251`, `351`.

### 7. Browser smoke test status

**Still not performed by me** — no browser automation tool is available in this environment. I
verified server-side reachability only: both API calls above return `200` for the
`reviewer_test` account, and the frontend container is serving the latest built
`ClaimReviewWorkspace-*.js` chunk (confirmed via `docker exec ... ls assets/`). The actual
in-browser render, click-through, and console-error check still require your own browser session.

### 8. Remaining blockers

None on my end beyond the inability to run a browser myself. If the page still fails to render at
the URL above, please share the exact error card message/ID (the new `PageErrorBoundary` should
now show one instead of a blank screen — see §4 of the Browser Smoke Test Repair section above) or
any console error text, and I'll fix the specific issue directly rather than guessing further.

---

## Reviewer Menu Test Entry

The user could not discover the new workspace from the UI at all — the "المطالبات والموافقات"
menu only had links to the batch system, the pre-auth email inbox, and the claims report, none of
which lead to `ClaimReviewWorkspace`. Added a single, clearly-marked temporary menu entry.

### 1. Menu file changed

```
frontend/src/menu-items/components.jsx
```

Only this file. No other menu/config file touched.

### 2. Exact label added

**"مراجعة المطالبات"** (English: "Claim Review (temp)"), with a `chip: { label: 'مؤقت', color:
'warning' }` — visually flagged as temporary directly in the menu UI itself (an orange "مؤقت"
badge next to the label), not just in code comments.

### 3. Exact route

```
/claims/401/medical-review
```

A fixed URL to one real test claim (id `401`) — not a dynamic/parameterized entry, since building
real dynamic navigation is explicitly CLAIM-REVIEW-SPLIT-2B's job, not this repair's.

### 4. Whether the route is temporary

Yes, explicitly. The menu item's `id` is `claims-review-workspace-temp`, and it carries a code
comment directly above it in `components.jsx`:

> "TEMPORARY (CLAIM-REVIEW-SPLIT-2A): direct link to a single fixed test claim so the new
> ClaimReviewWorkspace is reachable from the UI while no reviewer inbox exists yet. This is NOT
> the reviewer inbox — it is a fixed URL to claim id 401 for local/dev smoke testing only. It must
> be replaced by a real dynamic entry once CLAIM-REVIEW-SPLIT-2B (reviewer inbox) lands; remove
> this item at that point."

No `devOnly`/`hidden`/`testOnly` flag exists anywhere in this menu configuration schema (checked —
only a stray commented-out `// hidden:` note in an unrelated item, not an implemented mechanism),
so per your fallback instruction, the link was added directly and marked temporary via the code
comment + visible "مؤقت" chip instead.

### 5. Confirmation this is not the 2B inbox

Confirmed — no list, no query, no filtering, no pagination, no `getPendingClaims`/
`getApprovedClaims` API wiring was added. It is a single static `url` string pointing at one fixed
claim id, nothing more.

### 6. Confirmation batch/manual/provider pages were not touched

`git status --short` for this change shows exactly one file:
`frontend/src/menu-items/components.jsx`. No batch entry/detail file, no manual claim entry file,
no provider submission file, and no PreAuthorization/Visit file appears in the diff.

### 7. Confirmation "تقرير المطالبات" remains a report link, not repurposed

The existing `claims-report` menu item (`id: 'claims-report'`, title "تقرير المطالبات (مراجعة)",
`url: '/reports/claims'`) was **not modified** — still points to the read-only claims report, as
before. The new temporary item was inserted as a **separate, additional** menu entry directly
after "نظام الدفعات (Batches)" and before "طلبات البريد (Pre-Auth)", not merged into or replacing
any existing item.

### 8. User smoke test instructions

1. Log in as `reviewer_test` / `Admin@123` (created in the previous repair pass, still present in
   the local dev database, assigned to provider 1).
2. Open the "المطالبات والموافقات" menu group in the sidebar.
3. Click "مراجعة المطالبات" (marked with an orange "مؤقت" chip).
4. This navigates to `/claims/401/medical-review`.
5. The `ClaimReviewWorkspace` page should render: member/provider context, the one service line
   (read-only, with the temporary per-line decision buttons and their "مؤقت" banner), financial
   summary, an empty (0 attachments) read-only attachments panel, and the sticky action bar with
   Approve/Reject/Request-info buttons.

### Tests / checks run for this change

```
git diff --check          → clean
npx eslint src/menu-items/components.jsx   → 0 errors, 4 warnings (pre-existing prettier-style only)
npx vite build             → succeeded in 28.96s
```

Rebuilt and redeployed the local `waad-local-frontend` container; confirmed healthy.

---

**Local commit only, per the current delivery rule. Not pushed.**

## CLAIM-REVIEW-SPLIT-2A READY FOR USER UI SMOKE TEST

STOP.
