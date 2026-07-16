package com.waad.tba.modules.preauthorization.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import com.waad.tba.modules.preauthorization.api.request.PartialApprovePreAuthorizationRequest;
import com.waad.tba.modules.preauthorization.api.request.RequestPreAuthorizationInfoRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class PreAuthorizationDecisionControllerContractTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void partialApprovalRequiresAmountAndReason() {
        var violations = validator.validate(new PartialApprovePreAuthorizationRequest(null, ""));
        assertEquals(2, violations.size());
    }

    @Test
    void requestInformationRequiresNonBlankNotes() {
        var violations = validator.validate(new RequestPreAuthorizationInfoRequest("   "));
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("required")
                || violations.iterator().next().getMessage().contains("مطلوب"));
    }

    @Test
    void endpointPathsRemainStable() throws Exception {
        Method partial = PreAuthorizationController.class.getDeclaredMethod(
                "approvePartial", Long.class, PartialApprovePreAuthorizationRequest.class,
                org.springframework.security.core.Authentication.class);
        Method info = PreAuthorizationController.class.getDeclaredMethod(
                "requestInformation", Long.class, RequestPreAuthorizationInfoRequest.class,
                org.springframework.security.core.Authentication.class);
        assertEquals("/{id:\\d+}/approve-partial", partial.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("/{id:\\d+}/request-info", info.getAnnotation(PostMapping.class).value()[0]);
    }
}
