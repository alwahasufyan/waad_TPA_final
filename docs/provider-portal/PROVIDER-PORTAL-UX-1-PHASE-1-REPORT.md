# PROVIDER-PORTAL-UX-1 — Phase 1 Report: Critical Functional Fixes (Revision 2, approved)

**Status:** Implemented, tested, owner-approved. Backend compiles clean (`mvn -o compile`, 0 errors). Backend unit tests: 4/4 pass. Frontend unit tests: 5/5 pass. Frontend `vite build` succeeds. Frontend lint on touched files: 0 errors (only pre-existing prettier formatting warnings, unchanged count).
**Scope touched:** error-message pipeline (backend + provider frontend) + structured error logging. No navigation, workspace, performance, attachment, or messaging work started, per instruction.

**Revision 2 — owner feedback incorporated:**
1. §1 — `GlobalExceptionHandler` reworked into a pure fallback layer; it no longer authors business-specific Arabic text.
2. §2 — structured logging added for every exception on the claims path.
3. §3 — two tests added before commit, per instruction.

---

## 0. D5 (draft/autosave) — re-verified, correcting the Phase 0 guess

Read `ProviderClaimsSubmission.jsx` end to end for the draft path:
- The "autosave" every 1200ms (`localDraftStorageKey` effect, ~L702–745) writes to **`localStorage` only** — it never calls the backend. It cannot create duplicate server-side drafts; it's a client-side recovery snapshot, cleared on successful submit (`localStorage.removeItem(localDraftStorageKey)`, L1508).
- The **real** server draft is only created by `handleSubmit` (~L1465–1533): it uses `activeClaimId` to decide `POST /claims` (first save) vs `PUT /claims/{id}/data` (every save after) — repeated "حفظ كمسودة" clicks update the same claim, they do not create new ones.
- Both submit buttons are `disabled={submitting || ...}`, so a double-click while a request is in flight is blocked at the UI level.

**Conclusion: D5 is not a defect.** No duplicate-draft risk found. Downgraded to "verified clean" — no code change needed.

---

## 1. D4 (generic errors) — root cause, fix, and the fallback-only architecture

### Root cause (confirmed, not what Phase 0 guessed)
Phase 0 assumed the frontend toaster was the problem. Reading the real pipeline showed the frontend already prefers `messageAr`/`message` from the backend in most places. The actual defects were two independent things:
- **Backend:** the handlers for `PolicyNotActiveException`, `CoverageValidationException`, `ClaimStateTransitionException`, `BusinessRuleException`, `ResourceNotFoundException`, and `IllegalStateException` never populated `messageAr` at all — these are exactly the exception types thrown on the claim golden path (e.g. `ClaimService.java:757` *"Cannot submit claim in %s status..."*).
- **Frontend:** `ProviderClaimsSubmission.jsx` (3 call sites) and `ProviderPreApprovalSubmission.jsx` (1 call site) read `err.response?.data?.message` directly, bypassing `messageAr` even when the backend sent a good one.

### Architecture, revised per owner instruction
The owner's rule: **the service that throws the exception owns the Arabic wording; the handler only falls back when none was given.** No `instanceof`-driven message crafting allowed to accumulate in `GlobalExceptionHandler`.

