package org.commonlink.repository

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base of the tests that need a real PostgreSQL rather than the suite's H2: row locks (`PESSIMISTIC_WRITE`), native
 * `ON CONFLICT`, true concurrency. Starts one container for every subclass (they share one Spring context) and points
 * the datasource at it explicitly, because the shared `@ServiceConnection` container ([TestcontainersConfig]) does not
 * replace the test profile's H2 URL. Unlike H2, the schema comes from the real Flyway migrations, validated against the
 * entities (`ddl-auto: validate`): column defaults, CHECK constraints and partial indexes are those of production.
 *
 * Needs a Docker daemon; excluded by `-PnoDocker` (Clever Cloud build).
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
abstract class RealPostgresTest {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            postgres.start()
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }
}
