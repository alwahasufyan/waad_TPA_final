# Reporting Framework - R2 Group 1 (Claims Reports) Progress Report

Date: 2026-07-11
Group: 1 (Claims Reports)
Status: Started (In Progress)

---

## Scope (Group 1 Only)

In-scope artifacts:
- REP-CLM-001
- REP-CLM-003
- REP-CLM-004

Artifact Type:
- Report only

Out of scope for this group:
- Any Workspace/Dashboard/Operational/Document artifact
- Any new report creation
- Any business logic change

---

## Preconditions Verified

1. Migration Matrix updated with Artifact Type column.
2. Matrix filtered to true report artifacts only.
3. Group-gating rule active (no Group 2 before Group 1 approval).
4. R2 constraints preserved (migration-only).

---

## Group 1 Execution Plan

1. Align claims report routes/menu entries with registry-governed Reports Center mapping.
2. Keep API behavior unchanged (same contracts).
3. Replace duplicated print/export wiring with shared framework paths where applicable.
4. Validate no business-logic diff.
5. Submit Group 1 completion report for approval.

---

## Current Status by Report

| Report Code | Artifact Type | Status |
|---|---|---|
| REP-CLM-001 | Report | In Progress |
| REP-CLM-003 | Report | In Progress |
| REP-CLM-004 | Report | In Progress |

---

## Implementation Delta Completed

1. Registry updates completed:
	- Added REP-CLM-003 (Claim Statement Preview) as claims report artifact.
	- Added REP-CLM-004 (Provider Claims Report) in registry with provider-portal surface.
2. Report center surface governance completed:
	- Reports Center now queries claims reports with surface = report-center.
	- Provider-portal-only claims report stays out of admin Reports Center listing.
3. Statement preview template extraction completed:
	- Shared layout component extracted from statement-preview page.
	- Existing API endpoints and request params remain unchanged.
4. No intentional business logic or API contract changes were introduced.

---

## Validation Status

Automated checks:
- File-level diagnostics for modified Group 1 files: Passed (no errors).
- Registry/engine surface separation: implemented and code-wired.

Project-wide checks:
- Frontend lint returns pre-existing global warnings across the workspace.
- Frontend build currently fails due to pre-existing module resolution issue
  unrelated to Group 1 migration scope (`reporting/reportEngine` alias resolution in this workspace state).

Functional test gate (mandatory before "Migrated"):
- REP-CLM-001: Pending functional execution.
- REP-CLM-003: Pending functional execution.
- REP-CLM-004: Pending functional execution (inside Provider Portal only).

Until all three functional tests pass, Group 1 remains In Progress and not marked Migrated.

---

## Gate

Do not start Group 2 before explicit approval of this Group 1 report.
