package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.dto.BackupDtos.*;
import com.waad.tba.modules.systembackup.entity.*;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;

@Service
@RequiredArgsConstructor
public class BackupService {

    private final BackupSettingsService settingsService;
    private final BackupStorageService storageService;
    private final BackupChecksumService checksumService;
    private final BackupManifestService manifestService;
    private final BackupHistoryService historyService;
    private final SystemBackupJobRepository jobRepository;
    private final Optional<AuditLogService> auditLogService;

    // Guarantees a single backup runs at a time (manual or scheduled).
    private static final java.util.concurrent.locks.ReentrantLock BACKUP_LOCK =
            new java.util.concurrent.locks.ReentrantLock();

    public boolean isBackupRunning() {
        return BACKUP_LOCK.isLocked();
    }

    @Transactional(readOnly = true)
    public List<BackupJobDto> list() {
        return jobRepository.findTop100ByOrderByStartedAtDesc().stream().map(historyService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public BackupJobDto get(Long id) {
        return historyService.toDto(findJob(id));
    }

    @Transactional(readOnly = true)
    public BackupStatusDto status() {
        Path localPath = settingsService.localBackupPath();
        boolean writable = storageService.isWritableDirectory(localPath);
        Long usableSpace = storageService.usableSpace(localPath);
        return new BackupStatusDto(
                jobRepository.findTopByOrderByStartedAtDesc().map(historyService::toDto).orElse(null),
                jobRepository.count(),
                jobRepository.countByStatus(BackupStatus.SUCCESS),
                jobRepository.countByStatus(BackupStatus.FAILED),
                localPath.toString(),
                settingsService.getSettings().localHostPath(),
                settingsService.getSettings().localContainerPath(),
                localPath.toString() != null && !localPath.toString().isBlank(),
                writable,
                writable ? "المسار المحلي المعتمد على السيرفر قابل للكتابة" : "المسار المحلي المعتمد على السيرفر غير قابل للكتابة",
                usableSpace,
                jobRepository.findTopByOrderByStartedAtDesc().map(SystemBackupJob::getFileSize).orElse(null)
        );
    }

    @Transactional
    public BackupJobDto create(BackupType type, String note, String username) {
        if (type == null) {
            throw new IllegalArgumentException("Backup type is required");
        }
        SystemBackupSettings settings = settingsService.getOrCreate();
        if (!Boolean.TRUE.equals(settings.getLocalEnabled())) {
            throw new IllegalStateException("Local backup destination is disabled");
        }
        // Prevent two backups running at the same time (manual + scheduled).
        if (!BACKUP_LOCK.tryLock()) {
            throw new IllegalStateException("نسخة احتياطية أخرى قيد التنفيذ حاليًا. حاول بعد اكتمالها.");
        }
        try {
            return runCreate(type, note, username, settings);
        } finally {
            BACKUP_LOCK.unlock();
        }
    }

    private BackupJobDto runCreate(BackupType type, String note, String username, SystemBackupSettings settings) {
        LocalDateTime startedAt = LocalDateTime.now();
        SystemBackupJob job = jobRepository.save(SystemBackupJob.builder()
                .type(type)
                .status(BackupStatus.RUNNING)
                .note(note)
                .createdBy(username)
                .startedAt(startedAt)
                .environment(storageService.environmentName())
                .destinationPath(settings.getLocalPath())
                .encrypted(false)
                .backupFormat("zip")
                .gitCommit(resolveGitCommit())
                .build());

        List<String> warnings = new ArrayList<>();
        Path workingDir = null;
        try {
            Path backupRoot = settingsService.localBackupPath();
            Files.createDirectories(backupRoot);
            if (!storageService.isWritableDirectory(backupRoot)) {
                throw new IllegalStateException("Local backup path is not writable: " + backupRoot);
            }

            workingDir = storageService.createWorkingDirectory(backupRoot, job.getId());
            String baseName = "waad-backup-" + job.getId() + "-" + type.name().toLowerCase() + "-" + System.currentTimeMillis();
            Path archive = backupRoot.resolve(baseName + ".zip").toAbsolutePath().normalize();

            boolean includeDb = type == BackupType.DATABASE_ONLY || type == BackupType.FULL_SYSTEM;
            boolean includeFiles = type == BackupType.FILES_ONLY || type == BackupType.FULL_SYSTEM;

            Path dbDump = null;
            if (includeDb) {
                dbDump = storageService.dumpDatabase(workingDir, "database.dump", warnings);
            }
            Path finalDbDump = dbDump;
            BackupManifest preliminaryManifest = new BackupManifest(
                    job.getId(),
                    type,
                    startedAt,
                    null,
                    job.getEnvironment(),
                    job.getGitCommit(),
                    storageService.includedComponents(includeDb, includeFiles),
                    username,
                    note,
                    null,
                    null,
                    "zip",
                    archive.toString(),
                    warnings
            );

            storageService.writeZip(archive, zip -> {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifestService.toBytes(preliminaryManifest));
                zip.closeEntry();

                if (finalDbDump != null) {
                    zip.putNextEntry(new ZipEntry("database/database.dump"));
                    Files.copy(finalDbDump, zip);
                    zip.closeEntry();
                }
                if (includeFiles) {
                    storageService.addPathToZip(zip, storageService.uploadsPath(), "uploads", warnings);
                }
            });

            String checksum = checksumService.sha256(archive);
            long size = Files.size(archive);
            LocalDateTime completedAt = LocalDateTime.now();
            Path manifestPath = backupRoot.resolve(baseName + ".manifest.json").toAbsolutePath().normalize();
            manifestService.write(manifestPath, new BackupManifest(
                    job.getId(),
                    type,
                    startedAt,
                    completedAt,
                    job.getEnvironment(),
                    job.getGitCommit(),
                    storageService.includedComponents(includeDb, includeFiles),
                    username,
                    note,
                    checksum,
                    size,
                    "zip",
                    archive.toString(),
                    warnings
            ));

            job.setStatus(BackupStatus.SUCCESS);
            job.setFileName(archive.getFileName().toString());
            job.setFilePath(archive.toString());
            job.setFileSize(size);
            job.setChecksum(checksum);
            job.setManifestPath(manifestPath.toString());
            job.setCompletedAt(completedAt);
            job.setDurationMs(Duration.between(startedAt, completedAt).toMillis());
            job.setWarnings(String.join("\n", warnings));
        } catch (Exception e) {
            LocalDateTime completedAt = LocalDateTime.now();
            job.setStatus(BackupStatus.FAILED);
            job.setCompletedAt(completedAt);
            job.setDurationMs(Duration.between(startedAt, completedAt).toMillis());
            job.setErrorMessage(safeMessage(e));
            job.setWarnings(String.join("\n", warnings));
        } finally {
            cleanupWorkingDirectory(workingDir);
        }

        SystemBackupJob saved = jobRepository.save(job);
        auditLogService.ifPresent(service -> service.createAuditLog(
                "BACKUP_MANUAL_RUN",
                "SystemBackupJob",
                saved.getId(),
                "Manual backup executed (type=" + saved.getType() + ", status=" + saved.getStatus() + ")",
                null,
                username,
                null,
                null
        ));
        return historyService.toDto(saved);
    }

    @Transactional(readOnly = true)
    public ValidationResultDto validate(Long id) {
        SystemBackupJob job = findJob(id);
        if (job.getFilePath() == null || job.getChecksum() == null) {
            return new ValidationResultDto(id, false, job.getChecksum(), null,
                    "Backup has no downloadable artifact",
                    "لا توجد حزمة نسخة احتياطية قابلة للتحقق");
        }
        Path path = Path.of(job.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return new ValidationResultDto(id, false, job.getChecksum(), null,
                    "Backup file is missing",
                    "ملف النسخة الاحتياطية غير موجود");
        }
        String actual = checksumService.sha256(path);
        boolean valid = actual.equalsIgnoreCase(job.getChecksum());
        return new ValidationResultDto(id, valid, job.getChecksum(), actual,
                valid ? "Checksum is valid" : "Checksum mismatch",
                valid ? "التحقق ناجح: checksum مطابق" : "فشل التحقق: checksum غير مطابق");
    }

    @Transactional(readOnly = true)
    public Resource download(Long id) {
        SystemBackupJob job = findJob(id);
        if (job.getStatus() != BackupStatus.SUCCESS || job.getFilePath() == null) {
            throw new IllegalStateException("Backup is not available for download");
        }
        Path path = Path.of(job.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalStateException("Backup file is missing");
        }
        return new FileSystemResource(path);
    }

    @Transactional(readOnly = true)
    public String downloadFileName(Long id) {
        return findJob(id).getFileName();
    }

    private SystemBackupJob findJob(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Backup job not found"));
    }

    private String resolveGitCommit() {
        try {
            Path head = Path.of(".git", "HEAD");
            if (!Files.exists(head)) return null;
            String headValue = Files.readString(head, StandardCharsets.UTF_8).trim();
            if (headValue.startsWith("ref:")) {
                Path refPath = Path.of(".git", headValue.substring(5).trim());
                return Files.exists(refPath) ? Files.readString(refPath, StandardCharsets.UTF_8).trim() : null;
            }
            return headValue;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 1800 ? message.substring(0, 1800) : message;
    }

    private void cleanupWorkingDirectory(Path workingDir) {
        if (workingDir == null) {
            return;
        }
        try {
            Path normalized = workingDir.toAbsolutePath().normalize();
            if (!normalized.getFileName().toString().startsWith("backup-")) {
                return;
            }
            try (var stream = Files.walk(normalized)) {
                for (Path path : stream.sorted((a, b) -> b.compareTo(a)).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (Exception ignored) {
            // Best-effort cleanup only. Backup history remains authoritative.
        }
    }
}
