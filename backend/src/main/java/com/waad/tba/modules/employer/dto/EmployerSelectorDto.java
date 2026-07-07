package com.waad.tba.modules.employer.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for employer selector dropdown.
 * Uses 'label' field for proper frontend Autocomplete/Select display.
 */
@Data
@Builder
public class EmployerSelectorDto {

    private Long id;
    
    /**
     * Display label for dropdown (employer name)
     * Frontend expects 'label' for getOptionLabel
     */
    private String label;
    
    /**
     * Employer code (for filtering and identification)
     */
    private String code;
}
