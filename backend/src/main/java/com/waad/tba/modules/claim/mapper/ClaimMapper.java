package com.waad.tba.modules.claim.mapper;

import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.dto.engine.*;
import com.waad.tba.modules.claim.entity.*;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.provider.dto.EffectivePriceResponseDto;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.claim.service.CoverageEngineService;
import com.waad.tba.modules.claim.service.CoverageEngineService.BatchUsageAccumulator;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ClaimMapper (CANONICAL REBUILD 2026-04-24)
 * 
 * Maps between Claim entities and DTOs.
 * Enforces architectural laws for financial consistency.
 * 
 * LAW: All financial calculations flow through CoverageEngineService.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ClaimMapper {

        private final ProviderContractService providerContractService;
        private final BenefitPolicyRepository benefitPolicyRepository;
        private final MedicalCategoryRepository medicalCategoryRepository;
        private final ProviderContractPricingItemRepository pricingItemRepository;
        private final ProviderContractRepository providerContractRepository;
        private final ClaimBatchRepository claimBatchRepository;
        private final CoverageEngineService coverageEngineService;

        private static final BigDecimal HUNDRED = new BigDecimal("100.00");
        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private static final BigDecimal EPSILON = new BigDecimal("0.01");

        private com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy resolvePolicy(
                        com.waad.tba.modules.member.entity.Member member) {
                if (member == null)
                        return null;
                var direct = member.getBenefitPolicy();
                if (direct != null)
                        return direct;
                if (member.getEmployer() != null) {
                        return benefitPolicyRepository
                                        .findActiveEffectivePolicyForEmployer(member.getEmployer().getId(),
                                                        LocalDate.now())
                                        .orElse(null);
                }
                return null;
        }

        public Claim toEntity(ClaimCreateDto dto, Visit visit, Provider provider, PreAuthorization preAuth,
                        ClaimBatch claimBatch) {
                Claim claim = Claim.builder()
                                .visit(visit)
                                .member(visit.getMember())
                                .providerId(provider.getId())
                                .providerName(provider.getName())
                                .serviceDate(dto.getServiceDate())
                                .diagnosisCode(dto.getDiagnosisCode())
                                .diagnosisDescription(dto.getDiagnosisDescription())
                                .doctorName(dto.getDoctorName())
                                .status(dto.getStatus() != null ? dto.getStatus() : ClaimStatus.APPROVED)
                                .complaint(dto.getComplaint())
                                .reviewerComment(dto.getRejectionReason())
                                .preAuthorization(preAuth)
                                .claimBatch(claimBatch)
                                .manualCategoryEnabled(
                                                dto.getManualCategoryEnabled() != null ? dto.getManualCategoryEnabled()
                                                                : false)
                                .primaryCategoryCode(dto.getPrimaryCategoryCode())
                                .fullCoverage(dto.getFullCoverage() != null ? dto.getFullCoverage() : false)
                                .isBacklog(visit.getVisitType() == com.waad.tba.modules.visit.entity.VisitType.LEGACY_BACKLOG)
                                .build();

                processEngineCalculations(claim, dto.getLines());
                return claim;
        }

        public void updateEntityFromDto(Claim claim, ClaimUpdateDto dto, PreAuthorization preAuth) {
                claim.setServiceDate(dto.getServiceDate());
                claim.setDiagnosisCode(dto.getDiagnosisCode());
                claim.setDiagnosisDescription(dto.getDiagnosisDescription());
                claim.setDoctorName(dto.getDoctorName());
                claim.setComplaint(dto.getComplaint());
                claim.setReviewerComment(dto.getRejectionReason());
                claim.setManualCategoryEnabled(
                                dto.getManualCategoryEnabled() != null ? dto.getManualCategoryEnabled() : false);
                claim.setPrimaryCategoryCode(dto.getPrimaryCategoryCode());
                claim.setFullCoverage(dto.getFullCoverage() != null ? dto.getFullCoverage() : false);
                claim.setPreAuthorization(preAuth);

                if (dto.getLines() != null) {
                        processEngineCalculations(claim, dto.getLines());
                }
        }

        private void processEngineCalculations(Claim claim, List<ClaimLineDto> lineDtos) {
                Long policyId = resolvePolicy(claim.getMember()) != null ? resolvePolicy(claim.getMember()).getId()
                                : null;
                Map<Long, BatchUsageAccumulator> batchUsageContext = new HashMap<>();

                BulkCoverageEngineRequest engineRequest = BulkCoverageEngineRequest.builder()
                                .policyId(policyId)
                                .memberId(claim.getMember().getId())
                                .serviceYear(claim.getServiceDate() != null ? claim.getServiceDate().getYear()
                                                : LocalDate.now().getYear())
                                .fullCoverage(Boolean.TRUE.equals(claim.getFullCoverage()))
                                .excludeClaimId(claim.getId())
                                .build();

                Long contextCategoryId = null;
                if (claim.getPrimaryCategoryCode() != null) {
                        contextCategoryId = medicalCategoryRepository.findByCode(claim.getPrimaryCategoryCode())
                                        .map(MedicalCategory::getId)
                                        .orElse(null);
                }

                List<ClaimLine> lines = new ArrayList<>();
                BigDecimal totalRequestedAmount = BigDecimal.ZERO;
                BigDecimal contractDiscountPercent = resolveActiveProviderDiscountPercent(claim.getProviderId());
                claim.setAppliedDiscountPercent(contractDiscountPercent);

                for (ClaimLineDto lineDto : lineDtos) {
                        BigDecimal enteredUnitPrice = lineDto.getUnitPrice() != null ? lineDto.getUnitPrice()
                                        : BigDecimal.ZERO;
                        BigDecimal resolvedUnitPrice = null;
                        Long resolvedPricingItemId = lineDto.getPricingItemId();
                        String codeToLookup = lineDto.getServiceCode();

                        if (codeToLookup == null && resolvedPricingItemId != null) {
                                codeToLookup = pricingItemRepository.findById(resolvedPricingItemId)
                                                .map(item -> item.getServiceCode())
                                                .orElse(null);
                        }

                        if ("GEN-MEDICATION".equals(codeToLookup) || "GEN-MEDICAL-SERVICE".equals(codeToLookup)) {
                                resolvedUnitPrice = enteredUnitPrice;
                        } else if (codeToLookup != null) {
                                EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                                                claim.getProviderId(), codeToLookup, claim.getServiceDate());

                                if (priceResponse.isHasContract() && priceResponse.getContractPrice() != null) {
                                        resolvedUnitPrice = priceResponse.getContractPrice();
                                        resolvedPricingItemId = priceResponse.getPricingItemId();
                                }
                        }

                        if (resolvedUnitPrice == null && resolvedPricingItemId != null) {
                                resolvedUnitPrice = pricingItemRepository.findById(resolvedPricingItemId)
                                                .map(item -> item.getContractPrice())
                                                .orElse(enteredUnitPrice);
                        }

                        boolean isFreeTextAllowed = "GEN-MEDICATION".equals(codeToLookup)
                                        || "GEN-MEDICAL-SERVICE".equals(codeToLookup);
                        if (!isFreeTextAllowed && resolvedUnitPrice == null) {
                                // PROVIDER-PORTAL-DATA-1: neither a contract code lookup nor a pricing item
                                // resolved a price — this line has no valid contracted pricing source and
                                // must not silently proceed with a zero/frontend-supplied amount.
                                throw new BusinessRuleException(
                                                "Claim line has no valid contracted pricing source (no resolvable service code or pricing item)",
                                                "تعذر استخدام هذه الخدمة لأن ربطها بسعر العقد غير مكتمل. يرجى مراجعة مسؤول العقود أو اختيار خدمة أخرى.");
                        }

                        // PROVIDER-PORTAL-DATA-1: the requested amount MUST be derived from the
                        // authoritative, backend-resolved contract price — never from a
                        // frontend-supplied unitPrice — so a client can never override the contract.
                        // enteredUnitPrice is only used as the basis when no contract price could be
                        // resolved at all (the free-text GEN-* lines above).
                        BigDecimal amountBasis = resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice;
                        Integer quantity = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
                        BigDecimal lineRequestedTotal = amountBasis.multiply(BigDecimal.valueOf(quantity));

                        Long pricingItemCategoryId = null;
                        if (resolvedPricingItemId != null) {
                                pricingItemCategoryId = pricingItemRepository.findById(resolvedPricingItemId)
                                                .map(item -> item.getMedicalCategory() != null
                                                                ? item.getMedicalCategory().getId()
                                                                : null)
                                                .orElse(null);
                        }

                        Long serviceCatIdForCoverage = pricingItemCategoryId != null ? pricingItemCategoryId
                                        : lineDto.getServiceCategoryId();
                        String serviceCatName = lineDto.getServiceCategoryName();

                        if ("GEN-MEDICATION".equals(codeToLookup) || "GEN-MEDICAL-SERVICE".equals(codeToLookup)) {
                                String targetCode = "CAT-OP-GEN";
                                if ("CAT-IP".equals(claim.getPrimaryCategoryCode())) {
                                        targetCode = "CAT-IP-GEN";
                                } else if ("GEN-MEDICATION".equals(codeToLookup)) {
                                        targetCode = "CAT-OP-DRUG";
                                }
                                var optionalCat = medicalCategoryRepository.findByCode(targetCode);
                                if (optionalCat.isPresent()) {
                                        serviceCatIdForCoverage = optionalCat.get().getId();
                                        serviceCatName = optionalCat.get().getName();
                                }
                        }

                        ClaimLineInput lineInput = ClaimLineInput.builder()
                                        .lineId(String.valueOf(lines.size()))
                                        .serviceId(resolvedPricingItemId)
                                        .categoryId(contextCategoryId)
                                        .serviceCategoryId(serviceCatIdForCoverage)
                                        .enteredUnitPrice(enteredUnitPrice)
                                        .contractPrice(resolvedUnitPrice)
                                        .quantity(quantity)
                                        .manualRefusedAmount(lineDto.getManualRefusedAmount())
                                        .manualRefusalReason(lineDto.getRejectionReason() != null
                                                        && !lineDto.getRejectionReason().isBlank()
                                                                        ? lineDto.getRejectionReason()
                                                                        : lineDto.getManualRefusalReason())
                                        .rejected(Boolean.TRUE.equals(lineDto.getRejected()))
                                        .build();

                        CoverageResult result = coverageEngineService.evaluateLine(engineRequest, lineInput,
                                        batchUsageContext);

                        boolean isRejected = Boolean.TRUE.equals(lineDto.getRejected());
                        BigDecimal manualRefused = lineDto.getManualRefusedAmount() != null
                                        ? lineDto.getManualRefusedAmount()
                                        : BigDecimal.ZERO;

                        int coveragePercent = result.getCoveragePercent() != null ? result.getCoveragePercent() : 0;
                        int normalizedCoverage = Math.min(100, Math.max(0, coveragePercent));
                        BigDecimal patientRate = BigDecimal.valueOf(100 - normalizedCoverage);

                        BigDecimal gross = scale2(lineRequestedTotal);
                        BigDecimal patientShare = scale2(
                                        gross.multiply(patientRate).divide(HUNDRED, 2, RoundingMode.HALF_UP));
                        BigDecimal providerShare = maxZero(scale2(gross.subtract(patientShare)));

                        boolean beforeRejection = claim.getDiscountBeforeRejection() != Boolean.FALSE;
                        BigDecimal rejectedAmount;
                        BigDecimal finalPayable;
                        BigDecimal contractDiscount;

                        BigDecimal systemRejected = maxZero(result.getSystemRefusedAmount());
                        BigDecimal manualRejection = maxZero(manualRefused);
                        // If the line is marked as rejected, the provider share is the candidate for rejection (Patient still pays their co-pay)
                        BigDecimal rejectionCandidate = isRejected ? providerShare : maxZero(scale2(systemRejected.add(manualRejection)));

                        if (beforeRejection) {
                            // MODE: BEFORE (Discount on full provider share, then subtract rejection)
                            contractDiscount = scale2(providerShare.multiply(contractDiscountPercent)
                                    .divide(HUNDRED, 2, RoundingMode.HALF_UP));
                            BigDecimal providerNet = maxZero(scale2(providerShare.subtract(contractDiscount)));
                            rejectedAmount = min(providerNet, rejectionCandidate);
                            finalPayable = maxZero(scale2(providerNet.subtract(rejectedAmount)));
                        } else {
                            // MODE: AFTER (Subtract rejection first, then discount on remainder)
                            BigDecimal candidateToSubtract = min(providerShare, rejectionCandidate);
                            BigDecimal afterRejection = maxZero(scale2(providerShare.subtract(candidateToSubtract)));
                            contractDiscount = scale2(afterRejection.multiply(contractDiscountPercent)
                                    .divide(HUNDRED, 2, RoundingMode.HALF_UP));
                            // CLAIMS-FINANCIAL-INTEGRITY-2: rejectedAmount MUST only ever represent a
                            // real medical/contractual/cap rejection — never the discount. This is
                            // exactly candidateToSubtract, computed BEFORE the discount is applied.
                            rejectedAmount = candidateToSubtract;
                            finalPayable = maxZero(scale2(afterRejection.subtract(contractDiscount)));
                        }

                        // CLAIMS-FINANCIAL-INTEGRITY-2: persist the discount split explicitly so it
                        // can never be folded into (or mistaken for) refusedAmount/companyShare.
                        // providerShare here IS the company's coveragePercent share of the requested
                        // total, before any discount is taken off it.
                        BigDecimal companyShareBeforeDiscount = providerShare;
                        BigDecimal providerDiscountAmount = contractDiscount;

                        CoverageResult.UsageDetails usageDetails = result.getUsageDetails();
                        boolean hasRealCap = usageDetails != null && usageDetails.isHasLimit()
                                        && (usageDetails.getAmountLimit() != null || usageDetails.getTimesLimit() != null);

                        ClaimLine line = ClaimLine.builder()
                                        .claim(claim)
                                        .serviceCode(result.getServiceCode() != null ? result.getServiceCode()
                                                        : (lineDto.getServiceCode() != null ? lineDto.getServiceCode()
                                                                        : "N/A"))
                                        .serviceName(result.getServiceName() != null ? result.getServiceName()
                                                        : (lineDto.getServiceName() != null ? lineDto.getServiceName()
                                                                        : "Unknown Service"))
                                        .pricingItemId(resolvedPricingItemId)
                                        .serviceCategoryId(serviceCatIdForCoverage)
                                        .serviceCategoryName(serviceCatName)
                                        .appliedCategoryId(result.getResolvedCategoryId())
                                        .appliedCategoryName(result.getResolvedCategoryId() != null
                                                        ? medicalCategoryRepository
                                                                        .findById(result.getResolvedCategoryId())
                                                                        .map(MedicalCategory::getName).orElse("N/A")
                                                        : "N/A")
                                        .requiresPA(result.isRequiresPreApproval())
                                        .coveragePercentSnapshot(result.getCoveragePercent())
                                        .patientCopayPercentSnapshot(result.getCoveragePercent() != null
                                                        ? 100 - result.getCoveragePercent()
                                                        : 0)
                                        .manualRefusedAmount(manualRefused)
                                        .manualRefusalReason(lineDto.getRejectionReason() != null
                                                        && !lineDto.getRejectionReason().isBlank()
                                                                        ? lineDto.getRejectionReason()
                                                                        : lineDto.getManualRefusalReason())
                                        .unitPrice(resolvedUnitPrice != null ? resolvedUnitPrice : enteredUnitPrice)
                                        .totalPrice(result.getEffectiveTotal())
                                        .requestedUnitPrice(enteredUnitPrice)
                                        .approvedUnitPrice(result.getEffectiveUnitPrice())
                                        .quantity(quantity)
                                        .requestedTotal(lineRequestedTotal)
                                        .approvedAmount(finalPayable)
                                        .companyShare(finalPayable)
                                        .companyShareBeforeDiscount(companyShareBeforeDiscount)
                                        .providerDiscountAmount(providerDiscountAmount)
                                        .patientShare(patientShare)
                                        .refusedAmount(rejectedAmount) // real rejection only — never includes the discount
                                        .priceExcessRefused(isRejected ? BigDecimal.ZERO
                                                        : maxZero(result.getPriceRefused()))
                                        .limitRefused(isRejected ? BigDecimal.ZERO : maxZero(result.getLimitRefused()))
                                        // CLAIMS-FINANCIAL-INTEGRITY-2: only persist cap snapshots when a real
                                        // cap actually exists (hasRealCap) — never fabricate a cap for display.
                                        .benefitLimit(hasRealCap ? usageDetails.getAmountLimit() : null)
                                        .amountLimitSnapshot(hasRealCap ? usageDetails.getAmountLimit() : null)
                                        .timesLimitSnapshot(hasRealCap ? usageDetails.getTimesLimit() : null)
                                        .usedAmountSnapshot(hasRealCap ? usageDetails.getUsedAmount() : null)
                                        .remainingAmountSnapshot(hasRealCap ? usageDetails.getRemainingAmount() : null)
                                        .rejected(isRejected)
                                        .rejectionReason(lineDto.getRejectionReason() != null
                                                        && !lineDto.getRejectionReason().isBlank()
                                                                        ? lineDto.getRejectionReason()
                                                                        : (isRejected ? "مرفوض كلياً من قبل المراجع"
                                                                                        : result.getRefusalReason()))
                                        .approvedQuantity(finalPayable.compareTo(BigDecimal.ZERO) > 0 ? quantity : 0)
                                        .build();

                        lines.add(line);
                        totalRequestedAmount = totalRequestedAmount.add(lineRequestedTotal);
                }

                if (claim.getLines() != null) {
                        claim.getLines().clear();
                        claim.getLines().addAll(lines);
                } else {
                        claim.setLines(lines);
                }
                claim.setRequestedAmount(totalRequestedAmount);
                calculateClaimTotals(claim);
        }

        private void calculateClaimTotals(Claim claim) {
                List<ClaimLine> lines = claim.getLines();

                // CLAIMS-FINANCIAL-INTEGRITY-2: claim-level totals are now a PURE SUM of each
                // line's own already-computed, authoritative fields — never an independent
                // re-derivation. This guarantees the claim-level and line-level numbers can
                // never diverge (the exact "three divergent financial truths" bug reported).
                BigDecimal totalRequested = sumField(lines, ClaimLine::getRequestedTotal);
                BigDecimal totalRefused = sumField(lines, ClaimLine::getRefusedAmount);
                BigDecimal totalPatientShare = sumField(lines, ClaimLine::getPatientShare);
                BigDecimal totalCompanyShare = sumField(lines, ClaimLine::getCompanyShare);

                claim.setRequestedAmount(totalRequested);
                claim.setRefusedAmount(totalRefused);
                claim.setNetProviderAmount(totalCompanyShare);
                claim.setPatientCoPay(totalPatientShare);

                validateLineBalances(lines);
        }

        private BigDecimal sumField(List<ClaimLine> lines, java.util.function.Function<ClaimLine, BigDecimal> extractor) {
                return lines.stream()
                                .map(l -> {
                                        BigDecimal value = extractor.apply(l);
                                        return value != null ? value : BigDecimal.ZERO;
                                })
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /**
         * Validates the CLAIMS-FINANCIAL-INTEGRITY-2 invariant for each line:
         *
         *   companyShareBeforeDiscount == refusedAmount + providerDiscountAmount + companyShare
         *
         * i.e. the company's coveragePercent share of the requested total (BEFORE
         * discount) must reconcile exactly into: what was refused (real rejection
         * only), what was discounted away (provider contract discount — never a
         * refusal), and what is finally payable. A provider discount must never be
         * folded into refusedAmount, so this check would fail loudly if it were.
         *
         * Tiny rounding drift (<= tolerance) is absorbed into companyShare (the
         * final payable). A material mismatch throws BusinessRuleException instead
         * of silently persisting corrupted numbers.
         */
        private void validateLineBalances(List<ClaimLine> lines) {
                BigDecimal tolerance = new BigDecimal("0.02");
                for (ClaimLine l : lines) {
                        BigDecimal companyBeforeDiscount = l.getCompanyShareBeforeDiscount() != null
                                        ? l.getCompanyShareBeforeDiscount() : BigDecimal.ZERO;
                        BigDecimal discount = l.getProviderDiscountAmount() != null ? l.getProviderDiscountAmount()
                                        : BigDecimal.ZERO;
                        BigDecimal refused = l.getRefusedAmount() != null ? l.getRefusedAmount() : BigDecimal.ZERO;
                        BigDecimal finalPayable = l.getCompanyShare() != null ? l.getCompanyShare() : BigDecimal.ZERO;

                        BigDecimal reconstructed = scale2(refused.add(discount).add(finalPayable));
                        BigDecimal diff = scale2(companyBeforeDiscount.subtract(reconstructed));
                        BigDecimal absDiff = diff.abs();

                        if (absDiff.compareTo(BigDecimal.ZERO) == 0) {
                                continue;
                        }

                        if (absDiff.compareTo(tolerance) <= 0) {
                                // Tiny rounding drift only — absorb into the final payable so the ledger
                                // reconciles exactly, rather than blocking a legitimate save.
                                l.setCompanyShare(scale2(finalPayable.add(diff)));
                                log.debug(
                                        "[MAPPER] Absorbed rounding diff {} into companyShare for line service={}",
                                        diff, l.getServiceCode());
                                continue;
                        }

                        log.error(
                                        "⚠️ [MAPPER] MATERIAL line balance mismatch: service={}, companyShareBeforeDiscount={}, refused={}, discount={}, companyShare={}, diff={}",
                                        l.getServiceCode(), companyBeforeDiscount, refused, discount, finalPayable, diff);
                        throw new BusinessRuleException(
                                        String.format(
                                                        "Line balance mismatch for service %s: companyShareBeforeDiscount=%s but refused(%s)+discount(%s)+companyShare(%s)=%s (diff=%s)",
                                                        l.getServiceCode(), companyBeforeDiscount, refused, discount,
                                                        finalPayable, reconstructed, diff),
                                        "خطأ في اتساق البيانات المالية لبند الخدمة \"" + l.getServiceName()
                                                        + "\": حصة الشركة قبل الخصم لا تساوي مجموع (المرفوض + خصم العقد + المستحق النهائي). يرجى مراجعة الحساب قبل الحفظ.");
                }
        }

        private BigDecimal resolveActiveProviderDiscountPercent(Long providerId) {
                if (providerId == null) {
                        return ZERO;
                }
                BigDecimal discount = providerContractRepository.findActiveContractByProvider(providerId)
                                .map(c -> c.getDiscountPercent() != null ? c.getDiscountPercent() : BigDecimal.ZERO)
                                .orElse(BigDecimal.ZERO);
                if (discount.compareTo(BigDecimal.ZERO) < 0 || discount.compareTo(HUNDRED) > 0) {
                        throw new IllegalStateException("Invalid provider discount percent for provider " + providerId
                                        + ": " + discount);
                }
                return scale2(discount);
        }

        private BigDecimal scale2(BigDecimal value) {
                return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        }

        private BigDecimal maxZero(BigDecimal value) {
                BigDecimal scaled = scale2(value);
                return scaled.compareTo(BigDecimal.ZERO) < 0 ? ZERO : scaled;
        }

        private BigDecimal min(BigDecimal a, BigDecimal b) {
                if (a == null)
                        return scale2(b);
                if (b == null)
                        return scale2(a);
                return scale2(a.min(b));
        }

        public void replaceClaimLinesForDraft(Claim claim, List<ClaimLineDto> lineDtos) {
                processEngineCalculations(claim, lineDtos);
        }

        public ClaimViewDto toViewDto(Claim claim) {
                if (claim == null)
                        return null;
                var member = claim.getMember();
                var employer = (member != null) ? member.getEmployer() : null;

                String primaryCategoryName = null;
                if (claim.getPrimaryCategoryCode() != null) {
                        primaryCategoryName = medicalCategoryRepository.findByCode(claim.getPrimaryCategoryCode())
                                        .map(MedicalCategory::getName)
                                        .orElse(null);
                }

                if (Boolean.TRUE.equals(claim.getFullCoverage())) {
                        primaryCategoryName = (primaryCategoryName != null)
                                        ? primaryCategoryName + " (تغطية كاملة)"
                                        : "تغطية كاملة";
                }

                BigDecimal appliedDiscount = claim.getAppliedDiscountPercent();
                if (appliedDiscount == null) {
                        appliedDiscount = resolveActiveProviderDiscountPercent(claim.getProviderId());
                }

                // CLAIMS-FINANCIAL-INTEGRITY-2: sum the authoritative per-line discount
                // amounts instead of re-approximating from claim-level totals — this was
                // the exact source of the claim-level/line-level/review-UI divergence
                // reported (three different numbers for the same claim). Legacy lines
                // saved before this fix have providerDiscountAmount == null and are
                // treated as 0 here (not backfilled/fabricated).
                BigDecimal summedProviderDiscount = null;
                if (claim.getLines() != null && !claim.getLines().isEmpty()) {
                        summedProviderDiscount = claim.getLines().stream()
                                        .map(l -> l.getProviderDiscountAmount() != null ? l.getProviderDiscountAmount()
                                                        : BigDecimal.ZERO)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                }

                return ClaimViewDto.builder()
                                .id(claim.getId())
                                .claimNumber(claim.getClaimNumber() != null ? claim.getClaimNumber()
                                                : "CLM-" + claim.getId())
                                .memberId(member != null ? member.getId() : null)
                                .memberFullName(member != null ? member.getFullName() : null)
                                .memberName(member != null ? member.getFullName() : null)
                                .memberNationalNumber(member != null ? member.getNationalNumber() : null)
                                .employerId(employer != null ? employer.getId() : null)
                                .employerName(employer != null ? employer.getName() : null)
                                .providerId(claim.getProviderId())
                                .providerName(claim.getProviderName())
                                .doctorName(claim.getDoctorName())
                                .serviceDate(claim.getServiceDate())
                                .status(claim.getStatus())
                                .requestedAmount(claim.getRequestedAmount())
                                .totalAmount(claim.getRequestedAmount())
                                .approvedAmount(claim.getApprovedAmount())
                                .refusedAmount(claim.getRefusedAmount())
                                .providerDiscountPercent(appliedDiscount)
                                // CLAIMS-FINANCIAL-INTEGRITY-2: authoritative sum of each line's own
                                // providerDiscountAmount (never a claim-level re-approximation).
                                .providerDiscountAmount(summedProviderDiscount != null ? summedProviderDiscount
                                                : BigDecimal.ZERO)
                                .patientCoPay(claim.getPatientCoPay())
                                .netProviderAmount(claim.getNetProviderAmount())
                                .discountBeforeRejection(claim.getDiscountBeforeRejection())
                                .diagnosisCode(claim.getDiagnosisCode())
                                .diagnosisDescription(claim.getDiagnosisDescription())
                                .complaint(claim.getComplaint())
                                .reviewerComment(claim.getReviewerComment())
                                .manualCategoryEnabled(claim.getManualCategoryEnabled())
                                .primaryCategoryCode(claim.getPrimaryCategoryCode())
                                .primaryCategoryName(primaryCategoryName)
                                .fullCoverage(claim.getFullCoverage())
                                .claimBatchId(claim.getClaimBatch() != null ? claim.getClaimBatch().getId() : null)
                                .claimBatchCode(claim.getClaimBatch() != null ? claim.getClaimBatch().getBatchCode()
                                                : null)
                                .lines(claim.getLines().stream().map(this::toLineDto).collect(Collectors.toList()))
                                .active(claim.getActive())
                                .createdAt(claim.getCreatedAt())
                                .updatedAt(claim.getUpdatedAt())
                                .createdBy(claim.getCreatedBy())
                                .updatedBy(claim.getUpdatedBy())
                                .submissionChannel(claim.getSubmissionChannel() != null ? claim.getSubmissionChannel().name() : null)
                                .submittedBy(claim.getSubmittedBy())
                                .reviewedBy(claim.getReviewedBy())
                                .deletedAt(claim.getDeletedAt())
                                .deletedBy(claim.getDeletedBy())
                                .voidReason(claim.getVoidReason())
                                .build();
        }

        public ClaimLineDto toLineDto(ClaimLine line) {
                return ClaimLineDto.builder()
                                .id(line.getId())
                                .pricingItemId(line.getPricingItemId())
                                .serviceCode(line.getServiceCode())
                                .serviceName(line.getServiceName())
                                .serviceCategoryId(line.getServiceCategoryId())
                                .serviceCategoryName(line.getServiceCategoryName())
                                .appliedCategoryId(line.getAppliedCategoryId())
                                .appliedCategoryName(line.getAppliedCategoryName())
                                .unitPrice(line.getUnitPrice())
                                .totalPrice(line.getTotalPrice())
                                .requestedUnitPrice(line.getRequestedUnitPrice())
                                .approvedUnitPrice(line.getApprovedUnitPrice())
                                .requestedQuantity(line.getRequestedQuantity())
                                .approvedQuantity(line.getApprovedQuantity())
                                .requestedTotal(line.getRequestedTotal())
                                .approvedAmount(line.getApprovedAmount())
                                .refusedAmount(line.getRefusedAmount())
                                .rejected(Boolean.TRUE.equals(line.getRejected()))
                                .rejectionReason(line.getRejectionReason())
                                .rejectionReasonCode(line.getRejectionReasonCode())
                                .reviewerNotes(line.getReviewerNotes())
                                .reviewerDecision(line.getReviewerDecision())
                                .manualRefusedAmount(line.getManualRefusedAmount())
                                .manualRefusalReason(line.getManualRefusalReason())
                                .coveragePercent(line.getCoveragePercentSnapshot())
                                .patientSharePercent(line.getPatientCopayPercentSnapshot())
                                .benefitLimit(line.getBenefitLimit())
                                .usedAmount(line.getUsedAmountSnapshot())
                                .remainingAmount(line.getRemainingAmountSnapshot())
                                .companyShare(line.getCompanyShare())
                                .patientShare(line.getPatientShare())
                                .companyShareBeforeDiscount(line.getCompanyShareBeforeDiscount())
                                .providerDiscountAmount(line.getProviderDiscountAmount())
                                .priceExcessRefused(line.getPriceExcessRefused())
                                .limitRefused(line.getLimitRefused())
                                .requiresPA(line.getRequiresPA())
                                .build();
        }
}
