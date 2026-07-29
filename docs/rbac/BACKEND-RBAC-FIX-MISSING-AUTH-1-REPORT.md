# BACKEND-RBAC-FIX-MISSING-AUTH-1 — Add Explicit Authorization to Unguarded Backend Controllers

**Status: READY FOR REVIEW.** Implemented locally. Not committed. Not pushed.

## 1. Audit finding being fixed

`BACKEND-RBAC-ENDPOINT-AUDIT-1-REPORT.md` §5/§7 found three controller clusters with **no `@PreAuthorize` (or any other authorization check) at all**, relying only on the global `anyRequest().authenticated()` — meaning any authenticated user of any role could call them:

1. `MemberDuplicateController` — `GET /reset-kinship` (raw SQL bulk `UPDATE`), `GET /`, `POST /merge` (member record merge). Rated **Critical**.
2. `KinshipMismatchController` — `GET /`, `POST /{id}/fix`, `POST /{id}/ignore`, `POST /bulk-fix`, `POST /bulk-ignore`. Rated **High**.
3. `PreAuthEmailRequestController` — `GET /`, `GET /{id}`, `DELETE /{id}` (delete of inbound pre-authorization email records). Rated **High**.

> **Product clarification (post-implementation):** the email-based pre-authorization intake this controller serves is **legacy/transitional**, not the intended future workflow. The future pre-authorization workflow is provider-portal submission + a reviewer inbox/workspace, modeled on the existing claims-review flow (provider submits → reviewer sees submitted requests → reviewer opens a review workspace → approve/reject/request-correction → provider sees status/correction notes) — not email intake. This does **not** change the fix below: the endpoint existed, was completely unguarded, and is still reachable, so hardening it remains valid safety work regardless of the workflow's long-term status. See §6 and §9 for how this reclassification affects scope and follow-ups.

A fourth, low-risk finding (`MedicalServiceLookupController`) was investigated but deliberately deferred — see §9.

## 2. Controllers changed

- `backend/src/main/java/com/waad/tba/modules/member/controller/MemberDuplicateController.java`
- `backend/src/main/java/com/waad/tba/modules/member/controller/KinshipMismatchController.java`
- `backend/src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthEmailRequestController.java`

No business logic, DTOs, services, or repositories were touched — only class-level and (for one controller) method-level `@PreAuthorize` annotations, plus an explanatory comment at each.

## 3. Exact endpoints protected

| Controller | Endpoint | New authorization |
|---|---|---|
| `MemberDuplicateController` | `GET /api/v1/system-settings/member-duplicates/reset-kinship` | `hasRole('SUPER_ADMIN')` (class-level) |
| `MemberDuplicateController` | `GET /api/v1/system-settings/member-duplicates` | `hasRole('SUPER_ADMIN')` (class-level) |
| `MemberDuplicateController` | `POST /api/v1/system-settings/member-duplicates/merge` | `hasRole('SUPER_ADMIN')` (class-level) |
| `KinshipMismatchController` | `GET /api/v1/system-settings/kinship-mismatches` | `hasRole('SUPER_ADMIN')` (class-level) |
| `KinshipMismatchController` | `POST /api/v1/system-settings/kinship-mismatches/{id}/fix` | `hasRole('SUPER_ADMIN')` (class-level) |
| `KinshipMismatchController` | `POST /api/v1/system-settings/kinship-mismatches/{id}/ignore` | `hasRole('SUPER_ADMIN')` (class-level) |
| `KinshipMismatchController` | `POST /api/v1/system-settings/kinship-mismatches/bulk-fix` | `hasRole('SUPER_ADMIN')` (class-level) |
| `KinshipMismatchController` | `POST /api/v1/system-settings/kinship-mismatches/bulk-ignore` | `hasRole('SUPER_ADMIN')` (class-level) |
| `PreAuthEmailRequestController` | `GET /api/preauthorization/email-requests` | `hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')` (class-level) |
| `PreAuthEmailRequestController` | `GET /api/preauthorization/email-requests/{id}` | `hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')` (class-level) |
| `PreAuthEmailRequestController` | `DELETE /api/preauthorization/email-requests/{id}` | `hasRole('SUPER_ADMIN')` (method-level override) |

## 4. Roles allowed before/after

