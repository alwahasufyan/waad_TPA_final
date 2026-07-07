package com.waad.tba.modules.claim.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ╔═══════════════════════════════════════════════════════════════════════════╗
 * ║              CLAIM FINANCIAL SUMMARY DTO - SINGLE SOURCE OF TRUTH         ║
 * ║───────────────────────────────────────────────────────────────────────────║
 * ║ ALL financial aggregations MUST come from this DTO.                       ║
 * ║ Frontend is FORBIDDEN from calculating totals, sums, or counts.           ║
 * ║                                                                           ║
 * ║ FINANCIAL LAW:                                                            ║
 * ║  - Database SUM() is authoritative                                        ║
 * ║  - No JavaScript .reduce() on financial fields                            ║
 * ║  - Numbers must match exactly with SQL queries                            ║
 * ╚═══════════════════════════════════════════════════════════════════════════╝
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "ملخص مالي موحد للمطالبات - المصدر الوحيد للبيانات المالية")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaimFinancialSummaryDto {

    // ═══════════════════════════════════════════════════════════════════════════
    // DATE RANGE (for context)
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Schema(description = "تاريخ البداية")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fromDate;
    
    @Schema(description = "تاريخ النهاية")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate toDate;

    // ═══════════════════════════════════════════════════════════════════════════
    // COUNTS (AUTHORITATIVE - from COUNT(*) SQL)
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Schema(description = "إجمالي عدد المطالبات")
    private Long totalClaimsCount;
    
    @Schema(description = "عدد المطالبات المعلقة")
    private Long pendingClaimsCount;
    
    @Schema(description = "عدد المطالبات قيد المراجعة")
    private Long underReviewClaimsCount;
    
    @Schema(description = "عدد المطالبات الموافق عليها")
    private Long approvedClaimsCount;
    
    @Schema(description = "عدد المطالبات المرفوضة")
    private Long rejectedClaimsCount;
    
    @Schema(description = "عدد المطالبات المسددة")
    private Long settledClaimsCount;

    // ═══════════════════════════════════════════════════════════════════════════
    // AMOUNTS (AUTHORITATIVE - from COALESCE(SUM(), 0) SQL)
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Schema(description = "إجمالي المبالغ المطلوبة - SUM(requestedAmount)")
    private BigDecimal totalRequestedAmount;
    
    @Schema(description = "إجمالي المبالغ المعتمدة - SUM(approvedAmount) for APPROVED/SETTLED")
    private BigDecimal totalApprovedAmount;
    
    @Schema(description = "إجمالي تحمل المرضى - SUM(patientCoPay)")
    private BigDecimal totalPatientCoPay;
    
    @Schema(description = "إجمالي المستحق لمقدمي الخدمات - SUM(netProviderAmount)")
    private BigDecimal totalNetProviderAmount;
    
    @Schema(description = "إجمالي المبالغ المسددة فعلياً - SUM(netProviderAmount) for SETTLED only")
    private BigDecimal totalSettledAmount;
    
    @Schema(description = "المبالغ المعلقة للتسوية - totalApproved - totalSettled")
    private BigDecimal outstandingAmount;
    
    @Schema(description = "إجمالي الخصم (الفرق بين المطلوب والمعتمد)")
    private BigDecimal totalDifferenceAmount;

    // ═══════════════════════════════════════════════════════════════════════════
    // PROVIDER BREAKDOWN (if grouped)
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Schema(description = "ملخص حسب مقدمي الخدمات")
    private List<ProviderSummary> providerSummaries;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EMPLOYER BREAKDOWN (if grouped)
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Schema(description = "ملخص حسب جهات العمل")
    private List<EmployerSummary> employerSummaries;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS BREAKDOWN
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Schema(description = "ملخص حسب حالة المطالبات")
    private List<StatusSummary> statusSummaries;

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED DTOs
    // ═══════════════════════════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "ملخص مالي لمقدم خدمة")
    public static class ProviderSummary {
        private Long providerId;
        private String providerName;
        private Long claimsCount;
        private BigDecimal requestedAmount;
        private BigDecimal approvedAmount;
        private BigDecimal patientCoPay;
        private BigDecimal netProviderAmount;
        private BigDecimal settledAmount;
        private BigDecimal outstandingAmount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "ملخص مالي لجهة عمل")
    public static class EmployerSummary {
        private Long employerId;
        private String employerName;
        private Long claimsCount;
        private Long membersCount;
        private BigDecimal requestedAmount;
        private BigDecimal approvedAmount;
        private BigDecimal patientCoPay;
        private BigDecimal netProviderAmount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "ملخص حسب حالة المطالبات")
    public static class StatusSummary {
        private String status;
        private String statusArabic;
        private Long count;
        private BigDecimal totalAmount;
    }
}
