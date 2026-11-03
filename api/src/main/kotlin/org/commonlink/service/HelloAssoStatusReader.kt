package org.commonlink.service

import org.commonlink.config.HelloAssoProperties
import org.commonlink.dto.HelloAssoLinkState
import org.commonlink.dto.HelloAssoStatusDto
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.payment.HelloAssoWebhookState
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.UserRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Builds the `helloasso` part of the neutral payment status (flow §10.5, brief §2.2) — database reads only, never
 * the secret nor a token.
 */
@Component
class HelloAssoStatusReader(
    private val connectionRepository: HelloAssoConnectionRepository,
    private val userRepository: UserRepository,
    private val properties: HelloAssoProperties,
) {

    /** The association's HelloAsso link details; every field empty while no row exists yet. */
    fun read(associationId: UUID): HelloAssoStatusDto {
        val connection = connectionRepository.findByAssociationId(associationId)
            ?: return HelloAssoStatusDto(link = HelloAssoLinkState.NONE, webhook = HelloAssoWebhookState.UNKNOWN)
        val base = properties.notificationBaseUrl.trim().trimEnd('/')
        return HelloAssoStatusDto(
            link = when (connection.state) {
                HelloAssoConnectionState.ACTIVE -> HelloAssoLinkState.OK
                HelloAssoConnectionState.BROKEN -> HelloAssoLinkState.BROKEN
                HelloAssoConnectionState.NOT_LINKED, HelloAssoConnectionState.DISCONNECTED -> HelloAssoLinkState.NONE
            },
            notificationUrl = base.takeIf { it.isNotEmpty() }
                ?.let { it + HelloAssoCredentialsService.NOTIFICATION_PATH + connection.webhookToken },
            organizationSlug = connection.organizationSlug,
            canCollect = connection.canCollect,
            everCompliant = connection.everCompliant,
            lastEligibilityCheckAt = connection.lastEligibilityCheckAt,
            webhook = HelloAssoWebhookState.of(connection),
            clientIdMasked = HelloAssoLinkService.maskClientId(connection.clientId),
            linkedAt = connection.linkedAt,
            linkedBy = connection.linkedBy?.let { userRepository.findById(it).orElse(null)?.email },
            secretWipedAt = connection.secretWipedAt,
            lastRefreshAt = connection.lastRefreshAt,
        )
    }
}
