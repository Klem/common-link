package org.commonlink.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.config.BridgeProperties
import org.commonlink.config.BridgeRestClientConfig
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.exception.BadGatewayException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A Bridge payment link created for a payout. */
data class BridgePaymentLink(
    /** Bridge payment-link id — the key its webhook notification carries. */
    val id: String,
    /** URL the association must open to authorise the transfer at its own bank. */
    val url: String,
)

/** State of a Bridge payment link, as read back from Bridge. */
data class BridgePaymentLinkState(
    val status: BridgePaymentStatus,
    /** Bridge transaction id, present once the association has authenticated at its bank. */
    val transactionId: String?,
    /** Bridge's reason for a rejection, e.g. `debit_account_insufficient_funds`. */
    val statusReason: String?,
)

/**
 * Initiates outgoing SEPA transfers through Bridge's Open Banking payment initiation, with a
 * **dynamic beneficiary**.
 *
 * The association is the debtor: it authorises the transfer with its own bank, and the funds move
 * directly from its account to the payee's IBAN. Bridge never holds the money, and CommonLink needs
 * no payment account of its own. The payee IBAN is passed inline in
 * `transactions[].beneficiary.iban`, so no beneficiary has to be pre-registered anywhere.
 *
 * Endpoints used:
 * - `POST /v3/payment/payment-links` — create the initiation and get the bank-authorisation URL
 * - `GET  /v3/payment/payment-links/{id}` — read the authoritative state
 *
 * Status is driven by Bridge's webhook. Because Bridge documents no webhook signature, the
 * notification is treated as a bare trigger and the state is always re-read with [getPaymentLink]
 * — the body is never trusted. This mirrors [MollieWebhookService]'s handling.
 *
 * When [BridgeProperties.demoMode] is true (the default) Bridge is never contacted: a synthetic
 * link is returned and reported as settled, so the payout journey can be exercised end-to-end
 * without credentials. Flipping `app.bridge.demo-mode` to false is the only change needed once
 * sandbox credentials are provisioned.
 *
 * @param props Bridge configuration; see [BridgeProperties].
 * @param objectMapper Jackson mapper used to parse Bridge responses.
 * @param restClient Client pointed at [BridgeProperties.baseUrl], carrying the Bridge timeouts
 *   (see [BridgeRestClientConfig]).
 */
