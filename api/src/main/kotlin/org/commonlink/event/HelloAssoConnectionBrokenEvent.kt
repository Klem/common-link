package org.commonlink.event

import java.util.UUID

/**
 * Published once when HelloAsso rejects an association's refresh token and its link goes ACTIVE → BROKEN
 * ([org.commonlink.service.HelloAssoConnectionBreaker]). Consumed after commit to warn the association that it must
 * paste a HelloAsso API key again.
 */
data class HelloAssoConnectionBrokenEvent(val associationId: UUID)
