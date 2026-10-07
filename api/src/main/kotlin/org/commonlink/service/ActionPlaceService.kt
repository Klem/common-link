package org.commonlink.service

import org.commonlink.entity.ActionPlaceType
import org.commonlink.entity.Campaign
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.UnprocessableEntityException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.util.Locale

/**
 * A validated place of action, ready to be stored on a [Campaign].
 *
 * @property type Kind of place.
 * @property code Normalised code (INSEE commune, department, ISO country), null for FRANCE.
 * @property label Display label, resolved server-side.
 * @property latitude Commune centre latitude, null for any other kind.
 * @property longitude Commune centre longitude, null for any other kind.
 */
data class ResolvedActionPlace(
    val type: ActionPlaceType,
    val code: String?,
    val label: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * Validates and resolves a campaign's "lieu de l'action".
 *
 * This is the backend mirror of the editor's place picker: every code the frontend can send is
 * re-checked here (format first, then existence against geo.api.gouv.fr for French places, against
 * the JDK ISO 3166 list for countries), and the label/coordinates are always recomputed.
 */
@Service
class ActionPlaceService(private val geoApiClient: GeoApiClient) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Validates a place of action submitted by an association.
     *
     * @param type Kind of place.
     * @param rawCode Code entered for that kind; ignored for [ActionPlaceType.FRANCE].
     * @return the resolved place.
     * @throws UnprocessableEntityException if the code is malformed or unknown, or if a country code
     *   designates France or one of its overseas departments (those are entered as departments).
     * @throws BadGatewayException if geo.api.gouv.fr cannot be reached to check a French code.
     */
    fun resolve(type: ActionPlaceType, rawCode: String?): ResolvedActionPlace {
        val code = rawCode?.trim()?.uppercase()
        return when (type) {
            ActionPlaceType.FRANCE -> ResolvedActionPlace(type, null, FRANCE_LABEL)

            ActionPlaceType.COMMUNE -> {
                if (code == null || !COMMUNE_CODE.matches(code)) throw invalid(type)
                val commune = geoApiClient.findCommune(code) ?: throw invalid(type)
                ResolvedActionPlace(
                    type, commune.code, communeLabel(commune), commune.latitude, commune.longitude,
                )
            }

            ActionPlaceType.DEPARTEMENT -> {
                if (code == null || !DEPARTMENT_CODE.matches(code)) throw invalid(type)
                val department = geoApiClient.findDepartment(code) ?: throw invalid(type)
                ResolvedActionPlace(type, department.code, "${department.name} (${department.code})")
            }

            ActionPlaceType.PAYS -> {
                if (code == null || code !in FOREIGN_COUNTRIES) throw invalid(type)
                ResolvedActionPlace(type, code, Locale.of("", code).getDisplayCountry(Locale.FRENCH))
            }
        }
    }

    /**
     * Best-effort default place of action from an association's headquarters address.
     *
     * Picks the commune of [postalCode] whose name matches [city] (accent- and case-insensitive),
     * or the only commune of that postal code. Never throws: any miss or upstream failure is
     * logged and yields null, so callers (campaign creation, backfill) never fail on geocoding.
     *
     * @return the commune place, or null when it cannot be determined.
     */
    fun fromAssociationAddress(city: String?, postalCode: String?): ResolvedActionPlace? {
        val cp = postalCode?.trim()
        if (cp == null || !POSTAL_CODE.matches(cp)) return null
        return try {
            val communes = geoApiClient.findCommunesByPostalCode(cp)
            val commune = communes.firstOrNull { city != null && normalise(it.name) == normalise(city) }
                ?: communes.singleOrNull()
            if (commune == null) {
                logger.info("No unambiguous commune for postalCode={} city={}", cp, city)
                null
            } else {
                ResolvedActionPlace(
                    ActionPlaceType.COMMUNE, commune.code, communeLabel(commune),
                    commune.latitude, commune.longitude,
                )
            }
        } catch (e: BadGatewayException) {
            logger.warn("Association address geocoding skipped for postalCode={}: {}", cp, e.message)
            null
        }
    }

    /** Writes [place] onto [campaign]'s place-of-action columns. */
    fun applyTo(campaign: Campaign, place: ResolvedActionPlace) {
        campaign.actionPlaceType = place.type
        campaign.actionPlaceCode = place.code
        campaign.actionPlaceLabel = place.label
        campaign.actionLatitude = place.latitude
        campaign.actionLongitude = place.longitude
    }

    private fun communeLabel(commune: GeoCommune) =
        commune.departmentCode?.let { "${commune.name} ($it)" } ?: commune.name

    private fun invalid(type: ActionPlaceType) =
        UnprocessableEntityException("Invalid place of action for type $type")

    private fun normalise(value: String): String =
        Normalizer.normalize(value.trim().lowercase(Locale.FRENCH), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9]"), "")

    private companion object {
        const val FRANCE_LABEL = "France entière"
        val COMMUNE_CODE = Regex("^(\\d{5}|2[AB]\\d{3})$")
        val DEPARTMENT_CODE = Regex("^(\\d{2}|2[AB]|97[1-6])$")
        val POSTAL_CODE = Regex("^\\d{5}$")
        /** France and its overseas departments (971-976) are not foreign countries. */
        val FRENCH_ISO_CODES = setOf("FR", "GP", "MQ", "GF", "RE", "YT")
        val FOREIGN_COUNTRIES: Set<String> = Locale.getISOCountries().toSet() - FRENCH_ISO_CODES
    }
}
