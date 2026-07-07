package com.waad.tba.modules.claim.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.waad.tba.modules.claim.dto.MedicalReviewerProviderAssignmentsResponse;
import com.waad.tba.modules.claim.entity.MedicalReviewerProvider;
import com.waad.tba.modules.claim.repository.MedicalReviewerProviderRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.security.AuthorizationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalReviewerProviderAssignmentService {

    private final MedicalReviewerProviderRepository medicalReviewerProviderRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    @Transactional
    public MedicalReviewerProviderAssignmentsResponse getAssignments(Long reviewerId) {
        validateReviewerExists(reviewerId);
        List<Long> assignedProviderIds = medicalReviewerProviderRepository.findProviderIdsByReviewerId(reviewerId);
        return new MedicalReviewerProviderAssignmentsResponse(reviewerId, assignedProviderIds);
    }

    @Transactional
    public MedicalReviewerProviderAssignmentsResponse updateAssignments(Long reviewerId, List<Long> providerIds, HttpServletRequest request) {
        User reviewer = validateReviewerExists(reviewerId);

        Set<Long> requestedProviderIds = new HashSet<>();
        if (providerIds != null) {
            requestedProviderIds.addAll(providerIds.stream().filter(id -> id != null && id > 0).toList());
        }

        validateProvidersExist(requestedProviderIds);

        List<MedicalReviewerProvider> existingMappings = medicalReviewerProviderRepository.findByReviewerId(reviewerId);
        Map<Long, MedicalReviewerProvider> existingByProviderId = new HashMap<>();
        for (MedicalReviewerProvider mapping : existingMappings) {
            if (mapping.getProvider() != null && mapping.getProvider().getId() != null) {
                existingByProviderId.put(mapping.getProvider().getId(), mapping);
            }
        }

        User currentUser = authorizationService.getCurrentUser();
        String actorUsername = currentUser != null ? currentUser.getUsername() : "system";
        Long actorUserId = currentUser != null ? currentUser.getId() : null;

        List<MedicalReviewerProvider> toSave = new ArrayList<>();

        for (MedicalReviewerProvider mapping : existingMappings) {
            if (Boolean.TRUE.equals(mapping.getActive())) {
                mapping.deactivate();
                mapping.setUpdatedBy(actorUsername);
                toSave.add(mapping);
            }
        }

        for (Long providerId : requestedProviderIds) {
            MedicalReviewerProvider existing = existingByProviderId.get(providerId);
            if (existing != null) {
                existing.activate();
                existing.setUpdatedBy(actorUsername);
                toSave.add(existing);
                continue;
            }

            MedicalReviewerProvider newMapping = MedicalReviewerProvider.builder()
                    .reviewer(reviewer)
                    .provider(providerRepository.getReferenceById(providerId))
                    .active(true)
                    .createdBy(actorUsername)
                    .updatedBy(actorUsername)
                    .build();
            toSave.add(newMapping);
        }

        if (!toSave.isEmpty()) {
            medicalReviewerProviderRepository.saveAll(toSave);
        }

        String details = String.format(
                "Updated provider assignments for reviewerId=%d (%s). Assigned providers=%s",
                reviewerId,
                reviewer.getUsername(),
                requestedProviderIds
        );

        auditLogService.createAuditLog(
                "UPDATE_REVIEWER_PROVIDER_ASSIGNMENTS",
                "MedicalReviewerProvider",
                reviewerId,
                details,
                actorUserId,
                actorUsername,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );

        List<Long> assignedProviderIds = medicalReviewerProviderRepository.findProviderIdsByReviewerId(reviewerId);
        log.info("Updated reviewer-provider assignments: reviewerId={}, assignedCount={}", reviewerId, assignedProviderIds.size());

        return new MedicalReviewerProviderAssignmentsResponse(reviewerId, assignedProviderIds);
    }

    private User validateReviewerExists(Long reviewerId) {
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("Medical reviewer not found: " + reviewerId));

        boolean hasReviewerRole = "MEDICAL_REVIEWER".equals(reviewer.getUserType());

        if (!hasReviewerRole) {
            throw new IllegalArgumentException("User is not a medical reviewer: " + reviewerId);
        }

        return reviewer;
    }

    private void validateProvidersExist(Set<Long> providerIds) {
        for (Long providerId : providerIds) {
            if (!providerRepository.existsById(providerId)) {
                throw new IllegalArgumentException("Provider not found: " + providerId);
            }
        }
    }
}
