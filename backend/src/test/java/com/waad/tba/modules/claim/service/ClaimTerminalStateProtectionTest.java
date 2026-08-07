package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimDataUpdateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
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
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WAAD-CLAIM-STATE-MACHINE-INTEGRITY-1 regression coverage.
 *
 * Decision (Phase 4 audit, 2026-08-07): REJECTED is a hard-locked terminal
 * status (ClaimStateMachine.HARD_LOCKED_FINAL_STATES,
 * TRANSITION_MATRIX.get(REJECTED) == Set.of()). ClaimService.updateClaimData()
 * used to attempt APPROVED->REJECTED and REJECTED->APPROVED transitions
 * directly (legacy "FIX #11" / "admin re-approval" code), both of which
 * ALWAYS threw a confusing, generic ClaimStateTransitionException from deep
 * inside the state machine — neither path is exposed by any current frontend
 * UI. Rather than weakening terminal-state protection to make these work,
 * the operation is now rejected outright with a clear, actionable business
 * error: use the claim cancellation (delete/restore) workflow to reverse an
 * approved claim's financial credit; a rejected claim cannot be reopened.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class ClaimTerminalStateProtectionTest {

    @Autowired private ClaimService claimService;
    @Autowired private ClaimRepository claimRepository;
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
    @Autowired private com.waad.tba.modules.settlement.repository.ProviderAccountRepository providerAccountRepository;

    private Visit visit;
    private ProviderContractPricingItem pricingItem;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("terminal-state-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("terminal-state-admin").password("password").fullName("Admin")
                    .email("terminal-state-admin@waad.ly").userType("SUPER_ADMIN").active(true).build());
        }

        String suffix = String.valueOf(System.nanoTime()).substring(9);
        Employer employer = employerRepository.save(Employer.builder()
                .name("Terminal State Co").code("EMP-TS-" + suffix).active(true).build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Terminal State Plan").policyCode("POL-TS-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-TS-" + suffix).name("Terminal State Category").active(true).build());
        categoryId = category.getId();

        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .coveragePercent(100).active(true).build());

        Member member = memberRepository.save(Member.builder()
                .fullName("Terminal State Member").barcode("TS-" + suffix)
                .nationalNumber("TS-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());

        Provider provider = providerRepository.save(Provider.builder()
                .name("Terminal State Clinic").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-TS-" + suffix).allowAllEmployers(true).active(true).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-TS-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());

        pricingItem = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode("SRV-TS-" + suffix).serviceName("Terminal State Service")
                .medicalCategory(category)
                .basePrice(new BigDecimal("100")).contractPrice(new BigDecimal("100"))
                .active(true).build());

        providerAccountRepository.save(com.waad.tba.modules.settlement.entity.ProviderAccount.builder()
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

    @Test
    @WithMockUser(username = "terminal-state-admin", roles = "SUPER_ADMIN")
    void approvedClaim_rejectAttemptViaUpdateClaimData_failsWithClearBusinessError() {
        ClaimViewDto claim = claimService.createClaim(baseClaimBuilder(ClaimStatus.APPROVED).build());

        assertThatThrownBy(() -> claimService.updateClaimData(claim.getId(),
                ClaimDataUpdateDto.builder()
                        .status(ClaimStatus.REJECTED)
                        .rejectionReason("Attempting illegal terminal-state entry")
                        .build()))
                .isInstanceOf(BusinessRuleException.class);

        // The claim must be entirely unchanged — no partial mutation from the
        // failed attempt.
        Claim reloaded = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    @WithMockUser(username = "terminal-state-admin", roles = "SUPER_ADMIN")
    void rejectedClaim_reopenAttemptViaUpdateClaimData_failsWithClearBusinessError() {
        ClaimViewDto claim = claimService.createClaim(
                baseClaimBuilder(ClaimStatus.REJECTED).rejectionReason("Initial rejection").build());

        assertThatThrownBy(() -> claimService.updateClaimData(claim.getId(),
                ClaimDataUpdateDto.builder().status(ClaimStatus.APPROVED).build()))
                .isInstanceOf(BusinessRuleException.class);

        Claim reloaded = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.REJECTED);
    }

    @Test
    @WithMockUser(username = "terminal-state-admin", roles = "SUPER_ADMIN")
    void rejectedClaim_pureDataEdit_withNoStatusChange_stillSucceeds() {
        ClaimViewDto claim = claimService.createClaim(
                baseClaimBuilder(ClaimStatus.REJECTED).rejectionReason("Initial rejection").build());

        // A REJECTED claim remains editable for record-keeping (e.g. fixing a
        // typo'd doctor name) as long as no status transition is attempted.
        ClaimViewDto updated = claimService.updateClaimData(claim.getId(),
                ClaimDataUpdateDto.builder().doctorName("Dr. Corrected Name").build());

        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(updated.getDoctorName()).isEqualTo("Dr. Corrected Name");
    }
}
