package com.waad.tba.modules.claim.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "claim_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "file_name", length = 500, nullable = false)
    private String fileName;

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "file_type", length = 100)
    private String fileType;

    /**
     * File storage key (unique identifier in storage system)
     */
    @Column(name = "file_key", length = 500)
    private String fileKey;

    /**
     * Original filename as uploaded by user
     */
    @Column(name = "original_file_name", length = 500)
    private String originalFileName;

    /**
     * File size in bytes
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Username or user ID who uploaded the file
     */
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    /**
     * Type of attachment (INVOICE, MEDICAL_REPORT, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", length = 50)
    private ClaimAttachmentType attachmentType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
