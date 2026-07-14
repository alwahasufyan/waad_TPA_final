package com.waad.tba.modules.dangerzone.service;

import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.DangerZoneResultDto;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.DangerZoneStatusDto;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.OtpSendResultDto;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.ResetRequest;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.RestoreRequest;
import com.waad.tba.modules.dangerzone.service.DangerZoneTelegramOtpService.OtpSendResult;
import com.waad.tba.modules.errorlog.repository.SystemErrorLogRepository;
import com.waad.tba.modules.maintenance.service.MaintenanceModeService;
import com.waad.tba.modules.monitoring.service.TelegramAlertService;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringAlertStateRepository;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringErrorEventRepository;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupJobDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.BackupType;
import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import com.waad.tba.modules.systembackup.service.BackupService;
import com.waad.tba.modules.systembackup.service.BackupSettingsService;
import com.waad.tba.modules.systembackup.service.BackupStorageService;
import com.waad.tba.modules.systembackup.service.BackupStorageService.ProcessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Destructive operations (restore / reset), gated per environment.
 *
 * DEV/LOCAL: WAAD_DANGER_ZONE_ENABLED=true + SUPER_ADMIN + password + confirmation phrase + mandatory backup.
 * PRODUCTION: WAAD_PRODUCTION_DANGER_ZONE_ENABLED=true + maintenance mode ON + Telegram configured +
 *             SUPER_ADMIN + password + Telegram OTP (6-digit, 5 min, one-time) + confirmation phrase +
 *             mandatory backup. A plain env flag alone never opens production.
 *
 * Every step is audited; failures never crash the app and trigger a Telegram alert when possible.
 */
@Service
@Slf4j
public class DangerZoneService {

    public static final String RESTORE_PHRASE = "RESTORE WAAD DATABASE";
    public static final String RESET_PHRASE = "RESET WAAD DATA";
    public static final String OP_RESTORE = "RESTORE";
    public static final String OP_RESET = "RESET";
    private static final long COOLDOWN_MS = 30_000;

    private final Environment environment;
    private final AuthenticationManager authenticationManager;
    private final BackupService backupService;
    private final BackupSettingsService backupSettingsService;
    private final BackupStorageService storageService;
    private final SystemBackupJobRepository jobRepository;
    private final SystemErrorLogRepository errorLogRepository;
    private final SystemMonitoringErrorEventRepository monitoringErrorRepository;
    private final SystemMonitoringAlertStateRepository monitoringAlertStateRepository;
    private final MaintenanceModeService maintenanceModeService;
    private final DangerZoneTelegramOtpService otpService;
    private final TelegramAlertService telegramAlertService;
    private final Optional<AuditLogService> auditLogService;

    private volatile long lastOperationAt = 0L;

    public DangerZoneService(Environment environment,
                             AuthenticationManager authenticationManager,
                             BackupService backupService,
                             BackupSettingsService backupSettingsService,
                             BackupStorageService storageService,
                             SystemBackupJobRepository jobRepository,
                             SystemErrorLogRepository errorLogRepository,
                             SystemMonitoringErrorEventRepository monitoringErrorRepository,
                             SystemMonitoringAlertStateRepository monitoringAlertStateRepository,
                             MaintenanceModeService maintenanceModeService,
                             DangerZoneTelegramOtpService otpService,
                             TelegramAlertService telegramAlertService,
                             Optional<AuditLogService> auditLogService) {
        this.environment = environment;
        this.authenticationManager = authenticationManager;
        this.backupService = backupService;
        this.backupSettingsService = backupSettingsService;
        this.storageService = storageService;
        this.jobRepository = jobRepository;
        this.errorLogRepository = errorLogRepository;
        this.monitoringErrorRepository = monitoringErrorRepository;
        this.monitoringAlertStateRepository = monitoringAlertStateRepository;
        this.maintenanceModeService = maintenanceModeService;
        this.otpService = otpService;
        this.telegramAlertService = telegramAlertService;
        this.auditLogService = auditLogService;
    }

    // ===================== environment / flags =====================

    public boolean isProductionLike() {
        for (String p : environment.getActiveProfiles()) {
            if (p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production")) {
                return true;
            }
        }
        return false;
    }

