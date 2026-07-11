# 03 — Module Catalog

> Catalog of all 18 backend modules (`backend/src/main/java/com/waad/tba/modules/`) plus the cross-cutting `security/` and `common/` packages. Maturity ratings are relative to each other within this codebase, not an absolute industry benchmark.

Maturity scale: 🟢 Mature (state machine/guards, tests, stable schema) · 🟡 Functional (works, some rough edges) · 🟠 Emerging (present but thin) · 🔴 Fragile (structural inconsistencies found)

---

## employer

- **Purpose:** Top-level tenant/business entity — corporate clients purchasing group coverage.
- **Responsibilities:** Employer master data, contract dates, member-count limits, default-employer designation.
- **Aggregate Root:** `Employer`.
- **Entities:** `Employer`.
- **APIs:** `EmployerController` — CRUD + search + toggle-active.
- **DB Tables:** `employers`.
- **Dependencies:** None inbound; consumed by Member, BenefitPolicy, ProviderAllowedEmployer, PaymentRecord, User.
- **Current Maturity:** 🟡 Functional — simple entity, no lifecycle complexity, but `maxMemberLimit` enforcement unconfirmed.
- **Future Improvements:** Confirm/implement member-limit enforcement; document the V34 decision to decouple ProviderContract from Employer.

---

## member

- **Purpose:** Insured individuals (principal + dependents) enrolled under an employer's benefit policy.
- **Responsibilities:** Enrollment, card/barcode issuance, family hierarchy, bulk import, duplicate/kinship resolution, eligibility-relevant attributes (join date, deductibles).
- **Aggregate Root:** `Member` (self-referencing principal/dependent tree).
- **Entities:** `Member`, `MemberAttribute` (EAV), `MemberDeductible`, `MemberPolicyAssignment`, `MemberImportLog`/`MemberImportError`.
- **APIs:** `UnifiedMemberController`, `BeneficiarySearchController`, `NameSearchController`, `UnifiedSearchController`, `MemberImportController`, `MemberExcelTemplateController`, `MemberDuplicateController`, `KinshipMismatchController`, `UnifiedEligibilityController`.
- **DB Tables:** `members`, `member_attributes`, `member_deductibles`, `member_policy_assignments`, `member_import_logs`, `member_import_errors`.
- **Dependencies:** Employer (required), BenefitPolicy (auto-resolved). Upstream of Visit, Claim, EligibilityCheck, PreAuthorization.
- **Current Maturity:** 🟡 Functional — the "Unified" architecture consolidation is a positive sign of deliberate simplification, but no centralized state machine for `MemberStatus`/`CardStatus`, and duplicate identifier columns (`civil_id` vs `national_number`) are a data-quality risk.
- **Future Improvements:** Add a `MemberStateMachine` analogous to `ClaimStateMachine`; reconcile/retire the deprecated `civil_id` column; dedicated review of the large import/duplicate/kinship services.

---

## provider

- **Purpose:** Healthcare providers (hospital/clinic/lab/pharmacy/radiology) rendering services to members.
- **Responsibilities:** Provider master data, network tier, employer allow-listing, document management, provider-portal access surface.
- **Aggregate Root:** `Provider`.
- **Entities:** `Provider`, `ProviderAllowedEmployer`, `ProviderAdminDocument`, `ProviderService`.
- **APIs:** `ProviderController`, `ProviderPortalController`, `ProviderDocumentController`, `ProviderExcelController`, `ProviderExcelTemplateController`, `ProviderReportsController`.
- **DB Tables:** `providers`, `provider_allowed_employers`, `provider_admin_documents`, `provider_services`, `provider_service_price_import_logs`.
- **Dependencies:** None inbound (root master data); consumed by ProviderContract (cascade ALL), Visit/Claim/PreAuthorization (loose FK), ProviderAccount (1:1).
- **Current Maturity:** 🟡 Functional — schema stabilized after a significant V29/V32/V34 entity/DDL reconciliation (see `05-database-analysis.md` §9); `hasDocuments` computed `@Formula` is a slightly unusual but working pattern.
- **Future Improvements:** Verify the Swagger-documented "provider must have active contract with employer" check is actually enforced somewhere, or update the documentation.

