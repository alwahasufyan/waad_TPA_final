package com.waad.tba.modules.visit.entity;

/**
 * Visit Type Classification Enum
 * 
 * Defines the type/location of healthcare service delivery for each visit.
 * Used for:
 * - Filtering visits by service location type
 * - Reporting and analytics
 * - UI classification and display
 * - Service level differentiation
 * 
 * @author GitHub Copilot
 * @version 1.0
 * @since 2026-01-08
 */
public enum VisitType {
    
    /**
     * Emergency Room / Emergency Department visit
     * زيارة غرفة الطوارئ
     */
    EMERGENCY("طوارئ", "Emergency", "ER"),
    
    /**
     * Outpatient clinic visit (default)
     * زيارة عيادة خارجية
     */
    OUTPATIENT("عيادة خارجية", "Outpatient", "OPD"),
    
    /**
     * Inpatient hospital admission/stay
     * إقامة داخلية في المستشفى
     */
    INPATIENT("إقامة داخلية", "Inpatient", "IPD"),
    
    /**
     * Routine check-up visit
     * فحص روتيني
     */
    ROUTINE("روتينية", "Routine Check-up", "ROUTINE"),
    
    /**
     * Follow-up visit after previous treatment
     * زيارة متابعة
     */
    FOLLOW_UP("متابعة", "Follow-up", "FOLLOWUP"),
    
    /**
     * Preventive care visit (vaccination, screening, etc.)
     * زيارة وقائية
     */
    PREVENTIVE("وقائية", "Preventive", "PREV"),
    
    /**
     * Specialized consultation visit
     * استشارة تخصصية
     */
    SPECIALIZED("تخصصية", "Specialized", "SPEC"),
    
    /**
     * Home care visit
     * رعاية منزلية
     */
    HOME_CARE("رعاية منزلية", "Home Care", "HOME"),
    
    /**
     * Teleconsultation / Telemedicine visit
     * استشارة عن بُعد
     */
    TELECONSULTATION("استشارة عن بُعد", "Teleconsultation", "TELE"),
    
    /**
     * Day surgery (admission and discharge same day)
     * جراحة يومية
     */
    DAY_SURGERY("جراحة يومية", "Day Surgery", "DAY_SURG"),

    /**
     * Legacy backlog claim (historical data)
     * سجل تاريخي - مطالبات متراكمة
     */
    LEGACY_BACKLOG("سجل تاريخي", "Legacy Backlog", "LEGACY");
    
    private final String arabicLabel;
    private final String englishLabel;
    private final String code;
    
    /**
     * Constructor for VisitType enum
     * 
     * @param arabicLabel Arabic display label
     * @param englishLabel English display label
     * @param code Short code for the visit type
     */
    VisitType(String arabicLabel, String englishLabel, String code) {
        this.arabicLabel = arabicLabel;
        this.englishLabel = englishLabel;
        this.code = code;
    }
    
    /**
     * Get Arabic display label
     * @return Arabic label for UI display
     */
    public String getArabicLabel() {
        return arabicLabel;
    }
    
    /**
     * Get English display label
     * @return English label for UI display
     */
    public String getEnglishLabel() {
        return englishLabel;
    }
    
    /**
     * Get short code
     * @return Short code for the visit type
     */
    public String getCode() {
        return code;
    }
}
