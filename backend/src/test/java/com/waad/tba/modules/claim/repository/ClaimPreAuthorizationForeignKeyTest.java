package com.waad.tba.modules.claim.repository;

import com.waad.tba.config.IntegrationTestContainersConfig;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WAAD-CLAIM-PREAUTH-FK-FIX-1: regression coverage for the V113 migration —
 * claims.pre_authorization_id's FK previously pointed at the legacy, unused
 * preauthorization_requests table instead of pre_authorizations, so ANY
 * claim created with a real pre_authorization_id would fail at insert time.
 * The application layer (Claim.preAuthorization, a real @ManyToOne to the
 * PreAuthorization entity) was always correct — only the DB constraint was
 * wrong. These tests exercise the real constraint against a genuinely
 * migrated Testcontainers Postgres, not a mock.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
@Transactional
class ClaimPreAuthorizationForeignKeyTest {

    @Autowired private ClaimRepository claimRepository;
    @Autowired private PreAuthorizationRepository preAuthorizationRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Member member;
    private Provider provider;
    private Visit visit;

    @BeforeEach
    void setUp() {
        Employer employer = employerRepository.save(Employer.builder()
                .name("FK Test Co").code("EMP-FK-" + System.nanoTime()).active(true).build());
        member = memberRepository.save(Member.builder()
                .fullName("FK Test Member").barcode("FK-" + System.nanoTime())
                .nationalNumber("FK-" + System.nanoTime()).employer(employer).active(true).build());
        provider = providerRepository.save(Provider.builder()
                .name("FK Test Clinic").providerType(ProviderType.CLINIC)
                .licenseNumber("LIC-FK-" + System.nanoTime()).allowAllEmployers(true).active(true).build());
        visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
    }

    private PreAuthorization savedPreAuthorization() {
        PreAuthorization preAuth = PreAuthorization.builder()
                .preAuthNumber("PA-FK-TEST-" + System.nanoTime())
                .referenceNumber("PA-FK-TEST-" + System.nanoTime())
                .memberId(member.getId())
                .providerId(provider.getId())
                .visit(visit)
                .serviceCode("SRV-FK")
                .serviceCategoryId(1L)
                .contractPrice(BigDecimal.TEN)
                .requestDate(LocalDate.now())
                .expectedServiceDate(LocalDate.now())
                .build();
        return preAuthorizationRepository.save(preAuth);
    }

    private Claim minimalBacklogClaim(PreAuthorization preAuthorization) {
        ClaimLine line = ClaimLine.builder()
                .serviceCode("SRV-FK").serviceName("FK Test Service")
                .quantity(1).unitPrice(BigDecimal.TEN).requiresPA(false)
                .build();
        Claim claim = Claim.builder()
                .visit(visit)
                .member(member)
                .providerId(provider.getId())
                .status(ClaimStatus.SUBMITTED)
                .isBacklog(true) // bypasses the PA-required check regardless of line.requiresPA
                .requestedAmount(BigDecimal.TEN)
                .serviceDate(LocalDate.now())
                .preAuthorization(preAuthorization)
                .build();
        claim.addLine(line);
        line.setClaim(claim);
        return claim;
    }

    @Test
    void claimCanLinkToRealPreAuthorizationRow() {
        PreAuthorization preAuth = savedPreAuthorization();

        Claim claim = minimalBacklogClaim(preAuth);
        Claim saved = claimRepository.save(claim);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPreAuthorization().getId()).isEqualTo(preAuth.getId());

        // Confirm at the raw SQL level too — not just via the JPA proxy.
        Long linkedId = jdbcTemplate.queryForObject(
                "SELECT pre_authorization_id FROM claims WHERE id = ?", Long.class, saved.getId());
        assertThat(linkedId).isEqualTo(preAuth.getId());
    }

    @Test
    void claimCannotReferenceNonexistentPreAuthorization() {
        // A transient reference to an ID that was never persisted — Hibernate
        // only needs the ID for the FK column, so this triggers a real FK
        // violation at flush time without needing a full PreAuthorization row.
        PreAuthorization nonexistent = PreAuthorization.builder().id(999_999_999L).build();

        Claim claim = minimalBacklogClaim(nonexistent);

        assertThatThrownBy(() -> {
            claimRepository.save(claim);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fkTargetsPreAuthorizations_notLegacyPreauthorizationRequestsTable() {
        String targetTable = jdbcTemplate.queryForObject(
                """
                SELECT confrelid::regclass::text
                FROM pg_constraint
                WHERE conname = 'fk_claim_preauth'
                """,
                String.class);

        assertThat(targetTable).isEqualTo("pre_authorizations");
        assertThat(targetTable).isNotEqualTo("preauthorization_requests");
    }
}
