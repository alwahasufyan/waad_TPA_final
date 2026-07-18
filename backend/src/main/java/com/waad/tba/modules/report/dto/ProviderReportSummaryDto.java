package com.waad.tba.modules.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Backend-computed summary for the Providers report. Every figure is produced by
 * an aggregate query over the SAME filtered set as the rows (pagination aside),
 * so {@code totalProviders} always equals the filtered result count.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderReportSummaryDto {
    private long totalProviders;
    private long activeProviders;
    private long inactiveProviders;
    private long withActiveContracts;
    private long withoutActiveContracts;
    private long withActivePriceLists;
    private long withoutActivePriceLists;
    private long expiredContracts;
    private long expiringSoonContracts;
}
