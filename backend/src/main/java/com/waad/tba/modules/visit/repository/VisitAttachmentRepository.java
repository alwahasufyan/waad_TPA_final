package com.waad.tba.modules.visit.repository;

import com.waad.tba.modules.visit.entity.VisitAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Visit Attachment Repository
 */
@Repository
public interface VisitAttachmentRepository extends JpaRepository<VisitAttachment, Long> {
    
    /**
     * Find all attachments for a specific visit
     * 
     * @param visitId Visit ID
     * @return List of attachments
     */
    List<VisitAttachment> findByVisitId(Long visitId);
    
    /**
     * Count attachments for a specific visit
     * 
     * @param visitId Visit ID
     * @return Number of attachments
     */
    long countByVisitId(Long visitId);
    
    /**
     * Delete all attachments for a specific visit
     * 
     * @param visitId Visit ID
     */
    void deleteByVisitId(Long visitId);
}
