package org.commonlink.repository

import org.junit.jupiter.api.Tag
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Classe de base pour tous les tests de repositories JPA.
 *
 * Choix @DataJpaTest plutôt que @SpringBootTest :
 *  - Charge uniquement la slice JPA (entités, repositories, Flyway, DataSource)
 *  - 3-5x plus rapide : pas de web context, pas de sécurité, pas de services
 *
 * AutoConfigureTestDatabase.Replace.NONE : on désactive le remplacement par H2
 * et on laisse Testcontainers fournir le vrai PostgreSQL.
 *
 * @DataJpaTest ajoute @Transactional au niveau classe → chaque @Test rollback
 * automatiquement après son exécution. Les fixtures insérées en @BeforeEach
 * font partie de la même transaction et sont donc nettoyées sans DELETE explicite.
 *
 * @ImportTestcontainers pointe vers l'interface singleton : Spring Boot récupère
 * le même conteneur pour toutes les classes de test qui héritent de cette base
 * (l'ApplicationContext est mis en cache par Spring Test).
 *
 * @Tag("testcontainers") — hérité par toutes les sous-classes (JUnit5 hérite les tags
 * des superclasses) : exclu du build Clever Cloud (pas de Docker là-bas), toujours
 * exécuté par `./gradlew.bat test` en local.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@Tag("testcontainers")
abstract class AbstractRepositoryTest
