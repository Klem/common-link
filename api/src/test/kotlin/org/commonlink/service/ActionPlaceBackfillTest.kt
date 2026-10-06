package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.ActionPlaceType
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.TestFixtures
import org.junit.jupiter.api.Test

class ActionPlaceBackfillTest {

    private val campaignRepository = mockk<CampaignRepository>()
    private val geo = mockk<GeoApiClient>()
    private val backfill = ActionPlaceBackfill(campaignRepository, ActionPlaceService(geo))

    private val paris = TestFixtures.associationProfile(TestFixtures.associationUser(), city = "Paris", postalCode = "75001")
    private val nowhere = TestFixtures.associationProfile(
        TestFixtures.associationUser(email = "b@example.com"), identifier = "123456789", city = null, postalCode = null,
    )

    @Test
    fun `fills campaigns from the association address and geocodes each address once`() {
        val c1 = TestFixtures.campaign(paris, name = "A")
        val c2 = TestFixtures.campaign(paris, name = "B")
        val c3 = TestFixtures.campaign(nowhere, name = "C")
        every { campaignRepository.findAllWithoutActionPlace() } returns listOf(c1, c2, c3)
        every { geo.findCommunesByPostalCode("75001") } returns
            listOf(GeoCommune("75101", "Paris 1er Arrondissement", "75", 48.86, 2.34), GeoCommune("75056", "Paris", "75", 48.85, 2.35))

        backfill.run()

        assertThat(c1.actionPlaceType).isEqualTo(ActionPlaceType.COMMUNE)
        assertThat(c1.actionPlaceCode).isEqualTo("75056")
        assertThat(c2.actionPlaceLabel).isEqualTo("Paris (75)")
        assertThat(c3.actionPlaceType).isNull()
        verify(exactly = 1) { geo.findCommunesByPostalCode("75001") }
    }
}
