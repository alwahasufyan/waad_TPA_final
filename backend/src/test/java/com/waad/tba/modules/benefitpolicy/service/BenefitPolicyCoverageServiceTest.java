package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenefitPolicyCoverageServiceTest {

    @Mock
    private BenefitPolicyRepository policyRepository;
    @Mock
    private BenefitPolicyRuleRepository ruleRepository;
    @Mock
    private MedicalServiceRepository serviceRepository;
    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MedicalCategoryRepository categoryRepository;
    @Mock
    private MedicalServiceCategoryRepository serviceCategoryRepository;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private BenefitPolicyCoverageService coverageService;

    private Member testMember;
    private BenefitPolicy testPolicy;
    private MedicalService testService;

    @BeforeEach
    void setUp() {
        testPolicy = BenefitPolicy.builder()
                .id(1L)
                .name("Standard Plan")
                .status(BenefitPolicyStatus.ACTIVE)
                .active(true)
                .startDate(LocalDate.now().minusMonths(6))
                .endDate(LocalDate.now().plusMonths(6))
                .annualLimit(new BigDecimal("10000.00"))
                .defaultCoveragePercent(80)
                .build();

        testMember = Member.builder()
                .id(1L)
                .fullName("John Doe")
                .benefitPolicy(testPolicy)
                .build();

        testService = MedicalService.builder()
                .id(101L)
                .code("SRV001")
                .name("Consultation")
                .build();
    }

    @Test
    @DisplayName("Should validate active policy successfully")
    void validateMemberHasActivePolicy_Success() {
        assertDoesNotThrow(() -> coverageService.validateMemberHasActivePolicy(testMember, LocalDate.now()));
    }

    @Test
    @DisplayName("Should throw exception when member has no policy and auto-resolve fails")
    void validateMemberHasActivePolicy_NoPolicy() {
        testMember.setBenefitPolicy(null);
        
        assertThrows(BusinessRuleException.class, () -> 
            coverageService.validateMemberHasActivePolicy(testMember, LocalDate.now()));
    }

    @Test
    @DisplayName("Should get coverage from specific service rule")
    void getCoverageForService_ServiceRuleMatch() {
        // Arrange
        BenefitPolicyRule serviceRule = BenefitPolicyRule.builder()
                .id(10L)
                .coveragePercent(90)
                .active(true)
                .requiresPreApproval(true)
                .build();

        when(serviceRepository.findById(101L)).thenReturn(Optional.of(testService));
        when(ruleRepository.findBestRuleForService(eq(1L), eq(101L), any(), any(), any()))
                .thenReturn(Optional.of(serviceRule));

        // Act
        Optional<BenefitPolicyCoverageService.CoverageInfo> result = coverageService.getCoverageForService(testMember, 101L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(90, result.get().getCoveragePercent());
        assertTrue(result.get().isRequiresPreApproval());
        assertEquals("SERVICE", result.get().getRuleType());
    }

    @Test
    @DisplayName("Should fallback to policy default when no rule found")
    void getCoverageForService_PolicyFallback() {
        // Arrange
        when(serviceRepository.findById(101L)).thenReturn(Optional.of(testService));
        when(ruleRepository.findBestRuleForService(anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(Optional.empty());

        // Act
        Optional<BenefitPolicyCoverageService.CoverageInfo> result = coverageService.getCoverageForService(testMember, 101L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(80, result.get().getCoveragePercent()); // Policy default
        assertEquals("POLICY_DEFAULT", result.get().getRuleType());
    }

    @Test
    @DisplayName("Should throw exception if annual limit exceeded")
    void validateAmountLimits_Exceeded() {
        // Arrange
        when(claimRepository.sumApprovedAmountByMemberAndYear(anyLong(), anyInt(), anyList()))
                .thenReturn(new BigDecimal("9500.00")); // Spent 9500 of 10000

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> 
            coverageService.validateAmountLimits(testMember, testPolicy, new BigDecimal("600.00"), LocalDate.now()));
    }

    @Test
    @DisplayName("Should respect waiting period if not met")
    void validateWaitingPeriods_NotMet() {
        // Arrange
        testPolicy.setDefaultWaitingPeriodDays(30);
        testMember.setStartDate(LocalDate.now().minusDays(10)); // Only 10 days since enrollment

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> 
            coverageService.validateWaitingPeriods(testMember, testPolicy, null, LocalDate.now()));
    }
}
