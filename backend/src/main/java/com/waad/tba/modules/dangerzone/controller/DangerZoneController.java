package com.waad.tba.modules.dangerzone.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.DangerZoneResultDto;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.DangerZoneStatusDto;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.OtpSendRequest;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.OtpSendResultDto;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.ResetRequest;
import com.waad.tba.modules.dangerzone.dto.DangerZoneDtos.RestoreRequest;
import com.waad.tba.modules.dangerzone.service.DangerZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Destructive dev-only operations. SUPER_ADMIN only. All operations additionally require
 * the danger zone to be enabled (non-prod + env flag), verified inside the service.
 */
@RestController
@RequestMapping("/api/v1/system/danger-zone")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class DangerZoneController {

    private final DangerZoneService dangerZoneService;

    @GetMapping("/status")
    public ApiResponse<DangerZoneStatusDto> status() {
        return ApiResponse.success(dangerZoneService.status());
    }

    @PostMapping("/otp/send")
    public ApiResponse<OtpSendResultDto> sendOtp(@RequestBody OtpSendRequest request, Authentication authentication) {
        String username = authentication == null ? "SYSTEM" : authentication.getName();
        String operation = request == null ? null : request.operation();
        OtpSendResultDto result = dangerZoneService.sendOtp(operation, username);
        return ApiResponse.success(result, "OTP sent", result.messageAr());
    }

    @PostMapping("/restore/{backupId}")
    public ApiResponse<DangerZoneResultDto> restore(@PathVariable Long backupId,
                                                    @RequestBody RestoreRequest request,
                                                    Authentication authentication) {
        String username = authentication == null ? "SYSTEM" : authentication.getName();
        return ApiResponse.success(dangerZoneService.restore(backupId, request, username),
                "Dev restore completed", "تمت الاستعادة");
    }

    @PostMapping("/reset")
    public ApiResponse<DangerZoneResultDto> reset(@RequestBody ResetRequest request,
                                                  Authentication authentication) {
        String username = authentication == null ? "SYSTEM" : authentication.getName();
        return ApiResponse.success(dangerZoneService.reset(request, username),
                "Dev reset completed", "تمت إعادة التهيئة المحدودة");
    }
}
