package org.commonlink.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

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
 */
data class UpdateAssociationProfileRequest(

    @field:Size(min = 2, message = "Contact name must be at least 2 characters")
    val contactName: String?,

    @field:Size(max = 255)
    val city: String?,

    @field:Pattern(regexp = "^\\d{5}$", message = "Postal code must be exactly 5 digits")
    val postalCode: String?,

    @field:Size(max = 1000)
    val description: String?
)
