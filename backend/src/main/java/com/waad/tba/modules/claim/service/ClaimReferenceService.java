package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.claim.entity.ProviderClaimSequence;
import com.waad.tba.modules.claim.repository.ProviderClaimSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CLAIM-NUMBERING-1: generates the official, human-readable, sequential claim
 * reference — format {@code CLM-P{providerId, 3 digits}-{sequence, 6 digits}},
 * e.g. {@code CLM-P001-000001}.
 *
 * Concurrency-safe: {@link #generateNextReference(Long)} must be called inside
 * the same transaction as the claim's insert (it participates in the caller's
 * transaction via {@code Propagation.MANDATORY}) so the row lock taken here is
 * held until that transaction commits, guaranteeing two concurrent claim
 * creations for the same provider can never receive the same sequence value.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimReferenceService {

    private final ProviderClaimSequenceRepository sequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String generateNextReference(Long providerId) {
        if (providerId == null) {
            throw new IllegalArgumentException("providerId is required to generate a claim reference");
        }

        // Atomic upsert: safe even if two transactions race to create the first
        // row for a brand-new provider (loser's insert is a no-op).
        sequenceRepository.ensureRowExists(providerId);

        // Row-locked read: held until the enclosing transaction commits, so the
        // increment below is never lost or duplicated under concurrency.
        ProviderClaimSequence sequence = sequenceRepository.findByIdForUpdate(providerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Provider claim sequence row missing for provider " + providerId + " after ensureRowExists"));

        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        sequence.setUpdatedAt(LocalDateTime.now());
        sequenceRepository.save(sequence);

        String reference = String.format("CLM-P%03d-%06d", providerId, value);
        log.info("🔢 [CLAIM-NUMBERING-1] Generated claim reference {} for provider {}", reference, providerId);
        return reference;
    }
}
