package org.commonlink.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.commonlink.entity.ActionPlaceType
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignScope

/**
 * Place of action of a campaign ("lieu de l'action"), as exposed to clients.
 *
 * @param type Kind of place.
 * @param code INSEE commune code, department code or ISO 3166-1 alpha-2 country; null for FRANCE.
 * @param label Display label resolved server-side (e.g. `Vallauris (06)`, `France entière`).
 * @param scope Reach derived from [type] -- never entered by the association.
 * @param latitude Commune centre latitude; null unless [type] is COMMUNE.
 * @param longitude Commune centre longitude; null unless [type] is COMMUNE.
 */
data class ActionPlaceDto(
    val type: ActionPlaceType,
    val code: String?,
    val label: String,
    val scope: CampaignScope,
    val latitude: Double?,
    val longitude: Double?,
) {
    companion object {
        /**
         * Builds the DTO from the raw place-of-action columns.
         *
         * @return null when no place of action is set ([type] or [label] null).
         */
        fun of(
            type: ActionPlaceType?, code: String?, label: String?, latitude: Double?, longitude: Double?,
        ): ActionPlaceDto? =
            if (type == null || label == null) null
            else ActionPlaceDto(type, code, label, type.scope, latitude, longitude)
    }
}

/** Place of action of this campaign, or null when not set yet. */
fun Campaign.actionPlaceDto(): ActionPlaceDto? =
    ActionPlaceDto.of(actionPlaceType, actionPlaceCode, actionPlaceLabel, actionLatitude, actionLongitude)

/**
 * Place of action submitted from the campaign editor. Only the kind and the code are accepted;
 * label and coordinates are always resolved server-side.
 *
 * @param type Kind of place.
 * @param code Code for that kind (INSEE commune, department, ISO country); ignored for FRANCE.
 */
data class ActionPlaceRequest(
    @field:NotNull
    val type: ActionPlaceType?,

    @field:Size(max = 10)
    val code: String? = null,
)
