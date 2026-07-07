package com.waad.tba.modules.visit.entity;

/**
 * Visit Attachment Type Enum
 * 
 * Types of medical attachments that can be uploaded for a visit
 * (radiology, lab results, prescriptions, etc.)
 */
public enum VisitAttachmentType {
    XRAY("أشعة سينية", "X-Ray"),
    MRI("رنين مغناطيسي", "MRI Scan"),
    CT_SCAN("أشعة مقطعية", "CT Scan"),
    ULTRASOUND("موجات فوق صوتية", "Ultrasound"),
    LAB_RESULT("نتيجة مختبر", "Lab Result"),
    ECG("تخطيط قلب", "ECG/EKG"),
    PRESCRIPTION("وصفة طبية", "Prescription"),
    MEDICAL_REPORT("تقرير طبي", "Medical Report"),
    OTHER("أخرى", "Other");

    private final String arabicLabel;
    private final String englishLabel;

    VisitAttachmentType(String arabicLabel, String englishLabel) {
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
