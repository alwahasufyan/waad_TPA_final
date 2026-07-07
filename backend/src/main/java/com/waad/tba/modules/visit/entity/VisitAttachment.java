package com.waad.tba.modules.visit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Visit Attachment Entity
 * 
 * Represents a file attachment for a patient visit
 * (medical images, lab results, prescriptions, etc.)
 */
@Entity
@Table(name = "visit_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Associated visit
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    /**
     * File name (for display purposes)
     */
    @Column(name = "file_name", length = 500, nullable = false)
    private String fileName;

    /**
     * Original filename as uploaded by user
     */
    @Column(name = "original_file_name", length = 500)
    private String originalFileName;

    /**
     * File storage key (unique identifier in storage system)
     */
    @Column(name = "file_key", length = 500)
    private String fileKey;

    /**
     * File MIME type
     */
    @Column(name = "file_type", length = 100)
    private String fileType;

    /**
     * File size in bytes
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Type of attachment (XRAY, MRI, LAB_RESULT, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", length = 50)
    private VisitAttachmentType attachmentType;

    /**
     * Optional description or notes about the attachment
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Username or user ID who uploaded the file
     */
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    /**
     * Upload timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
