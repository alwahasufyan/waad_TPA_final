package com.waad.tba.modules.dangerzone.service;

import com.waad.tba.modules.dangerzone.entity.DangerZoneOtp;
import com.waad.tba.modules.dangerzone.repository.DangerZoneOtpRepository;
import com.waad.tba.modules.monitoring.service.MonitoringSettingsService;
import com.waad.tba.modules.monitoring.service.TelegramAlertService;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DangerZoneTelegramOtpServiceTest {

    @Mock private DangerZoneOtpRepository otpRepository;
    @Mock private TelegramAlertService telegramAlertService;
    @Mock private MonitoringSettingsService monitoringSettingsService;
    @Mock private AuditLogService auditLogService;

    private DangerZoneTelegramOtpService service;

    @BeforeEach
    void setUp() {
        service = new DangerZoneTelegramOtpService(otpRepository, telegramAlertService,
                monitoringSettingsService, Optional.of(auditLogService));
    }

    private static String hash(String code, String user, String op) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest((code + ":" + user + ":" + op).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private DangerZoneOtp otp(String codeHash, LocalDateTime expiresAt, int attempts) {
        return DangerZoneOtp.builder()
                .id(1L).operation("RESET").username("admin").codeHash(codeHash)
                .createdAt(LocalDateTime.now()).expiresAt(expiresAt).attempts(attempts).consumed(false).build();
    }

    @Test
    @DisplayName("send is rejected when Telegram is not configured")
    void sendRejectedWhenTelegramNotConfigured() {
        when(monitoringSettingsService.isTelegramConfigured()).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.send("RESET", "admin", "prod"));
        assertEquals(400, ex.getStatusCode().value());
        verify(otpRepository, never()).save(any());
    }

    @Test
    @DisplayName("send is rejected within the cooldown window")
    void sendRejectedWithinCooldown() {
        when(monitoringSettingsService.isTelegramConfigured()).thenReturn(true);
        DangerZoneOtp recent = DangerZoneOtp.builder().createdAt(LocalDateTime.now()).build();
        when(otpRepository.findTopByUsernameAndOperationOrderByCreatedAtDesc("admin", "RESET"))
                .thenReturn(Optional.of(recent));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.send("RESET", "admin", "prod"));
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("send stores a SHA-256 hash (not the plaintext code) and sends a Telegram message")
    void sendStoresHashNotPlaintext() {
        when(monitoringSettingsService.isTelegramConfigured()).thenReturn(true);
        when(otpRepository.findTopByUsernameAndOperationOrderByCreatedAtDesc("admin", "RESET"))
                .thenReturn(Optional.empty());
        when(otpRepository.save(any(DangerZoneOtp.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.send("RESET", "admin", "prod");

        ArgumentCaptor<DangerZoneOtp> captor = ArgumentCaptor.forClass(DangerZoneOtp.class);
        verify(otpRepository).save(captor.capture());
        DangerZoneOtp saved = captor.getValue();
        assertTrue(saved.getCodeHash().matches("[0-9a-f]{64}"), "code must be stored as a 64-char SHA-256 hex hash");
        assertFalse(Boolean.TRUE.equals(saved.getConsumed()));
        assertEquals(0, saved.getAttempts());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
        verify(telegramAlertService).sendMonitoringMessage(anyString());
        assertNotNull(result.expiresAt());
    }

    @Test
    @DisplayName("verify with no active OTP is rejected")
    void verifyNoActiveRejected() {
        when(otpRepository.findTopByUsernameAndOperationAndConsumedFalseOrderByCreatedAtDesc("admin", "RESET"))
                .thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.verifyAndConsume("RESET", "admin", "123456"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("verify with an expired OTP is rejected")
    void verifyExpiredRejected() throws Exception {
        DangerZoneOtp expired = otp(hash("123456", "admin", "RESET"), LocalDateTime.now().minusMinutes(1), 0);
        when(otpRepository.findTopByUsernameAndOperationAndConsumedFalseOrderByCreatedAtDesc("admin", "RESET"))
                .thenReturn(Optional.of(expired));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.verifyAndConsume("RESET", "admin", "123456"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("verify with a wrong code increments attempts and is rejected")
    void verifyWrongIncrementsAttempts() throws Exception {
        DangerZoneOtp stored = otp(hash("123456", "admin", "RESET"), LocalDateTime.now().plusMinutes(5), 0);
        when(otpRepository.findTopByUsernameAndOperationAndConsumedFalseOrderByCreatedAtDesc("admin", "RESET"))
                .thenReturn(Optional.of(stored));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.verifyAndConsume("RESET", "admin", "000000"));
        assertEquals(400, ex.getStatusCode().value());
        assertEquals(1, stored.getAttempts());
        verify(otpRepository).save(stored);
    }

    @Test
    @DisplayName("verify with too many attempts is rejected with 429")
    void verifyTooManyAttempts() throws Exception {
        DangerZoneOtp stored = otp(hash("123456", "admin", "RESET"), LocalDateTime.now().plusMinutes(5), 5);
        when(otpRepository.findTopByUsernameAndOperationAndConsumedFalseOrderByCreatedAtDesc("admin", "RESET"))
                .thenReturn(Optional.of(stored));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.verifyAndConsume("RESET", "admin", "123456"));
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("verify with the correct code consumes it (one-time use)")
    void verifyCorrectConsumes() throws Exception {
        DangerZoneOtp stored = otp(hash("482913", "admin", "RESET"), LocalDateTime.now().plusMinutes(5), 0);
        when(otpRepository.findTopByUsernameAndOperationAndConsumedFalseOrderByCreatedAtDesc("admin", "RESET"))
                .thenReturn(Optional.of(stored));
        when(otpRepository.save(any(DangerZoneOtp.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.verifyAndConsume("RESET", "admin", "482913"));
        assertTrue(stored.getConsumed());
        verify(otpRepository).save(stored);
    }
}
