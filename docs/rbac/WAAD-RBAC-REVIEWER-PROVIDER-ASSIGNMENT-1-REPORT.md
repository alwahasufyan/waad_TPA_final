# WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1 — Report

Status: **PARTIALLY READY — explain** (see §10). Not committed, not pushed.

## 1. Executive summary

The ticket asked to build many-to-many reviewer↔provider assignment
enforcement from scratch (DB table, services, APIs, enforcement,
frontend, tests). **Investigation found the feature already exists** —
built in an earlier, undocumented-in-this-session phase ("Medical
Reviewer Isolation Phase", 2026-02-12 / "CLAIM-REVIEW-SECURITY-1" /
"PREAUTH-REVIEW-WORKFLOW-1"). Reusing existing infrastructure per the
ticket's own instruction ("if there is already a similar endpoint from
earlier work, reuse it instead of duplicating"), this ticket's actual
work was:

1. Auditing the existing enforcement for gaps (found and fixed 4 real
   ones, all in pre-authorizations).
2. Removing a dangerous leftover dev script that ran on every app startup.
3. Building the missing piece: an admin UI to actually manage assignments
   (the backend API existed with no frontend consumer).
4. Adding regression tests for the fixed gaps.

## 2. Product decision confirmed

Reviewer-provider assignment is enforcement scope (not informational),
exactly as instructed, and this was already the existing design:
`ReviewerProviderIsolationService` throws `AccessDeniedException` (with
the bilingual "لا تملك صلاحية الوصول..." message) when a MEDICAL_REVIEWER
tries to access an unassigned provider's data; SUPER_ADMIN/WAAD_ADMIN
bypass it entirely (`isSubjectToIsolation()` returns `false` for any
non-`MEDICAL_REVIEWER` role); a reviewer with zero assignments gets an
empty result set, not an error and not "all providers".

## 3. DB migration

**None added.** `medical_reviewer_providers` already exists (from
`V10__provider_services.sql`, not its own migration) with exactly the
shape the ticket asked for: `reviewer_id`, `provider_id`, `active`
soft-delete flag, audit columns, unique constraint on
`(reviewer_id, provider_id)`, FKs to `users(id)`/`providers(id)`. Adding
a second, differently-named table (`medical_reviewer_provider_assignments`,
the name in the ticket text) would have created a second competing
schema for the same concept — not done.

## 4. Backend services/endpoints (pre-existing, reused)

- `ReviewerProviderIsolationService` — `isSubjectToIsolation`,
  `getAllowedProviderIds`, `validateReviewerAccess`,
  `hasAnyProviderAssignments`, `getAssignedProviders`.
- `MedicalReviewerProviderAssignmentService` +
  `MedicalReviewerProviderAssignmentController` —
  `GET/PUT /api/v1/admin/medical-reviewers/{id}/providers`
  (`SUPER_ADMIN`/`WAAD_ADMIN` only; PUT does a full idempotent
  replace-all with soft activate/deactivate, MEDICAL_REVIEWER-only target
  validation, audit log entry).
- `ReviewerScopeController` — `GET /api/v1/reviewers/my-providers` (the
  reviewer's own assigned providers, used by `ClaimReviewInbox.jsx`
  already).
- Frontend service `medical-reviewers.service.js` already wrapped all
  three endpoints — just had no UI consumer for the admin
  get/update pair.

## 5. Gaps found and fixed (the actual enforcement work in this ticket)

All in `backend/src/main/java/com/waad/tba/modules/preauthorization/`,
none in claims/visits (those were already correctly isolated — see §7):

1. **`PreAuthorizationService.getAllPreAuthorizations()`** (`GET /api/v1/pre-authorizations`,
   MEDICAL_REVIEWER-allowed) had **zero** provider scoping — an isolated
   reviewer could see every pre-authorization in the system. Fixed:
   branches on `isSubjectToIsolation`, uses new repo method
   `findByProviderIdInAndActiveTrue`, returns `Page.empty()` without
   querying if the reviewer has no assignments.
2. **`PreAuthorizationService.getPreAuthorizationsByProvider()`**
   (`GET /provider/{providerId}`) accepted any `providerId` with no
   validation. Fixed: calls `validateReviewerAccess` first (throws 403
   for an unassigned provider).
