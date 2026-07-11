# 20 — Technical Debt Register

> Consolidated from all 19 preceding review documents. Per the Engineering Constitution's Technical Debt principle: *"Technical debt should be reduced continuously... document technical debt when immediate correction is not possible."* This register is the authoritative input to `21-enterprise-roadmap.md`'s Epics. Capability gaps (Notification Center, Medical Classification Engine, Mobile Readiness) are tracked separately as Epics 6–8, not listed here as "debt," since debt implies something built and degraded — these are things not yet built.

Severity: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low

---

## 🔴 Critical (7 items — all detailed in `03-critical-findings.md`)

| # | Item | Area |
|---|---|---|
| D1 | Claim state-machine disagreement; `REJECTED → APPROVED` documented but unreachable | Financial/Backend |
| D2 | `VisitService.delete()` hard-deletes cascade onto settled Claims | Database/Financial |
| D3 | Dual coverage-calculation engines (`benefitpolicy` vs. `claim.ruleengine`) | Medical/Financial |
| D4 | Possible FK-target mismatch: `claims.pre_authorization_id` | Database |
| D5 | Destructive V66 migration recreated payment/audit tables | Database/Audit |
| D6 | ~10-year JWT expiration, no revocation | Security |
| D7 | Hardcoded 20% patient co-pay fallback | Financial |

## 🟠 High

| # | Item | Area | Source |
|---|---|---|---|
| D8 | Recurring dead-role `@PreAuthorize` references (`INSURANCE_ADMIN`, `ADMIN`, `SYSTEM_ADMIN`) | Security/Backend | `04`, `08` |
| D9 | No rate limiting on `/auth/*` endpoints | Security | `04` |
| D10 | Two structurally different API response envelopes (`ApiResponse` vs `ApiError`) | Backend/API | `08` |
| D11 | Two independent, differently-gated email service implementations | Backend | `08` |
| D12 | Two independent audit-log systems, same class name, no shared interface | Backend/Security | `04`, `08` |
| D13 | Entire operational `pre_authorizations` table has zero FK constraints | Database | `07` |
| D14 | Missing FK constraints on other hot columns (`visits`, `claim_lines`, `payment_records`, `claims.reviewer_id`, `users.company_id`) | Database | `07` |
| D15 | Thin automated test coverage (19 backend test files / 597 source files; ~1 frontend smoke test / 683 files) | Backend/Frontend | `08`, `09` |
| D16 | Very large, monolithic frontend components (top: 2,561 / 2,085 / 1,840 / 1,674 lines) | Frontend | `09`, `11` |
| D17 | Five inconsistent soft-delete conventions; `claims` has two unreconciled lifecycle flags | Database | `07` |
| D18 | No centralized state machine for PreAuthorization, Member, or ProviderContract lifecycles | Backend/Domain | `06`, `08` |
| D19 | No mobile/tablet support in Provider Portal or Claim Batch Entry | Frontend/UX | `11`, `18` |
| D20 | No test coverage for the Provider Portal's core submission flow | Frontend | `11` |
| D21 | Silent PDF Arabic-font-loading failure risk | Reporting/PDF | `14` |
| D22 | No confirmed Arabic diacritic/letter-variant search normalization | Localization | `15` |

## 🟡 Medium

