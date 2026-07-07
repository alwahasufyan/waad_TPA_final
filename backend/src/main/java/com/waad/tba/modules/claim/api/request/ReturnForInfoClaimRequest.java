package com.waad.tba.modules.claim.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Return Claim For Additional Information Request
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * BUSINESS RULES:
 * - Reason is MANDATORY (must explain what additional information is needed)
 * - Claim will be moved to RETURNED_FOR_INFO status
 * - Member/Provider can edit and resubmit
 * - No financial impact until resubmitted and approved
 * 
 * WORKFLOW:
 * 1. Reviewer calls POST /api/v1/claims/{id}/return-for-info with this request
 * 2. Backend validates mandatory reason
 * 3. Backend transitions claim to RETURNED_FOR_INFO status
 * 4. Backend records return action in audit log
 * 5. Member/Provider receives notification with required info
 * 6. Member/Provider can edit claim and resubmit
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnForInfoClaimRequest {
    
    /**
     * Mandatory reason explaining what additional information is needed.
     * 
     * Examples:
     * - "يرجى إرفاق التقرير الطبي الكامل"
     * - "الرجاء توضيح التشخيص"
     * - "مطلوب إرفاق نتائج الفحوصات"
     * - "Please attach complete medical report"
     * - "Please clarify diagnosis"
     * - "Lab results required"
     */
    @NotBlank(message = "Reason for requesting additional information is required")
    @Size(min = 10, max = 2000, message = "Reason must be between 10 and 2000 characters")
    private String reason;
    
    /**
     * Optional list of specific documents or information required.
     * 
     * Examples:
     * - "تقرير طبي، نتائج فحص الدم، صورة شعاعية"
     * - "Medical report, Blood test results, X-ray image"
     */
    @Size(max = 1000, message = "Required documents must not exceed 1000 characters")
    private String requiredDocuments;
}
