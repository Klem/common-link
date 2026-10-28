package org.commonlink.payment

/**
 * Coarse state of an association's link with its payment provider, as seen by the collection gates
 * (campaign publication, widget, landing page, donation checkout).
 *
 * The campaign publication gate maps it one-to-one onto
 * [org.commonlink.entity.CampaignReviewRefusalReason]: [NOT_CONNECTED] → `BANK_NOT_CONNECTED`,
 * [BROKEN] → `BANK_CONNECTION_BROKEN`, [PENDING] → `BANK_KYC_INCOMPLETE`.
 */
enum class ProviderReadinessStatus {
    /** The association never linked an account with this provider. */
    NOT_CONNECTED,

    /** The link exists but the provider no longer accepts our credentials; the association must re-link. */
    BROKEN,

    /** The link exists but the provider does not let the association collect yet (verification under way). */
    PENDING,

    /** The association can collect donations through this provider. */
    READY,
}

/**
 * Readiness of an association with its payment provider.
 *
 * @property status coarse state used by every collection gate.
 * @property reason optional provider-specific detail, for logs and diagnostics — callers keep their own
 *   user-facing messages so that refusals stay identical whatever the provider.
 * @property actionUrl optional URL where the association can complete or repair the link on the provider's side.
 */
data class ProviderReadiness(
    val status: ProviderReadinessStatus,
    val reason: String? = null,
    val actionUrl: String? = null,
) {
    /** `true` when the association can collect donations right now. */
    val isReady: Boolean get() = status == ProviderReadinessStatus.READY
}