---

## providercontract

- **Purpose:** Authoritative pricing/discount agreement between the TPA and a Provider — source of truth for claim-line unit pricing.
- **Responsibilities:** Contract lifecycle (DRAFT→ACTIVE→SUSPENDED/EXPIRED/TERMINATED), per-medical-category pricing items, discount-timing configuration.
- **Aggregate Root:** `ProviderContract`.
- **Entities:** `ProviderContract`, `ProviderContractPricingItem`.
- **APIs:** `ProviderContractController`, `ProviderContractPricingExcelController`.
- **DB Tables:** `provider_contracts`, `provider_contract_pricing_items`, `network_providers`, `legacy_provider_contracts`.
- **Dependencies:** Provider (parent). Consumed by ClaimLine (pricing snapshot), PreAuthorization (`contractPrice` snapshot).
- **Current Maturity:** 🟡 Functional — "only ONE active contract per provider" invariant **is** DB-enforced (partial unique index `uq_active_contract_per_provider`), better than initially appeared from entity-level Javadoc alone. `pricingItems` cascade PERSIST/MERGE-only (never REMOVE) is a deliberate, sound history-preservation choice.
- **Future Improvements:** A dedicated `ProviderContractStateMachine` class (mirroring `ClaimStateMachine`) would close the asymmetry between this module's scattered entity-guard transitions and Claim's centralized, auditable approach.

---

## medicaltaxonomy

- **Purpose:** Pure reference-data hierarchy of medical categories and services — explicitly "no coverage, claim, or provider logic here."
- **Responsibilities:** Category tree (self-referencing + multi-root closure table), service catalog, specialty/alias lookups, Excel import/export of taxonomy.
- **Aggregate Root:** `MedicalCategory`.
- **Entities:** `MedicalCategory`, `MedicalService`, `MedicalServiceCategory` (join), `MedicalSpecialty`, `ServiceAlias`.
- **APIs:** `MedicalCategoryController` (canonical `getServicesByCategory` — "the ONLY way to retrieve services for selection"), `MedicalCategoryExcelController`.
- **DB Tables:** `medical_categories`, `medical_category_roots`, (service/specialty/alias tables not enumerated in migration pass but implied by entity names).
- **Dependencies:** None inbound; consumed by BenefitPolicyRule, ProviderContractPricingItem, Claim/ClaimLine (category selection), Visit.
- **Current Maturity:** 🟢 Mature — schema cleaned up in V35 (dropped orphaned legacy columns), seed data reconciled twice (V25, V57), architectural "category-first" rule is consistently enforced via API design.
- **Future Improvements:** Read-role gap — CRUD read endpoints exclude `EMPLOYER_ADMIN`, `ACCOUNTANT`, `FINANCE_VIEWER`, `DATA_ENTRY`, which may prevent those roles from resolving service names in their own claim views; worth an access-policy review.

---

## benefitpolicy

- **Purpose:** Canonical, single source of truth for coverage decisions — coverage percentages, annual/lifetime/family limits, waiting periods, pre-approval requirements, per employer.
- **Responsibilities:** Policy definition, per-category rule overrides, coverage resolution algorithm, limit-usage calculation, waiting-period validation, reusable rule templates.
- **Aggregate Root:** `BenefitPolicy`.
- **Entities:** `BenefitPolicy`, `BenefitPolicyRule`, `BenefitPolicyTemplate`, `BenefitPolicyTemplateRule`.
- **APIs:** `BenefitPolicyController`, `BenefitPolicyRuleController`.
- **DB Tables:** `benefit_policies`, `benefit_policy_rules`, `benefit_policy_templates`, `benefit_policy_template_rules`.
- **Dependencies:** Employer (parent). Consumed by Eligibility, Claim (coverage/limit/waiting-period validation), Visit (claim-creation gate), PreAuthorization (PA-requirement source).
- **Current Maturity:** 🟡 Functional but architecturally contested — the service itself is well-engineered (DB-side SUM aggregation for performance, documented priority-resolution algorithm), but (a) its own Javadoc describes a 3-tier priority (`SERVICE_RULE > CATEGORY_RULE > POLICY_DEFAULT`) that no longer matches the 2-tier implementation after service-level rules were removed upstream, and (b) it competes with a second, parallel `claim.ruleengine.*` coverage system whose relationship to this "canonical" service is unclear.
- **Future Improvements:** **Highest-priority reconciliation item in the codebase** — either retire `claim.ruleengine.*` or explicitly document a division of labor between the two systems; update the stale Javadoc; audit the "internal staff backlog bypass" logic for its security implications (privilege-sensitive date-window bypass embedded in a shared coverage service).

