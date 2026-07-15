package com.waad.tba.modules.providercontract.scheduler;

import com.waad.tba.modules.providercontract.service.ProviderContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps ACTIVE contract status aligned with the contract end date. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderContractScheduler {

    private final ProviderContractService providerContractService;

    @Scheduled(cron = "${waad.provider-contracts.expiration-cron:0 10 1 * * *}")
    public void autoExpireContracts() {
        try {
            int expired = providerContractService.markExpiredContracts();
            if (expired > 0) {
                log.info("Provider contract expiration completed: expiredCount={}", expired);
            } else {
                log.debug("Provider contract expiration completed: no expired contracts");
            }
        } catch (Exception exception) {
            // A maintenance job must never terminate Spring's scheduling thread.
            log.error("Provider contract expiration job failed", exception);
        }
    }
}
