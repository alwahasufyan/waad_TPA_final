package com.waad.tba.modules.eligibility.rules;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityReason;
import com.waad.tba.modules.eligibility.domain.EligibilityRule;
import com.waad.tba.modules.eligibility.domain.RuleResult;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Rule: Policy Coverage Period
 * Phase E1 - Eligibility Engine
 * 
 * Validates that the service date falls within the BenefitPolicy coverage period.
 * This is a hard rule - failure stops evaluation.
 * 
 * Priority: 50
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Component
@Order(50)
public class PolicyCoveragePeriodRule implements EligibilityRule {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public String getRuleCode() {
        return "POLICY_COVERAGE_PERIOD";
    }

    @Override
    public String getNameAr() {
        return "التحقق من فترة التغطية";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean isHardRule() {
        return true;
    }

    @Override
    public boolean isApplicable(EligibilityContext context) {
        return context.hasBenefitPolicy() && context.getServiceDate() != null;
    }

    @Override
    public RuleResult evaluate(EligibilityContext context) {
        LocalDate serviceDate = context.getServiceDate();
        
        // Check BenefitPolicy (canonical source)
        if (context.hasBenefitPolicy()) {
            return evaluateBenefitPolicy(context.getBenefitPolicy(), serviceDate);
        }
        
        return RuleResult.fail(
            EligibilityReason.POLICY_NOT_FOUND,
            "No BenefitPolicy to evaluate coverage period"
        );
    }
    
    private RuleResult evaluateBenefitPolicy(BenefitPolicy benefitPolicy, LocalDate serviceDate) {
        LocalDate startDate = benefitPolicy.getStartDate();
        LocalDate endDate = benefitPolicy.getEndDate();
        
        // Check start date
        if (startDate == null) {
            return RuleResult.fail(
                EligibilityReason.POLICY_INACTIVE,
                "BenefitPolicy has no start date defined"
            );
        }
        
        // Check end date
        if (endDate == null) {
            return RuleResult.fail(
                EligibilityReason.POLICY_INACTIVE,
                "BenefitPolicy has no end date defined"
            );
        }
        
        // Service date before coverage start
        if (serviceDate.isBefore(startDate)) {
            return RuleResult.fail(
                EligibilityReason.SERVICE_DATE_BEFORE_COVERAGE,
                String.format(
                    "Service date: %s, BenefitPolicy coverage starts: %s",
                    serviceDate.format(DATE_FORMAT),
                    startDate.format(DATE_FORMAT)
                )
            );
        }
        
        // Service date after coverage end
        if (serviceDate.isAfter(endDate)) {
            return RuleResult.fail(
                EligibilityReason.SERVICE_DATE_AFTER_COVERAGE,
                String.format(
                    "Service date: %s, BenefitPolicy coverage ends: %s",
                    serviceDate.format(DATE_FORMAT),
                    endDate.format(DATE_FORMAT)
                )
            );
        }
        
        return RuleResult.pass(
            String.format("BenefitPolicy coverage: %s to %s", startDate.format(DATE_FORMAT), endDate.format(DATE_FORMAT))
        );
    }
}
