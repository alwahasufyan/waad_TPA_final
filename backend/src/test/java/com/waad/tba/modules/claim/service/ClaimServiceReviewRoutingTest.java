package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PROVIDER-PORTAL-REVIEW-ROUTING-1 — regression tests for provider claim
 * routing/visibility:
 * <ul>
 * <li>{@link ClaimService#submitClaim} only allows DRAFT/NEEDS_CORRECTION ->
 * SUBMITTED (a provider can never jump straight to APPROVED).</li>
 * <li>{@link ClaimService#listClaims} forwards the caller's status list
 * through to the reviewer-isolated repository query unchanged (the fix that
 * lets the reviewer inbox request exactly SUBMITTED/UNDER_REVIEW/
 * NEEDS_CORRECTION in one call instead of leaking every status including
 * DRAFT).</li>
 * <li>{@link ClaimService#getFinancialSummary} forwards the caller's status
 * list through to the repository unchanged (the fix that lets the batch/
 * monthly screen restrict itself to APPROVED/BATCHED/SETTLED).</li>
 * </ul>
 * Only the methods under test are exercised; every other ClaimService
 * dependency is irrelevant to them, so the service is constructed with nulls
 * elsewhere (same pattern as {@link ClaimServiceProviderStatusTest}).
 */
class ClaimServiceReviewRoutingTest {

    private final ClaimRepository claimRepository = mock(ClaimRepository.class);
    private final ClaimMapper claimMapper = mock(ClaimMapper.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final ClaimStateMachine claimStateMachine = mock(ClaimStateMachine.class);
    private final ClaimAuditService claimAuditService = mock(ClaimAuditService.class);
    private final ReviewerProviderIsolationService reviewerIsolationService = mock(ReviewerProviderIsolationService.class);

    private ClaimService newClaimService() {
        // 28 constructor args in current field-declaration order
        // (RequiredArgsConstructor); only the mocks above are exercised here.
        return new ClaimService(
                claimRepository, claimMapper, authorizationService, null, null, null, null, null, null,
                null, claimStateMachine, null, null, null, claimAuditService, null, null, null, null,
                null, reviewerIsolationService, null, null, null, null, null, null, null);
    }

    // ── submitClaim: DRAFT/NEEDS_CORRECTION -> SUBMITTED only ──────────────

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = { "DRAFT", "NEEDS_CORRECTION" })
    void submitClaim_fromAllowedStatus_transitionsToSubmitted(ClaimStatus startingStatus) {
        ClaimService service = newClaimService();
        Claim claim = Claim.builder().id(1L).status(startingStatus).build();
        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.of(claim));
        doAnswer(invocation -> {
            Claim c = invocation.getArgument(0);
            c.setStatus(ClaimStatus.SUBMITTED);
            return null;
        }).when(claimStateMachine).transition(any(Claim.class), eq(ClaimStatus.SUBMITTED), any());
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User submitter = mock(User.class);
        when(submitter.getUsername()).thenReturn("dar");
        when(authorizationService.getCurrentUser()).thenReturn(submitter);

        service.submitClaim(1L);

        verify(claimStateMachine).transition(eq(claim), eq(ClaimStatus.SUBMITTED), any());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        // PROVIDER-PORTAL-REVIEW-ROUTING-2: record who submitted the claim.
        assertThat(claim.getSubmittedBy()).isEqualTo("dar");
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = { "APPROVED", "SUBMITTED", "UNDER_REVIEW", "REJECTED", "SETTLED",
            "BATCHED", "APPROVAL_IN_PROGRESS" })
    void submitClaim_fromDisallowedStatus_isRejected_providerCannotSkipReview(ClaimStatus startingStatus) {
        ClaimService service = newClaimService();
        Claim claim = Claim.builder().id(2L).status(startingStatus).build();
        when(claimRepository.findById(2L)).thenReturn(java.util.Optional.of(claim));
        when(authorizationService.getCurrentUser()).thenReturn(mock(User.class));

        assertThatThrownBy(() -> service.submitClaim(2L))
                .isInstanceOf(BusinessRuleException.class);

        verify(claimStateMachine, never()).transition(any(), any(), any());
        verify(claimRepository, never()).save(any());
    }

    // ── listClaims: status list forwarded verbatim to the isolated query ───

    @Test
    void listClaims_reviewerIsolated_forwardsExactStatusListToRepository_neverLeaksDraftByDefault() {
        ClaimService service = newClaimService();
        User reviewer = mock(User.class);
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(authorizationService.isEmployerAdmin(reviewer)).thenReturn(false);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(1L));

        List<ClaimStatus> pendingReview = List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
                ClaimStatus.NEEDS_CORRECTION);
        when(claimRepository.searchPagedWithFiltersAndReviewerProviders(
                anyString(), eq(List.of(1L)), any(), any(), eq(pendingReview), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.listClaims(null, null, pendingReview, null, null, null, null, 0, 20, "createdAt", "desc", null);

        verify(claimRepository).searchPagedWithFiltersAndReviewerProviders(
                anyString(), eq(List.of(1L)), any(), any(), eq(pendingReview), any(), any(), any(), any(), any(), any());
    }

    // ── listClaims: excludeChannel forwarded verbatim (batch-entry claims-list fix) ─

    @Test
    void listClaims_withExcludeChannel_forwardsItToRepository_hidesProviderPortalClaimsFromBatchEntry() {
        ClaimService service = newClaimService();
        User admin = mock(User.class);
        when(authorizationService.getCurrentUser()).thenReturn(admin);
        when(authorizationService.isEmployerAdmin(admin)).thenReturn(false);
        when(reviewerIsolationService.isSubjectToIsolation(admin)).thenReturn(false);

        when(claimRepository.searchPagedWithFilters(
                anyString(), any(), any(), any(),
                eq(com.waad.tba.modules.claim.entity.SubmissionChannel.PROVIDER_PORTAL),
                any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.listClaims(1L, 1L, null, com.waad.tba.modules.claim.entity.SubmissionChannel.PROVIDER_PORTAL,
                null, null, null, null, 0, 20, "createdAt", "desc", null);

        verify(claimRepository).searchPagedWithFilters(
                anyString(), any(), any(), any(),
                eq(com.waad.tba.modules.claim.entity.SubmissionChannel.PROVIDER_PORTAL),
                any(), any(), any(), any(), any());
    }

    // ── getFinancialSummary: status list forwarded verbatim (batch/monthly fix) ─

    @Test
    void getFinancialSummary_forwardsExactStatusListToRepository_excludesNonFinancialStatusesWhenAsked() {
        ClaimService service = newClaimService();
        User admin = mock(User.class);
        when(authorizationService.getCurrentUser()).thenReturn(admin);
        when(authorizationService.isProvider(admin)).thenReturn(false);
        when(authorizationService.isEmployerAdmin(admin)).thenReturn(false);
        when(reviewerIsolationService.isSubjectToIsolation(admin)).thenReturn(false);

        List<ClaimStatus> financialOnly = List.of(ClaimStatus.APPROVED, ClaimStatus.BATCHED, ClaimStatus.SETTLED);
        when(claimRepository.getFinancialSummary(eq(5L), eq(1L), eq(financialOnly), any(), any()))
                .thenReturn(List.of());

        service.getFinancialSummary(5L, 1L, financialOnly, null, null);

        verify(claimRepository).getFinancialSummary(eq(5L), eq(1L), eq(financialOnly), any(), any());
    }
}
