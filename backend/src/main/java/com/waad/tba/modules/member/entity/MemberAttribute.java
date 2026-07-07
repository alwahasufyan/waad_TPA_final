package com.waad.tba.modules.member.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flexible key-value attribute storage for Member entities.
 * 
 * Supports dynamic attributes like:
 * - job_title
 * - department
 * - work_location
 * - grade
 * - manager
 * - cost_center
 * 
 * Compatible with Odoo hr.employee.public imports.
 */
@Entity
@Table(name = "member_attributes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"member_id", "attribute_code"}, name = "uk_member_attribute_code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MemberAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Member is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @NotBlank(message = "Attribute code is required")
    @Column(name = "attribute_code", nullable = false, length = 100)
    private String attributeCode;

    @Column(name = "attribute_value", columnDefinition = "TEXT")
    private String attributeValue;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(length = 50)
    private AttributeSource source = AttributeSource.MANUAL;

    @Column(name = "source_reference", length = 200)
    private String sourceReference;

    // Audit fields
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @CreatedDate
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Source of the attribute data
     */
    public enum AttributeSource {
        MANUAL,     // Created/edited manually via UI
        IMPORT,     // Imported from Excel
        ODOO,       // Synced from Odoo
        API         // Created via external API
    }

    /**
     * Common attribute codes (constants for type safety)
     */
    public static class Codes {
        public static final String JOB_TITLE = "job_title";
        public static final String DEPARTMENT = "department";
        public static final String WORK_LOCATION = "work_location";
        public static final String GRADE = "grade";
        public static final String MANAGER = "manager";
        public static final String COST_CENTER = "cost_center";
        public static final String WORK_EMAIL = "work_email";
        public static final String WORK_PHONE = "work_phone";
        public static final String MOBILE_PHONE = "mobile_phone";
        public static final String BADGE_ID = "badge_id";
        public static final String HIRE_DATE = "hire_date";
        public static final String CONTRACT_TYPE = "contract_type";
    }
}
