package com.waad.tba.modules.settlement.service;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.service.ClaimService;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAAD-PROVIDER-CREDIT-INTEGRITY-1 regression coverage.
 *
 * Confirmed defect: ClaimService.createClaim() used to publish
 * ClaimApprovedEvent (which credits ProviderAccount) based solely on
 * {@code netPayableAmount > 0}, with no check that the claim's saved status
 * was actually APPROVED. Since Claim.calculateFields()'s draft-time preview
 * always produces a positive estimate for almost any claim, a claim merely
 * SUBMITTED for review (awaiting a reviewer) could credit the provider before
 * anyone approved anything.
 *
 * WAAD-INTEGRATION-TEST-CONTEXT-1: the crediting/reversal listeners are
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} — they only fire
 * on a genuine physical commit. Every test here that needs to observe an
 * event-driven credit/reversal therefore forces a real commit via
 * {@code TestTransaction.flagForCommit()+end()+start()} (see
 * ClaimLifecycleIntegrationTest for the same established pattern) —
 * otherwise the assertions would pass "for free" regardless of whether the
 * fix actually works, since nothing would ever committing means the listener
 * never runs either way. Direct calls to
 * {@code ClaimFinancialSyncService.creditForClaim(...)} do NOT need this
 * dance — that method is {@code @Transactional(REQUIRES_NEW)} and commits
 * independently of the outer test transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class ProviderAccountCreditIntegrityTest {

    @Autowired private ClaimService claimService;
    @Autowired private ClaimFinancialSyncService claimFinancialSyncService;
    @Autowired private ProviderAccountRepository providerAccountRepository;
    @Autowired private AccountTransactionRepository accountTransactionRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private Visit visit;
    private ProviderContractPricingItem pricingItem;
    private Long categoryId;
    private Long providerId;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("credit-integrity-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("credit-integrity-admin").password("password").fullName("Admin")
                    .email("credit-integrity-admin@waad.ly").userType("SUPER_ADMIN").active(true).build());
        }

        String suffix = String.valueOf(System.nanoTime()).substring(9);
        Employer employer = employerRepository.save(Employer.builder()
                .name("Credit Integrity Co").code("EMP-CI-" + suffix).active(true).build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Credit Integrity Plan").policyCode("POL-CI-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-CI-" + suffix).name("Credit Integrity Category").active(true).build());
        categoryId = category.getId();

        // 100% coverage, no benefit limit — keeps the payable amount a clean,
        // trivially-asserted number (netProviderAmount == requestedTotal) so
        // these tests are purely about crediting integrity, not the split math
        // covered by WAAD-CLAIMS-FINANCIAL-CORRECTNESS-1's own tests.
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .coveragePercent(100).active(true).build());

        Member member = memberRepository.save(Member.builder()
                .fullName("Credit Integrity Member").barcode("CI-" + suffix)
                .nationalNumber("CI-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());

        Provider provider = providerRepository.save(Provider.builder()
                .name("Credit Integrity Clinic").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-CI-" + suffix).allowAllEmployers(true).active(true).build());
        providerId = provider.getId();

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-CI-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());

        pricingItem = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode("SRV-CI-" + suffix).serviceName("Credit Integrity Service")
                .medicalCategory(category)
                .basePrice(new BigDecimal("100")).contractPrice(new BigDecimal("100"))
                .active(true).build());

        providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).build());

        visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
    }

    private ClaimCreateDto.ClaimCreateDtoBuilder baseClaimBuilder(ClaimStatus status) {
        return ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(categoryId)
                        .unitPrice(new BigDecimal("100"))
                        .quantity(1)
                        .build()))
                .status(status);
    }

    /** Forces a real commit so AFTER_COMMIT listeners actually run, then clears
     * the persistence context and opens a fresh transaction for observation —
     * see the class-level Javadoc for why this is required. */
    private void commitAndRestartTransaction() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        entityManager.clear();
    }

    private ProviderAccount refreshedAccount() {
        return providerAccountRepository.findByProviderId(providerId).orElseThrow();
    }

    private long approvalTxCount(Long claimId) {
        return accountTransactionRepository.countByReferenceTypeAndReferenceId(ReferenceType.CLAIM_APPROVAL, claimId);
    }

    private long reversalTxCount(Long claimId) {
        return accountTransactionRepository.countByReferenceTypeAndReferenceId(ReferenceType.CLAIM_REVERSAL, claimId);
    }

    @Test
    @WithMockUser(username = "credit-integrity-admin", roles = "SUPER_ADMIN")
    void submittedClaim_doesNotCreditProvider() {
        ClaimViewDto claim = claimService.createClaim(baseClaimBuilder(ClaimStatus.SUBMITTED).build());
        commitAndRestartTransaction();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("0.00");
        assertThat(approvalTxCount(claim.getId())).isZero();
    }

    @Test
    @WithMockUser(username = "credit-integrity-admin", roles = "SUPER_ADMIN")
    void draftClaim_doesNotCreditProvider() {
        ClaimViewDto claim = claimService.createClaim(baseClaimBuilder(ClaimStatus.DRAFT).build());
        commitAndRestartTransaction();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.DRAFT);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("0.00");
        assertThat(approvalTxCount(claim.getId())).isZero();
    }

    @Test
    @WithMockUser(username = "credit-integrity-admin", roles = "SUPER_ADMIN")
    void needsCorrectionClaim_doesNotCreditProvider() {
        ClaimViewDto claim = claimService.createClaim(baseClaimBuilder(ClaimStatus.NEEDS_CORRECTION).build());
        commitAndRestartTransaction();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.NEEDS_CORRECTION);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("0.00");
        assertThat(approvalTxCount(claim.getId())).isZero();
    }

    @Test
    @WithMockUser(username = "credit-integrity-admin", roles = "SUPER_ADMIN")
    void rejectedClaim_doesNotCreditProvider() {
        ClaimViewDto claim = claimService.createClaim(
                baseClaimBuilder(ClaimStatus.REJECTED).rejectionReason("Test rejection at creation").build());
        commitAndRestartTransaction();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("0.00");
        assertThat(approvalTxCount(claim.getId())).isZero();
    }

    @Test
    @WithMockUser(username = "credit-integrity-admin", roles = "SUPER_ADMIN")
    void approvedClaim_creditsExactlyOnce() {
        ClaimViewDto claim = claimService.createClaim(baseClaimBuilder(ClaimStatus.APPROVED).build());
        commitAndRestartTransaction();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(approvalTxCount(claim.getId())).isEqualTo(1);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @WithMockUser(username = "credit-integrity-admin", roles = "SUPER_ADMIN")
    void repeatedApprovalRetry_doesNotDoubleCredit() {
        ClaimViewDto claim = claimService.createClaim(baseClaimBuilder(ClaimStatus.APPROVED).build());
        commitAndRestartTransaction();
        assertThat(approvalTxCount(claim.getId())).isEqualTo(1);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("100.00");

        // Simulate a retried/duplicate ClaimApprovedEvent delivery (e.g. a message
        // redelivery, or a caller invoking the sync path a second time). This
        // method is @Transactional(REQUIRES_NEW) — it commits independently of
        // the outer test transaction, exactly like the real AFTER_COMMIT listener
        // invocation would, so no extra TestTransaction dance is needed here.
        claimFinancialSyncService.creditForClaim(claim.getId(), null);
        claimFinancialSyncService.creditForClaim(claim.getId(), null);

        assertThat(approvalTxCount(claim.getId())).isEqualTo(1);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @WithMockUser(username = "credit-integrity-admin", roles = "SUPER_ADMIN")
    void reversalAndReapproval_followsExistingLedgerRules() {
        // NOTE: ClaimService.updateClaimData()'s "FIX #11" admin-reject-an-
        // approved-claim path (APPROVED -> REJECTED) calls
        // claimStateMachine.transition(claim, REJECTED, ...), but the
        // TRANSITION_MATRIX in ClaimStateMachine does not actually allow
        // APPROVED -> REJECTED (only SETTLED/BATCHED/NEEDS_CORRECTION are
        // reachable from APPROVED) — that path always throws
        // ClaimStateTransitionException today. This is a separate, pre-existing
        // bug outside today's scope (reported separately), so this test
        // exercises the OTHER real, working reversal/re-approval cycle instead:
        // soft-delete of an APPROVED claim (ClaimReversalEvent, debit) followed
        // by restore (ClaimApprovedEvent, re-credit) — see
        // ClaimService.deleteClaim()/restoreClaim().
        ClaimViewDto claim = claimService.createClaim(baseClaimBuilder(ClaimStatus.APPROVED).build());
        commitAndRestartTransaction();
        assertThat(approvalTxCount(claim.getId())).isEqualTo(1);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("100.00");

        // Soft-delete an APPROVED claim: must reverse (debit) the earlier credit.
        claimService.deleteClaim(claim.getId(), "Reversing for ledger-rules test");
        commitAndRestartTransaction();

        assertThat(reversalTxCount(claim.getId())).isEqualTo(1);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("0.00");

        // Restore the soft-deleted, previously-approved claim: must credit again.
        claimService.restoreClaim(claim.getId());
        commitAndRestartTransaction();

        assertThat(approvalTxCount(claim.getId())).isEqualTo(2);
        assertThat(reversalTxCount(claim.getId())).isEqualTo(1);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("100.00");

        // Second full cycle: delete/restore again. This is exactly what a live
        // Docker end-to-end run caught — AccountTransactionService.createClaimReversalDebit()
        // and .createClaimApprovedCredit() each used to reject creation whenever
        // ANY prior row of that reference type existed for the claim at all,
        // regardless of cycle count, which made a SECOND reversal (or credit)
        // impossible even though ProviderAccountService's own cycle-aware
        // approvalCount/reversalCount checks correctly allowed it. Both blanket
        // checks were removed as redundant with (and inconsistent with) those
        // already-correct caller-side checks.
        claimService.deleteClaim(claim.getId(), "Second reversal for ledger-rules test");
        commitAndRestartTransaction();

        assertThat(reversalTxCount(claim.getId())).isEqualTo(2);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("0.00");

        claimService.restoreClaim(claim.getId());
        commitAndRestartTransaction();

        assertThat(approvalTxCount(claim.getId())).isEqualTo(3);
        assertThat(reversalTxCount(claim.getId())).isEqualTo(2);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @WithMockUser(username = "credit-integrity-admin", roles = "SUPER_ADMIN")
    void zeroPayableApprovedClaim_doesNotCreateIncorrectCredit() {
        // 0% coverage on this line -> patient bears 100%, netProviderAmount == 0.
        // A legitimate outcome (e.g. a benefit not covered at all) that must NOT
        // produce any credit transaction or balance change, and must not prevent
        // the claim itself from being created/approved.
        MedicalCategory zeroCoverageCategory = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-CI-ZERO-" + System.nanoTime()).name("Zero Coverage Category").active(true).build());
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(visit.getMember().getBenefitPolicy())
                .medicalCategory(zeroCoverageCategory)
                .coveragePercent(0).active(true).build());
        ProviderContractPricingItem zeroCoverageItem = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(pricingItem.getContract()).serviceCode("SRV-CI-ZERO-" + System.nanoTime())
                .serviceName("Zero Coverage Service").medicalCategory(zeroCoverageCategory)
                .basePrice(new BigDecimal("100")).contractPrice(new BigDecimal("100"))
                .active(true).build());

        ClaimCreateDto dto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(zeroCoverageItem.getId())
                        .serviceCode(zeroCoverageItem.getServiceCode())
                        .serviceCategoryId(zeroCoverageCategory.getId())
                        .unitPrice(new BigDecimal("100"))
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();

        ClaimViewDto claim = claimService.createClaim(dto);
        commitAndRestartTransaction();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("0.00");
        assertThat(approvalTxCount(claim.getId())).isZero();
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @WithMockUser(username = "credit-integrity-admin", roles = "SUPER_ADMIN")
    void partiallyRejectedApprovedClaim_creditsOnlyTheApprovedPayableAmount() {
        // Claims have no distinct PARTIALLY_APPROVED status (unlike
        // PreAuthorization) — "partial approval" is represented as a normal
        // APPROVED claim whose netProviderAmount already reflects only the
        // approved portion after a manual line rejection. Verify the credit
        // matches that reduced amount, not the full gross requested amount.
        ClaimCreateDto dto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(categoryId)
                        .unitPrice(new BigDecimal("100"))
                        .quantity(1)
                        .manualRefusedAmount(new BigDecimal("40"))
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();

        ClaimViewDto claim = claimService.createClaim(dto);
        commitAndRestartTransaction();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("60.00");
        assertThat(approvalTxCount(claim.getId())).isEqualTo(1);
        assertThat(refreshedAccount().getRunningBalance()).isEqualByComparingTo("60.00");
    }
}
