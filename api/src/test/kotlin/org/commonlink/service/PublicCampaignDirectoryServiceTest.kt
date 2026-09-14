package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.PublicCampaignRow
import org.commonlink.repository.CampaignRepository
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.util.UUID

/**
 * Vérifie la seule logique que le service ajoute à la requête : la construction de l'URL de don
 * et la borne dure sur le nombre de lignes.
 */
class PublicCampaignDirectoryServiceTest {

    private val campaignRepository = mockk<CampaignRepository>()

    private fun row(widgetToken: String = "clk_abc") = PublicCampaignRow(
        campaignId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        campaignName = "Hiver Solidaire",
        campaignEmoji = "🍽",
        campaignCategory = "Solidarité",
        coverImage = "/api/public/campaigns/00000000-0000-0000-0000-000000000001/cover",
        goal = BigDecimal("10000"),
        raised = BigDecimal("4200"),
        milestoneCount = 4,
        associationName = "Les Restos du Coeur",
        associationLogo = null,
        widgetToken = widgetToken,
    )

    private fun service(frontendUrl: String) =
        PublicCampaignDirectoryService(campaignRepository, frontendUrl)

    @Test
    fun `listLive construit l'URL de don sur le frontend-url configure`() {
        every { campaignRepository.findPublicLive(any()) } returns listOf(row())

        val dto = service("https://app.common-link.org").listLive().single()

        assertThat(dto.donationUrl).isEqualTo("https://app.common-link.org/fr/lp/clk_abc")
        assertThat(dto.campaignName).isEqualTo("Hiver Solidaire")
        assertThat(dto.milestoneCount).isEqualTo(4)
    }

    @Test
    fun `listLive tolere un frontend-url avec slash final`() {
        every { campaignRepository.findPublicLive(any()) } returns listOf(row())

        val dto = service("http://localhost:3000/").listLive().single()

        assertThat(dto.donationUrl).isEqualTo("http://localhost:3000/fr/lp/clk_abc")
    }

    @Test
    fun `listLive borne la requete a 60 lignes`() {
        val pageable = slot<Pageable>()
        every { campaignRepository.findPublicLive(capture(pageable)) } returns emptyList()

        assertThat(service("http://localhost:3000").listLive()).isEmpty()

        verify(exactly = 1) { campaignRepository.findPublicLive(any()) }
        assertThat(pageable.captured.pageNumber).isZero()
        assertThat(pageable.captured.pageSize).isEqualTo(60)
    }
}
