package com.waad.tba.modules.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Stable response envelope for GET /api/v1/reports/providers:
 * paged rows + backend summary + the applied filters + generation timestamp.
 */
@Getter
@Builder
public class ProviderReportResponseDto {

    private RowsPage rows;
    private ProviderReportSummaryDto summary;
    private Map<String, Object> appliedFilters;
    private String generatedAt;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RowsPage {
        private List<ProviderReportRowDto> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
