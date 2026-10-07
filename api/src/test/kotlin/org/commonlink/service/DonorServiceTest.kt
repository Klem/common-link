package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.UpdateDonorProfileRequest
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.DonorProfileRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

private fun <T> T.setId(id: UUID): T = also {
    it!!.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, id)
}

class DonorServiceTest {

    private val donorProfileRepository = mockk<DonorProfileRepository>()
    private val service = DonorService(donorProfileRepository)

    private val userId  = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val donorId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val donorUser = User(email = "d@test.com", role = UserRole.DONOR, provider = AuthProvider.EMAIL, emailVerified = true)

    private fun profile() = DonorProfile(
        user = donorUser,
        displayName = "Marie D.",
        anonymous = false,
        firstName = "Marie",
        lastName = "Dupont",
    ).setId(donorId)

    /** Makes `save` return the very entity it was handed, as the real repository would. */
    private fun capturingSave() {
        val saved = slot<DonorProfile>()
        every { donorProfileRepository.save(capture(saved)) } answers { saved.captured }
    }

    @Test
    fun `getProfile returns the full profile including notification preferences`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(profile())

        val dto = service.getProfile(userId)

        assertThat(dto.id).isEqualTo(donorId)
        assertThat(dto.firstName).isEqualTo("Marie")
        assertThat(dto.lastName).isEqualTo("Dupont")
        assertThat(dto.displayName).isEqualTo("Marie D.")
        assertThat(dto.anonymous).isFalse()
        assertThat(dto.notifyMonthlyReport).isTrue()
        assertThat(dto.notifyNewPayout).isTrue()
        assertThat(dto.notifyGoalReached).isTrue()
        assertThat(dto.notifySuggestions).isFalse()
    }

    @Test
    fun `getProfile throws when the user has no donor profile`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.empty()

        assertThrows<UserNotFoundException> { service.getProfile(userId) }
    }

    @Test
    fun `updateProfile leaves absent fields untouched`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(profile())
        capturingSave()

        val dto = service.updateProfile(userId, UpdateDonorProfileRequest(displayName = "Nouveau"))

        assertThat(dto.displayName).isEqualTo("Nouveau")
        // Everything else keeps its previous value — PATCH semantics
        assertThat(dto.firstName).isEqualTo("Marie")
        assertThat(dto.lastName).isEqualTo("Dupont")
        assertThat(dto.anonymous).isFalse()
        assertThat(dto.notifyMonthlyReport).isTrue()
        assertThat(dto.notifySuggestions).isFalse()
    }

    @Test
    fun `updateProfile writes the civil identity`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(profile())
        capturingSave()

        val dto = service.updateProfile(userId, UpdateDonorProfileRequest(firstName = "Jean", lastName = "Martin"))

        assertThat(dto.firstName).isEqualTo("Jean")
        assertThat(dto.lastName).isEqualTo("Martin")
        // The public pseudonym is a separate field and must not follow the civil name
        assertThat(dto.displayName).isEqualTo("Marie D.")
    }

    @Test
    fun `updateProfile toggles each notification preference independently`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(profile())
        capturingSave()

        val offOne = service.updateProfile(userId, UpdateDonorProfileRequest(notifyMonthlyReport = false))
        assertThat(offOne.notifyMonthlyReport).isFalse()
        assertThat(offOne.notifyNewPayout).isTrue()
        assertThat(offOne.notifyGoalReached).isTrue()

        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(profile())
        val onSuggestions = service.updateProfile(userId, UpdateDonorProfileRequest(notifySuggestions = true))
        assertThat(onSuggestions.notifySuggestions).isTrue()
        assertThat(onSuggestions.notifyMonthlyReport).isTrue()

        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(profile())
        val allOff = service.updateProfile(
            userId,
            UpdateDonorProfileRequest(
                notifyMonthlyReport = false, notifyNewPayout = false,
                notifyGoalReached = false, notifySuggestions = false,
            ),
        )
        assertThat(allOff.notifyMonthlyReport).isFalse()
        assertThat(allOff.notifyNewPayout).isFalse()
        assertThat(allOff.notifyGoalReached).isFalse()
        assertThat(allOff.notifySuggestions).isFalse()
    }

    @Test
    fun `updateProfile sets the anonymity flag`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.of(profile())
        capturingSave()

        assertThat(service.updateProfile(userId, UpdateDonorProfileRequest(anonymous = true)).anonymous).isTrue()
    }

    @Test
    fun `updateProfile throws when the user has no donor profile`() {
        every { donorProfileRepository.findByUserId(userId) } returns Optional.empty()

        assertThrows<UserNotFoundException> {
            service.updateProfile(userId, UpdateDonorProfileRequest(displayName = "X"))
        }
    }
}
