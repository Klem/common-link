package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the LCB-FT asset-freeze screening service.
 *
 * **Score threshold** — The default value of 0.85 is deliberately conservative:
 * a false positive (flagging a non-listed person) costs a human review, while a false
 * negative (missing a listed person) is a legal violation under art. L.562-2 CMF.
 * The threshold is therefore set low enough to catch transcription errors
 * (accents, inverted name order, missing particles, multiple spaces), and human review
 * absorbs the resulting false positives. Raising it above 0.9 risks missing legitimate
 * matches; lowering it below 0.75 generates excessive noise. Any change must be tested
 * against both the variant suite and the non-match suite (see SanctionScreeningServiceTest).
 *
 * **Use-test-data** — When true, ingestion reads from a bundled XML fixture instead of
 * downloading the live register. Must be false in production — enforced by ProdConfigSecurityTest.
 */
@ConfigurationProperties(prefix = "commonlink.sanctions.screening")
data class SanctionsProperties(
    val scoreThreshold: Double = 0.85,
    val useTestData: Boolean = false,
    val registryUrl: String = "https://gels-avoirs.dgtresor.gouv.fr/ApiPublic/api/v1/publication/derniere-publication-fichier-xml",
)