| Endpoint cluster | Before | After |
|---|---|---|
| `MemberDuplicateController` (all 3) | Any authenticated user (any role) | `SUPER_ADMIN` only |
| `KinshipMismatchController` (all 5) | Any authenticated user (any role) | `SUPER_ADMIN` only |
| `PreAuthEmailRequestController` reads (list/view) | Any authenticated user (any role) | `SUPER_ADMIN`, `MEDICAL_REVIEWER` |
| `PreAuthEmailRequestController` delete | Any authenticated user (any role) | `SUPER_ADMIN` only |

This is a real, user-visible behavior change for `PROVIDER_STAFF`, `EMPLOYER_ADMIN`, `ACCOUNTANT`, `DATA_ENTRY`, and `FINANCE_VIEWER`: they previously could call all of the above and now get 403. This is the intended fix, not a regression — none of these roles had a legitimate frontend-driven reason to call these maintenance/data-correction endpoints (confirmed no frontend menu or route grants any of them access to these specific tools).

## 5. Why SUPER_ADMIN-only was chosen for the maintenance tools

Per the ticket's explicit instruction and the audit's own characterization: `MemberDuplicateController`'s `/merge` changes member identity relationships (which member records represent the same person) and `/reset-kinship` bulk-mutates a verification flag via a raw `UPDATE` statement bypassing the normal service/entity layer — both are destructive, hard-to-reverse, data-correction operations. `KinshipMismatchController`'s bulk-fix/bulk-ignore endpoints similarly alter member relationship/verification state in bulk. These are exactly the class of "maintenance tools" the frontend's own `system_settings` resource (SUPER_ADMIN-only in `ROLE_RESOURCE_ACCESS`) already models for every *other* maintenance tool (backups, monitoring, error logs) — restricting these two controllers to the same single role keeps the backend consistent with that existing frontend convention rather than inventing a new, narrower role list with no precedent.

## 6. PreAuthEmailRequest read/delete decision

The ticket's default guidance was followed exactly, since no product input was available to justify widening it: **reads** (`list`, `get`) are `SUPER_ADMIN` + `MEDICAL_REVIEWER`, since inbound pre-authorization emails are plausibly part of a reviewer's triage workflow (consistent with `MEDICAL_REVIEWER` already having read access to the related `EmailPreAuthController`/`PreAuthDashboardController` in the existing codebase, per the audit). **Delete** defaults to `SUPER_ADMIN` only, since deleting a raw inbound email/pre-auth intake record is a destructive, audit-trail-relevant action with no confirmed business requirement for reviewers to perform it. If the real workflow requires `MEDICAL_REVIEWER` to delete these records, that is a one-line role-list change for a follow-up ticket with explicit product confirmation — not assumed here.

**Legacy/transitional status (post-implementation product clarification):** the email-intake workflow this controller (and the related `EmailPreAuthController`) serves is legacy/transitional, not the intended future pre-authorization workflow — see §1. This role decision was made purely to close the unguarded-endpoint gap on code that exists and is reachable today; it is not an endorsement of email intake as the long-term design, and should not be read as a basis for building further features on top of it. No new authorization work should be planned for this controller beyond what's in this ticket; future pre-authorization access-control work belongs on the provider-portal-submission + reviewer-inbox flow instead (§9).

## 7. Tests added

Three new test classes, one per fixed controller, all genuinely exercising Spring Security's `@PreAuthorize` method-security interceptor (not just calling the controller method directly or mocking the service layer):

- `backend/src/test/java/com/waad/tba/modules/member/controller/MemberDuplicateControllerAuthorizationTest.java` — `providerStaff_cannotMergeMembers_returns403`, `financeViewer_cannotResetKinship_returns403`, `medicalReviewer_cannotMergeMembers_returns403`, `superAdmin_canAccess_or_reachesServiceLayer`.
- `backend/src/test/java/com/waad/tba/modules/member/controller/KinshipMismatchControllerAuthorizationTest.java` — `providerStaff_cannotBulkFixKinshipMismatch_returns403`, `employerAdmin_cannotIgnoreKinshipMismatch_returns403`, `superAdmin_canAccess_or_reachesServiceLayer`.
- `backend/src/test/java/com/waad/tba/modules/preauthorization/controller/PreAuthEmailRequestControllerAuthorizationTest.java` — `providerStaff_cannotListEmailRequests_returns403`, `medicalReviewer_canListEmailRequests_or_reachesServiceLayer`, `medicalReviewer_cannotDeleteEmailRequest_returns403`, `superAdmin_canDeleteEmailRequest_or_reachesServiceLayer`.

