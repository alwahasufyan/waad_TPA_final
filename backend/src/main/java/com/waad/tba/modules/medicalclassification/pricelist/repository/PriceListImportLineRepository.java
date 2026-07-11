package com.waad.tba.modules.medicalclassification.pricelist.repository;

import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceListImportLineRepository extends JpaRepository<PriceListImportLine, Long> {

    Page<PriceListImportLine> findByImportIdOrderByRowNoAsc(Long importId, Pageable pageable);

    Page<PriceListImportLine> findByImportIdAndReviewStatusOrderByRowNoAsc(
            Long importId, PriceListImportLine.ReviewStatus reviewStatus, Pageable pageable);

    List<PriceListImportLine> findByImportIdAndReviewStatus(
            Long importId, PriceListImportLine.ReviewStatus reviewStatus);

    long countByImportIdAndReviewStatus(Long importId, PriceListImportLine.ReviewStatus reviewStatus);

    void deleteByImportId(Long importId);

    /**
     * Critical-queue tab predicate (MC-2). Tab priority for a NEEDS_REVIEW line:
     * DUPLICATE (flag) → GUARD (other flags) → UNKNOWN (no reference match)
     * → LOW_CONFIDENCE (has a match, low score).
     */
    String QUEUE_PREDICATE = """
            l.importId = :importId AND l.reviewStatus = com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine.ReviewStatus.NEEDS_REVIEW AND (
              (:queue = 'DUPLICATE'      AND l.flags LIKE '%DUPLICATE_IN_FILE%')
              OR (:queue = 'GUARD'       AND l.flags IS NOT NULL AND l.flags NOT LIKE '%DUPLICATE_IN_FILE%')
              OR (:queue = 'UNKNOWN'     AND l.flags IS NULL AND (l.referenceMatch IS NULL OR l.referenceMatch = ''))
              OR (:queue = 'LOW_CONFIDENCE' AND l.flags IS NULL AND l.referenceMatch IS NOT NULL AND l.referenceMatch <> '')
            )
            """;

    @Query("SELECT l FROM PriceListImportLine l WHERE " + QUEUE_PREDICATE + " ORDER BY l.rowNo ASC")
    Page<PriceListImportLine> findQueue(@Param("importId") Long importId,
                                        @Param("queue") String queue, Pageable pageable);

    @Query("SELECT COUNT(l) FROM PriceListImportLine l WHERE " + QUEUE_PREDICATE)
    long countQueue(@Param("importId") Long importId, @Param("queue") String queue);
}
