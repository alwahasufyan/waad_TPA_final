package com.waad.tba.modules.systemadmin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.systemadmin.dto.ModuleAccessDto;
import com.waad.tba.modules.systemadmin.entity.ModuleAccess;
import com.waad.tba.modules.systemadmin.repository.ModuleAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Module Access Service
 * Phase 2 - System Administration
 * 
 * Manages module access control configurations (SUPER_ADMIN only)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModuleAccessService {

    private final ModuleAccessRepository moduleAccessRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Get all modules
     */
    @Transactional(readOnly = true)
    public List<ModuleAccessDto> getAllModules() {
        log.info("Fetching all module access configurations");
        return moduleAccessRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get module by ID
     */
    @Transactional(readOnly = true)
    public ModuleAccessDto getModuleById(Long id) {
        log.info("Fetching module by ID: {}", id);
        ModuleAccess module = moduleAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with ID: " + id));
        return toDto(module);
    }

    /**
     * Get module by key
     */
    @Transactional(readOnly = true)
    public ModuleAccessDto getModuleByKey(String moduleKey) {
        log.info("Fetching module by key: {}", moduleKey);
        ModuleAccess module = moduleAccessRepository.findByModuleKey(moduleKey)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with key: " + moduleKey));
        return toDto(module);
    }

    /**
     * Create new module
     */
    @Transactional
    public ModuleAccessDto createModule(ModuleAccessDto dto, String createdBy) {
        log.info("Creating module: {} by {}", dto.getModuleKey(), createdBy);

        // Validation
        if (moduleAccessRepository.existsByModuleKey(dto.getModuleKey())) {
            throw new IllegalArgumentException("Module key already exists: " + dto.getModuleKey());
        }

        // Build module
        ModuleAccess module = ModuleAccess.builder()
                .moduleKey(dto.getModuleKey())
                .moduleName(dto.getModuleName())
                .description(dto.getDescription())
                .allowedRoles(toJson(dto.getAllowedRoles()))
                .requiredPermissions(toJson(dto.getRequiredPermissions()))
                .featureFlagKey(dto.getFeatureFlagKey())
                .active(dto.getActive())
                .build();

        ModuleAccess saved = moduleAccessRepository.save(module);

        // Audit log
        auditLogService.createAuditLog(
                "MODULE_CREATED",
                "ModuleAccess",
                saved.getId(),
                String.format("Created module: %s (%s)", saved.getModuleName(), saved.getModuleKey()),
                null,
                createdBy,
                null,
                null
        );

        log.info("Module created successfully: {}", saved.getModuleKey());
        return toDto(saved);
    }

    /**
     * Update module
     */
    @Transactional
    public ModuleAccessDto updateModule(Long id, ModuleAccessDto dto, String updatedBy) {
        log.info("Updating module ID: {} by {}", id, updatedBy);

        ModuleAccess module = moduleAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with ID: " + id));

        // Check key uniqueness (if changed)
        if (!module.getModuleKey().equals(dto.getModuleKey()) && 
            moduleAccessRepository.existsByModuleKey(dto.getModuleKey())) {
            throw new IllegalArgumentException("Module key already exists: " + dto.getModuleKey());
        }

        // Update fields
        module.setModuleKey(dto.getModuleKey());
        module.setModuleName(dto.getModuleName());
        module.setDescription(dto.getDescription());
        module.setAllowedRoles(toJson(dto.getAllowedRoles()));
        module.setRequiredPermissions(toJson(dto.getRequiredPermissions()));
        module.setFeatureFlagKey(dto.getFeatureFlagKey());
        module.setActive(dto.getActive());

        ModuleAccess updated = moduleAccessRepository.save(module);

        // Audit log
        auditLogService.createAuditLog(
                "MODULE_UPDATED",
                "ModuleAccess",
                updated.getId(),
                String.format("Updated module: %s", updated.getModuleKey()),
                null,
                updatedBy,
                null,
                null
        );

        log.info("Module updated successfully: {}", updated.getModuleKey());
        return toDto(updated);
    }

    /**
     * Delete module
     */
    @Transactional
    public void deleteModule(Long id, String deletedBy) {
        log.info("Deleting module ID: {} by {}", id, deletedBy);

        ModuleAccess module = moduleAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with ID: " + id));

        String moduleKey = module.getModuleKey();
        moduleAccessRepository.delete(module);

        // Audit log
        auditLogService.createAuditLog(
                "MODULE_DELETED",
                "ModuleAccess",
                id,
                String.format("Deleted module: %s", moduleKey),
                null,
                deletedBy,
                null,
                null
        );

        log.info("Module deleted successfully: {}", moduleKey);
    }

    /**
     * Update module access (roles & permissions)
     */
    @Transactional
    public ModuleAccessDto updateModuleAccess(Long id, List<String> allowedRoles, 
                                             List<String> requiredPermissions, String updatedBy) {
        log.info("Updating access for module ID: {} by {}", id, updatedBy);

        ModuleAccess module = moduleAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with ID: " + id));

        module.setAllowedRoles(toJson(allowedRoles));
        module.setRequiredPermissions(toJson(requiredPermissions));

        ModuleAccess updated = moduleAccessRepository.save(module);

        // Audit log
        auditLogService.createAuditLog(
                "MODULE_ACCESS_UPDATED",
                "ModuleAccess",
                updated.getId(),
                String.format("Updated access for module: %s (roles: %d, permissions: %d)",
                        updated.getModuleKey(), allowedRoles.size(), requiredPermissions.size()),
                null,
                updatedBy,
                null,
                null
        );

        return toDto(updated);
    }

    /**
     * Get modules accessible by specific role
     */
    @Transactional(readOnly = true)
    public List<ModuleAccessDto> getModulesForRole(String roleName) {
        log.info("Fetching modules accessible by role: {}", roleName);
        return moduleAccessRepository.findByAllowedRolesContaining(roleName).stream()
                .filter(ModuleAccess::getActive)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get active modules
     */
    @Transactional(readOnly = true)
    public List<ModuleAccessDto> getActiveModules() {
        log.info("Fetching active modules");
        return moduleAccessRepository.findByActiveTrue().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get modules by feature flag
     */
    @Transactional(readOnly = true)
    public List<ModuleAccessDto> getModulesByFeatureFlag(String featureFlagKey) {
        log.info("Fetching modules with feature flag: {}", featureFlagKey);
        return moduleAccessRepository.findByFeatureFlagKey(featureFlagKey).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Toggle module active status
     */
    @Transactional
    public ModuleAccessDto toggleModuleStatus(Long id, boolean active, String updatedBy) {
        log.info("Toggling module ID: {} to {} by {}", id, active, updatedBy);

        ModuleAccess module = moduleAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found with ID: " + id));

        module.setActive(active);
        ModuleAccess updated = moduleAccessRepository.save(module);

        // Audit log
        auditLogService.createAuditLog(
                "MODULE_STATUS_TOGGLED",
                "ModuleAccess",
                updated.getId(),
                String.format("Module %s %s", updated.getModuleKey(), active ? "activated" : "deactivated"),
                null,
                updatedBy,
                null,
                null
        );

        return toDto(updated);
    }

    // Helper methods
    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("Error serializing list to JSON", e);
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error deserializing JSON to list", e);
            return new ArrayList<>();
        }
    }

    private ModuleAccessDto toDto(ModuleAccess module) {
        return ModuleAccessDto.builder()
                .id(module.getId())
                .moduleKey(module.getModuleKey())
                .moduleName(module.getModuleName())
                .description(module.getDescription())
                .allowedRoles(fromJson(module.getAllowedRoles()))
                .requiredPermissions(fromJson(module.getRequiredPermissions()))
                .featureFlagKey(module.getFeatureFlagKey())
                .active(module.getActive())
                .createdAt(module.getCreatedAt())
                .updatedAt(module.getUpdatedAt())
                .build();
    }
}
