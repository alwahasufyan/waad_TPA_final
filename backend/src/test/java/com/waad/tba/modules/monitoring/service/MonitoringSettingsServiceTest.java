package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.monitoring.dto.MonitoringDtos.MonitoringSettingsDto;
import com.waad.tba.modules.monitoring.entity.SystemMonitoringSettings;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringAlertStateRepository;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringSettingsRepository;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringSettingsServiceTest {

    private static final String EXISTING_TOKEN = "123456789:AAExisting_TokenLongEnough";

    @Mock
    private SystemMonitoringSettingsRepository repository;
    @Mock
    private SystemMonitoringAlertStateRepository alertStateRepository;
    @Mock
    private Environment environment;
    @Mock
    private AuditLogService auditLogService;

    private MonitoringSettingsService service;

    @BeforeEach
    void setUp() {
        service = new MonitoringSettingsService(repository, alertStateRepository, environment, Optional.of(auditLogService));
        lenient().when(alertStateRepository.findTop20ByOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        lenient().when(repository.findById(1L)).thenReturn(Optional.of(existing()));
        lenient().when(repository.save(any(SystemMonitoringSettings.class))).thenAnswer(i -> i.getArgument(0));
    }

    private SystemMonitoringSettings existing() {
        return SystemMonitoringSettings.builder()
                .id(1L)
                .telegramEnabled(true)
                .telegramBotToken(EXISTING_TOKEN)
                .telegramChatId("999")
                .alertEnvironment("test")
                .minIntervalSeconds(300)
                .recoveryEnabled(true)
                .automaticMonitoringEnabled(false)
                .checkIntervalSeconds(300)
                .diskWarningPercent(80)
                .diskCriticalPercent(90)
                .maxBackupAgeHours(72)
                .repeatedErrorThreshold(10)
                .repeatedErrorWindowMinutes(15)
                .alertCooldownSeconds(1800)
                .build();
    }

    private MonitoringSettingsDto dto(String botToken, String chatId) {
        return new MonitoringSettingsDto(
                true, null, null, botToken, chatId, null, "test", 300, true,
                null, null, null, null, null,
                false, 300, 80, 90, 72, 10, 15, 1800,
                null, null, null,
                null, null, null,
                null
        );
    }

    @Test
    @DisplayName("GET returns masked token and tokenConfigured=true, never the real token")
    void getSettingsMasksToken() {
        MonitoringSettingsDto result = service.getSettings();

        assertNull(result.botToken(), "raw botToken must never be returned");
        assertTrue(result.tokenConfigured());
        assertFalse(result.maskedBotToken().contains("AAExisting"), "masked token must not expose the secret body");
        assertFalse(EXISTING_TOKEN.equals(result.maskedBotToken()));
    }

    @Test
    @DisplayName("Saving a new token stores it but the returned DTO does not leak it")
    void updateStoresNewTokenWithoutLeaking() {
        SystemMonitoringSettings existing = existing();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        MonitoringSettingsDto result = service.update(dto("987654321:BBNewTokenLongEnough", "555"), "admin");

        assertEquals("987654321:BBNewTokenLongEnough", existing.getTelegramBotToken());
        assertNull(result.botToken());
        assertTrue(result.tokenConfigured());
        verify(auditLogService).createAuditLog(eq("MONITORING_SETTINGS_UPDATED"), any(), eq(1L), any(), any(), eq("admin"), any(), any());
    }

    @Test
    @DisplayName("Blank botToken on update preserves the previously stored token")
    void updateWithBlankTokenPreservesExisting() {
        SystemMonitoringSettings existing = existing();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(dto(null, "555"), "admin");

        assertEquals(EXISTING_TOKEN, existing.getTelegramBotToken());
    }
}
