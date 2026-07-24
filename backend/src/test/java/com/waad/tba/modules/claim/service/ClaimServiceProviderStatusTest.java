package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PROVIDER-PORTAL-STATUS-1 — regression tests for the provider claim-creation status
 * defect: {@code POST /claims} is a shared endpoint (PROVIDER_STAFF, DATA_ENTRY,
 * MEDICAL_REVIEWER, SUPER_ADMIN) and {@code ClaimMapper.toEntity} intentionally defaults
 * an unset status to APPROVED for the admin/manual "direct entry" flow. A provider-portal
 * submission never sends a status and was silently inheriting that APPROVED default.
 *
 * {@link ClaimService#enforceProviderClaimCreationStatus} is the fix: for PROVIDER_STAFF
 * users only, force status to DRAFT — regardless of what the client sent — before the DTO
 * ever reaches the mapper. Only that one method is under test; every other ClaimService
 * dependency is irrelevant to it, so the service is constructed with nulls elsewhere.
 */
class ClaimServiceProviderStatusTest {

    private final AuthorizationService authorizationService = mock(AuthorizationService.class);

    private ClaimService newClaimService() {
        // 28 constructor args in current field-declaration order (RequiredArgsConstructor);
        // authorizationService (position 3) is the only one this test exercises.
        return new ClaimService(
                null, null, authorizationService, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private User providerUser() {
        User user = mock(User.class);
        when(authorizationService.isProvider(user)).thenReturn(true);
        return user;
    }

    private User nonProviderUser() {
        User user = mock(User.class);
        when(authorizationService.isProvider(user)).thenReturn(false);
        return user;
    }

    @Test
    void providerCreation_withNoStatusSent_isForcedToDraft_notLeftToDefaultToApproved() {
        ClaimService service = newClaimService();
        User provider = providerUser();
        ClaimCreateDto dto = ClaimCreateDto.builder().status(null).build();

        service.enforceProviderClaimCreationStatus(dto, provider);

        assertEquals(ClaimStatus.DRAFT, dto.getStatus());
    }

    @Test
    void providerCreation_attemptingToSendApproved_isOverriddenToDraft_noPrivilegeEscalation() {
        ClaimService service = newClaimService();
        User provider = providerUser();
        ClaimCreateDto dto = ClaimCreateDto.builder().status(ClaimStatus.APPROVED).build();

        service.enforceProviderClaimCreationStatus(dto, provider);

        assertEquals(ClaimStatus.DRAFT, dto.getStatus());
    }

    @Test
    void nonProviderCreation_isUntouched_adminDirectEntryFlowStillWorks() {
        ClaimService service = newClaimService();
        User admin = nonProviderUser();
        ClaimCreateDto dto = ClaimCreateDto.builder().status(null).build();

        service.enforceProviderClaimCreationStatus(dto, admin);

        // Status is left null here — ClaimMapper.toEntity's own APPROVED default
        // (untouched by this fix) is what applies for non-provider roles.
        assertEquals(null, dto.getStatus());
    }
}
