package com.waad.tba.modules.medicalclassification.pricelist.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.ValidationException;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImport;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImportLine;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListVersion;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListVersionRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceChangeAuditRepository;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceChangeAudit;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provider Price List Version — a FINANCIAL ARTIFACT (owner decision, MC-3):
 * fully auditable, immutable after activation, the permanent historical
 * reference for claims and settlements.
 *
 * Lifecycle: DRAFT (freely re-validated/discardable) → approve (on the A11
 * comparison report) → publish (A10 gate must be green) → ACTIVE.
 * Publishing INSERTS pricing-item rows (version-tagged) and deactivates the
 * previous version's rows — it never updates or deletes a published price.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceListVersionService {

    private final PriceListVersionRepository versionRepository;
    private final PriceListImportRepository importRepository;
    private final PriceListImportLineRepository lineRepository;
    private final ProviderContractRepository contractRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final MedicalServiceRepository serviceRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final FinancialValidationService validationService;
    private final PriceChangeAuditRepository auditRepository;

    // ── draft creation ───────────────────────────────────────────────────────

    @Transactional
    public PriceListVersion createPatchDraft(Long contractId, String user) {
        Long contractOwnerId = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"))
                .getProvider().getId();

        versionRepository.findByContractIdAndStatus(contractId, PriceListVersion.Status.DRAFT)
                .ifPresent(d -> {
                    throw new BusinessRuleException(
                            "يوجد بالفعل نسخة مسودة (Draft) لهذا العقد (v" + d.getVersionNo() + "). يجب اعتمادها أو حذفها أولًا.");
                });

        int nextNo = versionRepository.findMaxVersionNoByContractId(contractId).orElse(0) + 1;
        PriceListVersion v = PriceListVersion.builder()
                .providerId(contractOwnerId)
                .contractId(contractId)
                .sourceImportId(null)
                .versionNo(nextNo)
                .sourceType(PriceListVersion.SourceType.PATCH)
                .status(PriceListVersion.Status.DRAFT)
                .notes("MC-4C Exception Edit")
                .build();
        PriceListVersion saved = versionRepository.save(v);

        log.info("[MCE-PATCH] Created PATCH draft v{} for contract {}, by {}", nextNo, contractId, user);
        return saved;
    }

    @Transactional
    public PriceListVersion createDraftFromImport(Long importId, Long contractIdParam, String user) {
        PriceListImport imp = importRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Import not found: " + importId));
        if (imp.getStatus() != PriceListImport.Status.REVIEW_COMPLETE) {
            throw new BusinessRuleException(
                    "لا يمكن إنشاء نسخة إلا بعد اكتمال المراجعة (الحالة الحالية: " + imp.getStatus() + ")");
        }
        Long contractId = imp.getContractId() != null ? imp.getContractId() : contractIdParam;
        if (contractId == null) {
            throw new ValidationException("العقد مطلوب لإنشاء نسخة قائمة أسعار");
        }
        ProviderContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found: " + contractId));
        if (contract.getProvider() == null || !imp.getProviderId().equals(contract.getProvider().getId())) {
            throw new BusinessRuleException("العقد لا يخص مرفق هذا الاستيراد");
        }
        boolean draftExists = versionRepository.findByContractIdOrderByVersionNoDesc(contractId).stream()
                .anyMatch(v -> v.getStatus() == PriceListVersion.Status.DRAFT);
        if (draftExists) {
            throw new BusinessRuleException("توجد نسخة مسودة قائمة لهذا العقد — انشرها أو أرشفها أولًا");
        }
        long approved = lineRepository.countByImportIdAndReviewStatus(
                importId, PriceListImportLine.ReviewStatus.APPROVED);
        if (approved == 0) {
            throw new BusinessRuleException("لا توجد أسطر معتمدة في هذا الاستيراد");
        }
        if (imp.getContractId() == null) {
            imp.setContractId(contractId);
            importRepository.save(imp);
        }

        int nextNo = versionRepository.findByContractIdOrderByVersionNoDesc(contractId).stream()
                .findFirst().map(v -> v.getVersionNo() + 1).orElse(1);

        PriceListVersion version = versionRepository.save(PriceListVersion.builder()
                .providerId(imp.getProviderId())
                .contractId(contractId)
                .versionNo(nextNo)
                .status(PriceListVersion.Status.DRAFT)
                .sourceImportId(importId)
                .notes("Created from import #" + importId + " by " + user)
                .build());

        // A10: validation runs immediately so the gate state is known up front
        validationService.validate(version.getId(), user);
        log.info("[MCE] Version #{} (v{}) DRAFT created from import #{} by {}",
                version.getId(), nextNo, importId, user);
        return version;
    }

    // ── approval + publish (the financial acts) ─────────────────────────────

    @Transactional
    public PriceListVersion approve(Long versionId, String user) {
        PriceListVersion version = getVersion(versionId);
        requireDraft(version);
        version.setApprovedBy(user);
        version.setApprovedAt(LocalDateTime.now());
        log.info("[MCE] Version #{} approved by {} (on the comparison report)", versionId, user);
        return versionRepository.save(version);
    }

    @Transactional
    public PriceListVersion publish(Long versionId, String user) {
        PriceListVersion version = getVersion(versionId);
        requireDraft(version);
        if (version.getApprovedBy() == null) {
            throw new BusinessRuleException("النسخة غير معتمدة — الاعتماد يتم على تقرير المقارنة أولًا");
        }
        // A10 gate: re-validate NOW (prices may have been fixed since), then check
        validationService.validate(versionId, user);
        FinancialValidationService.GateState gate = validationService.gateState(versionId);
        if (!gate.isOpen()) {
            throw new BusinessRuleException("بوابة النشر مغلقة: "
                    + gate.getOpenBlockers() + " مانع و " + gate.getOpenWarnings()
                    + " تحذير غير مُعالج — عالج الموانع وحُل أو أعفِ التحذيرات");
        }

        PriceListImport imp = importRepository.findById(version.getSourceImportId()).orElseThrow();
        List<PriceListImportLine> approvedLines = lineRepository.findByImportIdAndReviewStatus(
                imp.getId(), PriceListImportLine.ReviewStatus.APPROVED);

        ProviderContract contract = contractRepository.findById(version.getContractId()).orElseThrow();
        Map<Long, MedicalService> services = serviceRepository.findAllById(
                        approvedLines.stream().map(PriceListImportLine::getFinalServiceId)
                                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MedicalService::getId, s -> s));
        Map<Long, MedicalCategory> categories = categoryRepository.findAllById(
                        approvedLines.stream().map(PriceListImportLine::getFinalCategoryId)
                                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MedicalCategory::getId, c -> c));

        // 1) INSERT the new version's rows (never update old ones)
        List<ProviderContractPricingItem> newItems = new ArrayList<>(approvedLines.size());
        for (PriceListImportLine line : approvedLines) {
            if (line.getFinalCategoryId() == null || line.getFinalPrice() == null) {
                throw new BusinessRuleException(
                        "سطر معتمد بدون تصنيف/سعر نهائي (id=" + line.getId() + ") — لا يمكن النشر");
            }
            MedicalService svc = services.get(line.getFinalServiceId());
            MedicalCategory cat = categories.get(line.getFinalCategoryId());
            ProviderContractPricingItem item = ProviderContractPricingItem.builder()
                    .contract(contract)
                    .serviceCode(svc != null ? svc.getCode() : line.getRawCode())
                    .serviceName(line.getRawName())
                    .categoryName(cat != null ? cat.getName() : line.getSuggestedMainCategory())
                    .medicalCategory(cat)
                    .contractPrice(line.getFinalPrice())
                    .basePrice(line.getFinalPrice())
                    .active(true)
                    .effectiveFrom(LocalDate.now())
                    .notes("MCE v" + version.getVersionNo() + " · import #" + imp.getId()
                            + " · line " + line.getId())
                    .createdBy(user)
                    .versionId(version.getId())
                    .build();
            newItems.add(item);
        }

        // 2) supersede the previous ACTIVE version + deactivate (not delete) its rows
        versionRepository.findByContractIdAndStatus(version.getContractId(), PriceListVersion.Status.ACTIVE)
                .ifPresent(prev -> {
                    prev.setStatus(PriceListVersion.Status.SUPERSEDED);
                    prev.setEffectiveTo(LocalDate.now());
                    // flush NOW: the one-ACTIVE-per-contract partial unique index
                    // must see the supersede before the new version turns ACTIVE
                    versionRepository.saveAndFlush(prev);
                    List<ProviderContractPricingItem> oldItems =
                            pricingItemRepository.findByContractIdAndActiveTrue(version.getContractId());
                    oldItems.forEach(i -> i.setActive(false));
                    pricingItemRepository.saveAll(oldItems);
                    log.info("[MCE] Version v{} superseded ({} rows deactivated, none deleted)",
                            prev.getVersionNo(), oldItems.size());
                });

        pricingItemRepository.saveAll(newItems);

        version.setStatus(PriceListVersion.Status.ACTIVE);
        version.setEffectiveFrom(LocalDate.now());
        version.setPublishedBy(user);
        version.setPublishedAt(LocalDateTime.now());
        versionRepository.save(version);

        imp.setStatus(PriceListImport.Status.PUBLISHED);
        importRepository.save(imp);

        log.info("[MCE] Version #{} (v{}) PUBLISHED by {}: {} pricing rows inserted on contract {}",
                versionId, version.getVersionNo(), user, newItems.size(), version.getContractId());
        return version;
    }

    /** Blocker-fix path: adjust an approved line's final price while the version is still DRAFT. */
    @Transactional
    public PriceListImportLine fixLinePrice(Long importId, Long lineId, BigDecimal newPrice,
                                            String note, String user) {
        PriceListImport imp = importRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Import not found: " + importId));
        if (imp.getStatus() == PriceListImport.Status.PUBLISHED) {
            throw new BusinessRuleException("النسخة منشورة — الأسعار المنشورة غير قابلة للتغيير (كيان مالي)");
        }
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("السعر يجب أن يكون أكبر من صفر");
        }
        PriceListImportLine line = lineRepository.findById(lineId)
                .filter(l -> importId.equals(l.getImportId()))
                .orElseThrow(() -> new ResourceNotFoundException("Line not found: " + lineId));
        if (line.getReviewStatus() != PriceListImportLine.ReviewStatus.APPROVED) {
            throw new BusinessRuleException("تعديل السعر متاح للأسطر المعتمدة فقط");
        }
        String audit = "تعديل سعر: " + line.getFinalPrice() + " → " + newPrice + " بواسطة " + user
                + (note != null && !note.isBlank() ? " — " + note : "");
        line.setFinalPrice(newPrice);
        line.setReviewerNote(line.getReviewerNote() == null ? audit : line.getReviewerNote() + " | " + audit);
        return lineRepository.save(line);
    }

    @Transactional
    public PriceListVersion archiveDraft(Long versionId, String user) {
        PriceListVersion version = getVersion(versionId);
        requireDraft(version);
        version.setStatus(PriceListVersion.Status.ARCHIVED);
        version.setNotes((version.getNotes() == null ? "" : version.getNotes() + " | ")
                + "Archived by " + user);
        return versionRepository.save(version);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    public PriceListVersion getVersion(Long versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionId));
    }

    @Transactional
    public void recordPriceChange(Long versionId, Long pricingItemId, java.math.BigDecimal newPrice, String reason, String user) {
        PriceListVersion version = getVersion(versionId);
        requireDraft(version);
        if (version.getSourceType() != PriceListVersion.SourceType.PATCH) {
            throw new BusinessRuleException("هذا الإجراء مسموح فقط لنسخ التعديل الاستثنائي (PATCH)");
        }

        ProviderContractPricingItem item = pricingItemRepository.findById(pricingItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing item not found"));

        if (!item.getContract().getId().equals(version.getContractId())) {
            throw new BusinessRuleException("عنصر التسعير لا ينتمي لهذا العقد");
        }

        PriceChangeAudit audit = PriceChangeAudit.builder()
                .contractId(version.getContractId())
                .versionId(version.getId())
                .pricingItemId(item.getId())
                .changeType(PriceChangeAudit.ChangeType.PRICE_EDIT)
                .serviceCode(item.getServiceCode())
                .serviceName(item.getServiceName())
                .oldPrice(item.getContractPrice())
                .newPrice(newPrice)
                .reason(reason)
                .changedBy(user)
                .build();
        
        auditRepository.save(audit);
        
        item.setContractPrice(newPrice);
        item.setVersionId(version.getId());
        pricingItemRepository.save(item);
        
        log.info("[MCE-PATCH] Recorded price change for item {} from {} to {} by {}", pricingItemId, audit.getOldPrice(), newPrice, user);
    }

    @Transactional
    public PriceListVersion applyPatchDraft(Long versionId, String user) {
        PriceListVersion version = getVersion(versionId);
        requireDraft(version);
        if (version.getSourceType() != PriceListVersion.SourceType.PATCH) {
            throw new BusinessRuleException("هذا الإجراء مسموح فقط لنسخ التعديل الاستثنائي (PATCH)");
        }

        // Active immediately (no comparison report required for patches)
        version.setStatus(PriceListVersion.Status.ACTIVE);
        version.setEffectiveFrom(java.time.LocalDate.now());
        version.setPublishedBy(user);
        version.setPublishedAt(java.time.LocalDateTime.now());
        
        return versionRepository.save(version);
    }

    private static void requireDraft(PriceListVersion version) {
        if (version.getStatus() != PriceListVersion.Status.DRAFT) {
            throw new BusinessRuleException(
                    "النسخة " + version.getStatus() + " — الكيان المالي غير قابل للتغيير بعد التفعيل");
        }
    }

}
