# Stage 1 — Foundation Stabilization — Checkpoint Report

**Program:** Waad TPA Enterprise Evolution · **Stage:** 1 (Foundation Stabilization) · **Status:** ⏸ **Paused for business decisions — NOT yet complete**
**Governance:** ATEF Constitution (Evolution over Replacement; never invent business rules; business correctness first).

---

## Executive Summary

Reading the **actual source** (as ATEF mandates: "understand before modify") materially changed the risk picture from the earlier assessment, which had been built from research-agent summaries. Two of the seven "critical" findings were **de-risked** on inspection; two were **confirmed**; and three of the seven require a **business decision or explicit approval I must not make unilaterally** (Constitution: "Never invent business rules"; "Never recommend dropping/altering production data without explicit approval").

**Completed and verified this stage (safe, unambiguous, zero workflow change):**
- **CF-2** — Visit hard-delete cascade closed (prevents destruction of settled claims).
- **CF-1** — Claim state-machine documentation/dead-code reconciled to the authoritative runtime matrix.
- **CF-6** — JWT lifetime reduced from ~10 years to a configurable 24h default.

**Blocked pending your decision (details in §Decisions Required):** CF-3, CF-4, CF-5, CF-7.

The application builds, all touched tests pass, and every critical journey (login, member/provider search, claim list, settlement, dashboard, reports, benefit policies) returns healthy. The system is left **running on port 8081** for your manual browser verification.

---

## Assessment Corrections (from reading real code)

| Finding | Prior assessment claim | Verified reality in code | Impact |
|---|---|---|---|
| **CF-1** | Runtime matrix allowed `REJECTED → APPROVED`; workflow-trust crisis | `ClaimStateMachine.TRANSITION_MATRIX` defines `REJECTED → {}` and `HARD_LOCKED_FINAL_STATES` includes REJECTED. Runtime is **cleanly terminal & consistent**. The disagreeing table lived only in `ClaimStatus.getValidTransitions()`, which is **dead code** (only self-referenced; UI uses the state machine). | Downgraded from P0 workflow bug to documentation/dead-code hygiene |
| **CF-7** | Hardcoded 20% co-pay inside the core financial calculation | The `0.20` is inside the `if (!finalized ...)` branch explicitly commented **"naive preview for UI/Drafts"**. Finalized APPROVED/SETTLED claims **skip it entirely**. It is a draft-preview default, not the authoritative approval co-pay. | Downgraded from financial-core defect to a preview-default business decision |

These corrections are exactly the value of the "understand before modify" gate — we avoided a rushed change to a claim state machine and a financial calculation based on an inaccurate second-hand model.

---

## Completed Work (verified)

### CF-2 — Visit hard-delete data-integrity guard ✅
- **Problem (confirmed):** `Visit.claims` is `@OneToMany(cascade = CascadeType.ALL)` (`Visit.java:169`); `VisitService.delete()` called raw `repository.deleteById(id)` (`VisitService.java:192`). Deleting a visit cascade-destroyed **all** its claims — including SETTLED ones with real payments — bypassing every soft-delete/audit protection elsewhere in the platform.
- **Fix (evolution, reuse-first):** Added `ClaimRepository.countByVisitId(...)` (counts ALL claims, ignoring `active`, because any claim is protected financial/audit history) and guarded `delete()` with the existing `DeletionGuard` fluent pattern — the same pattern used across the codebase for consistent Arabic 422 messages. Happy path (visits with no claims) is unchanged.
- **Verification (live):** `DELETE /api/v1/visits/1` (visit has 1 claim) → **HTTP 422**, Arabic message `لا يمكن حذف الزيارة ... (مطالبات مرتبطة: 1)`, and the claim **remained present** (count still 1). Cascade destruction prevented.
- **Files:** `modules/visit/service/VisitService.java`, `modules/claim/repository/ClaimRepository.java`.

