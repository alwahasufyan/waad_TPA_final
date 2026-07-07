package com.waad.tba.modules.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Provider Claim Submission Response.
 * 
 * Returned after claim submission with:
 * - Claim ID and reference number
 * - Submission status
 * - Annual limit validation
 * - Warnings or errors
 * 
 * @since Phase 1 - Provider Portal - Claims Submission
 */
@Schema(description = "Provider claim submission response")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderClaimResponse {
    
    // ==================== SUBMISSION STATUS ====================
    
    /**
     * Success indicator.
     */
    @Schema(description = "Submission success", example = "true")
    private Boolean success;
    
    /**
     * Status message.
     */
    @Schema(description = "Status message", example = "المطالبة قُدمت بنجاح - رقم المرجع: CLM-2026-001234")
    private String message;
    
    /**
     * Status Code for UI.
     * - SUCCESS: Submitted successfully
     * - WARNING: Submitted with warnings (e.g., approaching limit)
     * - ERROR: Submission failed
     * - REQUIRES_APPROVAL: Requires pre-authorization
     */
    @Schema(description = "Status code", example = "SUCCESS", allowableValues = {"SUCCESS", "WARNING", "ERROR", "REQUIRES_APPROVAL"})
    private String statusCode;
    
    // ==================== CLAIM INFORMATION ====================
    
    /**
     * Claim ID (database primary key).
     */
    @Schema(description = "Claim ID", example = "456")
    private Long claimId;
    
    /**
     * Claim Reference Number (user-friendly).
     */
    @Schema(description = "Claim reference number", example = "CLM-2026-001234")
    private String claimReferenceNumber;
    
    /**
     * Claim Status.
     */
    @Schema(description = "Claim status", example = "SUBMITTED")
    private String claimStatus;
    
    /**
     * Submission Timestamp.
     */
    @Schema(description = "Submission timestamp", example = "2026-01-11T14:30:00")
    private LocalDateTime submissionTimestamp;
    
    // ==================== MEMBER INFORMATION ====================
    
    /**
     * Member Full Name.
     */
    @Schema(description = "Member full name", example = "أحمد محمد علي")
    private String memberFullName;
    
    /**
     * Member Barcode.
     */
    @Schema(description = "Member barcode", example = "WAD-2026-00001234")
    private String memberBarcode;
    
    // ==================== FINANCIAL INFORMATION ====================
    
    /**
     * Claimed Amount.
     */
    @Schema(description = "Claimed amount (LYD)", example = "150.00")
    private BigDecimal claimedAmount;
    
    /**
     * Annual Limit Information.
     */
    @Schema(description = "Member annual limit (LYD)", example = "5000.00")
    private BigDecimal annualLimit;
    
    @Schema(description = "Used amount before this claim (LYD)", example = "1250.50")
    private BigDecimal usedAmountBefore;
    
    @Schema(description = "Used amount after this claim (LYD)", example = "1400.50")
    private BigDecimal usedAmountAfter;
    
    @Schema(description = "Remaining limit after this claim (LYD)", example = "3599.50")
    private BigDecimal remainingLimit;
    
    @Schema(description = "Usage percentage after this claim", example = "28.01")
    private Double usagePercentage;
    
    // ==================== WARNINGS & VALIDATION ====================
    
    /**
     * Warnings (if any).
     */
    @Schema(description = "Warning messages", example = "[\"⚠️ الحد السنوي وصل إلى 85% - متبقي: 750.00 د.ل\"]")
    private List<String> warnings;
    
    /**
     * Errors (if any).
     */
    @Schema(description = "Error messages", example = "[\"❌ المبلغ المطلوب يتجاوز الحد السنوي المتبقي\"]")
    private List<String> errors;
    
    /**
     * Exceeded Limit.
     */
    @Schema(description = "Whether claim exceeds annual limit", example = "false")
    private Boolean exceededLimit;
    
    /**
     * Requires Pre-Approval.
     */
    @Schema(description = "Whether claim requires pre-approval", example = "false")
    private Boolean requiresPreApproval;
    
    // ==================== SERVICE-LEVEL LIMITS ====================
    
    /**
     * Service-Level Limit Check.
     */
    @Schema(description = "Service-level limit information")
    private ServiceLimitInfo serviceLimitInfo;
    
    /**
     * Service Limit Information.
     */
    @Schema(description = "Service-level limit validation")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceLimitInfo {
        
        @Schema(description = "Service category name", example = "Consultation")
        private String serviceName;
        
        @Schema(description = "Service amount limit (LYD)", example = "100.00")
        private BigDecimal amountLimit;
        
        @Schema(description = "Times limit (per year)", example = "12")
        private Integer timesLimit;
        
        @Schema(description = "Times used so far", example = "5")
        private Integer timesUsed;
        
        @Schema(description = "Times remaining", example = "7")
        private Integer timesRemaining;
        
        @Schema(description = "Exceeds service limit", example = "false")
        private Boolean exceedsLimit;
    }
    
    // ==================== ATTACHMENTS ====================
    
    /**
     * Attachment Upload Status.
     */
    @Schema(description = "Number of attachments uploaded", example = "2")
    private Integer attachmentsUploaded;
    
    @Schema(description = "Attachment upload errors (if any)")
    private List<String> attachmentErrors;
    
    // ==================== NEXT STEPS ====================
    
    /**
     * Next Steps for Provider.
     */
    @Schema(description = "Next steps message", example = "المطالبة قيد المراجعة - سيتم الرد خلال 10 أيام عمل")
    private String nextSteps;
}
