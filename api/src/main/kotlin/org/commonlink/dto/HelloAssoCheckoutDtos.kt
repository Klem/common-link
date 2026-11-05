package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Body of `POST /v5/organizations/{slug}/checkout-intents` (HelloAsso wire format, `additionalProperties: false`:
 * only documented fields, nulls omitted). Amounts in cents; [totalAmount] = [initialAmount] (no installments).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HelloAssoInitCheckoutJson(
    @field:JsonProperty("totalAmount") val totalAmount: Int,
    @field:JsonProperty("initialAmount") val initialAmount: Int,
    @field:JsonProperty("itemName") val itemName: String,
    @field:JsonProperty("backUrl") val backUrl: String,
    @field:JsonProperty("errorUrl") val errorUrl: String,
    @field:JsonProperty("returnUrl") val returnUrl: String,
    @field:JsonProperty("containsDonation") val containsDonation: Boolean,
    @field:JsonProperty("payer") val payer: HelloAssoPayerJson? = null,
    @field:JsonProperty("metadata") val metadata: Map<String, String>? = null,
)

/**
 * Pre-filled payer of a checkout. Only fields that pass HelloAsso's rules are set (an invalid one makes HelloAsso
 * refuse the whole checkout); never a birth date.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HelloAssoPayerJson(
    @field:JsonProperty("firstName") val firstName: String? = null,
    @field:JsonProperty("lastName") val lastName: String? = null,
    @field:JsonProperty("email") val email: String? = null,
    @field:JsonProperty("address") val address: String? = null,
    @field:JsonProperty("city") val city: String? = null,
    @field:JsonProperty("zipCode") val zipCode: String? = null,
    /** ISO 3166-1 alpha-3 (HelloAsso requires exactly 3 letters). */
    @field:JsonProperty("country") val country: String? = null,
)

/** Response of the checkout-intent creation: its id and the payment page (valid 15 minutes). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HelloAssoCheckoutIntentResponseJson(
    @field:JsonProperty("id") val id: Long? = null,
    @field:JsonProperty("redirectUrl") val redirectUrl: String? = null,
)

/** `GET /v5/organizations/{slug}/checkout-intents/{id}`: [order] is present only once a payment is authorized. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HelloAssoCheckoutIntentJson(
    @field:JsonProperty("id") val id: Long? = null,
    @field:JsonProperty("metadata") val metadata: Map<String, Any?>? = null,
    @field:JsonProperty("order") val order: HelloAssoOrderJson? = null,
)

/** Order of a paid checkout-intent. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HelloAssoOrderJson(
    @field:JsonProperty("id") val id: Long? = null,
    @field:JsonProperty("checkoutIntentId") val checkoutIntentId: Long? = null,
    @field:JsonProperty("payments") val payments: List<HelloAssoPaymentJson>? = null,
)

/**
 * One payment of an order: [state] (e.g. `Authorized`) and [paymentMeans] (e.g. `Card`, `Sepa`). Amount in cents;
 * [date] is the payment time, ISO-8601 with offset (`2026-11-05T15:27:41.8549543+01:00`, sandbox 2026-11-05).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HelloAssoPaymentJson(
    @field:JsonProperty("id") val id: Long? = null,
    @field:JsonProperty("amount") val amount: Int? = null,
    @field:JsonProperty("state") val state: String? = null,
    @field:JsonProperty("paymentMeans") val paymentMeans: String? = null,
    @field:JsonProperty("date") val date: String? = null,
)
