package com.waad.tba.modules.eligibility.rules;

import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityReason;
import com.waad.tba.modules.eligibility.domain.EligibilityRule;
import com.waad.tba.modules.eligibility.domain.RuleResult;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rule: Policy Exists
 * Phase E1 - Eligibility Engine
 * 
 * Validates that the member has a BenefitPolicy assigned.
 * This is a hard rule - failure stops evaluation.
 * 
 * Priority: 30
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Component
@Order(30)
public class PolicyExistsRule implements EligibilityRule {

    @Override
    public String getRuleCode() {
        return "POLICY_EXISTS";
    }

    @Override
    public String getNameAr() {
        return "التحقق من وجود الوثيقة";
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean isHardRule() {
        return true;
    }

    @Override
    public RuleResult evaluate(EligibilityContext context) {
        // Check for BenefitPolicy (only policy model)
        if (!context.hasBenefitPolicy()) {
            return RuleResult.fail(
                EligibilityReason.POLICY_NOT_FOUND,
                context.getMemberId() != null ? 
                    "No benefit policy found for member ID: " + context.getMemberId() : 
                    "No benefit policy found"
            );
        }
        
        return RuleResult.pass("BenefitPolicy found: " + context.getBenefitPolicy().getName());
    }
}
