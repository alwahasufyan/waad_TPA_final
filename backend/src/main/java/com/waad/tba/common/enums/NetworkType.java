package com.waad.tba.common.enums;

import java.math.BigDecimal;

/**
 * Provider network classification for insurance coverage purposes.
 * 
 * IN_NETWORK providers have contracts with the insurance company
 * and offer negotiated rates with higher coverage.
 * 
 * OUT_OF_NETWORK providers have no contract, resulting in:
 * - Lower coverage percentage
 * - Higher patient co-pay
 * - Possible balance billing
 * 
 * @since Phase 7 - Operational Completeness
 */
public enum NetworkType {
    
    /**
     * Provider has an active contract with the insurance company.
     * Benefits: Higher coverage, lower co-pay, no balance billing.
     */
    IN_NETWORK("ضمن الشبكة", new BigDecimal("80.00"), new BigDecimal("20.00")),
    
    /**
     * Provider has no contract with the insurance company.
     * Benefits: Lower coverage, higher co-pay, potential balance billing.
     */
    OUT_OF_NETWORK("خارج الشبكة", new BigDecimal("60.00"), new BigDecimal("40.00"));
    
    private final String arabicLabel;
    
    /** Default coverage percentage for this network type */
    private final BigDecimal defaultCoveragePercent;
    
    /** Default co-pay percentage for this network type */
    private final BigDecimal defaultCoPayPercent;
    
    NetworkType(String arabicLabel, BigDecimal defaultCoveragePercent, BigDecimal defaultCoPayPercent) {
        this.arabicLabel = arabicLabel;
        this.defaultCoveragePercent = defaultCoveragePercent;
        this.defaultCoPayPercent = defaultCoPayPercent;
    }
    
    public String getArabicLabel() {
        return arabicLabel;
    }
    
    /**
     * Get the default insurance coverage percentage for this network type.
     * Example: IN_NETWORK = 80% coverage, OUT_OF_NETWORK = 60% coverage
     * 
     * @return Coverage percentage (0-100)
     */
    public BigDecimal getDefaultCoveragePercent() {
        return defaultCoveragePercent;
    }
    
    /**
     * Get the default patient co-pay percentage for this network type.
     * Example: IN_NETWORK = 20% co-pay, OUT_OF_NETWORK = 40% co-pay
     * 
     * @return Co-pay percentage (0-100)
     */
    public BigDecimal getDefaultCoPayPercent() {
        return defaultCoPayPercent;
    }
    
    /**
     * Calculate the coverage amount based on this network type.
     * 
     * @param totalAmount The total claim amount
     * @return The amount covered by insurance
     */
    public BigDecimal calculateCoverageAmount(BigDecimal totalAmount) {
        if (totalAmount == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.multiply(defaultCoveragePercent).divide(new BigDecimal("100"));
    }
    
    /**
     * Calculate the co-pay amount based on this network type.
     * 
     * @param totalAmount The total claim amount
     * @return The amount the patient must pay (co-pay)
     */
    public BigDecimal calculateCoPayAmount(BigDecimal totalAmount) {
        if (totalAmount == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.multiply(defaultCoPayPercent).divide(new BigDecimal("100"));
    }
}
