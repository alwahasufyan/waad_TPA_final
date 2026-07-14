package com.waad.tba.modules.dangerzone.service;

import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.DangerZoneResultDto;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.ResetRequest;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.RestoreRequest;
import com.waad.tba.modules.errorlog.repository.SystemErrorLogRepository;
import com.waad.tba.modules.maintenance.service.MaintenanceModeService;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringAlertStateRepository;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringErrorEventRepository;
import com.waad.tba.modules.monitoring.service.TelegramAlertService;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupJobDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.BackupType;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import com.waad.tba.modules.systembackup.service.BackupService;
import com.waad.tba.modules.systembackup.service.BackupSettingsService;
import com.waad.tba.modules.systembackup.service.BackupStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DangerZoneServiceTest {

    private static final String RESET_PHRASE = "RESET WAAD DATA";
    private static final String RESTORE_PHRASE = "RESTORE WAAD DATABASE";

    @Mock private Environment environment;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private BackupService backupService;
    @Mock private BackupSettingsService backupSettingsService;
    @Mock private BackupStorageService storageService;
    @Mock private SystemBackupJobRepository jobRepository;
    @Mock private SystemErrorLogRepository errorLogRepository;
    @Mock private SystemMonitoringErrorEventRepository monitoringErrorRepository;
    @Mock private SystemMonitoringAlertStateRepository monitoringAlertStateRepository;
    @Mock private MaintenanceModeService maintenanceModeService;
    @Mock private DangerZoneTelegramOtpService otpService;
    @Mock private TelegramAlertService telegramAlertService;
    @Mock private AuditLogService auditLogService;

    private DangerZoneService service;

    @BeforeEach
    void setUp() {
        service = new DangerZoneService(environment, authenticationManager, backupService, backupSettingsService,
                storageService, jobRepository, errorLogRepository, monitoringErrorRepository,
                monitoringAlertStateRepository, maintenanceModeService, otpService, telegramAlertService,
                Optional.of(auditLogService));
    }

    private void profile(String... profiles) {
        lenient().when(environment.getActiveProfiles()).thenReturn(profiles);
    }

    private void devFlag(String value) {
        lenient().when(environment.getProperty("WAAD_DANGER_ZONE_ENABLED", "false")).thenReturn(value);
    }

    private void prodFlag(String value) {
        lenient().when(environment.getProperty("WAAD_PRODUCTION_DANGER_ZONE_ENABLED", "false")).thenReturn(value);
    }

    private ResetRequest reset(String phrase) {
        return new ResetRequest("pw", phrase, "111111", true, false, false);
    }

    private BackupJobDto safetyJob(BackupStatus status) {
        return new BackupJobDto(99L, BackupType.FULL_SYSTEM, status, "s.zip", 1L, "c",
                null, "admin", LocalDateTime.now(), LocalDateTime.now(), 1L, null, "prod", null, false, "/p", "zip", null);
    }

    // ===================== dev behaviour (unchanged) =====================

    @Test
    @DisplayName("Dev without the env flag is locked and rejects reset with 403")
    void devWithoutFlagIsLocked() {
        profile("dev");
        devFlag("false");

        assertFalse(service.status().enabled());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reset(reset(RESET_PHRASE), "admin"));
        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(backupService);
    }

    @Test
    @DisplayName("Dev wrong admin password is rejected with 400")
    void devWrongPasswordRejected() {
        profile("dev");
        devFlag("true");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reset(reset(RESET_PHRASE), "admin"));
        assertEquals(400, ex.getStatusCode().value());
        verifyNoInteractions(backupService);
    }

    @Test
    @DisplayName("Dev wrong confirmation phrase is rejected with 400")
    void devWrongPhraseRejected() {
        profile("dev");
        devFlag("true");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reset(reset("not the phrase"), "admin"));
        assertEquals(400, ex.getStatusCode().value());
        verifyNoInteractions(backupService);
    }

    @Test
    @DisplayName("Dev reset happy path takes a mandatory backup and clears selected tables (no OTP)")
    void devResetHappyPath() {
        profile("dev");
        devFlag("true");
        when(backupService.create(any(), anyString(), anyString())).thenReturn(safetyJob(BackupStatus.SUCCESS));

        DangerZoneResultDto result = service.reset(
                new ResetRequest("pw", RESET_PHRASE, null, true, true, false), "admin");

        assertTrue(result.success());
        assertEquals(Long.valueOf(99L), result.safetyBackupId());
        verify(backupService).create(eq(BackupType.FULL_SYSTEM), anyString(), eq("admin"));
        verify(errorLogRepository).deleteAllInBatch();
        // Dev path must NOT require an OTP.
        verifyNoInteractions(otpService);
    }

    @Test
    @DisplayName("Failure of the mandatory backup blocks the operation (409) and deletes nothing")
    void mandatoryBackupFailureBlocks() {
        profile("dev");
        devFlag("true");
        when(backupService.create(any(), anyString(), anyString())).thenReturn(safetyJob(BackupStatus.FAILED));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reset(new ResetRequest("pw", RESET_PHRASE, null, true, false, false), "admin"));
        assertEquals(409, ex.getStatusCode().value());
        verify(errorLogRepository, never()).deleteAllInBatch();
    }

    // ===================== production gates =====================

    @Test
    @DisplayName("Production is locked when WAAD_PRODUCTION_DANGER_ZONE_ENABLED=false")
    void productionLockedWithoutProdFlag() {
        profile("prod");
        prodFlag("false");
        devFlag("true"); // dev flag must never open production

        assertFalse(service.status().enabled());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reset(reset(RESET_PHRASE), "admin"));
        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(backupService);
        verify(otpService, never()).verifyAndConsume(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Production rejects when maintenance mode is off")
    void productionRejectsWithoutMaintenance() {
        profile("prod");
        prodFlag("true");
        when(maintenanceModeService.isEnabled()).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reset(reset(RESET_PHRASE), "admin"));
        assertEquals(409, ex.getStatusCode().value());
        verifyNoInteractions(backupService);
    }

    @Test
    @DisplayName("Production rejects when Telegram is not configured")
    void productionRejectsWithoutTelegram() {
        profile("prod");
        prodFlag("true");
        when(maintenanceModeService.isEnabled()).thenReturn(true);
        when(otpService.isTelegramConfigured()).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reset(reset(RESET_PHRASE), "admin"));
        assertEquals(400, ex.getStatusCode().value());
        verifyNoInteractions(backupService);
    }

    @Test
    @DisplayName("Production happy path consumes OTP, takes mandatory backup, then resets")
    void productionHappyPath() {
        profile("prod");
        prodFlag("true");
        when(maintenanceModeService.isEnabled()).thenReturn(true);
        when(otpService.isTelegramConfigured()).thenReturn(true);
        when(backupService.create(any(), anyString(), anyString())).thenReturn(safetyJob(BackupStatus.SUCCESS));

        DangerZoneResultDto result = service.reset(
                new ResetRequest("pw", RESET_PHRASE, "482913", false, true, false), "admin");

        assertTrue(result.success());
        verify(otpService).verifyAndConsume(eq("RESET"), eq("admin"), eq("482913"));
        verify(backupService).create(eq(BackupType.FULL_SYSTEM), anyString(), eq("admin"));
        verify(errorLogRepository).deleteAllInBatch();
    }

    @Test
    @DisplayName("Production restore rejected (403) before OTP when prod flag is off")
    void productionRestoreLocked() {
        profile("prod");
        prodFlag("false");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.restore(1L, new RestoreRequest("pw", RESTORE_PHRASE, "111111"), "admin"));
        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(otpService, backupService);
    }
}
