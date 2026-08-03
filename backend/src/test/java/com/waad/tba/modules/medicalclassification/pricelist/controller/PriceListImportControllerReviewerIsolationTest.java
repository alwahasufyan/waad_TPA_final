package com.waad.tba.modules.medicalclassification.pricelist.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.medicalclassification.engine.service.ClassificationEngineClient;
import com.waad.tba.modules.medicalclassification.pricelist.entity.PriceListImport;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportLineRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListImportRepository;
import com.waad.tba.modules.medicalclassification.pricelist.repository.PriceListVersionRepository;
import com.waad.tba.modules.medicalclassification.pricelist.service.ImportOrchestrationService;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * WAAD-PRICELIST-REVIEWER-ISOLATION-1: verifies every MEDICAL_REVIEWER-
 * accessible endpoint in PriceListImportController enforces the reviewer's
 * provider assignment — previously providerId was trusted as-is from the
 * request/path with no check at all, letting a reviewer upload, browse, or
 * cancel a price-list import for any provider, not just their own assigned
 * ones. MEDICAL_REVIEWER must not upload for an unassigned providerId.
 */
class PriceListImportControllerReviewerIsolationTest {

    @Mock
    private ImportOrchestrationService orchestrationService;
    @Mock
    private PriceListImportRepository importRepository;
    @Mock
    private PriceListImportLineRepository lineRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private ClassificationEngineClient engineClient;
    @Mock
    private PriceListVersionRepository versionRepository;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ReviewerProviderIsolationService reviewerIsolationService;
    @Mock
    private Authentication authentication;

    private PriceListImportController controller;

    private final User reviewer = User.builder().id(9L).username("reviewer").userType("MEDICAL_REVIEWER").build();
    private final User admin = User.builder().id(1L).username("admin").userType("SUPER_ADMIN").build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PriceListImportController(orchestrationService, importRepository, lineRepository,
                providerRepository, engineClient, versionRepository, authorizationService, reviewerIsolationService);
        when(versionRepository.findFirstBySourceImportIdOrderByIdDesc(anyLong())).thenReturn(Optional.empty());
    }

    private PriceListImport importFor(Long id, Long providerId) {
        return PriceListImport.builder().id(id).providerId(providerId).build();
    }

    // ── upload ──────────────────────────────────────────────────────────

    @Test
    void reviewerNotAssignedCannotUploadForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);
        MultipartFile file = new MockMultipartFile("file", "prices.xlsx", "application/vnd.ms-excel", new byte[] { 1 });

        assertThrows(AccessDeniedException.class,
                () -> controller.upload(51L, null, null, file, authentication));
        verify(orchestrationService, never()).createImport(any(), any(), any(), any(), any());
    }

    @Test
    void reviewerAssignedCanUploadForOwnProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);
        when(authentication.getName()).thenReturn("reviewer");
        when(orchestrationService.createImport(eq(1L), any(), any(), any(), eq("reviewer")))
                .thenReturn(importFor(100L, 1L));
        MultipartFile file = new MockMultipartFile("file", "prices.xlsx", "application/vnd.ms-excel", new byte[] { 1 });

        ResponseEntity<?> response = controller.upload(1L, null, null, file, authentication);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(orchestrationService).createImport(eq(1L), any(), any(), any(), eq("reviewer"));
    }

    @Test
    void superAdminBypassesUploadIsolationCheck() {
        when(authorizationService.getCurrentUser()).thenReturn(admin);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(admin, 51L);
        when(authentication.getName()).thenReturn("admin");
        when(orchestrationService.createImport(eq(51L), any(), any(), any(), eq("admin")))
                .thenReturn(importFor(200L, 51L));
        MultipartFile file = new MockMultipartFile("file", "prices.xlsx", "application/vnd.ms-excel", new byte[] { 1 });

        ResponseEntity<?> response = controller.upload(51L, null, null, file, authentication);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── list ────────────────────────────────────────────────────────────

    @Test
    void reviewerNotAssignedCannotListForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.list(51L, 0, 25));
        verify(importRepository, never()).findByProviderIdOrderByIdDesc(any(), any());
    }

    @Test
    void reviewerUnfilteredListIsScopedToAssignedProviders() {
        Pageable pageable = PageRequest.of(0, 25);
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(1L));
        when(importRepository.findByProviderIdInOrderByIdDesc(eq(List.of(1L)), any()))
                .thenReturn(new PageImpl<>(List.of(importFor(1L, 1L)), pageable, 1));

        ResponseEntity<ApiResponse<Page<?>>> response =
                (ResponseEntity) controller.list(null, 0, 25);

        assertThat(response.getBody().getData().getContent()).hasSize(1);
        verify(importRepository, never()).findAllByOrderByIdDesc(any());
    }

    @Test
    void reviewerWithNoAssignmentsGetsEmptyUnfilteredList() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of());

        ResponseEntity<ApiResponse<Page<?>>> response =
                (ResponseEntity) controller.list(null, 0, 25);

        assertThat(response.getBody().getData().getContent()).isEmpty();
        verify(importRepository, never()).findByProviderIdInOrderByIdDesc(any(), any());
    }

    @Test
    void superAdminUnfilteredListIsUnrestricted() {
        Pageable pageable = PageRequest.of(0, 25);
        when(authorizationService.getCurrentUser()).thenReturn(admin);
        when(reviewerIsolationService.isSubjectToIsolation(admin)).thenReturn(false);
        when(importRepository.findAllByOrderByIdDesc(any()))
                .thenReturn(new PageImpl<>(List.of(importFor(1L, 1L), importFor(2L, 51L)), pageable, 2));

        ResponseEntity<ApiResponse<Page<?>>> response =
                (ResponseEntity) controller.list(null, 0, 25);

        assertThat(response.getBody().getData().getContent()).hasSize(2);
    }

    // ── get / lines / cancel (id-based, fetch-then-validate) ─────────────

    @Test
    void reviewerNotAssignedCannotGetImportForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(importRepository.findById(100L)).thenReturn(Optional.of(importFor(100L, 51L)));
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.get(100L));
    }

    @Test
    void reviewerAssignedCanGetOwnImport() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(importRepository.findById(100L)).thenReturn(Optional.of(importFor(100L, 1L)));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);

        ResponseEntity<?> response = controller.get(100L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void reviewerNotAssignedCannotBrowseLinesForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(importRepository.findById(100L)).thenReturn(Optional.of(importFor(100L, 51L)));
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.lines(100L, null, 0, 50));
        verify(lineRepository, never()).findByImportIdOrderByRowNoAsc(any(), any());
    }

    @Test
    void reviewerNotAssignedCannotCancelImportForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(importRepository.findById(100L)).thenReturn(Optional.of(importFor(100L, 51L)));
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.cancel(100L, authentication));
        verify(orchestrationService, never()).cancel(any(), any());
    }

    @Test
    void reviewerAssignedCanCancelOwnImport() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(importRepository.findById(100L)).thenReturn(Optional.of(importFor(100L, 1L)));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);
        when(authentication.getName()).thenReturn("reviewer");
        when(orchestrationService.cancel(100L, "reviewer")).thenReturn(importFor(100L, 1L));

        ResponseEntity<?> response = controller.cancel(100L, authentication);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
