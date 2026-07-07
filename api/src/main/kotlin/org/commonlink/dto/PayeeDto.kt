package org.commonlink.dto

import org.commonlink.entity.Payee
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object representing a payee with its associated IBANs.
 *
 * Returned by all CRUD operations on payees. Embeds the full list of
 * [PayeeIbanDto] so that the frontend has a complete picture in one request.
 *
 * @param id UUID of the [org.commonlink.entity.Payee] record.
 * @param payeeType COMPANY (SIREN-identified organisation) or PERSON (natural person).
 * @param name Official registered name or "Prénom Nom" for persons.
 * @param identifier1 French SIREN identifier (9 digits); null for PERSON payees.
 * @param identifier2 French SIRET identifier (14 digits), null if only SIREN is known.
 * @param activityCode NAF/APE activity code, null if not set.
 * @param category Legal or administrative category, null if not set.
 * @param city City of the payee's registered address, null if not set.
 * @param postalCode Postal code of the payee's registered address, null if not set.
 * @param active Whether this payee is currently active.
 * @param hasPayouts True if at least one payout has been sent to this payee.
 * @param ibans All IBAN entries registered for this payee.
 * @param createdAt Timestamp when the payee was created.
 */
data class PayeeDto(
    val id: UUID,
    val payeeType: String,
    val name: String,
    val identifier1: String?,
    val identifier2: String?,
    val activityCode: String?,
    val category: String?,
    val city: String?,
    val postalCode: String?,
    val active: Boolean,
    val hasPayouts: Boolean,
    val ibans: List<PayeeIbanDto>,
    val createdAt: Instant
)

/**
 * Converts a [Payee] entity to a [PayeeDto], mapping its IBANs eagerly.
 *
 * @param hasPayouts Whether this payee has at least one associated payout. Defaults to false for
 *   newly created payees (CREATE / IBAN operations) where no payout can exist yet.
 */
fun Payee.toDto(hasPayouts: Boolean = false) = PayeeDto(
    id = id!!,
    payeeType = payeeType,
    name = name,
    identifier1 = identifier1,
    identifier2 = identifier2,
    activityCode = activityCode,
    category = category,
    city = city,
    postalCode = postalCode,
    active = active,
    hasPayouts = hasPayouts,
    ibans = ibans.map { it.toDto() },
    createdAt = createdAt
)
