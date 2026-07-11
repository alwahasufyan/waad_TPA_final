package com.waad.tba.modules.medicalclassification.pricelist.repository;

import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListValidationFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceListValidationFindingRepository extends JpaRepository<PriceListValidationFinding, Long> {

    List<PriceListValidationFinding> findByVersionIdOrderBySeverityAscIdAsc(Long versionId);

    long countByVersionIdAndSeverityAndStatus(Long versionId,
                                              PriceListValidationFinding.Severity severity,
                                              PriceListValidationFinding.Status status);

    void deleteByVersionIdAndStatus(Long versionId, PriceListValidationFinding.Status status);
}
