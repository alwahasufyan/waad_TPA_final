package com.waad.tba.modules.medicalclassification.pricelist.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.ValidationException;
import com.waad.tba.modules.medicalclassification.engine.service.CatalogKnowledgeService;
import com.waad.tba.modules.medicalclassification.pricelist.dto.ReviewDecisionDto;
import com.waad.tba.modules.medicalclassification.pricelist.dto.ReviewSummaryDto;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImport;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportRepository;
import com.waad.tba.modules.medicalclassification.repository.CatalogClassificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Medical Classification Workspace — review decisions (MC-2).
 *
 * Owner directive: this is not an error-fixing screen; every decision feeds
 * the WAAD medical dictionary ({@link CatalogKnowledgeService}) so future
 * imports need less review. A4 stands: nothing is approved except by a human
 * — individually here, or via the explicit audited "Approve Remaining".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private static final Set<PriceListImport.Status> REVIEWABLE =
            EnumSet.of(PriceListImport.Status.CLASSIFIED, PriceListImport.Status.IN_REVIEW);

    private final PriceListImportRepository importRepository;
    private final PriceListImportLineRepository lineRepository;
    private final CatalogKnowledgeService knowledge;
    private final CatalogClassificationHistoryRepository historyRepository;
    private final PriceListVersionService versionService;
    private final com.waad.tba.modules.providercontract.repository.ProviderContractRepository contractRepository;

    @Transactional(readOnly = true)
    public ReviewSummaryDto summary(Long importId) {
        PriceListImport imp = getImport(importId);
        long needsReview = lineRepository.countByImportIdAndReviewStatus(
                importId, PriceListImportLine.ReviewStatus.NEEDS_REVIEW);
        long pendingBulk = lineRepository.countByImportIdAndReviewStatus(
                importId, PriceListImportLine.ReviewStatus.PENDING_BULK);
        long approved = lineRepository.countByImportIdAndReviewStatus(
                importId, PriceListImportLine.ReviewStatus.APPROVED);
        long rejected = lineRepository.countByImportIdAndReviewStatus(
                importId, PriceListImportLine.ReviewStatus.REJECTED);
        return ReviewSummaryDto.builder()
                .importId(importId)
                .status(imp.getStatus().name())
                .totalLines(imp.getTotalLines())
                .needsReview(needsReview)
                .pendingBulk(pendingBulk)
                .approved(approved)
                .rejected(rejected)
                .unknownQueue(lineRepository.countQueue(importId, "UNKNOWN"))
                .lowConfidenceQueue(lineRepository.countQueue(importId, "LOW_CONFIDENCE"))
                .duplicateQueue(lineRepository.countQueue(importId, "DUPLICATE"))
                .guardQueue(lineRepository.countQueue(importId, "GUARD"))
                .approveRemainingEnabled(needsReview == 0 && pendingBulk > 0)
                .knowledgeDecisions(historyRepository.countByImportId(importId))
                .build();
    }

    @Transactional
    public PriceListImportLine decide(Long importId, Long lineId, ReviewDecisionDto decision, String reviewer) {
        PriceListImport imp = getImport(importId);
        if (!REVIEWABLE.contains(imp.getStatus())) {
            throw new BusinessRuleException("لا يمكن المراجعة في الحالة: " + imp.getStatus());
        }
        PriceListImportLine line = lineRepository.findById(lineId)
                .filter(l -> importId.equals(l.getImportId()))
                .orElseThrow(() -> new ResourceNotFoundException("Line not found in import: " + lineId));
        if (line.getReviewStatus() == PriceListImportLine.ReviewStatus.APPROVED
                || line.getReviewStatus() == PriceListImportLine.ReviewStatus.REJECTED) {
            throw new BusinessRuleException("هذا السطر تمت مراجعته مسبقًا");
        }

        applyDecision(line, decision, reviewer, PriceListImportLine.ApprovalMode.INDIVIDUAL);
        lineRepository.save(line);
        afterDecisions(imp);
        return line;
    }

    @Transactional
    public int decideBulk(Long importId, List<Long> lineIds, ReviewDecisionDto decision, String reviewer) {
        PriceListImport imp = getImport(importId);
        if (!REVIEWABLE.contains(imp.getStatus())) {
            throw new BusinessRuleException("لا يمكن المراجعة في الحالة: " + imp.getStatus());
        }
        if (lineIds == null || lineIds.isEmpty()) {
            throw new ValidationException("لم يتم تحديد أسطر");
        }
        int done = 0;
        for (Long lineId : lineIds) {
            PriceListImportLine line = lineRepository.findById(lineId)
                    .filter(l -> importId.equals(l.getImportId()))
                    .orElse(null);
            if (line == null
                    || line.getReviewStatus() == PriceListImportLine.ReviewStatus.APPROVED
                    || line.getReviewStatus() == PriceListImportLine.ReviewStatus.REJECTED) {
                continue;
            }
            applyDecision(line, decision, reviewer, PriceListImportLine.ApprovalMode.INDIVIDUAL);
            lineRepository.save(line);
            done++;
        }
        afterDecisions(imp);
        return done;
    }

    /**
     * A5: the explicit, audited bulk approval of the hidden high-confidence
     * majority — enabled ONLY when the critical queue is empty.
     */
    @Transactional
    public int approveRemaining(Long importId, String reviewer) {
        PriceListImport imp = getImport(importId);
        if (!REVIEWABLE.contains(imp.getStatus())) {
            throw new BusinessRuleException("لا يمكن الاعتماد في الحالة: " + imp.getStatus());
        }
        long critical = lineRepository.countByImportIdAndReviewStatus(
                importId, PriceListImportLine.ReviewStatus.NEEDS_REVIEW);
        if (critical > 0) {
            throw new BusinessRuleException(
                    "لا يمكن اعتماد المتبقي: ما زال هناك " + critical + " سطرًا في طابور المراجعة الحرج");
        }
        List<PriceListImportLine> bulk = lineRepository.findByImportIdAndReviewStatus(
                importId, PriceListImportLine.ReviewStatus.PENDING_BULK);
        int approved = 0;
        for (PriceListImportLine line : bulk) {
            if (line.getSuggestedCategoryId() == null) {
                // defense-in-depth: banding should have flagged these
                throw new BusinessRuleException(
                        "سطر بدون تصنيف معتمد في النظام (id=" + line.getId() + ") — يتطلب مراجعة فردية");
            }
            approveLine(line, line.getSuggestedCategoryId(), line.getMatchedServiceId(),
                    line.getRawPrice(), null, reviewer, PriceListImportLine.ApprovalMode.BULK_REMAINING);
            lineRepository.save(line);
            approved++;
        }
        log.info("[MCE] Import #{}: Approve Remaining by {} → {} lines (BULK_REMAINING)",
                importId, reviewer, approved);
        afterDecisions(imp);
        return approved;
    }

    /**
     * MC-4A (design review §3, D1): «إنهاء المراجعة واعتماد الموثوق (N)» —
     * ONE user action that performs, in order:
     *  1. Approve Remaining (the audited A5 bulk act, verbatim),
     *  2. auto-creates the DRAFT version (the old "create version" click had
     *     zero decision content),
     *  3. runs the A10 financial validation (inside draft creation).
     * Returns the new version id for navigation to the report. If no contract
     * can be resolved, review still completes and versionId is null.
     */
    @Transactional
    public FinishReviewResult finishReview(Long importId, Long contractIdParam, String reviewer) {
        PriceListImport imp = getImport(importId);
        long critical = lineRepository.countByImportIdAndReviewStatus(
                importId, PriceListImportLine.ReviewStatus.NEEDS_REVIEW);
        if (critical > 0) {
            throw new BusinessRuleException(
                    "لا يمكن إنهاء المراجعة: ما زال هناك " + critical + " حالة بحاجة قرار");
        }
        long pendingBulk = lineRepository.countByImportIdAndReviewStatus(
                importId, PriceListImportLine.ReviewStatus.PENDING_BULK);
        int bulkApproved = pendingBulk > 0 ? approveRemaining(importId, reviewer) : 0;
        // approveRemaining()/afterDecisions() has now moved the import to REVIEW_COMPLETE

        Long contractId = imp.getContractId() != null ? imp.getContractId() : contractIdParam;
        if (contractId == null) {
            contractId = contractRepository.findAll().stream()
                    .filter(c -> c.getProvider() != null
                            && imp.getProviderId().equals(c.getProvider().getId()))
                    .filter(c -> "ACTIVE".equals(String.valueOf(c.getStatus())))
                    .map(c -> c.getId())
                    .findFirst().orElse(null);
        }
        Long versionId = null;
        if (contractId != null) {
            versionId = versionService.createDraftFromImport(importId, contractId, reviewer).getId();
        }
        log.info("[MCE] Import #{}: review finished by {} (bulk-approved {}), version={}",
                importId, reviewer, bulkApproved, versionId);
        return new FinishReviewResult(bulkApproved, versionId, contractId);
    }

    public record FinishReviewResult(int bulkApproved, Long versionId, Long contractId) {
    }

    // ── internals ───────────────────────────────────────────────────────────

    private void applyDecision(PriceListImportLine line, ReviewDecisionDto decision,
                               String reviewer, PriceListImportLine.ApprovalMode mode) {
        switch (decision.getAction()) {
            case APPROVE -> {
                Long categoryId = decision.getCategoryId() != null
                        ? decision.getCategoryId()
                        : line.getSuggestedCategoryId();
                if (categoryId == null) {
                    throw new ValidationException(
                            "التصنيف مطلوب — اقتراح المحرك لم يُحل لتصنيف معتمد في النظام، اختر تصنيفًا");
                }
                approveLine(line, categoryId, decision.getServiceId(),
                        decision.getPrice() != null ? decision.getPrice() : line.getRawPrice(),
                        decision.getNote(), reviewer, mode);
            }
            case REJECT -> {
                line.setReviewStatus(PriceListImportLine.ReviewStatus.REJECTED);
                line.setReviewerNote(decision.getNote());
                line.setApprovedBy(reviewer);
                line.setApprovedAt(LocalDateTime.now());
                line.setApprovalMode(mode);
            }
        }
    }

    private void approveLine(PriceListImportLine line, Long categoryId, Long serviceId,
                             java.math.BigDecimal finalPrice, String note, String reviewer,
                             PriceListImportLine.ApprovalMode mode) {
        // Learning loop: link/create catalog service + aliases + history (A6)
        CatalogKnowledgeService.KnowledgeResult result =
                knowledge.recordApproval(line, categoryId, serviceId, reviewer);

        line.setFinalServiceId(result.getServiceId());
        line.setFinalCategoryId(categoryId);
        line.setFinalPrice(finalPrice);
        line.setReviewStatus(PriceListImportLine.ReviewStatus.APPROVED);
        line.setReviewerNote(note);
        line.setApprovedBy(reviewer);
        line.setApprovedAt(LocalDateTime.now());
        line.setApprovalMode(mode);
    }

    /** Import status transitions + counters after any batch of decisions. */
    private void afterDecisions(PriceListImport imp) {
        long needsReview = lineRepository.countByImportIdAndReviewStatus(
                imp.getId(), PriceListImportLine.ReviewStatus.NEEDS_REVIEW);
        long pendingBulk = lineRepository.countByImportIdAndReviewStatus(
                imp.getId(), PriceListImportLine.ReviewStatus.PENDING_BULK);
        long approved = lineRepository.countByImportIdAndReviewStatus(
                imp.getId(), PriceListImportLine.ReviewStatus.APPROVED);
        long rejected = lineRepository.countByImportIdAndReviewStatus(
                imp.getId(), PriceListImportLine.ReviewStatus.REJECTED);

        imp.setApprovedCount((int) approved);
        imp.setRejectedCount((int) rejected);
        if (needsReview == 0 && pendingBulk == 0) {
            imp.setStatus(PriceListImport.Status.REVIEW_COMPLETE);
        } else if (imp.getStatus() == PriceListImport.Status.CLASSIFIED) {
            imp.setStatus(PriceListImport.Status.IN_REVIEW);
        }
        importRepository.save(imp);
    }

    private PriceListImport getImport(Long importId) {
        return importRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Import not found: " + importId));
    }
}
