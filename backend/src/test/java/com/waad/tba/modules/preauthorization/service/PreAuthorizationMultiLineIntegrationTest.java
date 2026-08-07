package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.common.exception.ArchitecturalViolationException;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationCreateDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDecisionDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationResponseDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineReviewDecision;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): end-to-end coverage for multi-line
 * pre-authorization creation, per-line decisions, and finalization —
 * against a real Testcontainers Postgres so the V112 schema, the
 * category-limit accumulator's live SQL, and Spring-context wiring are all
 * genuinely exercised (not mocked).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class PreAuthorizationMultiLineIntegrationTest {

    @Autowired private PreAuthorizationService preAuthorizationService;
    @Autowired private PreAuthorizationRepository preAuthorizationRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private MedicalCategoryRepository categoryRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Member member;
    private Provider provider;
    private Visit visit;
    private MedicalCategory categoryA;
    private MedicalCategory categoryB;
    private ProviderContractPricingItem pricingA;
    private ProviderContractPricingItem pricingB;

    // WAAD-PREAUTH-MULTI-LINE-1: the concurrency test below deliberately
    // forces a real commit (TestTransaction.flagForCommit()) so a separate
    // physical transaction can observe it — unlike every other @Test in
    // this class, that data survives past its own test method. A unique
    // suffix per @BeforeEach invocation keeps every other test's setup rows
    // from colliding with whatever the concurrency test already committed,
    // regardless of JUnit's (unspecified) method execution order.
    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        uniqueSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);

        // Username is fixed (not suffixed) — it must match @WithMockUser's
        // compile-time-constant username on every test method. Made
        // idempotent (find-or-create) since the concurrency test below
        // permanently commits its data, including this row, so later test
        // methods in the same run must not try to re-insert it.
        if (userRepository.findByUsername("pa-multi-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("pa-multi-admin")
                    .password("password")
                    .fullName("System Admin")
                    .email("pa-multi-admin@waad.ly")
                    .userType("SUPER_ADMIN")
                    .active(true)
                    .build());
        }

        Employer employer = employerRepository.save(Employer.builder()
                .name("Test Company")
                .code("EMP-PA-MULTI-TEST-" + uniqueSuffix)
                .active(true)
                .build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Standard Plan")
                .policyCode("POL-PA-MULTI-TEST-" + uniqueSuffix)
                .employer(employer)
                .annualLimit(new BigDecimal("50000"))
                .defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE)
                .active(true)
                .build());

        member = memberRepository.save(Member.builder()
                .fullName("Jane Doe")
                .barcode("PA-MULTI-TEST-" + uniqueSuffix)
                .nationalNumber("PA-MULTI-TEST-" + uniqueSuffix)
                .employer(employer)
                .benefitPolicy(policy)
                .active(true)
                .build());

        provider = providerRepository.save(Provider.builder()
                .name("Test Clinic")
                .providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-PA-MULTI-TEST-" + uniqueSuffix)
                .allowAllEmployers(true)
                .active(true)
                .build());

        categoryA = categoryRepository.save(MedicalCategory.builder()
                .code("CAT-A-" + uniqueSuffix).name("Category A").active(true).build());
        categoryB = categoryRepository.save(MedicalCategory.builder()
                .code("CAT-B-" + uniqueSuffix).name("Category B").active(true).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-PA-MULTI-TEST-" + uniqueSuffix)
                .contractNumber("CNT-PA-MULTI-2026-" + uniqueSuffix)
                .provider(provider)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE)
                .active(true)
                .build());

        pricingA = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceCode("SRV-A")
                .serviceName("Service A")
                .medicalCategory(categoryA)
                .basePrice(new BigDecimal("100"))
                .contractPrice(new BigDecimal("100"))
                .active(true)
                .build());

        pricingB = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceCode("SRV-B")
                .serviceName("Service B")
                .medicalCategory(categoryB)
                .basePrice(new BigDecimal("50"))
                .contractPrice(new BigDecimal("50"))
                .active(true)
                .build());

        visit = visitRepository.save(Visit.builder()
                .member(member)
                .providerId(provider.getId())
                .visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED)
                .build());
    }

    private PreAuthorizationCreateDto legacyDto() {
        return PreAuthorizationCreateDto.builder()
                .visitId(visit.getId())
                .providerId(provider.getId())
                .pricingItemId(pricingA.getId())
                .build();
    }

    private PreAuthorizationCreateDto multiLineDto() {
        return PreAuthorizationCreateDto.builder()
                .visitId(visit.getId())
                .providerId(provider.getId())
                .lines(List.of(
                        PreAuthorizationLineDto.builder().pricingItemId(pricingA.getId()).build(),
                        PreAuthorizationLineDto.builder().pricingItemId(pricingB.getId()).build()))
                .build();
    }

    // ==================== Creation: legacy vs multi-line ====================

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void legacySingleServiceCreate_producesOneLine_andIdenticalHeaderResult() {
        PreAuthorizationResponseDto response = preAuthorizationService.createPreAuthorization(legacyDto(), "pa-multi-admin");

        assertThat(response.getLines()).hasSize(1);
        assertThat(response.getLines().get(0).getServiceCode()).isEqualTo("SRV-A");
        assertThat(response.getServiceCode()).isEqualTo("SRV-A");
        assertThat(response.getStatus()).isEqualTo(PreAuthStatus.PENDING.name());

        // Header line-0 cache correctness, verified against the persisted entity
        // (contractPrice/etc. are deliberately null in the response DTO — see
        // mapToResponseDto's "pre-auth is non-financial" comment).
        var persisted = preAuthorizationRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getServiceCode()).isEqualTo("SRV-A");
        assertThat(persisted.getServiceCategoryId()).isEqualTo(categoryA.getId());
        assertThat(persisted.getContractPrice()).isEqualByComparingTo("100.00");
        assertThat(persisted.getPricingItemId()).isEqualTo(pricingA.getId());
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void multiLineCreate_persistsTwoLines_andHeaderAggregatesAreCorrect() {
        PreAuthorizationResponseDto response = preAuthorizationService.createPreAuthorization(multiLineDto(), "pa-multi-admin");

        assertThat(response.getLines()).hasSize(2);
        assertThat(response.getLines()).extracting("serviceCode").containsExactly("SRV-A", "SRV-B");

        var persisted = preAuthorizationRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getLines()).hasSize(2);
        // Header line-0 cache = first line
        assertThat(persisted.getServiceCode()).isEqualTo("SRV-A");
        // Header contractPrice = SUM across lines (100 + 50)
        assertThat(persisted.getContractPrice()).isEqualByComparingTo("150.00");
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void lineWithQuantityGreaterThanOne_multipliesUnitPriceIntoContractPrice() {
        // WAAD-PREAUTH-LINE-QUANTITY-FIX-1 regression: a provider requesting
        // quantity=3 of a 100.00 service must be charged 300.00, not 100.00 —
        // previously quantity was silently discarded and every line was
        // treated as quantity=1 regardless of what was requested.
        PreAuthorizationCreateDto dto = PreAuthorizationCreateDto.builder()
                .visitId(visit.getId())
                .providerId(provider.getId())
                .lines(List.of(
                        PreAuthorizationLineDto.builder().pricingItemId(pricingA.getId()).quantity(3).build(),
                        PreAuthorizationLineDto.builder().pricingItemId(pricingB.getId()).build()))
                .build();

        PreAuthorizationResponseDto response = preAuthorizationService.createPreAuthorization(dto, "pa-multi-admin");

        var quantityLine = response.getLines().get(0);
        assertThat(quantityLine.getQuantity()).isEqualTo(3);
        assertThat(quantityLine.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(quantityLine.getContractPrice()).isEqualByComparingTo("300.00");

        var defaultQuantityLine = response.getLines().get(1);
        assertThat(defaultQuantityLine.getQuantity()).isEqualTo(1);
        assertThat(defaultQuantityLine.getContractPrice()).isEqualByComparingTo("50.00");

        // Header contractPrice = SUM across lines: 300 (3x100) + 50 = 350
        var persisted = preAuthorizationRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getContractPrice()).isEqualByComparingTo("350.00");
        assertThat(persisted.getLines().get(0).getQuantity()).isEqualTo(3);
        assertThat(persisted.getLines().get(0).getUnitPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void duplicatePricingItemAcrossLines_isRejected() {
        PreAuthorizationCreateDto dto = PreAuthorizationCreateDto.builder()
                .visitId(visit.getId())
                .providerId(provider.getId())
                .lines(List.of(
                        PreAuthorizationLineDto.builder().pricingItemId(pricingA.getId()).build(),
                        PreAuthorizationLineDto.builder().pricingItemId(pricingA.getId()).build()))
                .build();

        assertThatThrownBy(() -> preAuthorizationService.createPreAuthorization(dto, "pa-multi-admin"))
                .isInstanceOf(ArchitecturalViolationException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void createDtoWithNeitherPricingItemIdNorLines_failsBeanValidation() {
        PreAuthorizationCreateDto dto = PreAuthorizationCreateDto.builder()
                .visitId(visit.getId())
                .providerId(provider.getId())
                .build();

        Set<ConstraintViolation<PreAuthorizationCreateDto>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void createDtoWithPricingItemIdOnly_passesBeanValidation() {
        Set<ConstraintViolation<PreAuthorizationCreateDto>> violations = validator.validate(legacyDto());
        assertThat(violations).isEmpty();
    }

    @Test
    void createDtoWithLinesOnly_passesBeanValidation() {
        Set<ConstraintViolation<PreAuthorizationCreateDto>> violations = validator.validate(multiLineDto());
        assertThat(violations).isEmpty();
    }

    // ==================== Category-limit accumulator ====================

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void sameCategoryLines_shareCombinedUsageAccumulator_andRejectWhenJointlyExceedingLimit() {
        // Both pricing items share categoryA; rule caps categoryA at 100 total.
        ProviderContractPricingItem pricingA2 = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(pricingA.getContract())
                .serviceCode("SRV-A2")
                .serviceName("Service A2")
                .medicalCategory(categoryA)
                .basePrice(new BigDecimal("60"))
                .contractPrice(new BigDecimal("60"))
                .active(true)
                .build());

        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(member.getBenefitPolicy())
                .medicalCategory(categoryA)
                .coveragePercent(80)
                .amountLimit(new BigDecimal("100"))
                .build());

        PreAuthorizationCreateDto dto = PreAuthorizationCreateDto.builder()
                .visitId(visit.getId())
                .providerId(provider.getId())
                .lines(List.of(
                        PreAuthorizationLineDto.builder().pricingItemId(pricingA.getId()).build(), // 100
                        PreAuthorizationLineDto.builder().pricingItemId(pricingA2.getId()).build())) // +60 = 160 > 100
                .build();

        assertThatThrownBy(() -> preAuthorizationService.createPreAuthorization(dto, "pa-multi-admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("تجاوز الحد المسموح");
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void sameCategoryLines_withinCombinedLimit_bothSucceed() {
        ProviderContractPricingItem pricingA2 = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(pricingA.getContract())
                .serviceCode("SRV-A3")
                .serviceName("Service A3")
                .medicalCategory(categoryA)
                .basePrice(new BigDecimal("20"))
                .contractPrice(new BigDecimal("20"))
                .active(true)
                .build());

        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(member.getBenefitPolicy())
                .medicalCategory(categoryA)
                .coveragePercent(80)
                .amountLimit(new BigDecimal("1000"))
                .build());

        PreAuthorizationCreateDto dto = PreAuthorizationCreateDto.builder()
                .visitId(visit.getId())
                .providerId(provider.getId())
                .lines(List.of(
                        PreAuthorizationLineDto.builder().pricingItemId(pricingA.getId()).build(),
                        PreAuthorizationLineDto.builder().pricingItemId(pricingA2.getId()).build()))
                .build();

        PreAuthorizationResponseDto response = preAuthorizationService.createPreAuthorization(dto, "pa-multi-admin");
        assertThat(response.getLines()).hasSize(2);
    }

    // ==================== Per-line decisions + finalize ====================

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void perLineApprovalCeiling_cannotExceedOwnLinesContractPrice() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(multiLineDto(), "pa-multi-admin");
        Long lineAId = created.getLines().get(0).getId(); // contractPrice = 100

        PreAuthorizationLineDecisionDto tooHigh = PreAuthorizationLineDecisionDto.builder()
                .decision(LineReviewDecision.APPROVED)
                .approvedAmount(new BigDecimal("150.00")) // exceeds line's own 100 ceiling
                .build();

        assertThatThrownBy(() -> preAuthorizationService.submitLineDecision(created.getId(), lineAId, tooHigh, "pa-multi-admin"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void mixedLineDecisions_financialCeilingIsPerLine_cannotBorrowFromSiblingLine() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(multiLineDto(), "pa-multi-admin");
        Long lineAId = created.getLines().get(0).getId(); // contractPrice = 100
        Long lineBId = created.getLines().get(1).getId(); // contractPrice = 50

        // Approve line A at only 40 (well under its ceiling) — leaves 60 of
        // "headroom" on line A that must NOT be borrowable by line B.
        preAuthorizationService.submitLineDecision(created.getId(), lineAId,
                PreAuthorizationLineDecisionDto.builder()
                        .decision(LineReviewDecision.APPROVED)
                        .approvedAmount(new BigDecimal("40.00"))
                        .build(),
                "pa-multi-admin");

        // Line B's own ceiling is 50 — attempting 90 (which would only fit if
        // borrowing from line A's unused headroom) must fail.
        PreAuthorizationLineDecisionDto borrowAttempt = PreAuthorizationLineDecisionDto.builder()
                .decision(LineReviewDecision.APPROVED)
                .approvedAmount(new BigDecimal("90.00"))
                .build();
        assertThatThrownBy(() -> preAuthorizationService.submitLineDecision(created.getId(), lineBId, borrowAttempt, "pa-multi-admin"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void mixedApprovedAndRejectedLines_finalizeToPartiallyApproved() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(multiLineDto(), "pa-multi-admin");
        Long lineAId = created.getLines().get(0).getId();
        Long lineBId = created.getLines().get(1).getId();

        preAuthorizationService.submitLineDecision(created.getId(), lineAId,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(), "pa-multi-admin");
        preAuthorizationService.submitLineDecision(created.getId(), lineBId,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.REJECTED).reason("Not covered").build(),
                "pa-multi-admin");

        PreAuthorizationResponseDto finalized = preAuthorizationService.finalizePreAuthorizationReview(created.getId(), "pa-multi-admin");

        assertThat(finalized.getStatus()).isEqualTo(PreAuthStatus.PARTIALLY_APPROVED.name());
        var persisted = preAuthorizationRepository.findById(created.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PreAuthStatus.PARTIALLY_APPROVED);
        // Only line A (100) contributes to the header aggregate; line B is rejected.
        assertThat(persisted.getApprovedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void allApprovedLines_finalizeToApproved() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(multiLineDto(), "pa-multi-admin");
        for (var line : created.getLines()) {
            preAuthorizationService.submitLineDecision(created.getId(), line.getId(),
                    PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(), "pa-multi-admin");
        }

        PreAuthorizationResponseDto finalized = preAuthorizationService.finalizePreAuthorizationReview(created.getId(), "pa-multi-admin");

        assertThat(finalized.getStatus()).isEqualTo(PreAuthStatus.APPROVED.name());
        var persisted = preAuthorizationRepository.findById(created.getId()).orElseThrow();
        // Sum of both lines' full contract prices: 100 + 50
        assertThat(persisted.getApprovedAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void allRejectedLines_finalizeToRejected() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(multiLineDto(), "pa-multi-admin");
        for (var line : created.getLines()) {
            preAuthorizationService.submitLineDecision(created.getId(), line.getId(),
                    PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.REJECTED).reason("No").build(),
                    "pa-multi-admin");
        }

        PreAuthorizationResponseDto finalized = preAuthorizationService.finalizePreAuthorizationReview(created.getId(), "pa-multi-admin");

        assertThat(finalized.getStatus()).isEqualTo(PreAuthStatus.REJECTED.name());
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void finalize_withUndecidedLine_isRejected() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(multiLineDto(), "pa-multi-admin");
        Long lineAId = created.getLines().get(0).getId();
        preAuthorizationService.submitLineDecision(created.getId(), lineAId,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(), "pa-multi-admin");
        // lineB left undecided

        assertThatThrownBy(() -> preAuthorizationService.finalizePreAuthorizationReview(created.getId(), "pa-multi-admin"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void submitLineDecision_missingRejectionReason_isRejected() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(legacyDto(), "pa-multi-admin");
        Long lineId = created.getLines().get(0).getId();

        PreAuthorizationLineDecisionDto noReason = PreAuthorizationLineDecisionDto.builder()
                .decision(LineReviewDecision.REJECTED)
                .build();

        assertThatThrownBy(() -> preAuthorizationService.submitLineDecision(created.getId(), lineId, noReason, "pa-multi-admin"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void submitLineDecision_lineBelongingToAnotherPreAuthorization_isRejected() {
        PreAuthorizationResponseDto preAuthA = preAuthorizationService.createPreAuthorization(legacyDto(), "pa-multi-admin");

        // Second visit + pre-authorization for the same member/provider — a
        // genuinely different PreAuthorization aggregate.
        Visit visit2 = visitRepository.save(Visit.builder()
                .member(member)
                .providerId(provider.getId())
                .visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED)
                .build());
        PreAuthorizationResponseDto preAuthB = preAuthorizationService.createPreAuthorization(
                PreAuthorizationCreateDto.builder()
                        .visitId(visit2.getId())
                        .providerId(provider.getId())
                        .pricingItemId(pricingB.getId())
                        .build(),
                "pa-multi-admin");
        Long lineIdFromB = preAuthB.getLines().get(0).getId();

        // lineIdFromB does not belong to preAuthA — must be rejected, not
        // silently applied to the wrong aggregate.
        assertThatThrownBy(() -> preAuthorizationService.submitLineDecision(preAuthA.getId(), lineIdFromB,
                PreAuthorizationLineDecisionDto.builder().decision(LineReviewDecision.APPROVED).build(), "pa-multi-admin"))
                .isInstanceOf(com.waad.tba.common.exception.ResourceNotFoundException.class);
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void submitLineDecision_onTerminalPreAuthorization_isRejected() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(legacyDto(), "pa-multi-admin");
        Long lineId = created.getLines().get(0).getId();

        // Reject the whole (single-line) pre-authorization via the legacy
        // endpoint — status is now REJECTED, a terminal state.
        preAuthorizationService.rejectPreAuthorization(created.getId(),
                com.waad.tba.modules.preauthorization.dto.PreAuthorizationRejectDto.builder()
                        .rejectionReason("Not covered").build(),
                "pa-multi-admin");

        PreAuthorizationLineDecisionDto attempt = PreAuthorizationLineDecisionDto.builder()
                .decision(LineReviewDecision.APPROVED).build();
        assertThatThrownBy(() -> preAuthorizationService.submitLineDecision(created.getId(), lineId, attempt, "pa-multi-admin"))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ==================== Optimistic locking (concurrency protection) ====================

    /**
     * Proves PreAuthorizationLine's @Version column (added in Phase 1)
     * genuinely triggers Hibernate's optimistic-lock check under real
     * concurrent modification — not just documented intent. Two separate
     * transactions load the same line; the first commits a change (bumping
     * its version), then the second — still holding the pre-commit version
     * in memory — attempts to save and must fail with
     * ObjectOptimisticLockingFailureException (which GlobalExceptionHandler
     * maps to HTTP 409, verified separately in
     * GlobalExceptionHandlerMessageArTest).
     */
    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void concurrentLineModification_staleVersionIsRejectedByOptimisticLock() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(legacyDto(), "pa-multi-admin");

        // WAAD-INTEGRATION-TEST-CONTEXT-1 (same pattern as
        // ClaimLifecycleIntegrationTest): the REQUIRES_NEW sub-transactions
        // below use a genuinely separate physical transaction/connection —
        // under READ COMMITTED they cannot see this test's own setup/create
        // data until it is actually committed, not just flushed within the
        // still-open outer @Transactional wrapper. Force a real commit here;
        // the Testcontainers-managed Postgres instance is torn down after
        // this test run, so the permanently-committed rows are harmless.
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();

        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Transaction A: load the line, keep the reference (do not save yet).
        var lineA = tx.execute(status -> preAuthorizationRepository.findById(created.getId())
                .orElseThrow().getLines().get(0));

        // Transaction B: load the SAME row independently and commit a change —
        // this bumps the row's version in the database.
        tx.execute(status -> {
            var lineB = preAuthorizationRepository.findById(created.getId()).orElseThrow().getLines().get(0);
            lineB.setRejectionReason("Changed by transaction B");
            return null;
        });

        // Transaction A now tries to save its stale (pre-B) in-memory copy.
        assertThatThrownBy(() -> tx.execute(status -> {
            lineA.setRejectionReason("Changed by stale transaction A");
            preAuthorizationRepository.save(lineA.getPreAuthorization());
            return null;
        })).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    // ==================== Legacy whole-header endpoints still work unchanged ====================

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void legacyApproveEndpoint_stillWorksForSingleLineRequest() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(legacyDto(), "pa-multi-admin");

        PreAuthorizationResponseDto approved = preAuthorizationService.approvePreAuthorization(created.getId(),
                com.waad.tba.modules.preauthorization.dto.PreAuthorizationApproveDto.builder().build(), "pa-multi-admin");

        assertThat(approved.getStatus()).isEqualTo(PreAuthStatus.APPROVED.name());
        var persisted = preAuthorizationRepository.findById(created.getId()).orElseThrow();
        assertThat(persisted.getLines()).hasSize(1);
        assertThat(persisted.getLines().get(0).getReviewerDecision()).isEqualTo(LineReviewDecision.APPROVED);
        assertThat(persisted.getLines().get(0).getApprovedAmount()).isEqualByComparingTo(persisted.getContractPrice());
    }

    @Test
    @WithMockUser(username = "pa-multi-admin", roles = "SUPER_ADMIN")
    void legacyApproveEndpoint_rejectedForGenuineMultiLineRequest() {
        PreAuthorizationResponseDto created = preAuthorizationService.createPreAuthorization(multiLineDto(), "pa-multi-admin");

        assertThatThrownBy(() -> preAuthorizationService.approvePreAuthorization(created.getId(),
                com.waad.tba.modules.preauthorization.dto.PreAuthorizationApproveDto.builder().build(), "pa-multi-admin"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
