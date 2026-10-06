package org.commonlink.service

import org.commonlink.repository.CampaignRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * One-shot backfill of the place of action of campaigns created before V82, from the commune of
 * their association's headquarters ([ActionPlaceService.fromAssociationAddress]).
 *
 * - **Opt-in**: only registered when `app.campaign.action-place-backfill.enabled=true` (default
 *   false), so tests and CI never call geo.api.gouv.fr. Enable it for one deploy, then turn it off.
 * - **Idempotent**: only campaigns whose place is still null are touched; a re-run is a no-op for
 *   everything already filled, including places the association set itself.
 * - **Non-blocking**: runs asynchronously once the application is ready; a geocoding miss or an
 *   upstream outage leaves the campaign null and is logged, never failing the boot.
 */
@Component
@ConditionalOnProperty(name = ["app.campaign.action-place-backfill.enabled"], havingValue = "true")
class ActionPlaceBackfill(
    private val campaignRepository: CampaignRepository,
    private val actionPlaceService: ActionPlaceService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Fills every campaign without a place of action; geocodes each distinct address once. */
    @Async
    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun run() {
        val campaigns = campaignRepository.findAllWithoutActionPlace()
        logger.info("Action place backfill: {} campaign(s) without a place of action", campaigns.size)
        val byAddress = campaigns.groupBy { it.association.city to it.association.postalCode }
        var filled = 0
        byAddress.forEach { (address, group) ->
            val place = actionPlaceService.fromAssociationAddress(address.first, address.second) ?: return@forEach
            group.forEach { actionPlaceService.applyTo(it, place) }
            filled += group.size
        }
        logger.info("Action place backfill: filled {} / {} campaign(s)", filled, campaigns.size)
    }
}