### CF-1 — Claim state-machine documentation / dead-code reconciliation ✅
- **Problem:** `ClaimStatus.getValidTransitions()`/`canTransitionTo()` (dead code) listed transitions disagreeing with the authoritative `ClaimStateMachine.TRANSITION_MATRIX` (`UNDER_REVIEW→APPROVED` missing; `REJECTED→APPROVED` falsely claimed). The `ClaimStateMachine` class header also used non-existent role names (`EMPLOYER`/`INSURANCE`/`REVIEWER`).
- **Fix:** Reconciled the dead enum table to exactly mirror the runtime matrix and documented `ClaimStateMachine` as the single authoritative source. Corrected the header role-name table to real `SystemRole` constants. **No runtime behavior changed** (the enum methods are not on any live path; the live `allowsEdit()` was deliberately left untouched — editing a rejected claim's data ≠ transitioning its status).
- **Verification:** compile green; `ClaimStateMachineTest` passes.
- **Files:** `modules/claim/entity/ClaimStatus.java`, `modules/claim/service/ClaimStateMachine.java`.

### CF-6 — JWT expiration reduced and made configurable ✅
- **Problem:** `application.yml` set `jwt.expiration: 315360000000` (~10 years); a leaked bearer token stayed valid for a decade with no revocation.
- **Fix (configuration-before-code):** `expiration: ${JWT_EXPIRATION_MS:86400000}` — 24h default, env-tunable, matching `JwtTokenProvider`'s own `@Value` fallback. Session-cookie auth remains the preferred path. **Already-issued tokens keep their embedded expiry, so no active session is disrupted** — only newly-minted tokens get the shorter lifetime.
- **Verification (live):** freshly-issued token decodes to **exactly 24h** lifetime (was ~87,600h). Backend boots cleanly with the new config; login journey succeeds.
- **Files:** `backend/src/main/resources/application.yml`.

---

## Decisions Required (blocking Stage 1 completion — I will not guess these)

### CF-3 — Two parallel coverage engines *(needs investigation + your ruling)*
`benefitpolicy.BenefitPolicyCoverageService` (documented canonical) and `claim.ruleengine.*` (has its own admin controller) both implement coverage/limit logic. Per the Constitution's **Single Source of Truth** principle, one must be authoritative. **Before touching either**, I need to complete a call-site audit to confirm which executes in the live approval path — and then you/the business must confirm the intended canonical engine. This is not a safe blind edit.
**Ask:** Approve me to perform a (read-only) call-site audit and report which engine is live, with a consolidation recommendation for your sign-off.

### CF-4 — Pre-authorization FK target mismatch *(confirmed; needs approval for a production-data migration)*
Confirmed: the operational `PreAuthorization` entity maps to table `pre_authorizations`, but `claims.pre_authorization_id`'s FK references `preauthorization_requests(id)` (`V19__claims.sql:76`) — a different table. This may be a latent bug in claim↔pre-auth linkage. **However, correcting an FK target touches live clinical/financial linkage data**; per the Constitution ("protect production data / no destructive changes without explicit approval") this needs: (a) an investigation of how `Claim.preAuthorization` is actually mapped and what real data exists, and (b) your explicit approval of a carefully-planned, reversible migration. Not a Stage-1 quick fix.
**Ask:** Approve a scoped read-only investigation; I will return a precise finding + a safe migration plan for your approval before any schema change.

### CF-5 — Destructive V66 migration *(fact-finding only — I cannot fix code here)*
`V66__recreate_payment_tables.sql` does `DROP TABLE ... payment_records / payment_audit_logs; CREATE TABLE ...`. If it ran against a **populated** production database, historical payment-audit records may have been lost. This is an operational/ops-history question only you (or whoever ran production migrations) can answer.
**Ask:** Confirm whether V66 executed against populated production tables, and whether a pre-migration backup exists. No code change is possible or appropriate from my side.

### CF-7 — Draft-preview default co-pay (20%) *(business rule — must not invent)*
The 20% is a **draft/preview** default only (finalized claims are unaffected). Changing it to "derive from the member's benefit policy default coverage" is reasonable but is a **business rule** and also an architectural question (entities should not load policies — that belongs in a service). Per the Constitution ("never hardcode coverage percentages" vs. "never invent business rules"), I need your ruling.
**Ask:** Choose one — (a) keep 20% as the documented draft-preview default; (b) derive the preview co-pay from the policy's default coverage % (I would implement this in the service layer, not the entity); or (c) show no preview co-pay until a reviewer sets it.

---

## Definition of Done — Status

| Criterion | Status |
|---|---|
| Project builds successfully | ✅ `mvn compile` exit 0; full `mvn test` BUILD SUCCESS |
| No Critical Security Issues remain | ⚠ CF-6 fixed; CF-4 (FK) pending approval |
| Financial calculations validated | ✅ Financial/coverage unit tests pass; CF-7 is a preview-default decision, not a core-calc defect |
| Medical calculations validated | ✅ Coverage/eligibility tests pass; CF-3 consolidation pending |
| Claim workflow operational | ✅ Login→claim list→state machine verified (200s; state-machine test green) |
| Settlement workflow operational | ✅ `/provider-accounts` 200; balance-invariant tests green |
| No regression introduced | ✅ All touched tests pass; live journeys healthy |
| Documentation updated | ✅ This report + inline code docs |
| Self-review passed | ✅ Reuse-first (DeletionGuard, existing repo), no workflow change, additive-only |
| Manual browser testing | ⏳ **Reserved for you** (per Stop Condition) — app left running on :8081 |

**Stage 1 cannot be marked complete** until CF-3/CF-4/CF-5/CF-7 are resolved with your input.

---

## Files Modified

| File | Change | Risk |
|---|---|---|
| `modules/visit/service/VisitService.java` | CF-2 delete guard + import | Low — additive guard, happy path unchanged |
| `modules/claim/repository/ClaimRepository.java` | CF-2 `countByVisitId` query | Low — new read-only query |
| `modules/claim/entity/ClaimStatus.java` | CF-1 dead-table reconciliation + docs | None — dead code, no live path |
| `modules/claim/service/ClaimStateMachine.java` | CF-1 header doc correction | None — comment only |
| `backend/src/main/resources/application.yml` | CF-6 JWT expiration → configurable 24h | Low — only affects newly-issued tokens |

**Database changes:** none (no migration authored this stage). **Breaking changes:** none. **API contract changes:** none.

---

## Impact Summary

- **Performance:** neutral (one added indexed-count query on a delete path; delete is rare).
- **Security:** materially improved — JWT exposure window cut from ~10 years to 24h, revocable via config.
- **Financial:** protected — settled claims can no longer be destroyed via visit deletion.
- **Medical:** neutral this stage (CF-3 consolidation deferred pending decision).
- **Regression:** none observed.

## Lessons Learned

1. **"Understand before modify" earned its keep immediately** — two P0 findings were inaccurate second-hand and would have led to unnecessary, risky changes to a claim state machine and a financial calculation.
2. **The safe/unsafe boundary is sharp here:** additive guards, config values, and dead-code reconciliation are safe to ship now; FK-target changes, coverage-engine consolidation, and business-rule defaults are not — they need investigation and your ruling.

## Recommendations / Next Actions (awaiting your approval)

1. **Decide CF-3/CF-4/CF-5/CF-7** (see §Decisions Required).
2. On approval, proceed with the **remaining safe Stage-1 items** already scoped in `docs/assessment/20-technical-debt.md` that need no business ruling: missing FK constraints on `visits`/`claim_lines`/`payment_records` via additive `NOT VALID` migrations (excluding the pre-auth FK until CF-4 is resolved); dead-role `@PreAuthorize` cleanup; silent PDF-font-failure hardening; auth rate limiting.
3. **STOP for your manual verification** of the three completed fixes before I continue.

---

## How to Run for Verification

- Backend is currently **running on `http://localhost:8081`** (dev profile).
- Frontend: `cd frontend && yarn start` → `http://localhost:3001` (proxies `/api` → 8081).
- Login: `superadmin@tba.sa` / `Admin@123`.
- To re-test the CF-2 guard: attempt to delete a visit that has claims → expect a blocked 422 with an Arabic message; the claims remain intact.

*Per the Stop Condition, I am stopping here and will not begin any Stage 2 work. Awaiting your decisions on CF-3/CF-4/CF-5/CF-7 and your manual verification of the completed fixes.*
