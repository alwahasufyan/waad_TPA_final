package com.waad.tba.modules.providercontract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for passing contract context to Excel template generation
 * 
 * ARCHITECTURAL RULE: 
 * Never pass JPA Entities to template/file generation services.
 * Always use DTOs to avoid LazyInitializationException.
 * 
 * @since 2026-01-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractTemplateContext {
    
    /**
     * Contract ID
     */
    private Long contractId;
    
    /**
     * Contract code (e.g., "CNT-2026-001")
     */
    private String contractCode;
    
    /**
     * Provider name (unified Arabic name)
     */
    private String providerName;
    
    /**
     * Provider name in English (optional)
     */
    private String providerNameEn;
    
    /**
     * Contract status (for validation)
     */
    private String contractStatus;
    
    /**
     * Get formatted context display for Excel template header
     * 
     * @return Formatted string with contract info
     */
    public String getContextDisplay() {
        return String.format("📋 معلومات العقد | Contract Info: %s - %s", 
            contractCode != null ? contractCode : "N/A",
            providerName != null ? providerName : "N/A"
        );
    }
}
