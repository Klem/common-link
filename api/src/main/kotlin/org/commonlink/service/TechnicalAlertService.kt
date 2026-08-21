package org.commonlink.service

import org.commonlink.config.TechnicalAlertProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Class of technical incident, with the wording used in the alert e-mail.
 *
 * Each entry is also a **throttling bucket**: the cooldown and the burst counters are keyed on it,
 * so adding a value here adds an independent channel rather than diluting an existing one.
 *
 * @property title Human-readable subject line fragment.
 * @property severity Severity label carried in the subject, for mail-client filtering rules.
 */
enum class TechnicalAlertKind(val title: String, val severity: String) {
    /** Catch-all 500: a bug reached the client. Always worth a developer's attention. */
    UNHANDLED_EXCEPTION("Erreur non gérée (HTTP 500)", "ERROR"),

    /** Mollie refused or was unreachable — the donation path is down, money is not moving. */
    PAYMENT_GATEWAY_FAILURE("Passerelle de paiement Mollie en échec (HTTP 502)", "ERROR"),

    /** An upstream dependency (INSEE, VOP…) is unreachable — onboarding and IBAN checks stall. */
    UPSTREAM_UNAVAILABLE("Dépendance amont indisponible (HTTP 502)", "ERROR"),

    /** Sustained 403s: authorisation probing rather than one user hitting one wrong screen. */
    ACCESS_DENIED_BURST("Rafale de refus d'accès (HTTP 403)", "WARN"),

    /** Sustained 429s: credential stuffing or scripted abuse rather than an impatient user. */
    RATE_LIMIT_BURST("Rafale de dépassements de quota (HTTP 429)", "WARN"),
}

/**
 * Sends the technical incidents raised by [org.commonlink.exception.GlobalExceptionHandler] to the
 * developer mailbox configured under `app.technical`.
 *
 * ### Never breaks the request it reports on
 * Every public entry point is `@Async` and swallows its own exceptions. The one thing this service
 * must never do is throw back into an exception handler: that would turn a handled 500 into an
 * unhandled one and lose the original error entirely. `@Async` also keeps SMTP off the request
 * thread — the sources include the unauthenticated donation widget, which must not wait on a mail
 * server to render its failure.
 *
 * ### Throttling, and why the keys are what they are
 * [reportFailure] de-duplicates on `kind + exception class`, **never on the request path**. Paths
 * carry UUIDs and are attacker-drivable on the public endpoints, so a path-keyed map would grow
 * without bound and could be inflated on purpose. Keyed on exception class, the map is bounded by
 * the number of exception types in the application and needs no eviction. [reportBurst] keys on
 * the kind alone — a handful of entries. The path still travels in the e-mail body, where it is
 * useful and harmless.
 *
 * ### What never goes in the message
 * Path only — no query string, no headers, no request body. E-mail is not an access-controlled
 * channel (same rule as [ComplianceAlertEmailListener]), and query strings routinely carry tokens
 * and addresses. The stack trace *is* included: it is server-side code, not user data, and without
 * it the alert is not actionable.
 *
 * ### Client disconnects are not incidents
 * A browser cancelling an in-flight request surfaces as `ClientAbortException` (a Tomcat
 * `IOException` that `ResponseEntityExceptionHandler` does not absorb) and reaches the catch-all.
 * On a donation widget that is constant background noise, so it is logged but never mailed.
 */
