package org.commonlink.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.commonlink.config.MollieProperties
import org.commonlink.exception.MolliePaymentException
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Known Mollie payment statuses.
 *
 * Only [PAID] confirms a successful donation. [AUTHORIZED] (cards/Klarna/Billie/Riverty capture)
 * is **not** confirmed — treat as pending until a `paid` webhook arrives.
 */
enum class MolliePaymentStatus {
    OPEN, PENDING, AUTHORIZED, PAID, CANCELED, EXPIRED, FAILED;

    val isConfirmed: Boolean get() = this == PAID
    val isFailed: Boolean get() = this == CANCELED || this == EXPIRED || this == FAILED
    val isPending: Boolean get() = this == OPEN || this == PENDING || this == AUTHORIZED
}

/**
 * Resolved Mollie payment with parsed status and amount.
 *
 * @param id Mollie payment identifier (e.g. `tr_xxx`).
 * @param status Parsed payment status; unknown statuses fall back to [MolliePaymentStatus.PENDING].
 * @param amount Transaction amount parsed from Mollie's string representation.
 * @param checkoutUrl Hosted checkout URL from `_links.checkout.href`. Absent after the first redirect.
 * @param metadata Key-value pairs attached at payment creation.
 * @param method Payment method actually used by the payer (Mollie code, e.g. `creditcard`,
 *   `banktransfer`, `bancontact`). Null until the payer picks one on the hosted checkout page —
 *   so it is only reliably present once the payment reaches [MolliePaymentStatus.PAID].
 */
data class MolliePayment(
    val id: String,
    val status: MolliePaymentStatus,
    val amount: BigDecimal,
    val checkoutUrl: String?,
    val metadata: Map<String, String>,
    val method: String? = null,
)

