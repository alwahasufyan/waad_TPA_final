package com.waad.tba.modules.systemadmin.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Role View/Response
 * Phase 2 - System Administration - Role Management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleViewDto {
    
    private Long id;
    private String name;
    private String description;
    private List<String> permissions;
    private Integer userCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
