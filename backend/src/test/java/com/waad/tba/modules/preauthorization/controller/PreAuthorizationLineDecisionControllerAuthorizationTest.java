package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.modules.preauthorization.api.PreAuthorizationApiMapper;
import com.waad.tba.modules.preauthorization.api.response.PreAuthorizationResponse;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDecisionDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationResponseDto;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationAttachmentService;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationService;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.MethodSecurityConfig;
import com.waad.tba.security.ProviderContextGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): HTTP-layer authorization coverage for
 * the new per-line decision endpoints — exercises the real Spring Security
 * {@code @PreAuthorize} enforcement on the actual controller bean (not a
 * mocked service call), mirroring the established pattern in
 * {@code MemberDuplicateControllerAuthorizationTest}. The service itself is
 * mocked here on purpose: role/route authorization is an HTTP-layer concern
 * independent of business logic, which is already covered exhaustively by
 * {@code PreAuthorizationMultiLineIntegrationTest} (real DB) and
 * {@code PreAuthorizationServiceReviewerIsolationTest} (per-provider
 * isolation, mocked repository).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { PreAuthorizationController.class, MethodSecurityConfig.class })
class PreAuthorizationLineDecisionControllerAuthorizationTest {

    @Autowired
    private PreAuthorizationController controller;

    @MockitoBean private PreAuthorizationService preAuthorizationService;
    @MockitoBean private PreAuthorizationAttachmentService attachmentService;
    @MockitoBean private PreAuthorizationApiMapper apiMapper;
    @MockitoBean private PreAuthorizationRepository preAuthorizationRepository;
    @MockitoBean private ProviderContextGuard providerContextGuard;
    @MockitoBean private AuthorizationService authorizationService;
    @MockitoBean private ReviewerProviderIsolationService reviewerIsolationService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AccessDeniedAdvice())
                .build();
    }

    /** Mirrors MemberDuplicateControllerAuthorizationTest's own advice — see its javadoc. */
    @RestControllerAdvice
    static class AccessDeniedAdvice {
        @ExceptionHandler(AccessDeniedException.class)
        ResponseEntity<Void> handle(AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    private void stubServiceSuccess() {
        when(preAuthorizationService.submitLineDecision(anyLong(), anyLong(), any(PreAuthorizationLineDecisionDto.class), anyString()))
                .thenReturn(PreAuthorizationResponseDto.builder().id(1L).status("UNDER_REVIEW").build());
        when(preAuthorizationService.finalizePreAuthorizationReview(anyLong(), anyString()))
                .thenReturn(PreAuthorizationResponseDto.builder().id(1L).status("APPROVED").build());
        when(apiMapper.toResponse(any(PreAuthorizationResponseDto.class)))
                .thenReturn(PreAuthorizationResponse.builder().id(1L).status("APPROVED").build());
    }

    @Test
    @WithMockUser(roles = "MEDICAL_REVIEWER")
    void medicalReviewer_canSubmitLineDecision_returns200() throws Exception {
        stubServiceSuccess();
        mockMvc().perform(post("/api/v1/pre-authorizations/1/lines/10/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MEDICAL_REVIEWER")
    void medicalReviewer_canFinalizeReview_returns200() throws Exception {
        stubServiceSuccess();
        mockMvc().perform(post("/api/v1/pre-authorizations/1/finalize"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PROVIDER_STAFF")
    void providerStaff_cannotSubmitLineDecision_returns403() throws Exception {
        mockMvc().perform(post("/api/v1/pre-authorizations/1/lines/10/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROVIDER_STAFF")
    void providerStaff_cannotFinalizeReview_returns403() throws Exception {
        mockMvc().perform(post("/api/v1/pre-authorizations/1/finalize"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYER_ADMIN")
    void employerAdmin_cannotSubmitLineDecision_returns403() throws Exception {
        mockMvc().perform(post("/api/v1/pre-authorizations/1/lines/10/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MEDICAL_REVIEWER")
    void submitLineDecision_missingDecisionField_returns400() throws Exception {
        mockMvc().perform(post("/api/v1/pre-authorizations/1/lines/10/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
