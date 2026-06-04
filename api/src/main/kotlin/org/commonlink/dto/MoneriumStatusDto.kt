package org.commonlink.dto

/**
 * Response DTO for GET /api/monerium/status.
 *
 * @property connected true if the association has a completed [org.commonlink.entity.MoneriumConnection].
 * @property pending true if an OAuth2 authorization flow was started but not yet completed
 *   (non-expired record in [org.commonlink.entity.MoneriumOAuthState]).
 * @property profileId Monerium profile id linked to this association (null if not yet captured
 *   or if the Monerium account had no profile at connect time).
 * @property profileName Display name of the linked Monerium profile. The frontend renders this
 *   as "Connected as: [name]" so the user can spot a wrong-account autofill and reconnect.
 */
data class MoneriumStatusDto(
    val connected: Boolean,
    val pending: Boolean,
    val profileId: String? = null,
    val profileName: String? = null,
)
