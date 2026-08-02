package com.waad.tba;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.config.IntegrationTestContainersConfig;

// WAAD-INTEGRATION-TEST-CONTEXT-1: real Spring context needs a real,
// migrated database — see IntegrationTestContainersConfig for why this is a
// Testcontainers-managed Postgres rather than the shared dev database.
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestContainersConfig.class)
public class DropIndexTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void dropDuplicateIndex() {
        System.out.println("====== STARTING INDEX DROP ======");
        try {
            jdbcTemplate.execute("ALTER TABLE claims DROP CONSTRAINT IF EXISTS idx_claims_duplicate_prevention");
            System.out.println("Constraint dropped");
        } catch (Exception e) {
            System.out.println("Warning dropping constraint: " + e.getMessage());
        }

        try {
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_claims_duplicate_prevention");
            System.out.println("Index dropped");
        } catch (Exception e) {
            System.out.println("Warning dropping index: " + e.getMessage());
        }
        System.out.println("====== INDEX DROP COMPLETE ======");
    }
}
