package com.waad.tba.modules.maintenance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_maintenance_mode")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMaintenanceMode {

    @Id
    private Long id;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