@Service
class BridgePaymentInitiationService(
    private val props: BridgeProperties,
    private val objectMapper: ObjectMapper,
    @Qualifier("bridgeRestClient") private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(BridgePaymentInitiationService::class.java)

    init {
        if (!props.demoMode) {
            require(props.clientId.isNotBlank() && props.clientSecret.isNotBlank()) {
                "app.bridge.client-id and app.bridge.client-secret are required when demo-mode is false " +
                    "— set the BRIDGE_CLIENT_ID / BRIDGE_CLIENT_SECRET env vars"
            }
        }
    }

    /** Whether transfers are simulated rather than initiated through Bridge. */
    val isDemoMode: Boolean get() = props.demoMode

    /**
     * Creates a payment link that will debit the association and credit [payeeIban].
     *
     * [payoutId] is sent as `client_reference` at both link and transaction level, so a transfer
     * can always be traced back to the payout it settles — including from a bank statement.
     *
     * @param payoutId CommonLink payout id, used as the reconciliation reference.
     * @param payerName Name of the association paying — required by Bridge
     * @param payerReference Association id, echoed as `user.external_reference`.
     * @param payeeName Beneficiary name shown to the association and sent to the bank.
     * @param payeeIban Beneficiary IBAN — the dynamic beneficiary, credited directly.
     * @param amount Transfer amount in euros.
     * @param label Statement label; truncated to Bridge's 50-character limit for transactions.
     * @param senderIban The association's own IBAN, pre-filled as the debtor account when known.
     * @param callbackUrl Where Bridge returns the association after the bank flow.
     * @return the created link and the URL to redirect to.
     * @throws BadGatewayException if Bridge is unreachable, answers unintelligibly, or reads the
     *   link back with a destination that contradicts [payeeIban] — no authorisation URL is handed
     *   out then. Nothing has been debited in any of these cases: no transfer can happen until the
     *   association authenticates.
     */
    fun createPaymentLink(
        payoutId: UUID,
        payerName: String,
        payerReference: String,
        payeeName: String,
        payeeIban: String,
        amount: BigDecimal,
        label: String,
        senderIban: String?,
        callbackUrl: String,
    ): BridgePaymentLink {
        if (props.demoMode) {
            val demo = BridgePaymentLink(
                id = "$DEMO_ID_PREFIX$payoutId",
                url = "$callbackUrl?demo=1",
            )
            log.info("Bridge demo mode — simulated payment link {} for {} to {}", demo.id, amount, payeeIban)
            return demo
        }

        val normalisedIban = normalise(payeeIban)
        val body = CreatePaymentLinkRequestJson(
            clientReference = payoutId.toString(),
            callbackUrl = callbackUrl,
            senderIban = senderIban,
            // An explicit expiry is what is meant to keep the payout from staying engaged forever.
            // If the association closes the tab without authorising, nothing else would ever change
            // the state: there is no polling loop, and an unbounded link may never produce an event.
            // A bounded one expires, and the reserved amount is released by the LINK_EXPIRED arm of
            // BridgeWebhookService — provided Bridge emits payment.link.updated on that transition.
            //
            // UNVERIFIED (2026-09-22). Bridge documents that `expired`/`revoked` are carried by
            // payment.link.updated, but never that ageing past `expired_date` emits an event at all,
            // and the revoke endpoint mentions no webhook. Two links created on 2026-09-22 at 10:37Z
            // and 11:49Z expire a day later: if they are not FAILED/LINK_EXPIRED by then, this whole
            // release path does not exist and a sweeper is required. Bridge's own default is 15
            // minutes, deliberately overridden here — an association needs longer than that to
            // authorise at its bank.
            expiredDate = Instant.now().plus(LINK_VALIDITY).toString(),
            // Bridge rejects the whole body with a bare `invalid_request` when `user` is absent —
            // it never names the field. The association is the payer here, so it is a company.
            user = UserJson(
                companyName = payerName.take(BRIDGE_PAYER_NAME_MAX_LENGTH),
                externalReference = payerReference,
            ),
            transactions = listOf(
                TransactionJson(
                    amount = amount,
                    label = label.take(BRIDGE_LABEL_MAX_LENGTH),
                    clientReference = payoutId.toString(),
                    beneficiary = BeneficiaryJson(
                        iban = normalisedIban,
                        companyName = payeeName.take(BRIDGE_NAME_MAX_LENGTH),
                    ),
                )
            ),
        )

        val raw = try {
            restClient.post()
                .uri("/v3/payment/payment-links")
                .headers { it.addAll(bridgeHeaders()) }
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String::class.java) ?: "{}"
        } catch (ex: RestClientException) {
            log.error("Bridge createPaymentLink failed for payout {}: {}", payoutId, ex.message)
            throw BadGatewayException("Bridge payment initiation unavailable: ${ex.message}")
        }

        val parsed = try {
            objectMapper.readValue(raw, CreatePaymentLinkResponseJson::class.java)
        } catch (ex: Exception) {
            log.warn("Failed to parse Bridge payment-link response: {}", ex.message)
            null
        }

        val id = parsed?.id
        val url = parsed?.url
        if (id == null || url == null) {
            throw BadGatewayException("Bridge returned an unusable payment link for payout $payoutId")
        }

        // Read the link back and check the destination Bridge recorded is the one we sent.
        //
        // Bridge documents that when `beneficiary.iban` is not defined it silently substitutes the
        // IBAN configured in the dashboard. A request carrying our IBAN could then be answered 200
        // while the money is destined elsewhere — the association would authorise a debit towards
        // an account it never chose. Asserting what we *sent* proves nothing here; only what Bridge
        // stored does.
        //
        // That read-back is masked: Bridge's read endpoints disclose a few characters of the IBAN
        // and replace the rest with a mask character (`FR76XXXXXXXXXXXXXXXXXXXX250` in the
        // documented examples), so the comparison is on what Bridge discloses — see refusalReason.
        val recorded = try {
            fetchLink(id).transactions?.lastOrNull()?.beneficiary?.iban?.let { normalise(it) }
        } catch (ex: BadGatewayException) {
            log.error("Could not read back Bridge payment link {} for payout {}: {}", id, payoutId, ex.message)
            throw BadGatewayException(
                "Bridge payment link $id could not be verified before use: ${ex.message}"
            )
        }
        val refusal = recorded?.let { refusalReason(it, normalisedIban) }
        if (refusal != null) {
            log.error(
                "Bridge read back destination {} for payout {} where {} was sent — {}; refusing to hand " +
                    "out the authorisation URL",
                recorded, payoutId, normalisedIban, refusal,
            )
            throw BadGatewayException(
                "Bridge recorded a different destination IBAN for payout $payoutId — transfer refused"
            )
        }

        log.info("Bridge payment link {} created for payout {}", id, payoutId)
        return BridgePaymentLink(id, url)
    }

    /**
     * Reads the authoritative state of a payment link.
     *
     * This is the verification step of webhook handling, not a polling loop: it runs when Bridge
     * notifies us, precisely because the notification itself cannot be authenticated.
     *
     * Two calls, and the second is the one that carries the answer. A payment link only ever
     * describes what was *requested*: Bridge documents that the transaction objects it holds "do
     * not include `status` or `id` fields". Execution state lives on the **payment request** Bridge
     * creates once the association authorises at its bank, read through
     * `GET /v3/payment/payment-requests?payment_link_id={id}`, whose `status` is the very
     * `CREA`/`ACTC`/`PDNG`/`ACSC`/`RJCT` vocabulary [BridgePaymentStatus] mirrors. Deriving the
     * state from the link alone reported every transfer as [BridgePaymentStatus.CREA] for ever, so
     * no payout could settle and none ever did.
     *
     * The payment request's status wins whenever one exists; otherwise a dead link
     * (`expired`/`revoked`) is reported as such, and a still-usable link with no payment request
     * stays [BridgePaymentStatus.CREA] — nothing has been authorised yet.
     *
     * @param paymentLinkId Identifier returned by [createPaymentLink].
     * @return the current state.
     * @throws BadGatewayException if Bridge is unreachable or the response is unparsable — the
     *   caller must then leave the payout untouched rather than infer an outcome.
     */
    fun getPaymentLink(paymentLinkId: String): BridgePaymentLinkState {
        if (props.demoMode) {
            log.debug("Bridge demo mode — simulated link {} reported as settled", paymentLinkId)
            return BridgePaymentLinkState(BridgePaymentStatus.ACSC, "$DEMO_ID_PREFIX$paymentLinkId", null)
        }

        val link = fetchLink(paymentLinkId)
        val request = fetchPaymentRequest(paymentLinkId)
        val requestStatus = BridgePaymentStatus.fromTransactionWire(request?.status)

        val status = when {
            requestStatus != null -> requestStatus
            link.status.equals("expired", ignoreCase = true) -> BridgePaymentStatus.LINK_EXPIRED
            link.status.equals("revoked", ignoreCase = true) -> BridgePaymentStatus.LINK_REVOKED
            else -> {
                if (request?.status != null) {
                    log.warn(
                        "Unrecognised Bridge payment-request status '{}' on link {} — treated as not yet authorised",
                        request.status, paymentLinkId,
                    )
                }
                BridgePaymentStatus.CREA
            }
        }

        return BridgePaymentLinkState(
            status = status,
            transactionId = request?.transactions?.firstOrNull()?.id,
            statusReason = request?.statusReason,
        )
    }

    /** Reads the link itself — what was requested, and whether the link is still usable. */
    private fun fetchLink(paymentLinkId: String): PaymentLinkResponseJson {
        val raw = try {
            restClient.get()
                .uri("/v3/payment/payment-links/{id}", paymentLinkId)
                .headers { it.addAll(bridgeHeaders()) }
                .retrieve()
                .body(String::class.java) ?: "{}"
        } catch (ex: RestClientException) {
            log.error("Bridge getPaymentLink failed for {}: {}", paymentLinkId, ex.message)
            throw BadGatewayException("Bridge payment initiation unavailable: ${ex.message}")
        }

        return try {
            objectMapper.readValue(raw, PaymentLinkResponseJson::class.java)
        } catch (ex: Exception) {
            log.warn("Failed to parse Bridge payment-link state for {}: {}", paymentLinkId, ex.message)
            throw BadGatewayException("Bridge returned an unparsable state for payment link $paymentLinkId")
        }
    }

    /**
     * Reads the payment request Bridge created for this link, or null while there is none.
     *
     * A link the association has not authorised yet simply has no payment request; that is not an
     * error, it is [BridgePaymentStatus.CREA]. Bridge marks a link `completed` once one has been
     * initiated from it, so a second one is not expected — Bridge documents no ordering for the
     * list, so when several do come back the last is taken and the ambiguity is logged rather than
     * settled silently.
     */
    private fun fetchPaymentRequest(paymentLinkId: String): PaymentRequestJson? {
        val raw = try {
            restClient.get()
                .uri { it.path("/v3/payment/payment-requests").queryParam("payment_link_id", paymentLinkId).build() }
                .headers { it.addAll(bridgeHeaders()) }
                .retrieve()
                .body(String::class.java) ?: "{}"
        } catch (ex: RestClientException) {
            log.error("Bridge payment-request lookup failed for link {}: {}", paymentLinkId, ex.message)
            throw BadGatewayException("Bridge payment initiation unavailable: ${ex.message}")
        }

        val resources = try {
            objectMapper.readValue(raw, PaymentRequestListJson::class.java).resources
        } catch (ex: Exception) {
            log.warn("Failed to parse Bridge payment requests for link {}: {}", paymentLinkId, ex.message)
            throw BadGatewayException("Bridge returned an unparsable payment request for link $paymentLinkId")
        }

        if (resources != null && resources.size > 1) {
            log.warn(
                "Bridge returned {} payment requests for link {} — order is undocumented, taking the last",
                resources.size, paymentLinkId,
            )
        }
        return resources?.lastOrNull()
    }

    private fun normalise(iban: String) = iban.uppercase().replace(Regex("[^A-Z0-9]"), "")

    /**
     * Why [recorded] — the destination as Bridge reads it back, partially masked — cannot be the
     * [sent] IBAN, or null when it still can be.
     *
     * Every character Bridge discloses must match; a masked position carries no information and is
     * skipped. The check fails closed rather than silently weakening: a length that is not the one
     * sent means the read-back is not the documented mask at all, and a read-back that hides the
     * country code, the check digits or the trailing character discloses too little to verify
     * anything — an all-masked value would otherwise let any destination through.
     *
     * The guarantee remains partial by construction: a substitution is caught as soon as Bridge
     * discloses one differing character — in practice the check digits and the trailing characters,
     * which an unrelated account of the same length does not share — but an IBAN differing only
     * where Bridge masks cannot be ruled out. That limit is stated in
     * `docs/legal/verification-payee-iban.md`, points 4.5 and 6.
     */
    private fun refusalReason(recorded: String, sent: String): String? = when {
        recorded.length != sent.length ->
            "the read-back is ${recorded.length} characters where ${sent.length} were sent, so it is " +
                "not the documented mask"

        recorded.take(IBAN_DISCLOSED_PREFIX_LENGTH).any { it == MASK_CHAR } || recorded.last() == MASK_CHAR ->
            "Bridge disclosed neither the country code and check digits nor the trailing character, " +
                "so the destination cannot be verified"

        recorded.indices.any { recorded[it] != MASK_CHAR && recorded[it] != sent[it] } ->
            "a disclosed character contradicts the IBAN that was sent"

        else -> null
    }

    private fun bridgeHeaders() = HttpHeaders().apply {
        set("Client-Id", props.clientId)
        set("Client-Secret", props.clientSecret)
        set("Bridge-Version", props.apiVersion)
    }

    private companion object {
        /** Bridge caps a transaction label at 50 characters; CommonLink labels allow 500. */
        const val BRIDGE_LABEL_MAX_LENGTH = 50

        /** Bridge caps a beneficiary name at 35 characters. */
        const val BRIDGE_NAME_MAX_LENGTH = 35

        /**
         * Length at which the payer's name is truncated.
         *
         * Bridge documents no limit on `user.company_name`, so this is a deliberate guess: 70 is
         * the SEPA debtor-name ceiling every bank enforces anyway. It is kept separate from
         * [BRIDGE_NAME_MAX_LENGTH] rather than reusing the beneficiary's 35 — that one comes from
         * the documentation and must not be silently attributed to a field it never covered.
         */
        const val BRIDGE_PAYER_NAME_MAX_LENGTH = 70

        /** Prefix marking simulated Bridge data, so it can never pass for a real transfer. */
        const val DEMO_ID_PREFIX = "demo_"

        /**
         * Character Bridge substitutes for the digits it hides when it reads an IBAN back, e.g.
         * `FR76XXXXXXXXXXXXXXXXXXXX250`. It survives [normalise] — it is a letter — so the mask
         * must be handled explicitly rather than compared away.
         */
        const val MASK_CHAR = 'X'

        /**
         * Number of leading characters Bridge is expected to disclose: the country code and the
         * check digits, as every documented example does. Requiring them is not an arbitrary floor
         * — it is requiring Bridge to disclose what it documents, and refusing to pass a
         * verification off as done when it could not be performed.
         */
        const val IBAN_DISCLOSED_PREFIX_LENGTH = 4

        /**
         * How long the association has to authorise a transfer before the link dies.
         *
         * Bridge accepts up to 60 days; a day is ample for a deliberate action and keeps a
         * forgotten payout from holding a campaign's balance for weeks.
         */
        val LINK_VALIDITY: Duration = Duration.ofDays(1)
    }
}

