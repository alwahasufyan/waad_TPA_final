package com.waad.tba.modules.systemadmin.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Creating New Role
 * Phase 2 - System Administration - Role Management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleCreateDto {
    
    @NotBlank(message = "Role name is required")
    @Size(min = 2, max = 50, message = "Role name must be between 2 and 50 characters")
    private String name;
    
    private String description;
    
    private List<String> permissions;
    
    @Builder.Default
    private Boolean active = true;
}