3. **`PreAuthorizationService.search()`** (`GET /search`) had no
   isolation either. Fixed: same branch pattern, new repo method
   `searchByProviderIds`.
4. **`PreAuthorizationController.assertPreAuthorizationBelongsToCaller()`**
   — the shared guard behind `GET /{id}/attachments` and
   `GET /{id}/attachments/{attachmentId}` (both MEDICAL_REVIEWER-allowed)
   — had a code comment stating *"reviewer/admin roles are unaffected"*
   (a **documented, intentional** gap from an earlier ticket,
   DOCUMENTS-IDOR-1, which only scoped `PROVIDER_STAFF`). An isolated
   reviewer could view/download any provider's pre-authorization
   attachments. Fixed: now also calls
   `reviewerIsolationService.validateReviewerAccess(...)`, mirroring the
   pattern `VisitAttachmentController` already used correctly.

New `PreAuthorizationRepository` methods:
`findByProviderIdInAndActiveTrue(List<Long>, Pageable)`,
`searchByProviderIds(String, List<Long>, Pageable)`.

## 6. Dangerous leftover removed

`backend/src/main/java/com/waad/tba/config/ReviewerAssignmentFixer.java`
— a `@Component implements CommandLineRunner`, so it **ran on every
single app startup** (every `waad.ps1 rebuild` this whole session
triggered it). It hardcoded: find user `'nada'`, wipe all their
existing provider assignments, then assign them to **every provider in
the database**. Confirmed unreferenced anywhere else (grep) — deleted
outright rather than patched, since it was explicitly a one-off dev
script ("Temporary Utility... Executed once at startup") that had no
business being a permanent `CommandLineRunner`. This was a real
data-integrity hazard for any environment where this container image
runs, independent of anything else in this ticket.

## 7. Enforcement audit — claims & visits (already correct, spot-checked)

- `ClaimReviewService.getPendingClaims/getApprovedClaims`,
  `ClaimService.listClaims/getClaim/getFinancialSummary/findDeleted`,
  claim decision methods, `ClaimAttachmentController` — all already
  isolation-checked.
