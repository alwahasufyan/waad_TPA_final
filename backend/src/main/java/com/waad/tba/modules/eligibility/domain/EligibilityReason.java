package com.waad.tba.modules.eligibility.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Eligibility Reason Enum
 * Phase E1 - Eligibility Engine
 * 
 * Comprehensive enumeration of all possible eligibility check outcomes.
 * Each reason has a machine-readable code and Arabic message.
 * 
 * Categorized by:
 * - MEMBER_* : Member-related issues
 * - POLICY_* : Policy-related issues
 * - COVERAGE_* : Coverage/benefits issues
 * - PROVIDER_* : Provider/network issues
 * - SYSTEM_* : System/technical issues
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Getter
@RequiredArgsConstructor
public enum EligibilityReason {

    // ============================================
    // Member-Related Reasons
    // ============================================
    
    MEMBER_NOT_FOUND(
        "MEMBER_NOT_FOUND",
        "العضو غير موجود في النظام",
        "Member not found in the system",
        true
    ),
    
    MEMBER_INACTIVE(
        "MEMBER_INACTIVE",
        "حالة العضو غير فعالة",
        "Member status is not active",
        true
    ),
    
    MEMBER_SUSPENDED(
        "MEMBER_SUSPENDED",
        "عضوية العضو موقوفة",
        "Member is suspended",
        true
    ),
    
    MEMBER_TERMINATED(
        "MEMBER_TERMINATED",
        "تم إنهاء عضوية العضو",
        "Member has been terminated",
        true
    ),
    
    MEMBER_CARD_BLOCKED(
        "MEMBER_CARD_BLOCKED",
        "بطاقة العضو محجوبة",
        "Member card is blocked",
        true
    ),
    
    MEMBER_CARD_EXPIRED(
        "MEMBER_CARD_EXPIRED",
        "بطاقة العضو منتهية الصلاحية",
        "Member card has expired",
        true
    ),
    
    MEMBER_NOT_IN_SCOPE(
        "MEMBER_NOT_IN_SCOPE",
        "العضو خارج نطاق صلاحياتك",
        "Member is outside your access scope",
        true
    ),

    // ============================================
    // Policy-Related Reasons
    // ============================================
    
    POLICY_NOT_FOUND(
        "POLICY_NOT_FOUND",
        "الوثيقة غير موجودة",
        "Policy not found",
        true
    ),
    
    POLICY_INACTIVE(
        "POLICY_INACTIVE",
        "الوثيقة غير فعالة",
        "Policy is not active",
        true
    ),
    
    POLICY_SUSPENDED(
        "POLICY_SUSPENDED",
        "الوثيقة موقوفة",
        "Policy is suspended",
        true
    ),
    
    POLICY_EXPIRED(
        "POLICY_EXPIRED",
        "الوثيقة منتهية الصلاحية",
        "Policy has expired",
        true
    ),
    
    POLICY_CANCELLED(
        "POLICY_CANCELLED",
        "تم إلغاء الوثيقة",
        "Policy has been cancelled",
        true
    ),
    
    POLICY_NOT_YET_EFFECTIVE(
        "POLICY_NOT_YET_EFFECTIVE",
        "الوثيقة لم تبدأ بعد",
        "Policy is not yet effective",
        true
    ),

    // ============================================
    // Coverage-Related Reasons
    // ============================================
    
    SERVICE_DATE_BEFORE_COVERAGE(
        "SERVICE_DATE_BEFORE_COVERAGE",
        "تاريخ الخدمة قبل بداية التغطية",
        "Service date is before coverage start date",
        true
    ),
    
    SERVICE_DATE_AFTER_COVERAGE(
        "SERVICE_DATE_AFTER_COVERAGE",
        "تاريخ الخدمة بعد نهاية التغطية",
        "Service date is after coverage end date",
        true
    ),
    
    MEMBER_NOT_ENROLLED(
        "MEMBER_NOT_ENROLLED",
        "العضو غير مسجل في هذه الوثيقة",
        "Member is not enrolled in this policy",
        true
    ),
    
