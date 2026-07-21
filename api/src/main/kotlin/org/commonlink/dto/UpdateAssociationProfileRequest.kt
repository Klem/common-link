package org.commonlink.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * Request body for updating an association's editable profile fields.
 *
 * All fields are optional (null = keep existing value). Only provided non-null values are applied.
 * Constraints are enforced when a field is present (non-null); null skips validation.
 *
 * Fields that are not editable after creation (name, identifier/SIREN) are intentionally absent.
 *
 * @param contactName Name of the primary contact person (min 2 characters when provided).
 * @param city City of the association's headquarters.
 * @param postalCode French postal code (exactly 5 digits when provided).
 * @param description Public description of the association's mission.
 * @param siren French SIREN number (9 digits).
 * @param creationYear Year the association was founded (1800–2100).
 * @param contactEmail Association contact email address.
 * @param phone Association phone number (6–20 chars, digits/spaces/+/parens/dots/hyphens).
 */
data class UpdateAssociationProfileRequest(

    @field:Size(min = 2, message = "Contact name must be at least 2 characters")
    val contactName: String?,

    @field:Size(max = 255)
    val city: String?,

    @field:Pattern(regexp = "^\\d{5}$", message = "Postal code must be exactly 5 digits")
    val postalCode: String?,

    @field:Size(max = 1000)
    val description: String?,

    @field:Pattern(regexp = "^[0-9]{9}$", message = "SIREN must be exactly 9 digits")
    val siren: String?,

    @field:Min(1800)
    @field:Max(2100)
    val creationYear: Short?,

    @field:Email(message = "Contact email must be a valid email address")
    val contactEmail: String?,

    @field:Pattern(regexp = "^[0-9 +(). -]{6,20}$", message = "Phone number format is invalid")
    val phone: String?,

    /**
     * UUID of the campaign to use as the widget donation destination.
     * Null = no change. The campaign must belong to the same association;
     * non-LIVE campaigns are accepted here — the widget (B4) refuses donations
     * at request time if the destination is not LIVE.
     */
    val widgetDestinationCampaignId: UUID?,

    /** Full street address of the registered office. Printed on Cerfa receipts. */
    @field:Size(max = 255)
    val addressLine1: String? = null,

    /** Official purpose / objet social. Printed on Cerfa receipts. */
    @field:Size(max = 2000)
    val legalObject: String? = null,

    /** Full name of the authorised receipt signer. Printed on Cerfa receipts. */
    @field:Size(max = 255)
    val signerName: String? = null,

    /** Role/title of the authorised signer (e.g. "Trésorier"). Printed on Cerfa receipts. */
    @field:Size(max = 100)
    val signerRole: String? = null,
)
