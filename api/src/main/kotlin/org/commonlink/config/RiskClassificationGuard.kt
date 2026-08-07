package org.commonlink.config

import jakarta.annotation.PostConstruct
import org.commonlink.entity.RiskLevel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Fail-fast guard that validates [RiskClassificationProperties] at startup.
 *
 * The application refuses to start if:
 *  - the `version` field is empty (the classification document was not properly imported);
 *  - any [RiskLevel] value has no corresponding entry in `measures` (a partial mapping
 *    would mean a risk profile has no due-diligence procedure defined, which is a
 *    compliance gap, not a degraded-mode scenario).
 *
 * On successful validation the loaded version is logged at INFO level so it appears
 * in every startup log, and is exposed via [currentVersion] for any code that evaluates
 * risk and must stamp `risk_classification_version` on the resulting record.
 */
@Component
class RiskClassificationGuard(private val props: RiskClassificationProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun validate() {
        require(props.version.isNotBlank()) {
            "LCB-FT startup check: 'version' in compliance/risk-classification.yml is empty. " +
            "The classification document must carry a non-empty version identifier."
        }
        RiskLevel.entries.forEach { level ->
            requireNotNull(props.measures[level.name]) {
                "LCB-FT startup check: no due-diligence measures defined for " +
                "RiskLevel.${level.name} in compliance/risk-classification.yml. " +
                "Every risk level must have a corresponding entry."
            }
        }
        log.info(
            "LCB-FT: risk-classification version '{}' loaded (approved on {})",
            props.version,
            props.approvedOn,
        )
    }

    /** Returns the version identifier of the currently loaded classification document. */
    fun currentVersion(): String = props.version
}
