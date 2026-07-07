package com.waad.tba.modules.provider.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for registering a visit from the Provider Portal.
 * 
 * NEW FLOW (2026-01-13):
 * 1. Provider performs eligibility check
 * 2. Selects eligible member
 * 3. Clicks "Register Visit" → this request is sent
 * 4. Visit is created and linked to the member
 * 5. Provider can then create Claim or Pre-Authorization from Visit Log
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderVisitRegisterRequest {
    
    /**
     * Member ID to register the visit for (from eligibility check)
     */
    @NotNull(message = "معرف العضو مطلوب")
    private Long memberId;
    
    /**
     * Provider ID (resolved from authenticated user or passed explicitly)
     */
    private Long providerId;
    
    /**
     * Eligibility check ID that verified this member (optional but recommended)
     */
    private Long eligibilityCheckId;
    
    /**
     * Visit date (defaults to today if not provided)
     */
    private LocalDate visitDate;
    
    /**
     * Visit type (OUTPATIENT, INPATIENT, EMERGENCY, etc.)
     * REQUIRED - Cannot register a visit without specifying the type
     */
    @NotNull(message = "نوع الزيارة مطلوب")
    private String visitType;
    
    /**
     * Doctor/physician name (optional)
     */
    private String doctorName;
    
    /**
     * Medical specialty (optional)
     */
    private String specialty;
    
    /**
     * Initial diagnosis (optional)
     */
    private String diagnosis;
    
    /**
     * Notes (optional)
     */
    private String notes;
}
