package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStory
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.CampaignStoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class CampaignStoryServiceTest {

    private val associationProfileRepository = mockk<AssociationProfileRepository>()
    private val campaignRepository = mockk<CampaignRepository>()
    private val campaignStoryRepository = mockk<CampaignStoryRepository>()
    private val service = CampaignStoryService(associationProfileRepository, campaignRepository, campaignStoryRepository)

    private val userId: UUID = UUID.randomUUID()
    private val associationId: UUID = UUID.randomUUID()
    private val campaignId: UUID = UUID.randomUUID()

    private fun association(): AssociationProfile = mockk<AssociationProfile>(relaxed = true).also {
        every { it.id } returns associationId
    }

    private fun campaign(): Campaign = mockk<Campaign>(relaxed = true).also {
        every { it.id } returns campaignId
    }

    private fun stubOwnership() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(association())
        every { campaignRepository.findByIdAndAssociationId(campaignId, associationId) } returns Optional.of(campaign())
    }

    @Test
    fun `creates a story when the campaign has none yet`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns null
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.upsertStory(userId, campaignId, "Un texte de récit.", publish = false)

        assertEquals("Un texte de récit.", result.storyText)
        assertNull(result.publishedAt)
    }

    @Test
    fun `upsert overwrites the existing story in place, no duplicate row`() {
        stubOwnership()
        val existing = CampaignStory(campaign = campaign(), storyText = "Ancien texte", publishedAt = null)
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns existing
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.upsertStory(userId, campaignId, "Nouveau texte", publish = false)

        assertEquals("Nouveau texte", result.storyText)
        verify(exactly = 1) { campaignStoryRepository.save(existing) }
    }

    @Test
    fun `publish false never clears an already published story`() {
        stubOwnership()
        val publishedAt = Instant.now().minusSeconds(3600)
        val existing = CampaignStory(campaign = campaign(), storyText = "Texte publié", publishedAt = publishedAt)
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns existing
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.upsertStory(userId, campaignId, "Texte publié modifié", publish = false)

        assertEquals(publishedAt, result.publishedAt)
    }

    @Test
    fun `publish true sets publishedAt once and never moves it again`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns null
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.upsertStory(userId, campaignId, "Texte", publish = true)

        assertEquals(true, result.publishedAt != null)
    }

    @Test
    fun `getPublishedStory returns null when no story exists`() {
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns null

        assertNull(service.getPublishedStory(campaignId))
    }

    @Test
    fun `getPublishedStory returns null when the story is still a draft`() {
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns
            CampaignStory(campaign = campaign(), storyText = "Brouillon", publishedAt = null)

        assertNull(service.getPublishedStory(campaignId))
    }

    @Test
    fun `getPublishedStory returns the story once published`() {
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns
            CampaignStory(campaign = campaign(), storyText = "Publié", publishedAt = Instant.now())

        assertEquals("Publié", service.getPublishedStory(campaignId)?.storyText)
    }

    @Test
    fun `upsertStory throws NotFoundException when the campaign belongs to another association`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(association())
        every { campaignRepository.findByIdAndAssociationId(campaignId, associationId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) {
            service.upsertStory(userId, campaignId, "Texte", publish = false)
        }
    }

    @Test
    fun `upsertStory throws UserNotFoundException when the user has no association profile`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.empty()

        assertThrows(UserNotFoundException::class.java) {
            service.upsertStory(userId, campaignId, "Texte", publish = false)
        }
    }

    @Test
    fun `getOwnStory returns null when nothing was ever written`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns null

        assertNull(service.getOwnStory(userId, campaignId))
    }

    @Test
    fun `getOwnStory returns a draft, unlike getPublishedStory`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns
            CampaignStory(campaign = campaign(), storyText = "Brouillon", publishedAt = null)

        assertEquals("Brouillon", service.getOwnStory(userId, campaignId)?.storyText)
    }

    @Test
    fun `getOwnStory returns a published story too`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns
            CampaignStory(campaign = campaign(), storyText = "Publié", publishedAt = Instant.now())

        assertEquals("Publié", service.getOwnStory(userId, campaignId)?.storyText)
    }

    @Test
    fun `getOwnStory throws NotFoundException when the campaign belongs to another association`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(association())
        every { campaignRepository.findByIdAndAssociationId(campaignId, associationId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) {
            service.getOwnStory(userId, campaignId)
        }
    }
}
