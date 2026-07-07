package com.waad.tba.modules.eligibility.rules;

import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityReason;
import com.waad.tba.modules.eligibility.domain.EligibilityRule;
import com.waad.tba.modules.eligibility.domain.RuleResult;
import com.waad.tba.modules.member.entity.Member;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rule: Member Active
 * Phase E1 - Eligibility Engine
 * 
 * Validates that the member has an ACTIVE status.
 * This is a hard rule - failure stops evaluation.
 * 
 * Priority: 20
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Component
@Order(20)
public class MemberActiveRule implements EligibilityRule {

    @Override
    public String getRuleCode() {
        return "MEMBER_ACTIVE";
    }

    @Override
    public String getNameAr() {
        return "التحقق من حالة العضو";
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public boolean isHardRule() {
        return true;
    }

    @Override
    public boolean isApplicable(EligibilityContext context) {
        // Only applicable if member exists
        return context.hasMember();
    }

    @Override
    public RuleResult evaluate(EligibilityContext context) {
        Member member = context.getMember();
        Member.MemberStatus status = member.getStatus();

        if (status == null) {
            return RuleResult.fail(
                EligibilityReason.MEMBER_INACTIVE,
                "Member status is null"
            );
        }

        switch (status) {
            case ACTIVE:
                return RuleResult.pass();
            
            case SUSPENDED:
                return RuleResult.fail(
                    EligibilityReason.MEMBER_SUSPENDED,
                    "Member: " + member.getFullName()
                );
            
            case TERMINATED:
                return RuleResult.fail(
                    EligibilityReason.MEMBER_TERMINATED,
                    "Member: " + member.getFullName()
                );
            
            case PENDING:
            default:
                return RuleResult.fail(
                    EligibilityReason.MEMBER_INACTIVE,
                    "Current status: " + status
                );
        }
    }
}
