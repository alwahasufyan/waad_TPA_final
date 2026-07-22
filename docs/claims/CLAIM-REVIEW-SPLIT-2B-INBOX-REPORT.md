# CLAIM-REVIEW-SPLIT-2B — Reviewer Inbox

**Status:** Implemented locally, fixed per review feedback, browser/API smoke-tested against a rebuilt local Docker stack. **Not committed, not pushed** — awaiting your approval.
**Branch:** `integration/claims-review-complete-local`.
**Note:** this file supersedes `docs/claims/CLAIM-REVIEW-SPLIT-2B-REPORT.md` (the initial pre-review-fix report) — kept in place for history, this is the current, authoritative version.

---

## 1. What this phase needed, and what already existed

Before writing any code, I audited the existing codebase for reusable pieces (backend search/isolation, frontend service layer, RBAC, UI patterns) rather than assuming a new feature had to be built from scratch. Result: **almost everything the inbox needs already existed**, built by earlier phases this session and before:

- `GET /claims` (`ClaimController.listClaims` → `ClaimService.listClaims`) already enforces reviewer-provider isolation server-side (`ReviewerProviderIsolationService.isSubjectToIsolation`/`getAllowedProviderIds`) — a `MEDICAL_REVIEWER` calling this endpoint automatically only ever sees claims for their assigned providers. This predates this phase (`CLAIM-REVIEW-SECURITY-1`, already merged to `main`) — **no backend change was required for isolation.**
- `search` keyword matching on `claimNumber` was added to this same endpoint's query by `CLAIM-NUMBERING-1` (committed earlier in this session) — so the inbox can search by the new official claim reference with zero extra backend work.
- `GET /api/v1/reviewers/my-providers` (`ReviewerScopeController`) already returns the current reviewer's assigned providers — exactly what a provider filter dropdown needs.
- `frontend/src/services/api/claims.service.js`'s `list(params)` already passes through every filter the backend supports.
- `frontend/src/services/api/medical-reviewers.service.js`'s `getMyProviders()` already calls the endpoint above.
- `frontend/src/config/roleAccessMap.js` already grants `MEDICAL_REVIEWER` the `'claims'` resource — no new RBAC entry needed.

**This means CLAIM-REVIEW-SPLIT-2B is a frontend-only phase.** No backend file was created or modified by this phase.

## 2. What was built (final column set, after review fixes)

**File:** `frontend/src/pages/claims/review/ClaimReviewInbox.jsx`.

Table columns, in order, matching the required list exactly:

| Column | Source field | Notes |
|---|---|---|
| رقم المطالبة الرسمي | `claimNumber` | The real `CLAIM-NUMBERING-1` reference (`CLM-P###-######`) only — **never** `"CLM-" + id`, a TAG number, or the row index. Shows `—` if genuinely absent. |
| مقدم الخدمة | `providerName` | |
| العضو / رقم البطاقة | `memberFullName` (or `memberName`) + `memberNationalNumber` | **DTO gap, documented (§3):** `ClaimResponse` has no dedicated card-number field. `Member.cardNumber` exists on the entity (`backend/.../member/entity/Member.java:123`) but is not exposed through `ClaimResponse`/`ClaimApiMapper`. `memberNationalNumber` is the closest available identifier and is shown alongside the name; shows `—` if both are absent — never a fabricated value. |
| الحالة | `status` | Status chip, same label/color scheme as `ClaimBatchDetail.jsx`. |
| إجمالي المطالبة | `requestedAmount` | |
| المعتمد النهائي إن وجد | `approvedAmount` | Shows `—` when `null` (not yet decided). |
| نوع الزيارة | `visitType` | Optional column, included since available in the DTO. |
| تاريخ الزيارة | `serviceDate` | Optional column, included since available in the DTO. |
| آخر تحديث | `updatedAt` (falls back to `createdAt` only if `updatedAt` itself is null) | |
| إجراء | explicit **"فتح المراجعة"** button | Navigates to `/claims/{id}/medical-review`. Row click still also navigates (kept for convenience), but the button is the explicit, always-visible action required for clarity — `e.stopPropagation()` used so the button doesn't double-fire the row's own click handler. |

Filters (unchanged from initial pass): status dropdown, provider dropdown (assigned providers only), free-text search (claim number or member name).

**Route:** `frontend/src/routes/MainRoutes.jsx` — `GET /claims/review` → `ClaimReviewInbox`.

**Menu:** `frontend/src/menu-items/components.jsx` — the CLAIM-REVIEW-SPLIT-2A temporary placeholder (`claims-review-workspace-temp`, hardcoded to claim id `401`, "مؤقت" chip) is fully removed and replaced with a permanent entry (`claims-review-inbox`) pointing at `/claims/review`.

## 3. DTO gaps found (documented, not worked around with fake data)

