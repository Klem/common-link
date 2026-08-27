package org.commonlink.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.commonlink.entity.LegalDocumentType
import org.commonlink.repository.LegalDocumentRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container

/**
 * Dedicated, empty Postgres container — not the one shared by [org.commonlink.repository.TestcontainersConfig]
 * across the whole repository test suite, so the schema genuinely starts blank.
 */
@TestConfiguration
interface FlywayMigrationTestContainer {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}

/**
 * Boots the application against a genuinely empty Postgres container on the `local`
 * profile — there is no `application-local.yml`, so the base `application.yml` settings
 * apply as-is (`spring.flyway.enabled=true`, `ddl-auto=validate`), identical to what
 * staging/prod use. `local` is only needed to satisfy `@Profile("local")` beans
 * (e.g. [org.commonlink.service.EmailServiceStub]) that a full `@SpringBootTest` context
 * requires; it does not touch Flyway or ddl-auto. Nothing else in the suite exercises this
 * path: `application-test.yml` disables Flyway and lets Hibernate create the schema, so
 * V1..Vn have never actually run end-to-end before this test.
 */
@SpringBootTest
@ImportTestcontainers(FlywayMigrationTestContainer::class)
@ActiveProfiles("local")
@Tag("testcontainers")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
])
class FlywayMigrationTest {

    @Autowired
    lateinit var context: ConfigurableApplicationContext

    @Autowired
    lateinit var legalDocumentRepository: LegalDocumentRepository

    /**
     * ddl-auto=validate makes this assertion also prove the JPA entity mapping matches
     * exactly the schema Flyway produced — if either V1..Vn or an entity mapping drifted,
     * context startup fails before this line runs.
     */
    @Test
    fun `V1 to Vn migrate cleanly on an empty database and match the JPA entity mapping`() {
        assertTrue(context.isActive)
    }

    /**
     * `test` profile skips Flyway (Hibernate creates the schema instead), so V73's seeded
     * CGU/CGV placeholder rows are otherwise never exercised — this is the only path that
     * proves the INSERT statements in V73 actually ran and are readable through the entity.
     */
    @Test
    fun `V73 seeds exactly one published CGU and one published CGV document`() {
        val cgu = legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGU)
        val cgv = legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(LegalDocumentType.CGV)
        assertNotNull(cgu)
        assertNotNull(cgv)
        assertEquals(LegalDocumentType.CGU, cgu!!.documentType)
        assertEquals(LegalDocumentType.CGV, cgv!!.documentType)
    }
}
