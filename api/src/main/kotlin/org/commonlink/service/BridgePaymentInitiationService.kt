package org.commonlink.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.config.BridgeProperties
import org.commonlink.config.BridgeRestClientConfig
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.BridgeInitiationNotStartedException
import org.commonlink.exception.BridgeRequestRefusedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
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
 * - `GET  /v3/payment/payment-links/{id}` — whether the link itself is still usable
 * - `GET  /v3/payment/payment-requests/{id}` — the execution state of the request a notification named
 * - `GET  /v3/payment/payment-requests?payment_link_id={id}` — same, when it named none
 * - `POST /v3/payment/payment-links/{id}/revoke` — close a link a rejection left usable
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
                // `&`, not `?`: the callback already carries `?tab=payments&payout=…`, and a second
                // `?` is not a separator — the payout id then parses as `<uuid>?demo=1` and the tab
                // never recognises the payout it was told to watch.
                url = "$callbackUrl&demo=1",
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
            // A bounded one expires, and the LINK_EXPIRED arm of BridgeWebhookService returns the
            // payout to a retryable PENDING with its amount back on the campaign — provided Bridge
            // emits payment.link.updated on that transition.
            //
            // UNVERIFIED (2026-09-22). Bridge documents that `expired`/`revoked` are carried by
            // payment.link.updated, but never that ageing past `expired_date` emits an event at all,
            // and the revoke endpoint mentions no webhook. A link created on 2026-09-22 at 10:37Z
            // expires a day later: if its payout is not back to PENDING with a null Bridge status
            // by then, this whole release path does not exist and a sweeper is required. Bridge's
            // own default is 15 minutes, deliberately overridden here — an association needs longer
            // than that to authorise at its bank.
            expiredDate = Instant.now().plus(props.linkValidity).toString(),
            // Bridge rejects the whole body with a bare `invalid_request` when `user` is absent —
            // it never names the field. The association is the payer here, so it is a company.
            user = UserJson(
                companyName = payerName.take(BRIDGE_PAYER_NAME_MAX_LENGTH),
                externalReference = payerReference,
            ),
            transactions = listOf(
                TransactionJson(
                    amount = amount,
                    label = statementLabel(label),
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
        } catch (ex: HttpClientErrorException) {
            // Bridge answered and refused: it is not unavailable, it disagrees with the request.
            // Same outcome for the payout — nothing was created, so the row is dropped — but no
            // technical alert, see [BridgeRequestRefusedException].
            log.error("Bridge refused the payment link for payout {}: {}", payoutId, ex.message)
            throw BridgeRequestRefusedException("Bridge refused the payment initiation: ${ex.message}")
        } catch (ex: RestClientException) {
            // Bridge did not answer at all — down, unreachable, timed out. No link exists either,
            // so the row is dropped just the same, and this one *is* worth alerting on.
            log.error("Bridge createPaymentLink failed for payout {}: {}", payoutId, ex.message)
            throw BridgeInitiationNotStartedException("Bridge payment initiation unavailable: ${ex.message}")
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
        val recordedTransactions = try {
            fetchLink(id).transactions.orEmpty()
        } catch (ex: BadGatewayException) {
            log.error("Could not read back Bridge payment link {} for payout {}: {}", id, payoutId, ex.message)
            throw BadGatewayException(
                "Bridge payment link $id could not be verified before use: ${ex.message}"
            )
        }
        val recorded = recordedTransactions.singleOrNull()?.beneficiary?.iban?.let { normalise(it) }

        // No verifiable destination means no authorisation URL. An absent beneficiary is not a
        // check that could not be performed — it is the exact shape of the substitution this guard
        // exists to catch, since Bridge falls back to the IBAN configured in its dashboard when
        // none is given. It carries the same information as a fully-masked read-back, which is
        // already refused, so it is refused on the same footing. More than one transaction is
        // refused too: exactly one is ever sent, and picking among several by position is how the
        // payment-request path came to report a settled transfer as rejected.
        val refusal = when {
            recordedTransactions.size > 1 ->
                "Bridge recorded ${recordedTransactions.size} transactions where one was sent"
            recorded == null -> "Bridge disclosed no destination to compare"
            else -> refusalReason(recorded, normalisedIban)
        }
        if (refusal != null) {
            log.error(
                "Bridge read back destination {} for payout {} where {} was sent — {}; refusing to hand " +
                    "out the authorisation URL",
                recorded, payoutId, normalisedIban, refusal,
            )
            // The link exists and is authorisable; its destination is what we cannot vouch for.
            // Leaving it alive would keep an URL to an account the association never chose.
            revokeQuietly(id, "unverifiable destination")
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
     * The payment request's status wins over the link's, but only once the transfer is past
     * authorisation — see [BridgePaymentStatus.survivesLinkDeath]. A `CREA` or `ACTC` request on a
     * dead link is itself dead, because the URL that would let the association authorise no longer
     * works, so the link's `expired`/`revoked` is what gets reported. A still-usable link with no
     * payment request stays [BridgePaymentStatus.CREA] — nothing has been authorised yet.
     *
     * **Which** payment request is read matters, because a link can hold several: Bridge does not
     * burn a link on a rejection, so re-opening it and authorising again adds a second request
     * beside the first. A named [paymentRequestId] is read directly, but decides alone only once
     * the transfer has left the association's hands (`PDNG` and above); below that the link's
     * requests are ranked by [supersedes], never by list order, the named one included. Taking
     * `resources.last()` reported a settled 120 € transfer as rejected on 2026-09-22: Bridge had
     * returned both requests, newest first, and four consecutive notifications each re-stamped the
     * older rejection over it.
     *
     * @param paymentLinkId Identifier returned by [createPaymentLink].
     * @param paymentRequestId Payment request the notification is about, when it named one.
     * @return the current state.
     * @throws BadGatewayException if Bridge is unreachable or the response is unparsable — the
     *   caller must then leave the payout untouched rather than infer an outcome.
     */
    fun getPaymentLink(paymentLinkId: String, paymentRequestId: String? = null): BridgePaymentLinkState {
        if (props.demoMode) {
            log.debug("Bridge demo mode — simulated link {} reported as settled", paymentLinkId)
            return BridgePaymentLinkState(BridgePaymentStatus.ACSC, "$DEMO_ID_PREFIX$paymentLinkId", null)
        }

        val link = fetchLink(paymentLinkId)

        // Reading the named request answers "what happened to *this* one", which is the right
        // question only while no sibling can outrank it. A request that has not moved money can:
        // on 2026-09-22 a redelivered notification for a `RJCT` re-stamped a settled 120 €
        // transfer as rejected, four times in a row. So the named request decides alone only from
        // `PDNG` upwards — where the bank is executing or has executed, and nothing on the link
        // outranks it — and otherwise the link's own requests are ranked, the named one included.
        val named = paymentRequestId?.let { fetchPaymentRequestById(it, paymentLinkId) }
        val request = named?.takeIf { rank(it.status) >= RANK_LEFT_THE_ASSOCIATION }
            ?: fetchPaymentRequest(paymentLinkId)
            ?: named
        val requestStatus = BridgePaymentStatus.fromTransactionWire(request?.status)

        val status = when {
            // A request the association has yet to authorise loses to a dead link: the URL that
            // would let it authorise no longer works, so that request can never go anywhere. Only
            // a transfer already past authorisation outranks the link's fate.
            requestStatus?.survivesLinkDeath == true -> requestStatus
            link.status.equals("expired", ignoreCase = true) -> BridgePaymentStatus.LINK_EXPIRED
            link.status.equals("revoked", ignoreCase = true) -> BridgePaymentStatus.LINK_REVOKED
            requestStatus != null -> requestStatus
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

    /**
     * Revokes a payment link so nothing more can be authorised from it.
     *
     * Bridge does **not** burn a link when a transfer is rejected: on 2026-09-22 a link whose
     * payment request came back `RJCT` still showed as `Valide / Non payé`, and re-opening it
     * created a second payment request that settled. Meanwhile the payout had been failed and its
     * amount returned to the campaign's confirmable balance. So a rejected payout that is not
     * revoked leaves a live authorisation URL against funds already given back — and a transfer
     * authorised through it would publish a permanent on-chain attestation beyond the balance.
     *
     * Bridge documents only `200` and `404` here. A `404` is treated as done: whatever the cause,
     * Bridge cannot authorise a link it does not know. Every other `4xx` is treated the same way
     * and logged — the undocumented ones all describe a link already `completed`, `expired` or
     * `revoked`, none of which is a fresh authorisable link, and answering the webhook non-2xx
     * instead would have Bridge redeliver the same impossible revocation for two days. Only a
     * `5xx` or a network failure is retryable, because then nothing is known.
     *
     * @param paymentLinkId Link to revoke.
     * @throws BadGatewayException if Bridge could not be reached or failed — the caller must then
     *   leave the amount engaged rather than release it against a link that may still be live.
     */
    fun revokePaymentLink(paymentLinkId: String) {
        if (props.demoMode) {
            log.debug("Bridge demo mode — simulated revocation of link {}", paymentLinkId)
            return
        }

        try {
            restClient.post()
                .uri("/v3/payment/payment-links/{id}/revoke", paymentLinkId)
                .headers { it.addAll(bridgeHeaders()) }
                .retrieve()
                .toBodilessEntity()
            log.info("Bridge payment link {} revoked", paymentLinkId)
        } catch (ex: HttpClientErrorException) {
            log.warn("Bridge refused to revoke link {} ({}) — it cannot be a usable link", paymentLinkId, ex.statusCode)
        } catch (ex: RestClientException) {
            log.error("Bridge revocation failed for link {}: {}", paymentLinkId, ex.message)
            throw BadGatewayException("Bridge payment initiation unavailable: ${ex.message}")
        }
    }

    /**
     * Revokes [paymentLinkId] without ever throwing.
     *
     * For the paths that are already abandoning a link: the caller is on its way to fail or
     * release the payout, and a revocation that cannot be confirmed must not replace that outcome
     * with a different error. Worst case the link stays authorisable until it expires, which is
     * what happened before this existed.
     */
    fun revokeQuietly(paymentLinkId: String, why: String) {
        runCatching { revokePaymentLink(paymentLinkId) }
            .onFailure { log.error("Could not revoke Bridge link {} after {}: {}", paymentLinkId, why, it.message) }
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
     * Reads the one payment request a notification named, through
     * `GET /v3/payment/payment-requests/{id}`, or null to fall back to [fetchPaymentRequest].
     *
     * Preferred over the list whenever the notification carries the id: it answers "what happened
     * to *this* request" instead of "what is on this link", which is the only question with a
     * single answer once a link holds several requests.
     *
     * The id comes from the notification body, so it is checked against the link the payout was
     * resolved from before its status is used. The list endpoint could not return a foreign
     * request — it is filtered by link — whereas this one returns whatever id it is given, and
     * settling on it would settle the wrong payout. A mismatch, a request Bridge does not know, or
     * a resource carrying no link id is therefore not fatal: it degrades to the scoped list rather
     * than deciding on an unverified resource.
     */
    private fun fetchPaymentRequestById(paymentRequestId: String, paymentLinkId: String): PaymentRequestJson? {
        val raw = try {
            restClient.get()
                .uri("/v3/payment/payment-requests/{id}", paymentRequestId)
                .headers { it.addAll(bridgeHeaders()) }
                .retrieve()
                .body(String::class.java) ?: "{}"
        } catch (ex: HttpClientErrorException.NotFound) {
            // Retrying for two days would not make Bridge know this id; read the link instead.
            log.warn("Bridge does not know payment request {} named by a notification on link {}", paymentRequestId, paymentLinkId)
            return null
        } catch (ex: RestClientException) {
            log.error("Bridge payment-request read failed for {}: {}", paymentRequestId, ex.message)
            throw BadGatewayException("Bridge payment initiation unavailable: ${ex.message}")
        }

        val request = try {
            objectMapper.readValue(raw, PaymentRequestJson::class.java)
        } catch (ex: Exception) {
            log.warn("Failed to parse Bridge payment request {}: {}", paymentRequestId, ex.message)
            throw BadGatewayException("Bridge returned an unparsable payment request $paymentRequestId")
        }

        if (request.paymentLinkId != paymentLinkId) {
            log.warn(
                "Bridge payment request {} belongs to link {}, not {} — falling back to the link's own requests",
                paymentRequestId, request.paymentLinkId, paymentLinkId,
            )
            return null
        }
        return request
    }

    /**
     * Reads the payment requests of a link and keeps the one that decides its fate, or null while
     * there is none.
     *
     * Used when a notification names no payment request, and also when the one it names has not
     * moved money — a sibling may have. A link the association has not authorised yet simply has
     * no request; that is not an error, it is [BridgePaymentStatus.CREA]. Several can coexist — a
     * rejection leaves the link usable, so a second authorisation adds a second request — and
     * Bridge documents no order for the list, so the winner is chosen by [supersedes] and never by
     * position.
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
                "Bridge returned {} payment requests for link {} — ranking them, no sibling having " +
                    "left the association's hands",
                resources.size, paymentLinkId,
            )
        }
        return resources?.reduceOrNull { kept, candidate -> if (supersedes(candidate, kept)) candidate else kept }
    }

    /**
     * Whether [candidate] describes the fate of the link better than [kept] does.
     *
     * Two tiers, and the boundary is whether money has left the association's hands.
     *
     * **From `PDNG` upwards — the transfer is executing or executed — rank decides, always.**
     *  1. `ACSC` — the bank executed a transfer. No sibling undoes that fact, and reporting it as
     *     anything else loses money from the ledger.
     *  2. `PART` — part of it executed. Money moved and a human has to reconcile it.
     *  3. `PDNG` — the bank is executing. The link's fate no longer matters.
     *
     * Nothing below may ever outrank these. Grouping them with `CREA`/`ACTC`, as this did until
     * 2026-09-23, left `[CREA, PDNG]` decided by Bridge's undocumented list order: pick `CREA`,
     * let the link expire, and the payout is released — its amount handed back to the campaign
     * while the bank is executing the transfer.
     *
     * **Below it — `ACTC`, `CREA`, `RJCT` — the most recently created request wins.** None of
     * them moved a cent, so the question is not which state is "best" but which attempt is the
     * one the association is actually on. Rank alone got that backwards on 2026-09-24: a payer
     * entered the tunnel (`ACTC` at 10:18:52), went back, authorised again and was refused
     * (`RJCT` at 10:19:11) — and the abandoned `ACTC` outranked the real refusal. The old
     * reasoning ("a rejection loses to any live attempt, the association retried") assumed the
     * rejection was the older one; a back-navigation inverts it. `created_at` settles it either
     * way, and it is the creation date rather than `updated_at` because what is being compared is
     * which attempt started last, not which row Bridge touched last.
     *
     * Falls back to rank whenever the dates cannot decide — absent, equal, unparsable, or either
     * side carrying a status this code does not recognise. An unknown state must never win on a
     * timestamp.
     */
    private fun supersedes(candidate: PaymentRequestJson, kept: PaymentRequestJson): Boolean {
        val candidateRank = rank(candidate.status)
        val keptRank = rank(kept.status)

        val decidedByRank = candidateRank >= RANK_LEFT_THE_ASSOCIATION ||
            keptRank >= RANK_LEFT_THE_ASSOCIATION ||
            candidateRank < 0 || keptRank < 0
        if (decidedByRank) return candidateRank > keptRank

        val candidateStart = startedAt(candidate)
        val keptStart = startedAt(kept)
        if (candidateStart != null && keptStart != null && candidateStart != keptStart) {
            return candidateStart > keptStart
        }
        return candidateRank > keptRank
    }

    /** When the association started this attempt, or null when Bridge disclosed nothing usable. */
    private fun startedAt(request: PaymentRequestJson): Instant? =
        (request.createdAt ?: request.updatedAt)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun rank(wireStatus: String?): Int =
        when (BridgePaymentStatus.fromTransactionWire(wireStatus)) {
            BridgePaymentStatus.ACSC -> 5
            BridgePaymentStatus.PART -> 4
            BridgePaymentStatus.PDNG -> RANK_LEFT_THE_ASSOCIATION
            BridgePaymentStatus.ACTC -> 2
            BridgePaymentStatus.CREA -> 1
            BridgePaymentStatus.RJCT -> 0
            // Unrecognised, and the link-only states fromTransactionWire never returns.
            else -> -1
        }

    /**
     * Renders the association's justification as something a bank statement will accept.
     *
     * Bridge rejects some characters in a transaction label with
     * `payment.transaction.characters_not_allowed`, and documents neither the rule nor even that
     * error. What is known is measured, not guessed: on 2026-09-23 a label containing `:` was
     * refused twice, while labels carrying `é`, `è`, `'` and `.` went through in the same session.
     * So the set kept here is what was observed to pass, plus the hyphen — rather than an invented
     * whitelist. Anything else becomes a space.
     *
     * Rendered rather than validated, deliberately. The label is the association's own accounting
     * justification and is stored verbatim on the payout; only what travels to the bank is
     * constrained. Rejecting the payout instead would turn a typed colon into a `502` and a
     * technical alert, which is what happened before this existed — and a validation rule built on
     * one observed character would sooner or later refuse a legitimate label.
     */
    private fun statementLabel(label: String): String =
        label.replace(Regex("[^\\p{L}\\p{N} .'-]"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()
            .take(BRIDGE_LABEL_MAX_LENGTH)
            .trim()
            .ifBlank { "Virement" }

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
         * Rank of `PDNG`, the point at which the transfer has left the association's hands.
         *
         * The boundary of the ranking's upper tier: at or above it the bank is executing or has
         * executed, so no sibling request and no dead link can change the outcome — see
         * [supersedes].
         */
        const val RANK_LEFT_THE_ASSOCIATION = 3
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
 *
 * [paymentLinkId] is what scopes a request read by id back to the link it belongs to. The list
 * endpoint cannot return a foreign request, being filtered by link; reading one by an id taken
 * from a notification body can, so that read is only trusted once this matches.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
private data class PaymentRequestJson(
    val id: String?,
    val status: String?,
    @JsonProperty("status_reason") val statusReason: String?,
    @JsonProperty("payment_link_id") val paymentLinkId: String?,
    /** ISO 8601 creation date — which of a link's attempts started last. See `supersedes`. */
    @JsonProperty("created_at") val createdAt: String?,
    /** ISO 8601 last-update date, used only when Bridge discloses no creation date. */
    @JsonProperty("updated_at") val updatedAt: String?,
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