@Service
class TechnicalAlertService(
    private val emailService: EmailService,
    private val properties: TechnicalAlertProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Last e-mail instant per `kind|exception-class` signature. Bounded by exception-class count. */
    private val lastSentAt = ConcurrentHashMap<String, Instant>()

    /** Tumbling-window occurrence counters, one per burst-eligible kind. */
    private val bursts = ConcurrentHashMap<TechnicalAlertKind, Burst>()

    /**
     * Reports a failure that deserves an e-mail on its first occurrence.
     *
     * Subsequent occurrences of the same `kind + exception class` are silent until
     * [TechnicalAlertProperties.cooldown] has elapsed; the suppression itself is logged at DEBUG so
     * the gap between "the log shows 4000 errors" and "I received one e-mail" is explainable.
     *
     * @param kind       Incident class, also the throttling bucket.
     * @param httpMethod HTTP method of the failing request, if known.
     * @param path       Request path **without** query string, if known.
     * @param ex         Originating exception; its class is part of the de-duplication key.
     */
    @Async
    fun reportFailure(kind: TechnicalAlertKind, httpMethod: String?, path: String?, ex: Throwable?) {
        try {
            if (isClientDisconnect(ex)) return
            val signature = "${kind.name}|${ex?.javaClass?.name ?: "-"}"
            if (!acquire(signature)) {
                logger.debug("Technical alert {} suppressed by cooldown", signature)
                return
            }
            send(
                kind = kind,
                context = buildMap {
                    put("Requête", "${httpMethod ?: "?"} ${path ?: "?"}")
                    if (ex != null) {
                        put("Exception", ex.javaClass.name)
                        put("Message", ex.message ?: "(aucun)")
                    }
                    put("Anti-répétition", "prochaine alerte identique dans au plus ${properties.cooldown}")
                },
                stackTrace = ex?.let(::renderStackTrace),
            )
        } catch (e: Exception) {
            logger.error("Technical alert {} could not be processed: {}", kind, e.javaClass.simpleName)
        }
    }

    /**
     * Records one occurrence of a burst-eligible signal and e-mails only once the count reaches
     * [TechnicalAlertProperties.burstThreshold] within [TechnicalAlertProperties.burstWindow].
     *
     * Reaching the threshold resets the window, so a sustained attack produces one e-mail per
     * threshold crossing rather than one per request — and the cooldown applies on top of that.
     *
     * The window is **tumbling**, not sliding: an expired window is discarded rather than aged out
     * occurrence by occurrence. A prober pacing itself just under the threshold per window is
     * therefore never reported. That is the accepted trade for a counter that costs two fields and
     * cannot itself be turned into a memory-exhaustion vector; catching the slow case is the log
     * watch's job, not this channel's.
     *
     * @param kind       Incident class, also the counter and throttling bucket.
     * @param httpMethod HTTP method of the last occurrence, if known.
     * @param path       Request path **without** query string of the last occurrence, if known.
     */
    @Async
    fun reportBurst(kind: TechnicalAlertKind, httpMethod: String?, path: String?) {
        try {
            val threshold = properties.burstThreshold
            if (threshold <= 0) return

            var reached = 0
            bursts.compute(kind) { _, current ->
                val now = Instant.now()
                val counter =
                    if (current == null || Duration.between(current.windowStart, now) > properties.burstWindow) {
                        Burst(now, 0)
                    } else {
                        current
                    }
                counter.count++
                if (counter.count >= threshold) {
                    reached = counter.count
                    Burst(now, 0)
                } else {
                    counter
                }
            }
            if (reached == 0) return

            if (!acquire(kind.name)) {
                logger.debug("Burst alert {} suppressed by cooldown ({} occurrences)", kind, reached)
                return
            }
            send(
                kind = kind,
                context = mapOf(
                    "Occurrences" to "$reached en moins de ${properties.burstWindow}",
                    "Dernière requête" to "${httpMethod ?: "?"} ${path ?: "?"}",
                    "Anti-répétition" to "prochaine alerte identique dans au plus ${properties.cooldown}",
                ),
                stackTrace = null,
            )
        } catch (e: Exception) {
            logger.error("Burst alert {} could not be processed: {}", kind, e.javaClass.simpleName)
        }
    }

    /**
     * Delivers one alert, or explains in the log why it was not delivered.
     *
     * A blank recipient is a configuration state, not an error: it logs at WARN and returns, the
     * same contract as [ComplianceAlertEmailListener].
     */
    private fun send(kind: TechnicalAlertKind, context: Map<String, String>, stackTrace: String?) {
        if (!properties.alertsEnabled) {
            logger.debug("Technical alerting disabled — {} not sent", kind)
            return
        }
        val recipient = properties.notificationEmail
        if (recipient.isBlank()) {
            logger.warn(
                "app.technical.notification-email is not configured — technical alert {} raised with no e-mail sent",
                kind,
            )
            return
        }
        try {
            emailService.sendTechnicalAlert(
                recipientEmail = recipient,
                severity = kind.severity,
                title = kind.title,
                context = context,
                stackTrace = stackTrace,
            )
            logger.info("Technical alert sent: {}", kind)
        } catch (e: Exception) {
            // The incident itself is already in the log at ERROR by the caller, so nothing is lost
            // here — only its push. Logged at WARN to avoid a mail outage masquerading as an
            // application outage in whatever watches the ERROR stream.
            logger.warn("Failed to e-mail technical alert {}: {}", kind, e.javaClass.simpleName)
        }
    }

    /**
     * Atomically checks and stamps the cooldown for one signature.
     *
     * @return `true` when the caller may send, `false` while the cooldown is still running.
     */
    private fun acquire(signature: String): Boolean {
        val now = Instant.now()
        var allowed = false
        lastSentAt.compute(signature) { _, previous ->
            if (previous == null || Duration.between(previous, now) >= properties.cooldown) {
                allowed = true
                now
            } else {
                previous
            }
        }
        return allowed
    }

    /**
     * Detects a client that hung up mid-response, which is normal traffic rather than a defect.
     *
     * Walks the cause chain looking for an [IOException] whose type or message says the socket
     * went away. The walk is depth-bounded because a self-referencing cause chain, however
     * malformed, must not hang the alerting path.
     */
    private fun isClientDisconnect(ex: Throwable?): Boolean {
        var current = ex
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is IOException) {
                val type = current.javaClass.simpleName
                val message = current.message?.lowercase().orEmpty()
                if (type == "ClientAbortException" ||
                    "broken pipe" in message ||
                    "connection reset" in message ||
                    "connection was aborted" in message
                ) {
                    return true
                }
            }
            val cause = current.cause
            current = if (cause === current) null else cause
            depth++
        }
        return false
    }

    /** Renders a stack trace, truncated so a deep chain cannot produce an unsendable message. */
    private fun renderStackTrace(ex: Throwable): String {
        val writer = StringWriter()
        ex.printStackTrace(PrintWriter(writer))
        val text = writer.toString()
        return if (text.length <= MAX_STACK_TRACE_CHARS) text else text.take(MAX_STACK_TRACE_CHARS) + "\n… (tronqué)"
    }

    /** Mutable occurrence counter for one [TechnicalAlertKind]; only ever touched inside `compute`. */
    private class Burst(val windowStart: Instant, var count: Int)

    private companion object {
        const val MAX_CAUSE_DEPTH = 20
        const val MAX_STACK_TRACE_CHARS = 16_000
    }
}
