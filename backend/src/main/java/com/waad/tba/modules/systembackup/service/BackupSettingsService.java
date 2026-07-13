package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupSettingsDto;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import com.waad.tba.modules.systembackup.repository.SystemBackupSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BackupSettingsService {
    private static final long SETTINGS_ID = 1L;

    private final SystemBackupSettingsRepository repository;
    private final Environment environment;

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
        settings.setUpdatedBy(username);
        settings.setUpdatedAt(LocalDateTime.now());
        return toDto(repository.save(settings));
    }

    public Path localBackupPath() {
        return Path.of(getOrCreate().getLocalPath()).toAbsolutePath().normalize();
    }

    private BackupSettingsDto toDto(SystemBackupSettings settings) {
        return new BackupSettingsDto(
                settings.getLocalEnabled(),
                settings.getLocalDisplayName(),
                defaultBackupPath(),
                localHostPath(),
                defaultBackupPath(),
                "مسار محلي على السيرفر",
                settings.getRetentionDays()
        );
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
