package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.claim.dto.ClaimApproveDto;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimSettleDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;

// WAAD-INTEGRATION-TEST-CONTEXT-1: real Spring context needs a real,
// migrated database — see IntegrationTestContainersConfig for why this is a
// Testcontainers-managed Postgres rather than the shared dev database.
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
public class ClaimLifecycleIntegrationTest {

        @Autowired
        private ClaimService claimService;

        @Autowired
        private ClaimReviewService claimReviewService;

        @Autowired
        private EmployerRepository employerRepository;

        @Autowired
        private BenefitPolicyRepository benefitPolicyRepository;

        @Autowired
        private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

        @Autowired
        private MemberRepository memberRepository;

        @Autowired
        private ProviderRepository providerRepository;

        @Autowired
        private ProviderContractRepository contractRepository;

        @Autowired
        private ProviderContractPricingItemRepository pricingRepository;

        @Autowired
        private MedicalServiceRepository medicalServiceRepository;

        @Autowired
        private MedicalCategoryRepository medicalCategoryRepository;

        @Autowired
        private VisitRepository visitRepository;

        @Autowired
        private com.waad.tba.modules.settlement.repository.ProviderAccountRepository providerAccountRepository;

        @Autowired
        private EntityManager entityManager;

        private Employer employer;
        private BenefitPolicy policy;
        private Member member;
        private Provider provider;
        private ProviderContract contract;
        private MedicalService service;
        private Visit visit;

        @BeforeEach
        void setupData() {
                // 0. User for auditing
                userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                                .username("admin")
                                .password("password")
                                .fullName("System Admin")
                                .email("admin@waad.ly")
                                .userType("SUPER_ADMIN")
                                .active(true)
                                .build());

                // 1. Employer
                employer = employerRepository.save(Employer.builder()
                                .name("Test Company")
                                .code("EMP-TEST")
                                .active(true)
                                .build());