---

## eligibility

- **Purpose:** Rule-chain evaluation of whether a member may receive care right now, producing an immutable audit trail.
- **Responsibilities:** Sequential rule evaluation (hard/soft failure semantics), coverage-snapshot embedding, family eligibility.
- **Aggregate Root:** `EligibilityCheck` (append-only audit record).
- **Entities:** `EligibilityCheck`.
- **APIs:** `EligibilityController`.
- **DB Tables:** `eligibility_checks`.
- **Dependencies:** Member, BenefitPolicy (via BenefitPolicyCoverageService), Provider (optional), Visit (optional).
- **Current Maturity:** 🟢 Mature — genuinely pluggable rule-engine design (ordered Spring beans), correct denormalized-snapshot audit pattern, exception-safe (audit-log failures never block the actual decision).
- **Future Improvements:** Confirm and document the family-eligibility composition logic; consider unifying the two frontend entry points' underlying QR/lookup logic into one shared hook/component.

---

## visit

- **Purpose:** The central linking entity of the Visit-Centric architecture — a single patient encounter that is the mandatory parent of every Claim and PreAuthorization.
- **Responsibilities:** Encounter registration, provider/category/service selection, status progression, provider-ID security enforcement.
- **Aggregate Root:** `Visit`.
- **Entities:** `Visit`, `VisitAttachment`.
- **APIs:** `VisitController`, `VisitAttachmentController`.
- **DB Tables:** `visits`, `visit_attachments`.
- **Dependencies:** Member (required), Employer (denormalized), Provider (loose). Upstream of Claim, PreAuthorization, EligibilityCheck (all 1:many, cascade ALL).
- **Current Maturity:** 🔴 Fragile in one specific, high-impact way — the hard-delete-with-cascade-ALL issue (see `02-business-workflows.md` §5) is a genuine data-integrity risk in an otherwise reasonably mature module (real state enum, optimistic locking, strong provider-ID security enforcement).
- **Future Improvements:** **Immediate-priority fix candidate** — convert `VisitService.delete()` to soft-delete or add a guard blocking deletion when non-DRAFT claims exist; confirm/implement the missing `→PREAUTH_APPROVED`/`→COMPLETED` transition paths; verify the provider-contract-at-visit-creation check.

---

## preauthorization

- **Purpose:** Pre-service approval request for medical services flagged as requiring pre-authorization; reserves (does not deduct) against member limits.
- **Responsibilities:** PA request lifecycle, two-phase reservation (soft-hold vs. hard-deduct-on-claim), email-in PA request pipeline, expiry management.
- **Aggregate Root:** `PreAuthorization`.
- **Entities:** `PreAuthorization`, `PreAuthorizationAudit`, `PreAuthEmailRequest`, `PreAuthEmailAttachment`.
- **APIs:** `PreAuthorizationController`, `PreAuthorizationAuditController`, `PreAuthDashboardController`, `EmailPreAuthController`, `PreAuthEmailRequestController`.
- **DB Tables:** `preauthorization_requests`, `pre_authorizations`, `pre_authorization_attachments`, `pre_authorization_audit`, `pre_auth_email_requests`, `pre_auth_email_attachments`.
- **Dependencies:** Visit (parent, nullable for email-in requests), Member/Provider (loose FK). Referenced by Claim.
- **Current Maturity:** 🟠 Emerging relative to its financial importance — sound two-phase reservation design, but (a) no centralized state-machine class unlike Claim, (b) the operational `pre_authorizations` table has **zero FK constraints** on any of its relational columns, and (c) `claims.pre_authorization_id` points at `preauthorization_requests` (a different table than the one described as "the real working preauth table" in code comments) — a possible FK-target mismatch worth urgent verification.
- **Future Improvements:** Verify the `claims.pre_authorization_id → preauthorization_requests` vs. `pre_authorizations` question — this is a candidate for a real bug, not just a documentation gap; add FK constraints; build a `PreAuthorizationStateMachine`.

