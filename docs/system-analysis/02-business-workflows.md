# 02 — Business Workflows

> Every workflow below is documented as **observed in code** (entity guards, service validations, controller role checks), not as originally specified. Where documentation and code diverge, both are reported.

---

## 1. Employer Workflow

**Purpose:** Onboard and maintain the corporate clients who purchase group coverage. Employer is the top-level tenant boundary for members, benefit policies, and (historically) provider contracts.

**Actors:** SUPER_ADMIN (full CRUD), EMPLOYER_ADMIN (own-employer visibility).

**Entry Point:** `EmployerController` (`/api/v1/employers`), frontend `pages/employers/`.

**Business Rules:**
- `code` is unique and immutable-in-practice (business identifier).
- `maxMemberLimit` (nullable = unlimited) is intended to cap enrollment, though the research pass could not confirm active enforcement in `MemberService` — **flag for verification**.
- `isDefault` flag marks a fallback employer (partial unique index ensures only one).
- No status/lifecycle enum — just a boolean `active` (simple on/off), unlike the richer state machines elsewhere in the domain.

**State Transitions:** None (binary active/inactive only).

**Outputs:** Employer record consumed by Member, BenefitPolicy, ProviderAllowedEmployer, PaymentRecord, User (scope).

**Dependencies:** Downstream of nothing; upstream of almost everything (Member, BenefitPolicy, Visit denormalize `employer_id`).

**Pain Points:**
- `provider_contracts.employer_id` FK was **removed** in migration V34 — contracts are no longer employer-scoped at the DB level, a notable business-model shift (from "employer negotiates provider pricing" to "TPA negotiates provider pricing globally") that isn't narrated anywhere in the docs — worth confirming this was an intentional product decision, not an accidental cleanup casualty.
- No enrollment-cap enforcement confirmed for `maxMemberLimit`.

---

## 2. Member Workflow

**Purpose:** Enroll and maintain insured individuals (principal employees + dependents) under an Employer's benefit policy; manage card/barcode issuance; support bulk import and duplicate/kinship resolution.

**Actors:** SUPER_ADMIN, EMPLOYER_ADMIN (own employer), DATA_ENTRY.

**Entry Point:** `UnifiedMemberController` + `BeneficiarySearchController`, `MemberImportController`, `MemberDuplicateController`, `KinshipMismatchController`; frontend `pages/members/` ("Unified" architecture — principal + dependents merged into one self-referencing entity, replacing an earlier Member/FamilyMember split).

**Business Rules:**
- Self-referencing `parent_id`: `parent == null` → PRINCIPAL, else DEPENDENT with a `relationship` enum.
- A PRINCIPAL member **cannot be persisted without a barcode** (`@PrePersist/@PreUpdate validateState()`, throws `IllegalStateException`).
- `benefitPolicy` auto-assigned from the employer's active policy if not explicitly set — a side-effecting "validation" step (`BenefitPolicyCoverageService.validateMemberHasActivePolicy()`) that silently persists a resolved policy assignment.
- `MemberStatus`: `ACTIVE, SUSPENDED, TERMINATED, PENDING`. `CardStatus`: `ACTIVE, INACTIVE, BLOCKED, EXPIRED` — two independent lifecycle dimensions (member enrollment vs. physical card).
- Internal-staff "backlog" bypass: ignores policy effective-date windows for historical data entry — an intentional operational escape hatch embedded directly in shared coverage-resolution code.

**State Transitions:** No centralized state-machine class (unlike Claim) — status changes appear to be direct field sets by services, not guarded transitions.

**Outputs:** Member records consumed by Visit, Claim, EligibilityCheck, PreAuthorization.

**Dependencies:** Employer (required), BenefitPolicy (auto-resolved).

**Pain Points:**
- Duplicate identifier columns (`civil_id` deprecated-but-kept alongside `national_number`) — a data-quality risk (two "unique person" identifiers that can drift).
- No centralized state machine for `MemberStatus`/`CardStatus` transitions, unlike the rigor applied to Claim — inconsistent engineering investment across similarly-important lifecycles.
- Bulk import (`MemberExcelImportService`), duplicate detection (`MemberDuplicateService`), and kinship-mismatch auto-fix (`KinshipMismatchService`) are large, business-rule-dense services that were flagged but not deep-dived in this pass — recommended as a dedicated follow-up given they directly affect data integrity for the rest of the system.

