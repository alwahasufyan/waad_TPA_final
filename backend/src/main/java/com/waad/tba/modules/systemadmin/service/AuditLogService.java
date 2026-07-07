package com.waad.tba.modules.systemadmin.service;

import com.waad.tba.modules.systemadmin.entity.AuditLog;
import com.waad.tba.modules.systemadmin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Audit Log Service
 * Phase 2 - System Administration
 * 
 * Manages audit trail for all system activities
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Get all audit logs (paginated)
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAllAuditLogs(Pageable pageable) {
        log.info("Fetching all audit logs (page {})", pageable.getPageNumber());
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Get audit log by ID
     */
    @Transactional(readOnly = true)
    public AuditLog getAuditLogById(Long id) {
        log.info("Fetching audit log by ID: {}", id);
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found with ID: " + id));
    }

    /**
     * Get audit logs by user ID
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByUser(Long userId, Pageable pageable) {
        log.info("Fetching audit logs for user: {}", userId);
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    /**
     * Get audit logs by entity
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByEntity(String entityType, Long entityId, Pageable pageable) {
        log.info("Fetching audit logs for entity: {} with ID: {}", entityType, entityId);
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                entityType, entityId, pageable);
    }

    /**
     * Get audit logs by action type
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByAction(String action, Pageable pageable) {
        log.info("Fetching audit logs for action: {}", action);
        return auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
    }

    /**
     * Get audit logs within time range
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByTimeRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        log.info("Fetching audit logs between {} and {}", start, end);
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable);
    }

    /**
     * Get all distinct action types
     */
    @Transactional(readOnly = true)
    public List<String> getAllActionTypes() {
        log.info("Fetching all distinct action types");
        return auditLogRepository.findDistinctActions();
    }

    /**
     * Get all distinct entity types
     */
    @Transactional(readOnly = true)
    public List<String> getAllEntityTypes() {
        log.info("Fetching all distinct entity types");
        return auditLogRepository.findDistinctEntityTypes();
    }

    /**
     * Create audit log entry
     */
    @Transactional
    public void createAuditLog(String action, String entityType, Long entityId, 
                               String details, Long userId, String username, 
                               String ipAddress, String userAgent) {
        AuditLog auditLog = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .userId(userId)
                .username(username)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit log created: {} by {}", action, username);
    }

    /**
     * Get audit log count by user
     */
    @Transactional(readOnly = true)
    public Long getAuditLogCountByUser(Long userId) {
        return auditLogRepository.countByUserId(userId);
    }

    /**
     * Get audit log count by action
     */
    @Transactional(readOnly = true)
    public Long getAuditLogCountByAction(String action) {
        return auditLogRepository.countByAction(action);
    }
}
