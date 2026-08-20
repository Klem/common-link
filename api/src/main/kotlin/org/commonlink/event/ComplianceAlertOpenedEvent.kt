package org.commonlink.event

import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertSubjectType
import java.util.UUID

/**
 * Published by [org.commonlink.service.ComplianceAlertService.createOrIgnore] when a **new**
 * compliance alert row is inserted — never when an existing open alert is returned, so a
 * subscriber cannot be woken twice for the same (origin, subject) pair while it stays open.
 *
 * ### Deliberately identity-free
 * The event carries the alert id, its origin, its subject *type* and its severity — never the
 * screened name, nor the matched register entry. Subscribers that need the circumstantial detail
 * read it from the evidence table behind the compliance authorisation, exactly as the alert screen
 * does. This mirrors the log-hygiene rule applied throughout the freeze-screening services: LCB-FT
 * identity data never travels through a channel that is not access-controlled.
 *
 * [subjectId] is included because a subscriber needs it to build a deep link, and it is an opaque
 * UUID — it discloses nothing on its own.
 */
data class ComplianceAlertOpenedEvent(
    val alertId: UUID,
    val origin: ComplianceAlertOrigin,
    val subjectType: ComplianceAlertSubjectType,
    val subjectId: UUID?,
    val severity: ComplianceAlertSeverity,
)
