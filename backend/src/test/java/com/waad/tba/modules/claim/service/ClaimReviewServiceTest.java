package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.dto.ClaimApproveDto;
import com.waad.tba.modules.claim.dto.ClaimRejectDto;
import com.waad.tba.modules.claim.dto.ClaimReviewDto;
import com.waad.tba.modules.claim.dto.ClaimSettleDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.settlement.service.ProviderAccountService;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;

@ExtendWith(MockitoExtension.class)
class ClaimReviewServiceTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ClaimMapper claimMapper;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ReviewerProviderIsolationService reviewerIsolationService;
    @Mock
    private ClaimStateMachine claimStateMachine;
    @Mock
    private AtomicFinancialService atomicFinancialService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BenefitPolicyCoverageService benefitPolicyCoverageService;
    @Mock
    private com.waad.tba.common.service.BusinessDaysCalculatorService businessDaysCalculator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ClaimAuditService claimAuditService;
    @Mock
    private ProviderAccountService providerAccountService;
    @Mock
    private MedicalAuditLogService medicalAuditLogService;

    @InjectMocks
    private ClaimReviewService claimReviewService;

    private User reviewer;
    private Claim claim;
    private Member member;

    @BeforeEach
    void setUp() {
        reviewer = User.builder().id(1L).username("reviewer").userType("MEDICAL_REVIEWER").build();
        member = Member.builder().id(10L).fullName("Test Member").build();
        claim = Claim.builder()
                .id(100L)
                .status(ClaimStatus.SUBMITTED)
                .member(member)
                .providerId(50L)
                .requestedAmount(new BigDecimal("1000"))
                .build();
    }

    @Test
    void startReview_shouldTransitionStatus() {
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.startReview(100L);

        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.UNDER_REVIEW), eq(reviewer), any());
        verify(claimRepository).save(claim);
    }

    @Test
    void startReview_reviewerNotAssignedToProvider_shouldThrowAccessDenied() {
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException(
                "Medical reviewer does not have access to this provider"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 50L);

        assertThatThrownBy(() -> claimReviewService.startReview(100L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(claimStateMachine, org.mockito.Mockito.never()).transition(any(), any(), any(), any());
        verify(claimRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void startReview_invalidStatus_shouldThrowException() {
        claim.setStatus(ClaimStatus.APPROVED);
        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimReviewService.startReview(100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا يمكن بدء المراجعة");
    }

    @Test
    void rejectClaim_shouldTransitionToRejected() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimRejectDto dto = new ClaimRejectDto();
        dto.setRejectionReason("Medical necessity not proven");

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.rejectClaim(100L, dto);

        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 50L);
        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.REJECTED), eq(reviewer), any());
        assertThat(claim.getReviewerComment()).isEqualTo("Medical necessity not proven");
    }

    @Test
    void rejectClaim_missingReason_shouldThrowException() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimRejectDto dto = new ClaimRejectDto(); // No reason

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);

        assertThatThrownBy(() -> claimReviewService.rejectClaim(100L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("سبب الرفض مطلوب");
    }

    @Test
    void settleClaim_shouldTransitionToSettled() {
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setApprovedAmount(new BigDecimal("800"));
        claim.setNetProviderAmount(new BigDecimal("800"));

        ClaimSettleDto dto = new ClaimSettleDto();
        dto.setPaymentReference("PAY-123");
        dto.setSettlementAmount(new BigDecimal("800"));

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.settleClaim(100L, dto);

        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.SETTLED), eq(reviewer), any());
        assertThat(claim.getPaymentReference()).isEqualTo("PAY-123");
        assertThat(claim.getSettledAt()).isNotNull();
    }

    @Test
    void settleClaim_excessiveAmount_shouldThrowException() {
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setNetProviderAmount(new BigDecimal("800"));

        ClaimSettleDto dto = new ClaimSettleDto();
        dto.setPaymentReference("PAY-123");
        dto.setSettlementAmount(new BigDecimal("900")); // Exceeds approved

        when(claimRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimReviewService.settleClaim(100L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("يتجاوز المبلغ المستحق");
    }

    @Test
    void requestApproval_shouldInitiateAsyncPhase() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimApproveDto dto = new ClaimApproveDto();
        dto.setNotes("Approving this");

        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.requestApproval(100L, dto);

        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.APPROVAL_IN_PROGRESS), eq(reviewer), any());
        // Note: processApprovalAsync is called after this, but since it's @Async it
        // might be mocked or handled differently in full integration tests.
        // In unit tests, we just verify the first phase.
    }

    @Test
    void requestApproval_reviewerAssignedToProvider_isolationCheckedBeforeTransition() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimApproveDto dto = new ClaimApproveDto();
        dto.setNotes("Approving this");

        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.requestApproval(100L, dto);

        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 50L);
        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.APPROVAL_IN_PROGRESS), eq(reviewer), any());
    }

    @Test
    void requestApproval_reviewerNotAssignedToProvider_shouldThrowAccessDenied() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimApproveDto dto = new ClaimApproveDto();
        dto.setNotes("Approving this");

        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException(
                "Medical reviewer does not have access to this provider"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 50L);

        assertThatThrownBy(() -> claimReviewService.requestApproval(100L, dto))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(claimStateMachine, org.mockito.Mockito.never()).transition(any(), any(), any(), any());
        verify(claimRepository, org.mockito.Mockito.never()).save(any());
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLAIMS-APPROVAL-CALC-1: PUT /review must not be able to create an
    // approvedAmount / netProviderAmount divergence.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void reviewClaim_approvedStatus_shouldBeRejected_cannotDivergeAmounts() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimReviewDto dto = new ClaimReviewDto();
        dto.setStatus(ClaimStatus.APPROVED);
        dto.setApprovedAmount(new BigDecimal("160")); // pre-discount figure a reviewer might type

        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.isReviewer(reviewer)).thenReturn(true);

        assertThatThrownBy(() -> claimReviewService.reviewClaim(100L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا يمكن اعتماد المطالبة عبر مسار المراجعة العام");

        // The claim must be left completely untouched: no partial write of approvedAmount,
        // no state transition, no save.
        assertThat(claim.getApprovedAmount()).isNull();
        assertThat(claim.getNetProviderAmount()).isNull();
        verify(claimStateMachine, org.mockito.Mockito.never()).transition(any(), any(), any(), any());
        verify(claimRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void reviewClaim_needsCorrection_stillWorks() {
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        ClaimReviewDto dto = new ClaimReviewDto();
        dto.setStatus(ClaimStatus.NEEDS_CORRECTION);
        dto.setReviewerComment("Missing lab report, please resubmit");

        when(claimRepository.findById(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.isReviewer(reviewer)).thenReturn(true);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toViewDto(any(Claim.class))).thenReturn(new ClaimViewDto());

        claimReviewService.reviewClaim(100L, dto);

        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 50L);
        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.NEEDS_CORRECTION), eq(reviewer), any());
        assertThat(claim.getReviewerComment()).isEqualTo("Missing lab report, please resubmit");
    }

    @Test
    void processApprovalAsync_appliesDiscount_approvedAmountEqualsNetProviderAmount() {
        claim.setStatus(ClaimStatus.APPROVAL_IN_PROGRESS);
        claim.setRequestedAmount(new BigDecimal("1000"));
        claim.setRefusedAmount(BigDecimal.ZERO);
        claim.setAppliedDiscountPercent(new BigDecimal("10"));
        claim.setDiscountBeforeRejection(true);

        ClaimApproveDto dto = new ClaimApproveDto();
        dto.setUseSystemCalculation(true);

        com.waad.tba.modules.claim.service.CostCalculationService.CostBreakdown breakdown =
                new com.waad.tba.modules.claim.service.CostCalculationService.CostBreakdown(
                        new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO,
                        com.waad.tba.common.enums.NetworkType.IN_NETWORK);

        when(claimRepository.findByIdForFinancialUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(atomicFinancialService.calculateCostsWithAtomicDeductible(claim)).thenReturn(breakdown);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);

        claimReviewService.processApprovalAsync(100L, dto);

        // providerShare = 1000 - 200 = 800; discount 10% = 80; net = 720
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo(new BigDecimal("720"));
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo(claim.getNetProviderAmount());
        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.APPROVED), eq(reviewer), any());
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLAIMS-FINANCIAL-SOURCE-OF-TRUTH-1: approval must use each line's
    // already-correct patientShare (from the real matched benefit rule at
    // claim creation), never CostCalculationService's independent
    // category-lookup — which lacks the mirror/root-category fallback the
    // creation-time engine has and can silently fall back to the policy's
    // generic default coverage% instead of the real matched rule.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void processApprovalAsync_usesLinePatientShare_notCostCalculationServiceFallback() {
        // requestedTotal=200, real matched coveragePercent=75% -> patientShare=50,
        // companyShareBeforeDiscount=150; contract discount 10%.
        claim.setStatus(ClaimStatus.APPROVAL_IN_PROGRESS);
        claim.setRequestedAmount(new BigDecimal("200"));
        claim.setRefusedAmount(BigDecimal.ZERO);
        claim.setAppliedDiscountPercent(new BigDecimal("10"));
        claim.setDiscountBeforeRejection(true);

        com.waad.tba.modules.claim.entity.ClaimLine line = com.waad.tba.modules.claim.entity.ClaimLine.builder()
                .id(1L)
                .claim(claim)
                .requestedTotal(new BigDecimal("200"))
                .patientShare(new BigDecimal("50"))
                .companyShareBeforeDiscount(new BigDecimal("150"))
                .companyShare(new BigDecimal("135"))
                .refusedAmount(BigDecimal.ZERO)
                .rejected(false)
                .build();
        claim.setLines(java.util.List.of(line));

        ClaimApproveDto dto = new ClaimApproveDto();
        dto.setUseSystemCalculation(true);

        // A deliberately WRONG breakdown (as CostCalculationService's buggy
        // category fallback would produce: 80% coverage -> patientResponsibility=40)
        // must NOT be used now that real lines are present.
        com.waad.tba.modules.claim.service.CostCalculationService.CostBreakdown wrongBreakdown =
                new com.waad.tba.modules.claim.service.CostCalculationService.CostBreakdown(
                        new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("40"), BigDecimal.ZERO, BigDecimal.ZERO,
                        com.waad.tba.common.enums.NetworkType.IN_NETWORK);

        when(claimRepository.findByIdForFinancialUpdate(100L)).thenReturn(Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(atomicFinancialService.calculateCostsWithAtomicDeductible(claim)).thenReturn(wrongBreakdown);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);

        claimReviewService.processApprovalAsync(100L, dto);

        // Real: patientCoPay=50, providerShare=150, discount 10%=15, net=135.
        // Buggy (must NOT happen): patientCoPay=40, providerShare=160, discount=16, net=144.
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo(new BigDecimal("135"));
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("135"));
    }
}
