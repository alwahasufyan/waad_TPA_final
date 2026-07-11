# Reporting Framework - R2 Migration Kickoff

Date: 2026-07-10
Status: Started
Scope: Existing reports migration only (no new reports)

---

## Official Constraints (Mandatory)

1. Do not create new reports.
2. Do not change business logic.
3. Migrate and unify existing reports only.
4. Full compliance with:
   - Report Registry (single source of truth)
   - Report Engine (only execution layer)
   - Global Print/PDF Identity

---

## R2 Objective

Lift-and-shift existing reporting pages into the Reporting Framework governance path, while preserving behavior.

R2 is limited to:
- route/menu/permission alignment to registry
- duplication removal
- unified PDF/Excel/Print path adoption

R2 must not introduce new analytical/business report logic.

---

## Governance Rules Applied in R2

1. Report Code is immutable.
2. Report Registry is the single source of truth.
3. Every migrated report remains mapped to:
   - code
   - owner
   - version
   - description
   - domain
   - permission resource
   - shared print/pdf identity (System Settings)
   - shared excel profile

---

## System Settings Identity Rule (PDF/Print)

Every migrated PDF/print output must read identity fields from System Settings:
- Institution Name
- Logo
- Address
- Phone
- Email
- Website

No static hardcoded identity values are allowed.

---

## R2 Execution Plan (Migration-only)

1. Prepare and approve a complete Migration Matrix for all current reports before moving any report.
2. Migrate by approved domain groups only.
3. After each group, submit progress report and wait for approval before moving to next group.
4. Replace duplicate local export/print implementations with shared framework paths.
5. Validate no business-logic diffs and no API contract behavior changes.

---

## Approved Migration Priority Order

1. Claims
2. Financial Settlements
3. Members
4. Employers
5. Providers
6. Contracts
7. Price Lists
8. Benefit Policies
9. Audit
10. Analytics

No group transition is allowed before explicit approval of the previous group.

---

## Mandatory Migration Matrix Columns

Every migrated report must be tracked with:
- Artifact Type
- Old Route
- New Route
- Old API
- New API
- Legacy Components
- Shared Components
- Print -> Global Print
- Export -> Shared Export Engine
- Status

Artifact Type policy:
- Only Artifact Type = Report is eligible for migration to Reports Center.
- Workspace/Dashboard/Operational/Document artifacts remain in their domain modules.

---

## Non-Goals

- No new report creation.
- No pricing/claims/settlement business recalculation changes.
- No architecture redesign.

---

## Kickoff Confirmation

R2 has started under frozen architecture constraints and migration-only scope.
