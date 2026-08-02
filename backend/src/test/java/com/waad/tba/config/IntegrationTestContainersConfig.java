package com.waad.tba.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * WAAD-INTEGRATION-TEST-CONTEXT-1
 *
 * Shared Testcontainers Postgres for the handful of real {@code @SpringBootTest}
 * integration tests (ClaimLifecycleIntegrationTest, DropIndexTest) that need a
 * genuine, migrated database — as opposed to the vast majority of this
 * project's tests, which are plain Mockito unit tests with no Spring context
 * at all.
 *
 * Deliberately NOT the shared local dev database (localhost:5433): that
 * database holds real, hand-verified dev data, and DropIndexTest in
 * particular runs real DDL (ALTER TABLE / DROP INDEX) against it — running
 * that against a shared, persistent database on every test run would mutate
 * dev data and make the test non-repeatable. A container is ephemeral,
 * isolated, and gets a completely fresh schema (all Flyway migrations run
 * from scratch) every time.
 *
 * {@code @ServiceConnection} auto-configures spring.datasource.* from the
 * running container — no manual JDBC URL/credentials wiring needed, and no
 * change to application.yml's real (dev/prod) datasource defaults.
 *
 * postgres:16 matches the image the local dev docker-compose stack already
 * uses (see waad-postgres-dev), so this doesn't introduce a second Postgres
 * major version to keep in sync.
 */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestContainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
    }
}
