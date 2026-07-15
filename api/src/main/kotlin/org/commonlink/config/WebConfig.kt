package org.commonlink.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
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

    /**
     * Prototype [RestClient.Builder] for injection into services that configure their own HTTP client.
     * Each injecting service calls [RestClient.Builder.build] to get a dedicated, independently configured instance.
     */
    @Bean
    fun restClientBuilder(): RestClient.Builder = RestClient.builder()
}
