package org.commonlink.bootstrap

import org.commonlink.entity.LegalContentFormat
import org.commonlink.entity.LegalDocument
import org.commonlink.exception.LegalTextSourceException
import org.commonlink.repository.LegalDocumentRepository
import org.commonlink.service.LandingLegalTextSource
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

/**
 * Injects the landing site's CGU/CGV texts into `legal_document` at startup, when they changed.
 *
 * For each text read by [LandingLegalTextSource]:
 * - its (type, version) is absent → inserted; having the latest `published_at`, it becomes the current
 *   version immediately (associations accept it at their next login or publication);
 * - present with the same SHA-256 → nothing to do (normal restart, or a code rollback to a text already
 *   published: the newer text stays current — nothing is ever re-published as "new"; a WARN says so when
 *   the bundled text is not the current version, since reverting a wording needs a new effective date);
 * - present with another SHA-256 → [LegalTextSourceException]: the 12-hex prefix in the version collided,
 *   and storing either text under that version would falsify every acceptance proof naming it.
 *
 * Rows are never updated or deleted. Two instances booting together race on the
 * `legal_document_type_version_uq` constraint: the loser re-reads the winner's row and compares hashes.
 *
 * Disabled by `app.legal.seed-on-startup=false` (unit-test contexts seed their own documents).
 */
@Component
@ConditionalOnProperty("app.legal.seed-on-startup", havingValue = "true", matchIfMissing = true)
class LegalDocumentSeeder(
    private val source: LandingLegalTextSource,
    private val legalDocumentRepository: LegalDocumentRepository,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        for (snapshot in source.load()) seed(snapshot)
    }

    /** Inserts [snapshot] unless already stored; fails on a same-version, different-text conflict. */
    fun seed(snapshot: LandingLegalTextSource.Snapshot) {
        val existing = legalDocumentRepository.findByDocumentTypeAndVersion(snapshot.documentType, snapshot.version)
        if (existing != null) {
            requireSameText(existing, snapshot)
            val current = legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(snapshot.documentType)
            if (current != null && current.version != snapshot.version) {
                // Typically a text reverted to an earlier wording (A, then B, then A again): A's version exists,
                // so nothing is inserted and B stays in force. Re-publishing A requires a new effective date.
                logger.warn(
                    "Bundled legal document {} version {} is an older, already published version — version {} " +
                        "stays in force. To put this text back in force, change its effective date.",
                    snapshot.documentType, snapshot.version, current.version,
                )
            } else {
                logger.info("Legal document up to date: type={} version={}", snapshot.documentType, snapshot.version)
            }
            return
        }
        try {
            legalDocumentRepository.saveAndFlush(
                LegalDocument(
                    documentType = snapshot.documentType,
                    version = snapshot.version,
                    content = snapshot.content,
                    contentFormat = LegalContentFormat.JSON_V1,
                    contentSha256 = snapshot.sha256,
                )
            )
            logger.info("Legal document published: type={} version={}", snapshot.documentType, snapshot.version)
        } catch (e: DataIntegrityViolationException) {
            // Another instance inserted the same version between our read and our insert.
            val winner = legalDocumentRepository.findByDocumentTypeAndVersion(snapshot.documentType, snapshot.version)
                ?: throw e
            requireSameText(winner, snapshot)
            logger.info("Legal document published by another instance: type={} version={}", snapshot.documentType, snapshot.version)
        }
    }

    private fun requireSameText(stored: LegalDocument, snapshot: LandingLegalTextSource.Snapshot) {
        if (stored.contentSha256 != snapshot.sha256) {
            throw LegalTextSourceException(
                "Legal document ${snapshot.documentType} version ${snapshot.version} is already stored with another " +
                    "text (sha256 ${stored.contentSha256} vs ${snapshot.sha256}) — refusing to start"
            )
        }
    }
}
