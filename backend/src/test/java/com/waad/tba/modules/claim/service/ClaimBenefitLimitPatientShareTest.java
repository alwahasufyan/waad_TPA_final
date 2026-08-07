package com.waad.tba.modules.claim.service;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.dto.ClaimApproveDto;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
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
 * WAAD-CLAIMS-FINANCIAL-CORRECTNESS-1 regression coverage (2026-08-06).
 *
 * Confirmed business rules for a line that exceeds its benefit-policy
 * category amount limit:
 *   1. Any amount exceeding the member's benefit limit (limitRefused) is
 *      fully borne by the member — never split by coveragePercent, never
 *      charged to the provider/insurer.
 *   2. Member copay is calculated ONLY on the allowed/covered amount left
 *      after excluding that benefit-limit excess.
 *   3. Total member responsibility = the over-limit excess + copay on the
 *      allowed amount.
 *   4. Provider discount / Waad TPA revenue is calculated only on the
 *      insurer/company share AFTER benefit-limit exclusion and copay.
 *
 * {@code ClaimLine.patientShare} persists the member's TOTAL responsibility
 * (copay + over-limit excess); {@code ClaimLine.limitRefused} persists just
 * the over-limit excess as a breakdown (so copay-only = patientShare -
 * limitRefused). {@code ClaimLine.companyShareBeforeDiscount} is the
 * insurer's share of the ALLOWED amount before any TPA discount;
 * {@code companyShare} is the final, post-discount net provider amount.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class ClaimBenefitLimitPatientShareTest {

    @Autowired private ClaimService claimService;
    @Autowired private ClaimReviewService claimReviewService;
    @Autowired private ClaimRepository claimRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private com.waad.tba.modules.settlement.repository.ProviderAccountRepository providerAccountRepository;

    private Visit visit;
    private ProviderContractPricingItem pricingItem;
    private Long categoryId;
    private BenefitPolicy policy;
    private Employer employer;
    private String suffix;

    @BeforeEach
    void setUp() {
        // Idempotent (find-or-create): some tests in this class force a real commit
        // (TestTransaction.flagForCommit()) to exercise AFTER_COMMIT credit listeners,
        // which permanently persists this user — a later test's own setUp() must not
        // try to re-insert it.
        if (userRepository.findByUsername("fin-audit-admin").isEmpty()) {
            userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                    .username("fin-audit-admin").password("password").fullName("Admin")
                    .email("fin-audit-admin@waad.ly").userType("SUPER_ADMIN").active(true).build());
        }

        suffix = String.valueOf(System.nanoTime()).substring(9);
        employer = employerRepository.save(Employer.builder()
                .name("Fin Audit Co").code("EMP-FA-" + suffix).active(true).build());

        policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Fin Audit Plan").policyCode("POL-FA-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-FA-" + suffix).name("Fin Audit Category").active(true).build());
        categoryId = category.getId();

        // Coverage 80%, amount limit 100 — proves the cap-vs-split ordering.
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .coveragePercent(80).amountLimit(new BigDecimal("100"))
                .active(true).build());

        var providerAndPricing = createProviderContractAndPricing(category, "0");
        pricingItem = providerAndPricing.pricingItem;

        Member member = memberRepository.save(Member.builder()
                .fullName("Fin Audit Member").barcode("FA-" + suffix)
                .nationalNumber("FA-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());

        visit = visitRepository.save(Visit.builder()
                .member(member).providerId(providerAndPricing.provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
    }

    private record ProviderAndPricing(Provider provider, ProviderContractPricingItem pricingItem) {}

    private ProviderAndPricing createProviderContractAndPricing(MedicalCategory category, String discountPercent) {
        String providerSuffix = suffix + "-" + discountPercent;
        Provider provider = providerRepository.save(Provider.builder()
                .name("Fin Audit Clinic " + providerSuffix).providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-FA-" + providerSuffix).allowAllEmployers(true).active(true).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-FA-" + providerSuffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .discountPercent(new BigDecimal(discountPercent))
                .status(ContractStatus.ACTIVE).active(true).build());

        ProviderContractPricingItem item = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode("SRV-FA-" + providerSuffix).serviceName("Fin Audit Service")
                .medicalCategory(category)
                .basePrice(new BigDecimal("140")).contractPrice(new BigDecimal("140"))
                .active(true).build());

        providerAccountRepository.save(com.waad.tba.modules.settlement.entity.ProviderAccount.builder()
                .providerId(provider.getId()).build());

        return new ProviderAndPricing(provider, item);
    }

    @Test
    @WithMockUser(username = "fin-audit-admin", roles = "SUPER_ADMIN")
    void lineExceedingBenefitLimit_memberBearsOverLimitExcessInFull_copayOnlyOnAllowedAmount() {
        ClaimCreateDto createDto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(categoryId)
                        // A real frontend submission always echoes an entered
                        // unit price — omitting it here would make the engine's
                        // own min(entered, contract) guard mask this bug entirely.
                        .unitPrice(new BigDecimal("140"))
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.SUBMITTED)
                .build();

        ClaimViewDto claim = claimService.createClaim(createDto);
        var line = claim.getLines().get(0);

        assertThat(line.getRequestedTotal()).isEqualByComparingTo("140.00");

        // The $40 over-limit excess is the member's own responsibility — it is
        // no longer a "refusal" charged against the provider/insurer.
        assertThat(line.getRefusedAmount()).isEqualByComparingTo("0.00");
        assertThat(line.getLimitRefused()).isEqualByComparingTo("40.00");

        // Only the ALLOWED 100 is split 80/20 between company and member copay.
        // Member's TOTAL responsibility = 20 copay + 40 over-limit = 60.
        assertThat(line.getPatientShare()).isEqualByComparingTo("60.00");
        assertThat(line.getCompanyShareBeforeDiscount()).isEqualByComparingTo("80.00");
        // No provider discount configured on this test's contract (0%), so the
        // final payable company share equals the pre-discount share.
        assertThat(line.getCompanyShare()).isEqualByComparingTo("80.00");

        // Header-level totals must reconcile: patientCoPay = member's total (60),
        // netProviderAmount = company's final payable (80).
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("60.00");
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("80.00");
    }

    /**
     * The exact controlled scenario from the WAAD-CLAIMS-FINANCIAL-CORRECTNESS-1
     * business-rule confirmation: requested=140, limit=100, coverage=80%,
     * provider discount=10% must produce member copay=20, member over-limit=40,
     * member total=60, company share before discount=80, TPA revenue=8, net
     * provider amount=72. Also proves companyDiscountAmount (TPA revenue, Fix B)
     * is correctly persisted at approval rather than staying at a stale draft
     * value, since this claim is created directly in APPROVED status.
     */
    @Test
    @WithMockUser(username = "fin-audit-admin", roles = "SUPER_ADMIN")
    void controlledScenario_requested140_limit100_coverage80_discount10() {
        var category = medicalCategoryRepository.findById(categoryId).orElseThrow();
        var providerAndPricing = createProviderContractAndPricing(category, "10");

        Member member = memberRepository.save(Member.builder()
                .fullName("Fin Audit Member Disc").barcode("FA-DISC-" + suffix)
                .nationalNumber("FA-DISC-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());

        Visit discVisit = visitRepository.save(Visit.builder()
                .member(member).providerId(providerAndPricing.provider().getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());

        ClaimCreateDto createDto = ClaimCreateDto.builder()
                .visitId(discVisit.getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(providerAndPricing.pricingItem().getId())
                        .serviceCode(providerAndPricing.pricingItem().getServiceCode())
                        .serviceCategoryId(categoryId)
                        .unitPrice(new BigDecimal("140"))
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();

        ClaimViewDto claim = claimService.createClaim(createDto);
        var line = claim.getLines().get(0);

        BigDecimal memberCopay = line.getPatientShare().subtract(line.getLimitRefused());
        assertThat(memberCopay).isEqualByComparingTo("20.00");
        assertThat(line.getLimitRefused()).isEqualByComparingTo("40.00");
        assertThat(line.getPatientShare()).isEqualByComparingTo("60.00");
        assertThat(line.getCompanyShareBeforeDiscount()).isEqualByComparingTo("80.00");
        assertThat(line.getProviderDiscountAmount()).isEqualByComparingTo("8.00");
        assertThat(line.getCompanyShare()).isEqualByComparingTo("72.00");

        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("60.00");
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("72.00");

        // WAAD-CLAIMS-FINANCIAL-CORRECTNESS-1 accounting-invariant verification:
        // limitRefused (40) is charged to the member exactly once — folded into
        // patientShare — and is NOT also present in refusedAmount (the
        // provider-attributable deduction), which must be zero here since
        // nothing was manually rejected or price-capped on this line.
        assertThat(line.getRefusedAmount()).isEqualByComparingTo("0.00");
        assertThat(line.getPriceExcessRefused()).isEqualByComparingTo("0.00");

        // Full reconciliation to gross: every dinar of the requested 140 is
        // accounted for across exactly the member's share, the provider's final
        // payable, the provider-attributable refusal (0), and the TPA discount.
        // WAAD-CLAIMS-FINANCIAL-CORRECTNESS-2: priceExcessRefused is deliberately
        // EXCLUDED from this sum — it is not part of gross's pie at all. gross
        // (requestedTotal) is already derived from the resolved CONTRACT price
        // (never the provider's entered price), so it never included any
        // over-contract excess to begin with; priceExcessRefused is a purely
        // informational/audit field describing that excess, not a further
        // deduction from gross. See diagnostic_priceRefused_... below for the
        // scenario where this distinction actually matters (priceExcessRefused > 0).
        BigDecimal reconciled = line.getPatientShare()
                .add(line.getCompanyShare())
                .add(line.getRefusedAmount())
                .add(line.getProviderDiscountAmount());
        assertThat(reconciled).isEqualByComparingTo(line.getRequestedTotal());
        assertThat(reconciled).isEqualByComparingTo("140.00");
    }

    /**
     * Fix B regression: {@code Claim.companyDiscountAmount} (the TPA revenue
     * figure CompanyProfitReportService/getCompanyProfitReport relies on) was
     * previously NEVER set by the real review/approval workflow
     * (ClaimReviewService.processApprovalAsync) — only Claim.calculateFields()'s
     * draft-time preview set it, and that preview explicitly refuses to run
     * again once the claim is finalized (APPROVED/SETTLED), so it stayed frozen
     * at whatever it was during the SUBMITTED draft. This test goes through the
     * actual startReview -> requestApproval workflow (not direct-as-APPROVED
     * creation, which the other tests in this class use and which happens to
     * already populate the field via the entity's own preview hook) to prove
     * companyDiscountAmount is correctly (re)computed at approval, matching the
     * controlled scenario's TPA revenue = 8.00.
     */
    @Test
    @WithMockUser(username = "fin-audit-admin", roles = { "SUPER_ADMIN", "REVIEWER" })
    void companyDiscountAmount_isRecalculatedAtApproval_notStaleFromDraft() {
        var category = medicalCategoryRepository.findById(categoryId).orElseThrow();
        var providerAndPricing = createProviderContractAndPricing(category, "10");

        Member member = memberRepository.save(Member.builder()
                .fullName("Fin Audit Member Review").barcode("FA-REV-" + suffix)
                .nationalNumber("FA-REV-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());

        Visit reviewVisit = visitRepository.save(Visit.builder()
                .member(member).providerId(providerAndPricing.provider().getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());

        ClaimCreateDto createDto = ClaimCreateDto.builder()
                .visitId(reviewVisit.getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(providerAndPricing.pricingItem().getId())
                        .serviceCode(providerAndPricing.pricingItem().getServiceCode())
                        .serviceCategoryId(categoryId)
                        .unitPrice(new BigDecimal("140"))
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.SUBMITTED)
                .build();

        ClaimViewDto createdClaim = claimService.createClaim(createDto);
        assertThat(createdClaim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);

        claimReviewService.startReview(createdClaim.getId());
        claimReviewService.requestApproval(createdClaim.getId(),
                ClaimApproveDto.builder().useSystemCalculation(true).notes("fin-audit approval").build());

        // requestApproval's internal processApprovalAsync call is a synchronous
        // self-invocation inside this test's own transaction (see
        // ClaimLifecycleIntegrationTest for the full explanation) — commit and
        // restart the test transaction so the change is actually persisted and
        // re-readable from a clean entity-manager state, exactly like a real
        // (committing) HTTP request would leave it.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        entityManager.clear();

        Claim approvedClaim = claimRepository.findById(createdClaim.getId()).orElseThrow();
        assertThat(approvedClaim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(approvedClaim.getPatientCoPay()).isEqualByComparingTo("60.00");
        assertThat(approvedClaim.getNetProviderAmount()).isEqualByComparingTo("72.00");
        assertThat(approvedClaim.getCompanyDiscountAmount()).isEqualByComparingTo("8.00");
    }

    /**
     * WAAD-CLAIMS-FINANCIAL-CORRECTNESS-2 regression (audit, 2026-08-07):
     * priceRefused arises when a provider's entered unit price exceeds the
     * contract price. gross ({@code lineRequestedTotal}) is ALREADY derived
     * from the resolved CONTRACT price (PROVIDER-PORTAL-DATA-1), never the
     * entered price — so the over-contract excess was never part of gross to
     * begin with. It must NOT also be subtracted from the allowed/split
     * basis (that was a genuine double-subtraction bug, empirically caught
     * here: contract=100, entered=150, coverage=80%, no discount previously
     * produced companyShare=40 instead of the correct 80). priceRefused is
     * charged to no one — it is persisted as priceExcessRefused purely as an
     * audit/display field, and reconciliation excludes it (see the
     * controlled-scenario test's comment for why).
     */
    @Test
    @WithMockUser(username = "fin-audit-admin", roles = "SUPER_ADMIN")
    void priceRefused_whenEnteredPriceExceedsContractPrice_doesNotReduceAllowedAmount() {
        var category = medicalCategoryRepository.findById(categoryId).orElseThrow();
        String priceSuffix = suffix + "-price";
        Provider provider = providerRepository.save(Provider.builder()
                .name("Fin Audit Price Clinic").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-FA-" + priceSuffix).allowAllEmployers(true).active(true).build());
        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-FA-" + priceSuffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .discountPercent(BigDecimal.ZERO)
                .status(ContractStatus.ACTIVE).active(true).build());
        // Contract price 100, well under the category's amountLimit=100 rule from
        // setUp() (== not >, so no limitRefused interference).
        ProviderContractPricingItem item = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode("SRV-FA-" + priceSuffix).serviceName("Fin Audit Price Service")
                .medicalCategory(category)
                .basePrice(new BigDecimal("100")).contractPrice(new BigDecimal("100"))
                .active(true).build());
        providerAccountRepository.save(com.waad.tba.modules.settlement.entity.ProviderAccount.builder()
                .providerId(provider.getId()).build());
        Member member = memberRepository.save(Member.builder()
                .fullName("Fin Audit Price Member").barcode("FA-PRICE-" + suffix)
                .nationalNumber("FA-PRICE-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());
        Visit priceVisit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());

        // Provider enters 150 for a service whose contract price is 100.
        ClaimCreateDto createDto = ClaimCreateDto.builder()
                .visitId(priceVisit.getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(item.getId())
                        .serviceCode(item.getServiceCode())
                        .serviceCategoryId(category.getId())
                        .unitPrice(new BigDecimal("150"))
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();

        ClaimViewDto claim = claimService.createClaim(createDto);
        var line = claim.getLines().get(0);

        assertThat(line.getRequestedTotal()).isEqualByComparingTo("100.00");
        assertThat(line.getPriceExcessRefused()).isEqualByComparingTo("50.00");
        assertThat(line.getLimitRefused()).isEqualByComparingTo("0.00");
        // Full, correct 80/20 split of the (already contract-capped) 100 gross —
        // priceExcessRefused does not shrink the base a second time.
        assertThat(line.getPatientShare()).isEqualByComparingTo("20.00");
        assertThat(line.getCompanyShareBeforeDiscount()).isEqualByComparingTo("80.00");
        assertThat(line.getCompanyShare()).isEqualByComparingTo("80.00");
        assertThat(line.getRefusedAmount()).isEqualByComparingTo("0.00");

        // Reconciliation excludes priceExcessRefused (it was never part of gross).
        BigDecimal reconciled = line.getPatientShare().add(line.getCompanyShare())
                .add(line.getRefusedAmount()).add(line.getProviderDiscountAmount());
        assertThat(reconciled).isEqualByComparingTo(line.getRequestedTotal());
    }

    /**
     * manualRefused (partial): a reviewer's manual, non-full-rejection
     * reduction is provider-attributable — it is subtracted from the
     * company's (post benefit-limit/copay) share, never from the member's
     * copay, and never treated as member-borne like limitRefused.
     */
    @Test
    @WithMockUser(username = "fin-audit-admin", roles = "SUPER_ADMIN")
    void manualRefused_partial_isProviderAttributable_notMemberBorne() {
        // Reuses setUp()'s own provider/contract/pricingItem/visit (140/100/80%,
        // 0% discount) — the exact same base scenario as the class's first test,
        // just with a manual reduction layered on top.
        // Same 140/100/80% setup as the base scenario (limitRefused=40, copay=20,
        // companyShareBeforeDiscount=80), PLUS a reviewer's manual reduction of 15
        // on top (e.g. a documentation/medical-necessity partial reduction).
        ClaimCreateDto createDto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(categoryId)
                        .unitPrice(new BigDecimal("140"))
                        .quantity(1)
                        .manualRefusedAmount(new BigDecimal("15"))
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();

        ClaimViewDto claim = claimService.createClaim(createDto);
        var line = claim.getLines().get(0);

        assertThat(line.getLimitRefused()).isEqualByComparingTo("40.00");
        // Member's total is unaffected by the manual reduction — still copay(20) +
        // over-limit(40) = 60. manualRefused never touches the member's side.
        assertThat(line.getPatientShare()).isEqualByComparingTo("60.00");
        assertThat(line.getCompanyShareBeforeDiscount()).isEqualByComparingTo("80.00");
        // The manual 15 is subtracted from the company's (no-discount) share: 80-15=65.
        assertThat(line.getRefusedAmount()).isEqualByComparingTo("15.00");
        assertThat(line.getCompanyShare()).isEqualByComparingTo("65.00");

        BigDecimal reconciled = line.getPatientShare().add(line.getCompanyShare())
                .add(line.getRefusedAmount()).add(line.getProviderDiscountAmount());
        assertThat(reconciled).isEqualByComparingTo(line.getRequestedTotal());
        assertThat(reconciled).isEqualByComparingTo("140.00");
    }

    /**
     * WAAD-CLAIMS-FINANCIAL-CORRECTNESS-3 regression (business-rule confirmation,
     * 2026-08-07): a FULL manual rejection ({@code ClaimLine.rejected=true}, set
     * either via {@code ClaimLineDto.rejected} at entry/update time or via
     * {@code ClaimService.submitLineDecision}'s REJECTED decision — both feed the
     * identical "Option 2" formula below) represents a reviewer/adjudicator
     * judgment that an ALREADY-RENDERED service is not covered — e.g. medical
     * necessity, documentation, or policy-exclusion grounds. This is distinct
     * from:
     *   - a line never submitted at all (never becomes a ClaimLine — no charge
     *     to anyone), and
     *   - benefit-limit excess ({@link #lineExceedingBenefitLimit_memberBearsOverLimitExcessInFull_copayOnlyOnAllowedAmount()}),
     *     where the provider is still paid for the allowed portion.
     *
     * Confirmed business rule for full rejection: the member's normal copay
     * stays active on the FULL requested amount (unaffected by the rejection),
     * and the provider absorbs the entire remaining company share as
     * refusedAmount. Rejection reasons are free text with no structured
     * liability signal (no field on ProviderContract/BenefitPolicy/
     * BenefitPolicyRule varies this by reason), so this rule is applied
     * uniformly — matching the explicit UI confirmation the reviewer sees
     * before triggering it ("رفض هذا البند بالكامل... حصة الشركة ستصبح صفراً").
     */
    @Test
    @WithMockUser(username = "fin-audit-admin", roles = "SUPER_ADMIN")
    void manualRefused_full_memberNormalCopayUnaffected_providerAbsorbsRemainder() {
        // Same 140/100/80% base scenario as setUp(), 0% discount, but the line is
        // fully rejected by a reviewer instead of just capped by the benefit limit.
        ClaimCreateDto createDto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem.getId())
                        .serviceCode(pricingItem.getServiceCode())
                        .serviceCategoryId(categoryId)
                        .unitPrice(new BigDecimal("140"))
                        .quantity(1)
                        .rejected(true)
                        .rejectionReason("Documentation insufficient")
                        .build()))
                .status(ClaimStatus.APPROVED)
                .build();

        ClaimViewDto claim = claimService.createClaim(createDto);
        var line = claim.getLines().get(0);

        assertThat(line.getRejected()).isTrue();

        // Benefit-limit exclusion does not apply to a fully-rejected line — the
        // member's copay basis is the FULL requested amount, not the limit-capped
        // allowed amount, and limitRefused is not separately tracked here (it
        // would double-count against a line already 100% refused).
        assertThat(line.getLimitRefused()).isEqualByComparingTo("0.00");

        // Member's normal copay (20% of the full 140) is unaffected by the
        // rejection — 28.00, not reduced or waived because the provider was denied.
        assertThat(line.getPatientShare()).isEqualByComparingTo("28.00");

        // The provider absorbs the entire remaining company share (140 - 28 = 112)
        // as refusedAmount — companyShare (final payable) is zero.
        assertThat(line.getCompanyShareBeforeDiscount()).isEqualByComparingTo("112.00");
        assertThat(line.getRefusedAmount()).isEqualByComparingTo("112.00");
        assertThat(line.getCompanyShare()).isEqualByComparingTo("0.00");

        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("28.00");
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("0.00");

        BigDecimal reconciled = line.getPatientShare().add(line.getCompanyShare())
                .add(line.getRefusedAmount()).add(line.getProviderDiscountAmount());
        assertThat(reconciled).isEqualByComparingTo(line.getRequestedTotal());
        assertThat(reconciled).isEqualByComparingTo("140.00");
    }

    /**
     * Per-member benefit limit, verified through the REAL async review workflow
     * (startReview -> requestApproval -> ClaimReviewService.processApprovalAsync
     * -> BenefitPolicyCoverageService.validateAmountLimits), not just at
     * line-level creation. A second claim that would push the member's
     * cumulative approved total over their perMemberLimit must be rejected by
     * the approval pipeline itself — the claim reverts to UNDER_REVIEW (per
     * processApprovalAsync's own catch/revert behavior) rather than becoming
     * APPROVED, and the provider account must NOT be credited for it.
     */
    @Test
    @WithMockUser(username = "fin-audit-admin", roles = { "SUPER_ADMIN", "REVIEWER" })
    void perMemberLimit_exceededOnSecondClaim_blocksApprovalViaRealAsyncPath_noCredit() {
        String limitSuffix = suffix.substring(Math.max(0, suffix.length() - 6));
        Employer limitEmployer = employerRepository.save(Employer.builder()
                .name("Fin Audit Limit Co").code("EL" + limitSuffix).active(true).build());
        BenefitPolicy limitPolicy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Fin Audit Limit Plan").policyCode("POL-LIMIT-" + limitSuffix).employer(limitEmployer)
                .annualLimit(new BigDecimal("999999")).perMemberLimit(new BigDecimal("100")).defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
        MedicalCategory limitCategory = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-LIMIT-" + limitSuffix).name("Fin Audit Limit Category").active(true).build());
        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(limitPolicy).medicalCategory(limitCategory)
                .coveragePercent(100).active(true).build());

        // NOTE: createProviderContractAndPricing() (used by the other tests in this
        // class) hardcodes contractPrice=140 — unsuitable here since the resolved
        // CONTRACT price always wins over any entered unitPrice
        // (PROVIDER-PORTAL-DATA-1), so this test builds its own two pricing items at
        // the exact amounts (80, 50) its limit scenario needs.
        Provider limitProvider = providerRepository.save(Provider.builder()
                .name("Fin Audit Limit Clinic").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-LIMIT-" + limitSuffix).allowAllEmployers(true).active(true).build());
        ProviderContract limitContract = contractRepository.save(ProviderContract.builder()
                .contractCode("CTR-LIMIT-" + limitSuffix).provider(limitProvider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .discountPercent(BigDecimal.ZERO)
                .status(ContractStatus.ACTIVE).active(true).build());
        ProviderContractPricingItem pricingItem80 = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(limitContract).serviceCode("SRV-LIMIT-80-" + limitSuffix).serviceName("Limit Service 80")
                .medicalCategory(limitCategory)
                .basePrice(new BigDecimal("80")).contractPrice(new BigDecimal("80"))
                .active(true).build());
        ProviderContractPricingItem pricingItem50 = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(limitContract).serviceCode("SRV-LIMIT-50-" + limitSuffix).serviceName("Limit Service 50")
                .medicalCategory(limitCategory)
                .basePrice(new BigDecimal("50")).contractPrice(new BigDecimal("50"))
                .active(true).build());
        providerAccountRepository.save(com.waad.tba.modules.settlement.entity.ProviderAccount.builder()
                .providerId(limitProvider.getId()).build());
        Member limitMember = memberRepository.save(Member.builder()
                .fullName("Fin Audit Limit Member").barcode("FA-LIMIT-" + limitSuffix)
                .nationalNumber("FA-LIMIT-" + limitSuffix).employer(limitEmployer).benefitPolicy(limitPolicy)
                .active(true).build());

        // Commit all prerequisite data first: validateAmountLimits now runs in a
        // genuinely separate REQUIRES_NEW transaction (WAAD-CLAIM-ASYNC-TRANSACTION-INTEGRITY-1),
        // which under READ COMMITTED cannot see this test's own still-uncommitted
        // setup rows — exactly like a real production request would never see
        // another, still-in-flight request's uncommitted writes either.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        entityManager.clear();
        limitMember = memberRepository.findById(limitMember.getId()).orElseThrow();
        limitProvider = providerRepository.findById(limitProvider.getId()).orElseThrow();
        pricingItem80 = pricingRepository.findById(pricingItem80.getId()).orElseThrow();
        pricingItem50 = pricingRepository.findById(pricingItem50.getId()).orElseThrow();

        // Claim 1: requested=80, within the 100 perMemberLimit — approves normally.
        Visit visit1 = visitRepository.save(Visit.builder()
                .member(limitMember).providerId(limitProvider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
        ClaimViewDto claim1 = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit1.getId()).serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem80.getId())
                        .serviceCode(pricingItem80.getServiceCode())
                        .serviceCategoryId(limitCategory.getId())
                        .unitPrice(new BigDecimal("80")).quantity(1).build()))
                .status(ClaimStatus.SUBMITTED)
                .build());
        claimReviewService.startReview(claim1.getId());
        claimReviewService.requestApproval(claim1.getId(),
                ClaimApproveDto.builder().useSystemCalculation(true).build());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        entityManager.clear();

        Claim reloadedClaim1 = claimRepository.findById(claim1.getId()).orElseThrow();
        assertThat(reloadedClaim1.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        // Claim 2: requested=50 -> cumulative 130 > perMemberLimit(100) -> must be
        // blocked by validateAmountLimits inside processApprovalAsync itself.
        Visit visit2 = visitRepository.save(Visit.builder()
                .member(limitMember).providerId(limitProvider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
        ClaimViewDto claim2 = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit2.getId()).serviceDate(LocalDate.now())
                .lines(List.of(ClaimLineDto.builder()
                        .pricingItemId(pricingItem50.getId())
                        .serviceCode(pricingItem50.getServiceCode())
                        .serviceCategoryId(limitCategory.getId())
                        .unitPrice(new BigDecimal("50")).quantity(1).build()))
                .status(ClaimStatus.SUBMITTED)
                .build());
        claimReviewService.startReview(claim2.getId());
        claimReviewService.requestApproval(claim2.getId(),
                ClaimApproveDto.builder().useSystemCalculation(true).build());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        entityManager.clear();

        Claim reloadedClaim2 = claimRepository.findById(claim2.getId()).orElseThrow();
        assertThat(reloadedClaim2.getStatus()).isNotEqualTo(ClaimStatus.APPROVED);
    }
}
