package com.waad.tba.modules.claim.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for settling a claim (marking it ready for payment).
 * 
 * Used by: POST /api/claims/{id}/settle
 * 
 * Business Rules:
 * - Claim must be in APPROVED status
 * - Payment reference must be provided
 * - Settlement will move claim to SETTLED status (terminal state)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimSettleDto {
    
    /**
     * Payment reference number from finance system.
     */
    @NotBlank(message = "رقم مرجع الدفع مطلوب")
    private String paymentReference;
    
    /**
     * Actual settlement amount (should match approved amount).
     */
    private BigDecimal settlementAmount;
    
    /**
     * Payment date.
     */
    private LocalDate paymentDate;
    
    /**
     * Bank transaction reference (optional).
     */
    private String bankReference;
    
    /**
     * Settlement notes.
     */
    private String notes;
}