| # | Item | Area | Source |
|---|---|---|---|
| D23 | Stale internal documentation (benefitpolicy 3-tier comment, `ClaimStateMachine`'s role-name table, sync/async contradiction in `ClaimApprovalEventListener`) | Backend | `08` |
| D24 | `AuthorizationService.isInsuranceAdmin()` misleadingly named | Security | `04` |
| D25 | Inconsistent OTP vs. token-link password-reset behavior (only one unlocks accounts) | Security | `04` |
| D26 | API versioning inconsistency (2 controllers omit `/v1`) | Backend/API | `08` |
| D27 | Deprecated-but-live API/UI surfaces (`VisitController` deprecated endpoints, `UnifiedSearchControllerDeprecated`, legacy member-import) | Backend/Frontend | `08`, `09`, `10` |
| D28 | Duplicate-purpose DB columns never reconciled (`user_login_attempts`, `expiry_date`/`expires_at`) | Database | `07` |
| D29 | `claim_lines` under-indexed relative to its reporting role | Database/Performance | `07`, `19` |
| D30 | Medical taxonomy read access excludes several roles that likely need it | Backend/Security | earlier analysis |
| D31 | Unverified `X-Employer-ID` header trust in legacy dashboard endpoints | Security/Backend | `04`, `08` |
| D32 | `ReportController.getClaimReportPdf` bypasses standard error contract | Backend/Reporting | `08`, `12` |
| D33 | `PaymentService.mapToDto()` silently swallows FK-lookup failures | Backend/Financial | `05` |
| D34 | Three parallel "unified/generic" frontend table components | Frontend | `09` |
| D35 | No asynchronous generation confirmed for large reports | Reporting/Performance | `12`, `19` |
| D36 | No watermark/signature/stamp-area support in printed/PDF documents | Printing/PDF | `13`, `14` |
| D37 | Mock notification bell misrepresents nonexistent functionality | Frontend/Notification | `16` |
| D38 | No caching layer for stable reference data | Performance | `19` |
| D39 | Manual, developer-dependent medical price-list classification process | Medical Classification | `17` |

## 🟢 Low

| # | Item | Area | Source |
|---|---|---|---|
| D40 | Two parallel i18n data sources (`locales/` vs `utils/locales/`) | Frontend/Localization | `09`, `15` |
| D41 | Hardcoded bilingual menu labels bypass i18n infrastructure | Frontend/Localization | `09`, `10` |
| D42 | One hardcoded LTR style in an RTL page | Frontend/Localization | `09`, `15` |
| D43 | Dead menu entry (hidden Documents Library) | Frontend/UX | `09`, `10` |
| D44 | Leftover template scaffold, stray files, mock fixture in frontend source tree | Frontend | `09` |
| D45 | Dead/unused `member_seq` database sequence | Database | `07` |
| D46 | `admin` vs. `systemadmin` module naming overlap | Backend/Architecture | earlier analysis |
| D47 | Repeated date-range CHECK-constraint logic across ~5 tables | Database | `07` |
| D48 | `PdfCompanySettingsController`'s auth guard may conflict with pre-login branding needs | Backend/PDF | earlier analysis |
| D49 | No keyboard row-navigation in the highest-volume data-entry screens | UX | `10`, `11` |
| D50 | Two date libraries (`dayjs` + `date-fns`) where one would suffice | Frontend | `15` |

---

## Severity Summary

| Severity | Count |
|---|---|
| 🔴 Critical | 7 |
| 🟠 High | 15 |
| 🟡 Medium | 17 |
| 🟢 Low | 11 |
| **Total tracked debt items** | **50** |

## Debt Concentration by Area

| Area | Item Count | Interpretation |
|---|---|---|
| Backend/Architecture | 16 | The reconciliation-not-redesign pattern described throughout this assessment — mostly duplicate implementations and documentation drift |
| Database | 12 | FK gaps and soft-delete inconsistency dominate; the historical migration churn itself is stabilized, not ongoing debt |
| Frontend | 11 | Concentrated in the largest, highest-traffic files and unconsolidated component duplication |
| Security | 7 | One critical (JWT), rest are contained and addressable |
| Financial/Medical | 4 | Notably the *smallest* debt concentration despite being the highest-stakes domain — direct evidence the team's engineering discipline correlates with business criticality |

## Constitution Alignment Note

Per the Constitution's own instruction — *"never ignore duplicate logic, dead code, unused APIs, large classes, large React components, magic numbers, hardcoded rules"* — every category named in that sentence has at least one confirmed instance in this register (duplicate logic: D3/D11/D12; dead code: D27/D44; unused APIs: D27; large classes/components: D16; magic numbers: D7; hardcoded rules: D7). This register should be treated as the concrete fulfillment of that Constitution clause, not a generic bug list.

---

*Continue to [`21-enterprise-roadmap.md`](./21-enterprise-roadmap.md) for how this register translates into sequenced Epics and the final engineering backlog.*
