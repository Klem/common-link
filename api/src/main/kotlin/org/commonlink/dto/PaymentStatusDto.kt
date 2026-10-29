package org.commonlink.dto

import org.commonlink.payment.BankSetupStatus
import org.commonlink.payment.PaymentProviderId

/**
 * Payment-setup status of the authenticated association, computed by its payment provider.
 *
 * @property provider the association's payment provider.
 * @property status the bank-setup lifecycle state (same values as `BankSetupStatus` in `app/src/lib/bankSetupStatus.ts`).
 * @property actionUrl optional provider-side page where the association completes or repairs its onboarding.
 * @property warnings provider-specific notices to display alongside the status; empty when none.
 */
data class PaymentStatusDto(
    val provider: PaymentProviderId,
    val status: BankSetupStatus,
    val actionUrl: String?,
    val warnings: List<String>,
)
