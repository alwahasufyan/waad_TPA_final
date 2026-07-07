package com.waad.tba.modules.member.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CoverageLimitsDto {
    private boolean covered;
    private int coveragePercent;
    
    private BigDecimal amountLimit; // Per claim limit if any
    
    private Integer timesLimit;     // Total times allowed
    private int timesUsed;          // Times already used
    private int remainingTimes;     // Remaining times
    
    private boolean timesLimitExceeded;
    private String warningMessage;
}
