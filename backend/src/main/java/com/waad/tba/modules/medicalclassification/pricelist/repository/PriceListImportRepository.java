package com.waad.tba.modules.medicalclassification.pricelist.repository;

import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PriceListImportRepository extends JpaRepository<PriceListImport, Long> {

    Page<PriceListImport> findByProviderIdOrderByIdDesc(Long providerId, Pageable pageable);

    Page<PriceListImport> findAllByOrderByIdDesc(Pageable pageable);

    /** Idempotency lookup: same provider + same file hash, still non-terminal. */
    @Query("SELECT i FROM PriceListImport i WHERE i.providerId = :providerId " +
           "AND i.fileHash = :fileHash AND i.status NOT IN :terminalStatuses " +
           "ORDER BY i.id DESC")
    Optional<PriceListImport> findActiveDuplicate(@Param("providerId") Long providerId,
                                                  @Param("fileHash") String fileHash,
                                                  @Param("terminalStatuses") Collection<PriceListImport.Status> terminalStatuses);

    boolean existsByContractIdAndStatusIn(Long contractId, Collection<PriceListImport.Status> statuses);
}
