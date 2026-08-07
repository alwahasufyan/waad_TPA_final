package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
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
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationCreateDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDecisionDto;
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
 * WAAD-CLAIMS-FINANCIAL-CORRECTNESS-1 (Fix C) regression coverage.
 *
 * Confirmed business rules:
 *   7. Approved PreAuthorizationLine financial snapshots must transfer to
 *      ClaimLine during conversion.
 *   8. Rejected lines must not convert to ClaimLines.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class ClaimPreAuthorizationLineConversionTest {

    @Autowired private ClaimService claimService;
    @Autowired private PreAuthorizationService preAuthorizationService;
    @Autowired private PreAuthorizationRepository preAuthorizationRepository;
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

    private Member member;
    private Provider provider;
    private Visit visit;
    private MedicalCategory category;
    private BenefitPolicy policy;
    private ProviderContractPricingItem pricingItem;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("pa-conv-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("pa-conv-admin").password("password").fullName("Admin")
                    .email("pa-conv-admin@waad.ly").userType("SUPER_ADMIN").active(true).build());
        }

        String suffix = String.valueOf(System.nanoTime()).substring(9);
        Employer employer = employerRepository.save(Employer.builder()
                .name("PA Conv Co").code("EMP-PAC-" + suffix).active(true).build());

        policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("PA Conv Plan").policyCode("POL-PAC-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-PAC-" + suffix).name("PA Conv Category").active(true).build());

        // No amount limit — isolates this test from Fix A's benefit-limit logic
        // entirely, so only the snapshot-transfer/rejection-block behavior (Fix C)
        // is under test.
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .coveragePercent(80).active(true).build());

        member = memberRepository.save(Member.builder()
                .fullName("PA Conv Member").barcode("PAC-" + suffix)
                .nationalNumber("PAC-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());

        provider = providerRepository.save(Provider.builder()
                .name("PA Conv Clinic").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-PAC-" + suffix).allowAllEmployers(true).active(true).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-PAC-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());

        pricingItem = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode("SRV-PAC-" + suffix).serviceName("PA Conv Service")
                .medicalCategory(category)
                .basePrice(new BigDecimal("100")).contractPrice(new BigDecimal("100"))
                .active(true).build());

        providerAccountRepository.save(com.waad.tba.modules.settlement.entity.ProviderAccount.builder()
                .providerId(provider.getId()).build());

        visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
    }

    @Test
    @WithMockUser(username = "pa-conv-admin", roles = "SUPER_ADMIN")
    void approvedPreAuthLine_coverageSnapshotTransfersToClaimLine_evenAfterPolicyRateLaterChanges() {
        var createdPa = preAuthorizationService.createPreAuthorization(
                PreAuthorizationCreateDto.builder()
                        .visitId(visit.getId()).providerId(provider.getId())
                        .pricingItemId(pricingItem.getId())
                        .build(),
                "pa-conv-admin");
        Long lineId = createdPa.getLines().get(0).getId();

        preAuthorizationService.submitLineDecision(createdPa.getId(), lineId,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(),
                "pa-conv-admin");

        // The PA line's coveragePercentSnapshot is now locked in at 80 (the rate
        // in effect at PA-approval time). Simulate the benefit policy's rate
        // changing afterwards — a real scenario (policy renegotiation, category
        // remap) that must NOT retroactively alter an already-approved
        // pre-authorization's numbers when it later converts to a claim.
        BenefitPolicyRule rule = benefitPolicyRuleRepository
                .findByBenefitPolicyIdAndMedicalCategoryId(policy.getId(), category.getId())
                .orElseThrow();
        rule.setCoveragePercent(50);
        benefitPolicyRuleRepository.save(rule);

        ClaimCreateDto claimDto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .preAuthorizationId(createdPa.getId())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId())
                        .unitPrice(new BigDecimal("100"))
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();

        ClaimViewDto claim = claimService.createClaim(claimDto);
        var line = claim.getLines().get(0);

        // Must reflect the PA-approval-time 80% snapshot, NOT the since-changed
        // 50% rule — proving the coverage snapshot transferred instead of being
        // silently re-resolved from the (now different) current policy rules.
        assertThat(line.getCoveragePercent()).isEqualTo(80);
        assertThat(line.getPatientShare()).isEqualByComparingTo("20.00");
        assertThat(line.getCompanyShareBeforeDiscount()).isEqualByComparingTo("80.00");
    }

    @Test
    @WithMockUser(username = "pa-conv-admin", roles = "SUPER_ADMIN")
    void rejectedPreAuthLine_cannotBeConvertedIntoClaimLine() {
        var createdPa = preAuthorizationService.createPreAuthorization(
                PreAuthorizationCreateDto.builder()
                        .visitId(visit.getId()).providerId(provider.getId())
                        .pricingItemId(pricingItem.getId())
                        .build(),
                "pa-conv-admin");
        Long lineId = createdPa.getLines().get(0).getId();

        preAuthorizationService.submitLineDecision(createdPa.getId(), lineId,
                PreAuthorizationLineDecisionDto.builder()
                        .decision(LineReviewDecision.REJECTED)
                        .reason("Not medically necessary")
                        .build(),
                "pa-conv-admin");

        ClaimCreateDto claimDto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .preAuthorizationId(createdPa.getId())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId())
                        .unitPrice(new BigDecimal("100"))
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();

        assertThatThrownBy(() -> claimService.createClaim(claimDto))
                .isInstanceOf(BusinessRuleException.class);
    }

    /**
     * Rule 7 + 8 together, end-to-end: a two-line PreAuthorization with one
     * APPROVED line and one REJECTED line converts to a Claim carrying
     * exactly ONE ClaimLine (for the approved PA line only — matching the
     * real frontend flow, which only ever submits approved PA lines as claim
     * lines; see useProviderClaimSubmission.js). The resulting ClaimLine's
     * amount/coverage snapshot must match the PA line's approved snapshot
     * exactly, and the rejected line must have no representation in the
     * claim at all.
     */
    @Test
    @WithMockUser(username = "pa-conv-admin", roles = "SUPER_ADMIN")
    void twoLinePreAuth_oneApprovedOneRejected_convertsToExactlyOneClaimLine() {
        MedicalCategory categoryB = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-PAC-B-" + System.nanoTime()).name("PA Conv Category B").active(true).build());
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(categoryB)
                .coveragePercent(60).active(true).build());
        ProviderContractPricingItem pricingItemB = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(pricingItem.getContract()).serviceCode("SRV-PAC-B-" + System.nanoTime())
                .serviceName("PA Conv Service B").medicalCategory(categoryB)
                .basePrice(new BigDecimal("50")).contractPrice(new BigDecimal("50"))
                .active(true).build());

        var createdPa = preAuthorizationService.createPreAuthorization(
                PreAuthorizationCreateDto.builder()
                        .visitId(visit.getId()).providerId(provider.getId())
                        .lines(List.of(
                                com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDto.builder()
                                        .pricingItemId(pricingItem.getId()).build(),
                                com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDto.builder()
                                        .pricingItemId(pricingItemB.getId()).build()))
                        .build(),
                "pa-conv-admin");
        Long lineAId = createdPa.getLines().get(0).getId();
        Long lineBId = createdPa.getLines().get(1).getId();

        preAuthorizationService.submitLineDecision(createdPa.getId(), lineAId,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(),
                "pa-conv-admin");
        preAuthorizationService.submitLineDecision(createdPa.getId(), lineBId,
                PreAuthorizationLineDecisionDto.builder()
                        .decision(LineReviewDecision.REJECTED).reason("Not covered")
                        .build(),
                "pa-conv-admin");

        // Frontend behavior: only the approved line (A) is ever submitted as a
        // claim line — the rejected line (B) is never included in the request.
        ClaimCreateDto claimDto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .preAuthorizationId(createdPa.getId())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId())
                        .unitPrice(new BigDecimal("100"))
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();

        ClaimViewDto claim = claimService.createClaim(claimDto);

        assertThat(claim.getLines()).hasSize(1);
        var claimLine = claim.getLines().get(0);
        assertThat(claimLine.getPricingItemId()).isEqualTo(pricingItem.getId());
        assertThat(claimLine.getServiceCode()).isEqualTo(pricingItem.getServiceCode());
        assertThat(claimLine.getServiceCategoryId()).isEqualTo(category.getId());
        assertThat(claimLine.getCoveragePercent()).isEqualTo(80);
        assertThat(claimLine.getRequestedTotal()).isEqualByComparingTo("100.00");
        assertThat(claimLine.getPatientShare()).isEqualByComparingTo("20.00");
        assertThat(claimLine.getCompanyShareBeforeDiscount()).isEqualByComparingTo("80.00");

        // The rejected line (B, category B / 60% coverage) has zero footprint —
        // its service code never appears among the claim's lines.
        assertThat(claim.getLines()).noneMatch(l -> pricingItemB.getServiceCode().equals(l.getServiceCode()));
    }

    private Long createAndApprovePaLineWithQuantity(int quantity) {
        var createdPa = preAuthorizationService.createPreAuthorization(
                PreAuthorizationCreateDto.builder()
                        .visitId(visit.getId()).providerId(provider.getId())
                        .lines(List.of(com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDto.builder()
                                .pricingItemId(pricingItem.getId()).quantity(quantity).build()))
                        .build(),
                "pa-conv-admin");
        Long lineId = createdPa.getLines().get(0).getId();
        preAuthorizationService.submitLineDecision(createdPa.getId(), lineId,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(),
                "pa-conv-admin");
        return createdPa.getId();
    }

    private ClaimCreateDto claimDtoWithQuantity(Long preAuthId, int quantity) {
        return ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .preAuthorizationId(preAuthId)
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(category.getId())
                        .unitPrice(new BigDecimal("100"))
                        .quantity(quantity)
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();
    }

    /**
     * WAAD-PREAUTH-LINE-QUANTITY-CEILING-1 regression coverage: a claim line's
     * quantity must never exceed what the matched, APPROVED PreAuthorizationLine
     * itself approved — nothing enforced this before, so a PA line approved for
     * e.g. 2 sessions could silently convert into a claim requesting 10.
     */
    @Test
    @WithMockUser(username = "pa-conv-admin", roles = "SUPER_ADMIN")
    void claimQuantityEqualsApprovedPaLineQuantity_conversionSucceeds() {
        Long preAuthId = createAndApprovePaLineWithQuantity(3);

        ClaimViewDto claim = claimService.createClaim(claimDtoWithQuantity(preAuthId, 3));

        var line = claim.getLines().get(0);
        assertThat(line.getRequestedTotal()).isEqualByComparingTo("300.00");
    }

    @Test
    @WithMockUser(username = "pa-conv-admin", roles = "SUPER_ADMIN")
    void claimQuantityLowerThanApprovedPaLineQuantity_conversionSucceeds_partialUsage() {
        Long preAuthId = createAndApprovePaLineWithQuantity(3);

        // Fewer sessions actually used than were approved — a legitimate
        // partial conversion, not a violation of the ceiling.
        ClaimViewDto claim = claimService.createClaim(claimDtoWithQuantity(preAuthId, 2));

        var line = claim.getLines().get(0);
        assertThat(line.getRequestedTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    @WithMockUser(username = "pa-conv-admin", roles = "SUPER_ADMIN")
    void claimQuantityExceedsApprovedPaLineQuantity_conversionBlocked() {
        Long preAuthId = createAndApprovePaLineWithQuantity(3);

        assertThatThrownBy(() -> claimService.createClaim(claimDtoWithQuantity(preAuthId, 5)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds");
    }
}
