package com.waad.tba.modules.visit.controller;

import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitAttachment;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.modules.visit.service.VisitAttachmentService;
import com.waad.tba.security.ProviderContextGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DOCUMENTS-IDOR-1: verifies visit attachment endpoints enforce provider-ownership,
 * not just role membership.
 */
class VisitAttachmentControllerAuthorizationTest {

    private VisitAttachmentService attachmentService;
    private VisitRepository visitRepository;
    private ProviderContextGuard providerContextGuard;
    private VisitAttachmentController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        attachmentService = mock(VisitAttachmentService.class);
        visitRepository = mock(VisitRepository.class);
        providerContextGuard = mock(ProviderContextGuard.class);
        controller = new VisitAttachmentController(attachmentService, visitRepository, providerContextGuard);
    }

    private Visit visitOwnedBy(Long visitId, Long providerId) {
        Visit visit = new Visit();
        visit.setId(visitId);
        visit.setProviderId(providerId);
        return visit;
    }

    private VisitAttachment attachmentFor(Visit visit) {
        VisitAttachment attachment = new VisitAttachment();
        attachment.setVisit(visit);
        attachment.setFileType("application/pdf");
        attachment.setOriginalFileName("report.pdf");
        return attachment;
    }

    @Test
    void providerCanDownloadOwnVisitAttachment() {
        Long visitId = 20L, attachmentId = 200L, providerId = 1L;
        Visit visit = visitOwnedBy(visitId, providerId);
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachmentFor(visit));
        when(attachmentService.downloadAttachment(attachmentId)).thenReturn(new byte[] {1, 2, 3});
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        doNothing().when(providerContextGuard).validateProviderAccess(providerId);

        ResponseEntity<?> response = controller.downloadAttachment(visitId, attachmentId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(providerContextGuard).validateProviderAccess(providerId);
    }

    @Test
    void providerCannotDownloadOtherProvidersVisitAttachment() {
        Long visitId = 20L, attachmentId = 200L, otherProviderId = 2L;
        Visit visit = visitOwnedBy(visitId, otherProviderId);
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachmentFor(visit));
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى هذا المرفق."))
                .when(providerContextGuard).validateProviderAccess(otherProviderId);

        assertThrows(AccessDeniedException.class,
                () -> controller.downloadAttachment(visitId, attachmentId));
        verify(attachmentService, never()).downloadAttachment(anyLong());
    }

    @Test
    void visitDocumentListStaysScoped() {
        Long visitId = 21L, providerId = 1L;
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visitOwnedBy(visitId, providerId)));
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى هذا المرفق."))
                .when(providerContextGuard).validateProviderAccess(providerId);

        assertThrows(AccessDeniedException.class, () -> controller.getVisitAttachments(visitId));
        verify(attachmentService, never()).getVisitAttachments(anyLong());
    }

    @Test
    void reviewerAndAdminAccessRemainsUnaffected() {
        Long visitId = 22L, providerId = 5L;
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visitOwnedBy(visitId, providerId)));
        doNothing().when(providerContextGuard).validateProviderAccess(providerId);
        when(attachmentService.getVisitAttachments(visitId)).thenReturn(List.of());

        ResponseEntity<?> response = controller.getVisitAttachments(visitId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void guessedAttachmentIdReturnsSafeNotFoundWithoutLeaking() {
        Long visitId = 23L, attachmentId = 900L;
        when(attachmentService.getAttachment(attachmentId))
                .thenThrow(new RuntimeException("Attachment not found with ID: " + attachmentId));

        ResponseEntity<?> response = controller.downloadAttachment(visitId, attachmentId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(providerContextGuard, never()).validateProviderAccess(any());
    }

    @Test
    void providerCannotDeleteOtherProvidersVisitAttachment() {
        Long visitId = 24L, attachmentId = 201L, otherProviderId = 6L;
        Visit visit = visitOwnedBy(visitId, otherProviderId);
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachmentFor(visit));
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى هذا المرفق."))
                .when(providerContextGuard).validateProviderAccess(otherProviderId);

        assertThrows(AccessDeniedException.class,
                () -> controller.deleteAttachment(visitId, attachmentId));
        verify(attachmentService, never()).deleteAttachment(anyLong());
    }

    @Test
    void providerCanDeleteOwnVisitAttachmentFollowingExistingRules() {
        Long visitId = 25L, attachmentId = 202L, providerId = 7L;
        Visit visit = visitOwnedBy(visitId, providerId);
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachmentFor(visit));
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        doNothing().when(providerContextGuard).validateProviderAccess(providerId);
        doNothing().when(attachmentService).deleteAttachment(attachmentId);

        ResponseEntity<String> response = controller.deleteAttachment(visitId, attachmentId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(attachmentService).deleteAttachment(attachmentId);
    }
}
