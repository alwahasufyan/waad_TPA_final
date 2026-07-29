# PREAUTH-REVIEW-WORKFLOW-1 — Wire Up the Existing Pre-Authorization Reviewer Workflow

**Status: READY FOR REVIEW.** Implemented locally. Not committed. Not pushed.

## 1. What was implemented

Per `PREAUTH-REVIEW-WORKFLOW-AUDIT-1-REPORT.md`'s finding that the backend was already ~90% built and the frontend was the gap, this ticket did not rebuild anything — it wired up what already existed and closed the one real backend gap the audit found:

1. **Reviewer inbox routed and menu-linked**: `/pre-approvals/review` now renders the existing `PreApprovalsInbox.jsx` (previously a real, working component that was simply never imported into `MainRoutes.jsx` or the menu — dead code). Menu entry "مراجعة الموافقات المسبقة" added, resource `pre_auth`, positioned parallel to `claims-review-inbox`.
2. **Request-info/"request correction" action added** to the reviewer inbox — new dialog, label "طلب استكمال بيانات", calling the already-existing backend endpoint `POST /api/v1/pre-authorizations/{id}/request-info`.
3. **Legacy email inbox left untouched** — `/pre-approvals/email-inbox` still exists, still routed, still menu-linked (now second in the menu, after the real reviewer inbox), not deleted, not expanded.
4. **Provider-side inbox broadened** — `/provider/pre-auth-inbox` now shows all of a provider's own submitted pre-authorizations across every status (previously only `APPROVED`/`ACKNOWLEDGED`, and — a bug found and fixed as a side effect of this exact broadening — it was querying the wrong endpoint entirely, see §9). Added to the Provider Portal menu (previously unlinked).
5. **Backend reviewer-provider isolation gap closed** for the two places the audit identified: the reviewer inbox query (`getPendingInbox`) and `startReview` (the one decision-adjacent method that had no isolation check at all, unlike every other decision method).

**Explicitly not done**, per the ticket's scope: claim↔pre-authorization enforcement (`CLAIM-PREAUTH-ENFORCEMENT-1`, separate ticket), convert-to-claim, any status changes, any claim financial logic.

## 2. Frontend routing/menu

| Route | Component | Change |
|---|---|---|
| `/pre-approvals/review` | `PreApprovalsInbox.jsx` | **New route.** Component already existed; now imported and wired in `MainRoutes.jsx`, guarded `resource="pre_auth"` (same as every other `/pre-approvals/*` route). |
| `/pre-approvals/email-inbox` | `EmailPreAuthInbox.jsx` | **Unchanged.** Still routed, still legacy/transitional. |
| `/provider/pre-auth-inbox` | `PreAuthInbox.jsx` | **Behavior changed** (broadened query, see §5), route itself unchanged. |

Menu (`frontend/src/menu-items/components.jsx`):
- Added `preauth-review-inbox` ("مراجعة الموافقات المسبقة") under "المطالبات والموافقات", positioned immediately before the existing `email-preauth-requests` entry — so the real workflow appears first, legacy second.
- Added `provider-preauth-inbox` ("طلبات الموافقة المسبقة") to the Provider Portal menu group, after "المستندات" and before the reports divider.

## 3. Request-info / request-correction action

