package org.commonlink

import org.commonlink.config.MoneriumConfig
import org.commonlink.config.OnchainConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(MoneriumConfig::class, OnchainConfig::class)
class CommonLinkApplication

fun main(args: Array<String>) {
    runApplication<CommonLinkApplication>(*args)
}
