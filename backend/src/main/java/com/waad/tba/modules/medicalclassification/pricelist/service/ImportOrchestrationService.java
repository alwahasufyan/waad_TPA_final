package com.waad.tba.modules.medicalclassification.pricelist.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.exception.ValidationException;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImport;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Upload + registration of provider price-list imports (MC-1).
 *
 * Owner conditions enforced here:
 *  #1 Idempotency by file hash — SHA-256 computed server-side; a non-terminal
 *     import with the same (provider, hash) rejects the upload and points to
 *     the existing import. FAILED/CANCELLED never block a retry.
 *  #2 Provenance — file size + hash stored at upload; engine/dictionary
 *     versions and execution time stored by {@link ImportProcessingService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportOrchestrationService {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024; // 25 MB
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("xlsx", "xls", "csv", "pdf", "pptx");
    private static final Set<String> ALLOWED_HINTS = Set.of("dental", "optics", "physio");
    private static final Set<PriceListImport.Status> TERMINAL_FOR_IDEMPOTENCY =
            EnumSet.of(PriceListImport.Status.FAILED, PriceListImport.Status.CANCELLED);
    private static final Set<PriceListImport.Status> IN_FLIGHT =
            EnumSet.of(PriceListImport.Status.UPLOADED, PriceListImport.Status.PROCESSING);

    private final PriceListImportRepository importRepository;
    private final ProviderRepository providerRepository;
    private final ProviderContractRepository contractRepository;
    private final ImportProcessingService processingService;

    /**
     * Module-local storage (same base dir as the shared file service). The
     * shared LocalFileStorageService deliberately whitelists only PDF/images/
     * DICOM — price lists (xlsx/csv/pptx) are stored here instead of loosening
     * that global security policy. This module enforces its own extension,
     * size and hash validations above.
     */
    @Value("${file.storage.local.base-dir:./storage/uploads}")
    private String storageBaseDir;

    @Transactional
    public PriceListImport createImport(Long providerId, Long contractId, String hint,
                                        MultipartFile file, String username) {
        // ── validations ──────────────────────────────────────────────────────
        if (file == null || file.isEmpty()) {
            throw new ValidationException("الملف مطلوب / file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("حجم الملف يتجاوز الحد المسموح (25MB)");
        }
        String originalName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ValidationException(
                    "صيغة غير مدعومة: " + ext + " — المسموح: xlsx, xls, csv, pdf, pptx");
        }
        if (hint != null && !hint.isBlank() && !ALLOWED_HINTS.contains(hint)) {
            throw new ValidationException("تلميح نوع المرفق غير صحيح: " + hint);
        }
        providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + providerId));
        if (contractId != null) {
            var contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new ResourceNotFoundException("Contract not found: " + contractId));
            if (contract.getProvider() == null || !providerId.equals(contract.getProvider().getId())) {
                throw new BusinessRuleException("العقد المحدد لا يخص هذا المرفق");
            }
            // R7: one in-flight import per contract
            if (importRepository.existsByContractIdAndStatusIn(contractId, IN_FLIGHT)) {
                throw new BusinessRuleException(
                        "يوجد استيراد قيد المعالجة لهذا العقد — انتظر اكتماله أو ألغه أولًا");
            }
        }

        // ── idempotency by SHA-256 (owner condition #1) ─────────────────────
        String fileHash = sha256(file);
        importRepository.findActiveDuplicate(providerId, fileHash, TERMINAL_FOR_IDEMPOTENCY)
                .ifPresent(existing -> {
                    throw new BusinessRuleException(
                            "هذا الملف مستورد مسبقًا لنفس المرفق (استيراد رقم " + existing.getId()
                                    + "، الحالة: " + existing.getStatus() + ") — لن يُرفع مرة أخرى");
                });

        // ── store + register ────────────────────────────────────────────────
        Path storedPath = storeFile(file, ext);

        PriceListImport imp = PriceListImport.builder()
                .providerId(providerId)
                .contractId(contractId)
                .fileName(originalName)
                .fileHash(fileHash)
                .fileStoragePath(storedPath.toString())
                .fileSizeBytes(file.getSize())
                .providerTypeHint((hint == null || hint.isBlank()) ? null : hint)
                .status(PriceListImport.Status.UPLOADED)
                .uploadedBy(username)
                .build();
        imp = importRepository.save(imp);

        log.info("[MCE] Import #{} registered: provider={}, contract={}, file={} ({} bytes, sha256={})",
                imp.getId(), providerId, contractId, originalName, file.getSize(), fileHash);

        // async classification — AFTER COMMIT, or the async thread may not yet
        // see the import row (verified race: import #3 failed "No value present")
        final Long newImportId = imp.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        processingService.processAsync(newImportId);
                    }
                });
        return imp;
    }

    @Transactional
    public PriceListImport cancel(Long importId, String username) {
        PriceListImport imp = importRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Import not found: " + importId));
        if (imp.getStatus() != PriceListImport.Status.UPLOADED
                && imp.getStatus() != PriceListImport.Status.CLASSIFIED
                && imp.getStatus() != PriceListImport.Status.IN_REVIEW) {
            throw new BusinessRuleException(
                    "لا يمكن إلغاء استيراد في الحالة: " + imp.getStatus());
        }
        imp.setStatus(PriceListImport.Status.CANCELLED);
        imp.setErrorMessage("Cancelled by " + username);
        return importRepository.save(imp);
    }

    /** Stores under {base}/classification/{uuid}.{ext} — ASCII-safe absolute path for the engine. */
    private Path storeFile(MultipartFile file, String ext) {
        try {
            Path dir = Paths.get(storageBaseDir).toAbsolutePath().normalize().resolve("classification");
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + ext);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded price list: " + e.getMessage(), e);
        }
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var in = file.getInputStream()) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | java.io.IOException e) {
            throw new IllegalStateException("Failed to hash uploaded file", e);
        }
    }
}
