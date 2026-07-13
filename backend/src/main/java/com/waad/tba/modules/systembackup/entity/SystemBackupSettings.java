package com.waad.tba.modules.systembackup.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_backup_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemBackupSettings {

    @Id
    private Long id;

    @Column(name = "local_enabled", nullable = false)
    @Builder.Default
    private Boolean localEnabled = true;

    @Column(name = "local_display_name", nullable = false, length = 150)
    @Builder.Default
    private String localDisplayName = "المسار المحلي الأساسي";

    @Column(name = "local_path", nullable = false, length = 1000)
    private String localPath;

    @Column(name = "retention_days", nullable = false)
    @Builder.Default
    private Integer retentionDays = 30;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
