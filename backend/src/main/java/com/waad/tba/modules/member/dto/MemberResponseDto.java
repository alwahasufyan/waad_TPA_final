package com.waad.tba.modules.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Member Response DTO
 * 
 * Standardized response for member data.
 * Uses nationalNumber (NOT civilId) per API contract.
 * 
 * @version 2026.1 - Breaking change: civilId removed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponseDto {
    private Long id;
    private Long employerId;
    private String employerName;
    private String fullName;
    private String nationalNumber;
    private String policyNumber;
    
    /**
     * BenefitPolicy ID for member's coverage association.
     * Used by reports to link members to their benefit policies.
     */
    private Long benefitPolicyId;
    
    /**
     * BenefitPolicy code for display purposes.
     * Example: "BP-001"
     */
    private String benefitPolicyCode;
    
    /**
     * BenefitPolicy name for display purposes.
     */
    private String benefitPolicyName;
    
    private LocalDate birthDate;
    private String gender;
    private String phone;
    private String email;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
