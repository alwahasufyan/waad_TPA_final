package com.waad.tba.modules.providercontract.scheduler;

import com.waad.tba.modules.providercontract.service.ProviderContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for Provider Contract management.
 *
 * Executes daily auto-expiration of contracts that have passed their end
 * date. ProviderContractService.markExpiredContracts() previously existed
 * but was never invoked by anything — contracts only transitioned to
 * EXPIRED reactively (e.g. on the next manual activation attempt), so a
 * contract past its end date could remain ACTIVE indefinitely if nobody
 * touched it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderContractScheduler {

    private final ProviderContractService providerContractService;

    /**
     * Auto-expire contracts daily at 1 AM.
     *
     * Finds all ACTIVE contracts where endDate < today and transitions them
     * to EXPIRED status.
     *
     * Schedule: Daily at 01:00:00 AM
     * Cron: "0 0 1 * * *" (second minute hour day month weekday)
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void autoExpireContracts() {
        log.info("======= SCHEDULED JOB: Auto-expiring provider contracts =======");

        try {
            int expiredCount = providerContractService.markExpiredContracts();

            if (expiredCount > 0) {
                log.info("✅ Auto-expiration completed: {} contracts expired", expiredCount);
            } else {
                log.debug("No contracts to expire");
            }

        } catch (Exception e) {
            log.error("❌ Error during auto-expiration of provider contracts", e);
            // Don't throw - let the scheduler continue
        }

        log.info("======= SCHEDULED JOB COMPLETE =======");
    }
}
