package org.commonlink.dto

/**
 * Simplified result returned by the INSEE Sirene search proxy.
 *
 * This DTO intentionally exposes only the ~13 fields needed by the frontend, instead of
 * the 30+ fields present in the raw INSEE Sirene 3.11 API response.
 *
 * @property siren SIREN number (9 digits) identifying the legal unit.
 * @property siret SIRET number (14 digits) identifying the establishment; null for SIREN-only queries.
 * @property name Legal name of the entity (denomination for companies, or first + last name for individuals).
 * @property nafCode NAF/APE activity code (e.g. "9499Z"), from the current legal period.
 * @property category Enterprise size category (e.g. "PME", "ETI", "GE"); may be null.
 * @property city City of the establishment; null for SIREN-only queries.
 * @property postalCode Postal code of the establishment; null for SIREN-only queries.
 * @property address Concatenated street address of the establishment; null for SIREN-only queries.
 * @property active Whether the entity is administratively active ("A") in the latest period.
 * @property isSiege Whether this establishment is the head office (siège social); false for SIREN-only queries.
 * @property isEss Whether the entity belongs to the Social and Solidarity Economy (ESS).
 * @property creationDate Creation date of the entity or establishment (ISO-8601 string, e.g. "2010-03-15"); may be null.
 * @property employeeRange INSEE employee range code (e.g. "11" = 10–19 employees); may be null.
 */
data class SireneSearchResultDto(
    val siren: String,
    val siret: String?,
    val name: String,
    val nafCode: String?,
    val category: String?,
    val city: String?,
    val postalCode: String?,
    val address: String?,
    val active: Boolean,
    val isSiege: Boolean,
    val isEss: Boolean,
    val creationDate: String?,
    val employeeRange: String?
)
