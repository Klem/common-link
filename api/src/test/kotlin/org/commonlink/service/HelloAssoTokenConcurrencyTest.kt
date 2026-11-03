package org.commonlink.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Two concurrent renewals of the same HelloAsso link on the full Spring context: the row lock serializes them, so
 * HelloAsso is called once and the second caller reads the renewed token (a second renewal could break the chain).
 */
@Tag("testcontainers")
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
class HelloAssoTokenConcurrencyTest {

    @Autowired private lateinit var tokenManager: HelloAssoTokenManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var connectionRepository: HelloAssoConnectionRepository

    @MockkBean private lateinit var gateway: HelloAssoGateway

    private var associationId: UUID? = null

    @AfterEach
    fun cleanUp() {
        associationId?.let { id ->
            connectionRepository.findByAssociationId(id)?.let { connectionRepository.delete(it) }
            val association = associationProfileRepository.findById(id).get()
            associationProfileRepository.delete(association)
            userRepository.delete(association.user)
        }
    }

    @Test
    fun `two concurrent renewals of an expired token call HelloAsso once`() {
        val user = userRepository.save(TestFixtures.associationUser(email = "ha-conc-${System.nanoTime()}@example.com"))
        val association = associationProfileRepository.save(
            TestFixtures.associationProfile(user).apply { paymentProvider = PaymentProviderId.HELLOASSO }
        )
        associationId = association.id
        connectionRepository.save(
            HelloAssoConnection(
                association = association, webhookToken = "wh_${UUID.randomUUID()}", state = HelloAssoConnectionState.ACTIVE,
                clientId = "client", refreshToken = "old-refresh", accessToken = "old-access",
                accessTokenExpiresAt = Instant.now().minusSeconds(60),
            )
        )
        every { gateway.refreshTokens("old-refresh") } answers {
            Thread.sleep(400) // keeps the lock long enough for the second caller to queue behind it
            HelloAssoTokens("new-access", "new-refresh", 1800)
        }

        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val results = (1..2).map { pool.submit<String> { start.await(); tokenManager.getValidAccessToken(association.id!!) } }
        start.countDown()
        val tokens = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(listOf("new-access", "new-access"), tokens)
        verify(exactly = 1) { gateway.refreshTokens(any()) }
        assertEquals("new-refresh", connectionRepository.findByAssociationId(association.id!!)!!.refreshToken)
    }
}
