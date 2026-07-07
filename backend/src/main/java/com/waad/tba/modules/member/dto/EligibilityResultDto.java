package com.waad.tba.modules.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Unified Eligibility Result DTO
 * Deterministic response for eligibility verification
 * Supports ONLY Card Number and Barcode search
 */
@Schema(description = "Deterministic eligibility check result - Card Number or Barcode only")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityResultDto {

    @Schema(description = "Member ID", example = "12345")
    private Long memberId;

    @Schema(description = "Full name (Arabic or English)", example = "أحمد محمد علي")
    private String fullName;

    @Schema(description = "Card number", example = "1234567890")
    private String cardNumber;

    @Schema(description = "Barcode identifier", example = "WAD-2026-00001234")
    private String barcode;
    
    // 🔒 CRITICAL HARDENING: Dependent identification
    @Schema(description = "Is this a dependent (family member) or primary member", example = "false")
    private Boolean dependent;
    
    @Schema(description = "Primary member ID (if this is a dependent)", example = "12340")
    private Long primaryMemberId;

    @Schema(description = "Member status", example = "ACTIVE", allowableValues = {"ACTIVE", "EXPIRED", "SUSPENDED", "BLOCKED", "TERMINATED"})
    private String memberStatus;

    @Schema(description = "Card status", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE", "BLOCKED", "EXPIRED"})
    private String cardStatus;

    @Schema(description = "Eligibility decision", example = "ELIGIBLE", allowableValues = {"ELIGIBLE", "NOT_ELIGIBLE"})
    private EligibilityDecision eligibilityDecision;

    @Schema(description = "Is member eligible for services", example = "true")
    private Boolean eligible;

    @Schema(description = "Employer organization name", example = "شركة الوعد للتأمين")
    private String employerName;

    @Schema(description = "Benefit policy name", example = "سياسة الموظفين الأساسية")
    private String policyName;

    @Schema(description = "Co-payment amount (percentage)", example = "10")
    private Integer copayAmount;

    @Schema(description = "Coverage limit", example = "50000.00")
    private BigDecimal coverageLimit;

    @Schema(description = "Policy start date", example = "2026-01-01")
    private LocalDate policyStartDate;

    @Schema(description = "Policy end date", example = "2026-12-31")
    private LocalDate policyEndDate;

    @Schema(description = "Reason for ineligibility (if not eligible)", example = "Card is blocked")
    private String ineligibilityReason;

    @Schema(description = "Additional status message", example = "العضوية نشطة - يمكن بدء زيارة")
    private String message;

    @Schema(description = "Input type detected", example = "CARD_NUMBER", allowableValues = {"CARD_NUMBER", "BARCODE"})
    private InputType inputType;

    /**
     * Eligibility decision enum
     */
    public enum EligibilityDecision {
        ELIGIBLE,
        NOT_ELIGIBLE
    }

    /**
     * Input type enum - detected from query pattern
     */
    public enum InputType {
        CARD_NUMBER,
        BARCODE
    }
}