// ── Bridge wire format ───────────────────────────────────────────────────────

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class BeneficiaryJson(
    val iban: String,
    @JsonProperty("company_name") val companyName: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class TransactionJson(
    val amount: BigDecimal,
    val currency: String = "EUR",
    val label: String,
    @JsonProperty("client_reference") val clientReference: String,
    val beneficiary: BeneficiaryJson,
)

/**
 * Bridge's `user` object — the payer. Mandatory on every payment link: omitting it is answered
 * `400 invalid_request` / "Invalid body content", with no indication of which field is missing.
 *
 * Bridge accepts either a `first_name`/`last_name` pair or a `company_name`; an association is a
 * legal entity, so only the latter is sent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
private data class UserJson(
    @JsonProperty("company_name") val companyName: String,
    @JsonProperty("external_reference") val externalReference: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class CreatePaymentLinkRequestJson(
    @JsonProperty("client_reference") val clientReference: String,
    @JsonProperty("callback_url") val callbackUrl: String,
    @JsonProperty("sender_iban") val senderIban: String?,
    @JsonProperty("expired_date") val expiredDate: String,
    val user: UserJson,
    val transactions: List<TransactionJson>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CreatePaymentLinkResponseJson(
    val id: String?,
    val url: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PaymentLinkBeneficiaryJson(
    val iban: String?,
)

/**
 * A transaction as the *payment link* carries it: the transfer that was asked for, never its
 * outcome.
 *
 * Bridge documents that these objects "do not include `status` or `id` fields". Declaring them
 * anyway meant they deserialised to null on every single call, which pinned every payout to
 * [BridgePaymentStatus.CREA] and made settlement unreachable. Only the beneficiary is meaningful
 * here, and only for the destination read-back guard; execution state comes from
 * [PaymentRequestJson].
 */
@JsonIgnoreProperties(ignoreUnknown = true)
private data class PaymentLinkTransactionJson(
    val beneficiary: PaymentLinkBeneficiaryJson?,
)

/** A transaction of a payment request — unlike the link's, this one does carry an id. */
@JsonIgnoreProperties(ignoreUnknown = true)
private data class PaymentRequestTransactionJson(
    val id: String?,
)

/**
 * The payment request Bridge creates when the association authorises the transfer at its bank.
 *
 * Its [status] is the authoritative execution state, in the same vocabulary as
 * [BridgePaymentStatus]: `CREA`, `ACTC`, `PDNG`, `ACSC`, `RJCT`, `PART`.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
private data class PaymentRequestJson(
    val id: String?,
    val status: String?,
    @JsonProperty("status_reason") val statusReason: String?,
    val transactions: List<PaymentRequestTransactionJson>?,
)

/** Bridge's list envelope for `GET /v3/payment/payment-requests`. */
@JsonIgnoreProperties(ignoreUnknown = true)
private data class PaymentRequestListJson(
    val resources: List<PaymentRequestJson>?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PaymentLinkResponseJson(
    val id: String?,
    val status: String?,
    val transactions: List<PaymentLinkTransactionJson>?,
)
