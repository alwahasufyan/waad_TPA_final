# 08 — Technical Debt Register

> Consolidated from all preceding documents, ranked by severity. Severity reflects **potential business impact if left unaddressed**, not effort to fix (effort is addressed separately in `09-improvement-roadmap.md`). Every item cites its source document for full context.

Severity legend: 🔴 **Critical** (financial/security/data-integrity risk) · 🟠 **High** (real correctness or maintainability risk) · 🟡 **Medium** (real but contained) · 🟢 **Low** (cosmetic/cleanup)

---

## 🔴 Critical

### T1. JWT tokens effectively never expire (~10 years), no revocation mechanism
A leaked or stolen JWT remains valid indefinitely; there is no denylist/blacklist. Session-cookie auth is the *preferred* path and mitigates this for normal usage, but the JWT fallback is fully live and this is a serious latent exposure.
**Source:** `03-module-catalog.md` §security, `01-system-overview.md` §8.

### T2. `VisitService.delete()` hard-deletes with cascade ALL onto Claims/EligibilityChecks
Deleting a Visit can cascade-hard-delete financial claim records, including SETTLED ones — directly contradicting the soft-delete discipline applied everywhere else in the financial domain, and structurally rooted in an aggregate-boundary ambiguity between Visit and Claim.
**Source:** `02-business-workflows.md` §5, `07-domain-analysis.md` §2.

### T3. Two parallel, unreconciled coverage/limit engines
`benefitpolicy.BenefitPolicyCoverageService` (documented canonical) and `claim.ruleengine.*` coexist with no confirmed division of labor. If both are live on different code paths, two members with identical policies could receive different coverage decisions.
**Source:** `03-module-catalog.md` §benefitpolicy/§claim, `07-domain-analysis.md` §1/§7.

### T4. `Claim` state-machine has two disagreeing sources of truth
`ClaimStatus.getValidTransitions()` and `ClaimStateMachine.TRANSITION_MATRIX` list different legal transitions for the same states (e.g. `UNDER_REVIEW → APPROVED` allowed in one, not the other). Additionally, both sources document `REJECTED → APPROVED` as a valid "admin re-edit" path, but `ClaimStateMachine.validateFinalStateLock()` unconditionally blocks it — the documented path is **currently unreachable**, meaning either a real bug or stale documentation about a workflow users may believe exists.
**Source:** `02-business-workflows.md` §6, `03-module-catalog.md` §claim.

### T5. `payment_records`/`payment_audit_logs` destructively recreated in production migration (V66)
An incomplete column rename in V63 was resolved by `DROP TABLE` + `CREATE TABLE` in V66 — a data-loss-risking pattern on a financial-audit table. Needs confirmation that no production payment-audit history was actually lost.
**Source:** `05-database-analysis.md` §9, `02-business-workflows.md` §9.

### T6. Hardcoded 20% patient co-pay fallback in financial calculation
`Claim.calculateFields()` defaults `patientCoPay` to 20% of net-accepted amount when not explicitly set, bypassing the policy's actual configured coverage percentage — a magic number embedded in an otherwise rule-driven financial system.
**Source:** `02-business-workflows.md` §6, `07-domain-analysis.md` §4.

### T7. Possible FK-target mismatch: `claims.pre_authorization_id` points at `preauthorization_requests`, not `pre_authorizations`
Code comments describe `pre_authorizations` as "the real working preauth table," yet the Claim FK targets a different table (`preauthorization_requests`). This needs urgent verification — it may be a live, silent bug rather than a documentation artifact.
**Source:** `05-database-analysis.md` §2/§3, `03-module-catalog.md` §preauthorization.

---

## 🟠 High

### T8. Recurring dead-role-reference bug class in `@PreAuthorize`
`INSURANCE_ADMIN` (ClaimController ×2), `ADMIN`/`SYSTEM_ADMIN` (EmailSettingsController ×4) — none are valid `SystemRole` values. Harmless where `SUPER_ADMIN` is also listed (cosmetic only), but represents a repeatable bug pattern that could produce a permanently-unreachable endpoint if a future check lists only an invalid role.
**Source:** `04-api-catalog.md` §API-Wide Observations.

