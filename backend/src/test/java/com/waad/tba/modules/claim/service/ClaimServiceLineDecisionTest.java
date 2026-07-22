package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.service.ArchitecturalGuardService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.api.request.LineDecisionRequest;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimAuditLog;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.entity.LineReviewDecision;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.claim.repository.ClaimLineRepository;
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
 * CLAIM-REVIEW-SPLIT-2C: verifies {@code ClaimService.submitLineDecision} —
 * authorization/isolation, status locking, reason requirements, audit, and
 * (critically) that the claim-level financial fields are never touched.
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceLineDecisionTest {

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
    @Mock private ClaimLineRepository claimLineRepository;
    @Mock private ClaimReferenceService claimReferenceService;
    @Mock private jakarta.persistence.EntityManager em;

    @InjectMocks
    private ClaimService claimService;

    private User reviewer;
    private Claim claim;
    private ClaimLine line;

    @BeforeEach
    void setUp() {
        reviewer = User.builder().id(2L).username("reviewer").userType("MEDICAL_REVIEWER").build();
        claim = Claim.builder()
                .id(100L)
                .status(ClaimStatus.UNDER_REVIEW)
                .providerId(60L)
                .requestedAmount(new BigDecimal("500"))
                .approvedAmount(new BigDecimal("400"))
                .netProviderAmount(new BigDecimal("400"))
                .build();
        line = ClaimLine.builder()
                .id(10L)
                .claim(claim)
                .serviceCode("SRV-1")
                .quantity(1)
                .unitPrice(new BigDecimal("100"))
                .rejected(false)
                .build();
    }

    @Test
    void approve_assignedReviewer_persistsDecisionAndClearsRejection() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimLineRepository.findByIdAndClaimId(10L, 100L)).thenReturn(Optional.of(line));
        when(claimLineRepository.save(any(ClaimLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimMapper.toLineDto(any(ClaimLine.class))).thenReturn(ClaimLineDto.builder().id(10L).build());

        LineDecisionRequest request = LineDecisionRequest.builder().decision(LineReviewDecision.APPROVED).build();
        claimService.submitLineDecision(100L, 10L, request);

        ArgumentCaptor<ClaimLine> captor = ArgumentCaptor.forClass(ClaimLine.class);
        verify(claimLineRepository).save(captor.capture());
        assertThat(captor.getValue().getReviewerDecision()).isEqualTo(LineReviewDecision.APPROVED);
        assertThat(captor.getValue().getRejected()).isFalse();
        assertThat(captor.getValue().getRejectionReason()).isNull();

        // Financial invariant: claim itself is never saved.
        verify(claimRepository, never()).save(any());
    }

    @Test
    void reject_withReason_persistsRejectedAndReason() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimLineRepository.findByIdAndClaimId(10L, 100L)).thenReturn(Optional.of(line));
        when(claimLineRepository.save(any(ClaimLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimMapper.toLineDto(any(ClaimLine.class))).thenReturn(ClaimLineDto.builder().id(10L).build());

        LineDecisionRequest request = LineDecisionRequest.builder()
                .decision(LineReviewDecision.REJECTED)
                .reason("خدمة غير مغطاة")
                .build();
        claimService.submitLineDecision(100L, 10L, request);

        ArgumentCaptor<ClaimLine> captor = ArgumentCaptor.forClass(ClaimLine.class);
        verify(claimLineRepository).save(captor.capture());
        assertThat(captor.getValue().getReviewerDecision()).isEqualTo(LineReviewDecision.REJECTED);
        assertThat(captor.getValue().getRejected()).isTrue();
        assertThat(captor.getValue().getRejectionReason()).isEqualTo("خدمة غير مغطاة");
    }

    @Test
    void clarificationRequired_withReason_persistsWithoutMarkingRejected() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimLineRepository.findByIdAndClaimId(10L, 100L)).thenReturn(Optional.of(line));
        when(claimLineRepository.save(any(ClaimLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimMapper.toLineDto(any(ClaimLine.class))).thenReturn(ClaimLineDto.builder().id(10L).build());

        LineDecisionRequest request = LineDecisionRequest.builder()
                .decision(LineReviewDecision.CLARIFICATION_REQUIRED)
                .reason("يرجى تأكيد التشخيص")
                .build();
        claimService.submitLineDecision(100L, 10L, request);

        ArgumentCaptor<ClaimLine> captor = ArgumentCaptor.forClass(ClaimLine.class);
        verify(claimLineRepository).save(captor.capture());
        assertThat(captor.getValue().getReviewerDecision()).isEqualTo(LineReviewDecision.CLARIFICATION_REQUIRED);
        assertThat(captor.getValue().getRejected()).isFalse();
        assertThat(captor.getValue().getRejectionReason()).isEqualTo("يرجى تأكيد التشخيص");
    }

    @Test
    void unassignedReviewer_isBlocked() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doThrow(new AccessDeniedException("لا يملك المراجع صلاحية الوصول لمقدم الخدمة هذا"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);

        LineDecisionRequest request = LineDecisionRequest.builder().decision(LineReviewDecision.APPROVED).build();

        assertThatThrownBy(() -> claimService.submitLineDecision(100L, 10L, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(claimLineRepository, never()).save(any());
    }

    @Test
    void lineNotBelongingToClaim_throwsNotFound() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimLineRepository.findByIdAndClaimId(10L, 100L)).thenReturn(Optional.empty());

        LineDecisionRequest request = LineDecisionRequest.builder().decision(LineReviewDecision.APPROVED).build();

        assertThatThrownBy(() -> claimService.submitLineDecision(100L, 10L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(claimLineRepository, never()).save(any());
    }

    @Test
    void terminalClaimStatus_isLocked() {
        claim.setStatus(ClaimStatus.APPROVED);
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);

        LineDecisionRequest request = LineDecisionRequest.builder().decision(LineReviewDecision.APPROVED).build();

        assertThatThrownBy(() -> claimService.submitLineDecision(100L, 10L, request))
                .isInstanceOf(BusinessRuleException.class);

        verify(claimLineRepository, never()).findByIdAndClaimId(any(), any());
        verify(claimLineRepository, never()).save(any());
    }

    @Test
    void rejectWithoutReason_throwsBusinessRuleException() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimLineRepository.findByIdAndClaimId(10L, 100L)).thenReturn(Optional.of(line));

        LineDecisionRequest request = LineDecisionRequest.builder().decision(LineReviewDecision.REJECTED).build();

        assertThatThrownBy(() -> claimService.submitLineDecision(100L, 10L, request))
                .isInstanceOf(BusinessRuleException.class);

        verify(claimLineRepository, never()).save(any());
    }

    @Test
    void clarificationWithoutReason_throwsBusinessRuleException() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimLineRepository.findByIdAndClaimId(10L, 100L)).thenReturn(Optional.of(line));

        LineDecisionRequest request = LineDecisionRequest.builder().decision(LineReviewDecision.CLARIFICATION_REQUIRED).build();

        assertThatThrownBy(() -> claimService.submitLineDecision(100L, 10L, request))
                .isInstanceOf(BusinessRuleException.class);

        verify(claimLineRepository, never()).save(any());
    }

    @Test
    void decision_neverSavesOrMutatesParentClaimFinancialFields() {
        BigDecimal originalApproved = claim.getApprovedAmount();
        BigDecimal originalNet = claim.getNetProviderAmount();

        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimLineRepository.findByIdAndClaimId(10L, 100L)).thenReturn(Optional.of(line));
        when(claimLineRepository.save(any(ClaimLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimMapper.toLineDto(any(ClaimLine.class))).thenReturn(ClaimLineDto.builder().id(10L).build());

        LineDecisionRequest request = LineDecisionRequest.builder()
                .decision(LineReviewDecision.REJECTED)
                .reason("تجاوز الحد")
                .build();
        claimService.submitLineDecision(100L, 10L, request);

        // The exact same Claim instance's own fields are untouched.
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo(originalApproved);
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo(originalNet);
        verify(claimRepository, never()).save(any());
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void auditEvent_isWritten() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimLineRepository.findByIdAndClaimId(10L, 100L)).thenReturn(Optional.of(line));
        when(claimLineRepository.save(any(ClaimLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimMapper.toLineDto(any(ClaimLine.class))).thenReturn(ClaimLineDto.builder().id(10L).build());

        LineDecisionRequest request = LineDecisionRequest.builder().decision(LineReviewDecision.APPROVED).build();
        claimService.submitLineDecision(100L, 10L, request);

        verify(claimAuditService, times(1)).recordChange(
                eq(claim), eq(ClaimAuditLog.ChangeType.LINE_DECISION), eq(reviewer), any(String.class), eq(claim));
    }

    @Test
    void reload_returnedDtoReflectsPersistedDecision() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(claimLineRepository.findByIdAndClaimId(10L, 100L)).thenReturn(Optional.of(line));
        when(claimLineRepository.save(any(ClaimLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimMapper.toLineDto(any(ClaimLine.class))).thenAnswer(inv -> {
            ClaimLine savedLine = inv.getArgument(0);
            return ClaimLineDto.builder().id(savedLine.getId()).reviewerDecision(savedLine.getReviewerDecision()).build();
        });

        LineDecisionRequest request = LineDecisionRequest.builder().decision(LineReviewDecision.APPROVED).build();
        ClaimLineDto result = claimService.submitLineDecision(100L, 10L, request);

        assertThat(result.getReviewerDecision()).isEqualTo(LineReviewDecision.APPROVED);
    }
}
