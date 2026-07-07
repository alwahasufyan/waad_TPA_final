package com.waad.tba.modules.systemadmin.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Module Access Control
 * Phase 2 - System Administration - Module Access
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleAccessDto {
    
    private Long id;
    
    @NotBlank(message = "Module name is required")
    private String moduleName;
    
    @NotBlank(message = "Module key is required")
    private String moduleKey;
    
    private String description;
    
    @NotNull(message = "Allowed roles are required")
    private List<String> allowedRoles;
    
    private List<String> requiredPermissions;
    
    private String featureFlagKey;
    
    @Builder.Default
    private Boolean active = true;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
