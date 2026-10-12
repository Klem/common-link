package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOnboardingStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.time.Instant
import java.util.UUID

/**
 * Pins the candidate set of the scheduled onboarding sweep
 * ([MollieConnectionRepository.findActiveNotCollecting]) against real PostgreSQL: a connection
 * that collects (COMPLETED with payments) and a BROKEN one are never candidates (sprint 20, Q1),
 * and the V84 columns are mapped and projected.
 */
class MollieConnectionRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val connectionRepository: MollieConnectionRepository,
    @Autowired private val em: TestEntityManager,
) : AbstractRepositoryTest() {

    private val ids = mutableMapOf<String, UUID>()
    private val statusSince = Instant.parse("2026-10-01T08:00:00Z")
    private val syncedAt = Instant.parse("2026-10-12T08:00:00Z")

    private fun connection(
        key: String,
        status: MollieOnboardingStatus,
        canReceivePayments: Boolean,
        state: MollieConnectionState = MollieConnectionState.ACTIVE,
    ) {
        val user = userRepository.save(TestFixtures.associationUser(email = "$key-${System.nanoTime()}@example.com"))
        val association = associationProfileRepository.save(TestFixtures.associationProfile(user))
        connectionRepository.save(
            MollieConnection(
                association = association,
                accessToken = "access_$key",
                refreshToken = "refresh_$key",
                expiresAt = Instant.now().plusSeconds(3600),
                state = state,
                onboardingStatus = status,
                canReceivePayments = canReceivePayments,
                lastSyncedAt = syncedAt,
                inReviewObserved = status == MollieOnboardingStatus.IN_REVIEW,
                onboardingStatusSince = statusSince,
            )
        )
        ids[key] = association.id!!
    }

    @BeforeEach
    fun seed() {
        connection("needsData", MollieOnboardingStatus.NEEDS_DATA, canReceivePayments = false)
        connection("inReview", MollieOnboardingStatus.IN_REVIEW, canReceivePayments = false)
        connection("completedNoPayments", MollieOnboardingStatus.COMPLETED, canReceivePayments = false)
        connection("collecting", MollieOnboardingStatus.COMPLETED, canReceivePayments = true)
        connection("broken", MollieOnboardingStatus.NEEDS_DATA, canReceivePayments = false, state = MollieConnectionState.BROKEN)
        em.flush()
        em.clear()
    }

    @Test
    fun `findActiveNotCollecting returns only ACTIVE connections that do not collect`() {
        val candidates = connectionRepository.findActiveNotCollecting().associateBy { it.associationId }

        assertThat(candidates.keys)
            .contains(ids["needsData"], ids["inReview"], ids["completedNoPayments"])
            .doesNotContain(ids["collecting"], ids["broken"])
    }

    @Test
    fun `findActiveNotCollecting projects the fields the sweep needs`() {
        val candidate = connectionRepository.findActiveNotCollecting().single { it.associationId == ids["inReview"] }

        assertThat(candidate.refreshToken).isEqualTo("refresh_inReview")
        assertThat(candidate.onboardingStatus).isEqualTo(MollieOnboardingStatus.IN_REVIEW)
        assertThat(candidate.canReceivePayments).isFalse
        assertThat(candidate.onboardingStatusSince).isEqualTo(statusSince)
        assertThat(candidate.lastSyncedAt).isEqualTo(syncedAt)
    }

    @Test
    fun `V84 columns round-trip`() {
        val reloaded = connectionRepository.findByAssociationId(ids["inReview"]!!)!!

        assertThat(reloaded.inReviewObserved).isTrue
        assertThat(reloaded.onboardingStatusSince).isEqualTo(statusSince)
    }
}