// ── Internal JSON DTOs (Mollie wire format) ──────────────────────────────────

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MollieAmountJson(val currency: String, val value: String)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MollieLinkJson(val href: String)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MollieLinksJson(val checkout: MollieLinkJson?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MolliePaymentResponseJson(
    val id: String,
    val status: String,
    val amount: MollieAmountJson,
    val metadata: Map<String, String>?,
    /** Method chosen by the payer; absent while the payment is still `open`. */
    val method: String? = null,
    @JsonProperty("_links") val links: MollieLinksJson?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MollieProfileJson(val id: String)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MollieProfilesEmbeddedJson(val profiles: List<MollieProfileJson>)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MollieProfilesResponseJson(
    @JsonProperty("_embedded") val embedded: MollieProfilesEmbeddedJson
)

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class MollieCreatePaymentRequestJson(
    val description: String,
    val amount: MollieAmountJson,
    val redirectUrl: String,
    val cancelUrl: String?,
    val webhookUrl: String?,
    val metadata: Map<String, String>,
    val profileId: String? = null,
    val testmode: Boolean? = null,
)

// ── Client ───────────────────────────────────────────────────────────────────

/**
 * Minimal Mollie REST client for creating and reading payments.
 *
 * **Amount serialisation (Mollie contract):** `value` must be a string with exactly 2 decimal
 * places, e.g. `"10.00"`. Passing a JSON number causes a 422.
 *
 * **Idempotency-Key:** when provided, the header is sent to Mollie on [createPayment]. Mollie
 * honours the key for 1 hour and rejects reuse of a key whose request body differs from the
 * first call — so the key must be unique to the exact parameters sent, not merely to the
 * donor+amount+widget "intent" (a caller wanting protection against double-submits must reuse
 * both the same key AND send a byte-identical body across the retry).
 */
@Service
class MollieClient(
    private val properties: MollieProperties,
    restClientBuilder: RestClient.Builder
) {
    private val log = LoggerFactory.getLogger(MollieClient::class.java)

    private val restClient: RestClient = restClientBuilder
        .baseUrl("${properties.apiBaseUrl}/v2")
        .build()

    /**
     * Creates a Mollie hosted-checkout payment and returns the checkout URL.
     *
     * @param amount Transaction amount — rounded to 2 decimal places before sending.
     * @param currency ISO-4217 code, defaults to EUR.
     * @param description Shown to the payer; truncated to 255 characters.
     * @param redirectUrl Mollie redirects the customer here after payment (success or unknown outcome).
     * @param cancelUrl Optional separate redirect for explicit cancellation; falls back to [redirectUrl].
     * @param webhookUrl Publicly reachable endpoint Mollie POSTs status changes to.
     * @param metadata Arbitrary key-value pairs stored on the Mollie payment (e.g. campaignId, donorProfileId).
     * @param idempotencyKey If non-null, sent as the `Idempotency-Key` request header.
     */
    fun getFirstProfileId(bearerToken: String): String {
        log.debug("Fetching Mollie profiles for Connect account")
        val testmodeParam = if (properties.testMode) "&testmode=true" else ""
        try {
            val response = restClient.get()
                .uri("/profiles?limit=1$testmodeParam")
                .header("Authorization", "Bearer $bearerToken")
                .retrieve()
                .body(MollieProfilesResponseJson::class.java)
                ?: throw MolliePaymentException("Mollie returned empty response for GET /profiles")
            return response.embedded.profiles.firstOrNull()?.id
                ?: throw MolliePaymentException("No Mollie profile found for this account")
        } catch (ex: MolliePaymentException) {
            throw ex
        } catch (ex: RestClientException) {
            log.error("Mollie getProfiles failed: {}", ex.message)
            throw MolliePaymentException("Mollie getProfiles failed: ${ex.message}", ex)
        }
    }

    fun createPayment(
        amount: BigDecimal,
        currency: String = "EUR",
        description: String,
        redirectUrl: String,
        cancelUrl: String? = null,
        webhookUrl: String? = null,
        metadata: Map<String, String>,
        idempotencyKey: String? = null,
        bearerToken: String? = null,
        profileId: String? = null,
    ): MolliePayment {
        val amountJson = MollieAmountJson(
            currency = currency,
            value = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        )
        val body = MollieCreatePaymentRequestJson(
            description = description.take(255),
            amount = amountJson,
            redirectUrl = redirectUrl,
            cancelUrl = cancelUrl,
            webhookUrl = webhookUrl?.takeIf { it.isNotBlank() },
            metadata = metadata,
            profileId = profileId,
            testmode = if (properties.testMode) true else null,
        )
        log.debug("Creating Mollie payment: {} {} — '{}'", amountJson.value, currency, body.description)
        try {
            val response = restClient.post()
                .uri("/payments")
                .header("Authorization", "Bearer ${bearerToken ?: properties.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .run { if (idempotencyKey != null) header("Idempotency-Key", idempotencyKey) else this }
                .retrieve()
                .body(MolliePaymentResponseJson::class.java)
                ?: throw MolliePaymentException("Mollie returned empty response for createPayment")
            return response.toDomain()
        } catch (ex: MolliePaymentException) {
            throw ex
        } catch (ex: RestClientException) {
            log.error("Mollie createPayment failed: {}", ex.message)
            throw MolliePaymentException("Mollie payment creation failed: ${ex.message}", ex)
        }
    }

    /**
     * Fetches the current state of a Mollie payment by id.
     *
     * Always re-fetch from Mollie on webhook receipt — never trust the webhook body alone.
     *
     * @param paymentId Mollie payment identifier, e.g. `tr_xxx`.
     */
    fun getPayment(paymentId: String, bearerToken: String? = null): MolliePayment {
        log.debug("Fetching Mollie payment: {}", paymentId)
        val testmodeParam = if (properties.testMode && bearerToken != null) "?testmode=true" else ""
        try {
            val response = restClient.get()
                .uri("/payments/{id}$testmodeParam", paymentId)
                .header("Authorization", "Bearer ${bearerToken ?: properties.apiKey}")
                .retrieve()
                .body(MolliePaymentResponseJson::class.java)
                ?: throw MolliePaymentException("Mollie returned empty response for getPayment($paymentId)")
            return response.toDomain()
        } catch (ex: MolliePaymentException) {
            throw ex
        } catch (ex: RestClientException) {
            log.error("Mollie getPayment failed for {}: {}", paymentId, ex.message)
            throw MolliePaymentException("Mollie getPayment failed for $paymentId: ${ex.message}", ex)
        }
    }

    private fun MolliePaymentResponseJson.toDomain(): MolliePayment {
        val parsedStatus = MolliePaymentStatus.entries.find { it.name.equals(status, ignoreCase = true) }
            ?: run {
                log.warn("Unknown Mollie status '{}' — treating as PENDING", status)
                MolliePaymentStatus.PENDING
            }
        return MolliePayment(
            id = id,
            status = parsedStatus,
            amount = BigDecimal(amount.value),
            checkoutUrl = links?.checkout?.href,
            metadata = metadata ?: emptyMap(),
            method = method?.takeIf { it.isNotBlank() },
        )
    }
}
