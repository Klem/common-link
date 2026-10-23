package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.onchain.OnchainCodec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.utils.Numeric

/**
 * Enqueues the registry v2 **registrations** on the current chain: an association and a campaign are each
 * registered once and forever (no lifecycle on-chain).
 *
 * Every write that references a campaign (donation, payout, budget update) first calls
 * [ensureCampaignRegistered] and depends on the returned job, so the outbox always registers the
 * association, then the campaign, then the rest — whatever the order in which the business events happen
 * (an association completing Mollie through a path that publishes no event, or a chain switch: a campaign
 * published in mock mode gets registered on the real chain with its first new donation or payout).
 * All methods are idempotent through the per-chain correlation keys `REGISTER_ASSOCIATION:<associationId>`,
 * `REGISTER_CAMPAIGN:<campaignId>` and `UPDATE_CAMPAIGN_BUDGET:<campaignId>:<previousBudgetJobId>`.
 */
@Service
class OnchainRegistrationService(
    private val outbox: OnchainOutboxService,
    private val objectMapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Ensures the association is (or will be) registered on the current chain.
     *
     * The registered legal identifier hash is `keccak256(siren)` when the association has a SIREN, otherwise
     * `keccak256(identifier)` (RNA number) — associations without a SIREN exist.
     *
     * @param association association able to collect donations (the caller decides).
     * @return the registration job (existing or new), or null when the association has no on-chain address yet.
     */
    @Transactional
    fun ensureAssociationRegistered(association: AssociationProfile): OnchainJob? {
        val address = association.walletAddress
        if (address.isNullOrBlank()) {
            logger.warn("Association {} has no on-chain address: registration skipped", association.id)
            return null
        }
        val legalId = association.siren?.takeIf { it.isNotBlank() } ?: association.identifier
        return outbox.enqueue(
            OnchainJobAction.REGISTER_ASSOCIATION,
            RegisterAssociationPayload(address, Numeric.toHexString(OnchainCodec.keccakSiren(legalId.trim()))),
            correlationKey = associationKey(association),
        )
    }

    /**
     * Ensures the campaign (and first its association) is (or will be) registered on the current chain, with
     * the budget hash current at enqueue time. The campaign job depends on the association job.
     *
     * @param campaign campaign that went live at least once (the caller decides).
     * @return the campaign registration job (existing or new), or null when a prerequisite is missing
     *   (association address, budget hash) — logged at WARN.
     */
    @Transactional
    fun ensureCampaignRegistered(campaign: Campaign): OnchainJob? {
        val budgetHash = campaign.budgetHash
        if (budgetHash.isNullOrBlank()) {
            logger.warn("Campaign {} has no budget hash: registration skipped", campaign.id)
            return null
        }
        val associationJob = ensureAssociationRegistered(campaign.association) ?: return null
        return outbox.enqueue(
            OnchainJobAction.REGISTER_CAMPAIGN,
            RegisterCampaignPayload(campaign.id!!, campaign.association.walletAddress!!, budgetHash),
            correlationKey = campaignKey(campaign),
            dependsOn = associationJob,
        )
    }

    /**
     * Records the campaign's CURRENT budget hash on-chain: registers the campaign (with that hash) when it is
     * not registered yet on the current chain, otherwise enqueues a budget update **only when the hash differs
     * from the one last declared** on that chain (registration or latest update). Each update depends on the
     * previous budget job, so successive updates land on-chain in order and the last declared hash wins.
     * Called when a campaign goes live (including a republication after a revert to draft, whose budget may have
     * changed meanwhile) and when the budget of a non-draft campaign changes.
     *
     * An update's correlation key names its predecessor (`UPDATE_CAMPAIGN_BUDGET:<campaignId>:<previousJobId>`):
     * a replay is idempotent, and two concurrent budget saves chaining onto the same predecessor cannot both
     * commit (unique `(chain_id, correlation_key)`) — the loser gets a 409 (its transaction rolls back) instead of reordering budgets.
     *
     * @param campaign campaign whose `budgetHash` is up to date.
     * @return the enqueued update, or the latest budget job when nothing changed (or the hash is missing),
     *   or the registration job, or null when a prerequisite of the registration is missing.
     */
    @Transactional
    fun recordCurrentBudget(campaign: Campaign): OnchainJob? {
        val latest = outbox.findLatestBudgetJob(campaignKey(campaign), budgetUpdateKeyPrefix(campaign))
            ?: return ensureCampaignRegistered(campaign)
        val budgetHash = campaign.budgetHash ?: return latest
        if (declaredBudgetHash(latest) == budgetHash) {
            logger.debug("Campaign {} budget unchanged since job {}: nothing enqueued", campaign.id, latest.id)
            return latest
        }
        logger.info("UPDATE_CAMPAIGN_BUDGET enqueued: campaignId={}, after job {}", campaign.id, latest.id)
        return outbox.enqueue(
            OnchainJobAction.UPDATE_CAMPAIGN_BUDGET,
            UpdateCampaignBudgetPayload(campaign.id!!, budgetHash),
            correlationKey = budgetUpdateKeyPrefix(campaign) + latest.id,
            dependsOn = latest,
        )
    }

    /**
     * @param job a `REGISTER_CAMPAIGN` or `UPDATE_CAMPAIGN_BUDGET` job.
     * @return the budget hash that job declares, or null when its payload carries none.
     */
    private fun declaredBudgetHash(job: OnchainJob): String? {
        val field = if (job.action == OnchainJobAction.UPDATE_CAMPAIGN_BUDGET) "newBudgetHashHex" else "budgetHashHex"
        return objectMapper.readTree(job.payloadJson).path(field).takeIf { it.isTextual }?.asText()
    }

    private fun associationKey(association: AssociationProfile) = "REGISTER_ASSOCIATION:${association.id}"
    private fun campaignKey(campaign: Campaign) = "REGISTER_CAMPAIGN:${campaign.id}"
    private fun budgetUpdateKeyPrefix(campaign: Campaign) = "UPDATE_CAMPAIGN_BUDGET:${campaign.id}:"
}
