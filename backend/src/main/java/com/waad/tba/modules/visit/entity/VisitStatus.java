package com.waad.tba.modules.visit.entity;

/**
 * Visit Status Enum
 * 
 * Tracks the lifecycle status of a medical visit.
 * Used for:
 * - Visit workflow management
 * - Filtering visits by status
 * - Determining available actions (create claim, pre-auth)
 * - Provider portal visit log
 * 
 * @author TBA WAAD System
 * @version 1.0
 * @since 2026-01-13
 */
public enum VisitStatus {
    
    /**
     * Visit registered, awaiting services/claims
     * الزيارة مسجلة، في انتظار الخدمات/المطالبات
     */
    REGISTERED("مسجلة", "Registered"),
    
    /**
     * Visit in progress (services being rendered)
     * الزيارة قيد التنفيذ
     */
    IN_PROGRESS("قيد التنفيذ", "In Progress"),
    
    /**
     * Pre-authorization requested (awaiting approval)
     * طلب موافقة مسبقة
     */
    PENDING_PREAUTH("انتظار الموافقة", "Pending Pre-Auth"),
    
    /**
     * Pre-authorization approved, ready for service
     * تمت الموافقة المسبقة
     */
    PREAUTH_APPROVED("موافقة مسبقة", "Pre-Auth Approved"),
    
    /**
     * Claim submitted for this visit
     * تم تقديم المطالبة
     */
    CLAIM_SUBMITTED("مطالبة مقدمة", "Claim Submitted"),
    
    /**
     * Visit completed, all claims settled
     * الزيارة مكتملة
     */
    COMPLETED("مكتملة", "Completed"),
    
    /**
     * Visit cancelled
     * الزيارة ملغاة
     */
    CANCELLED("ملغاة", "Cancelled");
    
    private final String labelAr;
    private final String labelEn;
    
    VisitStatus(String labelAr, String labelEn) {
        this.labelAr = labelAr;
        this.labelEn = labelEn;
    }
    
    public String getLabelAr() {
        return labelAr;
    }
    
    public String getLabelEn() {
        return labelEn;
    }
    
    /**
     * Check if visit allows creating a claim
     */
    public boolean allowsClaimCreation() {
        return this == REGISTERED || 
               this == IN_PROGRESS || 
               this == PREAUTH_APPROVED;
    }
    
    /**
     * Check if visit allows creating a pre-authorization
     */
    public boolean allowsPreAuthCreation() {
        return this == REGISTERED || 
               this == IN_PROGRESS;
    }
}
