# 04 — Security Review

**Score: 58/100 (D+)** · Reviewed against `.claude/reviews/security-review.md.txt` and `.claude/standards/security-standards.md.txt`.

---

## Authentication

| Check | Status | Evidence |
|---|---|---|
| Authenticated users only | ✓ | Class-level `@PreAuthorize`/`isAuthenticated()` defaults across all 58 controllers except `auth`'s public routes |
| Secure login | ✓ | Dual-mode: session-cookie (preferred, `SameSite=Strict`) and JWT (legacy fallback) |
| Session expiration | ✓ | HTTP session managed by Spring Security; re-derives roles from DB on every request |
| Refresh token handling | ⚠ | `/refresh-token` re-mints a full-validity (~10-year) JWT rather than a short-lived rotating token |
| Password policy | ✓ | `PasswordPolicyValidator` enforced, `PasswordPolicyViolationException` on violation |
| MFA support | ✗ (future, per constitution) | Not implemented — acceptable, explicitly deferred by the framework |

**Critical gap:** JWT expiration of ~10 years (`jwt.expiration: 315360000000` ms) with no denylist/blacklist. See CF-6. This is the single fact that most drags this section's score down.

## Authorization

| Check | Status | Evidence |
|---|---|---|
| RBAC implemented | ✓ | Static 7-role `SystemRole` enum, enforced via `@PreAuthorize` |
| Permission checks | ✓ | Present on nearly all mutating endpoints |
| Role isolation | ✓ | `AuthorizationService` enforces employer/provider data boundaries per role |
| No privilege escalation | ⚠ | See dead-role findings below — cosmetically risky, not currently exploitable |
| API authorization | ✓ | Consistently applied at controller-method level |
| UI authorization | ✓ | `PermissionGuard`, `ROLE_RESOURCE_ACCESS` map filters menu/routes |

**Findings:**
- **Recurring dead-role-reference bug**: `@PreAuthorize` expressions reference `INSURANCE_ADMIN` (ClaimController, 2 endpoints including soft-delete and restore), `ADMIN`, and `SYSTEM_ADMIN` (EmailSettingsController, all 4 endpoints) — none of these exist in `SystemRole`. Where `SUPER_ADMIN` is also listed, this is cosmetically harmless; it is a repeatable defect class that could silently produce a permanently-unreachable endpoint if a future check omits a valid role by mistake. **Constitution violation**: "Never bypass security checks for convenience" is not violated here, but "Reuse Before Creation" / consistency discipline is.
- `AuthorizationService.isInsuranceAdmin()` actually checks `SUPER_ADMIN` **or** `ACCOUNTANT` — there is no `INSURANCE_ADMIN` role. Misleading method naming is a genuine authorization-review risk: a future maintainer could misjudge who this method actually grants access to.
- `ProviderContextGuard` is a real, working anti-tampering control — provider-submitted `providerId` values are always overwritten server-side from the authenticated session for `PROVIDER_STAFF` users. This is the security pattern the rest of the codebase should be measured against.

## Data Protection

| Check | Status | Evidence |
|---|---|---|
| Sensitive data encrypted | ⚠ | Not confirmed for data-at-rest (relies on infrastructure/DB-level encryption, not verified in this pass) |
| Passwords hashed | ✓ | Spring Security `PasswordEncoder`; reset/verification tokens stored SHA-256+Base64 hashed, raw token only ever emailed |
| No secrets in source code | ⚠ | Dev-profile JWT secret and default admin password (`Admin@123`) are hardcoded fallbacks in `application-dev.yml` — acceptable for local dev, but a real risk if `dev` profile is ever accidentally active in a shared/staging environment |
| Secure configuration | ✓ | Prod profile explicitly requires `JWT_SECRET` env var with no fallback |
| Secure backups | Not assessed | Outside code-review scope |

## API Security

| Check | Status | Evidence |
|---|---|---|
| Input validation | ✓ | Bean Validation (`@Valid`) widely used; `GlobalExceptionHandler` translates violations |
| Output validation | ✓ | `ApiResponse`/`ApiError` structured responses; no stack-trace leakage confirmed |
| Rate limiting | ✗ | No rate-limiting infrastructure found anywhere in the codebase |
| Request size limits | ✓ | `SPRING_MULTIPART_MAX_FILE_SIZE`/`MAX_REQUEST_SIZE` configured |
| Proper HTTP status codes | ✓ | `GlobalExceptionHandler` maps business exceptions to specific codes (422, 409, 423, 403, 400, 404) |
| Secure headers | ⚠ | `x-content-type-options`, `x-xss-protection` confirmed present; CSP headers not confirmed |

**Finding:** No rate limiting on any endpoint, including `/auth/login`, `/auth/forgot-password`, `/auth/register`. Combined with account-lockout-after-5-failed-attempts (which *is* implemented), brute-force risk is partially mitigated but not eliminated — an attacker can still enumerate many accounts up to the lockout threshold without throttling. This should be evaluated as OWASP-relevant (see below).

## OWASP Top 10

