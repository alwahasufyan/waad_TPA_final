package com.waad.tba.modules.preauthorization.repository;

import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for PreAuthorization Attachments
 */
@Repository
public interface PreAuthorizationAttachmentRepository extends JpaRepository<PreAuthorizationAttachment, Long> {

    /**
     * Find all attachments for a pre-authorization
     */
    List<PreAuthorizationAttachment> findByPreAuthorizationId(Long preAuthorizationId);

    /**
     * Count attachments for a pre-authorization
     */
    long countByPreAuthorizationId(Long preAuthorizationId);

    /**
     * Delete all attachments for a pre-authorization
     */
    void deleteByPreAuthorizationId(Long preAuthorizationId);
}
