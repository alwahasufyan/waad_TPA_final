package com.waad.tba.modules.auth.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private UserInfo user;
    
    /**
     * Simplified UserInfo DTO - Role-Based Only
     * Each user has exactly ONE role
     * No permissions array (Backend is the authority)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private List<String> roles;      // User's roles (typically ONE role)
        private List<String> permissions; // User's permissions (for frontend permission checks)
        private Long employerId;         // For EMPLOYER_ADMIN role
        private Long providerId;         // For PROVIDER role
        private String providerName;     // Provider name for display (PROVIDER role)
    }
}
