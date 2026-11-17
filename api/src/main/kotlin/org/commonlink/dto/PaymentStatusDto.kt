package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.commonlink.payment.BankSetupStatus
import org.commonlink.payment.HelloAssoWebhookState
import org.commonlink.payment.PaymentProviderId
import java.time.Instant

/**
 * Payment-setup status of the authenticated association, computed by its payment provider.
 *
 * @property provider the association's payment provider.
 * @property status the bank-setup lifecycle state (same values as `BankSetupStatus` in `app/src/lib/bankSetupStatus.ts`).
 * @property actionUrl optional provider-side page where the association completes or repairs its onboarding.
 * @property warnings provider-specific notices to display alongside the status; empty when none.
 * @property helloasso HelloAsso link details, for a HELLOASSO association only; absent from the JSON otherwise, so
 *   a Mollie response is unchanged.
 */
data class PaymentStatusDto(
    val provider: PaymentProviderId,
    val status: BankSetupStatus,
    val actionUrl: String?,
    val warnings: List<String>,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val helloasso: HelloAssoStatusDto? = null,
)

/** HelloAsso link as shown to the association: `NONE` (not linked or disconnected), `OK` (active), `BROKEN`. */
enum class HelloAssoLinkState { NONE, OK, BROKEN }

/**
 * HelloAsso link details of the neutral status (flow §10.5, brief §2.2), read from the database only. Never the
 * secret nor a token.
 *
 * @property link link state.
 * @property notificationUrl URL to paste in the HelloAsso back office; null until created or when not configured.
 * @property organizationSlug linked HelloAsso organization.
 * @property canCollect last eligibility read (`isCashInCompliant`).
 * @property everCompliant whether the organization could ever collect (tells "pending" from "suspended").
 * @property lastEligibilityCheckAt last eligibility read.
 * @property webhook whether HelloAsso notifications reach CommonLink.
 * @property clientIdMasked `abcd…wxyz` of the linked key.
 * @property linkedAt when the account was linked.
 * @property linkedBy email of the user who linked it.
 * @property secretWipedAt when the client secret used for the link was discarded.
 * @property lastRefreshAt last token renewal.
 */
data class HelloAssoStatusDto(
    val link: HelloAssoLinkState,
    val notificationUrl: String? = null,
    val organizationSlug: String? = null,
    val canCollect: Boolean = false,
    val everCompliant: Boolean = false,
    val lastEligibilityCheckAt: Instant? = null,
    val webhook: HelloAssoWebhookState,
    val clientIdMasked: String? = null,
    val linkedAt: Instant? = null,
    val linkedBy: String? = null,
    val secretWipedAt: Instant? = null,
    val lastRefreshAt: Instant? = null,
)
