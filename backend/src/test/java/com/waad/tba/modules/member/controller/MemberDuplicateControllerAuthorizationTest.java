package com.waad.tba.modules.member.controller;

import com.waad.tba.modules.member.service.MemberDuplicateService;
import com.waad.tba.security.MethodSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * BACKEND-RBAC-FIX-MISSING-AUTH-1: MemberDuplicateController previously had
 * no authorization check at all — these tests exercise the real Spring
 * Security @PreAuthorize enforcement (method-security AOP on the actual
 * controller bean), not just a mocked service call, so a regression here
 * (a removed/typo'd annotation) fails the build.
 *
 * Deliberately NOT a @WebMvcTest: this repo has a stray @SpringBootApplication
 * class (src/test/java/com/waad/tba/CheckLogic.java) that makes
 * @SpringBootConfiguration auto-detection ambiguous, and pointing
 * @WebMvcTest at the real application class pulls in every Filter bean
 * app-wide (JWT/session/rate-limit/monitoring/maintenance filters), which in
 * turn need JWT secrets, a DataSource, etc. — far heavier than this ticket's
 * scope. Instead this builds a minimal, explicit Spring context containing
 * only the controller under test + MethodSecurityConfig (@EnableMethodSecurity),
 * which is sufficient to exercise the real @PreAuthorize interceptor via a
 * standalone MockMvc — no HTTP-level security filter chain is needed since
 * @PreAuthorize is method-level AOP, not a servlet filter concern, and
 * @WithMockUser populates the SecurityContext directly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { MemberDuplicateController.class, MethodSecurityConfig.class })
class MemberDuplicateControllerAuthorizationTest {

    @Autowired
    private MemberDuplicateController controller;

    @MockitoBean
    private MemberDuplicateService duplicateService;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AccessDeniedAdvice())
                .build();
    }

    /**
     * Standalone MockMvc has no servlet-level ExceptionTranslationFilter (that
     * only exists inside the full Spring Security filter chain, which this
     * test deliberately avoids — see class javadoc). Without it, the real
     * AccessDeniedException thrown by the @PreAuthorize interceptor would
     * propagate as an uncaught servlet exception instead of a 403 response.
     * This advice reproduces just that one piece of translation.
     */
    @RestControllerAdvice
    static class AccessDeniedAdvice {
        @ExceptionHandler(AccessDeniedException.class)
        ResponseEntity<Void> handle(AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @Test
    @WithMockUser(roles = "PROVIDER_STAFF")
    void providerStaff_cannotMergeMembers_returns403() throws Exception {
        mockMvc().perform(post("/api/v1/system-settings/member-duplicates/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryMemberId\":1,\"duplicateMemberIds\":[2]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FINANCE_VIEWER")
    void financeViewer_cannotResetKinship_returns403() throws Exception {
        mockMvc().perform(get("/api/v1/system-settings/member-duplicates/reset-kinship"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MEDICAL_REVIEWER")
    void medicalReviewer_cannotMergeMembers_returns403() throws Exception {
        mockMvc().perform(post("/api/v1/system-settings/member-duplicates/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryMemberId\":1,\"duplicateMemberIds\":[2]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdmin_canAccess_or_reachesServiceLayer() throws Exception {
        when(duplicateService.findDuplicates()).thenReturn(List.of());

        mockMvc().perform(get("/api/v1/system-settings/member-duplicates"))
                .andExpect(status().isOk());
    }
}
