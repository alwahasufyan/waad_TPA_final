package com.waad.tba.modules.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Provider Claim Submission Request.
 * 
 * Used by healthcare providers to submit claims through Provider Portal.
 * 
 * Supports:
 * - Cash Claims (reimbursement)
 * - Direct Billing Claims
 * - All service types (Outpatient, Inpatient, Emergency)
 * 
 * @since Phase 1 - Provider Portal - Claims Submission
 */
@Schema(description = "Provider claim submission request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("deprecation")
public class ProviderClaimRequest {
    
    // ==================== VISIT-CENTRIC ARCHITECTURE (2026-01-15) ====================
    
    /**
     * Visit ID - REQUIRED for Visit-Centric Architecture.
     * Claims MUST be created from an existing Visit.
     * This enforces the flow: Eligibility → Visit → Claim
     */
    @NotNull(message = "Visit ID is required - Claims must be linked to a Visit")
    @Schema(description = "Visit ID (claim must be linked to a visit)", example = "1", required = true)
    private Long visitId;
    
    // ==================== MEMBER INFORMATION ====================
    
    /**
     * Member ID - Who is receiving the service.
     * Can be Principal or Dependent.
     */
    @NotNull(message = "Member ID is required")
    @Schema(description = "Member ID (principal or dependent)", example = "123", required = true)
    private Long memberId;
    
    // ==================== CLAIM TYPE ====================
    
    /**
     * Claim Type: CASH or DIRECT_BILLING
     */
    @NotNull(message = "Claim type is required")
    @Schema(description = "Claim type", example = "DIRECT_BILLING", required = true, allowableValues = {"CASH", "DIRECT_BILLING"})
    private String claimType;
    
    /**
     * Service Type: OUTPATIENT, INPATIENT, EMERGENCY
     */
    @NotNull(message = "Service type is required")
    @Schema(description = "Service type", example = "OUTPATIENT", required = true, allowableValues = {"OUTPATIENT", "INPATIENT", "EMERGENCY"})
    private String serviceType;
    
    // ==================== SERVICE DETAILS ====================
    
    /**
     * Service Date - When service was provided.
     */
    @NotNull(message = "Service date is required")
    @Schema(description = "Service date", example = "2026-01-11", required = true)
    private LocalDate serviceDate;
    
    /**
     * Service Category ID - من BenefitPolicyRule.
     * Example: Consultation, Laboratory, Radiology, etc.
     */
    @Schema(description = "Service category ID (from BenefitPolicyRule)", example = "1")
    private Long serviceCategoryId;
    
    /**
     * Service Name - Free text description.
     */
    @NotBlank(message = "Service name is required")
    @Size(max = 200, message = "Service name must not exceed 200 characters")
    @Schema(description = "Service name/description", example = "General Consultation", required = true)
    private String serviceName;
    
    /**
     * Diagnosis - ICD code or description (LEGACY - use diagnosisCode/diagnosisDescription).
     */
    @Schema(description = "Diagnosis (ICD code or description)", example = "J00 - Acute nasopharyngitis (common cold)")
    @Size(max = 500, message = "Diagnosis must not exceed 500 characters")
    private String diagnosis;
    
    /**
     * CANONICAL: Diagnosis Code (ICD-10).
     */
    @Schema(description = "Diagnosis code (ICD-10)", example = "J00")
    private String diagnosisCode;
    
    /**
     * CANONICAL: Diagnosis Description.
     */
    @Schema(description = "Diagnosis description", example = "Acute nasopharyngitis (common cold)")
    private String diagnosisDescription;
    
    // ==================== CLAIM LINES (CANONICAL) ====================
    
    /**
     * CANONICAL: Claim Lines with medicalServiceId.
     * Each line must reference a MedicalService - NO free-text services.
     */
    @Schema(description = "Claim lines with medicalServiceId (required)")
    private java.util.List<com.waad.tba.modules.claim.dto.ClaimLineDto> lines;
    
    /**
     * CANONICAL: Pre-Authorization ID (if applicable).
     */
    @Schema(description = "Pre-authorization ID (if applicable)", example = "123")
    private Long preAuthorizationId;
    
    // ==================== FINANCIAL INFORMATION ====================
    
    /**
     * Claimed Amount - Total amount claimed by provider.
     */
    @NotNull(message = "Claimed amount is required")
    @DecimalMin(value = "0.01", message = "Claimed amount must be greater than 0")
    @Schema(description = "Claimed amount (LYD)", example = "150.00", required = true)
    private BigDecimal claimedAmount;
    
    /**
     * Provider Notes - Additional information.
     */
    @Schema(description = "Provider notes", example = "Patient presented with fever and cough")
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    // ==================== PROVIDER INFORMATION ====================
    
    /**
     * Provider ID - من Providers table (optional if authenticated).
     */
    @Schema(description = "Provider ID (optional if auto-detected from auth)", example = "5")
    private Long providerId;
    
    /**
     * Provider Name - Free text if providerId not set.
     */
    @Schema(description = "Provider name (free text)", example = "عيادة النور الطبية")
    private String providerName;
    
    // ==================== ATTACHMENTS ====================
    
    /**
     * Number of attachments to upload.
     * Actual files sent via multipart/form-data.
     */
    @Schema(description = "Number of attachments", example = "2")
    private Integer attachmentCount;
    
    /**
     * Attachment descriptions.
     */
    @Schema(description = "Attachment descriptions", example = "[\"Medical Report\", \"Lab Results\"]")
    private String[] attachmentDescriptions;
    
    // ==================== PRE-APPROVAL ====================
    
    /**
     * Pre-Approval Reference (if exists).
     */
    @Schema(description = "Pre-approval reference number (if applicable)", example = "PA-2026-001")
    private String preApprovalReference;
    
    // ==================== VALIDATION ====================
    
    /**
     * Validate claim type.
     */
    public boolean isValidClaimType() {
        return "CASH".equals(claimType) || "DIRECT_BILLING".equals(claimType);
    }
    
    /**
     * Validate service type.
     */
    public boolean isValidServiceType() {
        return "OUTPATIENT".equals(serviceType) || 
               "INPATIENT".equals(serviceType) || 
               "EMERGENCY".equals(serviceType);
    }
}