Added to `PreApprovalsInbox.jsx`:
- New icon button (⟲, `AssignmentReturn`) alongside Approve/Reject, visible for `PENDING`/`UNDER_REVIEW` rows (same visibility rule as Approve/Reject).
- New dialog: required textarea (max 1000 chars, matching the backend's `RequestPreAuthorizationInfoRequest.notes` `@Size(max = 1000)` constraint), label "طلب استكمال بيانات".
- New service method `preApprovalsService.requestInfo(id, notes)` → `POST /pre-authorizations/{id}/request-info`, added to `pre-approvals.service.js`.
- Status chip config extended with `NEEDS_CORRECTION` ("بحاجة لاستكمال بيانات") and `APPROVAL_IN_PROGRESS` ("جارِ الاعتماد") — both existed in the backend enum but had no frontend label before (would have rendered the raw enum string or fallen through to the `PENDING` default).

No backend change was needed for this — `POST /{id}/request-info` already existed, already correctly role-gated (`SUPER_ADMIN`, `MEDICAL_REVIEWER`), already transitions to `NEEDS_CORRECTION` via `PreAuthorizationService.requestInformation()`. Purely a frontend wiring gap, exactly as the audit found.

## 4. Legacy email inbox — confirmed untouched

`EmailPreAuthController`, `EmailPreAuthService`, `PreAuthEmailRequestController`, `EmailPreAuthInbox.jsx`, `email-preauth.service.js` — zero changes. Still routed at `/pre-approvals/email-inbox`, still menu-linked (now second, not first, in menu order — the only menu change affecting it, and even that was additive, not a removal). Its direct-to-`APPROVED` "approve" shortcut is unchanged, per the ticket's explicit "do not implement convert-to-claim... do not change statuses" scope.

## 5. Provider-side broadening

`PreAuthInbox.jsx` (component `ProviderPreAuthInbox`) was rewritten to:
- Resolve the current provider from `useAuth()`'s `user.providerId` (the same pattern already used elsewhere in the provider portal, e.g. `useProviderClaimSubmission.js`).
- Call the new service method `preApprovalsService.getByProvider(providerId, {...})` → `GET /pre-authorizations/provider/{providerId}` — an endpoint that already existed (`getPreAuthorizationsByProvider`), just never used by this page.
- Render a single table of all the provider's submissions with status chips covering all 9 statuses (`PENDING`, `UNDER_REVIEW`, `NEEDS_CORRECTION`, `APPROVAL_IN_PROGRESS`, `APPROVED`, `ACKNOWLEDGED`, `REJECTED`, `USED`, `EXPIRED`, `CANCELLED`) and the reviewer's comment column (surfaces `NEEDS_CORRECTION` notes directly).
- Keep the "تم الاطلاع" (acknowledge) action, now shown only on `APPROVED` rows within the single table rather than a separate tab.

## 6. Backend: reviewer-provider isolation

**`getPendingInbox(Pageable)`** (`PreAuthorizationService.java`) — previously called `preAuthorizationRepository.findByStatusIn(...)` unconditionally, meaning a `MEDICAL_REVIEWER` calling `/inbox/pending` saw every provider's pending/under-review items. Now:
```java
User currentUser = authorizationService.getCurrentUser();
if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
    List<Long> allowedProviderIds = reviewerIsolationService.getAllowedProviderIds(currentUser);
    preAuths = allowedProviderIds.isEmpty()
            ? Page.empty(pageable)
            : preAuthorizationRepository.findByStatusInAndReviewerProviders(allowedProviderIds, inboxStatuses, pageable);
} else {
    preAuths = preAuthorizationRepository.findByStatusIn(inboxStatuses, pageable);
}
```
`SUPER_ADMIN` (and anyone `isSubjectToIsolation` returns `false` for) is unaffected — same unscoped query as before.

New repository method `PreAuthorizationRepository.findByStatusInAndReviewerProviders(providerIds, statuses, pageable)`, mirroring `ClaimRepository.findByStatusInAndReviewerProviders` exactly (same join-fetch pattern, same `active = true` filter).

**`startReview(Long id, String reviewedBy)`** — previously the one decision-adjacent method with zero authorization beyond the controller's role check; any `MEDICAL_REVIEWER` could start-review any provider's `PENDING` item regardless of assignment. Now calls the same private `assertReviewerAccess(preAuth)` helper every other decision method (`approvePreAuthorization`, `approvePartial`, `requestInformation`, `requestApproval`, `rejectPreAuthorization`, `reviewPreAuth`) already used — added as the very first check, before the active/status guards, so an unassigned reviewer's request never reaches the status-transition logic at all.

**Not changed**: `getAllPreAuthorizations`, `getPreAuthorizationsByProvider`, `getPreAuthorizationsByStatus`, `search` — the audit flagged these too, but the ticket's instruction was specifically "pending/list/inbox query" (i.e. the query backing the reviewer inbox, `getPendingInbox`) and `startReview`; widening the other list endpoints is a larger, separate change not requested here and not needed for this workflow to function correctly (they aren't called by the reviewer inbox).

## 7. Tests added

`backend/src/test/java/com/waad/tba/modules/preauthorization/service/PreAuthorizationServiceReviewerIsolationTest.java` (new, 6 tests, Mockito unit tests calling the service directly — same style as `ClaimServiceReviewerIsolationTest`, which this mirrors):

