package com.waad.tba.modules.claim.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Adjudication Report.
 * 
 * تقرير التدقيق المالي يوضح:
 * - المبالغ المطلوبة من كل مقدم خدمة
 * - المبالغ المستقطعة (تحمل المريض)
 * - المبالغ المستحقة للدفع
 * 
 * Used by: GET /api/reports/adjudication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjudicationReportDto {
    
    /**
     * فترة التقرير
     */
    private LocalDate fromDate;
    private LocalDate toDate;
    
    /**
     * ملخص إجمالي
     */
    private BigDecimal totalRequested;      // إجمالي المطلوب
    private BigDecimal totalPatientCoPay;   // إجمالي تحمل المرضى
    private BigDecimal totalNetPayable;     // إجمالي المستحق للدفع
    
    /**
     * إحصائيات المطالبات
     */
    private Long totalClaims;
    private Long approvedClaims;
    private Long rejectedClaims;
    private Long pendingClaims;
    private Long settledClaims;
    
    /**
     * تفاصيل حسب مقدم الخدمة
     */
    private List<ProviderSummary> providerSummaries;
    
    /**
     * تفاصيل المطالبات
     */
    private List<ClaimSummary> claimDetails;
    
    // ========== Nested Classes ==========
    
    /**
     * ملخص لكل مقدم خدمة
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderSummary {
        private String providerName;
        private Long claimCount;
        private BigDecimal totalRequested;
        private BigDecimal totalPatientCoPay;
        private BigDecimal netPayable;
    }
    
    /**
     * ملخص لكل مطالبة
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimSummary {
        private Long claimId;
        private String memberName;
        private String memberCivilId;
        private String providerName;
        private LocalDate visitDate;
        private String status;
        private BigDecimal requestedAmount;
        private BigDecimal patientCoPay;
        private BigDecimal netProviderAmount;
        private String reviewerComment;
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Get Arabic summary
     */
    public String getSummaryArabic() {
        return String.format(
            "📊 تقرير التدقيق المالي\n" +
            "═══════════════════════════════════\n" +
            "📅 الفترة: من %s إلى %s\n" +
            "📝 إجمالي المطالبات: %d\n" +
            "═══════════════════════════════════\n" +
            "💰 إجمالي المطلوب: %.2f د.ل\n" +
            "👤 إجمالي تحمل المرضى: %.2f د.ل\n" +
            "🏥 إجمالي المستحق للدفع: %.2f د.ل\n" +
            "═══════════════════════════════════",
            fromDate, toDate, totalClaims,
            totalRequested, totalPatientCoPay, totalNetPayable
        );
    }
}
