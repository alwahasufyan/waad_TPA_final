package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.modules.preauthorization.api.PreAuthorizationApiMapper;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAttachment;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationAttachmentService;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationService;
import com.waad.tba.security.ProviderContextGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DOCUMENTS-IDOR-1: verifies pre-authorization attachment endpoints enforce
 * provider-ownership (via the parent PreAuthorization.providerId), not just role membership.
 */
class PreAuthorizationAttachmentAuthorizationTest {

    private PreAuthorizationService preAuthorizationService;
    private PreAuthorizationAttachmentService attachmentService;
    private PreAuthorizationApiMapper apiMapper;
    private PreAuthorizationRepository preAuthorizationRepository;
    private ProviderContextGuard providerContextGuard;
    private PreAuthorizationController controller;

    @BeforeEach
    void setUp() {
        preAuthorizationService = mock(PreAuthorizationService.class);
        attachmentService = mock(PreAuthorizationAttachmentService.class);
        apiMapper = mock(PreAuthorizationApiMapper.class);
        preAuthorizationRepository = mock(PreAuthorizationRepository.class);
        providerContextGuard = mock(ProviderContextGuard.class);
        controller = new PreAuthorizationController(
                preAuthorizationService, attachmentService, apiMapper,
                preAuthorizationRepository, providerContextGuard);
    }

    private PreAuthorization preAuthOwnedBy(Long id, Long providerId) {
        PreAuthorization preAuth = new PreAuthorization();
        preAuth.setId(id);
        preAuth.setProviderId(providerId);
        return preAuth;
    }

    private PreAuthorizationAttachment attachmentFor(Long preAuthorizationId) {
        PreAuthorizationAttachment attachment = new PreAuthorizationAttachment();
        attachment.setPreAuthorizationId(preAuthorizationId);
        attachment.setFileType("application/pdf");
        attachment.setOriginalFileName("preauth.pdf");
        return attachment;
    }

    @Test
    void providerCanDownloadOwnPreAuthorizationAttachment() {
        Long preAuthId = 30L, attachmentId = 300L, providerId = 1L;
        when(preAuthorizationRepository.findById(preAuthId)).thenReturn(Optional.of(preAuthOwnedBy(preAuthId, providerId)));
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachmentFor(preAuthId));
        when(attachmentService.downloadAttachment(attachmentId)).thenReturn(new byte[] {1, 2, 3});
        doNothing().when(providerContextGuard).validateProviderAccess(providerId);

        ResponseEntity<?> response = controller.downloadAttachment(preAuthId, attachmentId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(providerContextGuard).validateProviderAccess(providerId);
    }

    @Test
    void providerCannotDownloadOtherProvidersPreAuthorizationAttachment() {
        Long preAuthId = 30L, attachmentId = 300L, otherProviderId = 2L;
        when(preAuthorizationRepository.findById(preAuthId)).thenReturn(Optional.of(preAuthOwnedBy(preAuthId, otherProviderId)));
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachmentFor(preAuthId));
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى هذا المرفق."))
                .when(providerContextGuard).validateProviderAccess(otherProviderId);

        assertThrows(AccessDeniedException.class,
                () -> controller.downloadAttachment(preAuthId, attachmentId));
        verify(attachmentService, never()).downloadAttachment(anyLong());
    }

    @Test
    void preAuthorizationAttachmentListStaysScoped() {
        Long preAuthId = 31L, providerId = 1L;
        when(preAuthorizationRepository.findById(preAuthId)).thenReturn(Optional.of(preAuthOwnedBy(preAuthId, providerId)));
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى هذا المرفق."))
                .when(providerContextGuard).validateProviderAccess(providerId);

        assertThrows(AccessDeniedException.class, () -> controller.getAttachments(preAuthId));
        verify(attachmentService, never()).getAttachments(anyLong());
    }

    @Test
    void reviewerAndAdminAccessRemainsUnaffected() {
        Long preAuthId = 32L, providerId = 8L;
        when(preAuthorizationRepository.findById(preAuthId)).thenReturn(Optional.of(preAuthOwnedBy(preAuthId, providerId)));
        doNothing().when(providerContextGuard).validateProviderAccess(providerId);
        when(attachmentService.getAttachments(preAuthId)).thenReturn(List.of());

        ResponseEntity<?> response = controller.getAttachments(preAuthId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void guessedAttachmentIdSafeNotFoundWithoutLeakingCrossProviderExistence() {
        // Attachment exists, but belongs to a DIFFERENT pre-authorization than the one
        // guessed in the URL — must be treated as not-found, not 403 (which would leak
        // that the attachment id exists under some other pre-authorization).
        Long guessedPreAuthId = 999L, attachmentId = 301L, actualPreAuthId = 40L;
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachmentFor(actualPreAuthId));

        ResponseEntity<?> response = controller.downloadAttachment(guessedPreAuthId, attachmentId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(providerContextGuard, never()).validateProviderAccess(any());
        verify(preAuthorizationRepository, never()).findById(any());
    }
}
