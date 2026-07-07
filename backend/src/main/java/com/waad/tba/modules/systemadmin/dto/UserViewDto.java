package com.waad.tba.modules.systemadmin.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for User View/Response
 * Phase 2 - System Administration - User Management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserViewDto {
    
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private Boolean active;
    private Boolean emailVerified;
    private List<String> roles;
    private List<String> permissions;
    private Long employerId;
    private String employerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
