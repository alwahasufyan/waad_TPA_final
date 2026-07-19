package com.waad.tba.common.error;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ClaimStateTransitionException;
import com.waad.tba.common.exception.CoverageValidationException;
import com.waad.tba.common.exception.PolicyNotActiveException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.errorlog.service.SystemErrorLogService;
import com.waad.tba.security.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the fallback contract agreed for Phase 1: GlobalExceptionHandler never authors
 * business-specific Arabic text itself — it only (a) uses the throwing exception's own
 * messageAr when the service set one, or (b) falls back to a single generic Arabic string.
 * Every business exception response must carry a non-null messageAr either way, so the
 * frontend never has to show an English/internal message or its own generic fallback.
 */
class GlobalExceptionHandlerMessageArTest {

    private final SystemErrorLogService errorLogService = mock(SystemErrorLogService.class);
    private final Environment environment = mock(Environment.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(errorLogService, environment, authorizationService);

    private HttpServletRequest request(String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getMethod()).thenReturn("POST");
        return req;
    }

    @Test
    void businessRuleException_usesServiceProvidedArabicMessage() {
        BusinessRuleException ex = new BusinessRuleException("Cannot submit claim in APPROVED status.",
                "لا يمكن تقديم مطالبة في الحالة الحالية.");

        ResponseEntity<ApiError> response = handler.handleBusinessRule(ex, request("/api/v1/claims/5/submit"));

        assertEquals("لا يمكن تقديم مطالبة في الحالة الحالية.", response.getBody().getMessageAr());
    }

    @Test
    void businessRuleException_withoutArabicMessage_fallsBackToGenericArabic_notEnglishLeak() {
        BusinessRuleException ex = new BusinessRuleException("Cannot submit claim in APPROVED status.");

        ResponseEntity<ApiError> response = handler.handleBusinessRule(ex, request("/api/v1/claims/5/submit"));

        String messageAr = response.getBody().getMessageAr();
        assertNotNull(messageAr);
        // Must not silently leak the raw English exception text as the Arabic message.
        assertEquals(false, messageAr.equals(ex.getMessage()));
    }

    @Test
    void everyBusinessExceptionType_alwaysProducesNonNullMessageAr() {
        assertNotNull(handler.handlePolicyNotActive(
                new PolicyNotActiveException("Policy P001 is not active"), request("/api/v1/claims")).getBody()
                .getMessageAr());

        assertNotNull(handler.handleCoverageValidation(
                new CoverageValidationException("Service not covered"), request("/api/v1/claims")).getBody()
                .getMessageAr());

        assertNotNull(handler.handleClaimTransition(
                new ClaimStateTransitionException("DRAFT", "APPROVED"), request("/api/v1/claims/5/review")).getBody()
                .getMessageAr());

        assertNotNull(handler.handleBusinessRule(
                new BusinessRuleException("Generic rule violation"), request("/api/v1/claims")).getBody()
                .getMessageAr());

        assertNotNull(handler.handleNotFound(
                new ResourceNotFoundException("Claim", "id", 5), request("/api/v1/claims/5")).getBody()
                .getMessageAr());
    }

    @Test
    void resourceNotFoundException_withServiceProvidedArabicMessage_isUsedVerbatim() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Claim 5 not found", "المطالبة غير موجودة.");

        ResponseEntity<ApiError> response = handler.handleNotFound(ex, request("/api/v1/claims/5"));

        assertEquals("المطالبة غير موجودة.", response.getBody().getMessageAr());
    }
}