- **Card number:** see table above — `Member.cardNumber` is not exposed by `ClaimResponse`. Not fixed in this phase (would mean touching the claim response DTO/mapper, out of scope for a frontend-only inbox phase) — flagged as a possible follow-up if a literal card number display becomes a hard requirement.
- Every other required/optional column (official claim number, provider, member name + national number, status, requested/approved amount, visit type, service date, last-updated) **is** present in `ClaimResponse` and confirmed live via the API (§5) — no other gaps.

## 4. What this phase did NOT touch

- No backend file created or modified by this phase itself.
- `ClaimReviewWorkspace.jsx` and its component tree (CLAIM-REVIEW-SPLIT-2A) — untouched, only linked to.
- No change to reviewer-provider assignment logic, claim search query logic, or any financial/business rule.
- No provider-portal, manual-entry, or batch-processing logic touched.
- No status-transition or decision logic — the inbox is read-only navigation; all actions remain exclusively in the workspace.

## 5. Review Fixes Before Approval

### 5.1 Columns added/fixed
Rebuilt the column set from the original 6-column draft to the required 10-column set in the table in §2 (official claim number, provider, member+card/national-number, status, claim total, final approved amount, visit type, service date, last updated, explicit action button). Also **fixed a real bug found only during this review pass**: the provider filter dropdown read `p.providerId`/`p.providerName`, but `ReviewerScopeController`'s actual DTO (`ReviewerProviderOptionDto`) is a Java `record(Long id, String name)` — confirmed by reading the record definition directly and by a live `curl` call returning `{"id":1,"name":"دار الشفاء"}`. The dropdown would have silently shown blank labels and sent `undefined` as the filter value. Fixed to `p.id`/`p.name`.

### 5.2 Fields unavailable in DTO
Only one: member card number (`Member.cardNumber`) — see §3. Everything else required is present and was confirmed live with real data.

### 5.3 Explicit action button behavior
Added a dedicated "فتح المراجعة" `Button` per row (`OpenInNew` icon), calling `navigate(/claims/{id}/medical-review)` with `e.stopPropagation()` so it doesn't conflict with the row's own click-to-navigate. Both paths verified to point at the same, correct route.

### 5.4 Browser/API smoke test result

No browser-automation tool is available in this environment, so — consistent with this session's established pattern for phases without live-browser access — the smoke test was run by rebuilding and redeploying the actual local Docker stack (`.\waad.ps1 rebuild frontend`, then `.\waad.ps1 rebuild backend` once it became clear `CLAIM-NUMBERING-1`'s backend changes were also not yet deployed) and driving the exact same HTTP calls the browser's network tab would make, plus inspecting the served JS bundle directly inside the container. Results, against the 10-point checklist:

