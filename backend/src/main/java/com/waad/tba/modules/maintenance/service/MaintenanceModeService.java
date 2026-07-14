package com.waad.tba.modules.maintenance.service;

import com.waad.tba.modules.maintenance.entity.SystemMaintenanceMode;
import com.waad.tba.modules.maintenance.repository.SystemMaintenanceModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * A simple, DB-backed maintenance flag. When enabled it is used as a hard precondition for
 * production danger-zone operations, and (via MaintenanceModeFilter) blocks mutating requests
 * from non-admin users.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceModeService {

    private static final long ID = 1L;

    private final SystemMaintenanceModeRepository repository;
    private volatile Boolean cached;

    public SystemMaintenanceMode getOrCreate() {
        return repository.findById(ID).orElseGet(() -> repository.save(SystemMaintenanceMode.builder()
                .id(ID).enabled(false).updatedAt(LocalDateTime.now()).build()));
    }

    public boolean isEnabled() {
        try {
            boolean value = Boolean.TRUE.equals(getOrCreate().getEnabled());
            cached = value;
            return value;
        } catch (Exception e) {
            // If the DB is unreachable, fall back to the last known value (default: not in maintenance).
            return Boolean.TRUE.equals(cached);
        }
    }

    @Transactional
    public SystemMaintenanceMode set(boolean enabled, String reason, String username) {
        SystemMaintenanceMode state = getOrCreate();
        state.setEnabled(enabled);
        state.setReason(reason);
        state.setUpdatedBy(username);
        state.setUpdatedAt(LocalDateTime.now());
        SystemMaintenanceMode saved = repository.save(state);
        cached = enabled;
        return saved;
    }
}
