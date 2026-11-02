package org.commonlink.event

import org.commonlink.payment.PaymentProviderId
import java.util.UUID

/**
 * Published when a curator moves an association to another payment provider
 * ([org.commonlink.service.PaymentProviderChangeService.change]). Never published for a no-op change.
 *
 * Consumed after commit by [org.commonlink.service.PaymentProviderChangeEmailListener] to inform the association.
 * Carries no reason: the curator's reason is internal and stays in the compliance journal.
 */
data class PaymentProviderChangedEvent(
    val associationId: UUID,
    val from: PaymentProviderId,
    val to: PaymentProviderId,
)
