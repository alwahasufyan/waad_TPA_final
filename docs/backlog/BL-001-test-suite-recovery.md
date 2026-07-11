# Backlog Item BL-001 — Test Suite Recovery

**Status:** 📋 Open · Outside Stage 1 (Stage 1 is closed and this is explicitly NOT counted as a Stage 1 failure).
**Origin:** Discovered during Stage 1 Finalization verification.
**Owner:** TBD · **Priority:** High (enabler for all future stages) · **Type:** Engineering / Test Infrastructure.

---

## Summary

The backend test suite was hard-disabled at the build level (`<skipTests>true</skipTests>` in `pom.xml`, since made overridable in Stage 1). When actually run (`mvn test -DskipTests=false`), it reports **17 failing of 69 tests**. None are Stage 1 regressions (proven via `git` — the failing services/tests were never modified in Stage 1). This item restores the suite to a trustworthy, green, always-run state so it can serve as a real safety net for future stages.

## Current State (facts, as observed Stage 1)

Full run: **69 tests, 14 failures, 3 errors, 52 passing.**

| Failing class | Result | Category |
|---|---|---|
| `CostCalculationServiceTest` | 6 fail / 6 | Calculation-assertion mismatch (e.g. `expected 100.00 but was 0.00`) |
| `CoverageEngineServiceTest` | 6 fail / 8 | Calculation + Arabic-vs-English error-code assertions — **overlaps CF-3 coverage-engine territory (deferred)** |
| `MemberExcelImportServiceTest` | 2 fail + 1 error | Import assertions + context/data |
| `ClaimLifecycleIntegrationTest` | 1 error | Fails to boot Spring context |
| `DropIndexTest` | 1 error | Fails to boot Spring context |

**Root cause of the 2 boot errors:** a rogue dev scratch class `com.waad.tba.CheckLogic` carries `@SpringBootConfiguration`, colliding with `TbaWaadApplication` (`Found multiple @SpringBootConfiguration annotated classes`). Sibling scratch classes (`CheckAbdulmunim`, `CheckDuplicates`, `ResetKinship`, `DropIndexTest`) are dev utilities living in `src/test`, not real tests.

## Proposed Scope (to be planned/approved before execution)

Split into safe vs. business-dependent work:

### Part A — Safe test-infra recovery (no business rules)
1. Remove or relocate the dev scratch classes (`CheckLogic`, `CheckAbdulmunim`, `CheckDuplicates`, `ResetKinship`, `DropIndexTest`) out of `src/test` (they are not tests). This alone unblocks `@SpringBootTest` context boot.
2. Re-run and re-baseline: confirm how many failures remain once context-boot is fixed.
3. Quarantine genuinely-deferred tests with `@Disabled("BL-001 / CF-3 — see decision doc")` + a tracking reference, so the suite goes green and stays runnable in CI, with disabled tests explicitly visible (never silently skipped).
4. Re-enable the suite in CI/build (flip `skipTests` default to `false`, or add a CI profile that runs it) once green.

### Part B — Business/domain-dependent (needs input, NOT guessed)
5. For each `CostCalculationServiceTest` / `CoverageEngineServiceTest` failure, determine whether the **test expectation** or the **production code** encodes the intended behavior. This is a domain judgment.
   - The `CoverageEngineServiceTest` failures are coupled to **CF-3** (dual coverage engines, deferred). Fold this analysis into CF-3 if/when CF-3 is picked up.
6. Fix the confirmed-wrong side (test or code) with regression coverage.

## Definition of Done
- Dev scratch classes removed from `src/test`.
- Suite boots and runs cleanly via Maven; disabled tests are explicit and ticket-linked.
- CI runs the suite and fails the build on regression.
- A documented, agreed baseline of which tests are green vs. quarantined-pending-domain-input.

## Constraints (ATEF)
- **Never invent business rules** to force a test green — calculation-assertion resolution (Part B) requires domain confirmation of intended behavior.
- No production business/workflow logic changed to satisfy a test unless the code (not the test) is confirmed wrong.

## Notes
- This item is a strong candidate to run **first in Stage 2** (as an enabler) because a trustworthy suite de-risks every subsequent change — but its scheduling is the business's call.
