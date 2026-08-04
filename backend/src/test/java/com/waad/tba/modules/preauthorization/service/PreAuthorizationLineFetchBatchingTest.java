package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationCreateDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationLineDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationResponseDto;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAAD-PREAUTH-MULTI-LINE-1 (Phase 3): proves the @BatchSize fix on
 * PreAuthorization.lines actually caps the query count for a paginated
 * list of multi-line pre-authorizations — without it, mapToResponseDto's
 * unconditional preAuth.getLines() access in a list endpoint would be one
 * extra SELECT per row (N+1). Hibernate statistics are enabled for this
 * test only, via @DynamicPropertySource, so the assertion is against real
 * SQL execution counts, not just "the response looks right."
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class PreAuthorizationLineFetchBatchingTest {

    @DynamicPropertySource
    static void enableHibernateStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired private PreAuthorizationService preAuthorizationService;
    @Autowired private PreAuthorizationRepository preAuthorizationRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Member member;
    private Provider provider;
    private ProviderContractPricingItem pricingA;
    private ProviderContractPricingItem pricingB;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("pa-batch-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("pa-batch-admin").password("password").fullName("Admin")
                    .email("pa-batch-admin@waad.ly").userType("SUPER_ADMIN").active(true).build());
        }

        Employer employer = employerRepository.save(Employer.builder()
                .name("Test Co").code("EMP-PA-BATCH-" + System.nanoTime()).active(true).build());
        member = memberRepository.save(Member.builder()
                .fullName("Batch Member").barcode("PA-BATCH-" + System.nanoTime())
                .nationalNumber("PA-BATCH-" + System.nanoTime()).employer(employer).active(true).build());
        provider = providerRepository.save(Provider.builder()
                .name("Batch Clinic").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-PA-BATCH-" + System.nanoTime()).allowAllEmployers(true).active(true).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-PA-BATCH-" + System.nanoTime())
                .contractNumber("CNT-PA-BATCH-" + System.nanoTime())
                .provider(provider).startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());

        pricingA = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode("SRV-BATCH-A").serviceName("Batch Service A")
                .basePrice(new BigDecimal("40")).contractPrice(new BigDecimal("40")).active(true).build());
        pricingB = pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode("SRV-BATCH-B").serviceName("Batch Service B")
                .basePrice(new BigDecimal("30")).contractPrice(new BigDecimal("30")).active(true).build());
    }

    private Long createMultiLinePreAuth() {
        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
        PreAuthorizationResponseDto response = preAuthorizationService.createPreAuthorization(
                PreAuthorizationCreateDto.builder()
                        .visitId(visit.getId())
                        .providerId(provider.getId())
                        .lines(List.of(
                                PreAuthorizationLineDto.builder().pricingItemId(pricingA.getId()).serviceCategoryId(1L).build(),
                                PreAuthorizationLineDto.builder().pricingItemId(pricingB.getId()).serviceCategoryId(1L).build()))
                        .build(),
                "pa-batch-admin");
        return response.getId();
    }

    @Test
    @WithMockUser(username = "pa-batch-admin", roles = "SUPER_ADMIN")
    void pagedListOfMultiLinePreAuths_doesNotIssueOneLinesQueryPerRow() {
        for (int i = 0; i < 5; i++) {
            createMultiLinePreAuth();
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        var page = preAuthorizationRepository.findByActiveTrue(PageRequest.of(0, 20));
        // Force lazy `lines` access on every row, exactly like
        // PreAuthorizationService.mapToResponseDto does for list endpoints.
        page.getContent().forEach(pa -> assertThat(pa.getLines()).isNotEmpty());

        long queryCount = stats.getPrepareStatementCount();
        // Without batching: 1 (page query) + 5 (one lines query per row) = 6+.
        // With @BatchSize(25), all 5 rows' lines collections load in a
        // single "WHERE pre_authorization_id IN (...)" batch query: 2 total.
        // Assert well under the N+1 count (5 rows) to prove batching is active.
        assertThat(queryCount).isLessThan(5L);
    }
}
