package com.waad.tba.modules.member.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Coverage status for chronic conditions.
 * Determines how a chronic condition is handled for insurance coverage.
 */
@Getter
@RequiredArgsConstructor
public enum ChronicCoverageStatus {
    
    /**
     * Condition is fully covered by the policy.
     * All related treatments, medications, and procedures are covered.
     */
    COVERED("COV", "مغطى بالكامل", "Fully Covered", 
            "الحالة مغطاة بالكامل وفق شروط الوثيقة"),
    
    /**
     * Condition is excluded from coverage.
     * No claims related to this condition will be approved.
     */
    EXCLUDED("EXC", "مستثنى من التغطية", "Excluded", 
            "الحالة مستثناة من التغطية - لن يتم قبول أي مطالبات متعلقة بها"),
    
    /**
     * Condition is in waiting period.
     * Coverage will begin after the waiting period ends.
     */
    WAITING_PERIOD("WP", "في فترة الانتظار", "Waiting Period", 
            "الحالة في فترة الانتظار - ستبدأ التغطية بعد انتهاء الفترة"),
    
    /**
     * Condition has partial coverage.
     * Only certain treatments or a percentage of costs are covered.
     */
    PARTIAL("PRT", "تغطية جزئية", "Partial Coverage", 
            "تغطية جزئية - نسبة محددة أو علاجات معينة فقط"),
    
    /**
     * Condition coverage is pending review.
     * Medical committee needs to review and decide.
     */
    PENDING_REVIEW("PRV", "قيد المراجعة", "Pending Review", 
            "قيد مراجعة اللجنة الطبية لتحديد حالة التغطية"),
    
    /**
     * Condition was initially excluded but now covered after waiting period.
     * Used to track history of coverage changes.
     */
    COVERED_AFTER_WAITING("CAW", "مغطى بعد فترة الانتظار", "Covered After Waiting", 
            "أصبحت الحالة مغطاة بعد انتهاء فترة الانتظار"),
    
    /**
     * Condition requires pre-approval for each claim.
     * Covered but needs PA before treatment.
     */
    REQUIRES_PRE_APPROVAL("RPA", "يتطلب موافقة مسبقة", "Requires Pre-Approval", 
            "مغطى لكن يتطلب موافقة مسبقة لكل علاج"),
    
    /**
     * Coverage limited to specific providers or treatments.
     */
    LIMITED("LMT", "تغطية محدودة", "Limited Coverage", 
            "تغطية محدودة بمقدمي خدمة أو علاجات معينة");
    
    /**
     * Unique code for the status
     */
    private final String code;
    
    /**
     * Arabic label for display
     */
    private final String labelAr;
    
    /**
     * English label for display
     */
    private final String labelEn;
    
    /**
     * Description of what this status means
     */
    private final String description;
    
    /**
     * Check if this status allows claims to be submitted
     */
    public boolean allowsClaims() {
        return this == COVERED 
            || this == PARTIAL 
            || this == COVERED_AFTER_WAITING 
            || this == REQUIRES_PRE_APPROVAL
            || this == LIMITED;
    }
    
    /**
     * Check if this status requires pre-approval
     */
    public boolean requiresPreApproval() {
        return this == REQUIRES_PRE_APPROVAL 
            || this == PARTIAL 
            || this == LIMITED;
    }
    
    /**
     * Check if claims should be auto-rejected
     */
    public boolean autoRejectClaims() {
        return this == EXCLUDED 
            || this == WAITING_PERIOD 
            || this == PENDING_REVIEW;
    }
    
    /**
     * Find status by code
     */
    public static ChronicCoverageStatus fromCode(String code) {
        if (code == null) return null;
        for (ChronicCoverageStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
