package com.waad.tba.modules.eligibility.rules;

import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityReason;
import com.waad.tba.modules.eligibility.domain.EligibilityRule;
import com.waad.tba.modules.eligibility.domain.RuleResult;
import com.waad.tba.modules.member.entity.Member;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rule: Member Card Valid
 * Phase E1 - Eligibility Engine
 * 
 * Validates that the member's card is in a valid status (ACTIVE).
 * This is a hard rule - failure stops evaluation.
 * 
 * Priority: 25
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Component
@Order(25)
public class MemberCardValidRule implements EligibilityRule {

    @Override
    public String getRuleCode() {
        return "MEMBER_CARD_VALID";
    }

    @Override
    public String getNameAr() {
        return "التحقق من صلاحية بطاقة العضو";
    }

    @Override
    public int getPriority() {
        return 25;
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
        Member.CardStatus cardStatus = member.getCardStatus();

        if (cardStatus == null) {
            // If no card status, assume active (backwards compatibility)
            return RuleResult.pass("Card status not set, assuming active");
        }

        switch (cardStatus) {
            case ACTIVE:
                return RuleResult.pass();
            
            case BLOCKED:
                return RuleResult.fail(
                    EligibilityReason.MEMBER_CARD_BLOCKED,
                    member.getBlockedReason() != null ? 
                        "Reason: " + member.getBlockedReason() : 
                        "Card number: " + member.getCardNumber()
                );
            
            case EXPIRED:
                return RuleResult.fail(
                    EligibilityReason.MEMBER_CARD_EXPIRED,
                    "Card number: " + member.getCardNumber()
                );
            
            case INACTIVE:
            default:
                return RuleResult.fail(
                    EligibilityReason.MEMBER_INACTIVE,
                    "Card status: " + cardStatus
                );
        }
    }
}
