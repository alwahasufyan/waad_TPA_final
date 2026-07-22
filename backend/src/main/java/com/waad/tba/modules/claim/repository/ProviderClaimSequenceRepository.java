package com.waad.tba.modules.claim.repository;

import com.waad.tba.modules.claim.entity.ProviderClaimSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * CLAIM-NUMBERING-1: repository for the per-provider claim reference counter.
 */
public interface ProviderClaimSequenceRepository extends JpaRepository<ProviderClaimSequence, Long> {

    /**
     * Ensures a sequence row exists for this provider. Safe under concurrency:
     * two transactions racing to create the first row for a brand-new provider
     * will not conflict — the loser's insert is a no-op (ON CONFLICT DO NOTHING),
     * and both then proceed to lock the same, now-existing row via
     * {@link #findByIdForUpdate(Long)}.
     */
    @Modifying
    @Query(value = "INSERT INTO provider_claim_sequences (provider_id, next_value, updated_at) "
            + "VALUES (:providerId, 1, CURRENT_TIMESTAMP) ON CONFLICT (provider_id) DO NOTHING", nativeQuery = true)
    void ensureRowExists(@Param("providerId") Long providerId);

    /**
     * Locks the provider's sequence row for the duration of the calling
     * transaction (PESSIMISTIC_WRITE = SELECT ... FOR UPDATE), so the
     * read-increment-write cycle in ClaimReferenceService is atomic across
     * concurrent callers for the same provider.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProviderClaimSequence s WHERE s.providerId = :providerId")
    Optional<ProviderClaimSequence> findByIdForUpdate(@Param("providerId") Long providerId);
}
