package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.common.service.ArchitecturalGuardService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDecisionDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.Priority;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineReviewDecision;
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

    // ==================== getAllPreAuthorizations ====================
    // WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1: this endpoint had zero
    // reviewer-provider scoping before this ticket — an isolated reviewer
    // could see every pre-authorization in the system via GET /.

    @Test
    void getAllPreAuthorizations_reviewerAssignedToProviders_usesProviderScopedQuery() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(10L, 11L));
        when(preAuthorizationRepository.findByProviderIdInAndActiveTrue(List.of(10L, 11L), pageable))
                .thenReturn(Page.empty(pageable));

        preAuthorizationService.getAllPreAuthorizations(pageable);

        verify(preAuthorizationRepository).findByProviderIdInAndActiveTrue(List.of(10L, 11L), pageable);
        verify(preAuthorizationRepository, never()).findByActiveTrue(any());
    }

    @Test
    void getAllPreAuthorizations_reviewerWithNoAssignedProviders_returnsEmptyWithoutQuerying() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of());

        Page<?> result = preAuthorizationService.getAllPreAuthorizations(pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(preAuthorizationRepository, never()).findByProviderIdInAndActiveTrue(anyList(), any());
        verify(preAuthorizationRepository, never()).findByActiveTrue(any());
    }

    @Test
    void getAllPreAuthorizations_superAdmin_seesAllProviders_notScoped() {
        when(authorizationService.getCurrentUser()).thenReturn(superAdmin);
        when(reviewerIsolationService.isSubjectToIsolation(superAdmin)).thenReturn(false);
        when(preAuthorizationRepository.findByActiveTrue(pageable)).thenReturn(Page.empty(pageable));

        preAuthorizationService.getAllPreAuthorizations(pageable);

        verify(preAuthorizationRepository).findByActiveTrue(pageable);
        verify(preAuthorizationRepository, never()).findByProviderIdInAndActiveTrue(anyList(), any());
    }

    // ==================== getPreAuthorizationsByProvider ====================
    // WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1: this endpoint accepted any
    // providerId with no validation — an isolated reviewer could pass an
    // unassigned providerId directly via GET /provider/{providerId}.

    @Test
    void getPreAuthorizationsByProvider_reviewerNotAssigned_throwsAccessDenied() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("لا يملك المراجع صلاحية الوصول لمقدم الخدمة هذا"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 99L);

        assertThatThrownBy(() -> preAuthorizationService.getPreAuthorizationsByProvider(99L, pageable))
                .isInstanceOf(AccessDeniedException.class);

        verify(preAuthorizationRepository, never()).findByProviderIdAndActiveTrue(any(Long.class), any());
    }

    @Test
    void getPreAuthorizationsByProvider_reviewerAssigned_succeeds() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 10L);
        when(preAuthorizationRepository.findByProviderIdAndActiveTrue(10L, pageable)).thenReturn(Page.empty(pageable));

        preAuthorizationService.getPreAuthorizationsByProvider(10L, pageable);

        verify(preAuthorizationRepository).findByProviderIdAndActiveTrue(10L, pageable);
    }

    @Test
    void getPreAuthorizationsByProvider_superAdmin_bypassesIsolation() {
        when(authorizationService.getCurrentUser()).thenReturn(superAdmin);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(superAdmin, 10L);
        when(preAuthorizationRepository.findByProviderIdAndActiveTrue(10L, pageable)).thenReturn(Page.empty(pageable));

        preAuthorizationService.getPreAuthorizationsByProvider(10L, pageable);

        verify(reviewerIsolationService).validateReviewerAccess(superAdmin, 10L);
        verify(preAuthorizationRepository).findByProviderIdAndActiveTrue(10L, pageable);
    }

    // ==================== getPreAuthorizationsByStatus ====================
    // WAAD-PREAUTH-REVIEWER-HISTORY-1: this endpoint had zero reviewer-provider
    // scoping — an isolated reviewer looking up the "processed" history view
    // (APPROVED/REJECTED) would have seen every provider's records.

    @Test
    void getPreAuthorizationsByStatus_reviewerAssignedToProviders_usesProviderScopedQuery() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(10L, 11L));
        when(preAuthorizationRepository.findByStatusInAndReviewerProviders(
                List.of(10L, 11L), List.of(PreAuthStatus.APPROVED), pageable))
                .thenReturn(Page.empty(pageable));

        preAuthorizationService.getPreAuthorizationsByStatus(PreAuthStatus.APPROVED, pageable);

        verify(preAuthorizationRepository).findByStatusInAndReviewerProviders(
                List.of(10L, 11L), List.of(PreAuthStatus.APPROVED), pageable);
        verify(preAuthorizationRepository, never()).findByStatusAndActiveTrue(any(), any());
    }

    @Test
    void getPreAuthorizationsByStatus_reviewerWithNoAssignedProviders_returnsEmptyWithoutQuerying() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of());

        Page<?> result = preAuthorizationService.getPreAuthorizationsByStatus(PreAuthStatus.REJECTED, pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(preAuthorizationRepository, never()).findByStatusInAndReviewerProviders(anyList(), anyList(), any());
        verify(preAuthorizationRepository, never()).findByStatusAndActiveTrue(any(), any());
    }

    @Test
    void getPreAuthorizationsByStatus_superAdmin_seesAllProviders_notScoped() {
        when(authorizationService.getCurrentUser()).thenReturn(superAdmin);
        when(reviewerIsolationService.isSubjectToIsolation(superAdmin)).thenReturn(false);
        when(preAuthorizationRepository.findByStatusAndActiveTrue(PreAuthStatus.APPROVED, pageable))
                .thenReturn(Page.empty(pageable));

        preAuthorizationService.getPreAuthorizationsByStatus(PreAuthStatus.APPROVED, pageable);

        verify(preAuthorizationRepository).findByStatusAndActiveTrue(PreAuthStatus.APPROVED, pageable);
        verify(preAuthorizationRepository, never()).findByStatusInAndReviewerProviders(anyList(), anyList(), any());
    }

    // ==================== search ====================
    // WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1: search had zero reviewer
    // isolation — an isolated reviewer's search results included every
    // provider's pre-authorizations.

    @Test
    void search_reviewerAssignedToProviders_usesProviderScopedSearchQuery() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(10L, 11L));
        when(preAuthorizationRepository.searchByProviderIds("abc", List.of(10L, 11L), pageable))
                .thenReturn(Page.empty(pageable));

        preAuthorizationService.search("abc", pageable);

        verify(preAuthorizationRepository).searchByProviderIds("abc", List.of(10L, 11L), pageable);
        verify(preAuthorizationRepository, never()).search(any(String.class), any());
    }

    @Test
    void search_reviewerWithNoAssignedProviders_returnsEmptyWithoutQuerying() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of());

        Page<?> result = preAuthorizationService.search("abc", pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(preAuthorizationRepository, never()).searchByProviderIds(any(), anyList(), any());
        verify(preAuthorizationRepository, never()).search(any(String.class), any());
    }

    @Test
    void search_superAdmin_seesAllProviders_notScoped() {
        when(authorizationService.getCurrentUser()).thenReturn(superAdmin);
        when(reviewerIsolationService.isSubjectToIsolation(superAdmin)).thenReturn(false);
        when(preAuthorizationRepository.search("abc", pageable)).thenReturn(Page.empty(pageable));

        preAuthorizationService.search("abc", pageable);

        verify(preAuthorizationRepository).search("abc", pageable);
        verify(preAuthorizationRepository, never()).searchByProviderIds(any(), anyList(), any());
    }

    // ==================== submitLineDecision / finalizePreAuthorizationReview (WAAD-PREAUTH-MULTI-LINE-1, Phase 2) ====================
    // Reuses assertReviewerAccess()/reviewerIsolationService — the same
    // isolation mechanism already proven correct above for every other
    // decision method — applied here to the two new per-line methods.

    private PreAuthorization underReviewPreAuthWithOneLine(Long providerId) {
        PreAuthorizationLine line = PreAuthorizationLine.builder()
                .id(200L)
                .lineNumber(1)
                .serviceCode("SRV-X")
                .serviceCategoryId(1L)
                .contractPrice(java.math.BigDecimal.TEN)
                .build();
        PreAuthorization preAuth = PreAuthorization.builder()
                .id(100L)
                .providerId(providerId)
                .memberId(50L)
                .active(true)
                .status(PreAuthStatus.UNDER_REVIEW)
                .priority(Priority.NORMAL)
                .lines(new java.util.ArrayList<>(List.of(line)))
                .build();
        line.setPreAuthorization(preAuth);
        return preAuth;
    }

    @Test
    void submitLineDecision_reviewerAssignedToProvider_succeeds() {
        PreAuthorization preAuth = underReviewPreAuthWithOneLine(60L);
        when(preAuthorizationRepository.findById(100L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.isReviewer(reviewer)).thenReturn(true);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
        when(preAuthorizationRepository.save(any(PreAuthorization.class))).thenAnswer(inv -> inv.getArgument(0));

        preAuthorizationService.submitLineDecision(100L, 200L,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(), "reviewer");

        assertThat(preAuth.getLines().get(0).getReviewerDecision()).isEqualTo(LineReviewDecision.APPROVED);
        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);
    }

    @Test
    void submitLineDecision_reviewerNotAssignedToProvider_throwsAccessDenied_beforeLineMutated() {
        PreAuthorization preAuth = underReviewPreAuthWithOneLine(60L);
        when(preAuthorizationRepository.findById(100L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.isReviewer(reviewer)).thenReturn(true);
        doThrow(new AccessDeniedException("لا يملك المراجع صلاحية الوصول لمقدم الخدمة هذا"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);

        assertThatThrownBy(() -> preAuthorizationService.submitLineDecision(100L, 200L,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(), "reviewer"))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(preAuth.getLines().get(0).getReviewerDecision()).isNull();
        verify(preAuthorizationRepository, never()).save(any());
    }

    @Test
    void submitLineDecision_superAdmin_bypassesIsolation_succeeds() {
        PreAuthorization preAuth = underReviewPreAuthWithOneLine(60L);
        when(preAuthorizationRepository.findById(100L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.getCurrentUser()).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);
        when(preAuthorizationRepository.save(any(PreAuthorization.class))).thenAnswer(inv -> inv.getArgument(0));

        preAuthorizationService.submitLineDecision(100L, 200L,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.REJECTED).reason("no").build(),
                "admin");

        assertThat(preAuth.getLines().get(0).getReviewerDecision()).isEqualTo(LineReviewDecision.REJECTED);
        verify(reviewerIsolationService, never()).validateReviewerAccess(any(), any());
    }

    @Test
    void finalizePreAuthorizationReview_reviewerNotAssignedToProvider_throwsAccessDenied() {
        PreAuthorization preAuth = underReviewPreAuthWithOneLine(60L);
        when(preAuthorizationRepository.findById(100L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.isReviewer(reviewer)).thenReturn(true);
        doThrow(new AccessDeniedException("لا يملك المراجع صلاحية الوصول لمقدم الخدمة هذا"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 60L);

        assertThatThrownBy(() -> preAuthorizationService.finalizePreAuthorizationReview(100L, "reviewer"))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(preAuth.getStatus()).isEqualTo(PreAuthStatus.UNDER_REVIEW);
        verify(preAuthorizationRepository, never()).save(any());
    }
}
