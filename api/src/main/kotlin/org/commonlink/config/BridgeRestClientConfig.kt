package org.commonlink.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Builds the [RestClient] used to talk to Bridge.
 *
 * Exists so the timeouts are applied in exactly one visible place rather than through a nullable
 * constructor parameter whose Kotlin default Spring may or may not honour — a factory silently
 * resolved to null would drop the timeouts without any error, and a payout call is the last place
 * where an indefinite hang is acceptable: a user is waiting on the confirmation.
 *
 * Tests construct `BridgePayoutService` with their own `RestClient` (bound to a
 * `MockRestServiceServer`) and never see this bean.
 */
@Configuration
class BridgeRestClientConfig {

    /**
     * @param props Bridge configuration supplying the base URL and both timeouts.
     * @param builder Spring's auto-configured builder, so shared customisations still apply.
     * @return a client pointed at Bridge, with connect and read timeouts set.
     */
    @Bean("bridgeRestClient")
    fun bridgeRestClient(props: BridgeProperties, builder: RestClient.Builder): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(props.connectTimeout)
            setReadTimeout(props.readTimeout)
        }
        return builder.clone()
            .baseUrl(props.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
