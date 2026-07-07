package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Allowed Employer (Partner) visible to a Provider
 * 
 * Used by Provider Portal to display list of partners they can access.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowedEmployerDto {
    
    /**
     * Employer/Partner ID
     * Special value: -1 indicates "Global Network" (all employers)
     */
    private Long id;
    
    /**
     * Employer/Partner name (Arabic)
     */
    private String name;
    
    /**
     * Employer/Partner name (English, if available)
     */
    private String nameEn;
    
    /**
     * Is this a "global" entry representing all employers?
     * true = Provider has allowAllEmployers flag
     */
    @Builder.Default
    private Boolean isGlobal = false;
    
    /**
     * Is this partnership currently active?
     */
    @Builder.Default
    private Boolean isActive = true;
}