Implemented exactly that:
- `BusinessRuleException` (base class for `PolicyNotActiveException`, `CoverageValidationException`, `ClaimStateTransitionException`) and `ResourceNotFoundException` each gained an **optional** `messageAr` field + constructor overload + `getMessageAr()`. Existing throw-sites are unaffected (they simply don't set it yet, and get the fallback below) — services can adopt it incrementally, one throw-site at a time, without a big-bang rewrite.
- `GlobalExceptionHandler` now has exactly **one** resolution rule, used identically for every business-exception handler:
  ```
  ex.getMessageAr() if set (service-authored)
      → else the raw message if it already contains Arabic script (a lot of existing throw-sites already write Arabic directly)
      → else ONE generic constant: "تعذر تنفيذ العملية. الرجاء المحاولة مرة أخرى أو التواصل مع الدعم الفني."
  ```
  This lives in a single private `build(status, code, message, exceptionMessageAr, request, details)` overload — no per-exception-type literal strings inside the handler, so it will not grow into a wall of `if (ex instanceof ...)` as more exception types are added.
- `IllegalStateException` / `EntityNotFoundException` (generic JDK/JPA exceptions, not domain `BusinessException`s) use the same mechanical rule minus the service-authored branch (there's no field to read on a JDK exception) — they only get the Arabic-detection-or-generic-fallback, consistent with treating them as framework-level guards rather than user-facing business rules.
- Frontend: the 4 call sites now go through one shared resolver, `frontend/src/utils/apiErrorMessage.mjs` → `resolveApiErrorMessage(errorData, fallback)`, instead of 4 separate inline fallback chains — `messageAr` → `message` → `error` → provided default.

### What this does NOT cover yet
- Existing throw-sites (`ClaimService`, `ClaimStateMachine`, `AtomicFinancialService`, etc.) were **not modified** to add `messageAr` — that would mean touching claim/financial business logic files, which is out of scope without a proven defect per the standing instruction. They now get the generic fallback (previously: nothing, or a leaked English string) instead of the specific wording those cases deserve. If/when a specific message is wanted for a specific throw-site, the pattern is: add `messageAr` to that one `throw new BusinessRuleException(...)` call — no handler change needed.
- Non-claim/pre-approval provider flows (eligibility, visits, documents) weren't swept for the same frontend bypass pattern — flag if you want that pulled forward.

---

## 2. Structured logging (owner addition)

Every business/state/not-found exception on the claims path now logs, server-side only (never in the API response):

```
requestId=<trackingId> user=<username|anonymous> providerId=<id|-> claimId=<id|-> visitId=<id|-> endpoint=<METHOD path>
```

- `requestId`/`user`: existing tracking-id + `SecurityContextHolder` auth name.
- `providerId`: resolved via `AuthorizationService.getCurrentUser()` (best-effort, swallowed on failure — never breaks error handling).
- `claimId` / `visitId`: extracted from the request path (`/claims/{id}`, `/visits/{id}`) via a small regex helper — no new dependency, no touching of business services.
- Implemented once as `GlobalExceptionHandler.logContext(request, trackingId)` and reused across all the business-exception handlers (`handlePolicyNotActive`, `handleCoverageValidation`, `handleClaimTransition`, `handleBusinessRule`, `handleNotFound`, `handleIllegalState`).

Verified in the test run output (see §3) — e.g. `claimId=5` was correctly extracted from `POST /api/v1/claims/5/submit`.

---

## 3. Tests added (required before commit)

**Backend — `backend/src/test/java/com/waad/tba/common/error/GlobalExceptionHandlerMessageArTest.java`** (4 tests, all pass):
1. `businessRuleException_usesServiceProvidedArabicMessage` — when a service sets `messageAr` explicitly, the handler response uses it verbatim.
2. `businessRuleException_withoutArabicMessage_fallsBackToGenericArabic_notEnglishLeak` — when no Arabic is available anywhere, the response is non-null **and is not the raw English exception text** (i.e. no leak).
3. `everyBusinessExceptionType_alwaysProducesNonNullMessageAr` — for `PolicyNotActiveException`, `CoverageValidationException`, `ClaimStateTransitionException`, `BusinessRuleException`, `ResourceNotFoundException`: response `messageAr` is always non-null.
4. `resourceNotFoundException_withServiceProvidedArabicMessage_isUsedVerbatim` — same service-authored-message check for `ResourceNotFoundException`.

Run: `mvn -o -DskipTests=false "-Dtest=GlobalExceptionHandlerMessageArTest" test` → `Tests run: 4, Failures: 0, Errors: 0`. (Note: this repo defaults `skipTests=true`; `-DskipTests=false` is required to actually execute tests, on this run and in CI.)

**Frontend — `frontend/src/utils/apiErrorMessage.test.mjs`** (5 tests, all pass, run via Node's built-in test runner — no new test framework/dependency added since none existed in this project):
1. Prefers `messageAr` over `message` when both are present.
2. Falls back to `message` when `messageAr` is absent.
3. Falls back to `error` when both are absent.
4. Returns the caller-provided default when nothing usable is present (covers both `{}` and `null`).
5. Passes through a plain string `errorData` as-is.

Run: `node --test src/utils/apiErrorMessage.test.mjs` → `# pass 5, # fail 0`.

Also verified end-to-end: `npx vite build` succeeds with the new `.mjs` import wired into `ProviderClaimsSubmission.jsx` and `ProviderPreApprovalSubmission.jsx` (both chunks build cleanly).

---

## 4. Regression risk assessment

- No SQL/migrations, no entity/business-logic changes, no changes to `ClaimService`, `ClaimStateMachine`, `AtomicFinancialService`, or any financial/medical rule.
- Backend change is additive (`messageAr` population + one new constructor field per exception class) — `message`/`code`/`details`/HTTP status for every response are unchanged.
- Frontend changes only add a preferred earlier branch (`messageAr`) before the existing fallback chain, now centralized in one function — if `messageAr` is absent, behavior is identical to before.

---

## 5. Not started (per instruction)
Navigation simplification, workspace redesign, performance optimization, attachment versioning, messaging engine — untouched.

---

**PROVIDER-PORTAL-UX-1 PHASE 1 — APPROVED. Committing now, then proceeding directly to Phase 2 (navigation simplification) per instruction.**
