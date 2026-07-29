package com.waad.tba.modules.member.controller;

import com.waad.tba.modules.member.dto.KinshipMismatchDto;
import com.waad.tba.modules.member.service.KinshipMismatchService;
import com.waad.tba.security.MethodSecurityConfig;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BACKEND-RBAC-FIX-MISSING-AUTH-1: KinshipMismatchController previously had
 * no authorization check at all — these tests exercise the real Spring
 * Security @PreAuthorize enforcement (method-security AOP on the actual
 * controller bean). See MemberDuplicateControllerAuthorizationTest for why
 * this uses a minimal explicit context instead of @WebMvcTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { KinshipMismatchController.class, MethodSecurityConfig.class })
class KinshipMismatchControllerAuthorizationTest {

    @Autowired
    private KinshipMismatchController controller;

    @MockitoBean
    private KinshipMismatchService kinshipMismatchService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AccessDeniedAdvice())
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
    void providerStaff_cannotBulkFixKinshipMismatch_returns403() throws Exception {
        mockMvc().perform(post("/api/v1/system-settings/kinship-mismatches/bulk-fix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[1,2]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYER_ADMIN")
    void employerAdmin_cannotIgnoreKinshipMismatch_returns403() throws Exception {
        mockMvc().perform(post("/api/v1/system-settings/kinship-mismatches/1/ignore"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdmin_canAccess_or_reachesServiceLayer() throws Exception {
        when(kinshipMismatchService.findMismatches()).thenReturn(List.<KinshipMismatchDto>of());

        mockMvc().perform(get("/api/v1/system-settings/kinship-mismatches"))
                .andExpect(status().isOk());
    }
}
