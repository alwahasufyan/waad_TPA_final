package com.waad.tba.modules.report.service;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.report.dto.CompanyProfitReportRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyProfitReportService {

    private final ClaimRepository claimRepository;

    @Transactional(readOnly = true)
    public List<CompanyProfitReportRowDto> getCompanyProfitReport(Long employerId, Integer year, Integer month, Long providerId) {
        if (year == null) {
            throw new IllegalArgumentException("Year is required for this report");
        }

        List<ClaimStatus> approvedStatuses = Arrays.asList(
                ClaimStatus.APPROVED,
                ClaimStatus.BATCHED,
                ClaimStatus.SETTLED
        );

        // WAAD-CLAIMS-FINANCIAL-CORRECTNESS-1 (Fix D): companyDueValue now comes
        // straight from ClaimRepository.getCompanyProfitReport()'s SUM of the
        // authoritative, always-fresh Claim.companyDiscountAmount (Fix B). The old
        // "recompute if <= 0" fallback here used to paper over stale/zero draft
        // values, but it re-derived a SECOND, independently-drifting estimate via
        // MAX(appliedDiscountPercent) across a whole group of claims — itself
        // inaccurate for any group spanning claims with different discount rates,
        // and incorrectly overriding a legitimately-zero (no-discount) result. With
        // companyDiscountAmount now reliable, this fallback is removed rather than
        // fixed, so there is exactly one source of truth for TPA revenue.
        return claimRepository.getCompanyProfitReport(employerId, year, month, providerId, approvedStatuses);
    }
}