                // 2. Benefit Policy
                policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                                .name("Standard Plan")
                                .policyCode("POL-TEST")
                                .employer(employer)
                                .annualLimit(new BigDecimal("50000"))
                                .defaultCoveragePercent(80)
                                .startDate(LocalDate.now().minusMonths(1))
                                .endDate(LocalDate.now().plusYears(1))
                                .status(BenefitPolicyStatus.ACTIVE) // Assuming PolicyStatus is BenefitPolicyStatus
                                .active(true)
                                .build());

                // 3. Member
                member = memberRepository.save(Member.builder()
                                .fullName("John Doe")
                                .barcode("1234567890")
                                .nationalNumber("TEST-123")
                                .employer(employer)
                                .benefitPolicy(policy)
                                .active(true)
                                .build());

                // 4. Provider
                provider = providerRepository.save(Provider.builder()
                                .name("General Hospital")
                                .providerType(ProviderType.HOSPITAL)
                                .licenseNumber("LIC-TEST-456")
                                .allowAllEmployers(true)
                                .active(true)
                                .build());

                // 5. Medical Category
                var category = medicalCategoryRepository.save(MedicalCategory.builder()
                                .code("CAT-001")
                                .name("General Services")
                                .active(true)
                                .build());

                // 6. Medical Service
                service = medicalServiceRepository.save(MedicalService.builder()
                                .code("SRV-001")
                                .name("General Consultation")
                                .categoryId(category.getId())
                                .cost(new BigDecimal("150"))
                                .active(true)
                                .build());

                // 6. Contract + Pricing Item + Provider Account
                contract = seedContractPricingAndAccount(provider, "CON-TEST", "CNT-2026-001");

                // 8. Visit
                visit = visitRepository.save(Visit.builder()
                                .member(member)
                                .providerId(provider.getId())
                                .visitDate(LocalDate.now())
                                .status(VisitStatus.REGISTERED)
                                .build());
        }

        @Test
        @WithMockUser(username = "admin", roles = { "ADMIN", "REVIEWER" })
        void fullClaimLifecycle_shouldSucceed() {
                // Step 1: Create Claim from Visit
                // WAAD-INTEGRATION-TEST-CONTEXT-1: ClaimMapper.processEngineCalculations
                // resolves pricing from serviceCode/pricingItemId only — medicalServiceId
                // alone (this test's original fixture) is never consulted by the current
                // pricing-resolution flow (provider-contract pricing items, matched by
                // serviceCode), so it must be supplied for the line to resolve a price.
                ClaimCreateDto createDto = ClaimCreateDto.builder()
                                .visitId(visit.getId())
                                .serviceDate(LocalDate.now())
                                .lines(List.of(ClaimLineDto.builder()
                                                .medicalServiceId(service.getId())
                                                .serviceCode(service.getCode())
                                                .quantity(1)
                                                .build()))
                                .status(ClaimStatus.SUBMITTED)
                                .build();

                ClaimViewDto createdClaim = claimService.createClaim(createDto);
                assertThat(createdClaim).isNotNull();
                assertThat(createdClaim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
                assertThat(createdClaim.getRequestedAmount()).isEqualByComparingTo("120.00");

                // CLAIM-NUMBERING-1: claim gets an official sequential reference,
                // not the raw database id.
                assertThat(createdClaim.getClaimNumber()).matches("^CLM-P\\d{3}-\\d{6}$");
                assertThat(createdClaim.getClaimNumber()).isNotEqualTo("CLM-" + createdClaim.getId());

                // Step 2: Start Review
                ClaimViewDto underReview = claimReviewService.startReview(createdClaim.getId());
                assertThat(underReview.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);

                // Step 3: Request Approval (Phase 1)
                // Since we are in an integration test without a running async executor,
                // we'll wait or call the logic manually if needed.
                // But for Lifecycle verification, let's assume we can settle once Approved.

                ClaimApproveDto approveDto = ClaimApproveDto.builder()
                                .useSystemCalculation(true)
                                .notes("Looks good")
                                .build();

                // requestApproval (Phase 1) internally triggers processApprovalAsync
                // (Phase 2) itself — it must NOT also be invoked directly here too. That
                // internal call is a plain "this.processApprovalAsync(...)" self-invocation,
                // which bypasses the Spring proxy entirely, so it runs neither @Async nor in
                // its own REQUIRES_NEW transaction — it executes synchronously, still inside
                // requestApproval()'s own (outer, test-owned) transaction. Calling
                // processApprovalAsync a second time directly through the injected proxy (as
                // this test previously did "for stability") DOES go through the proxy and
                // really does dispatch to a background thread — racing the first,
                // already-complete synchronous run and non-deterministically
                // double-processing the same claim. Removed; requestApproval() alone is
                // sufficient and deterministic.
                claimReviewService.requestApproval(createdClaim.getId(), approveDto);

                // WAAD-INTEGRATION-TEST-CONTEXT-1: ClaimApprovalEventListener (the code
                // that credits the ProviderAccount on approval) is a
                // @TransactionalEventListener(phase = AFTER_COMMIT) — it only fires on a
                // genuine physical commit. Because Phase 2 ran as a self-invocation (see
                // above) inside this test's own outer @Transactional block — which this
                // test framework only ever ROLLS BACK, never commits — that commit (and
                // therefore the credit) would never happen if we just flushed and kept
                // going. Flagging for commit, ending, and restarting the test transaction
                // forces a real commit of everything so far and opens a fresh transaction
                // for the rest of the test, exactly reproducing what a real (committing)
                // HTTP request would do in production. The Testcontainers-managed
                // Postgres instance is torn down after this test run, so the
                // permanently-committed rows are harmless.
                TestTransaction.flagForCommit();
                TestTransaction.end();
                TestTransaction.start();
                entityManager.clear();

                ClaimViewDto approvedClaim = claimService.getClaim(createdClaim.getId());
                assertThat(approvedClaim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
                assertThat(approvedClaim.getApprovedAmount()).isGreaterThan(BigDecimal.ZERO);

                // Step 5: Settle Payment
                ClaimSettleDto settleDto = ClaimSettleDto.builder()
                                .paymentReference("PAY-001")
                                .notes("Settled via Test")
                                .build();

                ClaimViewDto settledClaim = claimReviewService.settleClaim(createdClaim.getId(), settleDto);
                assertThat(settledClaim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
                assertThat(settledClaim.getPaymentReference()).isEqualTo("PAY-001");
        }

        /**
         * WAAD-INTEGRATION-TEST-CONTEXT-1: seeds a contract + pricing item (so
         * ClaimMapper's provider-contract price lookup resolves a real price for
         * "SRV-001") and a ProviderAccount (required by ClaimReviewService.settleClaim
         * — settlement never auto-creates one) for the given provider. Extracted so
         * claimsForDifferentProviders_eachSequenceStartsAtOneIndependently can seed
         * the same for its second provider, which otherwise has no contract at all.
         */
        private ProviderContract seedContractPricingAndAccount(Provider p, String contractCode, String contractNumber) {
                ProviderContract c = contractRepository.save(ProviderContract.builder()
                                .contractCode(contractCode)
                                .contractNumber(contractNumber)
                                .provider(p)
                                .startDate(LocalDate.now().minusMonths(1))
                                .endDate(LocalDate.now().plusMonths(11))
                                .status(ContractStatus.ACTIVE)
                                .active(true)
                                .build());

                pricingRepository.save(ProviderContractPricingItem.builder()
                                .contract(c)
                                .serviceCode(service.getCode())
                                .serviceName(service.getName())
                                .basePrice(new BigDecimal("150"))
                                .contractPrice(new BigDecimal("120"))
                                .active(true)
                                .build());

                providerAccountRepository.save(com.waad.tba.modules.settlement.entity.ProviderAccount.builder()
                                .providerId(p.getId())
                                .build());

                return c;
        }

        private ClaimCreateDto createDtoForVisit(Visit v) {
                return ClaimCreateDto.builder()
                                .visitId(v.getId())
                                .serviceDate(LocalDate.now())
                                .lines(List.of(ClaimLineDto.builder()
                                                .medicalServiceId(service.getId())
                                                .serviceCode(service.getCode())
                                                .quantity(1)
                                                .build()))
                                .status(ClaimStatus.SUBMITTED)
                                .build();
        }

        @Test
        @WithMockUser(username = "admin", roles = { "ADMIN", "REVIEWER" })
        void secondClaimForSameProvider_incrementsSequenceAndKeepsUniqueReference() {
                ClaimViewDto firstClaim = claimService.createClaim(createDtoForVisit(visit));

                Visit secondVisit = visitRepository.save(Visit.builder()
                                .member(member)
                                .providerId(provider.getId())
                                .visitDate(LocalDate.now())
                                .status(VisitStatus.REGISTERED)
                                .build());
                ClaimViewDto secondClaim = claimService.createClaim(createDtoForVisit(secondVisit));

                assertThat(secondClaim.getClaimNumber()).isNotEqualTo(firstClaim.getClaimNumber());

                String providerPrefix = "CLM-P" + String.format("%03d", provider.getId());
                assertThat(firstClaim.getClaimNumber()).startsWith(providerPrefix);
                assertThat(secondClaim.getClaimNumber()).startsWith(providerPrefix);

                int firstSequence = Integer.parseInt(firstClaim.getClaimNumber().substring(firstClaim.getClaimNumber().length() - 6));
                int secondSequence = Integer.parseInt(secondClaim.getClaimNumber().substring(secondClaim.getClaimNumber().length() - 6));
                assertThat(secondSequence).isEqualTo(firstSequence + 1);
        }

        @Test
        @WithMockUser(username = "admin", roles = { "ADMIN", "REVIEWER" })
        void claimsForDifferentProviders_eachSequenceStartsAtOneIndependently() {
                ClaimViewDto claimForProviderOne = claimService.createClaim(createDtoForVisit(visit));

                Provider secondProvider = providerRepository.save(Provider.builder()
                                .name("Second Clinic")
                                .providerType(ProviderType.HOSPITAL)
                                .licenseNumber("LIC-TEST-999")
                                .allowAllEmployers(true)
                                .active(true)
                                .build());
                seedContractPricingAndAccount(secondProvider, "CON-TEST-2", "CNT-2026-002");
                Visit visitForSecondProvider = visitRepository.save(Visit.builder()
                                .member(member)
                                .providerId(secondProvider.getId())
                                .visitDate(LocalDate.now())
                                .status(VisitStatus.REGISTERED)
                                .build());
                ClaimViewDto claimForProviderTwo = claimService.createClaim(createDtoForVisit(visitForSecondProvider));

                assertThat(claimForProviderOne.getClaimNumber())
                                .isEqualTo("CLM-P" + String.format("%03d", provider.getId()) + "-000001");
                assertThat(claimForProviderTwo.getClaimNumber())
                                .isEqualTo("CLM-P" + String.format("%03d", secondProvider.getId()) + "-000001");
        }
}
