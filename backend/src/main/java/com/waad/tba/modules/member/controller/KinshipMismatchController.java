package com.waad.tba.modules.member.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.member.dto.KinshipMismatchDto;
import com.waad.tba.modules.member.dto.KinshipMismatchFixRequest;
import com.waad.tba.modules.member.service.KinshipMismatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// BACKEND-RBAC-FIX-MISSING-AUTH-1: maintenance tool that alters member
// relationship/verification state — previously had no authorization check
// at all beyond the global anyRequest().authenticated(). Restricted to
// SUPER_ADMIN.
@RestController
@RequestMapping("/api/v1/system-settings/kinship-mismatches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN')")
public class KinshipMismatchController {

    private final KinshipMismatchService kinshipMismatchService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<KinshipMismatchDto>>> getMismatches() {
        List<KinshipMismatchDto> mismatches = kinshipMismatchService.findMismatches();
        return ResponseEntity.ok(ApiResponse.success(mismatches));
    }

    @PostMapping("/{id}/fix")
    public ResponseEntity<ApiResponse<Void>> fixMismatch(@PathVariable Long id, @RequestBody KinshipMismatchFixRequest request) {
        kinshipMismatchService.fixMismatch(id, request);
        return ResponseEntity.ok(ApiResponse.success("تم إصلاح بيانات القرابة بنجاح", null));
    }

    @PostMapping("/{id}/ignore")
    public ResponseEntity<ApiResponse<Void>> ignoreMismatch(@PathVariable Long id) {
        kinshipMismatchService.ignoreMismatch(id);
        return ResponseEntity.ok(ApiResponse.success("تم تأكيد صحة البيانات بنجاح", null));
    }

    @PostMapping("/bulk-fix")
    public ResponseEntity<ApiResponse<Void>> fixMismatchesBulk(@RequestBody com.waad.tba.modules.member.dto.KinshipMismatchBulkFixRequest request) {
        kinshipMismatchService.fixMismatchesBulk(request);
        return ResponseEntity.ok(ApiResponse.success("تم الإصلاح الجماعي بنجاح", null));
    }

    @PostMapping("/bulk-ignore")
    public ResponseEntity<ApiResponse<Void>> ignoreMismatchesBulk(@RequestBody List<Long> memberIds) {
        kinshipMismatchService.ignoreMismatchesBulk(memberIds);
        return ResponseEntity.ok(ApiResponse.success("تم تجاهل الأخطاء جماعياً بنجاح", null));
    }
}