---

## claim

- **Purpose:** The system's core value-delivery module — reimbursement requests, financial calculation, medical/financial review, state machine.
- **Responsibilities:** Claim + ClaimLine lifecycle, financial-identity calculation and validation, coverage/limit enforcement (in coordination with benefitpolicy), rule-engine sub-module, drafts, attachments, rejection reasons, reviewer-scope assignment, coverage-engine admin.
- **Aggregate Root:** `Claim` (with `ClaimLine` as a true child entity, cascade ALL/orphanRemoval).
- **Entities:** `Claim`, `ClaimLine`, `ClaimBatch`, `ClaimAttachment`, `ClaimHistory`, `ClaimRejectionReason`, `ClaimDraft`, rule-engine entities (`ClaimCoverageRule`, `ClaimRuleExecutionAudit`).
- **APIs:** `ClaimController`, `ClaimBatchController`, `ClaimDraftController`, `ClaimAttachmentController`, `ClaimRejectionReasonController`, `CoverageEngineController`, `MedicalReviewerProviderAssignmentController`, `ReviewerScopeController`, `ReportsController` (claim-scoped), `ClaimCoverageRuleAdminController`.
- **DB Tables:** `claim_batches`, `claims`, `claim_lines`, `claim_attachments`, `claim_history`, `claim_audit_logs`, `claim_rejection_reasons`, `claim_drafts`, `claim_coverage_rules`, `claim_rule_execution_audit`, `medical_audit_logs`.
- **Dependencies:** Visit (mandatory), Member, PreAuthorization (optional), ProviderContract (pricing), BenefitPolicy/BenefitPolicyRule (coverage). Upstream of Settlement (event-driven).
- **Current Maturity:** 🟡 Functional-but-contested — this is simultaneously the **most rigorously engineered module** (hard financial-identity guards, optimistic locking with an explicit double-deduction rationale, comprehensive snapshot/audit design, a real centralized state machine with role policy) **and** the module with the **most confirmed internal inconsistencies** (two state-machine sources of truth disagreeing, an unreachable documented transition, a hardcoded 20% co-pay fallback, a parallel rule-engine of uncertain relationship to `benefitpolicy`). It is mature in engineering discipline, fragile in internal consistency.
- **Future Improvements:** Reconcile `ClaimStatus.getValidTransitions()` with `ClaimStateMachine.TRANSITION_MATRIX` (pick one source of truth, delete or clearly mark the other as illustrative-only); resolve the `REJECTED → APPROVED` reachability question with product; replace the hardcoded 20% co-pay fallback with a policy-derived default; decide the fate of `claim.ruleengine.*` relative to `BenefitPolicyCoverageService`; correct the stale role-name table in `ClaimStateMachine`'s own class Javadoc.

---

## settlement

