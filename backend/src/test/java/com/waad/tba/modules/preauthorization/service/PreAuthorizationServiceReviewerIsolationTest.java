package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.common.service.ArchitecturalGuardService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.Priority;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PREAUTH-REVIEW-WORKFLOW-1: verifies the reviewer-provider isolation gap
 * found in PREAUTH-REVIEW-WORKFLOW-AUDIT-1-REPORT.md §9 is closed —
 * getPendingInbox() now scopes MEDICAL_REVIEWER results to their assigned
 * providers (previously it returned every provider's pending items
 * regardless of assignment), and startReview() now enforces the same
 * assertReviewerAccess() check every other decision method already had.
 */
@ExtendWith(MockitoExtension.class)
class PreAuthorizationServiceReviewerIsolationTest {

    @Mock private PreAuthorizationRepository preAuthorizationRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ProviderContractPricingItemRepository pricingItemRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private ProviderContractService providerContractService;
    @Mock private PreAuthorizationAuditService auditService;
    @Mock private AuthorizationService authorizationService;
    @Mock private ProviderContextGuard providerContextGuard;
    @Mock private BenefitPolicyCoverageService benefitPolicyCoverageService;
    @Mock private ArchitecturalGuardService architecturalGuard;
    @Mock private ReviewerProviderIsolationService reviewerIsolationService;
    @Mock private PreAuthEmailNotificationService emailNotificationService;
    @Mock private EmailPreAuthService emailPreAuthService;

    @InjectMocks
    private PreAuthorizationService preAuthorizationService;

    private User reviewer;
    private User superAdmin;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        reviewer = User.builder().id(2L).username("reviewer").userType("MEDICAL_REVIEWER").build();
        superAdmin = User.builder().id(1L).username("admin").userType("SUPER_ADMIN").build();
        pageable = PageRequest.of(0, 20);
    }

    // ==================== getPendingInbox ====================

    @Test
    void getPendingInbox_reviewerAssignedToProviders_usesProviderScopedQuery() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(10L, 11L));
        when(preAuthorizationRepository.findByStatusInAndReviewerProviders(
                List.of(10L, 11L), List.of(PreAuthStatus.PENDING, PreAuthStatus.UNDER_REVIEW), pageable))
                .thenReturn(Page.empty(pageable));

        preAuthorizationService.getPendingInbox(pageable);

        verify(preAuthorizationRepository).findByStatusInAndReviewerProviders(
                List.of(10L, 11L), List.of(PreAuthStatus.PENDING, PreAuthStatus.UNDER_REVIEW), pageable);
        verify(preAuthorizationRepository, never()).findByStatusIn(anyList(), any());
    }

    @Test
    void getPendingInbox_reviewerWithNoAssignedProviders_returnsEmptyWithoutQuerying() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of());

        Page<?> result = preAuthorizationService.getPendingInbox(pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(preAuthorizationRepository, never()).findByStatusInAndReviewerProviders(anyList(), anyList(), any());
        verify(preAuthorizationRepository, never()).findByStatusIn(anyList(), any());
    }

    @Test
    void getPendingInbox_superAdmin_seesAllProviders_notScoped() {
        when(authorizationService.getCurrentUser()).thenReturn(superAdmin);
        when(reviewerIsolationService.isSubjectToIsolation(superAdmin)).thenReturn(false);
        when(preAuthorizationRepository.findByStatusIn(
                List.of(PreAuthStatus.PENDING, PreAuthStatus.UNDER_REVIEW), pageable))
                .thenReturn(Page.empty(pageable));

        preAuthorizationService.getPendingInbox(pageable);

        verify(preAuthorizationRepository).findByStatusIn(
                List.of(PreAuthStatus.PENDING, PreAuthStatus.UNDER_REVIEW), pageable);
        verify(preAuthorizationRepository, never()).findByStatusInAndReviewerProviders(anyList(), anyList(), any());
    }

    // ==================== startReview ====================

    private PreAuthorization pendingPreAuthForProvider(Long providerId) {
        return PreAuthorization.builder()
                .id(100L)
                .providerId(providerId)
                .memberId(50L)
                .active(true)
                .status(PreAuthStatus.PENDING)
                .priority(Priority.NORMAL)
                .build();
    }

    @Test
    void startReview_reviewerAssignedToProvider_succeeds() {
        PreAuthorization preAuth = pendingPreAuthForProvider(60L);
        when(preAuthorizationRepository.findById(100L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.isReviewer(reviewer)).thenReturn(true);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(preAuthorizationRepository.save(any(PreAuthorization.class))).thenAnswer(inv -> inv.getArgument(0));

        preAuthorizationService.startReview(100L, "reviewer");

        assertThat(preAuth.getStatus()).isEqualTo(PreAuthStatus.UNDER_REVIEW);
        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        verify(preAuthorizationRepository).save(preAuth);
    }

    @Test
    void startReview_reviewerNotAssignedToProvider_throwsAccessDenied_beforeStatusChange() {
        PreAuthorization preAuth = pendingPreAuthForProvider(60L);
        when(preAuthorizationRepository.findById(100L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.isReviewer(reviewer)).thenReturn(true);
        doThrow(new AccessDeniedException("لا يملك المراجع صلاحية الوصول لمقدم الخدمة هذا"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);

        assertThatThrownBy(() -> preAuthorizationService.startReview(100L, "reviewer"))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(preAuth.getStatus()).isEqualTo(PreAuthStatus.PENDING);
        verify(preAuthorizationRepository, never()).save(any());
    }

    @Test
    void startReview_superAdmin_bypassesIsolation_succeeds() {
        PreAuthorization preAuth = pendingPreAuthForProvider(60L);
        when(preAuthorizationRepository.findById(100L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.getCurrentUser()).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);
        when(preAuthorizationRepository.save(any(PreAuthorization.class))).thenAnswer(inv -> inv.getArgument(0));

        preAuthorizationService.startReview(100L, "admin");

        assertThat(preAuth.getStatus()).isEqualTo(PreAuthStatus.UNDER_REVIEW);
        verify(reviewerIsolationService, never()).validateReviewerAccess(any(), any());
        verify(preAuthorizationRepository).save(preAuth);
    }
}
