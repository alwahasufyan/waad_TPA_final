package com.waad.tba.modules.preauthorization.api.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Approve Pre-Authorization Request
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * ⚠️⚠️⚠️ CRITICAL DECISION SAFETY CONTRACT ⚠️⚠️⚠️
 * 
 * This contract EXPLICITLY FORBIDS all decision-altering fields.
 * The approvedAmount and copayPercentage are CALCULATED by the backend from:
 * 
 * 1. Provider Contract Pricing (ProviderContract.pricingItem.price)
 * 2. Benefit Policy Rules (BenefitPolicy.coveragePercent, limits)
 * 3. Member Eligibility (coverage status, remaining limits)
 * 4. Service Coverage Rules (service category coverage %)
 * 
 * Frontend CANNOT influence the approval decision under any circumstances.
 * 
 * BACKEND AUTHORITY:
 * The backend service layer is the SINGLE SOURCE OF TRUTH for all approval
 * decisions. The approval workflow guarantees:
 * 
 * - Approved amount is calculated from contract pricing
 * - Coverage limits are enforced
 * - Co-pay percentages match benefit policy
 * - Member limits are respected
 * - Service requires valid PA based on category rules
 * 
 * WORKFLOW:
 * 1. Reviewer calls POST /api/v1/pre-authorizations/{id}/approve with this request
 * 2. Backend retrieves pre-authorization entity
 * 3. Backend validates coverage and eligibility
 * 4. Backend calculates approved amount from contract pricing
 * 5. Backend calculates copay from benefit policy
 * 6. Backend saves approved pre-authorization with calculated values
 * 7. Backend returns PreAuthorizationResponse with read-only decision fields
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovePreAuthorizationRequest {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ALLOWED FIELDS (Non-decision metadata only)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Optional approval notes from the reviewer.
     * Used to document the approval decision.
     */
    @Size(max = 1000, message = "Approval notes must not exceed 1000 characters")
    private String approvalNotes;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ⛔⛔⛔ FORBIDDEN FIELDS - DECISION SAFETY CRITICAL ⛔⛔⛔
    // ═══════════════════════════════════════════════════════════════════════════
    // 
    // The following fields are EXPLICITLY AND PERMANENTLY FORBIDDEN:
    // 
    // ❌ approvedAmount - CALCULATED by backend from contract pricing
    // ❌ copayPercentage - CALCULATED by backend from benefit policy
    // ❌ copayAmount - CALCULATED by backend (approved × copay%)
    // ❌ insuranceCoveredAmount - CALCULATED by backend (approved - copay)
    // ❌ coverageLimits - ENFORCED by backend business rules
    // ❌ expiryDate - CALCULATED by backend from approval date + policy rules
    // 
    // WHY THIS IS CRITICAL:
    // - Pre-authorizations BLOCK or ALLOW claims processing
    // - Wrong approval amounts → Claims approved incorrectly → Financial loss
    // - Wrong copay → Patient pays wrong amount → Disputes
    // - Wrong limits → Services denied/approved incorrectly → Patient care affected
    // - Legal/compliance violations if decisions manipulated
    // 
    // ENFORCEMENT:
    // - This contract has NO decision fields (compile-time safety)
    // - Backend services NEVER read decision fields from requests
    // - ProviderContract + BenefitPolicy are ONLY sources of approval data
    // - Database constraints prevent invalid decision states
    // 
    // CALCULATION FLOW:
    // ```java
    // // Backend service layer (ApprovePreAuthorizationUseCase)
    // PreAuthorization preAuth = preAuthRepository.findById(preAuthId);
    // 
    // // Get contract price (BACKEND AUTHORITY)
    // BigDecimal contractPrice = providerContractService.getServicePrice(
    //     preAuth.getProvider(),
    //     preAuth.getMedicalService()
    // );
    // 
    // // Get coverage from benefit policy (BACKEND AUTHORITY)
    // BigDecimal copayPercentage = benefitPolicyService.getCopayPercentage(
    //     preAuth.getMember(),
    //     preAuth.getServiceCategory()
    // );
    // 
    // // Calculate approved amount and copay (NOT from request)
    // preAuth.setApprovedAmount(contractPrice);
    // preAuth.setCopayPercentage(copayPercentage);
    // preAuth.setCopayAmount(contractPrice.multiply(copayPercentage).divide(100));
    // preAuth.setInsuranceCoveredAmount(contractPrice.subtract(copayAmount));
    // preAuth.setStatus(PreAuthStatus.APPROVED);
    // 
    // preAuthRepository.save(preAuth);
    // ```
    // 
    // FRONTEND CANNOT BYPASS THIS FLOW.
    // ═══════════════════════════════════════════════════════════════════════════
}