- **Purpose:** Track provider running balances and record actual payments — monthly aggregate reconciliation, not strictly per-claim.
- **Responsibilities:** Provider account balance integrity, payment recording with override/audit controls, event-driven sync from claim approval/reversal.
- **Aggregate Root:** `ProviderAccount`.
- **Entities:** `ProviderAccount`, `AccountTransaction`, `PaymentRecord`, `PaymentAuditLog`.
- **APIs:** `ProviderAccountController`, `PaymentController`.
- **DB Tables:** `provider_accounts`, `account_transactions`, `payment_records`, `payment_audit_logs`.
- **Dependencies:** Claim (event source: `ClaimApprovedEvent`, `ClaimReversalEvent`), Provider, Employer.
- **Current Maturity:** 🟡 Functional with one serious historical scar — the balance-invariant enforcement (`assertBalanceInvariant`) and double-entry-style DB CHECK constraints on `account_transactions` show real financial-engineering rigor, but `payment_records`/`payment_audit_logs` were **destructively dropped and recreated** in migration V66 after an incomplete V63 rename — a data-loss-risking pattern for a financial-audit table. No FK constraints on `payment_records.employer_id`/`provider_id`.
- **Future Improvements:** Confirm no production data was lost in the V66 recreation; add missing FK constraints; replace the exception-swallowing employer/provider name lookup in `PaymentService.mapToDto()` with a surfaced data-integrity warning.

---

## audit

