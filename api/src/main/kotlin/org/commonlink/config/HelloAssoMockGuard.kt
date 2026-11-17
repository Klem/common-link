package org.commonlink.config

import jakarta.annotation.PostConstruct
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Refuses to start under the `prod` profile with `app.helloasso.mock=true`: the fake HelloAsso
 * ([org.commonlink.service.FakeHelloAssoGateway]) is already excluded from `prod`, so in prod the flag would silently
 * talk to the real HelloAsso while someone believes it is mocked. Fails loudly instead.
 */
@Component
class HelloAssoMockGuard(
    private val properties: HelloAssoProperties,
    private val environment: Environment,
) {
    @PostConstruct
    fun check() {
        check(!(properties.mock && "prod" in environment.activeProfiles)) {
            "app.helloasso.mock=true is forbidden under the prod profile"
        }
    }
}
