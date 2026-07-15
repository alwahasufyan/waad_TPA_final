package com.waad.tba.modules.benefitpolicy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleImportPreviewDto;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/benefit-policies/{policyId}/rules/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BenefitPolicyRuleExcelController {

    private final BenefitPolicyRuleExcelService excelService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@PathVariable Long policyId) {
        byte[] bytes = excelService.generateTemplate(policyId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("waad-benefit-policy-" + policyId + "-rules.xlsx").build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BenefitPolicyRuleImportPreviewDto>> preview(
            @PathVariable Long policyId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(excelService.preview(policyId, file),
                "Import preview ready", "تمت معاينة الملف دون تعديل البيانات"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BenefitPolicyRuleImportPreviewDto>> apply(
            @PathVariable Long policyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("expectedHash") String expectedHash,
            Authentication authentication) {
        String actor = authentication == null ? "unknown" : authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(excelService.apply(policyId, file, expectedHash, actor),
                "Rules imported", "تم تطبيق قواعد المنافع بنجاح"));
    }
}
