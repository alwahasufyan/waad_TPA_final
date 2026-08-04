package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.modules.preauthorization.dto.PreAuthDashboardDto.OverallStats;
import com.waad.tba.modules.preauthorization.dto.PreAuthDashboardDto.PreAuthSummaryDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.Priority;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationAuditRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * WAAD-PREAUTH-DASHBOARD-PENDING-COUNT-1: a request stops being PENDING the
 * moment it's actually submitted for review (submit transitions it to
 * UNDER_REVIEW — see PreAuthorizationService.submitPreAuthorization()), so
 * the dashboard's "pending review" counter must count both statuses, not
 * PENDING alone, to match what the reviewer inbox itself considers pending
 * (PreAuthorizationService.getPendingInbox()).
 */
@ExtendWith(MockitoExtension.class)
class PreAuthDashboardServiceTest {

    @Mock private PreAuthorizationRepository preAuthRepository;
    @Mock private PreAuthorizationAuditRepository auditRepository;
    @Mock private ProviderRepository providerRepository;

    @InjectMocks
    private PreAuthDashboardService dashboardService;

    @Test
    void getOverallStats_pendingCount_includesBothPendingAndUnderReview() {
        when(preAuthRepository.getActiveSummary()).thenReturn(
                List.<Object[]>of(new Object[] { 10L, BigDecimal.valueOf(1000), BigDecimal.valueOf(500) }));
        when(preAuthRepository.countByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { PreAuthStatus.PENDING, 3L },
                new Object[] { PreAuthStatus.UNDER_REVIEW, 4L },
                new Object[] { PreAuthStatus.APPROVED, 2L },
                new Object[] { PreAuthStatus.REJECTED, 1L }));

        OverallStats stats = dashboardService.getOverallStats();

        assertThat(stats.getPendingCount()).isEqualTo(7L);
        assertThat(stats.getApprovedCount()).isEqualTo(2L);
        assertThat(stats.getRejectedCount()).isEqualTo(1L);
    }

    @Test
    void getOverallStats_noUnderReviewRows_pendingCountIsJustPending() {
        when(preAuthRepository.getActiveSummary()).thenReturn(
                List.<Object[]>of(new Object[] { 3L, BigDecimal.valueOf(300), BigDecimal.ZERO }));
        when(preAuthRepository.countByStatus()).thenReturn(List.<Object[]>of(
                new Object[] { PreAuthStatus.PENDING, 3L }));

        OverallStats stats = dashboardService.getOverallStats();

        assertThat(stats.getPendingCount()).isEqualTo(3L);
    }

    private PreAuthorization preAuthWithLines(int lineCount) {
        PreAuthorization.PreAuthorizationBuilder builder = PreAuthorization.builder()
                .id(1L)
                .referenceNumber("PA-000001")
                .memberId(10L)
                .providerId(20L)
                .serviceCode("SRV-A")
                .status(PreAuthStatus.PENDING)
                .priority(Priority.NORMAL);
        java.util.List<PreAuthorizationLine> lines = new java.util.ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            lines.add(PreAuthorizationLine.builder().id((long) (i + 1)).lineNumber(i + 1)
                    .serviceCode("SRV-" + i).serviceCategoryId(1L).contractPrice(BigDecimal.TEN).build());
        }
        return builder.lines(lines).build();
    }

    /**
     * WAAD-PREAUTH-MULTI-LINE-1 (Phase 3): toSummaryDto (used by both
     * getHighPriorityQueue and getExpiringSoon) must show "N services" for a
     * multi-line record, unchanged single-code display for the legacy
     * single-line case.
     */
    @Test
    void getHighPriorityQueue_multiLineRecord_showsServiceCount() {
        when(preAuthRepository.findHighPriorityPending()).thenReturn(List.of(preAuthWithLines(2)));

        List<PreAuthSummaryDto> queue = dashboardService.getHighPriorityQueue(10);

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).getServiceName()).isEqualTo("2 خدمات (SRV-A +1)");
    }

    @Test
    void getHighPriorityQueue_singleLineRecord_showsServiceCodeUnchanged() {
        when(preAuthRepository.findHighPriorityPending()).thenReturn(List.of(preAuthWithLines(1)));

        List<PreAuthSummaryDto> queue = dashboardService.getHighPriorityQueue(10);

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).getServiceName()).isEqualTo("SRV-A");
    }
}
