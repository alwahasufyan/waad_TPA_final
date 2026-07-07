package com.waad.tba.modules.systemadmin.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Feature Flag
 * Phase 2 - System Administration - Feature Flags
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagDto {
    
    private Long id;
    
    @NotBlank(message = "Flag key is required")
    private String flagKey;
    
    @NotBlank(message = "Flag name is required")
    private String flagName;
    
    private String description;
    
    @Builder.Default
    private Boolean enabled = true;
    
    private List<String> roleFilters;
    
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
