package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.exception.HelloAssoNotAssociationProviderException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.HelloAssoConnectionRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

/**
 * Shared rules of the per-association HelloAsso row: created once with a random notification token that never changes
 * afterwards (decision 2026-10-30), and HelloAsso steps reserved to HELLOASSO associations (decision 2026-11-02).
 */
@Component
class HelloAssoConnectionRows(private val connectionRepository: HelloAssoConnectionRepository) {

    private val random = SecureRandom()

    /**
     * Returns the association's HelloAsso row, creating it (NOT_LINKED, random token) on first need. A concurrent
     * creation keeps the row that won: its token is the stable one.
     */
    fun findOrCreate(association: AssociationProfile): HelloAssoConnection =
        connectionRepository.findByAssociationId(association.id!!) ?: try {
            connectionRepository.saveAndFlush(HelloAssoConnection(association = association, webhookToken = newToken()))
        } catch (ex: DataIntegrityViolationException) {
            connectionRepository.findByAssociationId(association.id) ?: throw ex
        }

    /** @throws HelloAssoNotAssociationProviderException when the association does not collect through HelloAsso. */
    fun requireHelloAssoProvider(association: AssociationProfile) {
        if (association.paymentProvider != PaymentProviderId.HELLOASSO) throw HelloAssoNotAssociationProviderException()
    }

    /** 256-bit random, URL-safe, unpadded (43 characters). */
    private fun newToken(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { random.nextBytes(it) })
}
