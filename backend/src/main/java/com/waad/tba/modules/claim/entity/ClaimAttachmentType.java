package com.waad.tba.modules.claim.entity;

/**
 * Claim Attachment Type Enum
 * 
 * Types of attachments that can be uploaded with a claim
 */
public enum ClaimAttachmentType {
    INVOICE("فاتورة", "Invoice"),
    MEDICAL_REPORT("تقرير طبي", "Medical Report"),
    PRESCRIPTION("وصفة طبية", "Prescription"),
    LAB_RESULT("نتيجة مختبر", "Lab Result"),
    XRAY("صورة أشعة", "X-Ray/Radiology Image"),
    OTHER("أخرى", "Other");

    private final String arabicLabel;
    private final String englishLabel;

    ClaimAttachmentType(String arabicLabel, String englishLabel) {
        this.arabicLabel = arabicLabel;
        this.englishLabel = englishLabel;
    }

    public String getArabicLabel() {
        return arabicLabel;
    }

    public String getEnglishLabel() {
        return englishLabel;
    }
}
