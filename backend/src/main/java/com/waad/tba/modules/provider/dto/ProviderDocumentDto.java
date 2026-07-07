package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Unified DTO for Provider Documents Center
 * 
 * Aggregates documents from:
 * - Visit Attachments
 * - PreAuthorization Attachments
 * - Claim Attachments
 * 
 * Provides a single view of all provider documents with their status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDocumentDto {
    
    /**
     * Attachment ID (unique within referenceType)
     */
    private Long id;
    
    /**
     * Type of reference: VISIT | PRE_AUTH | CLAIM
     */
    private String referenceType;
    
    /**
     * ID of the referenced entity (Visit ID, PreAuth ID, or Claim ID)
     */
    private Long referenceId;
    
    /**
     * Human-readable reference number (V-1234, PA-5678, CL-9012)
     */
    private String referenceNumber;
    
    /**
     * Type of document: MEDICAL_REPORT, INVOICE, XRAY, LAB_RESULT, etc.
     */
    private String documentType;
    
    /**
     * Arabic label for document type
     */
    private String documentTypeLabel;
    
    /**
     * Document status: REQUIRED | UPLOADED | APPROVED | REJECTED
     */
    private String status;
    
    /**
     * Arabic label for status
     */
    private String statusLabel;
    
    /**
     * Original file name (null if REQUIRED but not yet uploaded)
     */
    private String fileName;
    
    /**
     * File size in bytes (null if REQUIRED but not yet uploaded)
     */
    private Long fileSize;
    
    /**
     * File MIME type
     */
    private String fileType;
    
    /**
     * Upload timestamp (null if REQUIRED but not yet uploaded)
     */
    private LocalDateTime uploadedAt;
    
    /**
     * Who uploaded the document
     */
    private String uploadedBy;
    
    /**
     * Rejection reason (only if status = REJECTED)
     */
    private String rejectionReason;
    
    /**
     * Member name associated with this document
     */
    private String memberName;
    
    /**
     * Service name associated with this document
     */
    private String serviceName;
    
    /**
     * Download URL (for UPLOADED/APPROVED/REJECTED documents)
     */
    private String downloadUrl;
    
    // ==================== STATUS CONSTANTS ====================
    
    public static final String STATUS_REQUIRED = "REQUIRED";
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    
    // ==================== REFERENCE TYPE CONSTANTS ====================
    
    public static final String REF_TYPE_VISIT = "VISIT";
    public static final String REF_TYPE_PRE_AUTH = "PRE_AUTH";
    public static final String REF_TYPE_CLAIM = "CLAIM";
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Get Arabic label for reference type
     */
    public static String getReferenceTypeLabel(String refType) {
        return switch (refType) {
            case REF_TYPE_VISIT -> "زيارة";
            case REF_TYPE_PRE_AUTH -> "موافقة مسبقة";
            case REF_TYPE_CLAIM -> "مطالبة";
            default -> refType;
        };
    }
    
    /**
     * Get Arabic label for status
     */
    public static String getStatusLabelArabic(String status) {
        return switch (status) {
            case STATUS_REQUIRED -> "مطلوب";
            case STATUS_UPLOADED -> "مرفوع";
            case STATUS_APPROVED -> "مقبول";
            case STATUS_REJECTED -> "مرفوض";
            default -> status;
        };
    }
    
    /**
     * Get Arabic label for document type
     */
    public static String getDocumentTypeLabelArabic(String docType) {
        if (docType == null) return "مستند";
        return switch (docType.toUpperCase()) {
            case "MEDICAL_REPORT" -> "تقرير طبي";
            case "INVOICE" -> "فاتورة";
            case "XRAY" -> "أشعة سينية";
            case "MRI" -> "رنين مغناطيسي";
            case "CT_SCAN" -> "أشعة مقطعية";
            case "ULTRASOUND" -> "سونار";
            case "LAB_RESULT" -> "نتيجة مختبر";
            case "ECG" -> "تخطيط قلب";
            case "PRESCRIPTION" -> "وصفة طبية";
            case "RECEIPT" -> "إيصال";
            case "ID_DOCUMENT" -> "وثيقة هوية";
            case "INSURANCE_CARD" -> "بطاقة تأمين";
            case "OTHER" -> "أخرى";
            default -> docType;
        };
    }
}
