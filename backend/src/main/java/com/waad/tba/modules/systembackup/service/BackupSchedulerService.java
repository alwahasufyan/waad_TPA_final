package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.monitoring.service.TelegramAlertService;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupJobDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.BackupType;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs an automatic backup at the configured time-of-day, then purges old backups.
 * Safe by design: a single cycle at a time, failures are swallowed (never stop the app),
 * a Telegram alert is sent on failure when Telegram is enabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupSchedulerService {

    private final BackupSettingsService settingsService;
    private final BackupService backupService;
    private final BackupRetentionService retentionService;
    private final TelegramAlertService telegramAlertService;
    private final Optional<AuditLogService> auditLogService;

    private final AtomicBoolean cycleRunning = new AtomicBoolean(false);

    /** Fires once per minute; the due-time check decides whether to actually run. */
    @Scheduled(cron = "${WAAD_BACKUP_SCHEDULER_CRON:0 * * * * *}")
    public void tick() {
        try {
            runIfDue(LocalDateTime.now());
        } catch (Exception e) {
            // The scheduler must never throw out of @Scheduled.
            log.warn("[BKP-4] Backup scheduler tick failed: {}", e.getMessage());
        }
    }

    /** Package-visible for testing. Runs a backup cycle if the settings say it is due now. */
    void runIfDue(LocalDateTime now) {
        SystemBackupSettings settings;
        try {
            settings = settingsService.getOrCreate();
        } catch (Exception e) {
            log.warn("[BKP-4] Could not load backup settings for scheduler: {}", e.getMessage());
            return;
        }

        if (!Boolean.TRUE.equals(settings.getAutoBackupEnabled())) {
            return;
        }
        int hour = settings.getAutoBackupHour() == null ? 2 : settings.getAutoBackupHour();
        int minute = settings.getAutoBackupMinute() == null ? 0 : settings.getAutoBackupMinute();
        if (now.getHour() != hour || now.getMinute() != minute) {
            return;
        }
        // Do not run twice in the same minute (idempotent tick).
        LocalDateTime last = settings.getLastAutoBackupAt();
        if (last != null && last.truncatedTo(ChronoUnit.MINUTES).equals(now.truncatedTo(ChronoUnit.MINUTES))) {
            return;
        }
        if (!cycleRunning.compareAndSet(false, true)) {
            log.info("[BKP-4] Scheduled backup skipped — a cycle is already running");
            return;
        }
        try {
            runBackupCycle(settings, "SCHEDULER");
        } finally {
            cycleRunning.set(false);
        }
    }

    private void runBackupCycle(SystemBackupSettings settings, String username) {
        BackupType type = parseType(settings.getAutoBackupType());
        try {
            BackupJobDto job = backupService.create(type, "نسخة احتياطية تلقائية مجدولة", username);
            if (job.status() == BackupStatus.SUCCESS) {
                settingsService.recordAutoBackup("SUCCESS", "نسخة تلقائية ناجحة رقم " + job.id());
                audit("BACKUP_AUTO_RUN", job.id(), "Scheduled backup succeeded (type=" + type + ")", username);
                try {
                    retentionService.purge(false, username);
                } catch (Exception purgeError) {
                    log.warn("[BKP-4] Retention purge after scheduled backup failed: {}", purgeError.getMessage());
                }
            } else {
                String msg = job.errorMessage() == null ? "فشل غير معروف" : job.errorMessage();
                settingsService.recordAutoBackup("FAILED", msg);
                audit("BACKUP_AUTO_RUN_FAILED", job.id(), "Scheduled backup failed", username);
                notifyFailure(settings, msg);
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            settingsService.recordAutoBackup("FAILED", msg);
            audit("BACKUP_AUTO_RUN_FAILED", null, "Scheduled backup threw: " + msg, username);
            notifyFailure(settings, msg);
        }
    }

    private void notifyFailure(SystemBackupSettings settings, String reason) {
        try {
            telegramAlertService.sendMonitoringMessage(
                    "🚨 WAAD نسخ احتياطي\n" +
                            "فشلت النسخة الاحتياطية التلقائية المجدولة.\n" +
                            "البيئة: " + settings.getUpdatedBy() + "\n" +
                            "السبب: " + reason + "\n" +
                            "الوقت: " + LocalDateTime.now());
        } catch (Exception e) {
            // Telegram disabled or unreachable — never let this break the scheduler.
            log.warn("[BKP-4] Could not send backup-failure Telegram alert: {}", e.getMessage());
        }
    }

    private void audit(String action, Long entityId, String details, String username) {
        auditLogService.ifPresent(service -> {
            try {
                service.createAuditLog(action, "SystemBackupJob", entityId, details, null, username, null, null);
            } catch (Exception ignored) {
                // auditing must never break the cycle
            }
        });
    }

    private static BackupType parseType(String value) {
        if (value == null || value.isBlank()) {
            return BackupType.FULL_SYSTEM;
        }
        try {
            return BackupType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BackupType.FULL_SYSTEM;
        }
    }
}
