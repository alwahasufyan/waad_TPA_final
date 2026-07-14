package com.waad.tba.modules.dangerzone.service;

import com.waad.tba.modules.dangerzone.entity.DangerZoneOtp;
import com.waad.tba.modules.dangerzone.repository.DangerZoneOtpRepository;
import com.waad.tba.modules.monitoring.service.MonitoringSettingsService;
import com.waad.tba.modules.monitoring.service.TelegramAlertService;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Telegram OTP for production danger-zone operations.
 * - 6-digit code, 5-minute expiry, one-time use, max 5 attempts, 60s send cooldown.
 * - Only the SHA-256 hash is persisted; the plaintext code is never stored or logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DangerZoneTelegramOtpService {

    static final int CODE_MIN = 100_000;
    static final int CODE_BOUND = 900_000; // 100000..999999
    static final long EXPIRY_MINUTES = 5;
    static final int MAX_ATTEMPTS = 5;
    static final long SEND_COOLDOWN_SECONDS = 60;

    private final DangerZoneOtpRepository otpRepository;
    private final TelegramAlertService telegramAlertService;
    private final MonitoringSettingsService monitoringSettingsService;
    private final Optional<AuditLogService> auditLogService;
    private final SecureRandom random = new SecureRandom();

    /** Result of requesting an OTP (never contains the code). */
    public record OtpSendResult(String operation, LocalDateTime expiresAt, long validSeconds) {
    }

    public boolean isTelegramConfigured() {
        return monitoringSettingsService.isTelegramConfigured();
    }

    @Transactional
    public OtpSendResult send(String operation, String username, String environment) {
        if (!monitoringSettingsService.isTelegramConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Telegram غير مهيأ. اضبط Bot Token و Chat ID وفعّل التنبيهات أولًا.");
        }
        otpRepository.findTopByUsernameAndOperationOrderByCreatedAtDesc(username, operation).ifPresent(last -> {
            if (last.getCreatedAt() != null
                    && last.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(SEND_COOLDOWN_SECONDS))) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "الرجاء الانتظار قبل طلب رمز تأكيد جديد.");
            }
        });

        String code = String.valueOf(CODE_MIN + random.nextInt(CODE_BOUND));
        LocalDateTime now = LocalDateTime.now();
        DangerZoneOtp otp = otpRepository.save(DangerZoneOtp.builder()
                .operation(operation)
                .username(username)
                .codeHash(hash(code, username, operation))
                .environment(environment)
                .createdAt(now)
                .expiresAt(now.plusMinutes(EXPIRY_MINUTES))
                .attempts(0)
                .consumed(false)
                .build());

        try {
            telegramAlertService.sendMonitoringMessage(buildMessage(code, operation, username, environment));
        } catch (Exception e) {
            // The user will never receive the code — remove the row so cooldown does not block a retry.
            otpRepository.delete(otp);
            log.warn("Danger-zone OTP Telegram send failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذّر إرسال رمز التأكيد عبر Telegram. تحقق من الإعدادات واتصال السيرفر.");
        }

        audit("OTP_REQUESTED", username, "OTP requested for " + operation + " (env=" + environment + ")");
        return new OtpSendResult(operation, otp.getExpiresAt(), EXPIRY_MINUTES * 60);
    }

    /** Verify a code and consume it (one-time). Throws a safe Arabic error on any failure. */
    @Transactional
    public void verifyAndConsume(String operation, String username, String code) {
        DangerZoneOtp otp = otpRepository
                .findTopByUsernameAndOperationAndConsumedFalseOrderByCreatedAtDesc(username, operation)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "لا يوجد رمز تأكيد فعّال. اطلب رمزًا جديدًا عبر Telegram."));

        if (otp.getExpiresAt() == null || otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "انتهت صلاحية رمز التأكيد. اطلب رمزًا جديدًا.");
        }
        if (otp.getAttempts() != null && otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "تجاوزت عدد المحاولات المسموحة. اطلب رمزًا جديدًا.");
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "أدخل رمز Telegram.");
        }

        if (!constantTimeEquals(otp.getCodeHash(), hash(code.trim(), username, operation))) {
            otp.setAttempts((otp.getAttempts() == null ? 0 : otp.getAttempts()) + 1);
            otpRepository.save(otp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "رمز Telegram غير صحيح.");
        }

        otp.setConsumed(true);
        otpRepository.save(otp);
        audit("OTP_VERIFIED", username, "OTP verified for " + operation);
    }

    private String buildMessage(String code, String operation, String username, String environment) {
        return "🔐 رمز تأكيد عملية خطيرة في WAAD: " + code + "\n"
                + "العملية: " + operation + "\n"
                + "المستخدم: " + username + "\n"
                + "البيئة: " + environment + "\n"
                + "الوقت: " + LocalDateTime.now() + "\n"
                + "صالح لمدة " + EXPIRY_MINUTES + " دقائق.\n"
                + "إذا لم تطلب هذا الرمز، أوقف العملية فورًا.";
    }

    private static String hash(String code, String username, String operation) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((code + ":" + username + ":" + operation).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash OTP", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private void audit(String action, String username, String details) {
        auditLogService.ifPresent(service -> {
            try {
                service.createAuditLog(action, "DangerZoneOtp", null, details, null, username, null, null);
            } catch (Exception ignored) {
                // auditing must never break the flow
            }
        });
    }
}
