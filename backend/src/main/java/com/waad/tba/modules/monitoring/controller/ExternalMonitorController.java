package com.waad.tba.modules.monitoring.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.monitoring.service.MonitoringSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public heartbeat endpoint for the standalone external monitor (tools/external-monitor).
 * The external monitor runs outside this backend so it can alert even when the backend is down;
 * it also pings this endpoint so the admin UI can show that an external watchdog is alive.
 *
 * Auth: intentionally NOT behind login (the monitor has no session). If
 * WAAD_MONITOR_HEARTBEAT_TOKEN is configured, a matching X-Monitor-Token header is required.
 */
@RestController
@RequestMapping("/api/v1/system/monitoring")
@RequiredArgsConstructor
public class ExternalMonitorController {

    private final MonitoringSettingsService settingsService;
    private final Environment environment;

    public record HeartbeatRequest(String source, String status) {
    }

    @PostMapping("/external-heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @RequestBody(required = false) HeartbeatRequest request,
            @RequestHeader(value = "X-Monitor-Token", required = false) String token) {
        String expected = environment.getProperty("WAAD_MONITOR_HEARTBEAT_TOKEN");
        if (expected != null && !expected.isBlank() && !expected.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid monitor token"));
        }
        String source = request == null ? null : request.source();
        String status = request == null ? null : request.status();
        settingsService.recordExternalHeartbeat(source, status);
        return ResponseEntity.ok(ApiResponse.success(null, "Heartbeat recorded", "تم استلام نبضة المراقب الخارجي"));
    }
}
