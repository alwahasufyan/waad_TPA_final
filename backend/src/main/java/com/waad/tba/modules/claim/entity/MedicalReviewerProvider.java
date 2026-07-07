package com.waad.tba.modules.claim.entity;

import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.rbac.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Medical Reviewer Provider Mapping Entity.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * MEDICAL REVIEWER ISOLATION
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Purpose:
 * This entity implements strict reviewer-provider isolation by mapping medical
 * reviewers to specific providers. Each medical reviewer can only see and work on
 * claims from providers they are explicitly assigned to.
 * 
 * Business Rules:
 * 1. One reviewer can be assigned to multiple providers
 * 2. One provider can have multiple reviewers
 * 3. Each assignment must be unique (reviewer_id + provider_id)
 * 4. Soft delete via 'active' flag preserves audit trail
 * 5. Admin and SuperAdmin roles bypass this isolation
 * 
 * Use Cases:
 * - Prevent concurrent review conflicts (multiple reviewers on same claim)
 * - Enforce reviewer specialization (e.g., reviewer X handles clinic A only)
 * - Audit trail of reviewer assignments over time
 * 
 * Example:
 * - Reviewer "Dr. Ahmed" assigned to ["Clinic A", "Clinic B"]
 * - Reviewer "Dr. Fatima" assigned to ["Clinic C"]
 * - Dr. Ahmed sees only claims from Clinic A and B
 * - Dr. Fatima sees only claims from Clinic C
 * - Admin sees all claims (bypass isolation)
 * 
 * @since Medical Reviewer Isolation Phase (2026-02-12)
 */
@Entity
@Table(name = "medical_reviewer_providers", uniqueConstraints = {
    @UniqueConstraint(name = "uk_reviewer_provider", columnNames = {"reviewer_id", "provider_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalReviewerProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Medical Reviewer (User with MEDICAL_REVIEWER role)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    /**
     * Healthcare Provider assigned to this reviewer
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    /**
     * Active flag for soft delete
     * When false, assignment is considered deleted but preserved for audit
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Deactivate this assignment (soft delete)
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Reactivate this assignment
     */
    public void activate() {
        this.active = true;
    }
}
