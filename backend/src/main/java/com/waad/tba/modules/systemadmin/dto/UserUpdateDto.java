package com.waad.tba.modules.systemadmin.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Updating Existing User
 * Phase 2 - System Administration - User Management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDto {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Full name is required")
    private String fullName;
    
    private String phone;
    
    @NotNull(message = "Active status is required")
    private Boolean active;
    
    @NotNull(message = "At least one role must be assigned")
    @Size(min = 1, message = "At least one role must be assigned")
    private List<String> roles;
    
    private List<String> permissions;
    
    // For EMPLOYER role
    private Long employerId;
}
