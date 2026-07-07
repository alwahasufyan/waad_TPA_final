package com.waad.tba.modules.medicaltaxonomy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for Medical Service responses.
 * 
 * NOTE: requiresPA is DEPRECATED (set from entity for backward compatibility)
 * NOTE: requiresPreApproval is CANONICAL (computed from Member's BenefitPolicy)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalServiceResponseDto {

    private Long id;
    private String code;
    private String name;
    private Long categoryId;
    private String categoryName; // For UX - display category name
    private String categoryCode; // For reference
    private String description;
    private BigDecimal basePrice;
    
    /**
     * @deprecated Use requiresPreApproval instead
     * This is the entity-level flag (not policy-aware)
     */
    @Deprecated
    private boolean requiresPA;
    
    /**
     * CANONICAL: Does this service require pre-approval for the given member?
     * Computed from BenefitPolicyRule.requiresPreApproval (if member context exists)
     * Falls back to entity.requiresPA if no member context
     */
    private Boolean requiresPreApproval;
    
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
