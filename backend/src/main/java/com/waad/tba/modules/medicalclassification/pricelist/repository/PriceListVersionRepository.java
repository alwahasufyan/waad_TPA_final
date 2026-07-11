package com.waad.tba.modules.medicalclassification.pricelist.repository;

import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceListVersionRepository extends JpaRepository<PriceListVersion, Long> {

    List<PriceListVersion> findByContractIdOrderByVersionNoDesc(Long contractId);

    Optional<PriceListVersion> findByContractIdAndStatus(Long contractId, PriceListVersion.Status status);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(v.versionNo) FROM PriceListVersion v WHERE v.contractId = :contractId")
    Optional<Integer> findMaxVersionNoByContractId(@org.springframework.data.repository.query.Param("contractId") Long contractId);

    Optional<PriceListVersion> findFirstBySourceImportIdOrderByIdDesc(Long sourceImportId);
}
