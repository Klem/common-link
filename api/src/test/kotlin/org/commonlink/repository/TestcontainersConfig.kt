package org.commonlink.repository

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container

/**
 * Interface dont les champs statiques (companion object Kotlin = singleton JVM)
 * définissent les conteneurs partagés pour toute la suite de tests.
 *
 * Importé via @ImportTestcontainers(TestcontainersConfig::class) dans AbstractRepositoryTest.
 * Spring Boot cache l'ApplicationContext entre les classes de test → un seul conteneur
 * est démarré pour l'ensemble de la suite de tests repository.
 *
 * @ServiceConnection infère automatiquement spring.datasource.url/username/password
 * depuis le type du conteneur (PostgreSQLContainer). Pas besoin de @DynamicPropertySource.
 */
@TestConfiguration
interface TestcontainersConfig {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