**Why these aren't `@WebMvcTest`** (the ticket's stated preference), and what was used instead: this repository has a stray debug/scratch class, `backend/src/test/java/com/waad/tba/CheckLogic.java` (a `@SpringBootApplication`-annotated class with a `main()` method, clearly a one-off manual-run debugging tool, not a real test), sitting in the test source tree. Its presence makes Spring Boot's `@SpringBootConfiguration` auto-detection ambiguous for **every** `@WebMvcTest`/`@SpringBootTest` in the project (confirmed: this is the root cause the audit's `ClaimLifecycleIntegrationTest` finding was also brushing up against, and it is why **zero** `@WebMvcTest` existed anywhere in this codebase before this ticket — the audit's own §8 observation). Working around it by explicitly pointing `@ContextConfiguration` at the real application class made `@WebMvcTest` pull in every `Filter` bean app-wide (JWT/session/rate-limit/monitoring/maintenance filters), which cascaded into needing a `JWT_SECRET`, a `SystemErrorLogService` bean, and likely a real `DataSource` beyond that — far heavier than this ticket's scope, and explicitly the kind of situation the ticket's own fallback clause anticipated ("If MockMvc setup is too heavy due to dependencies... document the limitation clearly").

Instead, each test uses a **minimal, explicit Spring context** — `@ExtendWith(SpringExtension.class)` + `@ContextConfiguration(classes = { TheController.class, MethodSecurityConfig.class })` — containing only the controller under test and the app's real `@EnableMethodSecurity` configuration, with the controller's constructor dependencies replaced by `@MockitoBean`. `MockMvcBuilders.standaloneSetup(controller)` then builds a MockMvc instance directly around the (now method-security-proxied) controller bean. This is not a workaround that skips real enforcement: the tests genuinely trip the same `AuthorizationManagerBeforeMethodInterceptor`/`PreAuthorizeAuthorizationManager` that protects the real endpoints in production (confirmed by first running the tests *without* the fix — they failed with `AuthorizationDeniedException` propagating exactly as expected — before adding a small `@RestControllerAdvice` translating that exception to HTTP 403, since standalone MockMvc has no `ExceptionTranslationFilter` to do that translation the way the full servlet filter chain does in production). A regression that removes or typos a `@PreAuthorize` string will fail these tests. `CheckLogic.java` itself was **not** touched or deleted — it is unrelated pre-existing repo state, out of this ticket's scope to clean up, though it is now a documented, load-bearing blocker for any future `@WebMvcTest` work in this codebase (worth its own tiny cleanup ticket).

## 8. Test/build results

