package com.waad.tba.modules.claim.repository;

import com.waad.tba.modules.claim.entity.ClaimLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * CLAIM-REVIEW-SPLIT-2C: standalone repository for {@link ClaimLine}.
 *
 * Deliberately separate from {@code ClaimRepository} — fetching and saving a
 * line through THIS repository never loads/touches the parent {@code Claim}
 * entity, so {@code Claim}'s own {@code @PreUpdate} hook
 * ({@code calculateFields()}, which recomputes
 * approvedAmount/netProviderAmount/patientCoPay for non-finalized claims)
 * never fires. This is what lets a line-level reviewer decision be persisted
 * without side-effecting claim-level financial totals — see
 * {@code ClaimService.submitLineDecision} and the CLAIM-REVIEW-SPLIT-2C
 * report for the full reasoning.
 */
public interface ClaimLineRepository extends JpaRepository<ClaimLine, Long> {

    @Query("SELECT l FROM ClaimLine l WHERE l.id = :lineId AND l.claim.id = :claimId")
    Optional<ClaimLine> findByIdAndClaimId(@Param("lineId") Long lineId, @Param("claimId") Long claimId);
}
