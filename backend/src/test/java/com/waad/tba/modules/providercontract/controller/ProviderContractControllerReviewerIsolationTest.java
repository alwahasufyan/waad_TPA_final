package com.waad.tba.modules.providercontract.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.providercontract.dto.ProviderContractResponseDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractResponseDto.ProviderSummaryDto;
import com.waad.tba.modules.providercontract.service.ProviderContractPricingItemService;
import com.waad.tba.modules.providercontract.service.ProviderContractService;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * WAAD-RBAC-REVIEWER-CONTRACT-SCOPING-6: verifies every MEDICAL_REVIEWER-
 * accessible endpoint in ProviderContractController scopes results to the
 * reviewer's assigned providers (list/search/status/expiring were previously
 * unfiltered; id/code/provider-path endpoints previously trusted the request
 * as-is).
 */
class ProviderContractControllerReviewerIsolationTest {

    @Mock
    private ProviderContractService contractService;
    @Mock
    private ProviderContractPricingItemService pricingService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ReviewerProviderIsolationService reviewerIsolationService;

    private ProviderContractController controller;

    private final User reviewer = User.builder().id(9L).username("reviewer").userType("MEDICAL_REVIEWER").build();
    private final User admin = User.builder().id(1L).username("admin").userType("SUPER_ADMIN").build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ProviderContractController(contractService, pricingService, authorizationService,
                reviewerIsolationService);
    }

    private ProviderContractResponseDto contractFor(Long providerId) {
        return ProviderContractResponseDto.builder()
                .id(providerId * 100)
                .provider(ProviderSummaryDto.builder().id(providerId).name("Provider " + providerId).build())
                .build();
    }

    @Test
    void reviewerListSeesOnlyAssignedProviderContracts() {
        Pageable pageable = PageRequest.of(0, 20);
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(1L));
        when(contractService.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(contractFor(1L), contractFor(51L)), pageable, 2));

        ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> response = controller.getAll(pageable);

        List<ProviderContractResponseDto> content = response.getBody().getData().getContent();
        assertThat(content).hasSize(1);
        assertThat(content.get(0).getProvider().getId()).isEqualTo(1L);
    }

    @Test
    void reviewerWithNoAssignmentsGetsEmptyList() {
        Pageable pageable = PageRequest.of(0, 20);
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of());
        when(contractService.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(contractFor(1L)), pageable, 1));

        ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> response = controller.getAll(pageable);

        assertThat(response.getBody().getData().getContent()).isEmpty();
    }

    @Test
    void superAdminListIsUnrestricted() {
        Pageable pageable = PageRequest.of(0, 20);
        when(authorizationService.getCurrentUser()).thenReturn(admin);
        when(reviewerIsolationService.isSubjectToIsolation(admin)).thenReturn(false);
        when(contractService.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(contractFor(1L), contractFor(51L)), pageable, 2));

        ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> response = controller.getAll(pageable);

        assertThat(response.getBody().getData().getContent()).hasSize(2);
    }

    @Test
    void reviewerAssignedCanGetContractById() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(contractService.findById(100L)).thenReturn(contractFor(1L));
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);

        ResponseEntity<ApiResponse<ProviderContractResponseDto>> response = controller.getById(100L);

        assertThat(response.getBody().getData().getProvider().getId()).isEqualTo(1L);
        verify(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);
    }

    @Test
    void reviewerNotAssignedCannotGetContractByIdForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(contractService.findById(5100L)).thenReturn(contractFor(51L));
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.getById(5100L));
    }

    @Test
    void reviewerNotAssignedCannotGetContractsByProviderPath() {
        Pageable pageable = PageRequest.of(0, 20);
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.getByProvider(51L, pageable));
        verify(contractService, never()).findByProvider(any(), any());
    }

    @Test
    void reviewerAssignedCanGetContractsByProviderPath() {
        Pageable pageable = PageRequest.of(0, 20);
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(reviewer, 1L);
        when(contractService.findByProvider(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(contractFor(1L)), pageable, 1));

        ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> response =
                controller.getByProvider(1L, pageable);

        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    void reviewerNotAssignedCannotGetActiveContractForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.getActiveByProvider(51L));
        verify(contractService, never()).findActiveByProvider(any());
    }

    @Test
    void reviewerNotAssignedCannotGetContractedCategoriesForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.getContractedCategories(51L));
        verify(pricingService, never()).findCategoriesByProvider(any());
    }

    @Test
    void reviewerNotAssignedCannotGetContractedServicesByCategoryForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.getContractedServicesByCategory(51L, 3L));
        verify(pricingService, never()).findServicesByProviderAndCategory(any(), any());
    }

    @Test
    void reviewerNotAssignedCannotGetAllContractedServicesForOtherProvider() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        doThrow(new AccessDeniedException("denied")).when(reviewerIsolationService)
                .validateReviewerAccess(reviewer, 51L);

        assertThrows(AccessDeniedException.class, () -> controller.getAllContractedServices(51L));
        verify(pricingService, never()).findAllServicesByProvider(any());
    }

    @Test
    void reviewerExpiringListIsFilteredToAssignedProviders() {
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(1L));
        when(contractService.findExpiringWithinDays(30)).thenReturn(List.of(contractFor(1L), contractFor(51L)));

        ResponseEntity<ApiResponse<List<ProviderContractResponseDto>>> response = controller.getExpiring(30);

        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getProvider().getId()).isEqualTo(1L);
    }

    @Test
    void reviewerStatusListIsFilteredToAssignedProviders() {
        Pageable pageable = PageRequest.of(0, 20);
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(51L));
        when(contractService.findByStatus(
                com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(contractFor(1L), contractFor(51L)), pageable, 2));

        ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> response = controller.getByStatus(
                com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus.ACTIVE, pageable);

        assertThat(response.getBody().getData().getContent()).hasSize(1);
        assertThat(response.getBody().getData().getContent().get(0).getProvider().getId()).isEqualTo(51L);
    }

    @Test
    void reviewerSearchIsFilteredToAssignedProviders() {
        Pageable pageable = PageRequest.of(0, 20);
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(reviewerIsolationService.isSubjectToIsolation(reviewer)).thenReturn(true);
        when(reviewerIsolationService.getAllowedProviderIds(reviewer)).thenReturn(List.of(1L));
        when(contractService.search(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(contractFor(1L), contractFor(51L)), pageable, 2));

        ResponseEntity<ApiResponse<Page<ProviderContractResponseDto>>> response =
                controller.search(null, null, pageable);

        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    void superAdminBypassesAllIsolationChecks() {
        when(authorizationService.getCurrentUser()).thenReturn(admin);
        doNothing().when(reviewerIsolationService).validateReviewerAccess(admin, 51L);
        when(contractService.findActiveByProvider(51L)).thenReturn(contractFor(51L));

        ResponseEntity<ApiResponse<ProviderContractResponseDto>> response = controller.getActiveByProvider(51L);

        assertThat(response.getBody().getData().getProvider().getId()).isEqualTo(51L);
    }
}
