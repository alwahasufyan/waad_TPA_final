# CLAIM-REVIEW-SPLIT-2B — Reviewer Inbox

**Status:** Implemented locally. **Not committed, not pushed** — awaiting your review.
**Branch:** `integration/claims-review-complete-local`.

---

## 1. What this phase needed, and what already existed

Before writing any code, I audited the existing codebase for reusable pieces (backend search/isolation, frontend service layer, RBAC, UI patterns) rather than assuming a new feature had to be built from scratch. Result: **almost everything the inbox needs already existed**, built by earlier phases this session and before:

- `GET /claims` (`ClaimController.listClaims` → `ClaimService.listClaims`) already enforces reviewer-provider isolation server-side (`ReviewerProviderIsolationService.isSubjectToIsolation`/`getAllowedProviderIds`) — a `MEDICAL_REVIEWER` calling this endpoint automatically only ever sees claims for their assigned providers; a reviewer with zero assigned providers gets an empty page, not an error or someone else's data. This was already in place before this phase (part of `CLAIM-REVIEW-SECURITY-1`, already merged to `main`) — **no backend change was required for isolation.**
- `search` keyword matching on `claimNumber` was added to this same endpoint's query by `CLAIM-NUMBERING-1` (committed earlier in this session) — so the inbox can search by the new official claim reference with zero extra backend work.
- `GET /api/v1/reviewers/my-providers` (`ReviewerScopeController`) already returns the current reviewer's assigned providers as `{providerId, providerName}` options — exactly what a provider filter dropdown needs.
- `frontend/src/services/api/claims.service.js`'s `list(params)` already passes through every filter the backend supports (status/providerId/search/pagination/sort) with response normalization.
- `frontend/src/services/api/medical-reviewers.service.js`'s `getMyProviders()` already calls the endpoint above.
- `frontend/src/config/roleAccessMap.js` already grants `MEDICAL_REVIEWER` the `'claims'` resource — no new RBAC entry needed.

**This means CLAIM-REVIEW-SPLIT-2B is a frontend-only phase.** No backend file was touched.

## 2. What was built

**New file:** `frontend/src/pages/claims/review/ClaimReviewInbox.jsx` — a claims list page for reviewers:

- Header (`ModernPageHeader`) + refresh button.
- Filters: status dropdown (SUBMITTED / UNDER_REVIEW / NEEDS_CORRECTION / APPROVED / REJECTED / SETTLED, using the same Arabic labels/colors already established in `ClaimBatchDetail.jsx`'s `getStatusChip` for visual consistency with the rest of the app), provider dropdown (populated from `medicalReviewersService.getMyProviders()` — only shows providers actually assigned to the logged-in reviewer), and a free-text search box (claim number or member name, via the existing `search` keyword param).
- `DataGrid` (MUI, server-side pagination, matching the existing `PreApprovalsInbox.jsx` pattern used elsewhere in this app) showing: claim number, member name, provider name, requested amount, submission date, status chip.
- Row click navigates to the existing `/claims/:id/medical-review` route (`ClaimReviewWorkspace`, built in `CLAIM-REVIEW-SPLIT-2A`) — this is the intended handoff point between the two phases.

**Route added:** `frontend/src/routes/MainRoutes.jsx` — `GET /claims/review` → `ClaimReviewInbox`, lazy-loaded the same way as every other route in this file, guarded by the existing `PermissionGuard`.

**Menu entry replaced:** `frontend/src/menu-items/components.jsx` — the temporary, explicitly-marked placeholder from CLAIM-REVIEW-SPLIT-2A (`claims-review-workspace-temp`, a hardcoded link to claim id `401`, with a "مؤقت" chip) is now removed and replaced with a permanent entry (`claims-review-inbox`) pointing at `/claims/review` — exactly the removal the 2A report's own comment said should happen once this phase landed.

## 3. What this phase did NOT touch

- No backend file was created or modified — confirmed by `git status` showing only 3 frontend files changed/added.
- `ClaimReviewWorkspace.jsx` and its component tree (CLAIM-REVIEW-SPLIT-2A) — untouched, only linked to via `navigate()`.
- No change to reviewer-provider assignment logic, claim search query logic, or any financial/business rule.
- No status-transition or decision logic — the inbox is read-only navigation; all actions (approve/reject/return-for-info/etc.) remain exclusively in the workspace, unchanged.

## 4. Verification performed

```
npx eslint (3 changed files)   → 0 errors (pre-existing prettier-only warnings, unchanged in kind/count from before this phase)
npx vite build                 → succeeds; ClaimReviewInbox-*.js chunk present in dist/assets
git status                     → exactly 3 files: new ClaimReviewInbox.jsx, modified MainRoutes.jsx, modified menu-items/components.jsx
```

Backend contract cross-checked by reading `ClaimController.listClaims`'s exact `@RequestParam` names directly (not assumed) — `page`, `size`, `sortBy`, `sortDir`, `status`, `providerId`, `search` all match what `ClaimReviewInbox.jsx` sends via `claimsService.list(...)`.

**Not verified live in a browser:** the local Docker frontend/backend containers are still running the pre-existing build (last rebuilt before this session's commits) — this new page has not been deployed or click-tested in an actual browser session. I can rebuild and redeploy the local stack and walk through a live login as the existing `reviewer_test` account (assigned to provider 1) if you want that confirmed before approving — flagging this honestly rather than claiming it's browser-verified when it isn't.

## 5. Known follow-ups (not blocking, not done here)

- The status filter currently allows only one status at a time (matching the backend's single-`status` param) — an inbox conceptually might want "everything needing my action" (SUBMITTED + UNDER_REVIEW + NEEDS_CORRECTION at once) as a default view. Not implemented, since the backend endpoint doesn't support multi-status filtering today; would need either multiple sequential calls merged client-side or a backend change, both out of scope for this phase. Currently defaults to "الكل" (all statuses, no filter), same as browsing any other claims list.
- Sorting is fixed to `createdAt desc` (grid column headers are not currently wired to change `sortBy`) — consistent with `PreApprovalsInbox.jsx`'s same limitation, not a regression introduced here.

---

**CLAIM-REVIEW-SPLIT-2B READY FOR REVIEW**

STOP.