- **Targeted (this ticket's tests only)**: `mvn -o test -DskipTests=false -Dtest="MemberDuplicateControllerAuthorizationTest,KinshipMismatchControllerAuthorizationTest,PreAuthEmailRequestControllerAuthorizationTest"` → **11/11 pass**, 0 failures, 0 errors.
- **Broader pattern** (`-Dtest="*Rbac*,*Security*,*MemberDuplicate*,*KinshipMismatch*,*PreAuthEmail*"`): matched the same 3 classes (no other `*Rbac*`/`*Security*`-named test class exists in the suite) → same **11/11 pass**.
- **Full suite**: `mvn -o test -DskipTests=false` → completed in ~50s (not too slow to run), **270 tests, 18 failures, 5 errors**. Every failing/erroring test was inspected by name and confirmed **pre-existing and unrelated** to this ticket's 3 changed controllers: `DropIndexTest` (DB-migration-dependent), `ClaimMapperPricingContractTest` (1 test, NPE unrelated to RBAC), `ClaimLifecycleIntegrationTest` (context/DB-dependent integration test), `CostCalculationServiceTest` (6 failures, financial-calculation assertions), `CoverageEngineServiceTest` (6 failures, coverage-engine assertions, some showing `?????` — a console/locale encoding artifact on Arabic error messages, not a real logic bug introduced here), `DoctorNotesBenefitCapsRegressionTest` (5 failures/errors, benefit-cap financial logic + Mockito `PotentialStubbingProblem`), `MemberExcelImportServiceTest` (3 failures, Excel-import parsing). None of these reference `MemberDuplicateController`, `KinshipMismatchController`, `PreAuthEmailRequestController`, or any file this ticket touched — confirmed via `git status`/`git diff` scope (only the 3 controllers + 3 new test files changed). Note: this project runs with `<skipTests>true</skipTests>` by default (must be overridden with `-DskipTests=false`), which is consistent with an accumulated backlog of pre-existing, not-regularly-run test failures unrelated to this change.

## 9. Unresolved low-risk endpoint: `MedicalServiceLookupController`

Investigated as instructed rather than guessed. `GET /api/v1/medical-services/lookup` is called (via `frontend/src/services/api/medical-services.service.js`'s `lookupMedicalServices()`, used by `MedicalServiceSelector`/`CategoryServicePicker` components) from exactly 3 frontend contexts: `ContractPriceEditDialogs.jsx` (provider-contracts pricing, backend-accessible to `SUPER_ADMIN`/`ACCOUNTANT`), `ExceptionEditDialog.jsx` (classification exceptions, `SUPER_ADMIN`/`MEDICAL_REVIEWER` context), and `BenefitPolicyRulesTab.jsx` (benefit policy rules, largely `SUPER_ADMIN`-only backend-side). No usage was found in any provider-portal self-service page (`pages/provider/**`), so it is **not** part of the provider claim-submission flow. However, because `ProviderContractController`'s backend already grants `ACCOUNTANT` write access to contract pricing (§4 of the audit) even though the frontend menu doesn't currently surface `provider_contracts` to `ACCOUNTANT`, a fully confident minimal role list couldn't be pinned down without a product decision on whether that latent `ACCOUNTANT` access path is intentional. Per the ticket's own escape hatch ("If unclear, leave it for a separate low-risk ticket and document"), **no `@PreAuthorize` was added to this controller in this ticket.** Recommended for a follow-up (`BACKEND-RBAC-MEDICAL-SERVICE-LOOKUP-1` or similar): `hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'ACCOUNTANT', 'DATA_ENTRY')`, pending confirmation of the `ACCOUNTANT`/provider-contracts question.

## 10. Backend authorization caveat

Unchanged from `BACKEND-RBAC-ENDPOINT-AUDIT-1-REPORT.md`: this fix closes three real, confirmed gaps, but it does not constitute a full re-audit. The broader `FRONTEND_BACKEND_MISMATCH` findings (`EMPLOYER_ADMIN`/`employers`, `DATA_ENTRY`/`medical_catalog`, `benefit_policies` rules) and the test-coverage gap for the other ~15 endpoint families remain open, tracked as separate tickets in the audit report's §9 (items 2–7), explicitly not implemented here per this ticket's scope ("Do not widen DATA_ENTRY / EMPLOYER_ADMIN access in this ticket... Do not implement the broader mismatch tickets").

**Recorded follow-up direction (product clarification, not implemented here):** the email-based pre-authorization intake (`PreAuthEmailRequestController`, `EmailPreAuthController`) is legacy/transitional (§1, §6) — do not delete it now without explicit approval, and do not build new major features around it. The intended future pre-authorization workflow is provider-portal submission + a reviewer inbox/workspace, modeled on the existing claims-review flow (provider submits → reviewer inbox shows submitted requests → reviewer opens a review workspace → approve/reject/request-correction → provider sees status/correction notes). A future ticket (e.g. `PREAUTH-REVIEWER-WORKSPACE-1`) should design/implement that flow's authorization model on its own terms — new controllers/endpoints, not retrofitted onto the email-intake controllers fixed here.

## 11. Files changed

- `backend/src/main/java/com/waad/tba/modules/member/controller/MemberDuplicateController.java`
- `backend/src/main/java/com/waad/tba/modules/member/controller/KinshipMismatchController.java`
- `backend/src/main/java/com/waad/tba/modules/preauthorization/controller/PreAuthEmailRequestController.java`
- `backend/src/test/java/com/waad/tba/modules/member/controller/MemberDuplicateControllerAuthorizationTest.java` (new)
- `backend/src/test/java/com/waad/tba/modules/member/controller/KinshipMismatchControllerAuthorizationTest.java` (new)
- `backend/src/test/java/com/waad/tba/modules/preauthorization/controller/PreAuthEmailRequestControllerAuthorizationTest.java` (new)

No database schema, migrations, frontend code, `ROLE_RESOURCE_ACCESS`, or business logic were changed.

## 12. No-push confirmation

Nothing was pushed. Nothing was committed — awaiting explicit approval per standing rules.

---

**BACKEND-RBAC-FIX-MISSING-AUTH-1 READY FOR REVIEW**
