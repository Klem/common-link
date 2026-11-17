package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.AssociationProfile
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationDocumentMetadata
import org.commonlink.repository.AssociationDocumentRepository
import org.commonlink.repository.AssociationProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class KybDocumentServiceTest {

    private val profileRepo: AssociationProfileRepository = mockk()
    private val documentRepo: AssociationDocumentRepository = mockk()
    private val service = KybDocumentService(profileRepo, documentRepo)

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val associationId = UUID.fromString("00000000-0000-0000-0000-000000000042")

    private fun meta(type: AssociationDocumentType, id: UUID? = UUID.randomUUID()): AssociationDocumentMetadata {
        val m: AssociationDocumentMetadata = mockk()
        every { m.docType } returns type
        every { m.id } returns id
        return m
    }

    @Test
    fun `lists the provided KYB slots only, in display order`() {
        val rna = meta(AssociationDocumentType.VERIF_RNA_RECEIPT)
        val statutes = meta(AssociationDocumentType.VERIF_STATUTS)
        val other = meta(AssociationDocumentType.entries.first { it !in KybDocumentService.PROVIDED_DOCUMENT_TYPES })
        every { documentRepo.findMetadataByAssociationId(associationId) } returns listOf(rna, other, statutes)

        val result = service.listProvided(associationId)

        assertEquals(
            listOf(AssociationDocumentType.VERIF_STATUTS, AssociationDocumentType.VERIF_RNA_RECEIPT),
            result.map { it.type },
        )
        assertEquals(statutes.id, result[0].id)
    }

    @Test
    fun `resolves the association of the user`() {
        val profile: AssociationProfile = mockk()
        every { profile.id } returns associationId
        every { profileRepo.findByUserId(userId) } returns Optional.of(profile)
        every { documentRepo.findMetadataByAssociationId(associationId) } returns emptyList()

        assertEquals(emptyList<Any>(), service.listProvidedForUser(userId))
    }

    @Test
    fun `404 without an association profile`() {
        every { profileRepo.findByUserId(userId) } returns Optional.empty()

        assertThrows<NotFoundException> { service.listProvidedForUser(userId) }
    }
}
