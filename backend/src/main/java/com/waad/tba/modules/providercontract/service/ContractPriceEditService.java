package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.ValidationException;
import com.waad.tba.modules.medicalclassification.engine.service.CatalogKnowledgeService;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceChangeAudit;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListVersion;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceChangeAuditRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListVersionRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.providercontract.dto.ContractPriceEditDtos.*;
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
import java.util.List;

/**
 * MC-4C simplified, version-less price editing (APPROVED 2026-07-12).
 *
 * Individual service edits update the ACTIVE price list in place and record a
 * mandatory {@link PriceChangeAudit} entry. They NEVER create a new
 * price-list version — a new version is created only by a full import or by
 * restoring an archived version (handled elsewhere).
 *
 * Safe by design: {@code ClaimLine} snapshots all its amounts at claim time,
 * so editing an active price never alters historical claim results.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContractPriceEditService {

    private final ProviderContractRepository contractRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;
    private final PriceChangeAuditRepository auditRepository;
    private final PriceListVersionRepository versionRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final MedicalServiceRepository serviceRepository;
    private final CatalogKnowledgeService knowledgeService;

    // ── price correction ─────────────────────────────────────────────────────

    @Transactional
    public ProviderContractPricingItem correctPrice(Long contractId, Long itemId,
                                                    PriceCorrectionRequest req, String user) {
        ProviderContractPricingItem item = requireActiveItem(contractId, itemId);
        if (req.newPrice() == null || req.newPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("السعر الجديد يجب أن يكون أكبر من صفر");
        }
        requireReason(req.reason());

        BigDecimal oldPrice = item.getContractPrice();
        String before = snapshot(item);
        item.setContractPrice(req.newPrice());
        pricingItemRepository.save(item);

        audit(contractId, item, PriceChangeAudit.ChangeType.PRICE_CORRECTION,
                oldPrice, req.newPrice(), before, snapshot(item), req.reason(), user);
        log.info("[MC-4C] Price correction: contract={}, item={}, {} → {} by {}",
                contractId, itemId, oldPrice, req.newPrice(), user);
        return item;
    }

    // ── add service ──────────────────────────────────────────────────────────

    @Transactional
    public ProviderContractPricingItem addService(Long contractId, AddServiceRequest req, String user) {
        ProviderContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found: " + contractId));
        requireOperationalContract(contract);
        if (req.price() == null || req.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("السعر يجب أن يكون أكبر من صفر");
        }
        if (req.serviceName() == null || req.serviceName().isBlank()) {
            throw new ValidationException("اسم الخدمة مطلوب");
        }
        if (req.categoryId() == null) {
            throw new ValidationException("التصنيف مطلوب");
        }
        requireReason(req.reason());

        MedicalCategory category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ValidationException("التصنيف غير موجود في النظام"));

        // Optional link to a catalog service (do NOT fail if lookup returns nothing).
        String serviceCode = req.serviceCode();
        String serviceName = req.serviceName();
        boolean linkedToCatalog = false;
        if (req.medicalServiceId() != null) {
            MedicalService svc = serviceRepository.findById(req.medicalServiceId()).orElse(null);
            if (svc != null) {
                if (serviceCode == null || serviceCode.isBlank()) {
                    serviceCode = svc.getCode();
                }
                linkedToCatalog = true;
            }
        }
        if (serviceCode == null || serviceCode.isBlank()) {
            serviceCode = "MAN-" + System.currentTimeMillis(); // provider-added, no catalog code
        }
        if (pricingItemRepository.existsByContractIdAndServiceCodeAndActiveTrue(contractId, serviceCode)) {
            throw new BusinessRuleException("توجد خدمة فعالة بنفس الكود في قائمة أسعار العقد");
        }

        Long activeVersionId = activeVersionId(contractId);
        ProviderContractPricingItem item = ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceCode(serviceCode)
                .serviceName(serviceName)
                .categoryName(category.getName())
                .medicalCategory(category)
                .basePrice(req.price())
                .contractPrice(req.price())
                .currency("LYD")
                .versionId(activeVersionId)
                .effectiveFrom(LocalDate.now())
                .active(true)
                .notes("MC-4C add-service by " + user)
                .createdBy(user)
                .build();
        item = pricingItemRepository.save(item);

        // MC-6 Lite learning loop: ONLY when the admin explicitly linked this
        // add to an existing catalog service is the provider's wording safe to
        // learn as a global alias (a deliberate human decision, same as a
        // review approval). An unlinked, ad-hoc provider-only addition never
        // touches global catalog knowledge — prevents dangerous learning from
        // unverified data.
        if (linkedToCatalog) {
            knowledgeService.recordAdminLink(req.medicalServiceId(), category.getId(), serviceName, user);
        }

        audit(contractId, item, PriceChangeAudit.ChangeType.ADD_SERVICE,
                null, req.price(), "absent", snapshot(item), req.reason(), user);
        log.info("[MC-4C] Add service: contract={}, item={}, code={}, price={}, linkedToCatalog={} by {}",
                contractId, item.getId(), serviceCode, req.price(), linkedToCatalog, user);
        return item;
    }

    // ── deactivate service ───────────────────────────────────────────────────

    @Transactional
    public ProviderContractPricingItem deactivateService(Long contractId, Long itemId,
                                                         DeactivateServiceRequest req, String user) {
        ProviderContractPricingItem item = requireActiveItem(contractId, itemId);
        requireReason(req.reason());

        String before = snapshot(item);
        item.setActive(false);
        pricingItemRepository.save(item);

        audit(contractId, item, PriceChangeAudit.ChangeType.DEACTIVATE_SERVICE,
                item.getContractPrice(), item.getContractPrice(), before, snapshot(item), req.reason(), user);
        log.info("[MC-4C] Deactivate service: contract={}, item={} by {}", contractId, itemId, user);
        return item;
    }

    @Transactional
    public ProviderContractPricingItem reactivateService(Long contractId, Long itemId,
                                                          ReactivateServiceRequest req, String user) {
        ProviderContractPricingItem item = requireInactiveItem(contractId, itemId);
        requireReason(req.reason());
        if (pricingItemRepository.existsByContractIdAndServiceCodeAndActiveTrue(contractId, item.getServiceCode())) {
            throw new BusinessRuleException("لا يمكن إعادة التفعيل لأن كود الخدمة مستخدم في بند فعال آخر");
        }

        String before = snapshot(item);
        item.setActive(true);
        pricingItemRepository.save(item);
        audit(contractId, item, PriceChangeAudit.ChangeType.REACTIVATE_SERVICE,
                item.getContractPrice(), item.getContractPrice(), before, snapshot(item), req.reason(), user);
        log.info("[MC-4C] Reactivate service: contract={}, item={} by {}", contractId, itemId, user);
        return item;
    }

    // ── classification / code correction ─────────────────────────────────────

    @Transactional
    public ProviderContractPricingItem correctClassification(Long contractId, Long itemId,
                                                             ClassificationCorrectionRequest req, String user) {
        ProviderContractPricingItem item = requireActiveItem(contractId, itemId);
        requireReason(req.reason());

        String before = snapshot(item);
        if (req.newServiceCode() != null && !req.newServiceCode().isBlank()) {
            String newServiceCode = req.newServiceCode().trim();
            pricingItemRepository.findByContractIdAndServiceCodeActiveTrue(contractId, newServiceCode)
                    .filter(existing -> !existing.getId().equals(item.getId()))
                    .ifPresent(existing -> {
                        throw new BusinessRuleException("توجد خدمة فعالة بنفس الكود في قائمة أسعار العقد");
                    });
            item.setServiceCode(newServiceCode);
        }
        if (req.newServiceName() != null && !req.newServiceName().isBlank()) {
            item.setServiceName(req.newServiceName().trim());
        }
        if (req.newCategoryId() != null) {
            MedicalCategory category = categoryRepository.findById(req.newCategoryId())
                    .orElseThrow(() -> new ValidationException("التصنيف غير موجود في النظام"));
            item.setMedicalCategory(category);
            item.setCategoryName(category.getName());
        }
        pricingItemRepository.save(item);

        audit(contractId, item, PriceChangeAudit.ChangeType.CLASSIFICATION_CORRECTION,
                item.getContractPrice(), item.getContractPrice(), before, snapshot(item), req.reason(), user);
        log.info("[MC-4C] Classification correction: contract={}, item={} by {}", contractId, itemId, user);
        return item;
    }

    // ── audit trail ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AuditEntry> auditTrail(Long contractId) {
        return auditRepository.findByContractIdOrderByIdDesc(contractId).stream()
                .map(a -> new AuditEntry(
                        a.getId(),
                        a.getProviderId(),
                        a.getChangeType() == null ? null : a.getChangeType().name(),
                        a.getServiceCode(), a.getServiceName(),
                        a.getOldPrice(), a.getNewPrice(),
                        a.getOldValue(), a.getNewValue(), a.getBeforeState(), a.getAfterState(),
                        a.getReason(), a.getChangedBy(), a.getCreatedAt()))
                .toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ProviderContractPricingItem requireActiveItem(Long contractId, Long itemId) {
        ProviderContractPricingItem item = pricingItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing item not found: " + itemId));
        Long itemContractId = item.getContract() != null ? item.getContract().getId() : null;
        if (!contractId.equals(itemContractId)) {
            throw new BusinessRuleException("هذا البند لا يخص هذا العقد");
        }
        if (Boolean.FALSE.equals(item.getActive())) {
            throw new BusinessRuleException("لا يمكن تعديل خدمة موقوفة");
        }
        requireOperationalContract(item.getContract());
        return item;
    }

    private ProviderContractPricingItem requireInactiveItem(Long contractId, Long itemId) {
        ProviderContractPricingItem item = pricingItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing item not found: " + itemId));
        Long itemContractId = item.getContract() != null ? item.getContract().getId() : null;
        if (!contractId.equals(itemContractId)) {
            throw new BusinessRuleException("هذا البند لا يخص هذا العقد");
        }
        if (Boolean.TRUE.equals(item.getActive())) {
            throw new BusinessRuleException("الخدمة فعالة بالفعل");
        }
        requireOperationalContract(item.getContract());
        return item;
    }

    private void requireOperationalContract(ProviderContract contract) {
        if (contract == null || !Boolean.TRUE.equals(contract.getActive()) || !contract.canModifyPricing()) {
            throw new BusinessRuleException("العقد غير صالح لتعديل الأسعار التشغيلية");
        }
        // Operational edits belong to an already-published active list. This
        // prevents accidental direct setup rows from being treated as edits.
        if (activeVersionId(contract.getId()) == null) {
            throw new BusinessRuleException("لا توجد قائمة أسعار منشورة وسارية لهذا العقد");
        }
    }

    private Long activeVersionId(Long contractId) {
        return versionRepository.findByContractIdAndStatus(contractId, PriceListVersion.Status.ACTIVE)
                .map(PriceListVersion::getId)
                .orElse(null);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("السبب مطلوب لكل تعديل");
        }
    }

    private void audit(Long contractId, ProviderContractPricingItem item,
                       PriceChangeAudit.ChangeType type, BigDecimal oldPrice, BigDecimal newPrice,
                       String beforeState, String afterState, String reason, String user) {
        auditRepository.save(PriceChangeAudit.builder()
                .contractId(contractId)
                .providerId(item.getContract().getProvider() == null ? null : item.getContract().getProvider().getId())
                .versionId(item.getVersionId())
                .pricingItemId(item.getId())
                .changeType(type)
                .serviceCode(item.getServiceCode())
                .serviceName(item.getServiceName())
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .oldValue(beforeState)
                .newValue(afterState)
                .beforeState(beforeState)
                .afterState(afterState)
                .reason(reason)
                .changedBy(user)
                .build());
    }

    private static String snapshot(ProviderContractPricingItem item) {
        String category = item.getMedicalCategory() != null ? item.getMedicalCategory().getName() : item.getCategoryName();
        Long categoryId = item.getMedicalCategory() == null ? null : item.getMedicalCategory().getId();
        return "price=" + item.getContractPrice()
                + ";basePrice=" + item.getBasePrice()
                + ";serviceCode=" + safe(item.getServiceCode())
                + ";serviceName=" + safe(item.getServiceName())
                + ";categoryId=" + categoryId
                + ";category=" + safe(category)
                + ";active=" + item.getActive()
                + ";currency=" + safe(item.getCurrency())
                + ";unit=" + safe(item.getUnit())
                + ";effectiveFrom=" + item.getEffectiveFrom()
                + ";effectiveTo=" + item.getEffectiveTo();
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace(";", ",").replace("=", ":");
    }
}
