package com.waad.tba.modules.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Row-level bean for the Jasper claim audit report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimAuditFlatDto {
    private Long claimId;
    private String claimNo;
    private String originNo;
    private String insuranceNumber;
    private String patientName;
    private String complaint;
    private String diagnosis;
    private Date serviceDate;
    private String medicalService;
    private BigDecimal grossAmount;
    private BigDecimal netAmount;
    private BigDecimal rejectedAmount;
    private String rejectionReason;
    private Integer lineNo;
}