| Risk | Status | Notes |
|---|---|---|
| SQL Injection | ✓ Protected | JPA/Hibernate parameterized queries throughout; no raw string-concatenated SQL found in the reviewed code paths |
| XSS | ✓ Protected | React's default JSX escaping; no `dangerouslySetInnerHTML` misuse found in this pass (not exhaustively audited) |
| CSRF | ✓ Mitigated | CSRF disabled but replaced with `SameSite=Strict` cookies — a documented, defensible trade-off per `SecurityConfig`'s own rationale comment |
| Broken Authentication | ⚠ | JWT longevity issue (CF-6) is the concrete instance of this risk category |
| Broken Access Control | ⚠ | Dead-role references (above); `X-Employer-ID` header trust in legacy dashboard endpoints not confirmed safe (see `08-backend-review.md`) |
| SSRF | Not assessed | No obvious outbound-URL-from-user-input pattern found; would need dedicated review of file-upload/URL-fetch paths if any exist |
| File Upload attacks | ⚠ | See File Upload section below |
| Path Traversal | ⚠ | `FileController`'s `/{folder}/{filename}/...` pattern was not verified in this pass to sanitize `filename` against `../` traversal — recommend explicit verification |

## Audit

| Check | Status | Evidence |
|---|---|---|
| Login audit | ✓ | `AuthenticationEventListener` + `UserLoginAttempt` |
| Logout audit | ⚠ | Not explicitly confirmed |
| Permission changes | ✓ | `UserAuditLog`, `audit_logs` (systemadmin) |
| Sensitive actions logged | ✓ | `medical_audit_logs` — immutable, trigger-enforced, comprehensive for claim/medical decisions |
| Failed access attempts logged | ✓ | `UserLoginAttempt`, lockout counter |

**Structural finding:** Two independently-designed audit systems coexist with the *same class name* in two packages (`modules.audit.AuditLog` immutable, `modules.systemadmin.AuditLog` mutable) and no shared interface — a module could plausibly write to neither by omission. See `20-technical-debt.md` T11.

## Sensitive Operations

Financial Approval, Medical Approval, Settlement Approval, User Management, Role Management, and Configuration Changes are all confirmed to require explicit role checks (`ACCOUNTANT`/`MEDICAL_REVIEWER`/`SUPER_ADMIN` per operation, per `02-business-workflows` equivalent findings). **This is well done** — the role policy table for claim-state transitions is genuinely granular and appropriate.

## File Upload

| Check | Status | Evidence |
|---|---|---|
| Allowed file types | ⚠ | Not confirmed to be allow-listed at the `FileController` level in this pass |
| Size limits | ✓ | Configured via `SPRING_MULTIPART_MAX_FILE_SIZE` |
| Virus scanning | ✗ | No AV/malware scanning integration found |
| Random filenames | ⚠ | Not confirmed — recommend explicit verification that uploaded filenames are not used directly as storage paths |
| Secure storage | ⚠ | Local filesystem-based (`uploads/` directory) rather than an access-controlled object store — acceptable at current scale but worth flagging for `19-performance-review.md`/scalability discussion |

## Logging

| Check | Status | Evidence |
|---|---|---|
| No passwords logged | ✓ | No evidence of password logging found |
| No tokens logged | ✓ | Reset/verification tokens hashed before storage |
| No sensitive medical data logged | ⚠ | Not exhaustively verified — `ReportController.getClaimReportPdf`'s `printStackTrace()` exception handling is a concrete risk of leaking request context into stdout logs |
| Structured logs | ✓ | Correlation IDs (`trackingId`, `correlationId`) present throughout `ApiError` and audit records |

## Infrastructure

| Check | Status | Evidence |
|---|---|---|
| HTTPS | ✓ (deployment-level) | Nginx reverse proxy with SSL volume mount in `docker-compose.yml` |
| Secure cookies | ✓ | `SameSite=Strict` |
| Environment variables | ✓ | `.env`-driven secrets, `.env.example` template provided, `.gitignore` correctly excludes `.env` |
| Firewall | Not assessed | Infrastructure-level, outside code scope |
| Database security | ⚠ | Local dev default password `12345` documented in `run_dev.bat` — acceptable for local dev only, not a production finding |

---

## Decision

**⚠ Changes Required.**

The system is not insecure by design — it has real, correctly-applied defense-in-depth in several places (ProviderContextGuard, actuator/Swagger locked to SUPER_ADMIN, redacted exception messages, hashed tokens). But it cannot be certified production-secure as-is because of the JWT expiration issue (CF-6) and the absence of rate limiting on authentication endpoints. Both are addressable without architectural change.

## Priority Actions

1. **(Critical)** Shorten JWT expiration and add revocation (CF-6).
2. **(High)** Add rate limiting to `/auth/*` endpoints (login, forgot-password, register) — a standard Spring Security or gateway-level control, no architecture change required.
3. **(High)** Fix the dead-role `@PreAuthorize` references across `ClaimController` and `EmailSettingsController`.
4. **(Medium)** Verify `FileController` path-traversal and file-type-allowlist behavior explicitly; add if missing.
5. **(Medium)** Verify the `X-Employer-ID` header trust question in legacy dashboard endpoints.
6. **(Low)** Rename `AuthorizationService.isInsuranceAdmin()` to accurately reflect its actual grant (`SUPER_ADMIN` or `ACCOUNTANT`).

---

*Continue to [`05-financial-review.md`](./05-financial-review.md).*