---

## 3. Provider Workflow

**Purpose:** Onboard and manage healthcare providers (hospitals, clinics, labs, pharmacies, radiology) who deliver services and submit claims/pre-auths; control network access per employer.

**Actors:** SUPER_ADMIN (full CRUD), PROVIDER_STAFF (own-provider visibility via the Provider Portal).

**Entry Point:** `ProviderController`, `ProviderPortalController`, `ProviderDocumentController`, `ProviderExcelController`; frontend `pages/providers/` (admin CRUD) and `pages/provider/` (provider-facing portal — visit-centric, not classic CRUD).

**Business Rules:**
- `providerType`: HOSPITAL, CLINIC, LAB, PHARMACY, RADIOLOGY.
- `networkStatus` (`NetworkTier`): IN_NETWORK, OUT_OF_NETWORK, PREFERRED.
- `allowAllEmployers` boolean + `allowedEmployers` whitelist controls which employers' members a provider may serve.
- `defaultDiscountRate` is `@Deprecated` in favor of `ProviderContract.discountPercent` — legacy field retained for backward compatibility, not for new logic.
- `hasDocuments` is a computed `@Formula` (SQL subquery) rather than a maintained flag.

**State Transitions:** None on Provider itself (status flags, not a workflow state machine). The real lifecycle complexity lives in `ProviderContract` (see Provider Contract section below, folded into this workflow since it's inseparable in practice).

**Outputs:** Provider consumed by ProviderContract (1:many, cascade ALL), Visit/Claim/PreAuthorization (loose `providerId` reference, not JPA-mapped — deliberately decoupled), ProviderAccount (1:1, settlement).

**Dependencies:** None upstream; downstream of nothing except Employer (via allow-list).

**Pain Points:**
- `VisitController`'s Swagger documentation claims "provider must have an active contract with the member's company" is enforced at visit creation — this check was **not found** in `VisitService.create()`. Either it's enforced deeper in a code path not traced, or the documentation is aspirational/stale.
- Provider Contract's documented invariant "only ONE active contract per provider" has a supporting partial unique index at the DB level (`uq_active_contract_per_provider`) — this is actually enforced, contrary to the entity-level Javadoc-only impression; good defense-in-depth once confirmed.
- No centralized `ProviderContractStateMachine` (unlike Claim's `ClaimStateMachine`) — transition guards (`canActivate()`, `canSuspend()`, `canTerminate()`) exist as entity predicates, but actual transition/audit logic is presumably scattered in the service layer.

---

## 4. Eligibility Workflow

**Purpose:** At time of visit or on-demand, determine whether a member is eligible for care under their policy — validates member/card/policy status, waiting periods, coverage-period effectiveness, and service date — and produces an immutable, auditable result.

**Actors:** PROVIDER_STAFF (checks before registering a visit), internal staff (ad hoc checks), the system itself (invoked during Visit/Claim creation).

**Entry Point:** `EligibilityController`, `UnifiedEligibilityController`; frontend `pages/eligibility/EligibilityCheckPage.jsx` (back-office) and `pages/provider/ProviderEligibilityCheck.jsx` (provider portal — both integrate `html5-qrcode` for barcode-based member lookup, likely intentional duplication for two personas).

**Business Rules — rule chain (`EligibilityEngineServiceImpl`):**
1. Rules are Spring beans (`EligibilityRule` interface), auto-collected, ordered by priority: MemberExists → MemberActive → MemberCardValid → MemberEnrollment → PolicyExists → PolicyActive → PolicyCoveragePeriod → WaitingPeriod → ServiceDateValid.
2. **Hard-rule failure** stops evaluation immediately → not-eligible.
3. **Soft-rule failure** accumulates as a warning; evaluation continues.
4. Any rule exception is itself treated as a hard `SYSTEM_ERROR` failure.
5. Result is **always persisted** to `EligibilityCheck` (immutable audit record), even on the exception path; if the audit-save itself fails, that failure is swallowed so it never blocks the actual eligibility decision returned to the caller.
6. If a medical category/service is specified, coverage snapshot (coverage %, used/remaining amount, pre-approval requirement) is resolved via `BenefitPolicyCoverageService` and embedded in the result.

**State Transitions:** N/A — each check is a one-shot, immutable event, not a stateful object.

**Outputs:** `EligibilityCheck` record (audit trail, optionally linked to a `Visit` via `eligibilityCheckId`); a pass/fail decision with coverage snapshot returned to the caller.

**Dependencies:** Member, BenefitPolicy/BenefitPolicyRule (via `BenefitPolicyCoverageService`), Provider (optional), Visit (optional back-link).

**Pain Points:**
- Two front-end entry points (back-office vs. provider portal) implementing similar QR/barcode-lookup logic — risk of behavioral drift between them over time if not kept in sync deliberately.
- No confirmation in this pass of exactly how "family eligibility" (`FamilyEligibilityResponse` DTO exists) composes with the single-member rule chain — recommended follow-up.

---

## 5. Visit Workflow

**Purpose:** Register a single patient encounter (member + provider + medical category/service + date) — the **mandatory root** of every Claim and PreAuthorization under the system's Visit-Centric architecture.

**Actors:** PROVIDER_STAFF (primary — registers visits at point of care), DATA_ENTRY, SUPER_ADMIN/MEDICAL_REVIEWER (oversight).

**Entry Point:** `VisitController` (`/api/v1/visits`), `VisitAttachmentController`; frontend `pages/visits/` (admin CRUD) and `pages/provider/ProviderVisitLog.jsx` (provider-facing, more central to actual usage per file size/prominence).

**Business Rules:**
- Category **must** be selected before service (architectural rule, enforced by API design — `MedicalCategoryController.getServicesByCategory` is documented as "the ONLY way to retrieve services for selection").
- Visit creation requires the member to have an active/effective BenefitPolicy on the visit date (delegated to `BenefitPolicyCoverageService.validateCanCreateClaim()`).
- **Provider-ID server-side enforcement**: for PROVIDER_STAFF users, `providerId` is always overwritten server-side from the authenticated session context regardless of what the request body contains (`ProviderContextGuard`) — logged as a potential-security-issue warning on mismatch. SUPER_ADMIN/INSURANCE_ADMIN-equivalent users must supply a valid `providerId`.
- Optimistic locking (`@Version`) explicitly to prevent "concurrent status update race conditions."

**State Transitions (`VisitStatus`):**
```
REGISTERED → IN_PROGRESS → PENDING_PREAUTH → PREAUTH_APPROVED → CLAIM_SUBMITTED → COMPLETED
                                                                              ↘ CANCELLED (off-ramp, any non-terminal state)
```
- `allowsClaimCreation()`: true for REGISTERED, IN_PROGRESS, PREAUTH_APPROVED.
- `allowsPreAuthCreation()`: true for REGISTERED, IN_PROGRESS.
- Entity-level transition helpers exist for `→PENDING_PREAUTH` and `→CLAIM_SUBMITTED`; **no explicit transition to `PREAUTH_APPROVED` or `COMPLETED` was located** in the Visit entity — these likely happen in `PreAuthorizationService`/a completion job, not confirmed in this pass.

**Outputs:** Visit record; downstream Claim(s), PreAuthorization(s), EligibilityCheck(s) (1:many each).

**Dependencies:** Member (required), Employer (denormalized), Provider (loose reference), BenefitPolicy (via coverage validation).

**Pain Points (high priority):**
- `VisitService.delete()` performs a **hard delete** (`repository.deleteById`) with cascade `ALL` from Visit to Claims and EligibilityChecks — meaning deleting a Visit can cascade-hard-delete financial claim records, including potentially **SETTLED** ones. This directly contradicts the soft-delete discipline applied everywhere else in the financial domain (Claim, PreAuthorization, PaymentRecord all use soft-delete). This is the single highest-priority data-integrity risk identified in this workflow analysis and should be an early remediation candidate (see `09-improvement-roadmap.md`).
- The Swagger-documented provider-contract check at visit creation was not found enforced (see Provider workflow, above).
- No confirmed transition path to `PREAUTH_APPROVED`/`COMPLETED` — worth a targeted trace to `PreAuthorizationService`.

---

## 6. Claim Workflow (full lifecycle — the system's core)

**Purpose:** Request reimbursement for services rendered during a Visit; aggregate billed lines; compute approved/refused/co-pay/net-payable amounts against contract pricing and benefit-policy coverage; route through medical and financial review; on approval, credit the Provider's running account; eventually settle/pay.

**Actors:** PROVIDER_STAFF (submits), MEDICAL_REVIEWER (clinical review), ACCOUNTANT (financial review, approval, settlement), EMPLOYER_ADMIN (visibility/submission per role policy), SUPER_ADMIN (bypasses all role checks).

**Entry Point:** `ClaimController`, `ClaimBatchController`, `ClaimDraftController`, `ClaimAttachmentController`, `ClaimRejectionReasonController`, `CoverageEngineController`; frontend `pages/claims/` and `pages/claims/batches/` (batch-centric UI, not classic single-claim CRUD — `ClaimBatchEntry.jsx` is the single largest frontend file in the app at 2,085 lines, and `ProviderClaimsSubmission.jsx` in the provider portal is the largest overall at 2,561 lines).

**Business Rules:**
- **Mandatory Visit parent** — "Claims MUST always reference an existing Visit. No standalone claim creation allowed," enforced at `@PrePersist`.
- `requestedAmount` is always server-calculated as the sum of line totals — never trusted from the client.
- A line requiring pre-approval (per `BenefitPolicyRule.requiresPreApproval`) blocks claim creation unless a linked `PreAuthorization` exists — bypassable only for `isBacklog` historical claims.
- **Financial identity** (re-derived and validated on every persist, `Claim.validateFinancialIdentity()`):
  ```
  providerShare = requestedAmount − patientCoPay
  if discountBeforeRejection (contract default = true):
      netPayable = (providerShare − providerShare×discount%) − refusedAmount
  else:
      netPayable = (providerShare − refusedAmount) − (providerShare − refusedAmount)×discount%
  ```
  Deviation beyond a 0.05 rounding tolerance throws `IllegalStateException` — a hard financial guard, not just a UI validation.
- `patientCoPay` defaults to a **hardcoded 20%** of net-accepted amount if not explicitly set — a magic number that bypasses the policy's actual configured coverage percentage; flagged as a real financial-correctness risk (see `08-technical-debt.md`).
- Coverage/limit checks (annual, per-member, per-family, per-category) are resolved via `BenefitPolicyCoverageService` — see `07-domain-analysis.md` for the full algorithm.
- `appliedDiscountPercent` and `discountBeforeRejection` are **snapshotted** from the Provider Contract at approval time (not live-recalculated later).
- SLA fields (`expectedCompletionDate`, `slaDaysConfigured`) are snapshotted at claim-submission time from system config, so later SLA policy changes don't retroactively rewrite existing claims' due dates.
- `@Version` optimistic locking explicitly documented as protecting against "double deduction from member's balance."

**State Transitions (`ClaimStatus`, enforced by `ClaimStateMachine`):**
```
DRAFT → SUBMITTED → UNDER_REVIEW → APPROVAL_IN_PROGRESS → APPROVED → SETTLED
                          ↘ REJECTED (terminal, hard-locked)
                          ↘ NEEDS_CORRECTION → APPROVED
        (BATCHED is legacy-only, treated as ≈APPROVED)
```
Role policy for each transition (`ClaimStateMachine.TRANSITION_ROLE_POLICY`):

| Transition | Required Role(s) |
|---|---|
| DRAFT → SUBMITTED | EMPLOYER_ADMIN, ACCOUNTANT, PROVIDER_STAFF |
| SUBMITTED → UNDER_REVIEW | ACCOUNTANT, MEDICAL_REVIEWER |
| UNDER_REVIEW → APPROVAL_IN_PROGRESS / APPROVED / REJECTED | ACCOUNTANT, MEDICAL_REVIEWER |
| UNDER_REVIEW → NEEDS_CORRECTION | MEDICAL_REVIEWER only |
| NEEDS_CORRECTION → SUBMITTED / APPROVED | role-dependent per target |
| APPROVED → SETTLED / BATCHED | ACCOUNTANT only |
| APPROVED → NEEDS_CORRECTION | ACCOUNTANT, MEDICAL_REVIEWER |
| BATCHED → SETTLED / APPROVED | ACCOUNTANT only |

`SUPER_ADMIN` bypasses all role checks. Every successful transition writes a `STATUS_CHANGE` audit record.

**⚠️ Confirmed inconsistency:** `ClaimStatus.getValidTransitions()` (the enum's own documentation of legal transitions) and `ClaimStateMachine.TRANSITION_MATRIX` (what's actually enforced at runtime) **disagree** — e.g. the state machine allows `UNDER_REVIEW → APPROVED` directly while the enum does not list it. Separately, both sources describe `REJECTED → APPROVED` as an allowed "admin re-edit" path, but `ClaimStateMachine.validateFinalStateLock()` unconditionally blocks any transition *out of* `REJECTED` — meaning this documented path is **currently unreachable in practice**. This needs a product/architect decision: either restore the re-edit capability, or correct the documentation to state rejected claims are permanently terminal.

**Outputs:** Approved claims → `ClaimApprovedEvent` → credits `ProviderAccount.runningBalance`; settled claims carry `paymentReference`/`settledAt`/`paidAmount`.

**Dependencies:** Visit (mandatory), Member, PreAuthorization (optional), ProviderContract (pricing snapshot), BenefitPolicy/BenefitPolicyRule (coverage), ClaimBatch, Settlement (event-driven downstream).

**Pain Points:** See the state-machine inconsistency above (highest priority in the entire domain analysis), the hardcoded 20% co-pay fallback, and the two parallel coverage-engine implementations noted in `01-system-overview.md` §Weaknesses. Full detail in `07-domain-analysis.md` and `08-technical-debt.md`.

---

## 7. Medical Review Workflow

**Purpose:** Clinical review of a submitted claim's line items — verify medical necessity/appropriateness, reject individual lines with a reason code, and move the claim toward financial approval or back to the provider for correction.

**Actors:** MEDICAL_REVIEWER (primary), ACCOUNTANT (can also perform this transition per role policy), SUPER_ADMIN.

**Entry Point:** `ClaimController` (review sub-endpoints), `MedicalReviewerProviderAssignmentController`, `ReviewerScopeController`; frontend `pages/claims/ClaimViewMedicalReview.jsx` (1,249 lines) with `components/medical-review/UnifiedAttachmentViewer.jsx` (634 lines) for supporting document review.

**Business Rules:**
- Reviewer scope is assignable per-provider (`MedicalReviewerProviderAssignmentController`, `medical_reviewer_providers` table) — a reviewer may be restricted to specific providers rather than seeing all claims system-wide.
- `SUBMITTED → UNDER_REVIEW` requires the claim to be "complete" (has lines, member, providerId, service date) before review can begin.
- `UNDER_REVIEW → NEEDS_CORRECTION` is **MEDICAL_REVIEWER-only** — the one transition in the whole state machine restricted to a single role, signaling this is considered a clinically-privileged judgment call.
- Per-line rejection: `ClaimLine.rejected`, `rejectionReasonCode` (from `claim_rejection_reasons` lookup table), `manualRefusedAmount`.
- Every review action writes a `STATUS_CHANGE`/audit entry via `MedicalAuditLogService`.

**State Transitions:** Subset of the Claim state machine — `SUBMITTED → UNDER_REVIEW → {APPROVAL_IN_PROGRESS | REJECTED | NEEDS_CORRECTION}`.

**Outputs:** Reviewed claim (with per-line rejection annotations) ready for financial approval, or returned to `NEEDS_CORRECTION` for provider resubmission.

**Dependencies:** Claim, ClaimLine, `claim_rejection_reasons` lookup, `medical_audit_logs`.

**Pain Points:** The `MedicalAuditLogController` exposing full claim audit trails to any `MEDICAL_REVIEWER` (not just SUPER_ADMIN) may be broader read access than strictly necessary — worth a policy review, not necessarily a defect.

---

## 8. Financial Review Workflow

**Purpose:** Final financial sign-off on a medically-reviewed claim — confirm approved amounts, apply contract discount and co-pay calculations, and move the claim to `APPROVED` (ready for settlement).

**Actors:** ACCOUNTANT (primary and exclusive for the final APPROVED→SETTLED step), MEDICAL_REVIEWER (shares `UNDER_REVIEW`→`APPROVED` authority), SUPER_ADMIN.

**Entry Point:** Same `ClaimController` review/approve sub-endpoints as Medical Review (they share the state machine); frontend `pages/claims/batches/ClaimBatchDetail.jsx` (1,170 lines) is the primary financial-review surface for batch-submitted claims.

**Business Rules:**
- `→APPROVED` guard requires: ≥1 line, `totalApproved > 0`, all lines "calculated" (quantity/unitPrice/totalPrice non-null), `pendingRecalculation == false`, and a fresh coverage snapshot on every line.
- At approval, `appliedDiscountPercent` and `discountBeforeRejection` are frozen from the current Provider Contract state (see Claim workflow financial-identity formula).
- `companyDiscountAmount` is always system-calculated, never user-entered.

**State Transitions:** `UNDER_REVIEW/APPROVAL_IN_PROGRESS → APPROVED`, `APPROVED → SETTLED` or `→BATCHED` (ACCOUNTANT-only for the settlement step).

**Outputs:** `ClaimApprovedEvent` domain event → triggers Settlement workflow (provider account credit).

**Dependencies:** Claim, ProviderContract (pricing/discount snapshot), Settlement module (event consumer).

**Pain Points:** Same state-machine inconsistencies as the Claim workflow; the synchronous-vs-async confusion in `ClaimApprovalEventListener`'s own comments (documented as async in one place, confirmed synchronous — `@TransactionalEventListener(phase=AFTER_COMMIT)`, no `@Async` — in another) is specifically relevant here since it determines whether provider-account crediting happens before or after the HTTP response returns to the accountant approving the claim.

---

## 9. Settlement Workflow

**Purpose:** Track the running financial balance owed to each Provider (derived from approved claims) and record actual monthly payments made — modeled as **monthly aggregate reconciliation** per employer/provider pair, not strictly 1:1 with individual claim settlement events.

**Actors:** ACCOUNTANT (exclusive — payment creation/edit/delete), FINANCE_VIEWER (read-only), SUPER_ADMIN.

**Entry Point:** `ProviderAccountController`, `PaymentController`; frontend `pages/settlement/ProviderAccountsList.jsx`, `ProviderAccountView.jsx` (1,068 lines), `ProviderPaymentsList.jsx`.

**Business Rules:**
- `ProviderAccount.runningBalance = totalApproved − totalPaid`, asserted as a hard invariant after every mutation (`assertBalanceInvariant()`, throws `IllegalStateException` on drift).
- `debit()` (payment recorded) **cannot overdraft** — throws if `amount > runningBalance`.
- `PaymentService.getMonthlySettlementSummaries()` aggregates claim `netProviderAmount` for claims in `{APPROVED, SETTLED}` status, grouped by employer/provider/year/month, nets out matched `PaymentRecord`s, and derives a status of `UNPAID` / `PARTIALLY_PAID` / `FULLY_PAID`.
- `addPayment()`: cannot exceed the remaining amount for that employer/provider/month **unless** `overrideLimit=true` **and** a `reason` is supplied — a genuine compliance/override control.
- `updatePayment()`: **always** requires a `reason` (stricter than `addPayment`, which only requires one on override).
- `deletePayment()`: soft-delete, mandatory `reason`.
- Every add/update/delete writes a `PaymentAuditLog` entry (old amount, new amount, reason).
- Event-driven: `ClaimApprovedEvent` → credit; a `ClaimReversalEvent`/listener exists for when an approved claim is later voided (decreases balance & totalApproved without touching totalPaid).

**State Transitions:** ProviderAccount `status`: ACTIVE / SUSPENDED / CLOSED (simple, no complex workflow).

**Outputs:** `PaymentRecord` entries; updated `ProviderAccount.runningBalance`; monthly settlement summaries feeding `04-` reports and the Financial Consolidation report.

**Dependencies:** Claim (event source), Provider, Employer (via PaymentRecord — though these FKs are **not** enforced at the DB level, see `05-database-analysis.md`).

**Pain Points:**
- The `payment_records`/`payment_audit_logs` tables were **destructively dropped and recreated** in migration V66 after an incomplete rename attempt in V63 — a data-loss-risking pattern in a financial-records table, discovered via the database analysis (`05-database-analysis.md`). If this happened against a populated production database, historical payment audit data may have been lost; worth confirming with whoever ran that migration.
- No FK constraints on `payment_records.employer_id`/`provider_id` — referential integrity here relies entirely on application discipline.
- `PaymentService.mapToDto()` fetches employer/provider names via raw JPQL wrapped in a swallow-all try/catch — a bad employer/provider ID silently shows a placeholder rather than surfacing a data-integrity error.

---

## 10. Reports Workflow

**Purpose:** Give each actor role-appropriate visibility into claims, financial, and beneficiary data — both interactive (dashboard) and exportable (HTML/PDF/Excel) formats.

**Actors:** All roles, scoped per report type (`@PreAuthorize` varies by endpoint — dashboard summary is open to essentially all authenticated roles; financial consolidation/company profit restricted to SUPER_ADMIN/ACCOUNTANT/FINANCE_VIEWER).

**Entry Point:** `DashboardController` (`/api/v1/dashboard/*`), `ReportController` (`/api/reports/claims/{html,pdf}`), `FinancialReportController` (`/api/v1/reports/financial-consolidation`, `/company-profit`), `ProviderReportsController`; frontend `pages/reports/` (`BeneficiariesReports.jsx` 1,105 lines, `FinancialReports.jsx` 1,052 lines, `ProviderSettlementReport.jsx` 863 lines) and provider-portal report pages (claims/pre-auth/visits reports scoped to the logged-in provider).

**Business Rules:**
- PDF generation renders Thymeleaf HTML through OpenPDF/Flying Saucer, embedding the Cairo font for Arabic/RTL text via `BaseFont.IDENTITY_H` — if the font resource fails to load, the code risks silently skipping font registration rather than failing loudly (a production-risk detail for Arabic report correctness, not confirmed to have occurred).
- Legacy dashboard endpoints (`/stats`, `/claims-per-day`) accept employer scoping via an **`X-Employer-ID` request header** rather than deriving it solely from the authenticated user's session-bound `employerId` — this needs explicit verification that `DashboardService` cross-checks the header against the authenticated `EMPLOYER_ADMIN`'s actual employer rather than trusting it outright (a potential employer-scope bypass if not cross-checked; flagged, not confirmed).

**State Transitions:** N/A — reports are read-only projections.

**Outputs:** HTML/PDF documents, dashboard JSON payloads, Excel exports (via ExcelJS/xlsx on the frontend).

**Dependencies:** Claim, Settlement, Member, Provider — reports are downstream consumers of nearly every other module's data.

**Pain Points:**
- `ReportController.getClaimReportPdf` swallows exceptions with `printStackTrace()` and returns a bare 500 outside the system's standard `ApiError` contract — inconsistent error handling for a user-facing export feature.
- Potential font-loading silent-failure risk for Arabic PDF rendering (see above).
- The `X-Employer-ID` header trust question, above, is the single most security-relevant open question in the Reports workflow and should be resolved before this document is treated as a closed finding.

---

*Continue to [`03-module-catalog.md`](./03-module-catalog.md) for a per-module technical breakdown.*
