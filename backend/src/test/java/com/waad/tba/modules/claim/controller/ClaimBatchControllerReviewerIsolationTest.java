package com.waad.tba.modules.claim.controller;

import com.waad.tba.modules.claim.entity.ClaimBatch;
import com.waad.tba.modules.claim.service.ClaimBatchService;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * WAAD-RBAC-REVIEWER-BATCH-SCOPING-5: verifies claim-batch endpoints enforce
 * reviewer-provider isolation server-side — previously providerId was
 * trusted as-is and the search endpoint fell through to "every provider",
 * with only the frontend (ClaimBatchManagement.jsx) filtering the view.
 */
class ClaimBatchControllerReviewerIsolationTest {

    @Mock
    private ClaimBatchService claimBatchService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ReviewerProviderIsolationService reviewerIsolationService;

    private ClaimBatchController controller;

    private final User reviewer = User.builder().id(9L).username("reviewer").userType("MEDICAL_REVIEWER").build();
    private final User admin = User.builder().id(1L).username("admin").userType("SUPER_ADMIN").build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ClaimBatchController(claimBatchService, authorizationService, reviewerIsolationService);
    }

    private ClaimBatch batch(Long providerId) {
        return ClaimBatch.builder()
                .id(1L)
                .batchCode("EMP26-08-00001")
                .providerId(providerId)
                .employerId(1L)
                .batchYear(2026)
                .batchMonth(8)
                .status(ClaimBatch.ClaimBatchStatus.OPEN)
                .build();
    }

    @Test
    void reviewerAssignedToProviderCanGetCurrentBatch() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);
        when(claimBatchService.getExistingBatch(1L, 1L, 2026, 8)).thenReturn(batch(1L));

        ResponseEntity<?> response = controller.getCurrentBatch(1L, 1L, 2026, 8);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);
    }

    @Test
    void reviewerNotAssignedCannotGetCurrentBatchForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى مطالبات هذا المزود"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.getCurrentBatch(51L, 1L, 2026, 8));
        verify(claimBatchService, never()).getExistingBatch(any(), any(), anyInt(), anyInt());
    }

    @Test
    void reviewerNotAssignedCannotOpenBatchForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("لا تملك صلاحية الوصول إلى مطالبات هذا المزود"))
                .when(reviewerIsolationService).validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.openOrGetBatch(51L, 1L, 2026, 8));
        verify(claimBatchService, never()).createBatch(any(), any(), anyInt(), anyInt());
        verify(claimBatchService, never()).getExistingBatch(any(), any(), anyInt(), anyInt());
    }

    @Test
    void reviewerAssignedCanOpenOwnProviderBatch() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);
        when(claimBatchService.getExistingBatch(1L, 1L, 2026, 8)).thenReturn(null);
        when(claimBatchService.createBatch(1L, 1L, 2026, 8)).thenReturn(batch(1L));

        ResponseEntity<?> response = controller.openOrGetBatch(1L, 1L, 2026, 8);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(claimBatchService).createBatch(1L, 1L, 2026, 8);
    }

    @Test
    void reviewerWithMultipleAssignmentsSeesOnlyAssignedProvidersInSearch() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(1L, 51L));
        when(claimBatchService.findBatches(1L, 2026, 8, List.of(1L, 51L)))
                .thenReturn(List.of(batch(1L), batch(51L)));

        ResponseEntity<List<com.waad.tba.modules.claim.dto.ClaimBatchResponse>> response =
                controller.getBatches(1L, 2026, 8);

        assertThat(response.getBody()).hasSize(2);
        verify(claimBatchService).findBatches(1L, 2026, 8, List.of(1L, 51L));
        verify(claimBatchService, never()).findBatches(anyLong(), anyInt(), anyInt());
    }

    @Test
    void reviewerWithNoAssignmentsGetsEmptySearchResult() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of());
        when(claimBatchService.findBatches(1L, 2026, 8, List.of())).thenReturn(List.of());

        ResponseEntity<List<com.waad.tba.modules.claim.dto.ClaimBatchResponse>> response =
                controller.getBatches(1L, 2026, 8);

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void superAdminSearchIsUnrestricted() {
        when(authorizationService.getCurrentUser()).thenReturn(admin);
        when(reviewerIsolationService.isSubjectToIsolation(admin)).thenReturn(false);
        when(claimBatchService.findBatches(1L, 2026, 8, null)).thenReturn(List.of(batch(1L), batch(51L)));

        ResponseEntity<List<com.waad.tba.modules.claim.dto.ClaimBatchResponse>> response =
                controller.getBatches(1L, 2026, 8);

        assertThat(response.getBody()).hasSize(2);
        verify(reviewerIsolationService, never()).getAllowedProviderIds(any());
    }

    @Test
    void adminBypassesValidationOnCurrentBatch() {
        when(authorizationService.getCurrentUser()).thenReturn(admin);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(admin, 51L);
        when(claimBatchService.getExistingBatch(51L, 1L, 2026, 8)).thenReturn(batch(51L));

        ResponseEntity<?> response = controller.getCurrentBatch(51L, 1L, 2026, 8);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
