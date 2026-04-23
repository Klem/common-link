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
 * @param name Official registered name of the payee organisation.
 * @param identifier1 French SIREN identifier (9 digits).
 * @param identifier2 French SIRET identifier (14 digits), null if only SIREN is known.
 * @param activityCode NAF/APE activity code, null if not set.
 * @param category Legal or administrative category, null if not set.
 * @param city City of the payee's registered address, null if not set.
 * @param postalCode Postal code of the payee's registered address, null if not set.
 * @param active Whether this payee is currently active.
 * @param ibans All IBAN entries registered for this payee.
 * @param createdAt Timestamp when the payee was created.
 */
data class PayeeDto(
    val id: UUID,
    val name: String,
    val identifier1: String,
    val identifier2: String?,
    val activityCode: String?,
    val category: String?,
    val city: String?,
    val postalCode: String?,
    val active: Boolean,
    val ibans: List<PayeeIbanDto>,
    val createdAt: Instant
)

/**
 * Converts a [Payee] entity to a [PayeeDto], mapping its IBANs eagerly.
 */
fun Payee.toDto() = PayeeDto(
    id = id!!,
    name = name,
    identifier1 = identifier1,
    identifier2 = identifier2,
    activityCode = activityCode,
    category = category,
    city = city,
    postalCode = postalCode,
    active = active,
    ibans = ibans.map { it.toDto() },
    createdAt = createdAt
)
