package org.commonlink.dto

/**
 * Mollie Connect KYC connection status returned to the frontend.
 *
 * [onboardingStatus] uses Kotlin enum names (NEEDS_DATA / IN_REVIEW / COMPLETED) as per
 * decision 8 — conversion from Mollie's kebab-case values happens only in the HTTP client layer.
 */
data class MollieKycStatusDto(
    /** True if the association has an active or broken Mollie connection. */
    val connected: Boolean,
    /** True if an authorization flow was started but the callback has not completed yet. */
    val pending: Boolean,
    /** True if the connection exists but the refresh token has been rejected (invalid_grant). */
    val broken: Boolean,
    /** KYC status as a Kotlin enum name, null when not connected. */
    val onboardingStatus: String?,
    /** Whether Mollie has authorized this merchant to receive payments, null when not connected. */
    val canReceivePayments: Boolean?,
    /**
     * Deep link to the Mollie hosted onboarding wizard (_links.dashboard of GET /v2/onboarding/me).
     * Null when not connected or once onboarding is complete. The frontend opens it in a new tab
     * while the status is NEEDS_DATA.
     */
    val dashboardUrl: String?,
)
