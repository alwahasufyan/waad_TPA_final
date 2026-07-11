# 01 — System Overview

> Part of the **AlfaBeta TPA Engineering Framework (ATEF)** reference documentation.
> Status: analytical baseline, not a design proposal. No code was modified to produce this document.

---

## 1. What the System Does

**WaadCare / TBA-WAAD** (internal codebase name `tba-backend` / `tba-waad-system`) is a **Third Party Administrator (TPA) platform for health insurance claims processing**, built for the Libyan market (Arabic-first, RTL, local business conventions). A TPA sits between three parties:

- **Employers** — companies that purchase group medical coverage for their staff (the system's own documentation calls Employer "the ONLY business entity" / top-level tenant — a deliberate simplification that removed an earlier "Insurance Organization" concept).
- **Members** — the insured individuals (employees and their dependents) covered under an Employer's benefit policy.
- **Providers** — hospitals, clinics, labs, pharmacies, and radiology centers that render medical services to Members and submit claims for reimbursement.

The system's job is to answer, continuously and auditable: *is this member eligible for this service today, under this policy, at this provider, for how much, and who owes whom what as a result?* It does this across the full claim lifecycle: eligibility verification → visit registration → optional pre-authorization → claim submission → medical review → financial review/approval → provider settlement/payment.

## 2. Primary Business Objectives

1. **Correct, auditable adjudication of medical claims** against negotiated provider pricing and employer-specific benefit policies — this is the system's core value proposition and its most heavily engineered area (see `07-domain-analysis.md`).
2. **Enforce coverage rules deterministically** — annual/per-member/per-family limits, per-category coverage percentages, waiting periods, and pre-authorization requirements — so that a TPA can honor contracts at scale without manual recalculation.
3. **Maintain a defensible financial and medical audit trail** for every claim decision, given the regulatory/legal sensitivity of health insurance adjudication.
4. **Give each actor a workflow-appropriate interface**: providers submit visits/claims/pre-auths through a dedicated portal; internal staff (medical reviewers, accountants, admins) review and settle through an administrative console.
5. **Manage the provider network commercially** — contracts, negotiated pricing per medical category, and running account balances/payments — so the TPA can track what it owes each provider and reconcile payments monthly.

## 3. Main Actors

| Actor | System Role Constant | Primary Concerns |
|---|---|---|
| **Super Admin** | `SUPER_ADMIN` | Full unrestricted access ("god mode" by design — see `AuthorizationService`), system configuration, user management, audit log purges |
| **Employer Admin** | `EMPLOYER_ADMIN` | Own-employer visibility into members, claims, benefit policies |
| **Medical Reviewer** | `MEDICAL_REVIEWER` | Clinical/medical review of submitted claims (rejecting/approving lines on medical grounds) |
| **Accountant** | `ACCOUNTANT` | Financial review, claim approval/settlement, provider payments |
| **Finance Viewer** | `FINANCE_VIEWER` | Read-only financial reporting |
| **Provider Staff** | `PROVIDER_STAFF` | Provider-portal user: registers visits, submits claims and pre-authorizations, checks eligibility, uploads documents |
| **Data Entry** | `DATA_ENTRY` | Back-office data entry (members, visits) with limited scope |
| **Member (patient)** | *(not a system login role)* | Not a direct system user — represented as data, interacts physically at the Provider |

Note: the codebase has **no dynamic RBAC/permissions table** — these seven roles are a static enum (`SystemRole`), the single source of truth for authorization. A parallel, informal boolean-flag permission model also exists on the `users` table (`can_view_claims`, `can_view_visits`, etc.) — see `08-technical-debt.md`.

## 4. Core Modules

Backend modules (`backend/src/main/java/com/waad/tba/modules/`), 18 total:

`employer · member · provider · providercontract · medicaltaxonomy · benefitpolicy · eligibility · visit · preauthorization · claim · settlement · audit · report · pdf · rbac · auth · admin · systemadmin · dashboard`

These map to five functional layers:

1. **Master data**: employer, member, provider, providercontract, medicaltaxonomy (medical category/service hierarchy)
2. **Coverage engine**: benefitpolicy (canonical coverage rules), eligibility (rule-chain evaluation)
3. **Clinical/financial pipeline**: visit → preauthorization → claim → settlement
4. **Platform services**: auth, rbac, audit, report, pdf, dashboard
5. **Administration**: admin, systemadmin (feature flags, module access, email settings, general audit)

Frontend page groups mirror this closely (`frontend/src/pages/`): dashboard, members, employers, providers, provider-contracts, benefit-policies, benefit-packages, eligibility, visits, claims (+ batches), pre-approvals, settlement, medical-categories, reports, rbac, admin, settings, companies, documents, profile, and a dedicated **provider portal** (`pages/provider/`) that is architecturally and visually separate from the internal admin console.

## 5. Overall Architecture

```
┌─────────────────────────────┐        ┌──────────────────────────────┐
│  React 19 + MUI 7 (Vite)     │  HTTPS │  Spring Boot 3.5 / Java 21     │
│  frontend/                   │◄──────►│  backend/                      │
│  - Admin console              │  REST  │  - modules/* (DDD-ish layout)  │
│  - Provider portal            │  JSON  │  - security/ (JWT + session)   │
│  - Session-cookie auth        │        │  - Flyway-migrated schema      │
└─────────────────────────────┘        └──────────────┬───────────────┘
                                                        │ JDBC
                                                        ▼
                                              ┌─────────────────────┐
                                              │  PostgreSQL 16        │
                                              │  67 Flyway migrations │
                                              │  ~60+ tables           │
                                              └─────────────────────┘
```

- **Backend**: Spring Boot 3.5.11, Java 21, layered per-module (`entity/controller/service/repository/dto`), Spring Data JPA + Hibernate, Flyway for schema versioning, Spring Security with a **dual authentication** scheme (primary: HTTP session/cookie; legacy fallback: JWT bearer token), OpenPDF + Flying Saucer for PDF generation, Thymeleaf for report templates.
- **Frontend**: React 19, Vite 7, MUI 7 (+ MUI X data-grid/date-pickers/charts), React Router 7, Formik + Yup for forms, Zustand for the RBAC store, React Context for auth/theme/config, Axios for HTTP, `stylis-plugin-rtl` for Arabic RTL, `html5-qrcode` for barcode scanning, `exceljs`/`xlsx` for Excel import/export, custom in-house data-table components.
- **Database**: PostgreSQL 16, single schema, sequence + BIGSERIAL hybrid ID strategy, extensive CHECK constraints enforcing business rules at the DB layer (financial non-negativity, date ranges, status enums), JSONB used for audit payloads and flexible attributes.
- **Deployment**: Docker Compose (`db` / `backend` / `frontend`-with-nginx-reverse-proxy), resource-limited containers, health checks, `.env`-driven secrets.

Architecturally, the system follows a **"Visit-Centric" design law** (explicit in code comments): every Claim and PreAuthorization must trace back to a Visit — "no standalone claim/pre-auth creation allowed." This is the single most load-bearing architectural invariant in the domain model (see `07-domain-analysis.md`).

## 6. Technologies

| Layer | Technology |
|---|---|
| Backend framework | Spring Boot 3.5.11 (Web, Data JPA, Security, Validation) |
| Language / runtime | Java 21 |
| Build | Maven 3.9 |
| Database | PostgreSQL 16 |
| Schema migration | Flyway 10.10 |
| Auth | Spring Security, JJWT (JWT), HTTP session/cookie (primary) |
| PDF | OpenPDF + Flying Saucer (xhtmlrenderer) + Thymeleaf, Cairo font for Arabic |
| Frontend framework | React 19 |
| Build tool | Vite 7 |
| UI kit | MUI 7 (+ MUI X Data Grid, Date Pickers, Charts) |
| Forms | Formik 2 + Yup |
| State | Zustand (RBAC), React Context (auth/theme/config) |
| HTTP client | Axios |
| RTL | stylis + stylis-plugin-rtl |
| Excel | ExcelJS, SheetJS (xlsx) |
| Barcode/QR | html5-qrcode |
| Package manager | Yarn 4 (frontend), Maven (backend) |
| Containerization | Docker / Docker Compose, Nginx (frontend reverse proxy) |

## 7. Strengths

1. **Deliberate, well-documented architectural invariants.** The "Visit-Centric" rule, the "category-before-service" selection rule, and BenefitPolicyCoverageService's "single source of truth" framing are not accidental — they're stated explicitly in code comments and (mostly) enforced at the entity `@PrePersist`/`@PreUpdate` level. This is unusually disciplined for a system of this size.
2. **Real financial integrity guards at multiple layers.** `Claim.validateFinancialIdentity()` re-derives the payable amount and throws on drift; `ProviderAccount.assertBalanceInvariant()` enforces `runningBalance == totalApproved − totalPaid` after every mutation; the database has genuine double-entry-style CHECK constraints on `account_transactions`. This is a system that takes "the numbers must always reconcile" seriously.
3. **Deliberate snapshot/immutability design for audit-sensitive data.** `ClaimLine` freezes coverage percentages, limits, and used amounts at claim-creation time; `EligibilityCheck` freezes member/policy/employer names; SLA fields are snapshotted so later config changes don't retroactively rewrite history. This is correct TPA-domain thinking — coverage decisions must be reproducible as-of the decision date, not recalculated retroactively.
4. **Two audit-log systems, imperfectly unified but present.** An immutable, insert-only, JSONB-diff audit log (`medical_audit_logs`) sits alongside a general-purpose administrative audit log — the *intent* to maintain a legal-grade trail is clearly there, even if execution is inconsistent (see `08-technical-debt.md`).
5. **A real, functioning pluggable rule-engine for eligibility** (`EligibilityRule` beans, ordered, hard/soft failure semantics) — this is good extensibility design, not a hardcoded if/else chain.
6. **Provider-context security is defense-in-depth, not just role-based.** `ProviderContextGuard` forces `providerId` to come from the authenticated session for provider-portal users regardless of what the request body claims — a real anti-tampering control, not just a UI-level restriction.
7. **The team clearly monitors and reacts to production reality.** The 67-migration history (especially V26–V45) shows a team that found real production bugs (missing columns, mismatched CHECK constraints) and fixed them methodically with bilingual, explanatory commit-style comments — a healthy engineering culture even though the underlying churn itself is a smell (see below).
8. **RTL/Arabic is architecturally centralized**, not scattered — one emotion cache, one stylis-plugin-rtl instance, direction derived consistently from language.

## 8. Weaknesses

1. **State-machine documentation vs. runtime behavior have diverged in at least two confirmed places** in the Claim lifecycle (`ClaimStatus.getValidTransitions()` vs. `ClaimStateMachine.TRANSITION_MATRIX`, and the "REJECTED → APPROVED re-edit" path that is actually unreachable because of `HARD_LOCKED_FINAL_STATES`). For a claims-adjudication system, an incorrect mental model of the state machine is a real operational risk. See `07-domain-analysis.md` §State Machines.
2. **Two parallel coverage/limit engines coexist** (`benefitpolicy.BenefitPolicyCoverageService`, documented as canonical, vs. `claim.ruleengine.*`) with no visible reconciliation — a risk of divergent coverage decisions depending on which code path executes.
3. **Schema churn.** 20+ of the 67 migrations exist purely to fix entity/DDL mismatches from earlier migrations (missing columns, wrong CHECK values, orphaned columns) — evidence that schema changes were, for a period, not properly synchronized with JPA entity definitions before shipping. V34 ("production cleanup") is a large retroactive reconciliation. This has since stabilized, but it's a scar worth learning from, not repeating.
4. **JWT tokens do not expire in any practical sense (~10 years)**, with no revocation/blacklist mechanism — a serious security posture gap, mitigated somewhat by session-cookie auth being the *preferred* path, but the JWT fallback remains fully live.
5. **Two structurally different API response envelopes** (`ApiResponse` for success, `ApiError` for failure) — an integration and frontend-parsing inconsistency.
6. **Two independent email-sending implementations** with different environment-safety behavior — risk of accidentally emailing real users from a dev/test environment via the path that isn't gated by `email.enabled`.
7. **Thin automated test coverage relative to system complexity**: 19 backend test files for ~597 Java source files; effectively one frontend smoke test for ~683 frontend source files. Given the financial/medical stakes, this is the most consequential weakness for long-term safety of change.
8. **Several very large, monolithic frontend page components** (the largest at 2,561 lines) mixing form state, table rendering, validation, and submission logic — a real maintainability drag, concentrated in the highest-traffic provider-portal and claims-batch workflows.
9. **Missing foreign-key constraints on genuinely relational columns** in several hot tables (`visits.provider_id`, `pre_authorizations.*`, `claim_lines.pricing_item_id`, `payment_records.employer_id/provider_id`) — the database cannot itself guarantee referential integrity in these paths; it currently relies entirely on application-layer discipline.
10. **Inconsistent soft-delete conventions** across the schema (`deleted`, `is_deleted`, `deleted_at`-only, `active`-only — five different patterns) — makes "is this row really gone" a per-table question rather than a system-wide guarantee.

---

*Continue to [`02-business-workflows.md`](./02-business-workflows.md) for a workflow-by-workflow trace of how these modules cooperate in practice.*
