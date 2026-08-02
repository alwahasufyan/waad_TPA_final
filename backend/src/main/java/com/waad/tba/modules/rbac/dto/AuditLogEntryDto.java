package com.waad.tba.modules.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WAAD-RBAC-USERS-ROLES-PERMISSIONS-COMPLETION-1: one row of the admin
 * "سجل تغييرات الصلاحيات" screen — UserAuditLog enriched with resolved
 * usernames (the raw entity only stores userId/performedBy as IDs).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntryDto {
    private Long id;
    private String action;
    private String details;
    private Long targetUserId;
    private String targetUsername;
    private Long performedByUserId;
    private String performedByUsername;
    private String ipAddress;
    private LocalDateTime createdAt;
}
