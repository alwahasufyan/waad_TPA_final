package com.waad.tba.modules.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.member.entity.MemberImportError;
import com.waad.tba.modules.member.entity.MemberImportError.ErrorType;

@Repository
public interface MemberImportErrorRepository extends JpaRepository<MemberImportError, Long> {
    
    /**
     * Find all errors for an import log
     */
    List<MemberImportError> findByImportLogId(Long importLogId);
    
    /**
     * Find errors by import batch ID
     */
    @Query("SELECT e FROM MemberImportError e WHERE e.importLog.importBatchId = :batchId " +
           "ORDER BY e.rowNumber ASC")
    List<MemberImportError> findByImportBatchId(@Param("batchId") String batchId);
    
    /**
     * Find errors by type
     */
    List<MemberImportError> findByImportLogIdAndErrorType(Long importLogId, ErrorType errorType);
    
    /**
     * Count errors by type for an import
     */
    @Query("SELECT e.errorType, COUNT(e) FROM MemberImportError e " +
           "WHERE e.importLog.id = :logId GROUP BY e.errorType")
    List<Object[]> countByErrorType(@Param("logId") Long importLogId);
    
    /**
     * Delete all errors for an import log
     */
    @Modifying
       @Transactional
    @Query("DELETE FROM MemberImportError e WHERE e.importLog.id = :logId")
    void deleteByImportLogId(@Param("logId") Long importLogId);
    
    /**
     * Count errors for import log
     */
    long countByImportLogId(Long importLogId);
}
