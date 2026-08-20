package org.commonlink.service

import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.SanctionedNature
import org.commonlink.repository.FreezeScreeningMatchRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID

/**
 * Guards the one property that makes [FreezeScreeningEvidenceRecorder] worth being a bean: the
 * evidence must survive the rollback of the transaction that screened.
 *
 * ### Why a Spring context and not a mock
 *
 * The defect this test exists for shipped with a passing unit test. A hit is always recorded on a
 * path about to fail — `VerificationService.adminApprove` is `@Transactional` and throws
 * `ConflictException` on `HIT` — so `saveAll()`, which only queues an INSERT for flush-at-commit,
 * lost every row to the rollback. A mocked repository records the call and returns; it cannot
 * distinguish "queued then discarded" from "committed". Only a real transaction manager can.
 *
 * The failure was total and silent: the journal kept the hit (it writes in `REQUIRES_NEW`) while
 * `freeze_screening_match` stayed empty, so the compliance officer read "3 correspondences, top
 * score 0.93" without a single register entry to name — no decision was motivable.
 *
 * Rows committed here outlive the test method by design; [cleanUp] removes them.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
class FreezeScreeningEvidenceRecorderTest {

    @Autowired private lateinit var recorder: FreezeScreeningEvidenceRecorder
    @Autowired private lateinit var matchRepository: FreezeScreeningMatchRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val subjectId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val associationId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000a2")
    private val publicationDate: LocalDate = LocalDate.of(2026, 8, 12)

    @AfterEach
    fun cleanUp() {
        matchRepository.deleteAll()
    }

    private fun match(score: Double, idRegistre: Int, legalReference: String? = null) = ScreeningMatch(
        idRegistre = idRegistre,
        nom = "LISTED ENTITY $idRegistre",
        nature = SanctionedNature.LEGAL_ENTITY,
        score = score,
        dateOfBirth = null,
        legalReference = legalReference,
    )

    private fun record(matches: List<ScreeningMatch>) = recorder.record(
        auditLogSeqRef = 4242L,
        subjectType = ComplianceAuditSubjectType.ASSOCIATION,
        subjectId = subjectId,
        associationId = associationId,
        screenedName = "TECHNO +",
        matches = matches,
        publicationDate = publicationDate,
    )

    /**
     * The exact shape of the production path: screen, record, then refuse by throwing from inside
     * the caller's transaction.
     */
    @Test
    fun `evidence survives the rollback of the transaction that screened`() {
        val template = TransactionTemplate(transactionManager)

        assertThrows(IllegalStateException::class.java) {
            template.execute {
                record(listOf(match(0.9333, 1), match(0.87, 2)))
                // Stands for the ConflictException adminApprove throws on a HIT.
                throw IllegalStateException("approval refused — freeze register hit")
            }
        }

        val saved = matchRepository.findBySubjectIdOrderByScoreDesc(subjectId)
        assertEquals(2, saved.size)
        assertEquals(listOf(0.9333, 0.87), saved.map { it.score })
        saved.forEach {
            assertEquals(4242L, it.auditLogSeqRef)
            assertEquals(associationId, it.associationId)
            assertEquals(publicationDate, it.registryPublicationDate)
        }
    }

    /**
     * An association-scoped alert is resolved through `associationId`, so a correspondence
     * recorded for a representative must remain reachable from the association that carries the
     * alert — see `ComplianceController.resolveMatches`.
     */
    @Test
    fun `correspondence recorded for a representative stays reachable from the association`() {
        val repId = UUID.fromString("00000000-0000-0000-0000-0000000000b1")

        recorder.record(
            auditLogSeqRef = 7L,
            subjectType = ComplianceAuditSubjectType.REPRESENTATIVE,
            subjectId = repId,
            associationId = associationId,
            screenedName = "Jean Dupont",
            matches = listOf(match(0.91, 3)),
            publicationDate = publicationDate,
        )

        val byAssociation = matchRepository.findByAssociationIdOrderByScoreDesc(associationId)
        assertEquals(1, byAssociation.size)
        assertEquals(repId, byAssociation.first().subjectId)
    }

    /**
     * `matched_legal_reference` is the decisive field of a false-positive ruling: it names the
     * sanctions programme the entry falls under. It shipped structurally NULL — [ScreeningMatch]
     * carried no such field, so nothing could ever populate the column the schema and the KDoc
     * both advertised.
     */
    @Test
    fun `the legal reference of the matched entry is snapshotted`() {
        record(listOf(match(0.9333, 1, legalReference = "Règlement (UE) 2026/509")))

        val saved = matchRepository.findBySubjectIdOrderByScoreDesc(subjectId).single()
        assertEquals("Règlement (UE) 2026/509", saved.matchedLegalReference)
    }

    /** The stored value is the one that produced the score: "TECHNO +" is compared as "TECHNO". */
    @Test
    fun `the normalized value actually compared is stored, not the raw name`() {
        record(listOf(match(0.9333, 1)))

        val saved = matchRepository.findBySubjectIdOrderByScoreDesc(subjectId).single()
        assertEquals("TECHNO", saved.screenedNormalizedName)
    }

    /**
     * No correspondence, no row. The guard in [FreezeScreeningEvidenceRecorder.record] spares the
     * normalization and the write, not the transaction — the interceptor runs before the method
     * body, so the empty call still opens and commits one. Both callers only record on a non-empty
     * match set; the guard is there for the next one.
     */
    @Test
    fun `an empty match list writes nothing`() {
        record(emptyList())

        assertEquals(0, matchRepository.findBySubjectIdOrderByScoreDesc(subjectId).size)
    }
}
