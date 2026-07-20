package com.waad.tba.common.error;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ClaimStateTransitionException;
import com.waad.tba.common.exception.CoverageValidationException;
import com.waad.tba.common.exception.PolicyNotActiveException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.errorlog.entity.ErrorLogSeverity;
import com.waad.tba.modules.errorlog.entity.ErrorLogSource;
import com.waad.tba.modules.errorlog.entity.SystemErrorLog;
import com.waad.tba.modules.errorlog.service.SystemErrorLogService;
import com.waad.tba.modules.rbac.exception.AccountLockedException;
import com.waad.tba.modules.rbac.exception.EmailNotVerifiedException;
import com.waad.tba.modules.rbac.exception.InvalidResetTokenException;
import com.waad.tba.modules.rbac.exception.PasswordPolicyViolationException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Global Exception Handler with Phase 6 Business Exceptions.
 * 
 * Handles all application exceptions and returns standardized ApiError
 * responses.
 * 
 * Exception Hierarchy:
 * - BusinessRuleException (base for business rules)
 * - PolicyNotActiveException (policy validation failures)
 * - CoverageValidationException (coverage/limit failures)
 * - ClaimStateTransitionException (invalid state transitions)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SystemErrorLogService errorLogService;
    private final Environment environment;
    private final com.waad.tba.security.AuthorizationService authorizationService;

    public GlobalExceptionHandler(SystemErrorLogService errorLogService, Environment environment,
            com.waad.tba.security.AuthorizationService authorizationService) {
        this.errorLogService = errorLogService;
        this.environment = environment;
        this.authorizationService = authorizationService;
    }

    private String now() {
        return Instant.now().toString();
    }

    private String activeEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "local" : profiles[0];
    }

    /**
     * Persist an unexpected server error (HTTP 500) to the internal error log.
     * Best-effort: any failure here is swallowed so it never masks the original error.
     */
    private void recordServerError(Exception ex, HttpServletRequest request, int statusCode, String trackingId) {
        try {
            String username = null;
            String role = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                username = auth.getName();
                role = auth.getAuthorities() == null ? null
                        : auth.getAuthorities().stream().map(Object::toString).findFirst().orElse(null);
            }
            SystemErrorLog event = SystemErrorLog.builder()
                    .occurredAt(java.time.LocalDateTime.now())
                    .source(ErrorLogSource.BACKEND)
                    .severity(ErrorLogSeverity.ERROR)
                    .environment(activeEnvironment())
                    .correlationId(trackingId)
                    .username(username)
                    .role(role)
                    .httpMethod(request.getMethod())
                    .path(request.getRequestURI())
                    .statusCode(statusCode)
                    .errorCode(ErrorCode.INTERNAL_ERROR.name())
                    .userMessage("حدث خطأ غير متوقع. رقم التتبع: " + trackingId)
                    .technicalMessage(ex.getMessage())
                    .exceptionClass(ex.getClass().getName())
                    .stackExcerpt(stackExcerpt(ex))
                    .build();
            errorLogService.record(event);
        } catch (Exception recordFailure) {
            log.warn("Failed to record server error to error log: {}", recordFailure.getMessage());
        }
    }

    private static String stackExcerpt(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName());
        if (ex.getMessage() != null) {
            sb.append(": ").append(ex.getMessage());
        }
        StackTraceElement[] trace = ex.getStackTrace();
        int limit = Math.min(trace.length, 15);
        for (int i = 0; i < limit; i++) {
            sb.append("\n    at ").append(trace[i].toString());
        }
        return sb.toString();
    }

    private String generateTrackingId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }

    /**
     * Single generic Arabic fallback, used only when neither the exception nor its message
     * gives us an Arabic string. This is intentionally the ONLY hardcoded business-facing string
     * in this handler — see {@link #build(HttpStatus, ErrorCode, String, String, HttpServletRequest, Object)}.
     */
    private static final String GENERIC_AR_FALLBACK = "تعذر تنفيذ العملية. الرجاء المحاولة مرة أخرى أو التواصل مع الدعم الفني.";

    private ResponseEntity<ApiError> build(@NonNull HttpStatus status, ErrorCode code, String message,
            HttpServletRequest request, Object details) {
        return build(status, code, message, null, request, details);
    }

    /**
     * Populates messageAr from the exception's own Arabic message (set by the throwing service —
     * see {@code BusinessRuleException#getMessageAr()}), falling back to the raw message if it
     * already happens to be Arabic (a lot of existing throw-sites author Arabic text directly),
     * and only then to one generic string. This method never authors business-specific wording —
     * that responsibility belongs to the service that threw the exception, not this handler.
     */
    private ResponseEntity<ApiError> build(@NonNull HttpStatus status, ErrorCode code, String message,
            String exceptionMessageAr, HttpServletRequest request, Object details) {
        String trackingId = generateTrackingId();
        ApiError error = ApiError.of(code, message, request.getRequestURI(), details, now(), trackingId);
        String messageAr = exceptionMessageAr != null ? exceptionMessageAr
                : containsArabic(message) ? message
                : GENERIC_AR_FALLBACK;
        error.setMessageAr(messageAr);
        return ResponseEntity.status(status).body(error);
    }

    private static boolean containsArabic(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return true;
        }
        return false;
    }

    /**
     * Best-effort diagnostic context for exceptions on the claims path — logged server-side only,
     * never returned to the client. Lets support correlate a trackingId with who hit it, on what
     * provider/claim/visit, without any of this reaching the API response.
     */
    private String logContext(HttpServletRequest request, String trackingId) {
        String username = "anonymous";
        String providerId = "-";
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                username = auth.getName();
                var currentUser = authorizationService.getCurrentUser();
                if (currentUser != null && currentUser.getProviderId() != null) {
                    providerId = currentUser.getProviderId().toString();
                }
            }
        } catch (Exception ignored) {
            // best-effort only — never let logging break error handling
        }
        String path = request.getRequestURI();
        return String.format(
                "requestId=%s user=%s providerId=%s claimId=%s visitId=%s endpoint=%s %s",
                trackingId, username, providerId, extractPathId(path, "claims"), extractPathId(path, "visits"),
                request.getMethod(), path);
    }

    private static String extractPathId(String path, String segment) {
        if (path == null) return "-";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/" + segment + "/(\\d+)").matcher(path);
        return m.find() ? m.group(1) : "-";
    }

    // ========== Phase 6: Business Rule Exceptions ==========

    /**
     * Handle PolicyNotActiveException - returns 422 Unprocessable Entity.
     * 
     * EXAMPLE RESPONSE:
     * {
     * "code": "POLICY_NOT_ACTIVE",
     * "message": "Policy P001 is not active on 2025-01-15",
     * "path": "/api/claims",
     * "details": { "policyNumber": "P001", "requestedDate": "2025-01-15" }
     * }
     */
    @ExceptionHandler(PolicyNotActiveException.class)
    public ResponseEntity<ApiError> handlePolicyNotActive(PolicyNotActiveException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Policy validation failed - Message: {}, {}", ex.getMessage(), logContext(request, trackingId));

        Map<String, Object> details = new HashMap<>();
        if (ex.getPolicyNumber() != null) {
            details.put("policyNumber", ex.getPolicyNumber());
        }
        if (ex.getRequestedDate() != null) {
            details.put("requestedDate", ex.getRequestedDate().toString());
        }

        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), ex.getMessageAr(), request,
                details.isEmpty() ? null : details);
    }

    /**
     * Handle CoverageValidationException - returns 422 Unprocessable Entity.
     * 
     * EXAMPLE RESPONSE:
     * {
     * "code": "COVERAGE_VALIDATION_FAILED",
     * "message": "Dental services not covered in benefit package Gold",
     * "details": { "issue": "SERVICE_NOT_COVERED", "serviceCode": "DEN-001" }
     * }
     */
    @ExceptionHandler(CoverageValidationException.class)
    public ResponseEntity<ApiError> handleCoverageValidation(CoverageValidationException ex,
            HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Coverage validation failed - Issue: {}, Message: {}, {}",
                ex.getIssue(), ex.getMessage(), logContext(request, trackingId));

        Map<String, Object> details = new HashMap<>();
        details.put("issue", ex.getIssue().name());
        if (ex.getServiceCode() != null) {
            details.put("serviceCode", ex.getServiceCode());
        }
        if (ex.getRequestedAmount() != null) {
            details.put("requestedAmount", ex.getRequestedAmount());
        }
        if (ex.getAvailableLimit() != null) {
            details.put("availableLimit", ex.getAvailableLimit());
        }

        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), ex.getMessageAr(), request,
                details);
    }

    /**
     * Handle ClaimStateTransitionException - returns 409 Conflict.
     * 
     * EXAMPLE RESPONSE:
     * {
     * "code": "INVALID_CLAIM_TRANSITION",
     * "message": "Invalid state transition: DRAFT → APPROVED",
     * "details": { "fromStatus": "DRAFT", "toStatus": "APPROVED", "requiredRole":
     * "INSURANCE" }
     * }
     */
    @ExceptionHandler(ClaimStateTransitionException.class)
    public ResponseEntity<ApiError> handleClaimTransition(ClaimStateTransitionException ex,
            HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Claim state transition failed - From: {}, To: {}, {}",
                ex.getFromStatus(), ex.getToStatus(), logContext(request, trackingId));

        Map<String, Object> details = new HashMap<>();
        if (ex.getFromStatus() != null) {
            details.put("fromStatus", ex.getFromStatus());
        }
        if (ex.getToStatus() != null) {
            details.put("toStatus", ex.getToStatus());
        }
        if (ex.getRequiredRole() != null) {
            details.put("requiredRole", ex.getRequiredRole());
        }

        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), ex.getMessageAr(), request,
                details.isEmpty() ? null : details);
    }

    /**
     * Handle generic BusinessRuleException - returns 422 Unprocessable Entity.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Business rule violation - Code: {}, Message: {}, {}",
                ex.getErrorCode(), ex.getMessage(), logContext(request, trackingId));

        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), ex.getMessageAr(), request,
                null);
    }

    // ========== Existing Exception Handlers ==========

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        String path = request.getRequestURI();

        log.warn("Resource not found - Message: {}, {}", ex.getMessage(), logContext(request, trackingId));

        ErrorCode code;
        if (path.contains("/claims"))
            code = ErrorCode.CLAIM_NOT_FOUND;
        else if (path.contains("/companies"))
            code = ErrorCode.INTERNAL_ERROR; // Company not found
        else if (path.contains("/admin/users"))
            code = ErrorCode.USER_NOT_FOUND;
        else if (path.contains("/employers"))
            code = ErrorCode.EMPLOYER_NOT_FOUND;
        else if (path.contains("/members"))
            code = ErrorCode.MEMBER_NOT_FOUND;
        else if (path.contains("/policies"))
            code = ErrorCode.POLICY_NOT_FOUND;
        else
            code = ErrorCode.INTERNAL_ERROR;

        return build(HttpStatus.NOT_FOUND, code, ex.getMessage(), ex.getMessageAr(), request, null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleJpaEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Entity not found - Message: {}, {}", ex.getMessage(), logContext(request, trackingId));

        return build(HttpStatus.NOT_FOUND, ErrorCode.INTERNAL_ERROR, ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Business state violation - Message: {}, {}", ex.getMessage(), logContext(request, trackingId));
        return build(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        // Log full message internally but never expose internal class paths to the
        // client
        log.warn("Bad request - Path: {}, Message: {}, TrackingId: {}", request.getRequestURI(), ex.getMessage(),
                trackingId);

        // Use a generic, safe message to avoid exposing internal Spring class paths
        String safeMessage = "Invalid request parameter value.";
        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR,
                safeMessage,
                request.getRequestURI(),
                null,
                now(),
                trackingId);
        // Set Arabic message using pattern matching; fall back to a generic safe
        // message
        String messageAr = translateValidationMessage(ex.getMessage());
        // If translation returned the raw (potentially internal) message unchanged, use
        // a safe generic
        if (messageAr != null && messageAr.contains(".")) {
            messageAr = "قيمة معلمة الطلب غير صالحة.";
        }
        error.setMessageAr(messageAr);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle PropertyReferenceException - Invalid sort field in Pageable
     * Returns 400 Bad Request with details about the invalid property
     * 
     * Common Cause: User-provided sort field doesn't exist in Entity
     * Example: GET /api/v1/claims?sortBy=invalidField
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handlePropertyReference(PropertyReferenceException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();

        // Log full details server-side only — do NOT expose entity/property names to
        // client
        log.warn("Invalid sort field - Path: {}, TrackingId: {}, Details: {}",
                request.getRequestURI(), trackingId, ex.getMessage());

        String message = "Invalid sort field. Please use a valid field name.";
        String messageAr = "حقل الفرز غير صحيح. الرجاء استخدام اسم حقل صالح.";

        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR,
                message,
                request.getRequestURI(),
                null,
                now(),
                trackingId);
        error.setMessageAr(messageAr);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex,
            HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.error("Data integrity violation - Path: {}, TrackingId: {}", request.getRequestURI(), trackingId, ex);

        String messageAr = "خطأ في تكامل البيانات. العملية تنتهك أحد قيود قاعدة البيانات.";
        String rootMsg = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();

        if (rootMsg != null) {
            if (rootMsg.contains("users_username_key") || rootMsg.toLowerCase().contains("duplicate key") && rootMsg.contains("username")) {
                messageAr = "اسم المستخدم هذا مسجل مسبقاً بنظام آخر أو بحالة مختلفة.";
            } else if (rootMsg.contains("users_email_key") || rootMsg.toLowerCase().contains("duplicate key") && rootMsg.contains("email")) {
                messageAr = "البريد الإلكتروني هذا مسجل مسبقاً بنظام آخر أو بحالة مختلفة.";
            }
        }

        ApiError error = ApiError.of(
                ErrorCode.BUSINESS_RULE_VIOLATION,
                "Data integrity error. The operation violates a database constraint.",
                request.getRequestURI(),
                null,
                now(),
                trackingId);
        error.setMessageAr(messageAr);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        log.warn("Validation failed - Path: {}, Errors: {}, TrackingId: {}", request.getRequestURI(), fieldErrors,
                trackingId);

        // Build descriptive validation message
        String message = buildValidationMessage(fieldErrors);
        String messageAr = buildValidationMessageAr(fieldErrors);

        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR,
                message,
                request.getRequestURI(),
                fieldErrors,
                now(),
                trackingId);
        error.setMessageAr(messageAr);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Build descriptive validation message from field errors.
     */
    private String buildValidationMessage(Map<String, String> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return "Validation failed";
        }

        StringBuilder sb = new StringBuilder("Validation failed: ");
        fieldErrors.forEach((field, msg) -> sb.append(field).append(" - ").append(msg).append("; "));
        return sb.toString().trim();
    }

    /**
     * Build Arabic validation message from field errors.
     */
    private String buildValidationMessageAr(Map<String, String> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return "فشل التحقق من صحة البيانات";
        }

        Map<String, String> fieldTranslations = Map.of(
                "barcode", "الباركود/رقم البطاقة",
                "cardNumber", "رقم البطاقة",
                "serviceDate", "تاريخ الخدمة",
                "memberId", "معرف العضو",
                "providerId", "معرف مقدم الخدمة");

        StringBuilder sb = new StringBuilder("فشل التحقق من صحة البيانات: ");
        fieldErrors.forEach((field, msg) -> {
            String arabicField = fieldTranslations.getOrDefault(field, field);
            sb.append(arabicField).append(" - ").append(msg).append("؛ ");
        });
        return sb.toString().trim();
    }

    /**
     * Translate common validation messages to Arabic.
     */
    private String translateValidationMessage(String message) {
        if (message == null)
            return "خطأ في التحقق من صحة البيانات";

        // Common translations
        if (message.contains("barcode or card number") ||
                message.contains("Barcode or card number")) {
            return "يجب إدخال الباركود أو رقم البطاقة";
        }
        if (message.contains("Member not found")) {
            return "العضو غير موجود";
        }
        if (message.contains("not found")) {
            return "العنصر المطلوب غير موجود";
        }
        if (message.contains("required")) {
            return "حقل مطلوب - " + message;
        }

        return message; // Return original if no translation
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Authentication failed - Path: {}, User-Agent: {}, TrackingId: {}",
                request.getRequestURI(), request.getHeader("User-Agent"), trackingId);
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid username or password", request,
                null);
    }

    /**
     * Handle InternalAuthenticationServiceException - wraps BadCredentialsException
     * in some Spring flows.
     * Returns 401 Unauthorized instead of 500.
     */
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ApiError> handleInternalAuth(InternalAuthenticationServiceException ex,
            HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Internal authentication error (bad credentials) - Path: {}, TrackingId: {}",
                request.getRequestURI(), trackingId);
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid username or password", request,
                null);
    }

    /**
     * Handle DisabledException - account is disabled.
     * Returns 401 Unauthorized.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Disabled account login attempt - Path: {}, TrackingId: {}",
                request.getRequestURI(), trackingId);
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Account is disabled", request, null);
    }

    /**
     * Handle any remaining AuthenticationException subtypes.
     * Returns 401 Unauthorized.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Authentication failed - Path: {}, Message: {}, TrackingId: {}",
                request.getRequestURI(), ex.getMessage(), trackingId);
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Authentication failed", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Access denied - Path: {}, Message: {}, TrackingId: {}",
                request.getRequestURI(), ex.getMessage(), trackingId);
        // Pass the exception's own Arabic message through when it authored one (e.g.
        // ProviderContextGuard's ownership-violation messages) — same containsArabic
        // fallback pattern used throughout this handler; falls back to the generic
        // string for plain Spring Security access-denied exceptions with no Arabic text.
        String exceptionMessageAr = containsArabic(ex.getMessage()) ? ex.getMessage() : null;
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access is denied", exceptionMessageAr, request,
                null);
    }

    // ========== Security Exceptions (Account Lockout, Email Verification,
    // Password) ==========

    /**
     * Handle AccountLockedException - returns 423 Locked.
     * 
     * Account is locked due to multiple failed login attempts. Returns unlock time.
     * 
     * EXAMPLE RESPONSE:
     * {
     * "code": "ACCOUNT_LOCKED",
     * "message": "Account locked due to multiple failed login attempts. Try again
     * after 2025-01-15T10:30:00",
     * "messageAr": "تم قفل الحساب بسبب محاولات تسجيل دخول فاشلة متعددة. حاول مرة
     * أخرى بعد 2025-01-15T10:30:00",
     * "details": { "lockedUntil": "2025-01-15T10:30:00" }
     * }
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Account locked - Path: {}, Username: {}, LockedUntil: {}, TrackingId: {}",
                request.getRequestURI(), ex.getUsername(), ex.getLockedUntil(), trackingId);

        Map<String, Object> details = new HashMap<>();
        details.put("lockedUntil", ex.getLockedUntil().toString());

        ApiError error = ApiError.of(
                ErrorCode.ACCOUNT_LOCKED,
                ex.getMessage(),
                request.getRequestURI(),
                details,
                now(),
                trackingId);
        error.setMessageAr(ex.getMessageAr());

        return ResponseEntity.status(HttpStatus.LOCKED).body(error);
    }

    /**
     * Handle EmailNotVerifiedException - returns 403 Forbidden.
     * 
     * Email verification required before accessing this resource.
     * 
     * EXAMPLE RESPONSE:
     * {
     * "code": "EMAIL_NOT_VERIFIED",
     * "message": "Email verification required. Please check your email for
     * verification link.",
     * "messageAr": "التحقق من البريد الإلكتروني مطلوب. يرجى التحقق من بريدك
     * الإلكتروني للحصول على رابط التحقق."
     * }
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiError> handleEmailNotVerified(EmailNotVerifiedException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Email not verified - Path: {}, Username: {}, Email: {}, TrackingId: {}",
                request.getRequestURI(), ex.getUsername(), ex.getEmail(), trackingId);

        ApiError error = ApiError.of(
                ErrorCode.EMAIL_NOT_VERIFIED,
                ex.getMessage(),
                request.getRequestURI(),
                null,
                now(),
                trackingId);
        error.setMessageAr(ex.getMessageAr());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handle InvalidResetTokenException - returns 400 Bad Request.
     * 
     * Password reset or email verification token is invalid, expired, or already
     * used.
     * 
     * EXAMPLE RESPONSE:
     * {
     * "code": "INVALID_TOKEN",
     * "message": "Password reset token is invalid or has expired",
     * "messageAr": "رمز إعادة تعيين كلمة المرور غير صالح أو منتهي الصلاحية"
     * }
     */
    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ApiError> handleInvalidResetToken(InvalidResetTokenException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Invalid reset token - Path: {}, TrackingId: {}",
                request.getRequestURI(), trackingId);

        ApiError error = ApiError.of(
                ErrorCode.INVALID_TOKEN,
                ex.getMessage(),
                request.getRequestURI(),
                null,
                now(),
                trackingId);
        error.setMessageAr(ex.getMessageAr());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle PasswordPolicyViolationException - returns 400 Bad Request.
     * 
     * New password does not meet password policy requirements.
     * 
     * EXAMPLE RESPONSE:
     * {
     * "code": "PASSWORD_POLICY_VIOLATION",
     * "message": "Password must be at least 8 characters long and contain
     * uppercase, lowercase, digit, and special character",
     * "messageAr": "يجب أن تتكون كلمة المرور من 8 أحرف على الأقل وتحتوي على أحرف
     * كبيرة وصغيرة ورقم وحرف خاص",
     * "details": { "violations": ["TOO_SHORT", "MISSING_UPPERCASE"] }
     * }
     */
    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<ApiError> handlePasswordPolicyViolation(PasswordPolicyViolationException ex,
            HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Password policy violation - Path: {}, Violations: {}, TrackingId: {}",
                request.getRequestURI(), ex.getViolations(), trackingId);

        Map<String, Object> details = new HashMap<>();
        if (ex.getViolations() != null && !ex.getViolations().isEmpty()) {
            details.put("violations", ex.getViolations());
        }

        ApiError error = ApiError.of(
                ErrorCode.PASSWORD_POLICY_VIOLATION,
                ex.getMessage(),
                request.getRequestURI(),
                details.isEmpty() ? null : details,
                now(),
                trackingId);
        error.setMessageAr(ex.getMessageAr());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        log.warn("Upload size exceeded - Path: {}, TrackingId: {}", request.getRequestURI(), trackingId);

        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR,
                "Maximum upload size exceeded. Please upload a smaller file.",
                request.getRequestURI(),
                null,
                now(),
                trackingId);
        error.setMessageAr("حجم الملف المرفوع يتجاوز الحد الأقصى المسموح. الرجاء رفع ملف أصغر.");

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipart(MultipartException ex, HttpServletRequest request) {
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("maximum upload size exceeded")) {
            return handleMaxUploadSize(new MaxUploadSizeExceededException(-1L), request);
        }
        throw ex;
    }

    /**
     * Handle MethodArgumentTypeMismatchException - returns 400 Bad Request.
     * Triggered when a path or query parameter cannot be converted to the expected
     * type,
     * e.g. a non-numeric value for a Long @PathVariable.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        String paramName = ex.getName();
        Object value = ex.getValue();
        Class<?> requiredType = ex.getRequiredType();
        String typeName = requiredType != null ? requiredType.getSimpleName() : "unknown";

        log.warn("Type mismatch - Path: {}, Param: '{}', Value: '{}', Expected: {}, TrackingId: {}",
                request.getRequestURI(), paramName, value, typeName, trackingId);

        String message = String.format("Invalid value '%s' for parameter '%s': expected type %s.",
                value, paramName, typeName);
        String messageAr = String.format("قيمة غير صالحة '%s' للمعامل '%s': النوع المطلوب %s.",
                value, paramName, typeName);

        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR,
                message,
                request.getRequestURI(),
                null,
                now(),
                trackingId);
        error.setMessageAr(messageAr);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle ResponseStatusException — used by guarded operations (e.g. danger zone) to return
     * a precise HTTP status with a safe Arabic reason. Must be handled explicitly, otherwise the
     * catch-all below would turn every guard rejection into a 500 and log it as a server error.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        log.warn("Guarded operation rejected - Path: {}, Status: {}, Reason: {}, TrackingId: {}",
                request.getRequestURI(), status.value(), ex.getReason(), trackingId);

        ErrorCode code = status == HttpStatus.FORBIDDEN ? ErrorCode.ACCESS_DENIED
                : status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR
                : ErrorCode.VALIDATION_ERROR;
        String reason = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();

        ApiError error = ApiError.of(code, reason, request.getRequestURI(), null, now(), trackingId);
        error.setMessageAr(reason);
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        String trackingId = generateTrackingId();
        // Log the exception with full stack trace — server-side only
        log.error("Unexpected error occurred - Path: {}, TrackingId: {}", request.getRequestURI(), trackingId, ex);
        // Persist to internal error log (best-effort) so admins can inspect it without screenshots
        recordServerError(ex, request, HttpStatus.INTERNAL_SERVER_ERROR.value(), trackingId);
        // Return generic user message and safe technical details — never expose stack
        // trace
        Map<String, Object> details = new HashMap<>();
        details.put("reference", trackingId);
        details.put("exception", ex.getClass().getSimpleName());
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            details.put("reason", ex.getMessage());
        }

        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.", request, details);
    }
}