### T9. Two structurally different API response envelopes
Success responses (`ApiResponse`) and error responses (`ApiError`) have different JSON shapes — frontend code must special-case parsing by HTTP status rather than relying on one consistent contract.
**Source:** `03-module-catalog.md` §common, `01-system-overview.md` §8.

### T10. Two independent email-sending implementations with different safety behavior
`common.email.EmailService` respects `email.enabled`/dev-profile gating; `core.email.EmailService` (used for OTP reset and claim notifications) does not — risk of accidentally emailing real users from a dev/test environment.
**Source:** `03-module-catalog.md` §systemadmin (email triggers), `01-system-overview.md` §8.

### T11. Two independent, unreconciled audit-log systems sharing a class name across packages
`modules.audit.AuditLog` (immutable, `medical_audit_logs`) and `modules.systemadmin.AuditLog` (mutable, `audit_logs`) — no shared interface, no automatic capture; each module decides ad hoc which (if either) to write to.
**Source:** `03-module-catalog.md` §audit/§systemadmin, `07-domain-analysis.md` §11.

### T12. Entire operational `pre_authorizations` table has zero FK constraints
`member_id`, `provider_id`, `visit_id`, `email_request_id`, `service_category_id` are all unconstrained despite being clearly relational — referential integrity here relies entirely on application discipline.
**Source:** `05-database-analysis.md` §8.

### T13. Missing FK constraints on other hot relational columns
`visits.provider_id`/`.medical_category_id`/`.medical_service_id`, `claim_lines.pricing_item_id`/`.applied_category_id`, `payment_records.employer_id`/`.provider_id`, `claims.reviewer_id`, `users.company_id` (likely orphaned), `user_login_attempts.user_id`.
**Source:** `05-database-analysis.md` §8.

### T14. Thin automated test coverage relative to system complexity
19 backend test files for ~597 Java source files (461 REST endpoints); effectively one frontend smoke test for ~683 frontend source files. Given the financial/medical stakes, this is the most consequential long-term risk to safe change.
**Source:** structural recon (test counts), `01-system-overview.md` §8.

### T15. Very large, monolithic frontend page components
`ProviderClaimsSubmission.jsx` (2,561 lines), `ClaimBatchEntry.jsx` (2,085), `BenefitPolicyRulesTab.jsx` (1,840), `ProviderContractView.jsx` (1,674), and ~12 more files over 1,000 lines — mixing form state, table rendering, validation, and submission logic in single files, concentrated in the highest-traffic workflows.
**Source:** `06-ui-ux-audit.md` §4, `01-system-overview.md` §8.

### T16. Five different soft-delete conventions coexist across the schema
`deleted`+`deleted_at`+`deleted_by` triad, `deleted`-only, `deleted_at`-only-alongside-independent-`active`, `is_deleted`, and `active`-only — with `claims` itself having two independent, unreconciled lifecycle flags (`active` and `deleted_at`) with no CHECK constraint tying them together.
**Source:** `05-database-analysis.md` §11.

### T17. No centralized state machine for PreAuthorization, Member, or ProviderContract lifecycles
Only `Claim` has a dedicated, testable `ClaimStateMachine` class. The other three modules rely on scattered entity-guard predicates — an inconsistency in engineering investment across similarly consequential lifecycles, and the class of bug found in Claim's state machine (T4) has no equivalent safety net to even *detect* in these other modules.
**Source:** `03-module-catalog.md` §preauthorization/§member/§providercontract, `07-domain-analysis.md` §5.

---

## 🟡 Medium

### T18. `benefitpolicy` module's own Javadoc describes a stale 3-tier coverage priority
`SERVICE_RULE > CATEGORY_RULE > POLICY_DEFAULT` documented, but service-level rules were removed upstream — only 2 tiers actually exist. Risk: a maintainer trusts the comment over the code.
**Source:** `07-domain-analysis.md` §7, backend research findings.

