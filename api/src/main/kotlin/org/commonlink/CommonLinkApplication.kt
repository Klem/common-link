package org.commonlink

import org.commonlink.config.DonationCapProperties
import org.commonlink.config.MollieConnectConfig
import org.commonlink.config.MollieProperties
import org.commonlink.config.OnchainConfig
import org.commonlink.config.RiskClassificationProperties
import org.commonlink.config.SanctionsProperties
import org.commonlink.config.SanctionsSyncProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(
    OnchainConfig::class,
    MollieProperties::class,
    MollieConnectConfig::class,
    RiskClassificationProperties::class,
    SanctionsProperties::class,
    SanctionsSyncProperties::class,
    DonationCapProperties::class,
)
class CommonLinkApplication

fun main(args: Array<String>) {
    runApplication<CommonLinkApplication>(*args)
}
