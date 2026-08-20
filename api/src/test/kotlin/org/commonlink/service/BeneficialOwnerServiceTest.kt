package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.dto.AddBeneficialOwnerRequest
import org.commonlink.entity.BeneficialOwner
import org.commonlink.entity.BeneficialOwnerOrigin
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BeneficialOwnerServiceTest {

    private val repository: BeneficialOwnerRepository = mockk()
    private val associationProfileRepository: AssociationProfileRepository = mockk()
    private val complianceAuditLogService: ComplianceAuditLogService = mockk()

    private val service = BeneficialOwnerService(repository, associationProfileRepository, complianceAuditLogService)

    private val associationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val curatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val ownerId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private val auditLogDummy: ComplianceAuditLog = mockk()

    // ─── addOwner ─────────────────────────────────────────────────────────────

    @Test
    fun `addOwner logs UBO_CONFIRMED event with association subject`() {
        every { associationProfileRepository.existsById(associationId) } returns true
        val capturedEventType = slot<String>()
        every {
            complianceAuditLogService.append(
                eventType = capture(capturedEventType),
                subjectType = any(),
                payload = any(),
                subjectId = any(),
                actorUserId = any(),
            )
        } returns auditLogDummy
        every { repository.save(any()) } answers {
            val bo = firstArg<BeneficialOwner>()
            BeneficialOwner(
                id = ownerId,
                associationId = bo.associationId,
                name = bo.name,
                role = bo.role,
                dateOfBirth = bo.dateOfBirth,
                origin = bo.origin,
                collectedAt = bo.collectedAt,
                confirmedBy = bo.confirmedBy,
            )
        }

        service.addOwner(
            associationId,
            AddBeneficialOwnerRequest(name = "Jean Martin", origin = BeneficialOwnerOrigin.REGISTRY),
            curatorId,
        )

        assertThat(capturedEventType.captured).isEqualTo("UBO_CONFIRMED")
        verify(exactly = 1) {
            complianceAuditLogService.append(
                eventType = "UBO_CONFIRMED",
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                subjectId = associationId,
                actorUserId = curatorId,
                payload = any(),
            )
        }
    }

    @Test
    fun `addOwner throws NotFoundException when association does not exist`() {
        every { associationProfileRepository.existsById(associationId) } returns false

        assertThrows(NotFoundException::class.java) {
            service.addOwner(
                associationId,
                AddBeneficialOwnerRequest(name = "Jean Martin", origin = BeneficialOwnerOrigin.REGISTRY),
                curatorId,
            )
        }
    }

    // ─── discardOwner ─────────────────────────────────────────────────────────

    @Test
    fun `discardOwner logs UBO_DISCARDED event`() {
        val owner = BeneficialOwner(
            id = ownerId,
            associationId = associationId,
            name = "Jean Martin",
            origin = BeneficialOwnerOrigin.REGISTRY,
            collectedAt = Instant.now(),
            confirmedBy = curatorId,
        )
        every { repository.findById(ownerId) } returns Optional.of(owner)
        every { repository.save(any()) } returns owner
        val capturedEventType = slot<String>()
        every {
            complianceAuditLogService.append(
                eventType = capture(capturedEventType),
                subjectType = any(),
                payload = any(),
                subjectId = any(),
                actorUserId = any(),
            )
        } returns auditLogDummy

        service.discardOwner(associationId, ownerId, curatorId)

        assertThat(capturedEventType.captured).isEqualTo("UBO_DISCARDED")
        verify(exactly = 1) {
            complianceAuditLogService.append(
                eventType = "UBO_DISCARDED",
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                subjectId = associationId,
                actorUserId = curatorId,
                payload = any(),
            )
        }
    }

    @Test
    fun `discardOwner throws ConflictException when owner is already discarded`() {
        val owner = BeneficialOwner(
            id = ownerId,
            associationId = associationId,
            name = "Jean Martin",
            origin = BeneficialOwnerOrigin.REGISTRY,
            collectedAt = Instant.now(),
            confirmedBy = curatorId,
            discarded = true,
        )
        every { repository.findById(ownerId) } returns Optional.of(owner)

        assertThrows(ConflictException::class.java) {
            service.discardOwner(associationId, ownerId, curatorId)
        }
    }

    @Test
    fun `discardOwner throws NotFoundException when owner belongs to a different association`() {
        val otherAssociationId = UUID.randomUUID()
        val owner = BeneficialOwner(
            id = ownerId,
            associationId = otherAssociationId,
            name = "Jean Martin",
            origin = BeneficialOwnerOrigin.REGISTRY,
            collectedAt = Instant.now(),
            confirmedBy = curatorId,
        )
        every { repository.findById(ownerId) } returns Optional.of(owner)

        assertThrows(NotFoundException::class.java) {
            service.discardOwner(associationId, ownerId, curatorId)
        }
    }
}