- **Purpose:** Immutable, legal-grade audit trail specifically for medical/claim decisions.
- **Responsibilities:** Insert-only audit log with JSONB before/after diffs, correlation IDs, controlled bulk-delete with step-up password re-authentication.
- **Aggregate Root:** `AuditLog` (medical, table `medical_audit_logs`).
- **Entities:** `AuditLog` (this module's variant).
- **APIs:** `MedicalAuditLogController`.
- **DB Tables:** `medical_audit_logs` (DB trigger blocks UPDATE unconditionally; DELETE was blocked until V54, which relaxed it to allow SUPER_ADMIN purges).
- **Dependencies:** Written to by Claim, ClaimReview, ClaimStateMachine services.
- **Current Maturity:** 🟡 Functional but not unified — a second, independently-designed, **mutable** audit log exists in `systemadmin` (see below) with no shared interface or AOP-based automatic capture; each module decides ad hoc which trail (if either) to write to.
- **Future Improvements:** Define a single `AuditService` abstraction (or explicitly document why two are needed) to eliminate the risk of modules silently writing to neither.

---

## report

- **Purpose:** Claim statements and financial consolidation reporting, HTML/PDF export.
- **Responsibilities:** PDF rendering (OpenPDF + Flying Saucer + Thymeleaf, Arabic font embedding), financial consolidation and company-profit reports.
- **Aggregate Root:** N/A (stateless reporting service).
- **Entities:** None (reads across modules).
- **APIs:** `ReportController` (`/api/reports/claims/{html,pdf}`), `FinancialReportController` (`/api/v1/reports/financial-consolidation`, `/company-profit`).
- **DB Tables:** None owned; reads `claims`, `payment_records`, etc.
- **Dependencies:** Claim, Settlement, Member, Provider.
- **Current Maturity:** 🟠 Emerging — functional PDF pipeline with real Arabic/RTL support, but the font-loading failure mode is silent rather than loud, and the PDF controller's exception handling bypasses the system's standard error contract.
- **Future Improvements:** Fail loudly (and log clearly) if the Arabic font resource can't be loaded rather than silently rendering with a fallback font; route exceptions through `GlobalExceptionHandler` for a consistent `ApiError` response.

---

## pdf

- **Purpose:** Company branding configuration for PDF documents (logo, header/footer) — distinct from the `report` module which does the actual rendering.
- **Responsibilities:** `PdfCompanySettings` CRUD, active-settings lookup for pre-login branding (e.g. login page).
- **Aggregate Root:** `PdfCompanySettings`.
- **Entities:** `PdfCompanySettings`.
- **APIs:** `PdfCompanySettingsController`.
- **DB Tables:** `pdf_company_settings`.
- **Dependencies:** Consumed by `report` module's rendering pipeline.
- **Current Maturity:** 🟠 Emerging — small, focused module; the `@PreAuthorize("isAuthenticated()")` on a controller meant to also serve pre-login branding requests is a potential design contradiction worth verifying doesn't produce a 401 loop.
- **Future Improvements:** Confirm the pre-login branding call path actually works end-to-end (likely needs a `permitAll` carve-out for the `active` settings endpoint specifically).

---

## rbac

- **Purpose:** User identity, static role assignment, security-relevant self-service (password reset, email verification), and administrative user audit logging.
- **Responsibilities:** User CRUD (SUPER_ADMIN-only), password/token security (hashed tokens), lockout tracking, login-attempt logging.
- **Aggregate Root:** `User`.
- **Entities:** `User`, `PasswordResetToken`, `EmailVerificationToken`, `UserAuditLog`, `UserLoginAttempt`.
- **APIs:** `UserController` (class-level `hasRole('SUPER_ADMIN')`).
- **DB Tables:** `users`, `password_reset_tokens`, `email_verification_tokens`, `user_audit_log`, `user_login_attempts`.
- **Dependencies:** Referenced by Employer/Provider (scope), audit modules.
- **Current Maturity:** 🟡 Functional — genuinely good security hygiene for token storage (SHA-256+Base64 hashed, raw token only ever in the email), but the `users` table itself carries a non-normalized boolean-flag permission model (`can_view_claims`, etc.) alongside the static-role system, and `UserController`'s own Javadoc references a non-existent `INSURANCE_ADMIN` role.
- **Future Improvements:** Decide whether the boolean feature-flag columns on `users` are still load-bearing or vestigial; correct the stale Javadoc; reconcile `user_login_attempts`' duplicate column pairs (`attempt_result`/`success`, etc. — see `05-database-analysis.md`).

---

## auth

- **Purpose:** Authentication entry points — session/cookie login (primary) and JWT login (legacy fallback), registration, password reset (dual OTP/token flows), email verification.
- **Responsibilities:** Credential validation, token issuance, role-binding validation at login time.
- **Aggregate Root:** N/A (service-oriented).
- **Entities:** None owned directly (operates on `rbac.User`).
- **APIs:** `AuthController` — `/session/login`, `/session/me`, `/session/logout`, `/login`, `/register`, `/me`, `/forgot-password`+`/reset-password` (OTP), `/token/forgot-password`+`/token/reset-password`, `/password-reset-config`, `/verify-email`, `/resend-verification`, `/refresh-token`, `/users/me/password`.
- **DB Tables:** None owned (reads/writes `rbac` tables).
- **Dependencies:** rbac (User), security (JWT/session infrastructure).
- **Current Maturity:** 🟠 Emerging in consistency, though individually functional endpoints — two coexisting password-reset UX flows (OTP vs. token-link) behave differently on success (only the token flow unlocks a locked account); `/register` grants immediate `DATA_ENTRY`-equivalent access with no confirmed email-verification gate at registration time.
- **Future Improvements:** Unify the two reset flows' post-success side effects (both should unlock the account); confirm/restrict `/register`'s real-world exposure and access grant.

---

## admin

- **Purpose:** System-level administrative operations (distinct from `systemadmin` — a naming overlap worth resolving).
- **Responsibilities:** `SystemAdminController` — not deep-dived in this pass; recommend a targeted follow-up given the name collision with `systemadmin`.
- **Current Maturity:** 🟠 Emerging (under-researched in this pass — flagged for follow-up, not a defect finding).
- **Future Improvements:** Clarify the `admin` vs. `systemadmin` module boundary — either merge them or rename one to remove the ambiguity for future maintainers.

---

## systemadmin

- **Purpose:** Administrative configuration and general-purpose audit logging — feature flags, module access control, email settings, general user/role administration audit trail.
- **Responsibilities:** `AuditLog` (mutable variant, table `audit_logs`), feature flag toggles, module access management, email SMTP/IMAP configuration, password change endpoint.
- **Aggregate Root:** N/A (config-oriented, multiple small aggregates).
- **Entities:** `AuditLog` (systemadmin variant — **not** the same class as `modules.audit.AuditLog`), feature flags, module access records.
- **APIs:** `ChangePasswordController`, `EmailSettingsController`, `FeatureFlagController`, `ModuleAccessController`, `SystemController`, `UserManagementController`.
- **DB Tables:** `audit_logs`, `feature_flags`, `module_access`, `email_settings`, `system_settings`.
- **Dependencies:** Consumed by `common.guard.FeatureGuard`; writes audit entries on behalf of `UserManagementService`, `ModuleAccessService`, and cross-module callers (`VisitService`, `MedicalReviewerProviderAssignmentService`).
- **Current Maturity:** 🟡 Functional — DB-first feature-flag design with an `application.yml` fallback and an internal-staff bypass is a solid pattern; the **naming and semantic overlap with `modules.audit`** (two differently-designed audit logs, one mutable one immutable, both called `AuditLog`) is the standout structural issue.
- **Future Improvements:** Resolve the two-audit-log-classes-same-name situation explicitly (rename one, e.g. `AdminAuditLog` vs `MedicalAuditLog`, to eliminate ambiguity even if both systems are intentionally kept).

---

## dashboard

- **Purpose:** Aggregated KPI/analytics endpoints for the main admin console landing page.
- **Responsibilities:** Summary stats, monthly trends, member growth, cost-by-provider, service distribution, recent activity feed.
- **Aggregate Root:** N/A (read-only aggregation service).
- **Entities:** None owned.
- **APIs:** `DashboardController` — `/summary`, `/monthly-trends`, `/members-growth`, `/cost-by-provider`, `/service-distribution`, `/recent-activities`, legacy `/stats`, `/claims-per-day`.
- **DB Tables:** None owned; reads across Claim, Member, Provider, Visit.
- **Dependencies:** Claim, Member, Provider, Visit, Settlement.
- **Current Maturity:** 🟡 Functional — broad role access (essentially all authenticated roles), but the legacy endpoints' reliance on an `X-Employer-ID` request header for scoping (rather than deriving strictly from the session's `employerId`) is an unresolved question flagged for security follow-up.
- **Future Improvements:** Verify/harden the `X-Employer-ID` header handling in `DashboardService`; consider retiring the two "legacy" endpoints once confirmed unused by the current frontend.

