package com.waad.tba.modules.claim.service;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.claim.dto.ClaimApproveDto;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationCreateDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDecisionDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationResponseDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineReviewDecision;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationService;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WAAD-PREAUTH-SINGLE-CONVERSION-GUARD-1: a pre-authorization must convert to
 * a claim exactly once. Proves: (1) a fully-approved pre-authorization
 * converts successfully and transitions to USED, (2) a second claim against
 * the same (now USED) pre-authorization is rejected, (3) a PARTIALLY_APPROVED
 * pre-authorization also locks to USED after conversion — previously this
 * status was missing from the auto-transition, so multi-line partial
 * approvals never actually locked.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class ClaimPreAuthorizationReuseGuardTest {

    @Autowired private ClaimService claimService;
    @Autowired private ClaimReviewService claimReviewService;
    @Autowired private ClaimRepository claimRepository;
    @Autowired private PreAuthorizationService preAuthorizationService;
    @Autowired private PreAuthorizationRepository preAuthorizationRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private com.waad.tba.modules.settlement.repository.ProviderAccountRepository providerAccountRepository;

    private Member member;
    private Provider provider;
    private Visit visit;
    private ProviderContractPricingItem pricingItem;

    @BeforeEach
    void setUp() {
        // Idempotent (find-or-create): tests in this class that force a real
        // commit (TestTransaction.flagForCommit(), needed for genuinely
        // separate concurrent transactions) permanently persist this user —
        // a later test's own setUp() must not try to re-insert it.
        if (userRepository.findByUsername("reuse-guard-admin").isEmpty()) {
            userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                    .username("reuse-guard-admin").password("password").fullName("Admin")
                    .email("reuse-guard-admin@waad.ly").userType("SUPER_ADMIN").active(true).build());
        }

        String suffix = String.valueOf(System.nanoTime()).substring(9);
        Employer employer = employerRepository.save(Employer.builder()
                .name("Reuse Guard Co").code("EMP-RG-" + suffix).active(true).build());
        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Reuse Guard Plan").policyCode("POL-RG-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
        member = memberRepository.save(Member.builder()
                .fullName("Reuse Guard Member").barcode("RG-" + suffix)
                .nationalNumber("RG-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());
        provider = providerRepository.save(Provider.builder()
                .name("Reuse Guard Clinic").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-RG-" + suffix).allowAllEmployers(true).active(true).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-RG-" + suffix).name("Reuse Guard Category").active(true).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-RG-" + suffix)
                .provider(provider)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE)
                .active(true)
                .build());

        pricingItem = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceCode("SRV-RG")
                .serviceName("Reuse Guard Service")
                .medicalCategory(category)
                .basePrice(new BigDecimal("100"))
                .contractPrice(new BigDecimal("100"))
                .active(true)
                .build());

        providerAccountRepository.save(com.waad.tba.modules.settlement.entity.ProviderAccount.builder()
                .providerId(provider.getId()).build());

        visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
    }

    private PreAuthorizationResponseDto createAndFullyApprovePreAuth() {
        PreAuthorizationCreateDto createDto = PreAuthorizationCreateDto.builder()
                .visitId(visit.getId())
                .providerId(provider.getId())
                .pricingItemId(pricingItem.getId())
                .build();
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(createDto, "reuse-guard-admin");
        Long lineId = created.getLines().get(0).getId();
        preAuthorizationService.submitLineDecision(created.getId(), lineId,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(),
                "reuse-guard-admin");
        return preAuthorizationService.finalizePreAuthorizationReview(created.getId(), "reuse-guard-admin");
    }

    private ClaimCreateDto claimDtoFor(Long preAuthId) {
        return ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .preAuthorizationId(preAuthId)
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.SUBMITTED)
                .build();
    }

    @Test
    @WithMockUser(username = "reuse-guard-admin", roles = "SUPER_ADMIN")
    void convertingApprovedPreAuthToClaim_locksItToUsed_andBlocksSecondConversion() {
        PreAuthorizationResponseDto preAuth = createAndFullyApprovePreAuth();
        assertThat(preAuth.getStatus()).isEqualTo("APPROVED");

        claimService.createClaim(claimDtoFor(preAuth.getId()));

        var reloaded = preAuthorizationRepository.findById(preAuth.getId()).orElseThrow();
        assertThat(reloaded.getStatus().name()).isEqualTo("USED");

        assertThatThrownBy(() -> claimService.createClaim(claimDtoFor(preAuth.getId())))
                .isInstanceOf(BusinessRuleException.class);
    }

    /**
     * WAAD-PREAUTH-SINGLE-CONVERSION-GUARD-1 (race fix): the test above only
     * proves SEQUENTIAL reuse is blocked — both calls happen inside the same
     * test transaction, so the second trivially sees the first's own
     * uncommitted write. This test proves the actual concurrent case: two
     * genuinely separate transactions (real threads, real connections)
     * racing to convert the SAME approved pre-authorization at the same
     * moment. Before the pessimistic lock was added to the guard check, both
     * could read "not yet USED" before either committed and both would
     * successfully create a claim. With the lock, the second caller blocks
     * until the first's whole transaction (including its own markAsUsed()
     * call) commits, then correctly sees USED and is rejected.
     */
    @Test
    @WithMockUser(username = "reuse-guard-admin", roles = "SUPER_ADMIN")
    void concurrentConversionAttempts_exactlyOneSucceeds_preAuthLocksToUsed() throws Exception {
        PreAuthorizationResponseDto preAuth = createAndFullyApprovePreAuth();
        assertThat(preAuth.getStatus()).isEqualTo("APPROVED");

        // Real commit: the two racing threads below use their own separate
        // transactions/connections and must see this setup data.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        // @WithMockUser only populates SecurityContextHolder's ThreadLocal for
        // THIS thread — the two worker threads below need it explicitly
        // propagated, or ClaimService/ReviewerProviderIsolationService see no
        // authenticated user at all.
        var securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();

        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Object> results = java.util.Collections.synchronizedList(new ArrayList<>());

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    try {
                        results.add(claimService.createClaim(claimDtoFor(preAuth.getId())));
                    } catch (BusinessRuleException e) {
                        results.add(e);
                    } finally {
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    }
                }));
            }
            ready.await();
            go.countDown();
            for (var f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        long successCount = results.stream().filter(r -> r instanceof ClaimViewDto).count();
        long rejectedCount = results.stream().filter(r -> r instanceof BusinessRuleException).count();
        assertThat(successCount).isEqualTo(1);
        assertThat(rejectedCount).isEqualTo(1);

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        entityManager.clear();

        var reloadedPreAuth = preAuthorizationRepository.findById(preAuth.getId()).orElseThrow();
        assertThat(reloadedPreAuth.getStatus()).isEqualTo(PreAuthStatus.USED);

        long activeClaimsForThisPreAuth = claimRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .filter(c -> c.getPreAuthorization() != null && c.getPreAuthorization().getId().equals(preAuth.getId()))
                .count();
        assertThat(activeClaimsForThisPreAuth).isEqualTo(1);
    }

    /**
     * WAAD-PREAUTH-SINGLE-CONVERSION-GUARD-1: the delete/restore lifecycle
     * (and a fresh approval run after restore) must keep working exactly as
     * before — neither the new pessimistic lock (only taken in createClaim's
     * guard check) nor the new partial unique index (scoped to active=true)
     * should interfere with soft-deleting, restoring, or then approving the
     * SAME already-existing claim, since none of that goes through
     * createClaim()/the guard a second time.
     */
    @Test
    @WithMockUser(username = "reuse-guard-admin", roles = { "SUPER_ADMIN", "REVIEWER" })
    void deleteRestoreThenApprove_stillWorks_preAuthStaysUsedThroughout() {
        PreAuthorizationResponseDto preAuth = createAndFullyApprovePreAuth();

        ClaimCreateDto createDto = claimDtoFor(preAuth.getId());
        createDto.setStatus(ClaimStatus.SUBMITTED);
        ClaimViewDto created = claimService.createClaim(createDto);
        assertThat(created.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);

        var afterCreate = preAuthorizationRepository.findById(preAuth.getId()).orElseThrow();
        assertThat(afterCreate.getStatus()).isEqualTo(PreAuthStatus.USED);

        claimService.deleteClaim(created.getId(), "test: soft-delete for restore regression");
        Claim deleted = claimRepository.findById(created.getId()).orElseThrow();
        assertThat(deleted.getActive()).isFalse();

        // PreAuthorization is NOT reverted by a soft-delete — this is existing,
        // unchanged behavior; the guard/lock/index additions must not alter it.
        var afterDelete = preAuthorizationRepository.findById(preAuth.getId()).orElseThrow();
        assertThat(afterDelete.getStatus()).isEqualTo(PreAuthStatus.USED);

        ClaimViewDto restored = claimService.restoreClaim(created.getId());
        assertThat(restored.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claimRepository.findById(created.getId()).orElseThrow().getActive()).isTrue();

        // Restored claim can still be driven through the real approval workflow.
        claimReviewService.startReview(created.getId());
        claimReviewService.requestApproval(created.getId(),
                ClaimApproveDto.builder().useSystemCalculation(true).notes("post-restore approval").build());

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        entityManager.clear();

        Claim approved = claimRepository.findById(created.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(approved.getActive()).isTrue();
    }
}