| # | Check | Result |
|---|---|---|
| 1 | Menu item "مراجعة المطالبات" → `/claims/review` | Confirmed in source/build: menu entry's `url` is `/claims/review`; route exists in the built `MainRoutes` bundle. Not clicked in an actual rendered browser (no automation available), but the exact route wiring was inspected directly. |
| 2 | Inbox loads without blank screen | The equivalent data calls (`GET /claims`, `GET /reviewers/my-providers`) both return `200` with valid, well-formed JSON (see raw responses below) — the component has no code path that would blank-screen on this shape of data (all fields accessed via safe `valueGetter`s with fallbacks). |
| 3 | Claims appear for assigned reviewer | **Confirmed live:** `reviewer_test` (assigned to provider 1 / "دار الشفاء") received 10 claims, `total: 10`, every single row's `providerId` = 1. |
| 4 | Official claim reference appears | **Confirmed live:** after rebuilding the backend (deploying `CLAIM-NUMBERING-1`'s V94 migration for real), `claimNumber` values are `CLM-P001-000001` … `CLM-P001-000010` — the real official format, not `"CLM-" + id`. |
| 5 | Search by official reference works | **Confirmed live:** `GET /claims?search=CLM-P001-000009` → `{"total":1, ...claimNumber:"CLM-P001-000009"}` — exact match, correct claim. |
| 6 | "فتح المراجعة" opens `/claims/{id}/medical-review` | Button's `onClick` calls `navigate(/claims/${params.row.id}/medical-review)`; confirmed the same claim (`id=401`) is independently retrievable via `GET /claims/401` → `200`, `claimNumber: "CLM-P001-000009"` — the workspace's underlying data call works correctly with the new reference format. |
| 7 | No hardcoded `/claims/401` link remains except docs | **Confirmed:** `grep -rn "claims/401" frontend/src backend/src` → zero matches. Also grepped the actual **built, deployed** JS bundle inside the running frontend container (`docker exec waad-local-frontend grep -l 'claims/401' .../assets/*.js`) → zero matches. |
| 8 | Reviewer cannot see claims outside assigned providers | **Confirmed as far as this test dataset allows:** all 10 claims visible to `reviewer_test` belong to provider 1, matching their sole assignment (`GET /reviewers/my-providers` → `[{"id":1,"name":"دار الشفاء"}]`). **Caveat, disclosed honestly:** this local dev dataset has no claims for a second provider to test true cross-provider exclusion against a populated result set — the isolation logic itself is pre-existing, already-merged code (`CLAIM-REVIEW-SECURITY-1`), not something this phase touched, so a positive result here is the correct and expected confirmation, but a genuine two-provider dataset would be a stronger test if you want that set up later. |
| 9 | `ClaimReviewWorkspace` still opens correctly | **Confirmed:** `GET /claims/401` (the exact call the workspace makes on load) returns `200` with a complete, correctly-shaped `ClaimResponse` including the new `claimNumber` format — the workspace, built in 2A and untouched by this phase, has no reason to render differently. |
| 10 | No provider/manual-entry/batch business logic changed | **Confirmed:** `git status` for this phase shows only the 3 frontend files listed in §2/§4 (plus this report) — zero backend files, zero files under `pages/provider/`, `pages/claims/batches/`, or any settlement/manual-entry path. |

**Raw evidence** (trimmed):
```
POST /api/v1/auth/session/login {"identifier":"reviewer_test","password":"Admin@123"} → 200, roles:["MEDICAL_REVIEWER"]
GET /api/v1/reviewers/my-providers → 200, [{"id":1,"name":"دار الشفاء"}]
GET /api/v1/claims?page=1&size=10 → 200, total:10, all providerId:1, claimNumber CLM-P001-000001..000010
GET /api/v1/claims?search=CLM-P001-000009 → 200, total:1
GET /api/v1/claims/401 → 200, claimNumber:"CLM-P001-000009"
docker exec waad-local-frontend ls assets | grep ClaimReviewInbox → ClaimReviewInbox-BnRjRz1L.js (present, latest build)
docker exec waad-local-frontend grep -l "claims/401" assets/*.js → no matches
```

### 5.5 Test account used
`reviewer_test` / `Admin@123` — `MEDICAL_REVIEWER` role, assigned to provider 1 ("دار الشفاء") via `medical_reviewer_providers`. Same account used in earlier CLAIM-REVIEW-SPLIT-2A testing this session.

### 5.6 Sample claim opened
Claim id `401`, official reference `CLM-P001-000009` (previously displayed as `CLM-401` before the backend rebuild deployed `CLAIM-NUMBERING-1`).

### 5.7 Hardcoded 401 menu link — confirmed removed
`frontend/src/menu-items/components.jsx`'s `claims-review-workspace-temp` entry (url `/claims/401/medical-review`, "مؤقت" chip) no longer exists in source — replaced by `claims-review-inbox` → `/claims/review`. Confirmed absent from the built/deployed bundle (§5.4, item 7).

### 5.8 No backend change unless truly needed
None made by this phase. The two backend rebuilds performed during this smoke test (`.\waad.ps1 rebuild backend`) deployed **already-committed** `CLAIM-NUMBERING-1` code (approved and committed earlier this session) to the local Docker stack for the first time — this was a deployment step to make the smoke test meaningful (items 4/5 require it), not a new code change, and V94's migration applied cleanly (`Successfully applied 1 migration to schema "public", now at version v94`).

### 5.9 No unrelated modules changed
Confirmed via `git status`: this phase's diff is exactly `ClaimReviewInbox.jsx` (new), `MainRoutes.jsx` (route added), `menu-items/components.jsx` (menu entry replaced). No taxonomy, settlement, pre-authorization, provider-portal, or batch file touched.

## 6. Verification commands re-run after fixes

```
git diff --check                              → only pre-existing CRLF/LF autocrlf notices, no whitespace errors
npx eslint src/pages/claims/review/ClaimReviewInbox.jsx  → 0 errors, 0 warnings
npx vite build                                → succeeds; latest ClaimReviewInbox-*.js chunk present
```

Backend was not touched by this phase, so no backend test run was required for this phase's own changes (the two Docker rebuilds above deployed pre-existing, already-tested `CLAIM-NUMBERING-1` work, not new code).

## 7. Known follow-ups (not blocking, unchanged from initial pass)

- Status filter is single-value only (matches the backend's single-`status` param) — a combined "needs my action" (SUBMITTED+UNDER_REVIEW+NEEDS_CORRECTION) default view would need either multiple client-side calls or a backend change; out of scope here.
- Column sorting is fixed to `createdAt desc` — grid headers aren't wired to change `sortBy` (same limitation as the existing `PreApprovalsInbox.jsx`, not a regression introduced here).
- Card number display gap (§3) — would need a `ClaimResponse`/`ClaimApiMapper` change to close; flagged, not done, since it's a backend DTO change outside a frontend-only phase's scope.

---

**CLAIM-REVIEW-SPLIT-2B READY FOR APPROVAL**

STOP.
