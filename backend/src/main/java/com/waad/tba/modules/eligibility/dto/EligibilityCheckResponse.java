package com.waad.tba.modules.eligibility.dto;

import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Eligibility Check Response DTO
 * Phase E1 - Eligibility Engine
 * 
 * Output from eligibility verification.
 * Designed for API consumers (Claims, Pre-Auth, Provider Portal, Mobile).
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityCheckResponse {

    /**
     * Unique request identifier for tracking
     */
    private String requestId;

    /**
     * Primary eligibility flag
     */
    private boolean eligible;

    /**
     * Detailed status
     */
    private String status;

    /**
     * List of reasons (failures or warnings)
     */
    private List<ReasonDto> reasons;

    /**
     * Snapshot of entities at check time
     */
    private SnapshotDto snapshot;

    /**
     * Processing metrics
     */
    private MetricsDto metrics;

    // ================================================
    // Nested DTOs
    // ================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonDto {
        private String code;
        private String messageAr;
        private String details;

        public static ReasonDto from(EligibilityResult.ReasonDetail detail) {
            return ReasonDto.builder()
                    .code(detail.getCode())
                    .messageAr(detail.getMessageAr())
                    .details(detail.getDetails())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotDto {
        // Member
        private Long memberId;
        private String memberName;
        private String memberCivilId;
        private String memberStatus;
        private String memberCardNumber;
        
        // Policy
        private Long policyId;
        private String policyNumber;
        private String policyStatus;
        private LocalDate coverageStart;
        private LocalDate coverageEnd;
        private String productName;
        
        // Employer
        private Long employerId;
        private String employerName;
        
        // Provider
        private Long providerId;
        private String providerName;
        
        // Service
        private LocalDate serviceDate;
        private String serviceCode;
        
        // CANONICAL: Category/Service specific coverage info
        private Long medicalCategoryId;
        private String medicalCategoryName;
        private Long medicalServiceId;
        private String medicalServiceName;
        private Integer coveragePercent;  // From BenefitPolicyRule
        private Integer patientCopayPercent;  // From BenefitPolicyRule
        private Boolean requiresPreApproval;  // From BenefitPolicyRule
        
        // Financial Usage Data
        private Double benefitLimit;
        private Double usedAmount;
        private Double remainingAmount;
        private Long matchingCategoryId;

        public static SnapshotDto from(EligibilityResult.EligibilitySnapshot snapshot) {
            if (snapshot == null) return null;
            return SnapshotDto.builder()
                    .memberId(snapshot.getMemberId())
                    .memberName(snapshot.getMemberName())
                    .memberCivilId(snapshot.getMemberCivilId())
                    .memberStatus(snapshot.getMemberStatus())
                    .memberCardNumber(snapshot.getMemberCardNumber())
                    .policyId(snapshot.getPolicyId())
                    .policyNumber(snapshot.getPolicyNumber())
                    .policyStatus(snapshot.getPolicyStatus())
                    .coverageStart(snapshot.getCoverageStart())
                    .coverageEnd(snapshot.getCoverageEnd())
                    .productName(snapshot.getProductName())
                    .employerId(snapshot.getEmployerId())
                    .employerName(snapshot.getEmployerName())
                    .providerId(snapshot.getProviderId())
                    .providerName(snapshot.getProviderName())
                    .serviceDate(snapshot.getServiceDate())
                    .serviceCode(snapshot.getServiceCode())
                    // CANONICAL: Category/Service coverage
                    .medicalCategoryId(snapshot.getMedicalCategoryId())
                    .medicalCategoryName(snapshot.getMedicalCategoryName())
                    .medicalServiceId(snapshot.getMedicalServiceId())
                    .medicalServiceName(snapshot.getMedicalServiceName())
                    .coveragePercent(snapshot.getCoveragePercent())
                    .patientCopayPercent(snapshot.getPatientCopayPercent())
                    .requiresPreApproval(snapshot.getRequiresPreApproval())
                    .benefitLimit(snapshot.getBenefitLimit())
                    .usedAmount(snapshot.getUsedAmount())
                    .remainingAmount(snapshot.getRemainingAmount())
                    .matchingCategoryId(snapshot.getMatchingCategoryId())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricsDto {
        private long processingTimeMs;
        private int rulesEvaluated;

        public static MetricsDto from(long processingTimeMs, int rulesEvaluated) {
            return MetricsDto.builder()
                    .processingTimeMs(processingTimeMs)
                    .rulesEvaluated(rulesEvaluated)
                    .build();
        }
    }

    // ================================================
    // Factory Methods
    // ================================================

    /**
     * Create response from EligibilityResult
     */
    public static EligibilityCheckResponse from(EligibilityResult result) {
        return EligibilityCheckResponse.builder()
                .requestId(result.getRequestId())
                .eligible(result.isEligible())
                .status(result.getStatus() != null ? result.getStatus().name() : null)
                .reasons(result.getReasons() != null ? 
                        result.getReasons().stream()
                                .map(ReasonDto::from)
                                .toList() : 
                        List.of())
                .snapshot(SnapshotDto.from(result.getSnapshot()))
                .metrics(MetricsDto.from(result.getProcessingTimeMs(), result.getRulesEvaluated()))
                .build();
    }
}
