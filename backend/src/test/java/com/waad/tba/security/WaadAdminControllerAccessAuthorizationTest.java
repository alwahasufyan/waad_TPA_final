package com.waad.tba.security;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.controller.ClaimBatchController;
import com.waad.tba.modules.claim.dto.ClaimBatchResponse;
import com.waad.tba.modules.claim.service.ClaimBatchService;
import com.waad.tba.modules.dashboard.controller.DashboardController;
import com.waad.tba.modules.dashboard.dto.DashboardSummaryDto;
import com.waad.tba.modules.dashboard.service.DashboardService;
import com.waad.tba.modules.member.controller.MemberDuplicateController;
import com.waad.tba.modules.member.dto.MemberDuplicateGroupDto;
import com.waad.tba.modules.member.service.MemberDuplicateService;
import com.waad.tba.modules.claim.controller.MedicalReviewerProviderAssignmentController;
import com.waad.tba.modules.claim.dto.MedicalReviewerProviderAssignmentsResponse;
import com.waad.tba.modules.claim.service.MedicalReviewerProviderAssignmentService;
import com.waad.tba.modules.providercontract.controller.ContractPriceEditController;
import com.waad.tba.modules.providercontract.service.ContractPriceEditService;
import com.waad.tba.modules.rbac.controller.UserController;
import com.waad.tba.modules.rbac.dto.UserResponseDto;
import com.waad.tba.modules.rbac.service.EffectivePermissionService;
import com.waad.tba.modules.rbac.service.UserService;
import com.waad.tba.modules.report.controller.FinancialReportController;
import com.waad.tba.modules.report.dto.FinancialConsolidationDto;
import com.waad.tba.modules.report.service.FinancialConsolidationService;
import com.waad.tba.modules.settlement.controller.PaymentController;
import com.waad.tba.modules.settlement.dto.MonthlySettlementSummaryDto;
import com.waad.tba.modules.settlement.service.PaymentService;
import com.waad.tba.modules.systemadmin.controller.FeatureFlagController;
import com.waad.tba.modules.systemadmin.dto.FeatureFlagDto;
import com.waad.tba.modules.systemadmin.service.FeatureFlagService;
import com.waad.tba.common.controller.SystemSettingsController;
import com.waad.tba.common.entity.SystemSetting;
import com.waad.tba.common.repository.SystemSettingRepository;
import com.waad.tba.common.service.SystemSettingsService;
import com.waad.tba.modules.claim.service.SlaMonitoringScheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WAAD-RBAC-PHASE-3A-WAAD-ADMIN-FULL-ACCESS.
 *
 * Exercises the REAL Spring Security @PreAuthorize enforcement (method-security
 * AOP on the actual controller bean, via a minimal explicit context — the same
 * pattern already used by KinshipMismatchControllerAuthorizationTest /
 * MemberDuplicateControllerAuthorizationTest) for a representative controller
 * from each module this ticket opened to WAAD_ADMIN, plus one confirming a
 * non-admin role still cannot reach an admin-only endpoint.
 *
 * These are deliberately NOT full @SpringBootTest/MockMvc integration tests
 * (no DB, no JWT/session filter chain, no web server) — that would be heavy
 * for what's being verified here (whether @PreAuthorize accepts WAAD_ADMIN),
 * or a full 403 (or 200) is a "role not in the annotation" issue that a real
 * request would also hit through the full stack. A true end-to-end check
 * (login as WAAD_ADMIN, click through the UI) is documented as unavailable in
 * this session in the Phase 3A report and left for a browser-capable session.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        DashboardController.class,
        MemberDuplicateController.class,
        MedicalReviewerProviderAssignmentController.class,
        ContractPriceEditController.class,
        ClaimBatchController.class,
        FinancialReportController.class,
        PaymentController.class,
        FeatureFlagController.class,
        SystemSettingsController.class,
        UserController.class,
        MethodSecurityConfig.class
})
class WaadAdminControllerAccessAuthorizationTest {

    @Autowired private DashboardController dashboardController;
    @MockitoBean private DashboardService dashboardService;

    @Autowired private MemberDuplicateController memberDuplicateController;
    @MockitoBean private MemberDuplicateService memberDuplicateService;
    @MockitoBean private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired private MedicalReviewerProviderAssignmentController medicalReviewerProviderAssignmentController;
    @MockitoBean private MedicalReviewerProviderAssignmentService medicalReviewerProviderAssignmentService;

    @Autowired private ContractPriceEditController contractPriceEditController;
    @MockitoBean private ContractPriceEditService contractPriceEditService;

    @Autowired private ClaimBatchController claimBatchController;
    @MockitoBean private ClaimBatchService claimBatchService;
    @MockitoBean private com.waad.tba.modules.claim.service.ReviewerProviderIsolationService reviewerIsolationService;

