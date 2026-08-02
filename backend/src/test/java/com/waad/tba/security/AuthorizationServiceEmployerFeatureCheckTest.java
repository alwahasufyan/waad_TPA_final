package com.waad.tba.security;

import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.rbac.service.EffectivePermissionService;
import com.waad.tba.modules.visit.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * WAAD-RBAC-EMPLOYER-PERMISSIONS-MIGRATION-1: canEmployerViewMembers() /
 * canEmployerViewBenefitPolicies() used to read the standalone
 * users.can_view_members / can_view_benefit_policies boolean columns; they
 * now consult the "beneficiaries.read" / "benefit_policies.read" effective
 * permissions instead (role grant + any active per-user override, managed
 * via the "الصلاحيات الخاصة للمستخدمين" admin tab).
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceEmployerFeatureCheckTest {

    @Mock private UserRepository userRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private EffectivePermissionService effectivePermissionService;

    @InjectMocks
    private AuthorizationService service;

    private User employerAdmin;

    @BeforeEach
    void setUp() {
        employerAdmin = User.builder().id(10L).username("employer1").userType("EMPLOYER_ADMIN").employerId(1L).build();
    }

    @Test
    void canEmployerViewMembers_true_whenEffectivePermissionsIncludeBeneficiariesRead() {
        when(effectivePermissionService.getEffectivePermissions(employerAdmin))
                .thenReturn(Set.of("beneficiaries.read", "employers.read"));

        assertTrue(service.canEmployerViewMembers(employerAdmin));
    }

    @Test
    void canEmployerViewMembers_false_whenActiveRevokeOverrideRemovesBeneficiariesRead() {
        // Simulates the V106 migration outcome: an active REVOKE override means
        // the role's default grant of beneficiaries.read is no longer effective.
        when(effectivePermissionService.getEffectivePermissions(employerAdmin))
                .thenReturn(Set.of("employers.read"));

        assertFalse(service.canEmployerViewMembers(employerAdmin));
    }

    @Test
    void canEmployerViewMembers_false_whenEmployerAdminHasNoEmployerId() {
        User noEmployer = User.builder().id(11L).username("employer2").userType("EMPLOYER_ADMIN").employerId(null).build();

        assertFalse(service.canEmployerViewMembers(noEmployer));
    }

    @Test
    void canEmployerViewMembers_true_forSuperAdmin_withoutConsultingPermissions() {
        User superAdmin = User.builder().id(1L).username("root").userType("SUPER_ADMIN").build();

        assertTrue(service.canEmployerViewMembers(superAdmin));
    }

    @Test
    void canEmployerViewMembers_true_forNonEmployerRole_withoutConsultingPermissions() {
        User reviewer = User.builder().id(2L).username("reviewer").userType("MEDICAL_REVIEWER").build();

        assertTrue(service.canEmployerViewMembers(reviewer));
    }

    @Test
    void canEmployerViewBenefitPolicies_true_whenEffectivePermissionsIncludeBenefitPoliciesRead() {
        when(effectivePermissionService.getEffectivePermissions(employerAdmin))
                .thenReturn(Set.of("benefit_policies.read"));

        assertTrue(service.canEmployerViewBenefitPolicies(employerAdmin));
    }

    @Test
    void canEmployerViewBenefitPolicies_false_whenActiveRevokeOverrideRemovesBenefitPoliciesRead() {
        when(effectivePermissionService.getEffectivePermissions(employerAdmin))
                .thenReturn(Set.of("employers.read"));

        assertFalse(service.canEmployerViewBenefitPolicies(employerAdmin));
    }

    @Test
    void canEmployerViewBenefitPolicies_false_whenEmployerAdminHasNoEmployerId() {
        User noEmployer = User.builder().id(12L).username("employer3").userType("EMPLOYER_ADMIN").employerId(null).build();
        lenient().when(effectivePermissionService.getEffectivePermissions(noEmployer)).thenReturn(Set.of());

        assertFalse(service.canEmployerViewBenefitPolicies(noEmployer));
    }
}
