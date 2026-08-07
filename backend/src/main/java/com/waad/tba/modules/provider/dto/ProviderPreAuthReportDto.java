package com.waad.tba.modules.provider.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider Pre-Authorization Report DTO
 * For provider-specific pre-auth reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderPreAuthReportDto {
    
    private Long preAuthId;
    private Long visitId;
    private Long claimId;
    private String claimNumber;
    private String claimStatus;
    private String preAuthNumber;
    private LocalDate requestDate;
    private LocalDate validFrom;
    private LocalDate validTo;
    
    // Member info
    private Long memberId;
    private String memberName;
    private String memberBarcode;
    private String memberCardNumber;
    private String civilId;
    
    // Employer/Company
    private String employerName;

    // Provider context used when opening a linked claim
    private Long providerId;
    private String providerName;
    
    // Financial
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    
    // Status
    private String status;
    private String statusLabel;
    
    // Service details
    private String serviceName;
    private Integer sessionsRequested;
    private Integer sessionsApproved;
    private Integer sessionsUsed;
    
    // Medical justification
    private String medicalJustification;
    private String diagnosis;
    
    // Reviewer
    private String reviewerName;
    private String reviewerNotes;
    private LocalDate reviewDate;
    
    // Attachments
    private Integer attachmentsCount;

    /**
     * WAAD-PREAUTH-LINE-QUANTITY-FIX-1: full per-line breakdown, so the
     * provider-portal quick-view can show every requested service (not
     * just the header's "line 0" snapshot in serviceName/requestedAmount
     * above) along with each line's real quantity and decision.
     */
    private List<Line> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private String serviceName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal contractPrice;
        private BigDecimal approvedAmount;
        private String reviewerDecision;
        private String rejectionReason;
    }
}
