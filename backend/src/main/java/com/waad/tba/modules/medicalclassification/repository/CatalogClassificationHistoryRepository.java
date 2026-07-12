package com.waad.tba.modules.medicalclassification.repository;

import com.waad.tba.modules.medicalclassification.entity.CatalogClassificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogClassificationHistoryRepository extends JpaRepository<CatalogClassificationHistory, Long> {

    @Query("SELECT COUNT(h) FROM CatalogClassificationHistory h WHERE h.importLineId IN "
            + "(SELECT l.id FROM PriceListImportLine l WHERE l.importId = :importId)")
    long countByImportId(@Param("importId") Long importId);

    /** MC-6 Lite: knowledge-inspection endpoint — how many decisions shaped this service. */
    long countByMedicalServiceId(Long medicalServiceId);

    /** MC-6 Lite: knowledge-inspection endpoint — full decision trail for one service. */
    java.util.List<CatalogClassificationHistory> findByMedicalServiceIdOrderByCreatedAtDesc(Long medicalServiceId);
}
