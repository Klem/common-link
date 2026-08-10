package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Operational parameters for the scheduled asset-freeze register synchronisation.
 *
 * **fixed-delay-ms** — interval between the end of one synchronisation and the start of the
 * next (Spring `fixedDelay` semantics). Default: 86 400 000 ms (24 h). A stale register is a
 * latent compliance gap — measures lifted or added since the last sync are invisible to
 * screening controls. Operators should keep this value at 24 h or below in all environments.
 * Raising it above 7 days without documented justification constitutes a procedural breach.
 *
 * **initial-delay-ms** — delay before the very first execution after application startup.
 * Default: 60 000 ms (1 min), to let the application finish initialising before hitting the
 * DG Trésor API.
 *
 * **enabled** — set to false to disable the scheduler entirely (e.g. in test environments
 * that rely on fixture data). Default: true. Use-test-data in
 * [SanctionsProperties] already prevents live-registry calls in dev/CI without disabling
 * the scheduler, so this flag is rarely needed.
 */
@ConfigurationProperties(prefix = "commonlink.sanctions.sync")
data class SanctionsSyncProperties(
    val fixedDelayMs: Long = 86_400_000L,
    val initialDelayMs: Long = 60_000L,
    val enabled: Boolean = true,
)