    private boolean devFlag() {
        return Boolean.parseBoolean(environment.getProperty("WAAD_DANGER_ZONE_ENABLED", "false"));
    }

    private boolean productionFlag() {
        return Boolean.parseBoolean(environment.getProperty("WAAD_PRODUCTION_DANGER_ZONE_ENABLED", "false"));
    }

    /** Whether the danger zone can be opened at all in the current environment. */
    public boolean isEnabled() {
        if (isProductionLike()) {
            return productionFlag() && maintenanceModeService.isEnabled() && otpService.isTelegramConfigured();
        }
        return devFlag();
    }

    public DangerZoneStatusDto status() {
        boolean prod = isProductionLike();
        boolean maintenance = maintenanceModeService.isEnabled();
        boolean telegram = otpService.isTelegramConfigured();
        boolean enabled = isEnabled();
        String reason = buildReason(prod, enabled, maintenance, telegram);
        return new DangerZoneStatusDto(
                enabled, environmentName(), prod, reason,
                devFlag(), productionFlag(), maintenance, telegram, prod,
                RESTORE_PHRASE, RESET_PHRASE, serverRunbook());
    }

    private String buildReason(boolean prod, boolean enabled, boolean maintenance, boolean telegram) {
        if (enabled && prod) {
            return "منطقة الخطر مفتوحة على الإنتاج بحماية مشددة: تتطلب كود تأكيد Telegram + كلمة مرور المدير + عبارة تأكيد + نسخة أمان إجبارية.";
        }
        if (enabled) {
            return "منطقة الخطر متاحة في بيئة التطوير مع كامل الحمايات (كلمة مرور المدير + عبارة تأكيد + نسخة أمان).";
        }
        if (prod) {
            List<String> missing = new ArrayList<>();
            if (!productionFlag()) missing.add("تفعيل WAAD_PRODUCTION_DANGER_ZONE_ENABLED من إعدادات السيرفر");
            if (!maintenance) missing.add("تفعيل وضع الصيانة");
            if (!telegram) missing.add("تهيئة Telegram");
            return "منطقة الخطر في الإنتاج مقفلة. المطلوب: " + String.join("، ", missing) + ".";
        }
        return "منطقة الخطر مقفلة. فعّل WAAD_DANGER_ZONE_ENABLED=true في بيئة التطوير أولًا.";
    }

    // ===================== OTP =====================