### T19. `ClaimStateMachine`'s own class-level Javadoc uses role names that don't exist
References `INSURANCE`/`REVIEWER`/`EMPLOYER` where the real constants are `ACCOUNTANT`/`MEDICAL_REVIEWER`/`EMPLOYER_ADMIN` — internal documentation drift within the single most consequential state-machine class in the system.
**Source:** `02-business-workflows.md` §6.

### T20. `ClaimApprovalEventListener`'s inline comments contradict each other on sync vs. async execution
Documented as async in one comment, confirmed synchronous (`AFTER_COMMIT`, no `@Async`) by the actual annotation a few lines later — matters because it determines whether the HTTP response to an approving accountant waits on the provider-account credit.
**Source:** `02-business-workflows.md` §8, `07-domain-analysis.md` §9.

### T21. `AuthorizationService.isInsuranceAdmin()` is misleadingly named
Actually checks `SUPER_ADMIN` **or** `ACCOUNTANT`; there is no `INSURANCE_ADMIN` role. Risk of future misuse by a maintainer who trusts the method name.
**Source:** `03-module-catalog.md` §security.

### T22. Inconsistent OTP vs. token-link password-reset behavior
Only the token-link flow calls `unlockAccount()` on success; the OTP flow does not — a locked-out user who resets via OTP remains locked.
**Source:** `03-module-catalog.md` §auth, `02-business-workflows.md`.

### T23. API versioning inconsistency
`PreAuthEmailRequestController` (`/api/preauthorization/...`) and `ReportController` (`/api/reports/...`) omit the `/v1` segment used by all 56 other controllers.
**Source:** `04-api-catalog.md`.

### T24. Deprecated-but-live API surfaces
`VisitController.GET /all`/`GET /search` (marked deprecated), and the entire `UnifiedSearchControllerDeprecated` class (`/api/v1/members-deprecated`) remain deployed and reachable.
**Source:** `04-api-catalog.md`.

### T25. Two live import pipelines per major entity
`MemberImportController` (`/legacy-import`) and `MemberExcelTemplateController` (`/unified-members/import`) both live simultaneously — incremental migration to a newer mechanism without retiring the old one.
**Source:** `04-api-catalog.md`.

### T26. Duplicate-purpose columns never reconciled
`user_login_attempts` (`attempt_result`/`success`, `created_at`/`attempted_at`, `failure_reason`/`failed_reason`); `email_verification_tokens`/`password_reset_tokens` (`expiry_date`/`expires_at`).
**Source:** `05-database-analysis.md` §7.

### T27. `claim_lines` under-indexed relative to its role
Only 3 indexes on a 40+ column, high-volume child table central to financial reconciliation — no index on `rejected`, `pricing_item_id`, or `applied_category_id`.
**Source:** `05-database-analysis.md` §4.

### T28. Medical taxonomy read access excludes several roles that likely need it
`EMPLOYER_ADMIN`, `ACCOUNTANT`, `FINANCE_VIEWER`, `DATA_ENTRY` cannot read the category tree, which may prevent them from resolving service/category names in their own claim or report views.
**Source:** `03-module-catalog.md` §medicaltaxonomy, `04-api-catalog.md`.

### T29. Unverified `X-Employer-ID` header trust in legacy dashboard endpoints
`DashboardController`'s legacy `/stats`/`/claims-per-day` accept employer scoping via a client-supplied header rather than deriving it solely from the authenticated session — not confirmed as exploitable, but not confirmed as safe either.
**Source:** `02-business-workflows.md` §10, `03-module-catalog.md` §dashboard.

### T30. Silent PDF font-loading failure risk for Arabic reports
If the embedded Cairo font resource fails to load, `PdfExportService` risks silently falling back rather than failing loudly — could produce broken Arabic glyphs in production without an obvious error signal.
**Source:** `03-module-catalog.md` §report, `06-ui-ux-audit.md` §8.

