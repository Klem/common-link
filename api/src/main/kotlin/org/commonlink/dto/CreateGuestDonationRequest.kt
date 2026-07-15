package org.commonlink.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * Body for POST /api/public/widget/{widgetToken}/donations.
 *
 * Validation mirrors the frontend (app/src/app/.../embed/donate/.../page.tsx) per CLAUDE.md rule 8.
 * Identity fields (donorFullName..donorCountry) are snapshotted on the Donation for the Cerfa 2041-RD receipt.
 */
data class CreateGuestDonationRequest(

    @field:DecimalMin(value = "1.00", message = "must be at least 1.00 EUR")
    @field:DecimalMax(value = "10000.00", message = "must be at most 10000.00 EUR")
    @field:Digits(integer = 6, fraction = 2, message = "must have at most 2 decimal places")
    val amount: BigDecimal,

    @field:Email(message = "must be a valid email address")
    @field:NotBlank(message = "must not be blank")
    val donorEmail: String,

    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must not exceed 255 characters")
    val donorFullName: String,

    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must not exceed 255 characters")
    val donorAddressLine1: String,

    @field:Size(max = 255, message = "must not exceed 255 characters")
    val donorAddressLine2: String? = null,

    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 16, message = "must not exceed 16 characters")
    val donorPostalCode: String,

    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 128, message = "must not exceed 128 characters")
    val donorCity: String,

    @field:Pattern(regexp = "[A-Za-z]{2}", message = "must be a 2-letter ISO-3166 country code")
    val donorCountry: String = "FR",

    /** When true, the donor appears as anonymous on public donation listings. Does not affect the fiscal receipt. */
    val anonymousDisplay: Boolean = false,

    @field:AssertTrue(message = "RGPD consent is required")
    val consent: Boolean,

    /** Origin site auto-declared by the widget snippet. Sanitised server-side; treat as untrusted. */
    val sourceSite: String? = null,
)
