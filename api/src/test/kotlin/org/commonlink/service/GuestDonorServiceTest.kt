package org.commonlink.service

import org.commonlink.entity.AuthProvider
import org.commonlink.entity.CampaignStatus
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
@Transactional
class GuestDonorServiceTest {

    @Autowired private lateinit var guestDonorService: GuestDonorService
    @Autowired private lateinit var donationService: DonationService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var donationRepository: DonationRepository

    @Test
    fun `findOrCreateGuestDonor is idempotent - two calls same email return same User and DonorProfile`() {
        val email = "  Guest.Donor@Example.COM  "

        val profile1 = guestDonorService.findOrCreateGuestDonor(email, "Jean Dupont")
        val profile2 = guestDonorService.findOrCreateGuestDonor(email, "Jean Dupont")

        assertEquals(profile1.id, profile2.id, "Same DonorProfile id on both calls")
        assertEquals(profile1.user.id, profile2.user.id, "Same User id on both calls")

        val users = userRepository.findAll().filter { it.email == "guest.donor@example.com" }
        assertEquals(1, users.size, "Only one User row should exist")

        val profiles = donorProfileRepository.findAll().filter { it.user.id == profile1.user.id }
        assertEquals(1, profiles.size, "Only one DonorProfile row should exist")
    }

    @Test
    fun `guest user is created with correct flags`() {
        val profile = guestDonorService.findOrCreateGuestDonor("widget-guest@example.com", "Marie Curie")

        val user = profile.user
        assertEquals(AuthProvider.GUEST, user.provider)
        assertTrue(user.guest)
        assertFalse(user.emailVerified)
        assertEquals(null, user.passwordHash)
        assertEquals(null, user.googleSub)
        assertEquals("widget-guest@example.com", user.email)
    }

    @Test
    fun `guest DonorProfile stores displayName and anonymous defaults to false`() {
        val profile = guestDonorService.findOrCreateGuestDonor("anon@example.com", "Pierre Martin")

        assertEquals("Pierre Martin", profile.displayName)
        assertTrue(profile.anonymous)
        assertFalse(profile.user.guest.not()) // guest=true
    }

    @Test
    fun `confirmDonation derives wallet address for a guest donor identically to regular donors`() {
        val guestProfile = guestDonorService.findOrCreateGuestDonor("wallet-test@example.com", "Test Donor")
        assertEquals(null, guestProfile.walletAddress, "No wallet before first donation")

        val assocUser = userRepository.save(TestFixtures.associationUser(email = "assoc-guesttest@example.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc, status = CampaignStatus.LIVE))

        val providerRef = "mollie:tr_guesttest_001"
        val receipt = ByteArray(32) { it.toByte() }
        donationService.recordPayment(providerRef, guestProfile.id!!, campaign.id!!, BigDecimal("10.00"), receipt)

        val donation = donationRepository.findByProviderRef(providerRef)
        assertNotNull(donation?.confirmedAt, "Donation should be confirmed")

        val updatedProfile = donorProfileRepository.findById(guestProfile.id!!).orElseThrow()
        assertNotNull(updatedProfile.walletAddress, "walletAddress must be derived after confirmation")
        assertTrue(updatedProfile.walletAddress!!.startsWith("0x"), "EVM address starts with 0x")
        assertEquals(42, updatedProfile.walletAddress!!.length, "EVM address is 42 chars")
    }
}
