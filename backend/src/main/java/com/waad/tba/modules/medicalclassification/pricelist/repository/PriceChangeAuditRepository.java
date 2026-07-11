package com.waad.tba.modules.medicalclassification.pricelist.repository;

import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceChangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceChangeAuditRepository extends JpaRepository<PriceChangeAudit, Long> {

    List<PriceChangeAudit> findByVersionIdOrderByIdDesc(Long versionId);

    List<PriceChangeAudit> findByContractIdOrderByIdDesc(Long contractId);

    long countByVersionId(Long versionId);
}
