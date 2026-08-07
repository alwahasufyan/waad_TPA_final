package com.waad.tba.modules.providercontract.service;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAAD-PROVIDER-CONTRACT-OPTIMISTIC-LOCK-1 (scheduler follow-up): proves
 * {@link ProviderContractService#markExpiredContracts()} isolates each
 * contract into its own REQUIRES_NEW transaction, so a single row's
 * optimistic-lock conflict (a concurrent edit landing on that exact contract
 * while the batch runs) is caught and skipped rather than aborting the
 * entire batch. Before this fix, one @Transactional loop meant a single
 * OptimisticLockingFailureException on any row would roll back every
 * contract's expiration for that run.
 *
 * The conflict is made deterministic (not timing-luck-based) by having a
 * background thread hold a real PESSIMISTIC_WRITE row lock on the target
 * contract for the whole window the scheduler thread is running — Postgres
 * guarantees the scheduler's UPDATE for that row blocks until the lock is
 * released, and by then the row's version has already moved, so Hibernate's
 * version-checked UPDATE is guaranteed to match zero rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class ProviderContractSchedulerExpirationTest {

    @Autowired private ProviderContractService providerContractService;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private Long normalContractId;
    private Long conflictContractId;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime()).substring(9);

        Provider normalProvider = providerRepository.save(Provider.builder()
                .name("Scheduler Test Clinic Normal").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-SCHED-N-" + suffix).active(true).build());
        Provider conflictProvider = providerRepository.save(Provider.builder()
                .name("Scheduler Test Clinic Conflict").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-SCHED-C-" + suffix).active(true).build());

        ProviderContract normalContract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-SCHED-N-" + suffix)
                .provider(normalProvider)
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.now().minusMonths(2))
                .endDate(LocalDate.now().minusDays(1))
                .build());
        ProviderContract conflictContract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-SCHED-C-" + suffix)
                .provider(conflictProvider)
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.now().minusMonths(2))
                .endDate(LocalDate.now().minusDays(1))
                .build());

        normalContractId = normalContract.getId();
        conflictContractId = conflictContract.getId();

        // Real commit: the background lock-holding thread and the scheduler
        // call below both need this setup data visible from their own
        // separate REQUIRES_NEW transactions/connections.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    @Test
    void oneRowConflict_isSkipped_otherExpiredContractsStillSucceed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate lockHolderTx = new TransactionTemplate(transactionManager);

        try {
            // Background thread: acquire a real row lock on conflictContract and
            // hold it open for the whole window markExpiredContracts() runs in —
            // its UPDATE for this row is guaranteed to block until this releases.
            Future<?> lockHolder = pool.submit(() -> lockHolderTx.executeWithoutResult(status -> {
                ProviderContract locked = entityManager.find(ProviderContract.class, conflictContractId,
                        LockModeType.PESSIMISTIC_WRITE);
                lockHeld.countDown();
                try {
                    releaseLock.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Bump the version before releasing the lock, so the scheduler's
                // blocked UPDATE (built against the pre-bump version it already
                // read) is guaranteed to match zero rows once it unblocks.
                locked.setNotes("Concurrently edited while scheduler was running");
                contractRepository.save(locked);
            }));

            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> schedulerRun = pool.submit(() -> providerContractService.markExpiredContracts());

            // Generous margin for the scheduler thread to reach and issue its
            // (blocking) UPDATE for conflictContract — local Testcontainers
            // operations complete in single-digit milliseconds, so this is a
            // large safety margin, not a tight race.
            Thread.sleep(500);
            releaseLock.countDown();

            lockHolder.get(15, TimeUnit.SECONDS);
            // Not asserted against a specific count here: Testcontainers reuses
            // one shared database across every test in the suite, so the
            // aggregate count can include expired-but-still-ACTIVE contracts
            // left over by other tests. The per-contract checks below are the
            // real assertion — they verify this test's own two contracts by ID.
            schedulerRun.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        ProviderContract normalReloaded = contractRepository.findById(normalContractId).orElseThrow();
        ProviderContract conflictReloaded = contractRepository.findById(conflictContractId).orElseThrow();

        assertThat(normalReloaded.getStatus()).isEqualTo(ContractStatus.EXPIRED);
        // The conflicting contract was NOT expired — its concurrent edit won,
        // and it will simply be picked up again on the next scheduled run.
        assertThat(conflictReloaded.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(conflictReloaded.getNotes()).isEqualTo("Concurrently edited while scheduler was running");
    }

    @Test
    void noConflicts_allExpiredContractsSucceed() throws Exception {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        // Not asserted against a specific count: see the note in the
        // conflict test above — the shared Testcontainers database can carry
        // expired-but-still-ACTIVE contracts left over by other tests.
        providerContractService.markExpiredContracts();

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(contractRepository.findById(normalContractId).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.EXPIRED);
        assertThat(contractRepository.findById(conflictContractId).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.EXPIRED);
    }
}
