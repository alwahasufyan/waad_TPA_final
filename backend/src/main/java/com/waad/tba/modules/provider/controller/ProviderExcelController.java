package com.waad.tba.modules.provider.controller;

import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto;
import com.waad.tba.modules.provider.service.ProviderExcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for Provider Excel Import
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
@Tag(name = "Providers - Excel Import", description = "Import healthcare providers from Excel files")
@PreAuthorize("isAuthenticated()")
public class ProviderExcelController {

    private final ProviderExcelService excelService;

    @PostMapping(value = "/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Import providers from Excel",
        description = "Upload an Excel file to bulk import/update healthcare providers"
    )
    public ResponseEntity<ExcelImportResultDto> importFromExcel(
            @RequestParam("file") MultipartFile file
    ) {
        log.info("[ProviderExcelController] Received Excel upload request: {}", file.getOriginalFilename());
        
        ExcelImportResultDto result = excelService.importFromExcel(file);
        
        return ResponseEntity.ok(result);
    }
}
