package com.waad.tba.modules.preauthorization.api.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * API Request: Update Pre-Authorization Data
 * 
 * SECURITY: For PROVIDER and EMPLOYER_ADMIN only.
 * Allowed ONLY when status is PENDING or NEEDS_CORRECTION.
 * 
 * @since Provider Portal Security Fix (Phase 3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreAuthDataRequest {
    
    private LocalDate expectedServiceDate;
    
    @Size(max = 2000, message = "Clinical justification must not exceed 2000 characters")
    private String clinicalJustification;
    
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    private String priority;
}
