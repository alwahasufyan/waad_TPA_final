package com.waad.tba.modules.eligibility.rules;

import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityReason;
import com.waad.tba.modules.eligibility.domain.EligibilityRule;
import com.waad.tba.modules.eligibility.domain.RuleResult;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Rule: Service Date Validity
 * Phase E1 - Eligibility Engine
 * 
 * Validates that the service date is valid and not too far in the future.
 * This is a SOFT rule for future dates (warning), HARD for invalid dates.
 * 
 * Priority: 5 (evaluated very early)
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Component
@Order(5)
public class ServiceDateValidRule implements EligibilityRule {

    // Maximum days in the future allowed (for pre-authorization)
    private static final int MAX_FUTURE_DAYS = 90;

    @Override
    public String getRuleCode() {
        return "SERVICE_DATE_VALID";
    }

    @Override
    public String getNameAr() {
        return "التحقق من تاريخ الخدمة";
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public boolean isHardRule() {
        // This is technically hard for invalid dates, but soft for future dates
        // We handle this in evaluate() by returning appropriate reasons
        return true;
    }

    @Override
    public RuleResult evaluate(EligibilityContext context) {
        LocalDate serviceDate = context.getServiceDate();

        if (serviceDate == null) {
            return RuleResult.fail(
                EligibilityReason.SERVICE_DATE_INVALID,
                "Service date is required"
            );
        }

        LocalDate today = LocalDate.now();
        LocalDate maxFutureDate = today.plusDays(MAX_FUTURE_DAYS);

        // Check if date is too far in the past (more than 2 years)
        LocalDate minPastDate = today.minusYears(2);
        if (serviceDate.isBefore(minPastDate)) {
            return RuleResult.fail(
                EligibilityReason.SERVICE_DATE_INVALID,
                String.format(
                    "Service date %s is too far in the past (max 2 years)",
                    serviceDate
                )
            );
        }

        // Check if date is in the future
        if (serviceDate.isAfter(today)) {
            // Future date - this is a warning, not a hard failure
            // This allows pre-authorization scenarios
            if (serviceDate.isAfter(maxFutureDate)) {
                return RuleResult.fail(
                    EligibilityReason.SERVICE_DATE_IN_FUTURE,
                    String.format(
                        "Service date %s is too far in the future (max %d days)",
                        serviceDate,
                        MAX_FUTURE_DAYS
                    )
                );
            }
            
            // Within acceptable future range
            return RuleResult.pass(
                String.format("Future service date accepted for pre-authorization (%s)", serviceDate)
            );
        }

        return RuleResult.pass();
    }
}
