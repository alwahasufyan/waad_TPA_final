package com.waad.tba.modules.preauthorization.dto;

import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAudit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for PreAuthorization Audit Trail Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationAuditDto {

    private Long id;
    private Long preAuthorizationId;
    private String referenceNumber;
    private String changedBy;
    private LocalDateTime changeDate;
    private String action;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String notes;
    private String ipAddress;

    /**
     * Convert entity to DTO
     */
    public static PreAuthorizationAuditDto fromEntity(PreAuthorizationAudit audit) {
        return PreAuthorizationAuditDto.builder()
                .id(audit.getId())
                .preAuthorizationId(audit.getPreAuthorizationId())
                .referenceNumber(audit.getReferenceNumber())
                .changedBy(audit.getChangedBy())
                .changeDate(audit.getChangeDate())
                .action(audit.getAction().name())
                .fieldName(audit.getFieldName())
                .oldValue(audit.getOldValue())
                .newValue(audit.getNewValue())
                .notes(audit.getNotes())
                .ipAddress(audit.getIpAddress())
                .build();
    }
}
