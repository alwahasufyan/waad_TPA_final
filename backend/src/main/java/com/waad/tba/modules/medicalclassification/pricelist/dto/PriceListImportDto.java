package com.waad.tba.modules.medicalclassification.pricelist.dto;

import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImport;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Read model of one import (list + detail). Includes full provenance
 * (MC-1 owner condition #2).
 */
@Value
@Builder
public class PriceListImportDto {

    Long id;
    Long providerId;
    String providerName;
    Long contractId;
    String fileName;
    String fileHash;
    Long fileSizeBytes;
    String providerTypeHint;
    String status;

    Integer totalLines;
    Integer knownServices;
    Integer unknownServices;
    Integer lowConfidence;
    Integer duplicates;
    Integer approvedCount;
    Integer rejectedCount;

    String engineVersion;
    String fuzzEngine;
    String dictionaryVersion;
    Long executionMs;
    String thresholdConfig;

    String errorMessage;
    String uploadedBy;
    LocalDateTime uploadedAt;
    LocalDateTime processedAt;

    /** Version created from this import (MC-4A hub: one timeline, direct report link). */
    Long versionId;
    String versionStatus;

    public static PriceListImportDto from(PriceListImport imp, String providerName) {
        return from(imp, providerName, null, null);
    }

    public static PriceListImportDto from(PriceListImport imp, String providerName,
                                          Long versionId, String versionStatus) {
        return PriceListImportDto.builder()
                .id(imp.getId())
                .providerId(imp.getProviderId())
                .providerName(providerName)
                .contractId(imp.getContractId())
                .fileName(imp.getFileName())
                .fileHash(imp.getFileHash())
                .fileSizeBytes(imp.getFileSizeBytes())
                .providerTypeHint(imp.getProviderTypeHint())
                .status(imp.getStatus() == null ? null : imp.getStatus().name())
                .totalLines(imp.getTotalLines())
                .knownServices(imp.getKnownServices())
                .unknownServices(imp.getUnknownServices())
                .lowConfidence(imp.getLowConfidence())
                .duplicates(imp.getDuplicates())
                .approvedCount(imp.getApprovedCount())
                .rejectedCount(imp.getRejectedCount())
                .engineVersion(imp.getEngineVersion())
                .fuzzEngine(imp.getFuzzEngine())
                .dictionaryVersion(imp.getDictionaryVersion())
                .executionMs(imp.getExecutionMs())
                .thresholdConfig(imp.getThresholdConfig())
                .errorMessage(imp.getErrorMessage())
                .uploadedBy(imp.getUploadedBy())
                .uploadedAt(imp.getUploadedAt())
                .processedAt(imp.getProcessedAt())
                .versionId(versionId)
                .versionStatus(versionStatus)
                .build();
    }
}
