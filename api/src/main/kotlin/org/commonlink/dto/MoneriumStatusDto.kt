package org.commonlink.dto

/**
 * Response DTO for GET /api/monerium/status.
 *
 * @property connected true if the association has a completed [org.commonlink.entity.MoneriumConnection].
 * @property pending true if an OAuth2 authorization flow was started but not yet completed
 *   (non-expired record in [org.commonlink.entity.MoneriumOAuthState]).
 */
data class MoneriumStatusDto(val connected: Boolean, val pending: Boolean)
