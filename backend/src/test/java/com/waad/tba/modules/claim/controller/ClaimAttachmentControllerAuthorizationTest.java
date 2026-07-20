package com.waad.tba.modules.claim.controller;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimAttachment;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.service.ClaimAttachmentService;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.security.ProviderContextGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DOCUMENTS-IDOR-1: verifies claim attachment endpoints enforce provider-ownership,
 * not just role membership.
 */
class ClaimAttachmentControllerAuthorizationTest {

    @Mock
    private ClaimAttachmentService attachmentService;
    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ProviderContextGuard providerContextGuard;

    private ClaimAttachmentController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ClaimAttachmentController(attachmentService, claimRepository, providerContextGuard);
    }

    private Claim claimOwnedBy(Long claimId, Long providerId) {
        Claim claim = new Claim();
        claim.setId(claimId);
        claim.setProviderId(providerId);
        return claim;
    }

    @Test
    void providerCanDownloadOwnClaimAttachment() {
        Long claimId = 10L, attachmentId = 100L, providerId = 1L;
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claimOwnedBy(claimId, providerId)));
        doNothing().when(providerContextGuard).validateProviderAccess(providerId);

        ClaimAttachment attachment = new ClaimAttachment();
        Claim claim = claimOwnedBy(claimId, providerId);
        attachment.setClaim(claim);
        attachment.setFileType("application/pdf");
        attachment.setOriginalFileName("invoice.pdf");
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachment);
        when(attachmentService.downloadAttachment(attachmentId)).thenReturn(new byte[] {1, 2, 3});

        ResponseEntity<?> response = controller.downloadAttachment(claimId, attachmentId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(providerContextGuard).validateProviderAccess(providerId);
    }

    @Test
    void providerCannotDownloadOtherProvidersClaimAttachment() {
        Long claimId = 10L, attachmentId = 100L, otherProviderId = 2L;
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claimOwnedBy(claimId, otherProviderId)));
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى هذا المرفق."))
                .when(providerContextGuard).validateProviderAccess(otherProviderId);

        assertThrows(AccessDeniedException.class,
                () -> controller.downloadAttachment(claimId, attachmentId));

        // The attachment/download service must never be reached once ownership fails
        verify(attachmentService, never()).downloadAttachment(anyLong());
        verify(attachmentService, never()).getAttachment(anyLong());
    }

    @Test
    void attachmentListStaysScopedToOwnProvider() {
        Long claimId = 11L, providerId = 1L;
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claimOwnedBy(claimId, providerId)));
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى هذا المرفق."))
                .when(providerContextGuard).validateProviderAccess(providerId);

        assertThrows(AccessDeniedException.class, () -> controller.getClaimAttachments(claimId));
        verify(attachmentService, never()).getClaimAttachments(anyLong());
    }

    @Test
    void reviewerAndAdminAccessRemainsUnaffected() {
        Long claimId = 12L, attachmentId = 101L, providerId = 3L;
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claimOwnedBy(claimId, providerId)));
        // ProviderContextGuard#validateProviderAccess is a no-op for non-provider roles (admin/reviewer)
        doNothing().when(providerContextGuard).validateProviderAccess(providerId);
        when(attachmentService.getClaimAttachments(claimId)).thenReturn(List.of());

        ResponseEntity<?> response = controller.getClaimAttachments(claimId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void guessedClaimIdReturnsNotFoundWithoutLeakingOwnership() {
        Long claimId = 999L, attachmentId = 500L;
        when(claimRepository.findById(claimId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> controller.downloadAttachment(claimId, attachmentId));
        verify(providerContextGuard, never()).validateProviderAccess(any());
    }

    @Test
    void providerCannotDeleteOtherProvidersClaimAttachment() {
        Long claimId = 13L, attachmentId = 102L, otherProviderId = 4L;
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claimOwnedBy(claimId, otherProviderId)));
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى هذا المرفق."))
                .when(providerContextGuard).validateProviderAccess(otherProviderId);

        assertThrows(AccessDeniedException.class,
                () -> controller.deleteAttachment(claimId, attachmentId));
        verify(attachmentService, never()).deleteAttachment(anyLong());
    }
}
