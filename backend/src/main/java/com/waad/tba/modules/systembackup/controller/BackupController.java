package com.waad.tba.modules.systembackup.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.systembackup.dto.BackupDtos.*;
import com.waad.tba.modules.systembackup.service.BackupRetentionService;
import com.waad.tba.modules.systembackup.service.BackupService;
import com.waad.tba.modules.systembackup.service.BackupSettingsService;
import com.waad.tba.modules.systembackup.service.RestoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/backups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BackupController {

    private final BackupService backupService;
    private final BackupSettingsService settingsService;
    private final BackupRetentionService retentionService;
    private final RestoreService restoreService;

    @GetMapping("/status")
    public ApiResponse<BackupStatusDto> status() {
        return ApiResponse.success(backupService.status());
    }

    @GetMapping
    public ApiResponse<List<BackupJobDto>> list() {
        return ApiResponse.success(backupService.list());
    }

    @PostMapping
    public ApiResponse<BackupJobDto> create(@RequestBody CreateBackupRequest request, Authentication authentication) {
        String username = authentication == null ? "SYSTEM" : authentication.getName();
        BackupJobDto created = backupService.create(request.type(), request.note(), username);
        return ApiResponse.success(created, "Backup job completed", "تم تنفيذ طلب النسخ الاحتياطي");
    }

    @GetMapping("/{id}")
    public ApiResponse<BackupJobDto> get(@PathVariable Long id) {
        return ApiResponse.success(backupService.get(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource resource = backupService.download(id);
        String fileName = backupService.downloadFileName(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @PostMapping("/{id}/validate")
    public ApiResponse<ValidationResultDto> validate(@PathVariable Long id) {
        return ApiResponse.success(backupService.validate(id));
    }

    @PostMapping("/{id}/verify-restore")
    public ApiResponse<RestoreVerificationDto> verifyRestore(@PathVariable Long id) {
        return ApiResponse.success(restoreService.verify(id));
    }

    @PostMapping("/{id}/rehearse")
    public ApiResponse<RestoreRehearsalDto> rehearse(@PathVariable Long id) {
        return ApiResponse.success(restoreService.rehearse(id),
                "Restore rehearsal completed", "تم اختبار الاستعادة");
    }

    @PostMapping("/purge")
    public ApiResponse<PurgeResultDto> purge(@RequestParam(defaultValue = "true") boolean dryRun,
                                             Authentication authentication) {
        String username = authentication == null ? "SYSTEM" : authentication.getName();
        PurgeResultDto result = retentionService.purge(dryRun, username);
        return ApiResponse.success(result, "Retention purge executed", result.messageAr());
    }

    @GetMapping("/settings")
    public ApiResponse<BackupSettingsDto> settings() {
        return ApiResponse.success(settingsService.getSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<BackupSettingsDto> updateSettings(@RequestBody BackupSettingsDto request, Authentication authentication) {
        String username = authentication == null ? "SYSTEM" : authentication.getName();
        return ApiResponse.success(settingsService.update(request, username), "Backup settings updated", "تم حفظ إعدادات النسخ الاحتياطي");
    }
}
