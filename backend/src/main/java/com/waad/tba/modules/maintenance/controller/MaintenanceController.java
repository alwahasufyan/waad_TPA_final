package com.waad.tba.modules.maintenance.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.maintenance.entity.SystemMaintenanceMode;
import com.waad.tba.modules.maintenance.service.MaintenanceModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/system/maintenance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class MaintenanceController {

    private final MaintenanceModeService maintenanceModeService;

    public record MaintenanceStatusDto(boolean enabled, String reason, String updatedBy, LocalDateTime updatedAt) {
    }

    public record MaintenanceRequest(Boolean enabled, String reason) {
    }

    @GetMapping("/status")
    public ApiResponse<MaintenanceStatusDto> status() {
        SystemMaintenanceMode state = maintenanceModeService.getOrCreate();
        return ApiResponse.success(new MaintenanceStatusDto(
                Boolean.TRUE.equals(state.getEnabled()), state.getReason(), state.getUpdatedBy(), state.getUpdatedAt()));
    }

    @PostMapping
    public ApiResponse<MaintenanceStatusDto> set(@RequestBody MaintenanceRequest request, Authentication authentication) {
        String username = authentication == null ? "SYSTEM" : authentication.getName();
        boolean enabled = request != null && Boolean.TRUE.equals(request.enabled());
        String reason = request == null ? null : request.reason();
        SystemMaintenanceMode state = maintenanceModeService.set(enabled, reason, username);
        String msgAr = enabled ? "تم تفعيل وضع الصيانة" : "تم إيقاف وضع الصيانة";
        return ApiResponse.success(new MaintenanceStatusDto(
                Boolean.TRUE.equals(state.getEnabled()), state.getReason(), state.getUpdatedBy(), state.getUpdatedAt()),
                "Maintenance mode updated", msgAr);
    }
}