    WAITING_PERIOD_NOT_SATISFIED(
        "WAITING_PERIOD_NOT_SATISFIED",
        "فترة الانتظار لم تنتهِ بعد",
        "Waiting period has not been satisfied",
        true
    ),
    
    COVERAGE_LIMIT_EXHAUSTED(
        "COVERAGE_LIMIT_EXHAUSTED",
        "تم استنفاد حد التغطية",
        "Coverage limit has been exhausted",
        true
    ),
    
    SERVICE_NOT_COVERED(
        "SERVICE_NOT_COVERED",
        "الخدمة غير مشمولة في التغطية",
        "Service is not covered under this policy",
        true
    ),
    
    SERVICE_EXCLUDED(
        "SERVICE_EXCLUDED",
        "الخدمة مستثناة من التغطية",
        "Service is excluded from coverage",
        true
    ),

    // ============================================
    // Provider-Related Reasons
    // ============================================
    
    PROVIDER_NOT_FOUND(
        "PROVIDER_NOT_FOUND",
        "مقدم الخدمة غير موجود",
        "Provider not found",
        true
    ),
    
    PROVIDER_NOT_IN_NETWORK(
        "PROVIDER_NOT_IN_NETWORK",
        "مقدم الخدمة خارج الشبكة",
        "Provider is not in network",
        false  // Soft - may still be covered with different rate
    ),
    
    PROVIDER_INACTIVE(
        "PROVIDER_INACTIVE",
        "مقدم الخدمة غير فعال",
        "Provider is not active",
        true
    ),
    
    PROVIDER_CONTRACT_EXPIRED(
        "PROVIDER_CONTRACT_EXPIRED",
        "عقد مقدم الخدمة منتهي",
        "Provider contract has expired",
        false  // Soft - may still be covered
    ),

    // ============================================
    // Employer-Related Reasons
    // ============================================
    
    EMPLOYER_NOT_FOUND(
        "EMPLOYER_NOT_FOUND",
        "جهة العمل غير موجودة",
        "Employer not found",
        true
    ),
    
    EMPLOYER_INACTIVE(
        "EMPLOYER_INACTIVE",
        "جهة العمل غير فعالة",
        "Employer is not active",
        true
    ),
    
    EMPLOYER_CONTRACT_SUSPENDED(
        "EMPLOYER_CONTRACT_SUSPENDED",
        "عقد جهة العمل موقوف",
        "Employer contract is suspended",
        true
    ),

    // ============================================
    // System/Technical Reasons
    // ============================================
    
    SYSTEM_ERROR(
        "SYSTEM_ERROR",
        "خطأ في النظام",
        "System error occurred",
        true
    ),
    
    INVALID_REQUEST(
        "INVALID_REQUEST",
        "طلب غير صالح",
        "Invalid request parameters",
        true
    ),
    
    SERVICE_DATE_INVALID(
        "SERVICE_DATE_INVALID",
        "تاريخ الخدمة غير صالح",
        "Service date is invalid",
        true
    ),
    
    SERVICE_DATE_IN_FUTURE(
        "SERVICE_DATE_IN_FUTURE",
        "تاريخ الخدمة في المستقبل",
        "Service date cannot be in the future",
        false  // Soft - may be allowed for pre-authorization
    ),

    // ============================================
    // Success Reasons (for audit purposes)
    // ============================================
    
    ELIGIBLE(
        "ELIGIBLE",
        "العضو مؤهل للحصول على الخدمة",
        "Member is eligible for the service",
        false
    ),
    
    ELIGIBLE_WITH_WARNINGS(
        "ELIGIBLE_WITH_WARNINGS",
        "العضو مؤهل مع ملاحظات",
        "Member is eligible with warnings",
        false
    );

    /**
     * Machine-readable code
     */
    private final String code;

    /**
     * Arabic message for display
     */
    private final String messageAr;

    /**
     * English message for logging
     */
    private final String messageEn;

    /**
     * Whether this is a hard failure (stops eligibility)
     */
    private final boolean hardFailure;

    /**
     * Find reason by code
     */
    public static EligibilityReason fromCode(String code) {
        for (EligibilityReason reason : values()) {
            if (reason.getCode().equals(code)) {
                return reason;
            }
        }
        return SYSTEM_ERROR;
    }
}
