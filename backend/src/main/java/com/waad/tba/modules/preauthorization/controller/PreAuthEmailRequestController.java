package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.modules.preauthorization.entity.PreAuthEmailRequest;
import com.waad.tba.modules.preauthorization.repository.PreAuthEmailRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// BACKEND-RBAC-FIX-MISSING-AUTH-1: previously had no authorization check at
// all beyond the global anyRequest().authenticated(). Reads (list/view) are
// reviewer-workflow-relevant, so SUPER_ADMIN + MEDICAL_REVIEWER; deleting a
// raw inbound pre-authorization email record defaults to SUPER_ADMIN only
// (an audit-trail-relevant destructive action) — widen to include
// MEDICAL_REVIEWER only with an explicit product decision, not assumed here.
@RestController
@RequestMapping("/api/preauthorization/email-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN', 'MEDICAL_REVIEWER')")
public class PreAuthEmailRequestController {

    private final PreAuthEmailRequestRepository repository;

    @GetMapping
    public ResponseEntity<Page<PreAuthEmailRequest>> listRequests(
            @PageableDefault(size = 20, sort = "receivedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) Boolean processed) {

        if (processed != null) {
            return ResponseEntity.ok(repository.findByProcessed(processed, pageable));
        }
        return ResponseEntity.ok(repository.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreAuthEmailRequest> getRequest(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'WAAD_ADMIN')")
    public ResponseEntity<Void> deleteRequest(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
