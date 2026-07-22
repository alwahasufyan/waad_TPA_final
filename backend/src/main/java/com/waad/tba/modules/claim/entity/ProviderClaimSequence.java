package com.waad.tba.modules.claim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CLAIM-NUMBERING-1: one row per provider, holding the next sequence value to
 * hand out for that provider's claim reference (CLM-P{providerId}-{sequence}).
 *
 * Concurrency: rows are read via a pessimistic write lock
 * (see {@code ProviderClaimSequenceRepository#findByIdForUpdate}) inside the
 * same transaction that creates the claim, so two concurrent claim creations
 * for the same provider cannot receive the same sequence value.
 */
@Entity
@Table(name = "provider_claim_sequences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderClaimSequence {

    @Id
    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
