package com.waad.tba.modules.visit.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.visit.mapper.VisitMapper;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;

/**
 * HOTFIX (employer-member consistency) — verifies the mandatory backend guard
 * that a member must belong to the selected employer. This is the defense-in-depth
 * check that holds even if the frontend member lookup is bypassed.
 */
@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    @Mock private VisitRepository repository;
    @Mock private MemberRepository memberRepository;
    @Mock private VisitMapper mapper;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditLogService auditLogService;
    @Mock private ProviderRepository providerRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private PreAuthorizationRepository preAuthRepository;
    @Mock private ProviderContextGuard providerContextGuard;
    @Mock private BenefitPolicyCoverageService benefitPolicyCoverageService;

    @InjectMocks private VisitService visitService;

    private Member memberOfEmployer(long memberId, Long employerId) {
        Member m = Member.builder().id(memberId).build();
        if (employerId != null) {
            m.setEmployer(Employer.builder().id(employerId).build());
        }
        return m;
    }

    @Test
    void rejects_whenMemberBelongsToADifferentEmployer() {
        Member member = memberOfEmployer(10L, 1L); // member is in employer 1

        assertThatThrownBy(() -> visitService.validateMemberBelongsToEmployer(member, 2L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("لا ينتمي إلى جهة العمل المحددة");
    }

    @Test
    void rejects_whenSelectedEmployerGivenButMemberHasNoEmployer() {
        Member member = memberOfEmployer(11L, null); // member has no employer

        assertThatThrownBy(() -> visitService.validateMemberBelongsToEmployer(member, 5L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void passes_whenMemberBelongsToTheSelectedEmployer() {
        Member member = memberOfEmployer(12L, 7L);

        assertThatCode(() -> visitService.validateMemberBelongsToEmployer(member, 7L))
                .doesNotThrowAnyException();
    }

    @Test
    void skips_whenNoEmployerContextProvided_backwardCompatible() {
        Member member = memberOfEmployer(13L, 3L);

        // employerId == null → no selected-employer context → no enforcement
        assertThatCode(() -> visitService.validateMemberBelongsToEmployer(member, null))
                .doesNotThrowAnyException();
    }
}
