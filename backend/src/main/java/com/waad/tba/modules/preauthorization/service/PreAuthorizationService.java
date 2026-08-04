package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.modules.preauthorization.dto.*;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.Priority;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineReviewDecision;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.service.ArchitecturalGuardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for PreAuthorization business logic
 * Integrates with ProviderContract for price lookup and validation
 * 
 * ARCHITECTURAL RULE (2026-01-14):
 * Pre-authorizations MUST be created via Visit (Visit-Centric Architecture).
 * visitId is REQUIRED on all create operations.
 * 
 * SECURITY HARDENING (2026-01-16):
 * Provider data isolation enforced via ProviderContextGuard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreAuthorizationService {

    private final EntityManager entityManager;

    private final PreAuthorizationRepository preAuthorizationRepository;
    private final ProviderRepository providerRepository;
    private final MemberRepository memberRepository;
    private final com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository pricingItemRepository;
    private final VisitRepository visitRepository;
    private final ProviderContractService providerContractService;
    private final PreAuthorizationAuditService auditService;
    private final AuthorizationService authorizationService;
    private final ProviderContextGuard providerContextGuard;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;
    private final ArchitecturalGuardService architecturalGuard;
    private final com.waad.tba.modules.claim.service.ReviewerProviderIsolationService reviewerIsolationService;

    // ==================== CREATE ====================

    /**
     * Create a new pre-authorization with contract price lookup (CANONICAL REBUILD
     * 2026-01-16)
     * 
     * ARCHITECTURAL LAWS:
     * 1. Pre-authorization MUST be linked to an existing Visit
     * 2. Medical Service MUST be selected from Provider Contract (no free-text)
     * 3. Price is AUTO-RESOLVED from Provider Contract (no manual entry)
     * 4. Provider ID is validated for PROVIDER users (must match their session)
     * 
     * Data Flow: Visit → MedicalService (from Contract) → ContractPrice (auto)
     */
    @Transactional
    public PreAuthorizationResponseDto createPreAuthorization(PreAuthorizationCreateDto dto, String createdBy) {
        log.info("[PRE-AUTH] Creating pre-authorization: visitId={}, pricingItemId={}",
                dto.getVisitId(), dto.getPricingItemId());

        // ═══════════════════════════════════════════════════════════════════════════
        // PROVIDER PORTAL: Validate and enforce provider ID from JWT
        // ═══════════════════════════════════════════════════════════════════════════
        User currentUser = authorizationService.getCurrentUser();
        validateAndEnforceProviderId(dto, currentUser);

        // ═══════════════════════════════════════════════════════════════════════════
        // WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): normalize the request into a
        // uniform list of line requests. When dto.getLines() is absent/empty
        // (every request from today's frontend, and every legacy API
        // caller), this wraps the flat pricingItemId/serviceCategoryId
        // fields into a single-element list — the per-line resolution loop
        // below then runs identically to the old single-service code path,
        // guaranteeing byte-identical results for that case.
        // ═══════════════════════════════════════════════════════════════════════════
        boolean isMultiLineRequest = dto.getLines() != null && !dto.getLines().isEmpty();
        List<PreAuthorizationLineDto> lineDtos = isMultiLineRequest
                ? dto.getLines()
                : List.of(PreAuthorizationLineDto.builder()
                        .pricingItemId(dto.getPricingItemId())
                        .serviceCategoryId(dto.getServiceCategoryId())
                        .serviceCategoryName(dto.getServiceCategoryName())
                        .build());

        // ═══════════════════════════════════════════════════════════════════════════
        // ARCHITECTURAL GUARD: Validate system invariants before processing
        // The legacy single-ID overload is called unchanged for the legacy
        // path so its validation behavior/error messages stay byte-for-byte
        // identical to before this feature existed.
        // ═══════════════════════════════════════════════════════════════════════════
        if (isMultiLineRequest) {
            List<Long> pricingItemIds = lineDtos.stream().map(PreAuthorizationLineDto::getPricingItemId).toList();
            architecturalGuard.guardPreAuthCreation(dto.getVisitId(), pricingItemIds);
        } else {
            architecturalGuard.guardPreAuthCreation(dto.getVisitId(), dto.getPricingItemId());
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 1: Validate Visit exists (ARCHITECTURAL LAW: Visit-Centric)
        // ═══════════════════════════════════════════════════════════════════════════
        Visit visit = visitRepository.findById(dto.getVisitId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ARCHITECTURAL VIOLATION: Visit not found with ID: " + dto.getVisitId() +
                                ". Pre-authorization MUST be created from an existing Visit."));

        if (visit.getStatus() == com.waad.tba.modules.visit.entity.VisitStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot create pre-authorization for a cancelled visit");
        }

        // Get member from visit
        Member member = visit.getMember();
        if (member == null) {
            throw new IllegalArgumentException("Visit has no associated member");
        }

        if (!member.getActive()) {
            throw new IllegalArgumentException("Member is not active");
        }

        log.info("[PRE-AUTH] Visit {} validated. Member: {}", dto.getVisitId(), member.getId());

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 2: Validate Provider
        // ═══════════════════════════════════════════════════════════════════════════
        Provider provider = providerRepository.findById(dto.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with ID: " + dto.getProviderId()));

        if (!provider.getActive()) {
            throw new IllegalArgumentException("Provider is not active");
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 3 & 4: Resolve each line's service/pricing info from
        // ProviderContractPricingItem, exactly like the old single-service
        // logic, just run once per line. For the legacy 1-element list this
        // produces identical results to before.
        // ═══════════════════════════════════════════════════════════════════════════
        LocalDate requestDate = dto.getRequestDate() != null ? dto.getRequestDate() : LocalDate.now();
        List<PreAuthorizationLine> lines = new ArrayList<>();
        // WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): category-limit accumulator —
        // same pattern as CoverageEngineService.BatchUsageAccumulator for
        // multi-line Claims. Queries the DB-used amount once per distinct
        // category (cached), then accumulates in-memory across the lines in
        // THIS request, so sibling lines sharing a category can't jointly
        // exceed that category's amountLimit even if each individually
        // would pass on its own.
        Map<Long, BigDecimal> categoryUsedCache = new HashMap<>();
        Map<Long, BigDecimal> categoryAccumulated = new HashMap<>();
        int lineNumber = 0;
        for (PreAuthorizationLineDto lineDto : lineDtos) {
            lineNumber++;
            com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem pricingItem = pricingItemRepository
                    .findById(lineDto.getPricingItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ARCHITECTURAL VIOLATION: Pricing Item not found with ID: " + lineDto.getPricingItemId() +
                                    ". Service MUST be selected from Provider Contract."));

            String lineServiceCode = pricingItem.getServiceCode();
            String lineServiceName = pricingItem.getServiceName();
            Long pricingCategoryId = pricingItem.getMedicalCategory() != null
                    ? pricingItem.getMedicalCategory().getId() : null;

            log.info("[PRE-AUTH] Line {}: pricing item validated: {} ({})", lineNumber, lineServiceCode, lineServiceName);

            BigDecimal lineContractPrice = pricingItem.getContractPrice();
            if (lineContractPrice == null) {
                throw new IllegalArgumentException(
                        "ARCHITECTURAL VIOLATION: Pricing item '" + lineServiceCode + "' has no contract price.");
            }

            // CANONICAL: Category resolution - prefer line DTO, fallback to pricingItem.category
            Long lineCategoryId = lineDto.getServiceCategoryId() != null ? lineDto.getServiceCategoryId()
                    : pricingCategoryId;
            String lineCategoryName = lineDto.getServiceCategoryName();
            String lineServiceType = lineCategoryId != null ? "CATEGORY_" + lineCategoryId : "MEDICAL";

            // FINANCIAL SNAPSHOT: coverage percentage at creation time (IMMUTABLE)
            var coverageInfoOpt = benefitPolicyCoverageService.getCoverageForCategory(member, lineCategoryId);
            Integer lineCoveragePercentSnapshot = coverageInfoOpt.map(c -> c.getCoveragePercent()).orElse(null);
            Integer linePatientCopayPercentSnapshot = lineCoveragePercentSnapshot != null
                    ? (100 - lineCoveragePercentSnapshot) : null;
            log.info("[PRE-AUTH] Line {} coverage snapshot: coverage={}%, copay={}%", lineNumber,
                    lineCoveragePercentSnapshot, linePatientCopayPercentSnapshot);

            if (lineCategoryId != null && coverageInfoOpt.isPresent() && coverageInfoOpt.get().getAmountLimit() != null) {
                BigDecimal amountLimit = coverageInfoOpt.get().getAmountLimit();
                BigDecimal dbUsed = categoryUsedCache.computeIfAbsent(lineCategoryId,
                        catId -> benefitPolicyCoverageService.calculateCategoryUsedAmount(
                                member.getId(), catId, requestDate, null));
                BigDecimal alreadyAccumulated = categoryAccumulated.getOrDefault(lineCategoryId, BigDecimal.ZERO);
                BigDecimal projectedTotal = dbUsed.add(alreadyAccumulated).add(lineContractPrice);
                if (projectedTotal.compareTo(amountLimit) > 0) {
                    throw new BusinessRuleException(
                            "تجاوز الحد المسموح للفئة الطبية: المستخدم حاليًا " + dbUsed.add(alreadyAccumulated)
                                    + " والمطلوب لهذه الخدمة " + lineContractPrice + " يتجاوز الحد " + amountLimit);
                }
                categoryAccumulated.put(lineCategoryId, alreadyAccumulated.add(lineContractPrice));
            }

            lines.add(PreAuthorizationLine.builder()
                    .lineNumber(lineNumber)
                    .pricingItemId(pricingItem.getId())
                    .serviceCode(lineServiceCode)
                    .serviceName(lineServiceName)
                    .serviceType(lineServiceType)
                    .serviceCategoryId(lineCategoryId)
                    .serviceCategoryName(lineCategoryName)
                    .contractPrice(lineContractPrice)
                    .requiresPA(true)
                    .coveragePercentSnapshot(lineCoveragePercentSnapshot)
                    .patientCopayPercentSnapshot(linePatientCopayPercentSnapshot)
                    .build());
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 5: Build PreAuthorization Entity. Header per-service columns
        // are populated from line 0 ("line 0" backward-compat cache); header
        // contractPrice is the SUM across all lines (== line 0's price for
        // the legacy 1-line case, so no observable change there).
        // ═══════════════════════════════════════════════════════════════════════════
        PreAuthorizationLine firstLine = lines.get(0);
        BigDecimal totalContractPrice = lines.stream()
                .map(PreAuthorizationLine::getContractPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String referenceNumber = generateUniqueReferenceNumber();
        LocalDate expiryDate = requestDate.plusDays(dto.getExpiryDays() != null ? dto.getExpiryDays() : 30);

        Priority priority = Priority.NORMAL;
        if (dto.getPriority() != null) {
            try {
                priority = Priority.valueOf(dto.getPriority().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[PRE-AUTH] Invalid priority: {}, using NORMAL", dto.getPriority());
            }
        }

        PreAuthorization preAuth = PreAuthorization.builder()
                .preAuthNumber(referenceNumber) // Legacy column (required by database)
                .referenceNumber(referenceNumber)
                .memberId(member.getId())
                .providerId(dto.getProviderId())
                .visit(visit) // FK to Visit
                .serviceCode(firstLine.getServiceCode()) // "line 0" cache
                .serviceName(firstLine.getServiceName())
                .serviceType(firstLine.getServiceType())
                .serviceCategoryId(firstLine.getServiceCategoryId())
                .serviceCategoryName(firstLine.getServiceCategoryName())
                .pricingItemId(firstLine.getPricingItemId()) // WAAD-PREAUTH-CLAIM-SERVICE-LINK-1
                .requestDate(requestDate)
                .expectedServiceDate(requestDate)
                .expiryDate(expiryDate)
                .contractPrice(totalContractPrice) // SUM across lines
                .requiresPA(true)
                .coveragePercentSnapshot(firstLine.getCoveragePercentSnapshot())
                .patientCopayPercentSnapshot(firstLine.getPatientCopayPercentSnapshot())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "LYD")
                .status(PreAuthStatus.PENDING)
                .priority(priority)
                .diagnosisCode(dto.getDiagnosisCode() != null ? dto.getDiagnosisCode() : "Z00.0")
                .diagnosisDescription(dto.getDiagnosisDescription())
                .notes(dto.getNotes())
                .active(true)
                .createdBy(createdBy)
                .build();

        for (PreAuthorizationLine line : lines) {
            preAuth.addLine(line);
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 6: Save and Return
        // ═══════════════════════════════════════════════════════════════════════════
        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Created pre-authorization: id={}, ref={}, lines={}, totalContractPrice={}",
                preAuth.getId(), preAuth.getReferenceNumber(), lines.size(), totalContractPrice);

        // Log audit trail
        auditService.logCreate(preAuth.getId(), preAuth.getReferenceNumber(), createdBy,
                "Created with contract price: " + totalContractPrice + " LYD"
                        + (isMultiLineRequest ? " (" + lines.size() + " lines)" : ""));

        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== UPDATE ====================

    /**
     * Update pre-authorization (only if PENDING)
     */
    @Transactional
    public PreAuthorizationResponseDto updatePreAuthorization(Long id, PreAuthorizationUpdateDto dto,
            String updatedBy) {
        log.info("[PRE-AUTH] Updating pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        if (preAuth.getStatus() != PreAuthStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING pre-authorizations can be updated");
        }

        // Capture old state for audit
        String oldDiagnosisCode = preAuth.getDiagnosisCode();
        String oldDiagnosisDescription = preAuth.getDiagnosisDescription();
        String oldNotes = preAuth.getNotes();

        // Update allowed fields (price CANNOT be changed - canonical law)
        if (dto.getPriority() != null) {
            try {
                preAuth.setPriority(Priority.valueOf(dto.getPriority().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("[PRE-AUTH] Invalid priority: {}", dto.getPriority());
            }
        }
        if (dto.getDiagnosisCode() != null) {
            preAuth.setDiagnosisCode(dto.getDiagnosisCode());
        }
        if (dto.getDiagnosisDescription() != null) {
            preAuth.setDiagnosisDescription(dto.getDiagnosisDescription());
        }
        if (dto.getNotes() != null) {
            preAuth.setNotes(dto.getNotes());
        }
        if (dto.getExpiryDays() != null) {
            preAuth.setExpiryDate(preAuth.getRequestDate().plusDays(dto.getExpiryDays()));
        }

        preAuth.setUpdatedBy(updatedBy);

        // Audit logging
        if (dto.getDiagnosisCode() != null && !dto.getDiagnosisCode().equals(oldDiagnosisCode)) {
            auditService.logUpdate(id, preAuth.getReferenceNumber(), updatedBy,
                    "diagnosisCode", oldDiagnosisCode, dto.getDiagnosisCode());
        }
        if (dto.getDiagnosisDescription() != null && !dto.getDiagnosisDescription().equals(oldDiagnosisDescription)) {
            auditService.logUpdate(id, preAuth.getReferenceNumber(), updatedBy,
                    "diagnosisDescription", oldDiagnosisDescription, dto.getDiagnosisDescription());
        }

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Updated pre-authorization {}", id);

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    /**
     * Update pre-authorization DATA only (for PROVIDER and EMPLOYER_ADMIN).
     * SECURITY: Only allowed in PENDING and NEEDS_CORRECTION statuses.
     * 
     * @param id        PreAuth ID
     * @param dto       Data update DTO (no status/financial fields)
     * @param updatedBy Username
     * @return Updated pre-authorization
     * @since Provider Portal Security Fix (Phase 3)
     */
    @Transactional
    public PreAuthorizationResponseDto updatePreAuthData(Long id, PreAuthDataUpdateDto dto, String updatedBy) {
        log.info("[PRE-AUTH] Updating pre-authorization DATA: id={}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        // SECURITY: Verify status allows editing
        if (!preAuth.allowsEdit()) {
            throw new BusinessRuleException(
                    String.format(
                            "Cannot edit pre-authorization in %s status. Only PENDING and NEEDS_CORRECTION allow edits.",
                            preAuth.getStatus()));
        }

        // Update data fields only
        if (dto.getExpectedServiceDate() != null) {
            preAuth.setExpectedServiceDate(dto.getExpectedServiceDate());
        }
        // clinicalJustification doesn't exist in PreAuthorization - skip
        if (dto.getNotes() != null) {
            preAuth.setNotes(dto.getNotes());
        }
        if (dto.getPriority() != null) {
            try {
                preAuth.setPriority(Priority.valueOf(dto.getPriority().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("[PRE-AUTH] Invalid priority: {}", dto.getPriority());
            }
        }

        preAuth.setUpdatedBy(updatedBy);
        preAuth = preAuthorizationRepository.save(preAuth);

        // Audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), updatedBy, "data", "updated", "data_updated");

        log.info("✅ [PRE-AUTH] Data updated: id={}", id);

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    /**
     * Review pre-authorization (for REVIEWER and INSURANCE_ADMIN only).
     * SECURITY: Reviewers can ONLY change status/comment/approvedAmount.
     * 
     * @param id         PreAuth ID
     * @param dto        Review DTO (status, comment, approvedAmount only)
     * @param reviewedBy Username
     * @return Updated pre-authorization
     * @since Provider Portal Security Fix (Phase 3)
     */
    @Transactional
    public PreAuthorizationResponseDto reviewPreAuth(Long id, PreAuthReviewDto dto, String reviewedBy) {
        log.info("[PRE-AUTH] Reviewing pre-authorization: id={}, newStatus={}", id, dto.getStatus());

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        // Cannot review a cancelled pre-authorization
        if (preAuth.getStatus() == PreAuthStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot review a cancelled pre-authorization. ID: " + id);
        }
        User currentUser = authorizationService.getCurrentUser();
        if (!authorizationService.isReviewer(currentUser) &&
                !authorizationService.isInsuranceAdmin(currentUser) &&
                !authorizationService.isSuperAdmin(currentUser)) {
            throw new AccessDeniedException("Only reviewers can perform review actions");
        }

        // SECURITY: Apply reviewer-provider isolation
        if (authorizationService.isReviewer(currentUser)) {
            reviewerIsolationService.validateReviewerAccess(currentUser, preAuth.getProviderId());
        }

        ensureDecisionReviewable(preAuth);
        if (dto.getStatus() == PreAuthStatus.APPROVED || dto.getStatus() == PreAuthStatus.REJECTED) {
            ensureSingleLineForLegacyDecision(preAuth);
        }

        PreAuthStatus previousStatus = preAuth.getStatus();

        // Validate status transition
        if (dto.getStatus() != previousStatus) {
            // Validation for specific statuses
            if (dto.getStatus() == PreAuthStatus.REJECTED || dto.getStatus() == PreAuthStatus.NEEDS_CORRECTION) {
                if (dto.getReviewerComment() == null || dto.getReviewerComment().isBlank()) {
                    throw new BusinessRuleException(
                            dto.getStatus() + " status requires a reviewer comment");
                }
            }

            if (dto.getStatus() == PreAuthStatus.APPROVED) {
                // Pre-authorizations no longer have strict financial matters
                // Default to contract price if not provided, or zero
                BigDecimal approvedAmt = dto.getApprovedAmount() != null ? dto.getApprovedAmount()
                        : preAuth.getContractPrice();
                preAuth.setApprovedAmount(approvedAmt != null ? approvedAmt : BigDecimal.ZERO);

                if (dto.getCopayPercentage() != null) {
                    preAuth.setCopayPercentage(dto.getCopayPercentage());
                }
                syncSingleLineDecision(preAuth, LineReviewDecision.APPROVED, preAuth.getApprovedAmount(),
                        preAuth.getCopayAmount(), preAuth.getCopayPercentage(), null);
            } else if (dto.getStatus() == PreAuthStatus.REJECTED) {
                syncSingleLineDecision(preAuth, LineReviewDecision.REJECTED, null, null, null,
                        dto.getReviewerComment());
            }

            // Set rejection reason (using existing field)
            if (dto.getReviewerComment() != null) {
                if (dto.getStatus() == PreAuthStatus.REJECTED) {
                    preAuth.setRejectionReason(dto.getReviewerComment());
                } else if (dto.getStatus() == PreAuthStatus.NEEDS_CORRECTION) {
                    // Store in notes for NEEDS_CORRECTION
                    preAuth.setNotes(dto.getReviewerComment());
                }
            }

            // Perform status transition
            preAuth.setStatus(dto.getStatus());
        }

        preAuth.setUpdatedBy(reviewedBy);
        if (dto.getStatus() == PreAuthStatus.APPROVED) {
            preAuth.setApprovedBy(reviewedBy);
            preAuth.setApprovedAt(LocalDateTime.now());
        }
        preAuth = preAuthorizationRepository.save(preAuth);

        // Audit trail
        if (dto.getStatus() == PreAuthStatus.APPROVED) {
            auditService.logApprove(id, preAuth.getReferenceNumber(), reviewedBy,
                    dto.getReviewerComment() != null ? dto.getReviewerComment() : "Approved");
        } else if (dto.getStatus() == PreAuthStatus.REJECTED) {
            auditService.logReject(id, preAuth.getReferenceNumber(), reviewedBy,
                    dto.getReviewerComment() != null ? dto.getReviewerComment() : "Rejected");
        } else {
            // For NEEDS_CORRECTION and other status changes
            auditService.logUpdate(id, preAuth.getReferenceNumber(), reviewedBy,
                    previousStatus.name(), dto.getStatus().name(),
                    dto.getReviewerComment() != null ? dto.getReviewerComment() : "Status changed");
        }

        log.info("✅ [PRE-AUTH] Reviewed: id={}, status={}", id, preAuth.getStatus());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    /**
     * Submit pre-authorization for review.
     * Transitions from PENDING or NEEDS_CORRECTION to UNDER_REVIEW.
     * 
     * @param id          PreAuth ID
     * @param submittedBy Username
     * @return Updated pre-authorization
     * @since Provider Portal Draft-First Model (Phase 3)
     */
    @Transactional
    public PreAuthorizationResponseDto submitPreAuth(Long id, String submittedBy) {
        log.info("[PRE-AUTH] Submitting pre-authorization: id={}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        // Validate current status allows submission
        if (preAuth.getStatus() != PreAuthStatus.PENDING && preAuth.getStatus() != PreAuthStatus.NEEDS_CORRECTION) {
            throw new BusinessRuleException(
                    String.format(
                            "Cannot submit pre-authorization in %s status. Only PENDING and NEEDS_CORRECTION can be submitted.",
                            preAuth.getStatus()));
        }

        PreAuthStatus previousStatus = preAuth.getStatus();

        // Transition to UNDER_REVIEW
        preAuth.setStatus(PreAuthStatus.UNDER_REVIEW);
        preAuth.setUpdatedBy(submittedBy);
        preAuth = preAuthorizationRepository.save(preAuth);

        // Audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), submittedBy,
                previousStatus.name(), PreAuthStatus.UNDER_REVIEW.name(), "Pre-authorization submitted for review");

        log.info("✅ [PRE-AUTH] Submitted: id={}, status={}", id, preAuth.getStatus());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== APPROVE ====================

    /**
     * Approve pre-authorization with copay calculation
     */
    @Transactional
    public PreAuthorizationResponseDto approvePreAuthorization(Long id, PreAuthorizationApproveDto dto,
            String approvedBy) {
        log.info("[PRE-AUTH] Approving pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        assertReviewerAccess(preAuth);
        ensureDecisionReviewable(preAuth);
        ensureSingleLineForLegacyDecision(preAuth);

        if (!preAuth.canBeApproved()) {
            throw new IllegalStateException(
                    "PreAuthorization cannot be approved in current status: " + preAuth.getStatus());
        }

        BigDecimal approvedAmount = resolveApprovedAmount(preAuth, dto);
        BigDecimal copayPercentage = resolveCopayPercentage(preAuth, dto);

        // Contract price is an immutable financial ceiling.
        if (preAuth.getContractPrice() != null && approvedAmount.compareTo(preAuth.getContractPrice()) > 0) {
            throw new BusinessRuleException("المبلغ المعتمد لا يمكن أن يتجاوز سعر العقد");
        }

        // Calculate copay
        BigDecimal copayAmount = preAuth.calculateCopay(approvedAmount, copayPercentage);

        // Approve
        preAuth.approve(approvedAmount, copayAmount, approvedBy);
        preAuth.setCopayPercentage(copayPercentage);
        syncSingleLineDecision(preAuth, LineReviewDecision.APPROVED, approvedAmount, copayAmount, copayPercentage, null);

        if (dto.getApprovalNotes() != null) {
            preAuth.setNotes((preAuth.getNotes() != null ? preAuth.getNotes() + "\n" : "") +
                    "Approval Notes: " + dto.getApprovalNotes());
        }

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Approved pre-authorization {} with amount {} and copay {}",
                id, approvedAmount, copayAmount);

        // Note: visit status is NOT auto-set to COMPLETED here.
        // A visit may have multiple pre-authorizations and claims.
        // Visit completion is managed by the provider or when all linked items are
        // settled.
        log.debug("[PRE-AUTH] Pre-auth {} approved; visit status unchanged (may have other pending items)", id);

        // Log audit trail
        auditService.logApprove(id, preAuth.getReferenceNumber(), approvedBy,
                "Approved amount: " + approvedAmount +
                        ", Copay: " + copayPercentage + "%");

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    /** Reviewer-authorized reduction; contract price and coverage snapshots remain immutable. */
    @Transactional
    public PreAuthorizationResponseDto approvePartial(Long id, BigDecimal approvedAmount, String reason,
            String approvedBy) {
        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));
        assertReviewerAccess(preAuth);
        ensureDecisionReviewable(preAuth);
        ensureSingleLineForLegacyDecision(preAuth);
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب الموافقة الجزئية مطلوب");
        }
        if (preAuth.getContractPrice() == null) {
            throw new BusinessRuleException("لا يمكن الموافقة الجزئية دون سعر عقد");
        }
        if (!preAuth.canBeApproved()) {
            throw new BusinessRuleException("لا يمكن اعتماد الطلب جزئيًا في حالته الحالية");
        }
        if (approvedAmount == null || approvedAmount.signum() <= 0) {
            throw new BusinessRuleException("المبلغ المعتمد يجب أن يكون أكبر من صفر");
        }
        if (preAuth.getContractPrice() == null || approvedAmount.compareTo(preAuth.getContractPrice()) >= 0) {
            throw new BusinessRuleException("الموافقة الجزئية يجب أن تكون أقل من سعر العقد");
        }
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        if (member != null && member.getBenefitPolicy() != null) {
            benefitPolicyCoverageService.validateAmountLimits(member, member.getBenefitPolicy(), approvedAmount,
                    preAuth.getRequestDate() != null ? preAuth.getRequestDate() : LocalDate.now());
        }
        BigDecimal copayPercentage = resolveCopayPercentage(preAuth, null);
        BigDecimal copayAmount = preAuth.calculateCopay(approvedAmount, copayPercentage);
        preAuth.approve(approvedAmount, copayAmount, approvedBy);
        preAuth.setCopayPercentage(copayPercentage);
        syncSingleLineDecision(preAuth, LineReviewDecision.APPROVED, approvedAmount, copayAmount, copayPercentage, null);
        preAuth.setNotes((preAuth.getNotes() == null ? "" : preAuth.getNotes() + "\n")
                + "Partial approval: " + reason);
        preAuth = preAuthorizationRepository.save(preAuth);
        auditService.logApprove(id, preAuth.getReferenceNumber(), approvedBy,
                "PARTIAL_APPROVAL; contractPrice=" + preAuth.getContractPrice()
                        + "; approvedAmount=" + approvedAmount + "; reason=" + reason);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        return mapToResponseDto(preAuth, member, provider, null);
    }

    @Transactional
    public PreAuthorizationResponseDto requestInformation(Long id, String notes, String requestedBy) {
        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));
        assertReviewerAccess(preAuth);
        ensureDecisionReviewable(preAuth);
        if (notes == null || notes.isBlank()) {
            throw new BusinessRuleException("ملاحظات طلب المعلومات مطلوبة");
        }
        notes = notes.trim();
        PreAuthStatus oldStatus = preAuth.getStatus();
        preAuth.setStatus(PreAuthStatus.NEEDS_CORRECTION);
        preAuth.setNotes((preAuth.getNotes() == null ? "" : preAuth.getNotes() + "\n")
                + "Requested information: " + notes);
        preAuth.setUpdatedBy(requestedBy);
        preAuth = preAuthorizationRepository.save(preAuth);
        auditService.logUpdate(id, preAuth.getReferenceNumber(), requestedBy,
                "status", oldStatus.name(), PreAuthStatus.NEEDS_CORRECTION.name());
        auditService.logUpdate(id, preAuth.getReferenceNumber(), requestedBy,
                "requested_information", null, notes);
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== PER-LINE DECISIONS (WAAD-PREAUTH-MULTI-LINE-1, Phase 2) ====================

    /**
     * Record a reviewer's decision for ONE line of a (possibly multi-line)
     * pre-authorization. Does NOT change the header status — call
     * {@link #finalizePreAuthorizationReview(Long, String)} once every line
     * has a decision to compute the header's final outcome.
     *
     * <p>Mirrors ClaimService.submitLineDecision's shape (decision + reason),
     * simplified for PreAuthorization's no-discount, no-companyShare model:
     * a line's approved amount is capped at its OWN contractPrice — it can
     * never borrow ceiling headroom from a sibling line.
     */
    @Transactional
    public PreAuthorizationResponseDto submitLineDecision(Long preAuthId, Long lineId,
            PreAuthorizationLineDecisionDto dto, String reviewedBy) {
        PreAuthorization preAuth = preAuthorizationRepository.findById(preAuthId)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + preAuthId));

        assertReviewerAccess(preAuth);
        ensureDecisionReviewable(preAuth);

        PreAuthorizationLine line = preAuth.getLines().stream()
                .filter(l -> l.getId() != null && l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pre-authorization line " + lineId + " not found for pre-authorization " + preAuthId));

        LineReviewDecision decision = dto.getDecision();
        if (decision == null) {
            throw new BusinessRuleException("القرار مطلوب");
        }
        boolean reasonRequired = decision == LineReviewDecision.REJECTED
                || decision == LineReviewDecision.CLARIFICATION_REQUIRED;
        if (reasonRequired && (dto.getReason() == null || dto.getReason().isBlank())) {
            throw new BusinessRuleException(decision == LineReviewDecision.REJECTED
                    ? "سبب الرفض مطلوب" : "سبب طلب الاستيضاح مطلوب");
        }

        switch (decision) {
            case APPROVED -> {
                // Per-line ceiling: this line's approved amount can never
                // exceed its OWN contract price — it cannot borrow headroom
                // from another line in the same request.
                BigDecimal approvedAmount = dto.getApprovedAmount() != null ? dto.getApprovedAmount()
                        : line.getContractPrice();
                if (line.getContractPrice() != null && approvedAmount.compareTo(line.getContractPrice()) > 0) {
                    throw new BusinessRuleException("المبلغ المعتمد لهذه الخدمة لا يمكن أن يتجاوز سعر عقدها");
                }
                BigDecimal copayPercentage = resolveLineCopayPercentage(line);
                BigDecimal copayAmount = preAuth.calculateCopay(approvedAmount, copayPercentage);
                line.setReviewerDecision(LineReviewDecision.APPROVED);
                line.setApprovedAmount(approvedAmount);
                line.setCopayAmount(copayAmount);
                line.setCopayPercentage(copayPercentage);
                line.setInsuranceCoveredAmount(approvedAmount.subtract(copayAmount));
                line.setReservedAmount(line.getInsuranceCoveredAmount());
                line.setRejectionReason(null);
            }
            case REJECTED -> {
                line.setReviewerDecision(LineReviewDecision.REJECTED);
                line.setRejectionReason(dto.getReason());
                line.setApprovedAmount(BigDecimal.ZERO);
                line.setCopayAmount(BigDecimal.ZERO);
                line.setInsuranceCoveredAmount(BigDecimal.ZERO);
                line.setReservedAmount(BigDecimal.ZERO);
            }
            case CLARIFICATION_REQUIRED -> {
                // No financial effect yet — stays exactly as it was until
                // the reviewer actually approves or rejects it.
                line.setReviewerDecision(LineReviewDecision.CLARIFICATION_REQUIRED);
                line.setRejectionReason(dto.getReason());
            }
        }

        recomputeHeaderAggregatesFromLines(preAuth);
        preAuth.setUpdatedBy(reviewedBy);
        preAuth = preAuthorizationRepository.save(preAuth);

        auditService.logUpdate(preAuthId, preAuth.getReferenceNumber(), reviewedBy,
                "line_" + lineId + "_decision", null, decision.name());

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        return mapToResponseDto(preAuth, member, provider, null);
    }

    /**
     * Compute a line's copay percentage the same way resolveCopayPercentage()
     * does for the header — patientCopayPercentSnapshot, falling back to
     * 100 - coveragePercentSnapshot, falling back to zero.
     */
    private BigDecimal resolveLineCopayPercentage(PreAuthorizationLine line) {
        BigDecimal resolved;
        if (line.getPatientCopayPercentSnapshot() != null) {
            resolved = BigDecimal.valueOf(line.getPatientCopayPercentSnapshot());
        } else if (line.getCoveragePercentSnapshot() != null) {
            resolved = BigDecimal.valueOf(100 - line.getCoveragePercentSnapshot());
        } else {
            resolved = BigDecimal.ZERO;
        }
        if (resolved.compareTo(BigDecimal.ZERO) < 0 || resolved.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException("Resolved line copay percentage is out of range: " + resolved);
        }
        return resolved;
    }

    /**
     * Header approvedAmount/copayAmount/copayPercentage/insuranceCoveredAmount
     * become the SUM across lines currently decided APPROVED (undecided or
     * REJECTED lines contribute zero). Called after every per-line decision
     * so the header always reflects an accurate running total even before
     * the review is finalized. copayPercentage is stored as a simple
     * weighted-by-amount average for display purposes only — it has no
     * further calculation role once per-line amounts already exist.
     */
    private void recomputeHeaderAggregatesFromLines(PreAuthorization preAuth) {
        BigDecimal approvedSum = BigDecimal.ZERO;
        BigDecimal copaySum = BigDecimal.ZERO;
        BigDecimal insuranceCoveredSum = BigDecimal.ZERO;
        for (PreAuthorizationLine line : preAuth.getLines()) {
            if (line.getReviewerDecision() == LineReviewDecision.APPROVED) {
                approvedSum = approvedSum.add(line.getApprovedAmount() != null ? line.getApprovedAmount() : BigDecimal.ZERO);
                copaySum = copaySum.add(line.getCopayAmount() != null ? line.getCopayAmount() : BigDecimal.ZERO);
                insuranceCoveredSum = insuranceCoveredSum
                        .add(line.getInsuranceCoveredAmount() != null ? line.getInsuranceCoveredAmount() : BigDecimal.ZERO);
            }
        }
        preAuth.setApprovedAmount(approvedSum);
        preAuth.setCopayAmount(copaySum);
        preAuth.setInsuranceCoveredAmount(insuranceCoveredSum);
        preAuth.setCopayPercentage(approvedSum.compareTo(BigDecimal.ZERO) > 0
                ? copaySum.multiply(new BigDecimal("100")).divide(approvedSum, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
    }

    /**
     * Compute the header's final outcome from its lines' individual
     * decisions:
     * <ul>
     * <li>Any line still undecided → refuse to finalize; header stays in
     * its current review state (PENDING/UNDER_REVIEW).</li>
     * <li>Any line CLARIFICATION_REQUIRED → header → NEEDS_CORRECTION
     * (existing status, matches requestInformation()'s semantics).</li>
     * <li>All lines APPROVED → header → APPROVED.</li>
     * <li>All lines REJECTED → header → REJECTED.</li>
     * <li>Mixed APPROVED/REJECTED → header → PARTIALLY_APPROVED.</li>
     * </ul>
     * The combined policy-wide limit (annual/member/family) is validated
     * ONCE against the summed total of approved lines before finalizing to
     * APPROVED or PARTIALLY_APPROVED — mirrors ClaimReviewService's
     * "sum first, validate once" pattern for Claim's own multi-line total.
     */
    @Transactional
    public PreAuthorizationResponseDto finalizePreAuthorizationReview(Long preAuthId, String finalizedBy) {
        PreAuthorization preAuth = preAuthorizationRepository.findById(preAuthId)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + preAuthId));

        assertReviewerAccess(preAuth);
        ensureDecisionReviewable(preAuth);

        List<PreAuthorizationLine> lines = preAuth.getLines();
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("PreAuthorization has no lines to finalize");
        }

        boolean anyUndecided = lines.stream().anyMatch(l -> l.getReviewerDecision() == null);
        if (anyUndecided) {
            throw new BusinessRuleException("لم يتم اتخاذ قرار لجميع الخدمات بعد — لا يمكن إنهاء المراجعة");
        }

        boolean anyClarification = lines.stream()
                .anyMatch(l -> l.getReviewerDecision() == LineReviewDecision.CLARIFICATION_REQUIRED);
        boolean allApproved = lines.stream().allMatch(l -> l.getReviewerDecision() == LineReviewDecision.APPROVED);
        boolean allRejected = lines.stream().allMatch(l -> l.getReviewerDecision() == LineReviewDecision.REJECTED);

        PreAuthStatus previousStatus = preAuth.getStatus();

        if (anyClarification) {
            preAuth.setStatus(PreAuthStatus.NEEDS_CORRECTION);
            preAuth.setNotes((preAuth.getNotes() == null ? "" : preAuth.getNotes() + "\n")
                    + "Needs correction: one or more lines require clarification");
            preAuth.setUpdatedBy(finalizedBy);
            preAuth = preAuthorizationRepository.save(preAuth);
            auditService.logUpdate(preAuthId, preAuth.getReferenceNumber(), finalizedBy,
                    "status", previousStatus.name(), PreAuthStatus.NEEDS_CORRECTION.name());
        } else {
            recomputeHeaderAggregatesFromLines(preAuth);
            BigDecimal approvedLinesSum = preAuth.getApprovedAmount();

            if (!allRejected && approvedLinesSum.compareTo(BigDecimal.ZERO) > 0) {
                Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
                if (member != null && member.getBenefitPolicy() != null) {
                    benefitPolicyCoverageService.validateAmountLimits(member, member.getBenefitPolicy(),
                            approvedLinesSum, preAuth.getRequestDate() != null ? preAuth.getRequestDate() : LocalDate.now());
                }
            }

            if (allApproved) {
                preAuth.approve(approvedLinesSum, preAuth.getCopayAmount(), finalizedBy, PreAuthStatus.APPROVED);
            } else if (allRejected) {
                String consolidatedReasons = lines.stream()
                        .map(PreAuthorizationLine::getRejectionReason)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Rejected");
                preAuth.reject(consolidatedReasons, finalizedBy);
            } else {
                // Mixed APPROVED + REJECTED
                preAuth.approve(approvedLinesSum, preAuth.getCopayAmount(), finalizedBy, PreAuthStatus.PARTIALLY_APPROVED);
                String rejectedSummary = lines.stream()
                        .filter(l -> l.getReviewerDecision() == LineReviewDecision.REJECTED)
                        .map(l -> l.getServiceName() + ": " + l.getRejectionReason())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("");
                preAuth.setNotes((preAuth.getNotes() == null ? "" : preAuth.getNotes() + "\n")
                        + "Partially approved — rejected lines: " + rejectedSummary);
            }

            preAuth.setUpdatedBy(finalizedBy);
            preAuth = preAuthorizationRepository.save(preAuth);

            if (allRejected) {
                auditService.logReject(preAuthId, preAuth.getReferenceNumber(), finalizedBy,
                        preAuth.getRejectionReason());
            } else {
                auditService.logApprove(preAuthId, preAuth.getReferenceNumber(), finalizedBy,
                        "Finalized as " + preAuth.getStatus() + "; approvedAmount=" + approvedLinesSum);
            }
        }

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);
        return mapToResponseDto(preAuth, member, provider, null);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════════
     * SPLIT-PHASE APPROVAL: PHASE 1 - Request Approval (Fast, Non-Blocking)
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * This is the NEW approval endpoint that returns immediately.
     * It transitions the pre-auth to APPROVAL_IN_PROGRESS and triggers async
     * processing.
     * 
     * REPLACES: approvePreAuthorization() for production use (old method kept for
     * backward compatibility)
     * 
     * FLOW:
     * 1. Validate pre-auth exists and is in valid state
     * 2. Change status to APPROVAL_IN_PROGRESS (< 1 second)
     * 3. Trigger async background processing
     * 4. Return immediately with status "APPROVAL_IN_PROGRESS"
     * 
     * @param id         PreAuthorization ID
     * @param dto        Approval details
     * @param approvedBy User approving
     * @return PreAuth with APPROVAL_IN_PROGRESS status
     */
    @Transactional
    public PreAuthorizationResponseDto requestApproval(Long id, PreAuthorizationApproveDto dto, String approvedBy) {
        log.info("🚀 [SPLIT-PHASE] Phase 1: Requesting approval for pre-auth {}", id);

        // Quick validation without heavy locks
        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));
        assertReviewerAccess(preAuth);

        ensureDecisionReviewable(preAuth);
        ensureSingleLineForLegacyDecision(preAuth);
        if (!preAuth.canBeApproved()) {
            throw new IllegalStateException(
                    "PreAuthorization cannot be approved in current status: " + preAuth.getStatus());
        }

        // Store approval metadata for async processing
        if (dto.getApprovalNotes() != null) {
            preAuth.setNotes((preAuth.getNotes() != null ? preAuth.getNotes() + "\n" : "") +
                    "Approval Notes: " + dto.getApprovalNotes());
        }

        // Transition to APPROVAL_IN_PROGRESS
        preAuth.setStatus(PreAuthStatus.APPROVAL_IN_PROGRESS);
        PreAuthorization savedPreAuth = preAuthorizationRepository.save(preAuth);

        log.info("✅ [SPLIT-PHASE] Phase 1 complete: PreAuth {} marked as APPROVAL_IN_PROGRESS", id);

        // Trigger async phase 2 processing
        processApprovalAsync(id, dto, approvedBy);

        // Fetch related entities for response
        Member member = memberRepository.findById(savedPreAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(savedPreAuth.getProviderId()).orElse(null);

        return mapToResponseDto(savedPreAuth, member, provider, null);
    }

    /**
     * Enforce reviewer role and provider assignment at the service boundary.
     * Controller annotations remain a first line of defense, but approval
     * transitions must not rely on the HTTP entry point alone.
     */
    private void assertReviewerAccess(PreAuthorization preAuth) {
        User currentUser = authorizationService.getCurrentUser();
        if (!authorizationService.isReviewer(currentUser)
                && !authorizationService.isInsuranceAdmin(currentUser)
                && !authorizationService.isSuperAdmin(currentUser)) {
            throw new AccessDeniedException("Only authorized reviewers can perform pre-authorization decisions");
        }
        if (authorizationService.isReviewer(currentUser)) {
            reviewerIsolationService.validateReviewerAccess(currentUser, preAuth.getProviderId());
        }
    }

    private void ensureDecisionReviewable(PreAuthorization preAuth) {
        if (preAuth.getStatus() != PreAuthStatus.PENDING
                && preAuth.getStatus() != PreAuthStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("لا يمكن تنفيذ قرار المراجعة في الحالة الحالية");
        }
    }

    /**
     * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): the legacy whole-header decision
     * endpoints (approvePreAuthorization, approvePartial, rejectPreAuthorization,
     * requestApproval, reviewPreAuth) each carry exactly ONE amount/reason for
     * "the decision" — applying that single value across N independently-
     * priced lines is ambiguous (would either multiply the money or silently
     * apply it to only one line) and is deliberately unsupported. This is
     * pure forward-safety: every pre-authorization created via today's
     * frontend (and every legacy API caller) always has exactly one line, so
     * this guard never fires for existing traffic — it only blocks a
     * genuinely multi-line request (reachable once Phase 4 ships) from
     * being decided through the wrong endpoint. Use submitLineDecision()
     * per line + finalizePreAuthorizationReview() instead.
     */
    private void ensureSingleLineForLegacyDecision(PreAuthorization preAuth) {
        if (preAuth.getLines() != null && preAuth.getLines().size() > 1) {
            throw new BusinessRuleException(
                    "لا يمكن استخدام قرار الموافقة/الرفض الكامل مع طلب متعدد الخدمات — استخدم قرار كل خدمة على حدة ثم أنهِ المراجعة");
        }
    }

    /**
     * WAAD-PREAUTH-MULTI-LINE-1 (Phase 2): mirrors the header-level decision
     * onto its (single, per ensureSingleLineForLegacyDecision above) line so
     * Phase 3 read-consumers (claim conversion, per-line reviewer display)
     * see consistent data regardless of whether a pre-auth was decided via
     * the legacy whole-header endpoints or the new per-line ones. No-op if
     * lines is somehow empty (defensive; Phase 1's backfill + this service's
     * own creation path both guarantee at least one line).
     */
    private void syncSingleLineDecision(PreAuthorization preAuth, LineReviewDecision decision,
            BigDecimal approvedAmount, BigDecimal copayAmount, BigDecimal copayPercentage, String rejectionReason) {
        if (preAuth.getLines() == null || preAuth.getLines().isEmpty()) {
            return;
        }
        PreAuthorizationLine line = preAuth.getLines().get(0);
        line.setReviewerDecision(decision);
        if (decision == LineReviewDecision.APPROVED) {
            line.setApprovedAmount(approvedAmount);
            line.setCopayAmount(copayAmount);
            line.setCopayPercentage(copayPercentage);
            line.setInsuranceCoveredAmount(
                    approvedAmount != null && copayAmount != null ? approvedAmount.subtract(copayAmount) : null);
            line.setReservedAmount(line.getInsuranceCoveredAmount());
            line.setRejectionReason(null);
        } else if (decision == LineReviewDecision.REJECTED) {
            line.setRejectionReason(rejectionReason);
            line.setApprovedAmount(BigDecimal.ZERO);
            line.setCopayAmount(BigDecimal.ZERO);
            line.setInsuranceCoveredAmount(BigDecimal.ZERO);
            line.setReservedAmount(BigDecimal.ZERO);
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════════
     * SPLIT-PHASE APPROVAL: PHASE 2 - Process Approval (Heavy, Background)
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * This method executes in the background using @Async.
     * It performs all heavy calculations including copay, coverage validation, etc.
     * 
     * EXECUTES:
     * 1. Copay calculation
     * 2. Coverage validation (BenefitPolicy)
     * 3. Contract price validation
     * 4. Transition to APPROVED or REJECTED
     * 
     * ISOLATION: REQUIRES_NEW to avoid deadlocks with Phase 1
     * 
     * @param id         PreAuthorization ID
     * @param dto        Approval details (from Phase 1)
     * @param approvedBy User approving
     */
    @org.springframework.scheduling.annotation.Async("approvalTaskExecutor")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public void processApprovalAsync(Long id, PreAuthorizationApproveDto dto, String approvedBy) {
        log.info("⚙️ [SPLIT-PHASE] Phase 2: Starting async approval processing for pre-auth {}", id);

        try {
            PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

            log.info("🔒 [ASYNC-PROCESSING] Processing pre-auth {}", id);

            if (preAuth.getStatus() != PreAuthStatus.APPROVAL_IN_PROGRESS) {
                throw new IllegalStateException(
                        "PreAuthorization is not in APPROVAL_IN_PROGRESS status: " + preAuth.getStatus());
            }

            BigDecimal approvedAmount = resolveApprovedAmount(preAuth, dto);
            BigDecimal copayPercentage = resolveCopayPercentage(preAuth, dto);

            // Validate approved amount against contract price
            if (preAuth.getContractPrice() != null && approvedAmount.compareTo(preAuth.getContractPrice()) > 0) {
                log.warn("[PRE-AUTH] Approved amount {} exceeds contract price {}",
                        approvedAmount, preAuth.getContractPrice());
            }

            // Calculate copay
            BigDecimal copayAmount = preAuth.calculateCopay(approvedAmount, copayPercentage);

            // Validate coverage if member has benefit policy
            Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
            if (member != null && member.getBenefitPolicy() != null) {
                try {
                    benefitPolicyCoverageService.validateAmountLimits(
                            member, member.getBenefitPolicy(), approvedAmount,
                            preAuth.getRequestDate() != null ? preAuth.getRequestDate() : LocalDate.now());
                    log.debug("✅ BenefitPolicy amount validation passed");
                } catch (Exception e) {
                    log.error("❌ BenefitPolicy coverage validation failed: {}", e.getMessage());
                    throw new IllegalStateException("فشل التحقق من التغطية: " + e.getMessage());
                }
            }

            // Approve
            preAuth.approve(approvedAmount, copayAmount, approvedBy);
            preAuth.setCopayPercentage(copayPercentage);
            syncSingleLineDecision(preAuth, LineReviewDecision.APPROVED, approvedAmount, copayAmount, copayPercentage,
                    null);

            preAuth = preAuthorizationRepository.save(preAuth);
            log.info("[PRE-AUTH] Approved pre-authorization {} with amount {} and copay {}",
                    id, approvedAmount, copayAmount);

            // Note: visit status is NOT auto-set to COMPLETED here.
            // A visit may have multiple pre-authorizations and claims.
            // Visit completion is managed by the provider or when all linked items are
            // settled.
            log.debug("[PRE-AUTH] Pre-auth {} approved; visit status unchanged (may have other pending items)", id);

            // Log audit trail
            auditService.logApprove(id, preAuth.getReferenceNumber(), approvedBy,
                    "Approved amount: " + approvedAmount +
                            ", Copay: " + copayPercentage + "%");

            log.info("✅ [SPLIT-PHASE] Phase 2 complete: PreAuth {} approved successfully", id);

        } catch (Exception e) {
            log.error("❌ [SPLIT-PHASE] Phase 2 failed for pre-auth {}: {}", id, e.getMessage(), e);

            // Technical processing failures are retryable and must not be treated as
            // a medical rejection. Keep the request reviewable and record the failure.
            try {
                PreAuthorization failedPreAuth = preAuthorizationRepository.findById(id).orElse(null);
                if (failedPreAuth != null && failedPreAuth.getStatus() == PreAuthStatus.APPROVAL_IN_PROGRESS) {
                    failedPreAuth.setStatus(PreAuthStatus.UNDER_REVIEW);
                    failedPreAuth.setNotes((failedPreAuth.getNotes() == null ? "" : failedPreAuth.getNotes() + "\n")
                            + "Technical approval processing failed; retry required: " + e.getMessage());
                    failedPreAuth.setUpdatedBy(approvedBy);
                    preAuthorizationRepository.save(failedPreAuth);
                    log.info("🔄 PreAuth {} returned to UNDER_REVIEW after technical processing failure", id);
                }
            } catch (Exception rollbackError) {
                log.error("❌ Failed to return pre-auth {} to UNDER_REVIEW: {}", id, rollbackError.getMessage());
            }
        }
    }

    private BigDecimal resolveApprovedAmount(PreAuthorization preAuth, PreAuthorizationApproveDto dto) {
        if (dto != null && dto.getApprovedAmount() != null) {
            // No longer enforcing > 0
            return dto.getApprovedAmount();
        }

        if (preAuth.getContractPrice() != null) {
            return preAuth.getContractPrice();
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal resolveCopayPercentage(PreAuthorization preAuth, PreAuthorizationApproveDto dto) {
        BigDecimal resolved;

        if (dto != null && dto.getCopayPercentage() != null) {
            resolved = dto.getCopayPercentage();
        } else if (preAuth.getPatientCopayPercentSnapshot() != null) {
            resolved = BigDecimal.valueOf(preAuth.getPatientCopayPercentSnapshot());
        } else if (preAuth.getCoveragePercentSnapshot() != null) {
            resolved = BigDecimal.valueOf(100 - preAuth.getCoveragePercentSnapshot());
        } else {
            resolved = BigDecimal.ZERO;
        }

        if (resolved.compareTo(BigDecimal.ZERO) < 0 || resolved.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException("Resolved copay percentage is out of range: " + resolved);
        }

        return resolved;
    }

    // ==================== REJECT ====================

    /**
     * Reject pre-authorization
     */
    @Transactional
    public PreAuthorizationResponseDto rejectPreAuthorization(Long id, PreAuthorizationRejectDto dto,
            String rejectedBy) {
        log.info("[PRE-AUTH] Rejecting pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        assertReviewerAccess(preAuth);
        ensureDecisionReviewable(preAuth);
        ensureSingleLineForLegacyDecision(preAuth);

        preAuth.reject(dto.getRejectionReason(), rejectedBy);
        syncSingleLineDecision(preAuth, LineReviewDecision.REJECTED, null, null, null, dto.getRejectionReason());

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Rejected pre-authorization {} with reason: {}", id, dto.getRejectionReason());

        // A visit may contain several service requests. Cancel it only when no
        // other active request can still proceed.
        if (preAuth.getVisit() != null && preAuthorizationRepository.findByVisitIdAndActiveTrue(preAuth.getVisit().getId())
                .stream().allMatch(item -> item.getStatus() == PreAuthStatus.REJECTED
                        || item.getStatus() == PreAuthStatus.CANCELLED)) {
            Visit visit = preAuth.getVisit();
            visit.setStatus(com.waad.tba.modules.visit.entity.VisitStatus.CANCELLED);
            visitRepository.save(visit);
            log.info("✅ Updated visit {} status to CANCELLED after pre-auth rejection", visit.getId());
        }

        // Log audit trail
        auditService.logReject(id, preAuth.getReferenceNumber(), rejectedBy, dto.getRejectionReason());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== CANCEL ====================

    /**
     * Cancel pre-authorization
     */
    @Transactional
    public PreAuthorizationResponseDto cancelPreAuthorization(Long id, String cancelReason, String cancelledBy) {
        log.info("[PRE-AUTH] Cancelling pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        preAuth.cancel(cancelReason, cancelledBy);

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Cancelled pre-authorization {}", id);

        // Log audit trail
        auditService.logCancel(id, preAuth.getReferenceNumber(), cancelledBy, cancelReason);

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== ACKNOWLEDGE ====================

    /**
     * Acknowledge pre-authorization (Provider viewed the approval)
     * Lifecycle: APPROVED → ACKNOWLEDGED
     */
    @Transactional
    public PreAuthorizationResponseDto acknowledgePreAuthorization(Long id, String acknowledgedBy) {
        log.info("[PRE-AUTH] Provider acknowledging pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        // Validation: Only APPROVED/PARTIALLY_APPROVED pre-auths can be acknowledged
        if (preAuth.getStatus() != PreAuthStatus.APPROVED && preAuth.getStatus() != PreAuthStatus.PARTIALLY_APPROVED) {
            throw new IllegalStateException(
                    String.format("Only APPROVED pre-authorizations can be acknowledged. Current status: %s",
                            preAuth.getStatus()));
        }

        // Transition to ACKNOWLEDGED
        PreAuthStatus oldStatus = preAuth.getStatus();
        preAuth.setStatus(PreAuthStatus.ACKNOWLEDGED);
        preAuth.setUpdatedBy(acknowledgedBy);

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("✅ Pre-authorization {} acknowledged by provider", id);

        // Log audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), acknowledgedBy,
                "status", oldStatus.toString(), PreAuthStatus.ACKNOWLEDGED.toString());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== MARK AS USED ====================

    /**
     * Mark pre-authorization as USED (called when linked to a claim)
     * Lifecycle: APPROVED/ACKNOWLEDGED → USED
     * 
     * This is typically called automatically by ClaimService when a claim is
     * created with a pre-auth.
     */
    @Transactional
    public PreAuthorizationResponseDto markAsUsed(Long id, String claimNumber, String updatedBy) {
        log.info("[PRE-AUTH] Marking pre-authorization {} as USED (linked to claim {})", id, claimNumber);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        // Validation: Only APPROVED, PARTIALLY_APPROVED, or ACKNOWLEDGED pre-auths can be marked as USED
        if (preAuth.getStatus() != PreAuthStatus.APPROVED && preAuth.getStatus() != PreAuthStatus.PARTIALLY_APPROVED
                && preAuth.getStatus() != PreAuthStatus.ACKNOWLEDGED) {
            throw new IllegalStateException(
                    String.format(
                            "Only APPROVED or ACKNOWLEDGED pre-authorizations can be marked as USED. Current status: %s",
                            preAuth.getStatus()));
        }

        // Transition to USED
        PreAuthStatus oldStatus = preAuth.getStatus();
        preAuth.setStatus(PreAuthStatus.USED);
        preAuth.setUpdatedBy(updatedBy);

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("✅ Pre-authorization {} marked as USED (claim: {})", id, claimNumber);

        // Log audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), updatedBy,
                "status", oldStatus.toString(), PreAuthStatus.USED.toString());

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== DELETE ====================

    /**
     * Soft delete pre-authorization
     */
    @Transactional
    public void deletePreAuthorization(Long id, String deletedBy) {
        log.info("[PRE-AUTH] Deleting pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        preAuth.setActive(false);
        preAuth.setUpdatedBy(deletedBy);

        preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Deleted pre-authorization {}", id);

        // Log audit trail
        auditService.logDelete(id, preAuth.getReferenceNumber(), deletedBy);
    }

    // ==================== QUERIES ====================

    /**
     * Get pre-authorization by ID
     */
    @Transactional(readOnly = true)
    public PreAuthorizationResponseDto getPreAuthorizationById(Long id) {
        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    /**
     * Get pre-authorization by reference number
     */
    @Transactional(readOnly = true)
    public PreAuthorizationResponseDto getPreAuthorizationByReference(String referenceNumber) {
        PreAuthorization preAuth = preAuthorizationRepository.findByReferenceNumberAndActiveTrue(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PreAuthorization not found with reference: " + referenceNumber));

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    /**
     * Get all pre-authorizations (paginated)
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getAllPreAuthorizations(Pageable pageable) {
        // WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1: this endpoint had no
        // reviewer-provider scoping at all — an isolated MEDICAL_REVIEWER
        // could see every pre-authorization in the system. Mirrors
        // getPendingInbox()'s isolation branch above.
        User currentUser = authorizationService.getCurrentUser();
        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            List<Long> allowedProviderIds = reviewerIsolationService.getAllowedProviderIds(currentUser);
            if (allowedProviderIds.isEmpty()) {
                return Page.empty(pageable);
            }
            Page<PreAuthorization> isolatedPreAuths = preAuthorizationRepository
                    .findByProviderIdInAndActiveTrue(allowedProviderIds, pageable);
            return isolatedPreAuths.map(this::mapToResponseDtoLight);
        }

        Page<PreAuthorization> preAuths = preAuthorizationRepository.findByActiveTrue(pageable);
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Get pre-authorizations by member
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getPreAuthorizationsByMember(Long memberId, Pageable pageable) {
        Page<PreAuthorization> preAuths = preAuthorizationRepository.findByMemberIdAndActiveTrue(memberId, pageable);
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Get pre-authorizations by provider
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getPreAuthorizationsByProvider(Long providerId, Pageable pageable) {
        // WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1: reject an isolated
        // MEDICAL_REVIEWER passing an unassigned providerId directly.
        User currentUser = authorizationService.getCurrentUser();
        reviewerIsolationService.validateReviewerAccess(currentUser, providerId);

        Page<PreAuthorization> preAuths = preAuthorizationRepository.findByProviderIdAndActiveTrue(providerId,
                pageable);
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Get pre-authorizations by status
     *
     * WAAD-PREAUTH-REVIEWER-HISTORY-1: mirrors getPendingInbox()'s isolation
     * branch — an isolated MEDICAL_REVIEWER must only see APPROVED/REJECTED
     * (or any other status) pre-authorizations for their assigned providers,
     * not every provider's. This endpoint previously had no isolation at
     * all, which would have become reachable from the reviewer UI once the
     * "processed" history view was wired up to it.
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getPreAuthorizationsByStatus(PreAuthStatus status, Pageable pageable) {
        User currentUser = authorizationService.getCurrentUser();
        Page<PreAuthorization> preAuths;
        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            List<Long> allowedProviderIds = reviewerIsolationService.getAllowedProviderIds(currentUser);
            preAuths = allowedProviderIds.isEmpty()
                    ? Page.empty(pageable)
                    : preAuthorizationRepository.findByStatusInAndReviewerProviders(allowedProviderIds,
                            List.of(status), pageable);
        } else {
            preAuths = preAuthorizationRepository.findByStatusAndActiveTrue(status, pageable);
        }
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Get pending pre-authorizations for inbox (Operations Queue) - CANONICAL
     * 2026-01-26
     * 
     * Returns pre-authorizations with PENDING or UNDER_REVIEW status for
     * processing.
     * Mirrors ClaimService.getPendingClaims() behavior.
     * 
     * FIFO pattern - oldest first for fair processing.
     * 
     * Status Logic:
     * - PENDING: Newly created, awaiting initial review
     * - UNDER_REVIEW: Currently being reviewed by operations staff
     * 
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of PreAuthorizationResponseDto with all required fields for
     *         inbox display
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> getPendingInbox(Pageable pageable) {
        log.info("[SERVICE] Fetching pending pre-authorizations for inbox (PENDING + UNDER_REVIEW)");

        // CANONICAL: Include both PENDING and UNDER_REVIEW statuses (like Claims)
        List<PreAuthStatus> inboxStatuses = List.of(PreAuthStatus.PENDING, PreAuthStatus.UNDER_REVIEW);

        // PREAUTH-REVIEW-WORKFLOW-1: a MEDICAL_REVIEWER only sees pending/
        // under-review items for their assigned providers, mirroring
        // ClaimService's inbox-listing use of getAllowedProviderIds() — this
        // was previously enforced only at the point of taking a decision
        // (approve/reject/etc.), not at the point of listing. SUPER_ADMIN
        // (and any other role permitted to call this endpoint) is not
        // subject to isolation and sees everything, unchanged.
        User currentUser = authorizationService.getCurrentUser();
        Page<PreAuthorization> preAuths;
        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            List<Long> allowedProviderIds = reviewerIsolationService.getAllowedProviderIds(currentUser);
            preAuths = allowedProviderIds.isEmpty()
                    ? Page.empty(pageable)
                    : preAuthorizationRepository.findByStatusInAndReviewerProviders(allowedProviderIds, inboxStatuses, pageable);
        } else {
            preAuths = preAuthorizationRepository.findByStatusIn(inboxStatuses, pageable);
        }

        log.info("[SERVICE] Found {} pre-authorizations in inbox", preAuths.getTotalElements());
        return preAuths.map(this::mapToResponseDtoLight);
    }

    /**
     * Find valid pre-authorization for claim submission
     */
    @Transactional(readOnly = true)
    public PreAuthorizationResponseDto findValidPreAuthorization(Long memberId, Long providerId, String serviceCode) {
        List<PreAuthorization> validPreAuths = preAuthorizationRepository.findValidPreAuthorizations(
                memberId, providerId, serviceCode, LocalDate.now());

        if (validPreAuths.isEmpty()) {
            throw new ResourceNotFoundException("No valid pre-authorization found for member " + memberId +
                    ", provider " + providerId + ", service " + serviceCode);
        }

        // Return the most recent one
        PreAuthorization preAuth = validPreAuths.get(0);

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== START REVIEW ====================

    /**
     * Start review of a pre-authorization (PENDING → UNDER_REVIEW)
     * This is typically called by a reviewer taking ownership of the request.
     */
    @Transactional
    public PreAuthorizationResponseDto startReview(Long id, String reviewedBy) {
        log.info("[PRE-AUTH] Starting review for pre-authorization {}", id);

        PreAuthorization preAuth = preAuthorizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PreAuthorization not found with ID: " + id));

        // PREAUTH-REVIEW-WORKFLOW-1: this was the one decision-adjacent
        // method with no reviewer-provider isolation check at all — any
        // MEDICAL_REVIEWER could start-review any provider's item regardless
        // of assignment, unlike every other decision method below (approve,
        // reject, approvePartial, requestInformation, reviewPreAuth all call
        // this same assertReviewerAccess()).
        assertReviewerAccess(preAuth);

        if (!preAuth.getActive()) {
            throw new IllegalArgumentException("PreAuthorization is not active");
        }

        if (preAuth.getStatus() != PreAuthStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING pre-authorizations can be started for review. Current status: "
                            + preAuth.getStatus());
        }

        // Transition to UNDER_REVIEW
        preAuth.setStatus(PreAuthStatus.UNDER_REVIEW);
        preAuth.setUpdatedBy(reviewedBy);

        preAuth = preAuthorizationRepository.save(preAuth);
        log.info("[PRE-AUTH] Pre-authorization {} is now UNDER_REVIEW by {}", id, reviewedBy);

        // Update visit status if linked
        if (preAuth.getVisit() != null) {
            Visit visit = preAuth.getVisit();
            visit.setStatus(com.waad.tba.modules.visit.entity.VisitStatus.IN_PROGRESS);
            visitRepository.save(visit);
            log.info("✅ Updated visit {} status to IN_PROGRESS", visit.getId());
        }

        // Log audit trail
        auditService.logUpdate(id, preAuth.getReferenceNumber(), reviewedBy,
                "status", "PENDING", "UNDER_REVIEW");

        // Fetch related entities for response
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== CHECK VALIDITY ====================

    /**
     * Check if a member has a valid pre-authorization for a specific service.
     * Returns the valid pre-authorization if found, null otherwise.
     * 
     * @param memberId    The member ID
     * @param serviceCode The medical service code
     * @return Valid PreAuthorizationResponseDto or null if not found
     */
    @Transactional(readOnly = true)
    public PreAuthorizationResponseDto checkValidity(Long memberId, String serviceCode) {
        log.info("[PRE-AUTH] Checking validity for member {} and service {}", memberId, serviceCode);

        // Find approved and valid pre-authorizations for this member and service
        List<PreAuthorization> validPreAuths = preAuthorizationRepository
                .findValidByMemberAndService(memberId, serviceCode, LocalDate.now());

        if (validPreAuths.isEmpty()) {
            log.info("[PRE-AUTH] No valid pre-authorization found for member {} and service {}", memberId, serviceCode);
            return null;
        }

        // Return the most recent valid one
        PreAuthorization preAuth = validPreAuths.stream()
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .orElse(validPreAuths.get(0));

        log.info("[PRE-AUTH] Found valid pre-authorization {} for member {} and service {}",
                preAuth.getReferenceNumber(), memberId, serviceCode);

        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    // ==================== MAINTENANCE ====================

    /**
     * Mark expired pre-authorizations
     */
    @Transactional
    public int markExpiredPreAuthorizations() {
        log.info("[PRE-AUTH] Marking expired pre-authorizations");

        List<PreAuthorization> expiredList = preAuthorizationRepository.findExpiredPreAuthorizations(LocalDate.now());

        for (PreAuthorization preAuth : expiredList) {
            preAuth.markAsExpired();
        }

        if (!expiredList.isEmpty()) {
            preAuthorizationRepository.saveAll(expiredList);
        }

        log.info("[PRE-AUTH] Marked {} expired pre-authorizations", expiredList.size());
        return expiredList.size();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate unique reference number
     */
    private String generateUniqueReferenceNumber() {
        Number next = (Number) entityManager
                .createNativeQuery("select nextval('pre_authorization_number_seq')")
                .getSingleResult();
        return "PA-" + String.format("%06d", next.longValue());
    }

    /**
     * Map to response DTO with full details (CANONICAL REBUILD 2026-01-16)
     */
    private PreAuthorizationResponseDto mapToResponseDto(PreAuthorization preAuth, Member member,
            Provider provider, @Deprecated Object ignoredService) {
        Integer daysUntilExpiry = null;
        if (preAuth.getExpiryDate() != null) {
            daysUntilExpiry = (int) ChronoUnit.DAYS.between(LocalDate.now(), preAuth.getExpiryDate());
        }

        // Get visit info
        Visit visit = preAuth.getVisit();

        // WAAD-PREAUTH-CLAIM-SERVICE-LINK-1: prefer the pricingItemId stored
        // at creation time (all pre-auths created after this fix). For
        // legacy rows created before this column existed, fall back to a
        // best-effort live lookup by provider+serviceCode so old records
        // aren't left permanently broken when later converted to a claim.
        Long resolvedPricingItemId = preAuth.getPricingItemId();
        if (resolvedPricingItemId == null && preAuth.getServiceCode() != null && preAuth.getProviderId() != null) {
            resolvedPricingItemId = pricingItemRepository
                    .findEffectivePricingByCode(preAuth.getProviderId(), preAuth.getServiceCode(), LocalDate.now())
                    .map(com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem::getId)
                    .orElse(null);
        }

        return PreAuthorizationResponseDto.builder()
                .id(preAuth.getId())
                .referenceNumber(preAuth.getReferenceNumber())
                // Visit info
                .visitId(visit != null ? visit.getId() : null)
                .visitDate(visit != null ? visit.getVisitDate() : null)
                .visitType(visit != null && visit.getVisitType() != null ? visit.getVisitType().toString() : null)
                // Member info
                .memberId(preAuth.getMemberId())
                .memberName(member != null ? member.getFullName() : null)
                .memberCardNumber(member != null ? member.getCardNumber() : null)
                .memberNationalNumber(member != null ? member.getNationalNumber() : null)
                // Employer info (جهة العمل)
                .employerId(member != null && member.getEmployer() != null ? member.getEmployer().getId() : null)
                .employerName(member != null && member.getEmployer() != null ? member.getEmployer().getName() : null)
                .employerCode(member != null && member.getEmployer() != null ? member.getEmployer().getCode() : null)
                // Provider info
                .providerId(preAuth.getProviderId())
                .providerName(provider != null ? provider.getName() : null)
                .providerLicense(provider != null ? provider.getLicenseNumber() : null)
                // Medical Service info (from pricingItem denormalized snapshot)
                .medicalServiceId(null)
                .pricingItemId(resolvedPricingItemId)
                .serviceCode(preAuth.getServiceCode())
                .serviceName(preAuth.getServiceName())
                .serviceCategoryId(preAuth.getServiceCategoryId())
                .serviceCategoryName(preAuth.getServiceCategoryName())
                .requiresPA(preAuth.getRequiresPA())
                // WAAD-PREAUTH-MULTI-LINE-1 (Phase 2)
                .lines(preAuth.getLines() == null ? null
                        : preAuth.getLines().stream()
                                .map(line -> PreAuthorizationLineResponseDto.builder()
                                        .id(line.getId())
                                        .lineNumber(line.getLineNumber())
                                        .pricingItemId(line.getPricingItemId())
                                        .serviceCode(line.getServiceCode())
                                        .serviceName(line.getServiceName())
                                        .serviceCategoryId(line.getServiceCategoryId())
                                        .serviceCategoryName(line.getServiceCategoryName())
                                        .contractPrice(line.getContractPrice())
                                        .requiresPA(line.getRequiresPA())
                                        .approvedAmount(line.getApprovedAmount())
                                        .copayAmount(line.getCopayAmount())
                                        .copayPercentage(line.getCopayPercentage())
                                        .insuranceCoveredAmount(line.getInsuranceCoveredAmount())
                                        .reviewerDecision(line.getReviewerDecision() != null
                                                ? line.getReviewerDecision().name() : null)
                                        .rejectionReason(line.getRejectionReason())
                                        .build())
                                .toList())
                // Diagnosis
                .diagnosisCode(preAuth.getDiagnosisCode())
                .diagnosisDescription(preAuth.getDiagnosisDescription())
                // Dates
                .requestDate(preAuth.getRequestDate())
                .expiryDate(preAuth.getExpiryDate())
                .daysUntilExpiry(daysUntilExpiry)
                // Pricing (Contract-Driven) - Removed as per user requirement (Pre-Auth is
                // non-financial)
                .contractPrice(null)
                .approvedAmount(null)
                .copayAmount(null)
                .copayPercentage(null)
                .insuranceCoveredAmount(null)
                .currency(preAuth.getCurrency())
                // Status
                .status(preAuth.getStatus().toString())
                .priority(preAuth.getPriority().toString())
                // Additional
                .notes(preAuth.getNotes())
                .rejectionReason(preAuth.getRejectionReason())
                // Flags
                .hasContract(preAuth.getContractPrice() != null)
                .isValid(preAuth.isValid())
                .isExpired(preAuth.isExpired())
                .canBeApproved(preAuth.canBeApproved())
                .canBeRejected(preAuth.canBeRejected())
                .canBeCancelled(preAuth.canBeCancelled())
                // Audit
                .createdAt(preAuth.getCreatedAt())
                .updatedAt(preAuth.getUpdatedAt())
                .createdBy(preAuth.getCreatedBy())
                .updatedBy(preAuth.getUpdatedBy())
                .approvedAt(preAuth.getApprovedAt())
                .approvedBy(preAuth.getApprovedBy())
                .active(preAuth.getActive())
                .build();
    }

    /**
     * Map to response DTO (lightweight - for lists)
     * Fetches member, provider and service info for complete display
     */
    private PreAuthorizationResponseDto mapToResponseDtoLight(PreAuthorization preAuth) {
        // Fetch related entities for complete display
        Member member = memberRepository.findById(preAuth.getMemberId()).orElse(null);
        Provider provider = providerRepository.findById(preAuth.getProviderId()).orElse(null);

        return mapToResponseDto(preAuth, member, provider, null);
    }

    /**
     * PROVIDER PORTAL (2026-01-14):
     * Validate and enforce provider ID based on user role.
     * 
     * Rules (HARDENED 2026-01-16):
     * - PROVIDER users: providerId ALWAYS comes from ProviderContextGuard (session)
     * ANY providerId from request is IGNORED to prevent data leakage
     * - SUPER_ADMIN/INSURANCE_ADMIN can set any providerId
     * - Other users can set any providerId
     * 
     * @param dto         The pre-authorization creation DTO
     * @param currentUser The currently authenticated user
     */
    private void validateAndEnforceProviderId(PreAuthorizationCreateDto dto, User currentUser) {
        if (currentUser == null) {
            log.warn("⚠️ No authenticated user - skipping provider validation");
            return;
        }

        // Check if user is a PROVIDER - use ProviderContextGuard for strict enforcement
        if (authorizationService.isProvider(currentUser)) {
            // ═══════════════════════════════════════════════════════════════════════════
            // SECURITY HARDENING: Use ProviderContextGuard for validation
            // This ensures provider binding is validated and providerId is enforced
            // ═══════════════════════════════════════════════════════════════════════════
            providerContextGuard.validateProviderBinding(currentUser);
            Long userProviderId = currentUser.getProviderId();

            // Log if request contained different providerId (potential attack/bug)
            if (dto.getProviderId() != null && !dto.getProviderId().equals(userProviderId)) {
                log.warn(
                        "🚨 PROVIDER_ID_OVERRIDE: User {} requested providerId={} but enforced to {} (potential security issue)",
                        currentUser.getUsername(), dto.getProviderId(), userProviderId);
            }

            // ALWAYS override with user's providerId - NO EXCEPTIONS
            dto.setProviderId(userProviderId);

            log.info("🔒 PROVIDER {} creating pre-auth with their providerId: {} (enforced by ProviderContextGuard)",
                    currentUser.getUsername(), userProviderId);
        } else if (authorizationService.isSuperAdmin(currentUser)
                || authorizationService.isInsuranceAdmin(currentUser)) {
            // SUPER_ADMIN and INSURANCE_ADMIN can set any provider
            log.info("🔓 ADMIN user {} creating pre-auth - any providerId allowed", currentUser.getUsername());
        }
        // Other roles: no restriction on providerId
    }

    /**
     * Search pre-authorizations
     */
    @Transactional(readOnly = true)
    public Page<PreAuthorizationResponseDto> search(String query, Pageable pageable) {
        log.debug("Searching pre-authorizations with query: {}", query);
        if (query == null || query.isBlank()) {
            return getAllPreAuthorizations(pageable);
        }

        // WAAD-RBAC-REVIEWER-PROVIDER-ASSIGNMENT-1: search had no reviewer
        // isolation — mirrors getAllPreAuthorizations()'s branch above.
        User currentUser = authorizationService.getCurrentUser();
        if (reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            List<Long> allowedProviderIds = reviewerIsolationService.getAllowedProviderIds(currentUser);
            if (allowedProviderIds.isEmpty()) {
                return Page.empty(pageable);
            }
            return preAuthorizationRepository.searchByProviderIds(query, allowedProviderIds, pageable)
                    .map(pa -> mapToResponseDto(pa,
                            memberRepository.findById(pa.getMemberId()).orElse(null),
                            providerRepository.findById(pa.getProviderId()).orElse(null),
                            null));
        }

        return preAuthorizationRepository.search(query, pageable)
                .map(pa -> mapToResponseDto(pa,
                        memberRepository.findById(pa.getMemberId()).orElse(null),
                        providerRepository.findById(pa.getProviderId()).orElse(null),
                        null));
    }
}
