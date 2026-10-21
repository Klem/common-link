package org.commonlink.service

import org.commonlink.event.MollieOnboardingStatusChangedEvent
import org.commonlink.repository.MollieConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Registers an association on-chain as soon as it becomes able to collect donations
 * ([org.commonlink.entity.MollieConnection.canCollectDonations]).
 *
 * Registry v2 has no association lifecycle: the registration is permanent. A later regression (Mollie status
 * leaving COMPLETED, broken connection, freeze) records nothing on-chain — stopping the collection is a backend
 * decision — and a return to COMPLETED enqueues nothing new (idempotent correlation key).
 *
 * The Mollie paths that publish no [MollieOnboardingStatusChangedEvent] (OAuth callback, mock connect,
 * dev `force-complete`) are covered anyway: publishing a campaign requires a collecting association and
 * registers the association first ([OnchainRegistrationService.ensureCampaignRegistered]).
 */
@Component
class OnchainAssociationRegistrationListener(
    private val connectionRepository: MollieConnectionRepository,
    private val registration: OnchainRegistrationService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Runs after the poll transaction committed, in its own transaction, so a registration failure never
     * rolls back the Mollie status snapshot.
     *
     * @param event status (or `canReceivePayments`) change of an association's Mollie connection.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onMollieStatusChanged(event: MollieOnboardingStatusChangedEvent) {
        val connection = connectionRepository.findByAssociationId(event.associationId) ?: return
        if (!connection.canCollectDonations()) return
        val job = registration.ensureAssociationRegistered(connection.association)
        logger.info("Association {} can collect: on-chain registration job {}", event.associationId, job?.id)
    }
}
