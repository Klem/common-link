package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

@Schema(description = "Public confirmation status of a donation, visible to the donor on the return page.")
enum class DonationPublicStatus {
    /** Payment initiated but not yet confirmed by the Mollie webhook. */
    PENDING,

    /** Payment confirmed — on-chain recording job enqueued. */
    CONFIRMED,
}

/**
 * Public status of a donation, looked up by its opaque public ref.
 *
 * Everything beyond [status] is set only once CONFIRMED: it feeds the GA4 `purchase` event the
 * association's own page pushes after the donor returns (relayed by the CommonLink iframe), so the
 * values come from the server and cannot be tampered with in a URL. Never any donor identity —
 * whoever holds the ref learns the amount, the campaign and the anonymity choice, nothing more.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DonationStatusDto(
    @Schema(description = "Current status of the donation payment.")
    val status: DonationPublicStatus,
    @Schema(description = "Payment method used by the donor (e.g. creditcard, banktransfer). Only set when CONFIRMED.")
    val method: String? = null,
    @Schema(description = "Donated amount. Only set when CONFIRMED.")
    val amount: BigDecimal? = null,
    @Schema(description = "ISO 4217 currency of the amount. Only set when CONFIRMED.")
    val currency: String? = null,
    @Schema(description = "Campaign the donation went to. Only set when CONFIRMED.")
    val campaignId: UUID? = null,
    @Schema(description = "Name of that campaign. Only set when CONFIRMED.")
    val campaignName: String? = null,
    @Schema(description = "Name of the association running the campaign. Only set when CONFIRMED.")
    val associationName: String? = null,
    @Schema(description = "Whether the donor chose an anonymous display. Read from the donor profile, which each new donation overwrites, so it reflects the donor's latest choice. Only set when CONFIRMED.")
    val anonymous: Boolean? = null,
)
