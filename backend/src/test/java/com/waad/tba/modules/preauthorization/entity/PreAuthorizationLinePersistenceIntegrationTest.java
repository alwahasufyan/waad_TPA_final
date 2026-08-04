package com.waad.tba.modules.preauthorization.entity;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAAD-PREAUTH-MULTI-LINE-1 (Phase 1): verifies the additive schema/entity
 * work in isolation — PreAuthorizationLine persists via cascade from its
 * PreAuthorization header, the pre_authorization_lines FK/migration
 * (V112__pre_authorization_lines.sql) is valid, and orphanRemoval works —
 * exactly like Claim/ClaimLine. Phase 1 does not wire this into
 * PreAuthorizationService's creation flow, so this test builds entities
 * directly through the repository, not through the service layer.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class PreAuthorizationLinePersistenceIntegrationTest {

    @Autowired
    private PreAuthorizationRepository preAuthorizationRepository;

    @Autowired
    private EmployerRepository employerRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private EntityManager entityManager;

    private Visit visit;
    private Long providerId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        Employer employer = employerRepository.save(Employer.builder()
                .name("Test Company")
                .code("EMP-PA-LINE-TEST")
                .active(true)
                .build());

        Member member = memberRepository.save(Member.builder()
                .fullName("Jane Doe")
                .barcode("PA-LINE-TEST-1")
                .nationalNumber("PA-LINE-TEST-1")
                .employer(employer)
                .active(true)
                .build());
        memberId = member.getId();

        Provider provider = providerRepository.save(Provider.builder()
                .name("Test Clinic")
                .providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-PA-LINE-TEST")
                .allowAllEmployers(true)
                .active(true)
                .build());
        providerId = provider.getId();

        visit = visitRepository.save(Visit.builder()
                .member(member)
                .providerId(provider.getId())
                .visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED)
                .build());
    }

    private PreAuthorizationLine lineOf(String code, Long categoryId, String amount) {
        return PreAuthorizationLine.builder()
                .lineNumber(1)
                .pricingItemId(1L)
                .serviceCode(code)
                .serviceName("Service " + code)
                .serviceCategoryId(categoryId)
                .serviceCategoryName("Category " + categoryId)
                .contractPrice(new BigDecimal(amount))
                .requiresPA(true)
                .build();
    }

    @Test
    void savingHeaderWithTwoLines_persistsBothLinesViaCascade() {
        PreAuthorization preAuth = PreAuthorization.builder()
                .preAuthNumber("PA-LINE-TEST-0001")
                .memberId(memberId)
                .providerId(providerId)
                .visit(visit)
                .serviceCode("SRV-A")
                .serviceCategoryId(1L)
                .contractPrice(new BigDecimal("100.00"))
                .requestDate(LocalDate.now())
                .expectedServiceDate(LocalDate.now())
                .build();

        PreAuthorizationLine lineA = lineOf("SRV-A", 1L, "100.00");
        lineA.setLineNumber(1);
        PreAuthorizationLine lineB = lineOf("SRV-B", 2L, "50.00");
        lineB.setLineNumber(2);
        preAuth.addLine(lineA);
        preAuth.addLine(lineB);

        PreAuthorization saved = preAuthorizationRepository.save(preAuth);
        entityManager.flush();
        entityManager.clear();

        PreAuthorization reloaded = preAuthorizationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getLines()).hasSize(2);
        assertThat(reloaded.getServiceCount()).isEqualTo(2);
        assertThat(reloaded.getLines())
                .extracting(PreAuthorizationLine::getServiceCode)
                .containsExactlyInAnyOrder("SRV-A", "SRV-B");
        assertThat(reloaded.getLines())
                .allSatisfy(line -> assertThat(line.getPreAuthorization().getId()).isEqualTo(reloaded.getId()));
        // Header per-service columns ("line 0" cache) remain populated and untouched.
        assertThat(reloaded.getServiceCode()).isEqualTo("SRV-A");
        assertThat(reloaded.getContractPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void removingLine_orphanRemovalDeletesIt() {
        PreAuthorization preAuth = PreAuthorization.builder()
                .preAuthNumber("PA-LINE-TEST-0002")
                .memberId(memberId)
                .providerId(providerId)
                .visit(visit)
                .serviceCode("SRV-C")
                .serviceCategoryId(1L)
                .contractPrice(new BigDecimal("75.00"))
                .requestDate(LocalDate.now())
                .expectedServiceDate(LocalDate.now())
                .build();
        PreAuthorizationLine line = lineOf("SRV-C", 1L, "75.00");
        preAuth.addLine(line);
        PreAuthorization saved = preAuthorizationRepository.save(preAuth);
        entityManager.flush();
        entityManager.clear();

        PreAuthorization reloaded = preAuthorizationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getLines()).hasSize(1);

        reloaded.removeLine(reloaded.getLines().get(0));
        preAuthorizationRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        PreAuthorization afterRemoval = preAuthorizationRepository.findById(saved.getId()).orElseThrow();
        assertThat(afterRemoval.getLines()).isEmpty();
    }
}
