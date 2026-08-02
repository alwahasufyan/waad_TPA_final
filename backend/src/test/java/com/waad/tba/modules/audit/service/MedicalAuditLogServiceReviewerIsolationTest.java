package com.waad.tba.modules.audit.service;

import com.waad.tba.modules.audit.entity.AuditLog;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.audit.repository.MedicalAuditLogRepository;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WAAD-RBAC-REVIEWER-AUDIT-SCOPING-3: verifies searchClaimAuditLogs() scopes
 * an isolated MEDICAL_REVIEWER to their assigned providers' claims (without
 * a hard "claim ID required" block — reverted per explicit product
 * decision), while SUPER_ADMIN/WAAD_ADMIN keep unrestricted access.
 */
@ExtendWith(MockitoExtension.class)
class MedicalAuditLogServiceReviewerIsolationTest {

    @Mock private MedicalAuditLogRepository repository;
    @Mock private AuthorizationService authorizationService;
    @Mock private CorrelationIdProvider correlationIdProvider;
    @Mock private ClaimRepository claimRepository;
    @Mock private ReviewerProviderIsolationService reviewerIsolationService;

    @InjectMocks
    private MedicalAuditLogService service;

    private User reviewer;
    private User admin;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        reviewer = User.builder().id(2L).username("reviewer").userType("MEDICAL_REVIEWER").build();
        admin = User.builder().id(1L).username("admin").userType("SUPER_ADMIN").build();
        pageable = PageRequest.of(0, 20);
    }

    @Test
    void reviewer_noClaimId_scopedToAllowedProvidersClaims() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(1L));
        when(claimRepository.findIdsByProviderIdIn(List.of(1L))).thenReturn(List.of(100L, 101L));
        when(repository.findByEntityTypeAndEntityIdInOrderByTimestampDesc(
                EntityType.CLAIM, List.of("100", "101"), pageable))
                .thenReturn(Page.empty(pageable));

        service.searchClaimAuditLogs(null, null, pageable);

        verify(repository).findByEntityTypeAndEntityIdInOrderByTimestampDesc(
                EntityType.CLAIM, List.of("100", "101"), pageable);
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void reviewer_noAssignments_returnsEmptyWithoutQuerying() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of());

        Page<AuditLog> result = service.searchClaimAuditLogs(null, null, pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(repository, never()).findAll(any(Pageable.class));
        verify(repository, never()).findByEntityTypeAndEntityIdInOrderByTimestampDesc(any(), any(), any());
    }

    @Test
    void reviewer_claimIdForAssignedProvider_succeeds() {
        Claim claim = Claim.builder().id(50L).providerId(1L).build();
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(claimRepository.findById(50L)).thenReturn(Optional.of(claim));
        when(repository.findByEntityTypeAndEntityIdOrderByTimestampDesc(EntityType.CLAIM, "50", pageable))
                .thenReturn(Page.empty(pageable));

        service.searchClaimAuditLogs(50L, null, pageable);

        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);
        verify(repository).findByEntityTypeAndEntityIdOrderByTimestampDesc(EntityType.CLAIM, "50", pageable);
    }

    @Test
    void reviewer_claimIdForUnassignedProvider_throwsAccessDenied() {
        Claim claim = Claim.builder().id(60L).providerId(99L).build();
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(claimRepository.findById(60L)).thenReturn(Optional.of(claim));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 99L);

        assertThatThrownBy(() -> service.searchClaimAuditLogs(60L, null, pageable))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).findByEntityTypeAndEntityIdOrderByTimestampDesc(any(), anyString(), any());
    }

    @Test
    void admin_noClaimId_seesEverything_notScoped() {
        when(authorizationService.getCurrentUser()).thenReturn(admin);
        when(reviewerIsolationService.isSubjectToIsolation(admin)).thenReturn(false);
        when(repository.findAll(pageable)).thenReturn(Page.empty(pageable));

        service.searchClaimAuditLogs(null, null, pageable);

        verify(repository).findAll(pageable);
        verify(reviewerIsolationService, never()).getAllowedProviderIds(any());
    }
}
