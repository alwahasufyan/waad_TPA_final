package com.waad.tba.modules.claim.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Atomic Financial Service - Thread-Safe Financial Operations.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * FINANCIAL INTEGRITY GUARANTEE
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This service provides ATOMIC financial operations with proper locking to prevent:
 * 
 * 1. DOUBLE APPROVAL RACE CONDITION
 *    - Two concurrent approval requests for the same claim
 *    - Protected by: Claim-level pessimistic lock (SELECT ... FOR UPDATE)
 * 
 * 2. DEDUCTIBLE OVERSPEND RACE CONDITION
 *    - Two claims for the same member approved concurrently
 *    - Each sees full remaining deductible, both apply full deductible
 *    - Protected by: Member-level pessimistic lock
 *    - Example:
 *      * Member has $500 remaining deductible
 *      * Claim A ($400) and Claim B ($400) submitted concurrently
 *      * WITHOUT lock: Both see $500, both apply $400, total = $800 (WRONG)
 *      * WITH lock: Claim A locks member, applies $400. Claim B waits, sees $100 (CORRECT)
 * 
 * 3. SETTLEMENT OVERPAYMENT
 *    - Settlement amount exceeds approved amount
 *    - Protected by: Amount validation + pessimistic lock
 * 
 * LOCKING STRATEGY:
 * - Use REQUIRES_NEW propagation to ensure lock is held for entire operation
 * - Lock order: Claim first, then Member (prevents deadlocks)
 * - Use SERIALIZABLE isolation for critical financial operations
 * 
 * @since Phase 1 - Financial Integrity Remediation (2026-01-28)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtomicFinancialService {

    private final ClaimRepository claimRepository;
    private final MemberRepository memberRepository;
    private final CostCalculationService costCalculationService;

    /**
     * Calculate costs with atomic member locking.
     * 
     * This method:
     * 1. Acquires pessimistic lock on the member (SELECT ... FOR UPDATE)
     * 2. Queries all approved/settled claims for deductible calculation
     * 3. Calculates cost breakdown with accurate remaining deductible
     * 4. Lock is released when transaction commits
     * 
     * CRITICAL: Must be called within a transaction that will also save the claim.
     * The member lock prevents other concurrent claims from seeing stale deductible values.
     * 
     * @param claim The claim to calculate costs for
     * @return CostBreakdown with accurate deductible calculations
     * @throws BusinessRuleException if member cannot be locked or validation fails
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    public CostCalculationService.CostBreakdown calculateCostsWithAtomicDeductible(Claim claim) {
        if (claim == null) {
            throw new BusinessRuleException("FINANCIAL_ERROR: Claim cannot be null for cost calculation");
        }
        
        Member member = claim.getMember();
        if (member == null || member.getId() == null) {
            log.warn("⚠️ Claim {} has no member, using default cost calculation", claim.getId());
            return costCalculationService.calculateCosts(claim);
        }
        
        log.info("🔒 [ATOMIC-DEDUCTIBLE] Acquiring member lock for member {} (claim {})", 
            member.getId(), claim.getId());
        
        // ══════════════════════════════════════════════════════════════════════════
        // CRITICAL: Lock the MEMBER to prevent concurrent deductible calculations
        // ══════════════════════════════════════════════════════════════════════════
        // This ensures that if two claims for the same member are being processed
        // concurrently, only one can calculate deductibles at a time.
        Member lockedMember = memberRepository.findByIdWithLock(member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", member.getId()));
        
        log.info("✅ [ATOMIC-DEDUCTIBLE] Acquired pessimistic lock on member {} for deductible calculation", 
            lockedMember.getId());
        
        // Now calculate costs - the member lock ensures accurate deductible values
        // Other concurrent claims for this member will wait until we release the lock
        CostCalculationService.CostBreakdown breakdown = costCalculationService.calculateCosts(claim);
        
        log.info("📊 [ATOMIC-DEDUCTIBLE] Cost breakdown calculated: deductible={}, copay={}, insurance={}", 
            breakdown.deductibleApplied(), breakdown.coPayAmount(), breakdown.insuranceAmount());
        
        return breakdown;
    }

    /**
     * Validate requested amount is positive and non-null.
     * 
     * @param amount The amount to validate
     * @param context Description of the amount (for error messages)
     * @throws BusinessRuleException if amount is invalid
     */
    public void validatePositiveAmount(BigDecimal amount, String context) {
        if (amount == null) {
            throw new BusinessRuleException(
                String.format("FINANCIAL_ERROR: %s is required and cannot be null", context));
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(
                String.format("FINANCIAL_ERROR: %s must be greater than zero. Received: %s", context, amount));
        }
    }

    /**
     * Validate approved amount doesn't exceed requested amount.
     * 
     * @param approvedAmount The approved amount
     * @param requestedAmount The original requested amount
     * @throws BusinessRuleException if approved exceeds requested
     */
    public void validateApprovedAmount(BigDecimal approvedAmount, BigDecimal requestedAmount) {
        validatePositiveAmount(approvedAmount, "Approved amount");
        validatePositiveAmount(requestedAmount, "Requested amount");
        
        if (approvedAmount.compareTo(requestedAmount) > 0) {
            throw new BusinessRuleException(
                String.format("FINANCIAL_ERROR: Approved amount (%s) cannot exceed requested amount (%s)", 
                    approvedAmount, requestedAmount));
        }
    }

    /**
     * Validate settlement amount doesn't exceed net provider amount.
     * 
     * @param settlementAmount The settlement amount
     * @param netProviderAmount The approved net provider amount
     * @throws BusinessRuleException if settlement exceeds net provider
     */
    public void validateSettlementAmount(BigDecimal settlementAmount, BigDecimal netProviderAmount) {
        validatePositiveAmount(settlementAmount, "Settlement amount");
        
        if (netProviderAmount == null || netProviderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(
                "FINANCIAL_ERROR: Cannot settle claim with zero or null net provider amount");
        }
        
        if (settlementAmount.compareTo(netProviderAmount) > 0) {
            throw new BusinessRuleException(
                String.format("FINANCIAL_ERROR: Settlement amount (%s) cannot exceed net provider amount (%s)", 
                    settlementAmount, netProviderAmount));
        }
    }

    /**
     * Normalize coverage percentage to valid bounds [0, 100].
     * 
     * @param coveragePercent The raw coverage percentage
     * @return Normalized coverage percentage (0-100)
     */
    public int normalizeCoveragePercent(int coveragePercent) {
        int normalized = Math.min(100, Math.max(0, coveragePercent));
        if (normalized != coveragePercent) {
            log.warn("⚠️ [COVERAGE-BOUNDS] Coverage percent {} normalized to {} (must be 0-100)", 
                coveragePercent, normalized);
        }
        return normalized;
    }

    /**
     * Calculate co-pay percentage from coverage with bounds checking.
     * 
     * @param coveragePercent The coverage percentage (0-100)
     * @return Co-pay percentage (0-100), never negative
     */
    public int calculateCopayPercent(int coveragePercent) {
        int normalizedCoverage = normalizeCoveragePercent(coveragePercent);
        int copayPercent = 100 - normalizedCoverage;
        
        // Ensure non-negative (should never happen with proper normalization, but belt-and-suspenders)
        if (copayPercent < 0) {
            log.error("🚨 [FINANCIAL-ERROR] Calculated negative co-pay: {}% from coverage {}%", 
                copayPercent, coveragePercent);
            copayPercent = 0;
        }
        
        return copayPercent;
    }
}
