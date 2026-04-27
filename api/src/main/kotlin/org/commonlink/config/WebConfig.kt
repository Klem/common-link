package org.commonlink.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

/**
 * General-purpose web infrastructure beans.
 */
@Configuration
class WebConfig {

    /**
     * Shared [RestTemplate] instance for outbound HTTP calls (e.g. Monerium token exchange).
     */
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}
