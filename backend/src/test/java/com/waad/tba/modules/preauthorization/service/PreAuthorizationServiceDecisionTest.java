package com.waad.tba.modules.preauthorization.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationRejectDto;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;
import com.waad.tba.common.service.ArchitecturalGuardService;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreAuthorizationServiceDecisionTest {

    @Mock PreAuthorizationRepository preAuthorizationRepository;
    @Mock ProviderRepository providerRepository;
    @Mock MemberRepository memberRepository;
    @Mock ProviderContractPricingItemRepository pricingItemRepository;
    @Mock VisitRepository visitRepository;
    @Mock ProviderContractService providerContractService;
    @Mock PreAuthorizationAuditService auditService;
    @Mock AuthorizationService authorizationService;
    @Mock ProviderContextGuard providerContextGuard;
    @Mock BenefitPolicyCoverageService benefitPolicyCoverageService;
    @Mock ArchitecturalGuardService architecturalGuard;
    @Mock ReviewerProviderIsolationService reviewerIsolationService;
    @Mock PreAuthEmailNotificationService emailNotificationService;
    @Mock EmailPreAuthService emailPreAuthService;

    @InjectMocks PreAuthorizationService service;

    private User currentUser;

    @BeforeEach
    void reviewerContext() {
        currentUser = mock(User.class);
        lenient().when(authorizationService.getCurrentUser()).thenReturn(currentUser);
        lenient().when(authorizationService.isSuperAdmin(currentUser)).thenReturn(true);
        lenient().when(authorizationService.isInsuranceAdmin(currentUser)).thenReturn(true);
        lenient().when(authorizationService.isReviewer(currentUser)).thenReturn(false);
    }

    @Test
    void terminalRequestCannotRequestInformation() {
        PreAuthorization preAuth = preAuth(PreAuthStatus.APPROVED);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));

        assertThrows(BusinessRuleException.class,
                () -> service.requestInformation(7L, "more clinical details", "superadmin"));
        verify(preAuthorizationRepository, never()).save(any());
    }

    @Test
    void providerStaffCannotPerformDecisionAtServiceBoundary() {
        PreAuthorization preAuth = preAuth(PreAuthStatus.UNDER_REVIEW);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.isSuperAdmin(currentUser)).thenReturn(false);
        when(authorizationService.isInsuranceAdmin(currentUser)).thenReturn(false);
        when(authorizationService.isReviewer(currentUser)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.requestInformation(7L, "details", "provider"));
        verify(preAuthorizationRepository, never()).save(any());
    }

    @Test
    void approvalInProgressCannotBeApprovedAgain() {
        PreAuthorization preAuth = preAuth(PreAuthStatus.APPROVAL_IN_PROGRESS);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));

        assertThrows(BusinessRuleException.class,
                () -> service.requestApproval(7L, null, "superadmin"));
        verify(preAuthorizationRepository, never()).save(any());
    }

    @Test
    void validPartialApprovalCalculatesAndAudits() {
        PreAuthorization preAuth = basePreAuth(PreAuthStatus.UNDER_REVIEW);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(preAuthorizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.findById(33L)).thenReturn(Optional.empty());
        when(providerRepository.findById(22L)).thenReturn(Optional.empty());

        service.approvePartial(7L, new BigDecimal("40.00"), "مراجعة سريرية", "superadmin");

        assertEquals(PreAuthStatus.APPROVED, preAuth.getStatus());
        assertEquals(new BigDecimal("40.00"), preAuth.getApprovedAmount());
        assertEquals(new BigDecimal("40.00"), preAuth.getInsuranceCoveredAmount());
        verify(auditService).logApprove(eq(7L), eq("PA-TEST-7"), eq("superadmin"), contains("PARTIAL_APPROVAL"));
    }

    @Test
    void partialApprovalRejectsInvalidAmountsAndTerminalStates() {
        for (BigDecimal amount : new BigDecimal[] { BigDecimal.ZERO, new BigDecimal("-1"),
                new BigDecimal("100"), new BigDecimal("101") }) {
            PreAuthorization preAuth = basePreAuth(PreAuthStatus.UNDER_REVIEW);
            when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
            assertThrows(BusinessRuleException.class,
                    () -> service.approvePartial(7L, amount, "reason", "superadmin"));
        }

        for (PreAuthStatus status : new PreAuthStatus[] { PreAuthStatus.APPROVED,
                PreAuthStatus.REJECTED, PreAuthStatus.APPROVAL_IN_PROGRESS }) {
            PreAuthorization preAuth = basePreAuth(status);
            when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
            assertThrows(BusinessRuleException.class,
                    () -> service.approvePartial(7L, new BigDecimal("40"), "reason", "superadmin"));
        }
    }

    @Test
    void partialApprovalRequiresContractPrice() {
        PreAuthorization preAuth = basePreAuth(PreAuthStatus.UNDER_REVIEW);
        preAuth.setContractPrice(null);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));

        assertThrows(BusinessRuleException.class,
                () -> service.approvePartial(7L, new BigDecimal("40"), "reason", "superadmin"));
    }

    @Test
    void requestInformationPreservesNotesAndAuditsTransition() {
        PreAuthorization preAuth = basePreAuth(PreAuthStatus.UNDER_REVIEW);
        preAuth.setNotes("provider original note");
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(preAuthorizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.findById(33L)).thenReturn(Optional.empty());
        when(providerRepository.findById(22L)).thenReturn(Optional.empty());

        service.requestInformation(7L, "  missing report  ", "superadmin");

        assertEquals(PreAuthStatus.NEEDS_CORRECTION, preAuth.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(preAuth.getNotes().contains("provider original note"));
        org.junit.jupiter.api.Assertions.assertTrue(preAuth.getNotes().contains("Requested information: missing report"));
        verify(auditService, times(2)).logUpdate(anyLong(), anyString(), eq("superadmin"), anyString(), any(), anyString());
    }

    @Test
    void requestInformationRejectsNullBlankAndTerminalStates() {
        for (String notes : new String[] { null, "   " }) {
            PreAuthorization preAuth = basePreAuth(PreAuthStatus.UNDER_REVIEW);
            when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
            assertThrows(BusinessRuleException.class,
                    () -> service.requestInformation(7L, notes, "superadmin"));
        }
        PreAuthorization approved = basePreAuth(PreAuthStatus.APPROVED);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(approved));
        assertThrows(BusinessRuleException.class,
                () -> service.requestInformation(7L, "details", "superadmin"));
    }

    @Test
    void reviewerIsolationIsEnforcedBeforeMutationAndProviderDataEntryDenied() {
        PreAuthorization preAuth = basePreAuth(PreAuthStatus.UNDER_REVIEW);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.isSuperAdmin(currentUser)).thenReturn(false);
        when(authorizationService.isInsuranceAdmin(currentUser)).thenReturn(false);
        when(authorizationService.isReviewer(currentUser)).thenReturn(true);
        doThrow(new org.springframework.security.access.AccessDeniedException("wrong provider"))
                .when(reviewerIsolationService).validateReviewerAccess(currentUser, 22L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.requestInformation(7L, "details", "reviewer"));
        verify(preAuthorizationRepository, never()).save(any());

        when(authorizationService.isReviewer(currentUser)).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.requestInformation(7L, "details", "provider"));
    }

    @Test
    void rejectionUsesReviewerGuard() {
        PreAuthorization preAuth = basePreAuth(PreAuthStatus.UNDER_REVIEW);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.isSuperAdmin(currentUser)).thenReturn(false);
        when(authorizationService.isInsuranceAdmin(currentUser)).thenReturn(false);
        when(authorizationService.isReviewer(currentUser)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.rejectPreAuthorization(7L, new PreAuthorizationRejectDto("reason"), "provider"));
        verify(preAuthorizationRepository, never()).save(any());
    }

    @Test
    void auditFailurePropagatesForTransactionalRollback() {
        PreAuthorization preAuth = basePreAuth(PreAuthStatus.UNDER_REVIEW);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(preAuthorizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("audit unavailable")).when(auditService)
                .logApprove(anyLong(), anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class,
                () -> service.approvePartial(7L, new BigDecimal("40"), "reason", "superadmin"));
    }

    @Test
    void asyncTechnicalFailureReturnsRequestToReviewWithoutMedicalRejection() {
        PreAuthorization preAuth = basePreAuth(PreAuthStatus.APPROVAL_IN_PROGRESS);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth))
                .thenReturn(Optional.of(preAuth));
        when(memberRepository.findById(33L)).thenThrow(new IllegalStateException("temporary database error"));

        service.processApprovalAsync(7L, null, "superadmin");

        assertEquals(PreAuthStatus.UNDER_REVIEW, preAuth.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(preAuth.getRejectionReason());
        org.junit.jupiter.api.Assertions.assertTrue(preAuth.getNotes().contains("retry required"));
    }

    @Test
    void asyncSuccessfulProcessingReachesApproved() {
        PreAuthorization preAuth = basePreAuth(PreAuthStatus.APPROVAL_IN_PROGRESS);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(memberRepository.findById(33L)).thenReturn(Optional.empty());
        when(preAuthorizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processApprovalAsync(7L, null, "superadmin");

        assertEquals(PreAuthStatus.APPROVED, preAuth.getStatus());
        verify(auditService).logApprove(eq(7L), eq("PA-TEST-7"), eq("superadmin"), contains("Approved amount"));
    }

    @Test
    void rejectingOneOfTwoActiveVisitRequestsDoesNotCancelVisit() {
        Visit visit = Visit.builder().id(99L).build();
        PreAuthorization rejected = basePreAuth(PreAuthStatus.UNDER_REVIEW);
        rejected.setVisit(visit);
        PreAuthorization sibling = basePreAuth(PreAuthStatus.UNDER_REVIEW);
        sibling.setId(8L);
        sibling.setVisit(visit);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(rejected));
        when(preAuthorizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(preAuthorizationRepository.findByVisitIdAndActiveTrue(99L)).thenReturn(java.util.List.of(rejected, sibling));

        service.rejectPreAuthorization(7L, new PreAuthorizationRejectDto("not covered"), "superadmin");

        verify(visitRepository, never()).save(any());
    }

    @Test
    void rejectingLastActiveVisitRequestCancelsVisit() {
        Visit visit = Visit.builder().id(99L).build();
        PreAuthorization rejected = basePreAuth(PreAuthStatus.UNDER_REVIEW);
        rejected.setVisit(visit);
        when(preAuthorizationRepository.findById(7L)).thenReturn(Optional.of(rejected));
        when(preAuthorizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(preAuthorizationRepository.findByVisitIdAndActiveTrue(99L)).thenReturn(java.util.List.of(rejected));

        service.rejectPreAuthorization(7L, new PreAuthorizationRejectDto("not covered"), "superadmin");

        verify(visitRepository).save(visit);
    }

    private PreAuthorization preAuth(PreAuthStatus status) {
        return PreAuthorization.builder()
                .id(7L)
                .providerId(22L)
                .memberId(33L)
                .status(status)
                .active(true)
                .referenceNumber("PA-TEST-7")
                .build();
    }

    private PreAuthorization basePreAuth(PreAuthStatus status) {
        PreAuthorization result = preAuth(status);
        result.setContractPrice(new BigDecimal("100.00"));
        result.setServiceCategoryId(4L);
        result.setServiceCode("TEST-SERVICE");
        result.setServiceName("Test service");
        result.setPriority(PreAuthorization.Priority.NORMAL);
        result.setRequestDate(LocalDate.now());
        result.setExpiryDate(LocalDate.now().plusDays(30));
        result.setCurrency("LYD");
        return result;
    }
}