    public OtpSendResultDto sendOtp(String operation, String username) {
        String op = normalizeOperation(operation);
        // In production the full gate must be armed before an OTP is even issued.
        if (isProductionLike()) {
            assertProductionArmed();
        } else if (!devFlag()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "منطقة الخطر مقفلة. فعّل WAAD_DANGER_ZONE_ENABLED=true أولًا.");
        }
        OtpSendResult result = otpService.send(op, username, environmentName());
        return new OtpSendResultDto(result.operation(), result.expiresAt(), result.validSeconds(),
                "تم إرسال رمز تأكيد إلى Telegram. صالح لمدة " + (result.validSeconds() / 60) + " دقائق.");
    }

    // ===================== guards =====================

    private void assertProductionArmed() {
        if (!productionFlag()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "منطقة الخطر في الإنتاج مقفلة من إعدادات السيرفر.");
        }
        if (!maintenanceModeService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "يجب تفعيل وضع الصيانة قبل تنفيذ إجراءات الخطر على الإنتاج.");
        }
        if (!otpService.isTelegramConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Telegram غير مهيأ. لا يمكن تنفيذ إجراءات الخطر على الإنتاج بدون كود تأكيد Telegram.");
        }
    }

    /** Full precondition check for a destructive operation, per environment. Consumes the OTP in prod. */
    private void assertOperable(String operation, String password, String phrase, String otpCode, String username) {
        assertCooldown();
        if (isProductionLike()) {
            assertProductionArmed();
            assertReauth(username, password);
            otpService.verifyAndConsume(operation, username, otpCode); // one-time; audits OTP_VERIFIED
            assertPhrase(phrase, expectedPhrase(operation));
        } else {
            if (!devFlag()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "منطقة الخطر مقفلة. فعّل WAAD_DANGER_ZONE_ENABLED=true في بيئة التطوير أولًا.");
            }
            assertReauth(username, password);
            assertPhrase(phrase, expectedPhrase(operation));
        }
    }

    private void assertReauth(String username, String password) {
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "كلمة مرور المدير مطلوبة.");
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "كلمة مرور المدير غير صحيحة.");
        }
    }

    private void assertPhrase(String provided, String expected) {
        if (provided == null || !provided.trim().equals(expected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "عبارة التأكيد غير مطابقة.");
        }
    }

    private void assertCooldown() {
        if (System.currentTimeMillis() - lastOperationAt < COOLDOWN_MS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "الرجاء الانتظار قليلًا قبل تنفيذ عملية خطرة أخرى.");
        }
    }

    // ===================== restore =====================

    @Transactional
    public DangerZoneResultDto restore(Long backupId, RestoreRequest request, String username) {
        long start = System.currentTimeMillis();
        assertOperable(OP_RESTORE,
                request == null ? null : request.password(),
                request == null ? null : request.confirmationPhrase(),
                request == null ? null : request.otpCode(),
                username);

        SystemBackupJob job = jobRepository.findById(backupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "النسخة الاحتياطية غير موجودة."));
        if (job.getStatus() != BackupStatus.SUCCESS || job.getFilePath() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "النسخة غير صالحة للاستعادة.");
        }
        Path backupRoot = backupSettingsService.localBackupPath();
        Path archive = Path.of(job.getFilePath()).toAbsolutePath().normalize();
        if (!archive.startsWith(backupRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "مسار النسخة خارج المجلد المعتمد. مرفوض.");
        }
        if (!storageService.archiveContainsDatabaseDump(archive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "هذه النسخة لا تحتوي قاعدة بيانات للاستعادة.");
        }

        audit("RESTORE_STARTED", backupId, "Restore requested for backup " + backupId + " (env=" + environmentName() + ")", username);
        Long safetyBackupId = takeMandatoryBackup(OP_RESTORE, username);

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("waad-restore-");
            Path dump = storageService.extractDatabaseDump(archive, workDir);
            ProcessResult result = storageService.pgRestoreInto(dump);
            lastOperationAt = System.currentTimeMillis();
            if (!result.ok()) {
                failure("RESTORE", "pg_restore exit=" + result.exitCode(), username);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "فشلت الاستعادة. تم الاحتفاظ بنسخة أمان قبل التنفيذ رقم " + safetyBackupId + ".");
            }
            audit("RESTORE_SUCCESS", backupId, "Restore completed from backup " + backupId, username);
            return new DangerZoneResultDto(true,
                    "تمت الاستعادة بنجاح من النسخة رقم " + backupId + ". نسخة الأمان قبل التنفيذ رقم " + safetyBackupId + ".",
                    safetyBackupId, List.of("استعادة قاعدة البيانات من النسخة رقم " + backupId),
                    System.currentTimeMillis() - start);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            failure("RESTORE", "error: " + e.getMessage(), username);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "خطأ أثناء الاستعادة. نسخة الأمان رقم " + safetyBackupId + " متاحة.");
        } finally {
            cleanup(workDir);
        }
    }

    // ===================== reset =====================

    @Transactional
    public DangerZoneResultDto reset(ResetRequest request, String username) {
        long start = System.currentTimeMillis();
        assertOperable(OP_RESET,
                request == null ? null : request.password(),
                request == null ? null : request.confirmationPhrase(),
                request == null ? null : request.otpCode(),
                username);

        boolean monitoring = request != null && Boolean.TRUE.equals(request.resetMonitoringLogs());
        boolean errors = request != null && Boolean.TRUE.equals(request.resetErrorLogs());
        boolean backupMeta = request != null && Boolean.TRUE.equals(request.resetBackupMetadata());
        if (!monitoring && !errors && !backupMeta) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "اختر عنصرًا واحدًا على الأقل لإعادة التهيئة.");
        }

        audit("RESET_STARTED", null,
                "Reset requested (monitoring=" + monitoring + ", errors=" + errors + ", backupMeta=" + backupMeta
                        + ", env=" + environmentName() + ")",
                username);
        Long safetyBackupId = takeMandatoryBackup(OP_RESET, username);

        List<String> performed = new ArrayList<>();
        try {
            if (monitoring) {
                monitoringErrorRepository.deleteAllInBatch();
                monitoringAlertStateRepository.deleteAllInBatch();
                performed.add("تصفير سجلات المراقبة وحالات التنبيه");
            }
            if (errors) {
                errorLogRepository.deleteAllInBatch();
                performed.add("تصفير سجل أخطاء النظام");
            }
            if (backupMeta) {
                jobRepository.deleteAllInBatch();
                performed.add("تصفير بيانات سجل النسخ الاحتياطية (بدون حذف الملفات)");
            }
        } catch (Exception e) {
            failure("RESET", "error: " + e.getMessage(), username);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "خطأ أثناء إعادة التهيئة. نسخة الأمان رقم " + safetyBackupId + " متاحة.");
        }

        lastOperationAt = System.currentTimeMillis();
        audit("RESET_SUCCESS", null, "Reset performed: " + String.join(", ", performed), username);
        return new DangerZoneResultDto(true,
                "تمت إعادة التهيئة المحدودة بنجاح. نسخة الأمان قبل التنفيذ رقم " + safetyBackupId + ".",
                safetyBackupId, performed, System.currentTimeMillis() - start);
    }

    // ===================== helpers =====================

    private Long takeMandatoryBackup(String operation, String username) {
        audit("PRE_BACKUP_STARTED", null, "Mandatory pre-" + operation + " backup starting", username);
        BackupJobDto safety;
        try {
            safety = backupService.create(BackupType.FULL_SYSTEM,
                    "نسخة أمان إجبارية قبل " + operation + " (danger zone)", username);
        } catch (Exception e) {
            failure(operation, "mandatory backup threw: " + e.getMessage(), username);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "تعذّر إنشاء نسخة الأمان الإجبارية قبل التنفيذ. أُلغيت العملية للحفاظ على البيانات.");
        }
        if (safety == null || safety.status() != BackupStatus.SUCCESS) {
            failure(operation, "mandatory backup failed status", username);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "فشلت نسخة الأمان الإجبارية قبل التنفيذ. أُلغيت العملية للحفاظ على البيانات.");
        }
        audit("PRE_BACKUP_SUCCESS", safety.id(),
                "Mandatory pre-" + operation + " backup created id=" + safety.id() + " file=" + safety.fileName(), username);
        return safety.id();
    }

    private void failure(String operation, String detail, String username) {
        audit("FAILURE", null, operation + " failed: " + detail, username);
        try {
            telegramAlertService.sendMonitoringMessage(
                    "🚨 WAAD منطقة الخطر\n"
                            + "فشل تنفيذ عملية " + operation + ".\n"
                            + "المستخدم: " + username + "\n"
                            + "البيئة: " + environmentName() + "\n"
                            + "الوقت: " + LocalDateTime.now());
        } catch (Exception ignored) {
            // Telegram disabled/unreachable — never let this break the flow.
        }
    }

    private void audit(String action, Long entityId, String details, String username) {
        auditLogService.ifPresent(service -> {
            try {
                service.createAuditLog(action, "DangerZone", entityId, details, null, username, null, null);
            } catch (Exception ignored) {
                // auditing must never break the operation
            }
        });
    }

    private static String normalizeOperation(String operation) {
        if (operation == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "نوع العملية مطلوب.");
        }
        String op = operation.trim().toUpperCase();
        if (!OP_RESTORE.equals(op) && !OP_RESET.equals(op)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "نوع العملية غير صحيح.");
        }
        return op;
    }

    private static String expectedPhrase(String operation) {
        return OP_RESTORE.equals(operation) ? RESTORE_PHRASE : RESET_PHRASE;
    }

    private static void cleanup(Path dir) {
        if (dir == null) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort
                }
            });
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private String environmentName() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "local" : String.join(",", profiles);
    }

    private String serverRunbook() {
        return "الاستعادة/إعادة التهيئة على الإنتاج مؤمّنة: فعّل وضع الصيانة، اطلب كود Telegram، أدخل كلمة مرور المدير وعبارة التأكيد. "
                + "بديلًا يمكن تنفيذها من غلاف السيرفر: نزّل النسخة وتحقق من checksum ثم pg_restore --clean --if-exists ثم فحوصات الصحة.";
    }
}
