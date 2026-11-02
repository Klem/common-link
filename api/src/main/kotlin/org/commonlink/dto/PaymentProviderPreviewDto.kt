package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.commonlink.payment.PaymentProviderId
import org.commonlink.payment.ProviderReadinessStatus
import org.commonlink.service.PaymentProviderChangeService
import java.util.UUID

/**
 * What a curator sees before changing an association's payment provider: the current provider and the impact of
 * a change.
 *
 * @property associationId the association.
 * @property currentProvider provider that takes the association's new payments.
 * @property providers every known provider with the association's readiness there.
 * @property liveCampaigns campaigns currently LIVE (they stop collecting until the target provider is ready).
 * @property widgetEnabled whether the association's donation widget is active.
 * @property pendingDonations donations of the current provider still payable (created within the reservation TTL);
 *   they keep being settled by that provider after a change.
 */
data class PaymentProviderPreviewDto(
    val associationId: UUID,
    val currentProvider: PaymentProviderId,
    val providers: List<PaymentProviderOptionDto>,
    val liveCampaigns: Long,
    val widgetEnabled: Boolean,
    val pendingDonations: Long,
)

/**
 * One payment provider in a [PaymentProviderPreviewDto].
 *
 * @property provider the provider.
 * @property readiness the association's readiness with that provider; null when the provider has no implementation
 *   yet (HelloAsso before its adapter ships), i.e. it cannot collect.
 */
data class PaymentProviderOptionDto(
    val provider: PaymentProviderId,
    val readiness: ProviderReadinessStatus?,
)

/**
 * Body of `PUT /api/admin/associations/{id}/payment-provider`.
 *
 * @property provider target provider; the current one makes the call a no-op.
 * @property reason why the curator changes the provider; mandatory, kept in the compliance journal only.
 */
data class ChangePaymentProviderRequest(
    val provider: PaymentProviderId,
    @field:NotBlank
    @field:Size(max = PaymentProviderChangeService.REASON_MAX_LENGTH)
    val reason: String,
)
