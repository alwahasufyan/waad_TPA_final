package com.waad.tba.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private Long totalMembers;
    private Long totalClaims;
    private Long pendingClaims;
    private Long approvedClaims;
    private Long rejectedClaims;
    private Long totalEmployers;
    private Long totalInsuranceCompanies;
}