    @Autowired private FinancialReportController financialReportController;
    @MockitoBean private FinancialConsolidationService financialConsolidationService;
    @MockitoBean private com.waad.tba.modules.report.service.CompanyProfitReportService companyProfitReportService;

    @Autowired private PaymentController paymentController;
    @MockitoBean private PaymentService paymentService;

    @Autowired private FeatureFlagController featureFlagController;
    @MockitoBean private FeatureFlagService featureFlagService;

    @Autowired private SystemSettingsController systemSettingsController;
    @MockitoBean private SystemSettingsService systemSettingsService;
    @MockitoBean private SystemSettingRepository systemSettingRepository;
    @MockitoBean private SlaMonitoringScheduler slaMonitoringScheduler;
    @MockitoBean private com.waad.tba.security.AuthorizationService authorizationService;

    @Autowired private UserController userController;
    @MockitoBean private UserService userService;
    @MockitoBean private EffectivePermissionService effectivePermissionService;

    private MockMvc mockMvcFor(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AccessDeniedAdvice())
                .setCustomArgumentResolvers(
                        new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
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
    @WithMockUser(roles = "WAAD_ADMIN")
    void waadAdmin_canOpenDashboard() throws Exception {
        when(dashboardService.getSummary(any())).thenReturn(new DashboardSummaryDto());
        mockMvcFor(dashboardController)
                .perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAAD_ADMIN")
    void waadAdmin_canAccessMembers() throws Exception {
        when(memberDuplicateService.findDuplicates()).thenReturn(List.<MemberDuplicateGroupDto>of());
        mockMvcFor(memberDuplicateController)
                .perform(get("/api/v1/system-settings/member-duplicates"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAAD_ADMIN")
    void waadAdmin_canAccessProviders() throws Exception {
        when(medicalReviewerProviderAssignmentService.getAssignments(1L))
                .thenReturn(new MedicalReviewerProviderAssignmentsResponse(1L, List.of()));
        mockMvcFor(medicalReviewerProviderAssignmentController)
                .perform(get("/api/v1/admin/medical-reviewers/1/providers"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAAD_ADMIN")
    void waadAdmin_canAccessProviderContracts() throws Exception {
        when(contractPriceEditService.auditTrail(anyLong())).thenReturn(List.of());
        mockMvcFor(contractPriceEditController)
                .perform(get("/api/v1/provider-contracts/1/pricing/audit"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAAD_ADMIN")
    void waadAdmin_canAccessClaims() throws Exception {
        when(claimBatchService.getExistingBatch(1L, 1L, 2026, 1)).thenReturn(null);
        mockMvcFor(claimBatchController)
                .perform(get("/api/v1/claim-batches/current")
                        .param("providerId", "1")
                        .param("employerId", "1")
                        .param("year", "2026")
                        .param("month", "1"))
                .andExpect(status().isNotFound()); // null batch -> 404, but past the security layer
    }

    @Test
    @WithMockUser(roles = "WAAD_ADMIN")
    void waadAdmin_canAccessReports() throws Exception {
        when(financialConsolidationService.getMonthlyFinancialConsolidation(2026))
                .thenReturn(List.<FinancialConsolidationDto>of());
        mockMvcFor(financialReportController)
                .perform(get("/api/v1/reports/financial-consolidation").param("year", "2026"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAAD_ADMIN")
    void waadAdmin_canAccessSettlements() throws Exception {
        when(paymentService.getMonthlySettlementSummaries(any(), any(), any(), any(), any()))
                .thenReturn(List.<MonthlySettlementSummaryDto>of());
        mockMvcFor(paymentController)
                .perform(get("/api/v1/payments/summaries"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAAD_ADMIN")
    void waadAdmin_canAccessSystemSettingsIfProductAllowsFullAdmin() throws Exception {
        // Product decision (this ticket): WAAD_ADMIN IS a full operational admin
        // for routine system settings — FeatureFlagController and
        // SystemSettingsController were both widened; only DangerZoneController
        // (system reset/restore/OTP) and SystemAdminController (test-data
        // reset/seed) remain SUPER_ADMIN-only, see Phase 3A report §9.
        when(featureFlagService.getAllFeatureFlags()).thenReturn(List.<FeatureFlagDto>of());
        mockMvcFor(featureFlagController)
                .perform(get("/api/v1/admin/features/public"))
                .andExpect(status().isOk());

        when(systemSettingsService.getEditableSettings()).thenReturn(List.<SystemSetting>of());
        mockMvcFor(systemSettingsController)
                .perform(get("/api/v1/admin/system-settings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MEDICAL_REVIEWER")
    void nonAdminStillCannotAccessAdminEndpoints() throws Exception {
        // MEDICAL_REVIEWER was never granted access to /admin/users (only
        // SUPER_ADMIN/WAAD_ADMIN) — confirms this ticket's widening didn't
        // accidentally expand access for roles it wasn't meant to touch.
        mockMvcFor(userController)
                .perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }
}
