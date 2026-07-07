package com.waad.tba.modules.systemadmin.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Updating Role
 * Phase 2 - System Administration - Role Management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUpdateDto {
    
    @NotBlank(message = "Role name is required")
    private String name;
    
    private String description;
    
    private List<String> permissions;
    
    private Boolean active;
}
