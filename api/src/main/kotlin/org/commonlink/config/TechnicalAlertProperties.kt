package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration of the technical alerting channel — the e-mails that tell the *developers* that
 * the platform itself is failing, as opposed to a user doing something invalid.
 *
 * **Why a separate mailbox from `app.compliance.alert-notification-email`** — the two have
 * different readers and different urgency. A freeze alert waits for a compliance ruling; an
 * unhandled 500 on the donation path waits for a deploy. Mixing them means the on-call reader
 * learns to ignore the mailbox.
 *
 * **Blank [notificationEmail] disables the channel** and logs a warning at the moment an alert
 * would have been sent, exactly like `ComplianceAlertEmailListener`. There is deliberately no
 * fail-fast startup guard: alerting is an operational convenience, not a regulatory control, and
 * refusing to boot because nobody configured a dev mailbox would be a worse outage than the one
 * being reported.
 *
 * **Why cooldown and burst thresholds exist** — the alert sources are exception handlers. A
 * failing upstream or a crash loop produces one exception per request, so an un-throttled channel
 * turns a single incident into thousands of e-mails and gets the sender blacklisted. [cooldown]
 * caps how often the same failure signature is mailed; [burstThreshold] / [burstWindow] make the
 * security signals (403/429) report a *rate*, which is what actually matters there — a single 403
 * is normal traffic, two hundred in five minutes is someone probing.
 *
 * @property notificationEmail Mailbox notified of technical failures. Blank disables alerting.
 * @property alertsEnabled Master switch; `false` keeps the logging but sends nothing.
 * @property cooldown Minimum delay between two e-mails for the same failure signature.
 * @property burstThreshold Occurrences within [burstWindow] before a burst alert is raised.
 * @property burstWindow Tumbling window over which burst occurrences are counted; an expired
 *   window is discarded, so a rate paced just under the threshold is not reported.
 */
@ConfigurationProperties(prefix = "app.technical")
data class TechnicalAlertProperties(
    val notificationEmail: String = "",
    val alertsEnabled: Boolean = true,
    val cooldown: Duration = Duration.ofMinutes(30),
    val burstThreshold: Int = 20,
    val burstWindow: Duration = Duration.ofMinutes(5),
)
