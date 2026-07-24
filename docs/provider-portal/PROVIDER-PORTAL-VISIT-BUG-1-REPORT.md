# PROVIDER-PORTAL-VISIT-BUG-1 — Fix GET /visits/{id} Read-Only Audit Failure

## 1. Branch

`recovery/provider-portal-claim-submission` (built on top of local commit `d0e57fd feat(provider): restore claim submission workspace`, which is not disturbed by this change).

## 2. Bug confirmation

Confirmed exactly as described in the ticket. `VisitService.findById()` was annotated `@Transactional(readOnly = true)` and, inside that same method body, called `auditLogService.createAuditLog(...)` to record a `VIEW` audit entry.

## 3. Exact root cause

`AuditLogService.createAuditLog(...)` is itself annotated `@Transactional` (default propagation `REQUIRED`). Called from within `VisitService.findById()`'s `readOnly = true` transaction, it does **not** open a new transaction — it joins the existing one. Spring propagates the `readOnly` hint to the physical transaction/JDBC connection (`Connection.setReadOnly(true)`), and PostgreSQL enforces this at the protocol level: any write attempted inside a read-only transaction is rejected. The `INSERT INTO audit_logs` therefore could throw, surfacing as an HTTP 500 on `GET /visits/{id}`.

No other method in `VisitService` has this pattern — `findAll()`, `search()`, `findAllPaginated()`, and `count()` are all legitimately read-only (no audit or other write call inside them).

## 4. Exact file changed

`backend/src/main/java/com/waad/tba/modules/visit/service/VisitService.java` — one line only.

## 5. Exact code change

```diff
-    @Transactional(readOnly = true)
+    @Transactional
     public VisitResponseDto findById(Long id) {
```

No other lines in the method were touched. Business behavior (authorization checks, provider lookup, audit content, response mapping) is unchanged — only the transaction's write-capability was corrected to match what the method actually does.

## 6. Tests / compile result

- `git diff --check` — clean, no whitespace errors.
- `mvn -o compile` — `BUILD SUCCESS`.
- Focused test run (`VisitServiceTest` + the 6 Provider/Claims regression classes named in the ticket), forced on with `-DskipTests=false` (project defaults `skipTests=true`):

```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
- ClaimMapperPricingContractTest: 2
- ClaimReferenceServiceTest: 8
- ClaimReviewServiceTest: 13
- ClaimServiceLineDecisionTest: 11
- ClaimServiceProviderStatusTest: 3
- ClaimServiceReviewerIsolationTest: 4
- VisitServiceTest: 4
```
(The `ERROR`-level log lines seen for `ClaimReviewServiceTest` are expected error-path test scenarios — "Claim not found" assertions — not failures.)

No new unit test was added for the transaction annotation itself: `VisitServiceTest` is a pure Mockito unit test with all collaborators mocked, so it cannot observe Spring's proxy-level transactional/read-only behavior or PostgreSQL's write rejection — that behavior is only observable at the integration/live level, which is covered in §7 below. Adding one would require a `@SpringBootTest` against a real transactional context, which is disproportionate for a one-line annotation fix and was judged not "practical" per the ticket's own qualifier.

## 7. API smoke result

Backend rebuilt via `.\waad.ps1 rebuild backend` (fresh image, health check passed). Logged in as provider `dar` (provider 1, "دار الشفاء"):

- `GET /api/v1/visits/16` → **200 OK** (was reproducibly a 500 before the fix, by inspection of the code path — the audit-log insert is unconditional on every `findById` call).
- Repeated call → still **200 OK** (checked twice consecutively, ruling out a one-off).
- Backend logs confirm the audit INSERT actually executed inside the request: `... insert audit_logs (...) ...` followed by `Audit log created: VIEW by dar`, no exception.
- Verified directly in Postgres (`tba_waad_system.audit_logs`): a `VIEW` row for `entity_type='VISIT', entity_id=16, username='dar'` is persisted with the correct timestamp — proving the audit write survives, not just that no exception was thrown.

One flaky-cookie retry was needed for a couple of calls (documented pre-existing session-cookie flakiness from earlier sessions, unrelated to this change) — resolved by re-logging in immediately before the dependent call, consistent with prior sessions' findings.

## 8. Confirmation Provider Portal claim submission still works

`GET /visits/{id}` is exactly the call the Provider Portal claim submission workspace depends on to load visit/member context before submitting a claim. With the fix, this call returns 200 reliably. No Provider Portal frontend or `ClaimService`/`ClaimMapper` files were touched by this change (git status confirms only `VisitService.java` is modified), so the DATA-1/STATUS-1 behavior restored in `d0e57fd` is untouched and unaffected.

## 9. Confirmation Claims Review still works

`GET /api/v1/claims/inbox/pending?providerId=1` (reviewer inbox) tested live as `reviewer_test` → **200 OK**, returning claims with line-level `reviewerDecision` (e.g. `REJECTED`) intact. Combined with the 45/45 passing regression tests including `ClaimReviewServiceTest`, `ClaimServiceLineDecisionTest`, and `ClaimServiceReviewerIsolationTest`, Claims Review is unaffected by this change.

## 10. Confirmation no unrelated modules changed

`git status --short` shows only `VisitService.java` modified. No changes to Visit lifecycle/creation/update/delete logic, eligibility/benefit-policy logic, PreAuthorization, settlements, reports engine, taxonomy/classification, monitoring/backup, env files, or migrations. `.recovery/` and the pre-existing untracked WIP docs remain untouched and unstaged.

## 11. Confirmation no push was done

No `git commit` or `git push` was performed in this phase. Working tree currently shows `VisitService.java` as a local, uncommitted modification (`git status --short` → ` M backend/.../VisitService.java`), awaiting explicit commit approval per standing process.

## 12. Rollback plan

Trivial single-line revert: restore `@Transactional(readOnly = true)` on `VisitService.findById()` (or `git checkout -- backend/src/main/java/com/waad/tba/modules/visit/service/VisitService.java` since the change is not yet committed). No migration, no data change, no dependent code relies on the new annotation — rollback carries zero side effects.

---

**PROVIDER-PORTAL-VISIT-BUG-1 READY FOR REVIEW**
