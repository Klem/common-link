package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStory
import org.commonlink.entity.CampaignStoryImage
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.CampaignStoryImageRepository
import org.commonlink.repository.CampaignStoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import java.time.Instant
import java.util.Optional
import java.util.UUID

class CampaignStoryServiceTest {

    private val associationProfileRepository = mockk<AssociationProfileRepository>()
    private val campaignRepository = mockk<CampaignRepository>()
    private val campaignStoryRepository = mockk<CampaignStoryRepository>()
    private val campaignStoryImageRepository = mockk<CampaignStoryImageRepository>()
    private val service = CampaignStoryService(
        associationProfileRepository, campaignRepository, campaignStoryRepository, campaignStoryImageRepository,
    )

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

        val result = service.upsertStory(userId, campaignId, "Un texte de récit.", "Résumé.", publish = false)

        assertEquals("Un texte de récit.", result.storyText)
        assertEquals("Résumé.", result.storySummary)
        assertNull(result.publishedAt)
    }

    @Test
    fun `upsert overwrites the existing story in place, no duplicate row`() {
        stubOwnership()
        val existing = CampaignStory(campaign = campaign(), storyText = "Ancien texte", storySummary = "Ancien résumé", publishedAt = null)
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns existing
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.upsertStory(userId, campaignId, "Nouveau texte", "Nouveau résumé", publish = false)

        assertEquals("Nouveau texte", result.storyText)
        assertEquals("Nouveau résumé", result.storySummary)
        verify(exactly = 1) { campaignStoryRepository.save(existing) }
    }

    @Test
    fun `publish false never clears an already published story`() {
        stubOwnership()
        val publishedAt = Instant.now().minusSeconds(3600)
        val existing = CampaignStory(campaign = campaign(), storyText = "Texte publié", storySummary = "Résumé", publishedAt = publishedAt)
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns existing
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.upsertStory(userId, campaignId, "Texte publié modifié", "Résumé", publish = false)

        assertEquals(publishedAt, result.publishedAt)
    }

    @Test
    fun `publish true sets publishedAt once and never moves it again`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns null
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.upsertStory(userId, campaignId, "Texte", "Résumé", publish = true)

        assertEquals(true, result.publishedAt != null)
    }

    @Test
    fun `upsertStory strips a script tag from storyText before persisting`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns null
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.upsertStory(
            userId, campaignId,
            "<p>Bonjour</p><script>alert(1)</script>", "Résumé", publish = false,
        )

        assertFalse(result.storyText.contains("script"), result.storyText)
        assertTrue(result.storyText.contains("Bonjour"), result.storyText)
        assertFalse(saved.captured.storyText.contains("script"), saved.captured.storyText)
    }

    @Test
    fun `upsertStory strips an onerror attribute from an img tag before persisting`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns null
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.upsertStory(
            userId, campaignId,
            """<img src="/api/public/campaigns/x/story-images/y" onerror="alert(1)">""", "Résumé", publish = false,
        )

        assertFalse(result.storyText.contains("onerror"), result.storyText)
    }

    @Test
    fun `upsertStory keeps the allowed rich-text tags and the story image URL intact`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns null
        val saved = slot<CampaignStory>()
        every { campaignStoryRepository.save(capture(saved)) } answers { saved.captured }

        val html = """<h2>Titre</h2><p>Un <strong>texte</strong>.</p><img src="/api/public/campaigns/c/story-images/i">"""
        val result = service.upsertStory(userId, campaignId, html, "Résumé", publish = false)

        assertTrue(result.storyText.contains("<h2>Titre</h2>"), result.storyText)
        assertTrue(result.storyText.contains("<strong>texte</strong>"), result.storyText)
        assertTrue(result.storyText.contains("/api/public/campaigns/c/story-images/i"), result.storyText)
    }

    @Test
    fun `getPublishedStory returns null when no story exists`() {
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns null

        assertNull(service.getPublishedStory(campaignId))
    }

    @Test
    fun `getPublishedStory returns null when the story is still a draft`() {
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns
            CampaignStory(campaign = campaign(), storyText = "Brouillon", storySummary = "Résumé", publishedAt = null)

        assertNull(service.getPublishedStory(campaignId))
    }

    @Test
    fun `getPublishedStory returns the story once published`() {
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns
            CampaignStory(campaign = campaign(), storyText = "Publié", storySummary = "Résumé", publishedAt = Instant.now())

        assertEquals("Publié", service.getPublishedStory(campaignId)?.storyText)
    }

    @Test
    fun `upsertStory throws NotFoundException when the campaign belongs to another association`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(association())
        every { campaignRepository.findByIdAndAssociationId(campaignId, associationId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) {
            service.upsertStory(userId, campaignId, "Texte", "Résumé", publish = false)
        }
    }

    @Test
    fun `upsertStory throws UserNotFoundException when the user has no association profile`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.empty()

        assertThrows(UserNotFoundException::class.java) {
            service.upsertStory(userId, campaignId, "Texte", "Résumé", publish = false)
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
            CampaignStory(campaign = campaign(), storyText = "Brouillon", storySummary = "Résumé", publishedAt = null)

        assertEquals("Brouillon", service.getOwnStory(userId, campaignId)?.storyText)
    }

    @Test
    fun `getOwnStory returns a published story too`() {
        stubOwnership()
        every { campaignStoryRepository.findByCampaignId(campaignId) } returns
            CampaignStory(campaign = campaign(), storyText = "Publié", storySummary = "Résumé", publishedAt = Instant.now())

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

    // ── Story images ─────────────────────────────────────────────────────────

    private fun pngFile(name: String = "photo.png", sizeBytes: Int = 100): MockMultipartFile =
        MockMultipartFile("file", name, "image/png", ByteArray(sizeBytes))

    @Test
    fun `uploadStoryImage stores the image and returns its public URL`() {
        stubOwnership()
        val imageId = UUID.randomUUID()
        val saved = slot<CampaignStoryImage>()
        every { campaignStoryImageRepository.save(capture(saved)) } answers {
            CampaignStoryImage(
                id = imageId, campaignId = campaignId, data = saved.captured.data,
                contentType = saved.captured.contentType, sizeBytes = saved.captured.sizeBytes,
            )
        }

        val result = service.uploadStoryImage(userId, campaignId, pngFile())

        assertEquals(imageId.toString(), result.id)
        assertEquals("/api/public/campaigns/$campaignId/story-images/$imageId", result.url)
    }

    @Test
    fun `uploadStoryImage rejects an empty file`() {
        stubOwnership()
        val empty = MockMultipartFile("file", "photo.png", "image/png", ByteArray(0))

        assertThrows(UnprocessableEntityException::class.java) {
            service.uploadStoryImage(userId, campaignId, empty)
        }
    }

    @Test
    fun `uploadStoryImage rejects an oversized file`() {
        stubOwnership()
        val big = MockMultipartFile("file", "photo.png", "image/png", ByteArray(6 * 1024 * 1024))

        assertThrows(UnprocessableEntityException::class.java) {
            service.uploadStoryImage(userId, campaignId, big)
        }
    }

    @Test
    fun `uploadStoryImage rejects an unsupported MIME type`() {
        stubOwnership()
        val pdf = MockMultipartFile("file", "doc.pdf", "application/pdf", ByteArray(100))

        assertThrows(UnprocessableEntityException::class.java) {
            service.uploadStoryImage(userId, campaignId, pdf)
        }
    }

    @Test
    fun `uploadStoryImage throws NotFoundException when the campaign belongs to another association`() {
        every { associationProfileRepository.findByUserId(userId) } returns Optional.of(association())
        every { campaignRepository.findByIdAndAssociationId(campaignId, associationId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) {
            service.uploadStoryImage(userId, campaignId, pngFile())
        }
    }

    @Test
    fun `getStoryImage returns the sniffed content type and bytes for a matching campaign`() {
        val imageId = UUID.randomUUID()
        // Real PNG signature bytes so FileTypeSniffer recognises it -- an all-zero buffer
        // would fail the sniff and the service would (correctly) 404 it.
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val image = CampaignStoryImage(
            id = imageId, campaignId = campaignId, data = pngBytes, contentType = "image/png", sizeBytes = pngBytes.size.toLong(),
        )
        every { campaignStoryImageRepository.findById(imageId) } returns Optional.of(image)

        val (contentType, data) = service.getStoryImage(campaignId, imageId)

        assertEquals("image/png", contentType)
        assertEquals(pngBytes.size, data.size)
    }

    @Test
    fun `getStoryImage throws NotFoundException when the image belongs to a different campaign`() {
        val imageId = UUID.randomUUID()
        val otherCampaignId = UUID.randomUUID()
        val image = CampaignStoryImage(
            id = imageId, campaignId = otherCampaignId, data = ByteArray(10), contentType = "image/png", sizeBytes = 10,
        )
        every { campaignStoryImageRepository.findById(imageId) } returns Optional.of(image)

        assertThrows(NotFoundException::class.java) {
            service.getStoryImage(campaignId, imageId)
        }
    }

    @Test
    fun `getStoryImage throws NotFoundException when no such image exists`() {
        val imageId = UUID.randomUUID()
        every { campaignStoryImageRepository.findById(imageId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) {
            service.getStoryImage(campaignId, imageId)
        }
    }
}
