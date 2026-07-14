package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupSettingsDto;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import com.waad.tba.modules.systembackup.repository.SystemBackupSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BackupSettingsService {
    private static final long SETTINGS_ID = 1L;

    private final SystemBackupSettingsRepository repository;
    private final Environment environment;
    private final Optional<AuditLogService> auditLogService;

    public SystemBackupSettings getOrCreate() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> repository.save(SystemBackupSettings.builder()
                .id(SETTINGS_ID)
                .localPath(defaultBackupPath())
                .updatedBy("SYSTEM")
                .updatedAt(LocalDateTime.now())
                .build()));
    }

    public BackupSettingsDto getSettings() {
        return toDto(getOrCreate());
    }

    public BackupSettingsDto update(BackupSettingsDto dto, String username) {
        SystemBackupSettings settings = getOrCreate();
        settings.setLocalEnabled(Boolean.TRUE.equals(dto.localEnabled()));
        settings.setLocalDisplayName(blankToDefault(dto.localDisplayName(), "المسار المحلي الأساسي"));
        // The runtime backup path is server/Docker-configured only. Never accept
        // browser-submitted paths such as C:\Users\... or D:\Desktop here.
        settings.setLocalPath(defaultBackupPath());
        settings.setRetentionDays(dto.retentionDays() == null || dto.retentionDays() < 1 ? 30 : dto.retentionDays());
        settings.setAutoBackupEnabled(Boolean.TRUE.equals(dto.autoBackupEnabled()));
        settings.setAutoBackupType(normalizeType(dto.autoBackupType()));
        settings.setAutoBackupHour(clamp(dto.autoBackupHour(), 0, 23, 2));
        settings.setAutoBackupMinute(clamp(dto.autoBackupMinute(), 0, 59, 0));
        settings.setUpdatedBy(username);
        settings.setUpdatedAt(LocalDateTime.now());
        BackupSettingsDto result = toDto(repository.save(settings));
        auditLogService.ifPresent(service -> service.createAuditLog(
                "BACKUP_SETTINGS_UPDATED",
                "SystemBackupSettings",
                SETTINGS_ID,
                "Backup settings updated (retentionDays=" + settings.getRetentionDays()
                        + ", localEnabled=" + settings.getLocalEnabled() + ")",
                null,
                username,
                null,
                null
        ));
        return result;
    }

    public Path localBackupPath() {
        return Path.of(getOrCreate().getLocalPath()).toAbsolutePath().normalize();
    }

    @org.springframework.transaction.annotation.Transactional
    public void recordAutoBackup(String status, String messageAr) {
        SystemBackupSettings settings = getOrCreate();
        settings.setLastAutoBackupAt(LocalDateTime.now());
        settings.setLastAutoBackupStatus(status);
        settings.setLastAutoBackupMessage(safe(messageAr));
        repository.save(settings);
    }

    @org.springframework.transaction.annotation.Transactional
    public void recordPurge(String status, String messageAr) {
        SystemBackupSettings settings = getOrCreate();
        settings.setLastPurgeAt(LocalDateTime.now());
        settings.setLastPurgeStatus(status);
        settings.setLastPurgeMessage(safe(messageAr));
        repository.save(settings);
    }

    private static String safe(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }

    private BackupSettingsDto toDto(SystemBackupSettings settings) {
        return new BackupSettingsDto(
                settings.getLocalEnabled(),
                settings.getLocalDisplayName(),
                defaultBackupPath(),
                localHostPath(),
                defaultBackupPath(),
                "مسار محلي على السيرفر",
                settings.getRetentionDays(),
                settings.getAutoBackupEnabled(),
                settings.getAutoBackupType(),
                settings.getAutoBackupHour(),
                settings.getAutoBackupMinute(),
                settings.getLastAutoBackupAt(),
                settings.getLastAutoBackupStatus(),
                settings.getLastAutoBackupMessage(),
                settings.getLastPurgeAt(),
                settings.getLastPurgeStatus(),
                settings.getLastPurgeMessage()
        );
    }

    private static String normalizeType(String value) {
        if (value == null || value.isBlank()) {
            return "FULL_SYSTEM";
        }
        String upper = value.trim().toUpperCase();
        return switch (upper) {
            case "DATABASE_ONLY", "FILES_ONLY", "FULL_SYSTEM" -> upper;
            default -> "FULL_SYSTEM";
        };
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        int number = value == null ? fallback : value;
        if (number < min) {
            return min;
        }
        return Math.min(number, max);
    }

    private String defaultBackupPath() {
        String configured = environment.getProperty("BACKUP_LOCAL_1_CONTAINER");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        configured = environment.getProperty("BACKUP_DIR");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "/app/backups/local1";
    }

    private String localHostPath() {
        String configured = environment.getProperty("BACKUP_LOCAL_1_HOST");
        return configured == null || configured.isBlank() ? "مسار سيرفر مضبوط في Docker/البيئة" : configured.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
