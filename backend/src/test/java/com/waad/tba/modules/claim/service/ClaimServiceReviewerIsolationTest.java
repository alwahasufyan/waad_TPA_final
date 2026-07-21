package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import com.waad.tba.common.service.ArchitecturalGuardService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.dto.ClaimReturnForInfoDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationService;
import com.waad.tba.modules.provider.repository.ProviderAllowedEmployerRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.service.ProviderNetworkService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;

/**
 * CLAIM-REVIEW-SECURITY-1: verifies ClaimService's reviewer-facing endpoints
 * (returnForInfo, getCostBreakdown) enforce reviewer-provider isolation, not
 * just role membership.
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceReviewerIsolationTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private ClaimMapper claimMapper;
    @Mock private AuthorizationService authorizationService;
    @Mock private ProviderContextGuard providerContextGuard;
    @Mock private MedicalAuditLogService medicalAuditLogService;
    @Mock private MemberRepository memberRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private PreAuthorizationRepository preAuthorizationRepository;
    @Mock private BenefitPolicyCoverageService benefitPolicyCoverageService;
    @Mock private ClaimStateMachine claimStateMachine;
    @Mock private ProviderNetworkService providerNetworkService;
    @Mock private AttachmentRulesService attachmentRulesService;
    @Mock private CostCalculationService costCalculationService;
    @Mock private ClaimAuditService claimAuditService;
    @Mock private com.waad.tba.common.service.BusinessDaysCalculatorService businessDaysCalculator;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ArchitecturalGuardService architecturalGuard;
    @Mock private AtomicFinancialService atomicFinancialService;
    @Mock private PreAuthorizationService preAuthorizationService;
    @Mock private ReviewerProviderIsolationService reviewerIsolationService;
    @Mock private ProviderAllowedEmployerRepository providerAllowedEmployerRepository;
    @Mock private ClaimBatchService claimBatchService;
    @Mock private ClaimBatchRepository claimBatchRepository;
    @Mock private ClaimReviewService claimReviewService;
    @Mock private jakarta.persistence.EntityManager em;

    @InjectMocks
    private ClaimService claimService;

    private User reviewer;
    private Claim claim;

    @BeforeEach
    void setUp() {
        reviewer = User.builder().id(2L).username("reviewer").userType("MEDICAL_REVIEWER").build();
        claim = Claim.builder()
                .id(200L)
                .status(ClaimStatus.UNDER_REVIEW)
                .providerId(60L)
                .requestedAmount(new BigDecimal("500"))
                .build();
    }

    @Test
    void returnForInfo_reviewerAssignedToProvider_succeeds() {
        ClaimReturnForInfoDto dto = new ClaimReturnForInfoDto();
        dto.setReason("Missing lab report");

        when(claimRepository.findById(200L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new com.waad.tba.modules.claim.dto.ClaimViewDto());

        claimService.returnForInfo(200L, dto);

        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        verify(claimStateMachine).transition(claim, ClaimStatus.NEEDS_CORRECTION, reviewer);
    }

    @Test
    void returnForInfo_reviewerNotAssignedToProvider_throwsAccessDenied() {
        ClaimReturnForInfoDto dto = new ClaimReturnForInfoDto();
        dto.setReason("Missing lab report");

        when(claimRepository.findById(200L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("لا يملك المراجع صلاحية الوصول لمقدم الخدمة هذا"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);

        assertThatThrownBy(() -> claimService.returnForInfo(200L, dto))
                .isInstanceOf(AccessDeniedException.class);

        verify(claimStateMachine, never()).transition(any(), any(), any());
        verify(claimRepository, never()).save(any());
    }

    @Test
    void getCostBreakdown_reviewerNotAssignedToProvider_throwsAccessDenied() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.canAccessClaim(reviewer, 200L)).thenReturn(true);
        when(claimRepository.findById(200L)).thenReturn(Optional.of(claim));
        doThrow(new AccessDeniedException("لا يملك المراجع صلاحية الوصول لمقدم الخدمة هذا"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);

        assertThatThrownBy(() -> claimService.getCostBreakdown(200L))
                .isInstanceOf(AccessDeniedException.class);

        verify(costCalculationService, never()).calculateCosts(any());
    }

    @Test
    void getCostBreakdown_reviewerAssignedToProvider_succeeds() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.canAccessClaim(reviewer, 200L)).thenReturn(true);
        when(claimRepository.findById(200L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);

        claimService.getCostBreakdown(200L);

        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        verify(costCalculationService).calculateCosts(claim);
    }
}
