package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.event.MollieOnboardingStatusChangedEvent
import org.commonlink.onchain.OnchainCodec
import org.commonlink.repository.MollieConnectionRepository
import org.junit.jupiter.api.Test
import org.web3j.utils.Numeric
import java.time.Instant
import java.util.UUID

/**
 * Registry v2 registrations: association then campaign, each once, and every campaign-related write ordered
 * after the campaign registration.
 */
class OnchainRegistrationServiceTest {

    private val outbox = mockk<OnchainOutboxService>()
    private val service = OnchainRegistrationService(outbox)

    private val assocUser = User(email = "a@test.com", role = UserRole.ASSOCIATION, provider = AuthProvider.MAGIC_LINK)
    private fun association(siren: String? = "775672594", wallet: String? = "0xasso") =
        AssociationProfile(user = assocUser, name = "Asso", identifier = "W123456789").apply {
            setId(this, UUID.randomUUID())
            this.siren = siren
            this.walletAddress = wallet
        }

    private fun campaign(assoc: AssociationProfile, budgetHash: String? = "0x" + "ab".repeat(32)) =
        Campaign(association = assoc, name = "Camp", emoji = "🌍", description = "desc").apply {
            setId(this, UUID.randomUUID())
            this.budgetHash = budgetHash
        }

    private fun job(action: OnchainJobAction) = OnchainJob(action = action, payloadJson = "{}")

    private fun setId(entity: Any, id: UUID) {
        val field = generateSequence<Class<*>>(entity.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("id") }.getOrNull() }
            .first()
        field.isAccessible = true
        field.set(entity, id)
    }

    @Test
    fun `association registration hashes the SIREN`() {
        val assoc = association()
        val payload = slot<Any>()
        every { outbox.enqueue(OnchainJobAction.REGISTER_ASSOCIATION, capture(payload), "REGISTER_ASSOCIATION:${assoc.id}", null) } returns
            job(OnchainJobAction.REGISTER_ASSOCIATION)

        service.ensureAssociationRegistered(assoc)

        val p = payload.captured as RegisterAssociationPayload
        assertThat(p.address).isEqualTo("0xasso")
        assertThat(p.sirenHashHex).isEqualTo(Numeric.toHexString(OnchainCodec.keccakSiren("775672594")))
    }

    @Test
    fun `association without SIREN is registered with its RNA identifier`() {
        val assoc = association(siren = null)
        val payload = slot<Any>()
        every { outbox.enqueue(any(), capture(payload), any(), any()) } returns job(OnchainJobAction.REGISTER_ASSOCIATION)

        service.ensureAssociationRegistered(assoc)

        assertThat((payload.captured as RegisterAssociationPayload).sirenHashHex)
            .isEqualTo(Numeric.toHexString(OnchainCodec.keccakSiren("W123456789")))
    }

    @Test
    fun `association without on-chain address is skipped`() {
        assertThat(service.ensureAssociationRegistered(association(wallet = null))).isNull()
        verify(exactly = 0) { outbox.enqueue(any(), any(), any(), any()) }
    }

    @Test
    fun `campaign registration depends on the association registration and carries the budget hash`() {
        val assoc = association()
        val camp = campaign(assoc)
        val assocJob = job(OnchainJobAction.REGISTER_ASSOCIATION)
        val payload = slot<Any>()
        every { outbox.enqueue(OnchainJobAction.REGISTER_ASSOCIATION, any(), any(), null) } returns assocJob
        every { outbox.enqueue(OnchainJobAction.REGISTER_CAMPAIGN, capture(payload), "REGISTER_CAMPAIGN:${camp.id}", assocJob) } returns
            job(OnchainJobAction.REGISTER_CAMPAIGN)

        assertThat(service.ensureCampaignRegistered(camp)).isNotNull

        val p = payload.captured as RegisterCampaignPayload
        assertThat(p.campaignId).isEqualTo(camp.id)
        assertThat(p.association).isEqualTo("0xasso")
        assertThat(p.budgetHashHex).isEqualTo(camp.budgetHash)
    }

    @Test
    fun `campaign without budget hash is skipped`() {
        assertThat(service.ensureCampaignRegistered(campaign(association(), budgetHash = null))).isNull()
        verify(exactly = 0) { outbox.enqueue(any(), any(), any(), any()) }
    }

    @Test
    fun `first publication registers the campaign`() {
        val camp = campaign(association())
        every { outbox.findCurrent("REGISTER_CAMPAIGN:${camp.id}") } returns null
        every { outbox.enqueue(any(), any(), any(), any()) } answers { job(firstArg()) }

        service.recordCurrentBudget(camp)

        verify { outbox.enqueue(OnchainJobAction.REGISTER_CAMPAIGN, any(), any(), any()) }
        verify(exactly = 0) { outbox.enqueue(OnchainJobAction.UPDATE_CAMPAIGN_BUDGET, any(), any(), any()) }
    }

    @Test
    fun `a budget change on a registered campaign enqueues an update after the registration`() {
        val camp = campaign(association())
        val registration = job(OnchainJobAction.REGISTER_CAMPAIGN)
        every { outbox.findCurrent("REGISTER_CAMPAIGN:${camp.id}") } returns registration
        every { outbox.enqueue(OnchainJobAction.UPDATE_CAMPAIGN_BUDGET, any(), null, registration) } returns
            job(OnchainJobAction.UPDATE_CAMPAIGN_BUDGET)

        service.recordCurrentBudget(camp)

        verify { outbox.enqueue(OnchainJobAction.UPDATE_CAMPAIGN_BUDGET, any(), null, registration) }
        verify(exactly = 0) { outbox.enqueue(OnchainJobAction.REGISTER_CAMPAIGN, any(), any(), any()) }
    }

    // ── Listener ────────────────────────────────────────────────────────

    private val connectionRepository = mockk<MollieConnectionRepository>()
    private val registration = mockk<OnchainRegistrationService>()
    private val listener = OnchainAssociationRegistrationListener(connectionRepository, registration)

    private fun connection(assoc: AssociationProfile, status: MollieOnboardingStatus, canReceive: Boolean) = MollieConnection(
        association = assoc,
        accessToken = "tok",
        refreshToken = "ref",
        expiresAt = Instant.now().plusSeconds(3600),
        state = MollieConnectionState.ACTIVE,
        onboardingStatus = status,
        canReceivePayments = canReceive,
    )

    @Test
    fun `listener registers an association that can now collect`() {
        val assoc = association()
        every { connectionRepository.findByAssociationId(assoc.id!!) } returns connection(assoc, MollieOnboardingStatus.COMPLETED, true)
        every { registration.ensureAssociationRegistered(assoc) } returns job(OnchainJobAction.REGISTER_ASSOCIATION)

        listener.onMollieStatusChanged(MollieOnboardingStatusChangedEvent(assoc.id!!, MollieOnboardingStatus.IN_REVIEW, MollieOnboardingStatus.COMPLETED))

        verify { registration.ensureAssociationRegistered(assoc) }
    }

    @Test
    fun `listener records nothing for an association that cannot collect (regression included)`() {
        val assoc = association()
        every { connectionRepository.findByAssociationId(assoc.id!!) } returns connection(assoc, MollieOnboardingStatus.NEEDS_DATA, false)

        listener.onMollieStatusChanged(MollieOnboardingStatusChangedEvent(assoc.id!!, MollieOnboardingStatus.COMPLETED, MollieOnboardingStatus.NEEDS_DATA))

        verify(exactly = 0) { registration.ensureAssociationRegistered(any()) }
    }
}
