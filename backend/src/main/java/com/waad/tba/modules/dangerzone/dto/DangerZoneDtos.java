package com.waad.tba.modules.dangerzone.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class DangerZoneDtos {
    private DangerZoneDtos() {
    }

    /** Whether destructive operations are available in this environment, and why. */
    public record DangerZoneStatusDto(
            boolean enabled,
            String environment,
            boolean productionLike,
            String reasonAr,
            // Per-condition flags so the UI can explain exactly what is missing.
            boolean devFlagEnabled,
            boolean productionFlagEnabled,
            boolean maintenanceMode,
            boolean telegramConfigured,
            boolean otpRequired,
            String restoreConfirmationPhrase,
            String resetConfirmationPhrase,
            String serverRunbookAr
    ) {
    }

    public record RestoreRequest(
            String password,
            String confirmationPhrase,
            String otpCode
    ) {
    }

    public record ResetRequest(
            String password,
            String confirmationPhrase,
            String otpCode,
            Boolean resetMonitoringLogs,
            Boolean resetErrorLogs,
            Boolean resetBackupMetadata
    ) {
    }

    public record OtpSendRequest(
            String operation
    ) {
    }

    public record OtpSendResultDto(
            String operation,
            LocalDateTime expiresAt,
            long validSeconds,
            String messageAr
    ) {
    }

    public record DangerZoneResultDto(
            boolean success,
            String messageAr,
            Long safetyBackupId,
            List<String> performed,
            long durationMs
    ) {
    }
}