---

## Cross-Cutting: `security/`

- **Purpose:** Authentication (dual session+JWT) and static-role authorization infrastructure for the entire system.
- **Responsibilities:** `SecurityConfig` (filter chain, CORS, CSRF-disabled-with-SameSite-cookie rationale), `JwtTokenProvider`/`JwtAuthenticationFilter`, `SessionAuthenticationFilter` (preferred path), `CustomUserDetailsService`, `AuthorizationService` (service-layer data-boundary enforcement, "SUPER_ADMIN god mode"), `ProviderContextGuard` (anti-tampering for provider-scoped requests), `SystemRole` (the 7-role enum).
- **Current Maturity:** 🟡 Functional with one critical gap — genuinely thoughtful defense-in-depth in several places (provider-context enforcement, custom 401 entry point, Actuator/Swagger locked to SUPER_ADMIN, redacted `IllegalArgumentException` messages) undermined by a **~10-year JWT expiration with no revocation mechanism**, which is the single most consequential security finding in this entire analysis.
- **Future Improvements:** See `08-technical-debt.md` and `09-improvement-roadmap.md` for prioritized remediation — shortening JWT expiry and/or adding a denylist is the top recommendation.

---

## Cross-Cutting: `common/`

- **Purpose:** Shared infrastructure — DTOs (`ApiResponse`), error handling (`ApiError`, `GlobalExceptionHandler`), email (two competing implementations), Excel/file utilities, validation, deletion guards, feature guards, architectural-rule enforcement helpers.
- **Current Maturity:** 🟡 Functional — `GlobalExceptionHandler` is genuinely comprehensive and well-hardened (no stack-trace leakage, Arabic-translated constraint-violation messages); `DeletionGuard`/`FeatureGuard` are good DRY patterns. The **two independent email service implementations** (`common.email` vs `core.email`) with different dev/prod safety behavior is the standout issue, alongside the **two structurally different response envelopes** (`ApiResponse` success shape vs. `ApiError` failure shape).
- **Future Improvements:** Consolidate to one email service (or clearly partition responsibilities and rename to remove ambiguity); consider evolving toward one unified response envelope shape (can be done additively — see `09-improvement-roadmap.md`).

---

*Continue to [`04-api-catalog.md`](./04-api-catalog.md) for the full endpoint inventory.*
