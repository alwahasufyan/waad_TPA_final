package com.waad.tba.modules.claim.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Claim type classification for attachment validation rules.
 * 
 * Each claim type has different documentation requirements
 * based on medical and insurance regulations.
 * 
 * @since Phase 7 - Operational Completeness
 */
public enum ClaimType {
    
    /**
     * Outpatient consultation/visit claims.
     * Required: Medical report
     * Optional: Lab results, prescriptions
     */
    OUTPATIENT("عيادات خارجية", 
        Set.of(AttachmentCategory.MEDICAL_REPORT),
        Set.of(AttachmentCategory.LAB_RESULTS, AttachmentCategory.PRESCRIPTION)),
    
    /**
     * Inpatient hospitalization claims.
     * Required: Medical report, discharge summary
     * Optional: Lab results, radiology, itemized bill
     */
    INPATIENT("إقامة داخلية", 
        Set.of(AttachmentCategory.MEDICAL_REPORT, AttachmentCategory.DISCHARGE_SUMMARY),
        Set.of(AttachmentCategory.LAB_RESULTS, AttachmentCategory.RADIOLOGY_REPORT, AttachmentCategory.ITEMIZED_BILL)),
    
    /**
     * Emergency room visits.
     * Required: ER report, medical report
     * Optional: Lab results, radiology
     */
    EMERGENCY("طوارئ", 
        Set.of(AttachmentCategory.ER_REPORT, AttachmentCategory.MEDICAL_REPORT),
        Set.of(AttachmentCategory.LAB_RESULTS, AttachmentCategory.RADIOLOGY_REPORT)),
    
    /**
     * Laboratory and diagnostic testing.
     * Required: Lab results, doctor referral
     * Optional: Medical report
     */
    LABORATORY("مختبر", 
        Set.of(AttachmentCategory.LAB_RESULTS, AttachmentCategory.REFERRAL),
        Set.of(AttachmentCategory.MEDICAL_REPORT)),
    
    /**
     * Radiology and imaging services.
     * Required: Radiology report, doctor referral
     * Optional: Medical report
     */
    RADIOLOGY("أشعة", 
        Set.of(AttachmentCategory.RADIOLOGY_REPORT, AttachmentCategory.REFERRAL),
        Set.of(AttachmentCategory.MEDICAL_REPORT)),
    
    /**
     * Pharmacy/prescription claims.
     * Required: Prescription
     * Optional: Medical report
     */
    PHARMACY("صيدلية", 
        Set.of(AttachmentCategory.PRESCRIPTION),
        Set.of(AttachmentCategory.MEDICAL_REPORT)),
    
    /**
     * Dental services.
     * Required: Dental report
     * Optional: X-ray, treatment plan
     */
    DENTAL("أسنان", 
        Set.of(AttachmentCategory.DENTAL_REPORT),
        Set.of(AttachmentCategory.DENTAL_XRAY, AttachmentCategory.TREATMENT_PLAN)),
    
    /**
     * Optical/vision services.
     * Required: Eye exam report
     * Optional: Prescription
     */
    OPTICAL("بصريات", 
        Set.of(AttachmentCategory.EYE_EXAM_REPORT),
        Set.of(AttachmentCategory.PRESCRIPTION)),
    
    /**
     * Maternity and obstetric services.
     * Required: Medical report, pregnancy confirmation
     * Optional: Ultrasound, lab results
     */
    MATERNITY("أمومة", 
        Set.of(AttachmentCategory.MEDICAL_REPORT, AttachmentCategory.PREGNANCY_CONFIRMATION),
        Set.of(AttachmentCategory.ULTRASOUND_REPORT, AttachmentCategory.LAB_RESULTS)),
    
    /**
     * Surgical procedures.
     * Required: Medical report, surgical report, pre-approval
     * Optional: Lab results, radiology, itemized bill
     */
    SURGERY("جراحة", 
        Set.of(AttachmentCategory.MEDICAL_REPORT, AttachmentCategory.SURGICAL_REPORT, AttachmentCategory.PREAPPROVAL),
        Set.of(AttachmentCategory.LAB_RESULTS, AttachmentCategory.RADIOLOGY_REPORT, AttachmentCategory.ITEMIZED_BILL)),
    
