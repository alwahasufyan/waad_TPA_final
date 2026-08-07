package com.waad.tba.modules.providercontract.repository;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WAAD-PROVIDER-CONTRACT-OPTIMISTIC-LOCK-1: proves ProviderContract's new
 * @Version column genuinely triggers Hibernate's optimistic-lock check under
 * real concurrent modification (two admins editing the same contract at
 * once), mirroring the established pattern in
 * PreAuthorizationMultiLineIntegrationTest.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class ProviderContractOptimisticLockingTest {

    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long contractId;

    @BeforeEach
    void setUp() {
        Provider provider = providerRepository.save(Provider.builder()
                .name("Optimistic Lock Test Clinic").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-OL-" + System.nanoTime()).active(true).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-OL-" + System.nanoTime())
                .provider(provider)
                .status(ContractStatus.DRAFT)
                .startDate(LocalDate.now())
                .build());
        contractId = contract.getId();

        // Same pattern as PreAuthorizationMultiLineIntegrationTest: force a
        // real commit so the REQUIRES_NEW sub-transactions below can see
        // this setup data under READ COMMITTED.
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @Test
    void concurrentContractEdit_staleVersionIsRejectedByOptimisticLock() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Transaction A: load the contract, keep it in memory (stale after B commits).
        ProviderContract contractA = tx.execute(status -> contractRepository.findById(contractId).orElseThrow());

        // Transaction B: load the SAME row independently and commit a change — bumps version.
        tx.execute(status -> {
            ProviderContract contractB = contractRepository.findById(contractId).orElseThrow();
            contractB.setNotes("Changed by transaction B");
            return null;
        });

        // Transaction A now tries to save its stale (pre-B) in-memory copy.
        assertThatThrownBy(() -> tx.execute(status -> {
            contractA.setNotes("Changed by stale transaction A");
            contractRepository.save(contractA);
            return null;
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void sequentialEdits_incrementVersionAndSucceed() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long initialVersion = tx.execute(status -> contractRepository.findById(contractId).orElseThrow().getVersion());

        tx.execute(status -> {
            ProviderContract contract = contractRepository.findById(contractId).orElseThrow();
            contract.setNotes("First sequential edit");
            return null;
        });

        Long versionAfterFirstEdit = tx.execute(status -> contractRepository.findById(contractId).orElseThrow().getVersion());

        assertThat(versionAfterFirstEdit).isGreaterThan(initialVersion);
    }
}
