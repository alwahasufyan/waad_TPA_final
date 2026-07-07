package com.waad.tba.modules.benefitpolicy.scheduler;

import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for Benefit Policy management.
 * 
 * Executes daily auto-expiration of policies that have passed their end date.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BenefitPolicyScheduler {

    private final BenefitPolicyService benefitPolicyService;

    /**
     * Auto-expire policies daily at 1 AM.
     * 
     * Finds all ACTIVE policies where endDate < today and transitions them to EXPIRED status.
     * This ensures member eligibility checks always reflect current policy status.
     * 
     * Schedule: Daily at 01:00:00 AM
     * Cron: "0 0 1 * * *" (second minute hour day month weekday)
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void autoExpirePolicies() {
        log.info("======= SCHEDULED JOB: Auto-expiring benefit policies =======");
        
        try {
            int expiredCount = benefitPolicyService.expireOldPolicies();
            
            if (expiredCount > 0) {
                log.info("✅ Auto-expiration completed: {} policies expired", expiredCount);
            } else {
                log.debug("No policies to expire");
            }
            
        } catch (Exception e) {
            log.error("❌ Error during auto-expiration of benefit policies", e);
            // Don't throw - let the scheduler continue
        }
        
        log.info("======= SCHEDULED JOB COMPLETE =======");
    }
}
