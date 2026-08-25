package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Public confirmation status of a donation, visible to the donor on the return page.")
enum class DonationPublicStatus {
    /** Payment initiated but not yet confirmed by the Mollie webhook. */
    PENDING,

    /** Payment confirmed — on-chain recording job enqueued. */
    CONFIRMED,
}

data class DonationStatusDto(
    @Schema(description = "Current status of the donation payment.")
    val status: DonationPublicStatus,
    @Schema(description = "Payment method used by the donor (e.g. creditcard, banktransfer). Only ever set when status is CONFIRMED — the donor already knows their own method, so this leaks nothing new.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val method: String? = null,
)