    /**
     * Chronic disease management claims.
     * Required: Medical report, chronic condition documentation
     * Optional: Lab results, prescription
     */
    CHRONIC_CARE("رعاية أمراض مزمنة", 
        Set.of(AttachmentCategory.MEDICAL_REPORT, AttachmentCategory.CHRONIC_DOCUMENTATION),
        Set.of(AttachmentCategory.LAB_RESULTS, AttachmentCategory.PRESCRIPTION)),
    
    /**
     * Physical therapy and rehabilitation.
     * Required: Referral, therapy report
     * Optional: Medical report
     */
    PHYSIOTHERAPY("علاج طبيعي", 
        Set.of(AttachmentCategory.REFERRAL, AttachmentCategory.THERAPY_REPORT),
        Set.of(AttachmentCategory.MEDICAL_REPORT)),
    
    /**
     * General/unclassified claims.
     * Required: Medical report only
     * Optional: Any other documentation
     */
    GENERAL("عام", 
        Set.of(AttachmentCategory.MEDICAL_REPORT),
        Collections.emptySet());
    
    private final String arabicLabel;
    private final Set<AttachmentCategory> requiredAttachments;
    private final Set<AttachmentCategory> optionalAttachments;
    
    ClaimType(String arabicLabel, Set<AttachmentCategory> required, Set<AttachmentCategory> optional) {
        this.arabicLabel = arabicLabel;
        this.requiredAttachments = Collections.unmodifiableSet(required);
        this.optionalAttachments = Collections.unmodifiableSet(optional);
    }
    
    public String getArabicLabel() {
        return arabicLabel;
    }
    
    /**
     * Get the set of required attachment categories for this claim type.
     * Claims cannot transition to SUBMITTED without these attachments.
     */
    public Set<AttachmentCategory> getRequiredAttachments() {
        return requiredAttachments;
    }
    
    /**
     * Get the set of optional attachment categories for this claim type.
     * These are recommended but not required for submission.
     */
    public Set<AttachmentCategory> getOptionalAttachments() {
        return optionalAttachments;
    }
    
    /**
     * Get all valid attachment categories for this claim type (required + optional).
     */
    public Set<AttachmentCategory> getAllValidAttachments() {
        return Arrays.stream(AttachmentCategory.values())
            .filter(cat -> requiredAttachments.contains(cat) || optionalAttachments.contains(cat))
            .collect(Collectors.toUnmodifiableSet());
    }
    
    /**
     * Check if this claim type requires pre-approval.
     */
    public boolean requiresPreApproval() {
        return requiredAttachments.contains(AttachmentCategory.PREAPPROVAL);
    }
    
    /**
     * Attachment category classifications.
     */
    public enum AttachmentCategory {
        MEDICAL_REPORT("تقرير طبي"),
        DISCHARGE_SUMMARY("ملخص الخروج"),
        LAB_RESULTS("نتائج المختبر"),
        RADIOLOGY_REPORT("تقرير الأشعة"),
        PRESCRIPTION("وصفة طبية"),
        ITEMIZED_BILL("فاتورة مفصلة"),
        ER_REPORT("تقرير الطوارئ"),
        REFERRAL("إحالة"),
        DENTAL_REPORT("تقرير الأسنان"),
        DENTAL_XRAY("أشعة الأسنان"),
        TREATMENT_PLAN("خطة العلاج"),
        EYE_EXAM_REPORT("تقرير فحص العيون"),
        PREGNANCY_CONFIRMATION("تأكيد الحمل"),
        ULTRASOUND_REPORT("تقرير السونار"),
        SURGICAL_REPORT("تقرير جراحي"),
        PREAPPROVAL("موافقة مسبقة"),
        CHRONIC_DOCUMENTATION("وثائق الأمراض المزمنة"),
        THERAPY_REPORT("تقرير العلاج"),
        OTHER("أخرى");
        
        private final String arabicLabel;
        
        AttachmentCategory(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }
        
        public String getArabicLabel() {
            return arabicLabel;
        }
    }
}
