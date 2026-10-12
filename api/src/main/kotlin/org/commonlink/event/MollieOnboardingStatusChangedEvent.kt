package org.commonlink.event

import org.commonlink.entity.MollieOnboardingStatus
import java.util.UUID

/**
 * Published by [org.commonlink.service.MollieOnboardingSnapshotWriter.writePolled] whenever a poll
 * of Mollie (throttled status read, scheduled sweep or forced refresh) observes a change of the
 * onboarding status **or** of `canReceivePayments`. A payments-only change is published with
 * [previousStatus] == [newStatus] (e.g. COMPLETED → COMPLETED when payments get enabled).
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
