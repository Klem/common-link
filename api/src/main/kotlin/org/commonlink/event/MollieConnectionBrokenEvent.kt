package org.commonlink.event

import java.util.UUID

/**
 * Published once when Mollie definitively rejects an association's refresh token and the
 * connection transitions ACTIVE → BROKEN (see
 * [org.commonlink.service.MollieTokenRefreshExecutor]).
 *
 * Deliberately a distinct event rather than a widened
 * [MollieOnboardingStatusChangedEvent]: a broken authorisation is not a KYC progression, its
 * remedy is different (re-run the OAuth popup, not submit more data), and Mollie's own
 * onboarding status stays COMPLETED throughout.
 *
 * Consumed by [org.commonlink.service.MollieConnectEmailListener] to warn the association that
 * it has stopped being able to collect donations.
 */
data class MollieConnectionBrokenEvent(
    val associationId: UUID,
)
