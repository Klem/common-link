package org.commonlink.event

import org.commonlink.entity.MollieOnboardingStatus
import java.util.UUID

/**
 * Published by [org.commonlink.service.MollieConnectService] whenever the Mollie KYC onboarding
 * status of an association changes during a throttled capabilities poll.
 *
 * Consumed by [org.commonlink.service.MollieConnectEmailListener] to send a transition email
 * asynchronously, including regressions (e.g. IN_REVIEW → NEEDS_DATA).
 */
data class MollieOnboardingStatusChangedEvent(
    val associationId: UUID,
    /** Status before the poll — null is not used (transitions only happen on existing connections). */
    val previousStatus: MollieOnboardingStatus?,
    val newStatus: MollieOnboardingStatus,
)