### T31. `ReportController.getClaimReportPdf` bypasses the standard error contract
Swallows exceptions via `printStackTrace()` and returns a bare 500 outside `GlobalExceptionHandler`'s `ApiError` shape.
**Source:** `03-module-catalog.md` §report.

### T32. `PaymentService.mapToDto()` swallows employer/provider lookup failures silently
A bad employer/provider ID shows a placeholder rather than surfacing a data-integrity error.
**Source:** `02-business-workflows.md` §9.

### T33. Three parallel "unified/generic" table components on the frontend
`GenericDataTable`, `UnifiedDataTable`, `UnifiedMedicalTable` — each apparently grown independently; a UX improvement to one won't propagate to the others.
**Source:** `06-ui-ux-audit.md` §6.

### T34. Destructive/administrative endpoints exposed as regular REST routes
`DELETE /api/v1/admin/system/reset`, `POST /seed-test-data` — SUPER_ADMIN-gated, but worth confirming disabled/absent in production deployment profile.
**Source:** `04-api-catalog.md`.

---

## 🟢 Low

### T35. Two parallel i18n data sources
`locales/ar.js`/`en.js` and `utils/locales/ar.json`/`en.json` — duplication risk if a translation is updated in one but not the other.
**Source:** `06-ui-ux-audit.md` §9.

### T36. Hardcoded bilingual menu labels bypass the i18n infrastructure
`menu-items/components.jsx` inlines `title`/`titleEn` per entry instead of using locale-file keys.
**Source:** `06-ui-ux-audit.md` §2.

### T37. One hardcoded LTR style in an otherwise-RTL page
`pages/under-development/index.jsx` sets `textAlign: 'left'`.
**Source:** `06-ui-ux-audit.md` §9.

### T38. Dead menu entry: hidden Documents Library
Explicitly hidden per an in-code comment ("Hidden per user request") despite the page being fully built and routed.
**Source:** `06-ui-ux-audit.md` §1.

### T39. Leftover template scaffold and stray files in the frontend source tree
`layout/Component/*` (appears to be unused scaffold from the underlying admin template), a stray `.swp` file, and `pages/provider-contracts/data/providerContracts.mock.js` (657 lines) coexisting with the live service.
**Source:** `06-ui-ux-audit.md` §13.

### T40. `member_seq` sequence defined but never used
`members` table uses `BIGSERIAL` directly, never referencing `member_seq` — a dead schema object.
**Source:** `05-database-analysis.md` §10.

### T41. `users.company_id` appears to be an orphaned, unused column
No FK, no confirmed use elsewhere in the schema.
**Source:** `05-database-analysis.md` §8.

### T42. `admin` vs. `systemadmin` module naming overlap
Two modules with similar names and overlapping conceptual territory — a source of future-maintainer confusion even if each currently has a distinct, non-conflicting purpose.
**Source:** `03-module-catalog.md` §admin.

### T43. Repeated date-range CHECK constraint logic
`end_date >= start_date` implemented independently in ~5 tables rather than via one shared pattern.
**Source:** `05-database-analysis.md` §5.

### T44. `PdfCompanySettingsController`'s `isAuthenticated()` class-level guard may conflict with pre-login branding needs
Not confirmed broken, but worth an explicit end-to-end check.
**Source:** `03-module-catalog.md` §pdf.

---

## Severity Summary

| Severity | Count |
|---|---|
| 🔴 Critical | 7 |
| 🟠 High | 10 |
| 🟡 Medium | 17 |
| 🟢 Low | 10 |
| **Total tracked items** | **44** |

**Framing for ATEF**: none of these findings suggest the system is unsound — the opposite is true in several core areas (financial-identity guards, balance invariants, immutable audit trails, a real domain-event backbone). What they collectively show is a system that grew fast under real production pressure, accumulated the ordinary scar tissue of that growth (documentation drift, duplicate columns, a few parallel implementations that never fully converged), and would benefit most from **reconciliation and consolidation work**, not a rewrite. See `09-improvement-roadmap.md` for a sequenced plan.

---

*Continue to [`09-improvement-roadmap.md`](./09-improvement-roadmap.md) for how to act on this register.*
