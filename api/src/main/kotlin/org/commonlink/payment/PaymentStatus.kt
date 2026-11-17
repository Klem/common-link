package org.commonlink.payment

/**
 * Bank-setup lifecycle as displayed to an association, whatever its payment provider.
 *
 * Server-side mirror of `BankSetupStatus` in `app/src/lib/bankSetupStatus.ts`; values and meaning must stay
 * identical. Unlike [ProviderReadinessStatus], which gates collection, this is a display status: [COMPLETED]
 * means the provider finished verifying the association, not necessarily that it can collect yet.
 */
enum class BankSetupStatus {
    /** No account linked with the provider. */
    NOT_CONNECTED,

    /** Linked, but the provider requires more onboarding data before review. */
    NEEDS_DATA,

    /** Linked, data submitted, the provider is reviewing. */
    IN_REVIEW,

    /** The provider completed its verification. */
    COMPLETED,

    /** The link exists but is unusable (revoked authorisation, unreachable account); the association must re-link. */
    BROKEN,
}

/**
 * Payment-setup status of an association, as shown on its dashboard.
 *
 * @property status the bank-setup lifecycle state.
 * @property actionUrl optional provider-side page where the association completes or repairs its onboarding.
 * @property warnings optional provider-specific notices to display alongside the status.
 */
data class PaymentStatus(
    val status: BankSetupStatus,
    val actionUrl: String? = null,
    val warnings: List<String> = emptyList(),
)
