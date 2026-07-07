package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.ClaimBatch;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ClaimBatchResponse {
    private Long id;
    private String batchCode;
    private Long providerId;
    private Long employerId;
    private Integer year;
    private Integer month;
    private String monthLabel;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private String statusLabel;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    public static ClaimBatchResponse from(ClaimBatch batch) {
        if (batch == null) return null;
        
        return ClaimBatchResponse.builder()
            .id(batch.getId())
            .batchCode(batch.getBatchCode())
            .providerId(batch.getProviderId())
            .employerId(batch.getEmployerId())
            .year(batch.getBatchYear())
            .month(batch.getBatchMonth())
            .monthLabel(getMonthLabelAr(batch.getBatchMonth()))
            .periodStart(batch.getPeriodStart())
            .periodEnd(batch.getPeriodEnd())
            .status(batch.getStatus().name())
            .statusLabel(batch.getStatus().getArabicLabel())
            .createdAt(batch.getCreatedAt())
            .closedAt(batch.getClosedAt())
            .build();
    }

    private static String getMonthLabelAr(int month) {
        String[] months = {
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        };
        if (month >= 1 && month <= 12) return months[month - 1];
        return String.valueOf(month);
    }
}
