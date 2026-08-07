package com.waad.tba.modules.report.service;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
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
import com.waad.tba.modules.report.dto.FinancialConsolidationDto;
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

/**
 * WAAD-CLAIMS-FINANCIAL-CORRECTNESS-1 (Fix D) regression coverage for the
 * "export and summary reports use the same authoritative numbers" checklist
 * item — this is the SECOND report (alongside CompanyProfitReportService)
 * found during audit still re-deriving TPA revenue via a stale CASE/fallback
 * against the provider's CURRENT contract discount rate, rather than trusting
 * the always-fresh, per-claim Claim.companyDiscountAmount (Fix B).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class FinancialConsolidationFinancialCorrectnessTest {

    @Autowired private ClaimService claimService;
    @Autowired private FinancialConsolidationService financialConsolidationService;
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

    private Employer employer;
    private Provider provider;
    private ProviderContract contract;
    private BenefitPolicy policy;
    private MedicalCategory category;
    private ProviderContractPricingItem pricingItem;
    private String suffix;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("fin-consol-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("fin-consol-admin").password("password").fullName("Admin")
                    .email("fin-consol-admin@waad.ly").userType("SUPER_ADMIN").active(true).build());
        }

        suffix = String.valueOf(System.nanoTime()).substring(9);
        employer = employerRepository.save(Employer.builder()
                .name("Fin Consol Co " + suffix).code("EMP-FC-" + suffix).active(true).build());

        policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Fin Consol Plan").policyCode("POL-FC-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-FC-" + suffix).name("Fin Consol Category").active(true).build());
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .coveragePercent(100).active(true).build());

        provider = providerRepository.save(Provider.builder()
                .name("Fin Consol Clinic " + suffix).providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-FC-" + suffix).allowAllEmployers(true).active(true).build());

        contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-FC-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .discountPercent(new BigDecimal("10"))
                .status(ContractStatus.ACTIVE).active(true).build());

        pricingItem = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode("SRV-FC-" + suffix).serviceName("Fin Consol Service")
                .medicalCategory(category)
                .basePrice(new BigDecimal("100")).contractPrice(new BigDecimal("100"))
                .active(true).build());

        providerAccountRepository.save(com.waad.tba.modules.settlement.entity.ProviderAccount.builder()
                .providerId(provider.getId()).build());
    }

    private Visit newVisit(String memberSuffix) {
        Member member = memberRepository.save(Member.builder()
                .fullName("Fin Consol Member " + memberSuffix).barcode("FC-" + memberSuffix)
                .nationalNumber("FC-" + memberSuffix).employer(employer).benefitPolicy(policy).active(true).build());
        return visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
    }

    @Test
    @WithMockUser(username = "fin-consol-admin", roles = "SUPER_ADMIN")
    void monthlyConsolidation_afterContractRateChanges_usesEachClaimsOwnPersistedDiscount() {
        // Claim 1 at 10%: requested=100 -> companyDiscountAmount=10.
        claimService.createClaim(ClaimCreateDto.builder()
                .visitId(newVisit("c1-" + suffix).getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId()).serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId()).unitPrice(new BigDecimal("100")).quantity(1).build()))
                .status(ClaimStatus.APPROVED)
                .build());

        // Contract discount changes to 25% AFTER claim 1 is already approved.
        contract.setDiscountPercent(new BigDecimal("25"));
        contractRepository.saveAndFlush(contract);

        // Claim 2, quantity=2 -> requested=200 -> companyDiscountAmount at the NEW
        // 25% rate = 50. The old fallback would apply whatever pc.discountPercent is
        // AT REPORT-RUN TIME (25%) to BOTH claims combined (25% x 300 = 75), which is
        // wrong for claim 1 (approved when the rate was still 10%).
        claimService.createClaim(ClaimCreateDto.builder()
                .visitId(newVisit("c2-" + suffix).getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId()).serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId()).unitPrice(new BigDecimal("100")).quantity(2).build()))
                .status(ClaimStatus.APPROVED)
                .build());

        LocalDate now = LocalDate.now();
        List<FinancialConsolidationDto> rows = financialConsolidationService.getMonthlyFinancialConsolidation(now.getYear());
        FinancialConsolidationDto row = rows.stream()
                .filter(r -> employer.getName().equals(r.getEmployerName()))
                .findFirst().orElseThrow();

        FinancialConsolidationDto.MonthlyFinancials monthly = monthOf(row, now.getMonthValue());

        // Correct: 10 (claim 1 at its own 10%) + 50 (claim 2 at its own 25%) = 60.
        assertThat(monthly.getCompanyDiscountAmount()).isEqualByComparingTo("60.00");
        assertThat(monthly.getRequestedAmount()).isEqualByComparingTo("300.00");
    }

    private FinancialConsolidationDto.MonthlyFinancials monthOf(FinancialConsolidationDto row, int month) {
        return switch (month) {
            case 1 -> row.getMonth1();
            case 2 -> row.getMonth2();
            case 3 -> row.getMonth3();
            case 4 -> row.getMonth4();
            case 5 -> row.getMonth5();
            case 6 -> row.getMonth6();
            case 7 -> row.getMonth7();
            case 8 -> row.getMonth8();
            case 9 -> row.getMonth9();
            case 10 -> row.getMonth10();
            case 11 -> row.getMonth11();
            default -> row.getMonth12();
        };
    }
}