- `VisitService` list/search, `VisitAttachmentController` — already
  isolation-checked (its `assertVisitBelongsToCaller` already calls
  `reviewerIsolationService.validateReviewerAccess` when the caller is a
  reviewer, unlike the preauth equivalent before this ticket's fix).
- Not independently re-verified line-by-line beyond what's listed —
  reasonable confidence given the consistent pattern, but see §10.

## 8. Frontend UI (the missing piece — built in this ticket)

New `frontend/src/pages/rbac/users/ReviewerProviderAssignmentPanel.jsx` —
self-contained (own load/save, doesn't block the parent user-edit save),
mounted in `UserEdit.jsx`'s role step, shown only when `MEDICAL_REVIEWER`
is the selected role:
- MUI `Autocomplete` (multi-select) over the provider list already
  loaded by `UserEdit` via `providersService.getSelector()` (bulk, up to
  1000 — reasonable for MUI's client-side filtering at "~400 providers"
  scale; no new search endpoint needed).
- Table of currently-selected providers with a per-row "إزالة" action.
- Empty state: **"لا توجد مقدمو خدمة مسندون لهذا المراجع"**, exact text
  from the ticket.
- Save button calls the existing PUT endpoint (replace-all semantics);
  disabled until the selection actually changes.
- For non-reviewer roles: the whole section is hidden (not rendered),
  per the ticket's "hide this section" option.
- Not added to `UserDetails.jsx` (read-only page) — only to `UserEdit.jsx`,
  since assignment is an editing action; flagged in §10 if you'd rather
  it also appear (read-only) on the details page.

## 9. Tests / build results (all re-verified with a genuinely fresh run — see caveat below)

**Important build-tooling note surfaced while validating:** this repo's
`pom.xml` sets `<skipTests>true</skipTests>` by default (a deliberate,
pre-existing repo convention, not something changed here) — `mvn test`
alone silently skips all tests unless you also pass
`-DskipTests=false`. Every verification below was re-run with that flag
explicitly to make sure it's a real result, not a stale/skipped report.

- `mvn -o test -DskipTests=false` (full suite): **339 tests, 18 failures,
  5 errors, 0 skipped** — exactly the pre-existing documented baseline
  (financial-engine/coverage-engine/Excel-import test debt +
  `@SpringBootConfiguration` duplicate-class issues unrelated to RBAC).
  **Zero new failures from this ticket's changes.**
- New/updated tests, all passing:
  - `PreAuthorizationServiceReviewerIsolationTest` — 15 tests (6
    pre-existing + 9 new: `getAllPreAuthorizations`/
    `getPreAuthorizationsByProvider`/`search`, each for isolated-reviewer,
    empty-assignments, and SUPER_ADMIN-bypass cases).
  - `PreAuthorizationAttachmentAuthorizationTest` — 7 tests (5
    pre-existing + 2 new: reviewer blocked/allowed on the attachment
    guard fix), constructor signature updated for the 2 new
    controller dependencies.
- Frontend: `yarn build` succeeds; `eslint` on all touched files — 0
  errors (pre-existing style/prettier warnings only, none introduced).
- Browser/manual testing: **not done** — no test MEDICAL_REVIEWER
  credentials available to this session; see §10.

## 10. Remaining gaps / what "PARTIALLY READY" means

- **Not manually/browser-tested** — I don't have login credentials for
  a MEDICAL_REVIEWER test account (`reviewer_test`/`qa_reviewer`/`ali`
  exist in the DB but I don't know their passwords). Everything above is
  verified at the unit-test and code-review level, not end-to-end in the
  browser. Please test: assign/remove a provider for a reviewer in the
  new UI, then log in as that reviewer and confirm claims/preauth/visits
  are actually scoped, and that removing an assignment removes access
  immediately (no relogin should even be required for the backend calls —
  only the frontend's cached menu/permissions need a refresh, per the
  earlier permission-catalog work today).
- **`ReviewerScopeController`'s `/my-providers` bypass check compares
  `userType` to `"MEDICAL_REVIEWER"` directly** rather than reusing
  `isSubjectToIsolation` — functionally equivalent today but worth
  normalizing later, not fixed here to keep this diff focused.
- **`getAttachments`/`downloadAttachment` fix is scoped to
  pre-authorizations only** — the ticket's phase 4 also asked about
  "attachment/document authorization paths" broadly; claim and visit
  attachments were already correct (§7), so no further action was taken
  there, but a full line-by-line re-audit of every attachment endpoint
  in the system was not performed (out of scope for the time available).
- **Reports for reviewers** (ticket's optional §4.4): not touched — the
  granular `reports.*` permission work done earlier today is a
  frontend-only visibility layer; whether a MEDICAL_REVIEWER's *report
  data itself* (not just report page visibility) should be scoped to
  assigned providers is a separate, not-yet-scoped decision. Flagging as
  follow-up `REVIEWER-SCOPED-REPORTS-1`, per the ticket's own suggested
  name.
- **Not integrated into `UserDetails.jsx`** (read-only page) — see §8.

## 11. Files changed

**Backend:**
- Deleted: `backend/src/main/java/com/waad/tba/config/ReviewerAssignmentFixer.java`
- Modified: `PreAuthorizationController.java`, `PreAuthorizationRepository.java`, `PreAuthorizationService.java`
- Modified tests: `PreAuthorizationAttachmentAuthorizationTest.java`, `PreAuthorizationServiceReviewerIsolationTest.java`

**Frontend:**
- New: `frontend/src/pages/rbac/users/ReviewerProviderAssignmentPanel.jsx`
- Modified: `frontend/src/pages/rbac/users/UserEdit.jsx`

(The remaining modified files in the working tree — `rbac.js`,
`useRBACSidebar.js`, both `Navigation/index.jsx` files,
`menu-items/components.jsx`, `UsersList.jsx`, `reports/*`,
`reportRegistry.js`, `MainRoutes.jsx`, `useReportDomainAccess.js`, and
the two `V10x` migrations — belong to the earlier permission-catalog
enforcement work from earlier today, not this ticket; listed here only
because `git status` shows them uncommitted in the same working tree.)

## 12. No push confirmation

Nothing has been pushed. Nothing has been committed — all changes above
are local working-tree changes awaiting review, per the ticket's explicit
instruction.

---

**WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1 — PARTIALLY READY — explain:**
core enforcement gaps found and fixed, dangerous startup script removed,
missing admin UI built, all re-verified with a genuine (non-skipped)
test run — but not yet exercised in a real browser session with an
actual reviewer login, since no test credentials were available this
session.
