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
import com.waad.tba.modules.report.dto.CompanyProfitReportRowDto;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAAD-CLAIMS-FINANCIAL-CORRECTNESS-1 (Fix B/D) regression coverage.
 *
 * Confirmed business rules:
 *   6. TPA revenue equals the final provider discount calculated at final
 *      claim approval, not draft creation.
 *   Fix D: CompanyProfitReportService/ClaimRepository must use the
 *   authoritative, always-fresh Claim.companyDiscountAmount — never a
 *   MAX(discountPercent)-based re-derivation across a group of claims (which
 *   is wrong for any group spanning claims with different discount rates,
 *   and can incorrectly override a legitimately-zero result).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class CompanyProfitReportFinancialCorrectnessTest {

    @Autowired private ClaimService claimService;
    @Autowired private CompanyProfitReportService companyProfitReportService;
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
    @Autowired private EntityManager entityManager;

    private Employer employer;
    private Provider provider;
    private ProviderContract contract;
    private BenefitPolicy policy;
    private MedicalCategory category;
    private ProviderContractPricingItem pricingItem;
    private String suffix;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("profit-report-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("profit-report-admin").password("password").fullName("Admin")
                    .email("profit-report-admin@waad.ly").userType("SUPER_ADMIN").active(true).build());
        }

        suffix = String.valueOf(System.nanoTime()).substring(9);
        employer = employerRepository.save(Employer.builder()
                .name("Profit Report Co " + suffix).code("EMP-PR-" + suffix).active(true).build());

        policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Profit Report Plan").policyCode("POL-PR-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-PR-" + suffix).name("Profit Report Category").active(true).build());

        // 100% coverage, no limit — keeps companyShareBeforeDiscount == requestedAmount
        // exactly, so this test is purely about the discount/report layer, not the
        // benefit-limit split covered by WAAD-CLAIMS-FINANCIAL-CORRECTNESS-1/2's own tests.
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .coveragePercent(100).active(true).build());

        provider = providerRepository.save(Provider.builder()
                .name("Profit Report Clinic " + suffix).providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-PR-" + suffix).allowAllEmployers(true).active(true).build());

        contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-PR-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .discountPercent(new BigDecimal("10"))
                .status(ContractStatus.ACTIVE).active(true).build());

        pricingItem = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode("SRV-PR-" + suffix).serviceName("Profit Report Service")
                .medicalCategory(category)
                .basePrice(new BigDecimal("100")).contractPrice(new BigDecimal("100"))
                .active(true).build());

        providerAccountRepository.save(com.waad.tba.modules.settlement.entity.ProviderAccount.builder()
                .providerId(provider.getId()).build());
    }

    private Visit newVisit(String memberSuffix) {
        Member member = memberRepository.save(Member.builder()
                .fullName("Profit Report Member " + memberSuffix).barcode("PR-" + memberSuffix)
                .nationalNumber("PR-" + memberSuffix).employer(employer).benefitPolicy(policy).active(true).build());
        return visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
    }

    private CompanyProfitReportRowDto singleRow() {
        LocalDate now = LocalDate.now();
        List<CompanyProfitReportRowDto> rows = companyProfitReportService.getCompanyProfitReport(
                employer.getId(), now.getYear(), now.getMonthValue(), provider.getId());
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @Test
    @WithMockUser(username = "profit-report-admin", roles = "SUPER_ADMIN")
    void mixedDiscountRatesAcrossClaims_reportSumsEachClaimsOwnRate_notMaxRate() {
        // Claim 1 at 10% discount: requested=100, 100% coverage -> companyShareBeforeDiscount=100,
        // discount=10, companyDiscountAmount=10.
        claimService.createClaim(ClaimCreateDto.builder()
                .visitId(newVisit("mix1-" + suffix).getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId()).serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId()).unitPrice(new BigDecimal("100")).quantity(1).build()))
                .status(ClaimStatus.APPROVED)
                .build());

        // Contract renegotiated to 20% discount before the second claim.
        contract.setDiscountPercent(new BigDecimal("20"));
        contractRepository.saveAndFlush(contract);

        // Claim 2 at 20% discount: requested=200 -> companyShareBeforeDiscount=200,
        // discount=40, companyDiscountAmount=40. NOTE: unitPrice in the request DTO
        // is NOT the basis for requestedTotal when a pricingItemId is given
        // (PROVIDER-PORTAL-DATA-1 — the resolved CONTRACT price always wins, never a
        // frontend-supplied unit price) — quantity=2 against the 100 contract price
        // is the correct way to reach 200 here.
        claimService.createClaim(ClaimCreateDto.builder()
                .visitId(newVisit("mix2-" + suffix).getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId()).serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId()).unitPrice(new BigDecimal("100")).quantity(2).build()))
                .status(ClaimStatus.APPROVED)
                .build());

        CompanyProfitReportRowDto row = singleRow();

        // SUM of each claim's own correctly-computed discount (10 + 40 = 50) — NOT
        // MAX(10%, 20%) applied to the combined total (20% x 300 = 60, the old bug).
        assertThat(row.getCompanyDueValue()).isEqualByComparingTo("50.00");
        assertThat(row.getTotalClaimValue()).isEqualByComparingTo("300.00");
    }

    @Test
    @WithMockUser(username = "profit-report-admin", roles = "SUPER_ADMIN")
    void zeroDiscountClaim_reportContributesExactlyZero() {
        contract.setDiscountPercent(BigDecimal.ZERO);
        contractRepository.save(contract);

        claimService.createClaim(ClaimCreateDto.builder()
                .visitId(newVisit("zero-" + suffix).getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId()).serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId()).unitPrice(new BigDecimal("100")).quantity(1).build()))
                .status(ClaimStatus.APPROVED)
                .build());

        CompanyProfitReportRowDto row = singleRow();

        // Must remain genuinely zero — the old CompanyProfitReportService fallback
        // ("recompute if <= 0") used to override a legitimate zero-discount result.
        assertThat(row.getCompanyDueValue()).isEqualByComparingTo("0.00");
    }

    @Test
    @WithMockUser(username = "profit-report-admin", roles = "SUPER_ADMIN")
    void companyDiscountAmount_afterPartialManualRejection_reflectsPostRejectionShare() {
        // requested=100, 100% coverage -> companyShareBeforeDiscount=100. A manual
        // partial rejection of 30 reduces the company's share to 70 BEFORE the 10%
        // discount is applied (this contract's default discountBeforeRejection mode):
        // discount = 100 x 10% = 10 (on the full pre-rejection share, per BEFORE
        // mode), rejectedAmount = min(90, 30) = 30, companyShare = 90 - 30 = 60.
        claimService.createClaim(ClaimCreateDto.builder()
                .visitId(newVisit("partial-" + suffix).getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId()).serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId()).unitPrice(new BigDecimal("100")).quantity(1)
                        .manualRefusedAmount(new BigDecimal("30"))
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build());

        CompanyProfitReportRowDto row = singleRow();

        assertThat(row.getCompanyDueValue()).isEqualByComparingTo("10.00");
    }
}
