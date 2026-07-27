package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.modules.preauthorization.repository.PreAuthEmailRequestRepository;
import com.waad.tba.security.MethodSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BACKEND-RBAC-FIX-MISSING-AUTH-1: PreAuthEmailRequestController previously
 * had no authorization check at all — these tests exercise the real Spring
 * Security @PreAuthorize enforcement (method-security AOP on the actual
 * controller bean). Reads are SUPER_ADMIN + MEDICAL_REVIEWER; delete
 * defaults to SUPER_ADMIN only. See
 * MemberDuplicateControllerAuthorizationTest for why this uses a minimal
 * explicit context instead of @WebMvcTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { PreAuthEmailRequestController.class, MethodSecurityConfig.class })
class PreAuthEmailRequestControllerAuthorizationTest {

    @Autowired
    private PreAuthEmailRequestController controller;

    @MockitoBean
    private PreAuthEmailRequestRepository repository;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AccessDeniedAdvice())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @RestControllerAdvice
    static class AccessDeniedAdvice {
        @ExceptionHandler(AccessDeniedException.class)
        ResponseEntity<Void> handle(AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @Test
    @WithMockUser(roles = "PROVIDER_STAFF")
    void providerStaff_cannotListEmailRequests_returns403() throws Exception {
        mockMvc().perform(get("/api/preauthorization/email-requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MEDICAL_REVIEWER")
    void medicalReviewer_canListEmailRequests_or_reachesServiceLayer() throws Exception {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc().perform(get("/api/preauthorization/email-requests"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MEDICAL_REVIEWER")
    void medicalReviewer_cannotDeleteEmailRequest_returns403() throws Exception {
        mockMvc().perform(delete("/api/preauthorization/email-requests/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdmin_canDeleteEmailRequest_or_reachesServiceLayer() throws Exception {
        doNothing().when(repository).deleteById(1L);

        mockMvc().perform(delete("/api/preauthorization/email-requests/1"))
                .andExpect(status().isNoContent());
    }
}
