# PREAUTH-REVIEW-WORKFLOW-AUDIT-1 — Current Pre-Authorization Workflow vs. Claims-Review Pattern

**Audit only. No code changed. Nothing committed. Nothing pushed.**

## 1. Executive summary

The headline finding is not "this needs to be built" — it's **"this is already mostly built on the backend and largely disconnected on the frontend."** The `PreAuthorization` entity already has a full, real status lifecycle (`PENDING → UNDER_REVIEW → APPROVAL_IN_PROGRESS → APPROVED → ACKNOWLEDGED → USED`, with `REJECTED`/`NEEDS_CORRECTION`/`EXPIRED`/`CANCELLED` branches), and `PreAuthorizationService`/`PreAuthorizationController` already implement `startReview`, `reviewPreAuth`, `approve`, `approvePartial`, `requestInformation` (the "request correction" action), `reject`, `cancel`, `acknowledge`, and `markAsUsed` — role-gated correctly (`SUPER_ADMIN`/`MEDICAL_REVIEWER` for decisions), with reviewer-provider isolation applied to every individual decision action. Provider-portal submission (`ProviderPreApprovalSubmission.jsx`) is real, working, and supports attachments.

**What's actually missing is almost entirely on the frontend, in three specific ways:**

1. **There is no routed reviewer decision workspace.** A real one — `PreApprovalsInbox.jsx`, with Start Review/Approve/Reject actions fully wired to the backend — already exists as a file, but it is **not imported in `MainRoutes.jsx` and not linked from any menu**. It is dead code. The routes that *are* wired (`/pre-approvals`, `/pre-approvals/:id`) are explicitly, deliberately **read-only** (`PreApprovalsList.jsx`'s own code comment: *"This page is READ-ONLY for insurance admins and reviewers"*).
2. **Only the legacy email-intake page is linked from the sidebar.** The single pre-auth sidebar menu entry in the whole app is `/pre-approvals/email-inbox`. Every other pre-auth page — the list, the dashboard, the audit trail, the view page, and the provider's own inbox — is reachable only by typed URL or an in-context deep link, not by navigation.
3. **The provider-side status-visibility page is too narrow.** `/provider/pre-auth-inbox` (`PreAuthInbox.jsx`) only shows `APPROVED`/`ACKNOWLEDGED` items ("acknowledge your approvals"), not a general "here's the status of everything I submitted" view — a provider who gets `REJECTED` or `NEEDS_CORRECTION` has no page to see that, and no frontend path exists to act on `NEEDS_CORRECTION` at all (the backend's `request-info`/correction-loop action has zero frontend consumers).

**A separate, smaller finding worth flagging even though it's adjacent to (not central to) this audit's scope**: the claim-to-pre-authorization link is informational only — a claim can be created without a required pre-auth link, and a linked pre-auth's status is never checked before the claim proceeds (§9.4). This is a financial-integrity question, not a frontend-routing one; noted here, not solved.

**Minimal path forward is cheap relative to a rebuild**: wire the existing `PreApprovalsInbox.jsx` (or a corrected version of it) into the routes and menu, add a `request-info`/correction action to it, broaden the provider status view, and close the reviewer-isolation gap at the list-query level (§9.3). See §11 for phases.

**No blocking condition. Final status: READY FOR REVIEW.**

## 2. Current pre-authorization architecture

Two parallel intake paths feed the same core `PreAuthorization` entity/table, plus one fully separate legacy path:

- **Core, visit-based intake** (the real, intended model): `POST /api/v1/pre-authorizations` (`PreAuthorizationController`, backed by `PreAuthorizationService`), created from the Provider Portal's visit flow, status `PENDING` at creation.
- **Legacy email intake, two variants**:
  - `EmailPreAuthController` (`/api/v1/pre-auth/emails`) + `EmailPreAuthService` — parses inbound emails into `PreAuthEmailRequest`-adjacent records; its "approve" action creates a **new** `PreAuthorization` row directly in `APPROVED` status (`PreAuthorizationService.createPreAuthorizationFromEmail`), completely bypassing `PENDING`/`UNDER_REVIEW`.
  - `PreAuthEmailRequestController` (`/api/preauthorization/email-requests`) — a separate, simpler CRUD-only controller over inbound email records (list/view/delete), fixed for missing authorization in `BACKEND-RBAC-FIX-MISSING-AUTH-1`. Per the product clarification already recorded in that ticket, both of these are **legacy/transitional**.
- **Supporting controllers**: `PreAuthDashboardController` (read-only analytics), `PreAuthorizationAuditController` (read-only audit trail).
- **Attachments**: a dedicated `PreAuthorizationAttachment` entity/table, entirely separate from the email-intake attachment model (`PreAuthEmailAttachment`) — no shared code path between the two.

## 3. Current frontend pages/routes

| Route | Component | Who it's for | Reachable from menu? |
|---|---|---|---|
| `/pre-approvals/email-inbox` | `EmailPreAuthInbox.jsx` | Admin/reviewer (legacy email intake) | **Yes** — the only sidebar-linked pre-auth page |
| `/pre-approvals` | `PreApprovalsList.jsx` | Admin/reviewer, **read-only by design** | No (orphaned) |
| `/pre-approvals/dashboard` | `PreAuthDashboard.jsx` | Admin/reviewer, analytics only | No (orphaned) |
| `/pre-approvals/:id` | `PreApprovalView.jsx` | Admin/reviewer, **read-only**, no decision actions | No (orphaned; reached only via "View" from the list) |
| `/pre-approvals/:id/audit` | `PreAuthAuditPage.jsx` | Admin/reviewer, audit trail | No (orphaned) |
| `/provider/pre-approvals/submit` | `ProviderPreApprovalSubmission.jsx` | Provider, real working submission (draft + final submit, with attachments) | Not in menu; reachable only via a button inside `ProviderVisitLog.jsx`, requires `visitId` + `fromVisitLog=true` in the URL |
| `/provider/pre-auth-inbox` | `PreAuthInbox.jsx` (component `ProviderPreAuthInbox`) | Provider, but **only shows APPROVED/ACKNOWLEDGED items** | No (orphaned) — also absent from the Provider Portal menu group, which links Eligibility/Visits/Documents/Claims-Report/Pre-Auth-Report but not this |
| *(none — not routed)* | `PreApprovalsInbox.jsx` | The actual reviewer decision workspace (Start Review/Approve/Reject, polling for async approval) | **Not routed at all — dead code** |

## 4. Current backend endpoints

`PreAuthorizationController` (`/api/v1/pre-authorizations`, ~26 endpoints, class-level `isAuthenticated()`) — the complete, already-implemented action set: `POST /` (create), `PUT /{id}/data` (provider edit, gated to `allowsEdit()`), `PUT /{id}/review` (generic reviewer decision), `POST /{id}/submit` (PENDING/NEEDS_CORRECTION → UNDER_REVIEW), `POST /{id}/approve`, `POST /{id}/approve-partial`, `POST /{id}/request-info` (→ NEEDS_CORRECTION), `POST /{id}/reject`, `POST /{id}/cancel` (SUPER_ADMIN only), `POST /{id}/acknowledge`, `POST /{id}/mark-used`, `DELETE /{id}` (SUPER_ADMIN only), attachment CRUD, `GET /inbox/pending`, `GET /` + `/search` + `/status/{status}` + `/member/{id}` + `/provider/{id}` + `/valid` + `/check-validity`, `POST /{id}/start-review`. All decision endpoints are already correctly narrowed to `hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')`.

`EmailPreAuthController` (`/api/v1/pre-auth/emails`, 7 endpoints) and `PreAuthEmailRequestController` (`/api/preauthorization/email-requests`, 3 endpoints, now authorization-fixed) — legacy email intake, unchanged/untouched by this audit.

`PreAuthDashboardController` (7 read-only analytics endpoints) and `PreAuthorizationAuditController` (7 read-only audit endpoints) — both fully functional and already frontend-connected.

## 5. Current statuses/lifecycle

```
PENDING ──submit──► UNDER_REVIEW ──approve──► APPROVAL_IN_PROGRESS ──(async)──► APPROVED ──acknowledge──► ACKNOWLEDGED ──(claim created)──► USED
   │                     │                                                          │
   │                     ├──reject──────────────────────────────────────────► REJECTED
   │                     └──request-info─────────────────────────────────────► NEEDS_CORRECTION ──submit──► UNDER_REVIEW (loop)
   │
   └──cancel (SUPER_ADMIN)──► CANCELLED                    APPROVED ──expiry job──► EXPIRED
```
This is the existing, real lifecycle (`PreAuthorization.PreAuthStatus`, `PreAuthorizationService`) — no new statuses need to be invented; it already maps cleanly onto the ticket's proposed target lifecycle (`DRAFT`≈`PENDING` before submit, `SUBMITTED`≈the submit action, `UNDER_REVIEW`, `NEEDS_CORRECTION`, `APPROVED`, `REJECTED`, plus `EXPIRED`/`CANCELLED` which already exist). No lifecycle redesign is needed — only frontend wiring.

## 6. Provider portal submission status

**Yes, real and working**, with one navigability caveat. `ProviderPreApprovalSubmission.jsx` supports both "save as draft" (`POST /pre-authorizations`, stays `PENDING`) and "submit for review" (create, then `POST /{id}/submit` → `UNDER_REVIEW`), and supports attaching documents at submission time (multipart upload per file, `attachmentType='MEDICAL_REPORT'`). The caveat: it's only reachable via a button inside the Visit Log page (`ProviderVisitLog.jsx`, requiring `visitId`+`fromVisitLog=true` in the URL) — there's no standalone "submit a pre-authorization" entry point independent of an existing visit, and it's not in the Provider Portal menu.

## 7. Reviewer/admin review capability

**Backend: yes, fully capable.** Frontend: **no routed page exposes it.** `PreApprovalsList.jsx` (routed, linked only via deep-link from nowhere in the menu) is explicitly read-only by its own code comment. `PreApprovalView.jsx` (routed at `/pre-approvals/:id`) has zero approve/reject/start-review calls anywhere in the file (verified directly) — it shows a status timeline and a permanently-disabled "Convert to Claim" placeholder button. The component that *does* implement the real decision workflow, `PreApprovalsInbox.jsx`, exists, calls the correct backend endpoints (`start-review`, `approve` with async-status polling, `reject` with mandatory reason), but is **not imported anywhere in `MainRoutes.jsx` and not present in the menu file** — confirmed by a repo-wide grep returning only the component's own file. It also has no `request-info`/`NEEDS_CORRECTION` action wired, even though the backend supports it.

## 8. Email-based legacy components

`EmailPreAuthController`, `EmailPreAuthService`, `PreAuthEmailRequestController`, and the frontend's `EmailPreAuthInbox.jsx` + `email-preauth.service.js`. Confirmed self-contained: `EmailPreAuthInbox.jsx` never touches `PreApprovalView`/`PreApprovalsList`/the audit page, and its "approve" action creates pre-authorizations that skip the normal `PENDING`→`UNDER_REVIEW`→decision pipeline entirely (created directly as `APPROVED`). Its other action ("تحويل لزيارة" / convert to visit) is actually the *bridge* toward the real flow — it routes the request through the normal visit-based creation path instead. Per the product clarification already recorded in `BACKEND-RBAC-FIX-MISSING-AUTH-1-REPORT.md`, this cluster is legacy/transitional: **not deleted, not expanded, kept as-is** pending explicit approval to retire it.

## 9. Gap analysis against the claims-review pattern

| Claims-review pattern element | Pre-authorization equivalent | Status |
|---|---|---|
| Reviewer inbox menu entry (`claims-review-inbox` → `/claims/review`) | — | **Missing.** Only the legacy email-inbox is menu-linked; no equivalent for the core entity exists. |
| Reviewer inbox page listing pending items | `PreApprovalsInbox.jsx` exists with the right query/actions | **Built but unrouted/unlinked — dead code.** |
| Review workspace with approve/reject/request-correction | Backend fully supports it (`reviewPreAuth`, `approvePreAuthorization`, `rejectPreAuthorization`, `requestInformation`) | **Backend: done. Frontend: no routed page exposes approve/reject; request-info has zero UI consumers anywhere.** |
| Provider-submitted item invisible to reviewer until submitted | `allowsEdit()`/status gating (`PENDING`/`NEEDS_CORRECTION` editable; `submit` moves to `UNDER_REVIEW`) | **Present and correct**, mirrors claims' draft-then-submit model. |
| Reviewer cannot edit provider's original clinical data | DTO-shape guarantee (reviewer DTOs only carry `status`/`reviewerComment`/`approvedAmount`/`copayPercentage`) | **Present, but implicit** (no active field-immutability assertion — holds only because the DTOs don't expose the fields; see §12 caution). |
| Reviewer scoped to assigned providers (`ReviewerProviderIsolationService`) | Applied to every *decision* method | **Present for decisions.** |
| — same, but at the list/inbox-query level (claims: `findByStatusInAndReviewerProviders`) | `getPendingInbox`/list/search methods use plain `findByStatusIn`, no provider filter | **Gap** — a `MEDICAL_REVIEWER` today would see every provider's pending items in a list, even though they'd be blocked from *deciding* on ones outside their assignment. `startReview` itself also has no isolation check at all (any reviewer can start-review any provider's item). |
| Status-audited decisions | `PreAuthorizationAuditController` fully implemented and frontend-connected (`PreAuthAuditPage.jsx`) | **Present and working**, arguably ahead of the claims module's own audit UI. |
| Explicit status gates | Full enum + `allowsEdit()`/`canBeApproved()`/`canBeCancelled()`/`ensureDecisionReviewable()` guards | **Present and correct.** |

## 10. Recommended target routes

Adopting the ticket's proposal, adjusted to match what already exists:

- **Provider side** (already exists, needs menu + status-breadth fixes, not new routes):
  - `/provider/pre-approvals/submit` — keep; consider also linking it from the Provider Portal menu group directly (not only via the Visit Log deep-link) if standalone submission (not tied to an existing visit) is a real use case — needs a product decision, not assumed here.
  - `/provider/pre-auth-inbox` — broaden its query beyond `APPROVED`/`ACKNOWLEDGED` to show all of the provider's own submissions with status (a "my submissions" view), and add it to the Provider Portal menu.
- **Reviewer/admin side** (route already exists for list; workspace route needs to be added):
  - `/pre-approvals/review` — new route, wire up the existing `PreApprovalsInbox.jsx` (after adding a request-info/correction action) as the reviewer inbox. Add a menu entry parallel to `claims-review-inbox`.
  - `/pre-approvals/:id` — already exists (`PreApprovalView.jsx`); either extend it with approve/reject/request-info actions (matching `ClaimReviewWorkspace`'s pattern of one detail-and-decide page) or keep it read-only and make `PreApprovalsInbox.jsx`'s row actions the sole decision surface — a design choice for the implementation ticket, not decided here.
- **Legacy/transitional** — `/pre-approvals/email-inbox`: no route change, kept as-is pending separate approval to retire it (per the standing product clarification).

## 11. Recommended minimal implementation phases

Given the backend is essentially complete, this is primarily a frontend-wiring + one backend gap-closure effort, not a rebuild:

1. **Phase 1 (frontend routing/menu only, no new backend work)**: add a `/pre-approvals/review` route wiring in `PreApprovalsInbox.jsx` (audit/code-review it first — it hasn't been exercised in production since it's currently dead code), add its menu entry, add `/provider/pre-auth-inbox` to the Provider Portal menu.
2. **Phase 2 (small frontend addition)**: add a "request correction" action to the reviewer workspace calling the already-existing `POST /{id}/request-info`, and broaden `PreAuthInbox.jsx`'s query to show all of a provider's own submissions (not just `APPROVED`/`ACKNOWLEDGED`), so `NEEDS_CORRECTION` becomes an actually-usable status end-to-end.
3. **Phase 3 (small backend addition)**: close the reviewer-isolation gap at the list/inbox-query level — add a provider-scoped query method mirroring `ClaimRepository.findByStatusInAndReviewerProviders`, used in `getPendingInbox`/list/search when `reviewerIsolationService.isSubjectToIsolation(currentUser)`; also add the isolation check to `startReview` (currently the one decision-adjacent method missing it).
4. **Phase 4 (product decision, not assumed here)**: decide whether/when to retire the legacy email-intake pages, and whether `EmailPreAuthInbox.jsx`'s direct-to-`APPROVED` "approve" shortcut should be disabled once the real reviewer workflow is live (to stop new pre-authorizations from bypassing the review pipeline).
5. **Separate track, not part of "make it like claims review"**: the claim↔pre-authorization enforcement gap (§9's note, detailed in the executive summary) — whether a claim requiring pre-approval must have an `APPROVED` linked pre-auth before it can be created/approved. This is a financial-integrity question analogous to prior `ANNUAL-LIMIT-PRECHECK-1`-style findings from earlier audits, not a frontend-routing gap; recommend its own ticket if the business confirms it's a real requirement gap and not an intentional "pre-auth is advisory, not a hard block" design.

## 12. RBAC/data-scope requirements

- Decision actions (`approve`/`reject`/`request-info`/`approve-partial`/`reviewPreAuth`) already correctly enforce `hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')` at the controller level and reviewer-provider isolation at the service level — no change needed for those specific methods.
- `startReview` needs the same isolation check added (§9, §11 phase 3) — currently any `MEDICAL_REVIEWER` can start-review any provider's item even if unassigned, inconsistent with every other decision method.
- List/inbox queries need provider-scoping added for `MEDICAL_REVIEWER` (§9, §11 phase 3) for visibility to match decision-time restriction — today a reviewer can *see* everything but only *act* on their assigned providers' items, which is a confusing (if not exploitable) UX/information-disclosure inconsistency, not a mutation-level security hole.
- Provider-side pages already correctly scope to the caller's own provider via `ProviderContextGuard`/`assertPreAuthorizationBelongsToCaller` for attachments; the same pattern should be followed for whatever "my submissions" query change Phase 2 introduces.
- No new roles or resources are needed — this fits entirely within the existing `pre_auth` frontend resource and `SUPER_ADMIN`/`MEDICAL_REVIEWER`/`PROVIDER_STAFF` backend role set.

## 13. What not to delete yet

- Do not delete `EmailPreAuthController`, `PreAuthEmailRequestController`, `EmailPreAuthInbox.jsx`, or any email-intake code — legacy/transitional per the standing product clarification, kept until explicit approval to retire.
- Do not delete `PreApprovalsInbox.jsx` — it is dead code today, but it is the closest thing to a ready-made reviewer workspace and should be reviewed/adapted first, not discarded.
- Do not delete the disabled `PUT /{id}` legacy update endpoint/method in `PreAuthorizationController`/`PreAuthorizationService` without confirming nothing else depends on the broader field-set it supports (diagnosis code/description edits) — out of this audit's scope to decide.
- Do not remove the "Convert to Claim" disabled placeholder button in `PreApprovalView.jsx` without a decision on whether that's a planned Phase 2+ feature or dead UI.

## 14. Files likely to change (future implementation ticket, not this one)

- `frontend/src/routes/MainRoutes.jsx` — add `/pre-approvals/review` (and any workspace sub-route), add `/provider/pre-auth-inbox` to a menu-linked position.
- `frontend/src/menu-items/components.jsx` — add reviewer-inbox and provider-inbox entries.
- `frontend/src/pages/pre-approvals/PreApprovalsInbox.jsx` — review/adapt (currently unused/unverified in production), likely add request-info action.
- `frontend/src/pages/provider/PreAuthInbox.jsx` and `frontend/src/services/api/pre-approvals.service.js` — broaden the provider status query beyond APPROVED/ACKNOWLEDGED (also worth fixing the duplicate `getPending` export bug found incidentally in this file — the first definition is currently dead code, silently shadowed by a second one).
- `backend/src/main/java/com/waad/tba/modules/preauthorization/service/PreAuthorizationService.java` — add isolation-scoped queries for `getPendingInbox`/list/search, add isolation check to `startReview`.
- `backend/src/main/java/com/waad/tba/modules/preauthorization/repository/PreAuthorizationRepository.java` — add a provider-scoped query method mirroring `ClaimRepository.findByStatusInAndReviewerProviders`.

## 15. No-code-change confirmation

No source files, migrations, or configuration were created or modified during this ticket. The only new file is this report (`docs/preauthorization/PREAUTH-REVIEW-WORKFLOW-AUDIT-1-REPORT.md`).

## 16. No-push confirmation

Nothing was staged, committed, or pushed.

---

**PREAUTH-REVIEW-WORKFLOW-AUDIT-1 READY FOR REVIEW**
