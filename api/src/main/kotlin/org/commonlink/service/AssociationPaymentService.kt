package org.commonlink.service

import org.commonlink.dto.PaymentStatusDto
import org.commonlink.exception.UserNotFoundException
import org.commonlink.payment.BankSetupStatus
import org.commonlink.payment.PaymentProviderRegistry
import org.commonlink.repository.AssociationProfileRepository
import org.springframework.stereotype.Service
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
     * Returns the association's payment-setup status as computed by its provider. The provider first refreshes a
     * stale stored status ([org.commonlink.payment.PaymentProvider.refreshStatusIfStale], throttled, never throws),
     * then the status is read from the database ([org.commonlink.payment.PaymentProvider.paymentStatus]) — the same
     * freshness as `GET /api/mollie/connect/status` for a Mollie association.
     *
     * When the association's provider has no implementation yet (HelloAsso before its adapter ships), returns
     * that provider with [BankSetupStatus.NOT_CONNECTED] and no refresh — never another provider's status.
     *
     * Deliberately not wrapped in one transaction: the refresh commits in its own transactions, and the following
     * read must see them.
     *
     * @param userId UUID of the authenticated user (JWT principal), not the association id.
     * @throws UserNotFoundException if the user has no association profile.
     */
    fun getPaymentStatus(userId: UUID): PaymentStatusDto {
        val association = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        val provider = paymentProviderRegistry.findForAssociation(association)
            ?: return PaymentStatusDto(
                provider = association.paymentProvider,
                status = BankSetupStatus.NOT_CONNECTED,
                actionUrl = null,
                warnings = emptyList(),
            )
        val associationId = association.id!!
        provider.refreshStatusIfStale(associationId)
        val status = provider.paymentStatus(associationId)
        return PaymentStatusDto(
            provider = provider.id,
            status = status.status,
            actionUrl = status.actionUrl,
            warnings = status.warnings,
        )
    }
}