- `getPendingInbox_reviewerAssignedToProviders_usesProviderScopedQuery` — asserts `findByStatusInAndReviewerProviders` is called with the reviewer's exact assigned-provider list, and the unscoped `findByStatusIn` is never called.
- `getPendingInbox_reviewerWithNoAssignedProviders_returnsEmptyWithoutQuerying` — an unassigned reviewer gets an empty page without hitting either query method (matches `getAllowedProviderIds()`'s documented "empty, not all" behavior).
- `getPendingInbox_superAdmin_seesAllProviders_notScoped` — confirms `SUPER_ADMIN` still gets the unscoped query.
- `startReview_reviewerAssignedToProvider_succeeds` — status transitions `PENDING → UNDER_REVIEW`, `validateReviewerAccess` is called with the correct provider ID, save happens.
- `startReview_reviewerNotAssignedToProvider_throwsAccessDenied_beforeStatusChange` — `AccessDeniedException` propagates, and — critically — the entity's status is asserted to remain `PENDING` and `save()` is asserted `never()` called, confirming the check happens *before* any mutation, not after.
- `startReview_superAdmin_bypassesIsolation_succeeds` — confirms `SUPER_ADMIN` bypass still works and `validateReviewerAccess` is never invoked for that path.

## 8. Test/build results

- **Targeted**: `mvn -o test -DskipTests=false -Dtest="PreAuthorizationServiceReviewerIsolationTest,MemberDuplicateControllerAuthorizationTest,KinshipMismatchControllerAuthorizationTest,PreAuthEmailRequestControllerAuthorizationTest"` → **17/17 pass** (6 new + 11 from the prior `BACKEND-RBAC-FIX-MISSING-AUTH-1` ticket, run together to confirm no interference).
- **Broader pre-auth sweep**: `mvn -o test -DskipTests=false -Dtest="*PreAuth*"` → **33/33 pass**, including the pre-existing `PreAuthorizationServiceDecisionTest` (15 tests covering approve/reject/approve-partial/async-processing) — confirms the `getPendingInbox`/`startReview` changes didn't regress any existing decision-flow behavior.
- `mvn -o compile` — clean, no errors, only pre-existing unrelated warnings.
- `npx vite build` (from `frontend/`) — **succeeded, exit 0**. Confirmed `PreApprovalsInbox` and the rewritten `PreAuthInbox` both produce real lazy-loaded chunks in `dist/assets/` (`PreApprovalsInbox-*.js`, `PreAuthInbox-*.js`), proving the new route is genuinely reachable, not just syntactically valid.
- `npx eslint` on all 5 changed frontend files (`PreApprovalsInbox.jsx`, `PreAuthInbox.jsx`, `pre-approvals.service.js`, `MainRoutes.jsx`, `menu-items/components.jsx`) — **0 errors**, 38 warnings, all pre-existing `prettier/prettier` formatting and `no-unused-vars` findings unrelated to this ticket's edits (e.g. `Grid`/`MedicalIcon`/`approvedAmount`/`pollError` in `PreApprovalsInbox.jsx` were already unused before this ticket; `EmployerDashboard` etc. in `MainRoutes.jsx` are pre-existing dead imports elsewhere in that file).
- **Full backend suite was not run** — this project defaults to `<skipTests>true</skipTests>`, and per the prior `BACKEND-RBAC-FIX-MISSING-AUTH-1` ticket's documented findings, the full suite has a pre-existing backlog of ~18 failures/5 errors entirely unrelated to pre-authorization (financial-calculation and coverage-engine assertion tests). The targeted and `*PreAuth*` runs above are the relevant subset for this change and both pass cleanly; re-running the full suite would only re-confirm the same pre-existing, already-documented unrelated failures.

## 9. Bug found and fixed as a side effect of Phase §5's broadening

While rewriting `PreAuthInbox.jsx`, direct inspection of the backend confirmed the previous implementation was calling the wrong endpoint entirely: `getInbox('approved'|'acknowledged', ...)` hit `GET /pre-authorizations/inbox/pending?status=...`, but `PreAuthorizationController.getPendingInbox()` **does not accept a `status` query parameter at all** (verified by reading the method signature) — the backend's `getPendingInbox()` service method always returns `PENDING`/`UNDER_REVIEW` items regardless of any client-supplied status filter. So the provider inbox was previously silently displaying `PENDING`/`UNDER_REVIEW` items mislabeled under "موافق عليه" (Approved) and "تم الاطلاع" (Acknowledged) tabs — not the audit's originally-assumed "only shows approved/acknowledged" behavior, but an actual data-correctness bug. This is fixed as a direct consequence of switching to `getByProvider` (§5), not a separate change.

## 10. RBAC/data-scope requirements — confirmation

- No new roles or resources introduced. `/pre-approvals/review` uses the existing `pre_auth` frontend resource (already granted to `SUPER_ADMIN`+`MEDICAL_REVIEWER` in `ROLE_RESOURCE_ACCESS`); `/provider/pre-auth-inbox` uses the existing `provider_portal` resource.
- `ROLE_RESOURCE_ACCESS` was not modified.
- Backend role gates on `request-info`, `start-review`, and the inbox endpoint were not changed — only the *data returned* by the inbox query and the *access check* on `startReview` were tightened, matching exactly what was already enforced everywhere else in the same service.
- `GET /pre-authorizations/provider/{providerId}` (used by the new provider-side page) has no server-side ownership check (a `PROVIDER_STAFF` caller could in principle pass a different provider's ID) — this is a **pre-existing gap noted in the original audit's data-scope discussion, not introduced or fixed by this ticket**. The frontend always supplies the caller's own `user.providerId`, consistent with how this endpoint is used elsewhere; hardening the endpoint itself to reject a mismatched ID is out of this ticket's scope (would be a `BACKEND-RBAC-*`-style fix, not a workflow-wiring one) and is flagged here for visibility, not silently left undocumented.

## 11. What was not touched

- No statuses added, removed, or renamed.
- No claim financial logic touched (`ClaimService`, `CostCalculationService`, `CoverageEngineService` — zero changes).
- No claim↔pre-authorization enforcement added (`CLAIM-PREAUTH-ENFORCEMENT-1` remains a separate, unimplemented follow-up).
- No convert-to-claim implementation — the disabled placeholder button in `PreApprovalView.jsx` is unchanged.
- `PreApprovalsList.jsx` and `PreApprovalView.jsx` (the existing read-only list/detail pages) are unchanged — the new reviewer workspace is additive at `/pre-approvals/review`, not a replacement.
- Backend `getAllPreAuthorizations`/`getPreAuthorizationsByStatus`/`search` isolation gaps remain open, as documented in §6.

## 12. Files changed

**Backend:**
- `backend/src/main/java/com/waad/tba/modules/preauthorization/service/PreAuthorizationService.java` — `getPendingInbox()` reviewer-scoped, `startReview()` gained `assertReviewerAccess()`.
- `backend/src/main/java/com/waad/tba/modules/preauthorization/repository/PreAuthorizationRepository.java` — new `findByStatusInAndReviewerProviders()`.
- `backend/src/test/java/com/waad/tba/modules/preauthorization/service/PreAuthorizationServiceReviewerIsolationTest.java` (new).

**Frontend:**
- `frontend/src/routes/MainRoutes.jsx` — new `/pre-approvals/review` route + lazy import.
- `frontend/src/menu-items/components.jsx` — new `preauth-review-inbox` and `provider-preauth-inbox` menu entries.
- `frontend/src/pages/pre-approvals/PreApprovalsInbox.jsx` — added request-info dialog/action, `NEEDS_CORRECTION`/`APPROVAL_IN_PROGRESS` status labels.
- `frontend/src/pages/provider/PreAuthInbox.jsx` — rewritten to show all statuses via `getByProvider`.
- `frontend/src/services/api/pre-approvals.service.js` — added `requestInfo()` and `getByProvider()`; removed a duplicate `getPending` export (dead code, silently shadowed the richer of the two definitions — found and fixed while editing this exact file for the above additions).

No database schema, migrations, `ROLE_RESOURCE_ACCESS`, or claim-side files were changed.

## 13. No-push confirmation

Nothing was pushed. Nothing was committed — awaiting explicit approval per standing rules.

---

**PREAUTH-REVIEW-WORKFLOW-1 READY FOR REVIEW**
