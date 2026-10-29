package org.commonlink.service

import org.commonlink.dto.PaymentStatusDto
import org.commonlink.exception.UserNotFoundException
import org.commonlink.payment.PaymentProviderRegistry
import org.commonlink.repository.AssociationProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Provider-neutral payment information of the authenticated association, resolved through
 * [PaymentProviderRegistry] so the caller never needs to know which provider the association uses.
 */
@Service
class AssociationPaymentService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val paymentProviderRegistry: PaymentProviderRegistry,
) {

    /**
     * Returns the association's payment-setup status as computed by its provider. Database only: never calls the
     * provider (see [org.commonlink.payment.PaymentProvider.paymentStatus]).
     *
     * @param userId UUID of the authenticated user (JWT principal), not the association id.
     * @throws UserNotFoundException if the user has no association profile.
     */
    @Transactional(readOnly = true)
    fun getPaymentStatus(userId: UUID): PaymentStatusDto {
        val association = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        val provider = paymentProviderRegistry.forAssociation(association)
        val status = provider.paymentStatus(association.id!!)
        return PaymentStatusDto(
            provider = provider.id,
            status = status.status,
            actionUrl = status.actionUrl,
            warnings = status.warnings,
        )
    }
}
