package org.commonlink.service

import org.commonlink.dto.CampaignDto
import org.commonlink.dto.CampaignSummaryDto
import org.commonlink.dto.CreateCampaignRequest
import org.commonlink.dto.CreateMilestoneRequest
import org.commonlink.dto.MilestoneDto
import org.commonlink.dto.ReorderMilestonesRequest
import org.commonlink.dto.SaveBudgetRequest
import org.commonlink.dto.UpdateCampaignRequest
import org.commonlink.dto.UpdateMilestoneRequest
import org.commonlink.dto.toDto
import org.commonlink.dto.toSummaryDto
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignBudgetItem
import org.commonlink.entity.CampaignBudgetSection
import org.commonlink.entity.CampaignMilestone
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.MilestoneStatus
import org.commonlink.entity.OnchainJobAction
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignBudgetSectionRepository
import org.commonlink.repository.CampaignMilestoneRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.MoneriumConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Business logic for managing fundraising campaigns of an association.
 *
 * All operations are scoped to the authenticated association: the [userId] from the JWT is
 * resolved to an [org.commonlink.entity.AssociationProfile] id before any data access.
 * This prevents cross-association data leaks.
 *
 * Covers full campaign CRUD, bulk budget replacement, and milestone management.
 */
@Service
class CampaignService(
    private val campaignRepository: CampaignRepository,
    private val campaignBudgetSectionRepository: CampaignBudgetSectionRepository,
    private val campaignMilestoneRepository: CampaignMilestoneRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val moneriumConnectionRepository: MoneriumConnectionRepository,
    private val budgetHasher: CampaignBudgetHasher,
    private val outbox: OnchainOutboxService,
) {

    private val logger = LoggerFactory.getLogger(CampaignService::class.java)

    /** Returns true if a campaign with the given id exists (admin use — no association scoping). */
    fun existsById(id: UUID): Boolean = campaignRepository.existsById(id)

    /**
     * Applies an admin-initiated off-chain status transition and saves the campaign.
     *
     * Used by [org.commonlink.controller.AdminOnchainController] — not scoped to any association.
     * The caller is responsible for enqueueing the corresponding on-chain job afterwards.
     *
     * REVERT_TO_DRAFT is only allowed when [Campaign.raised] is zero, mirroring the
     * contract's `CampaignHasDonations` revert guard.
     *
     * @param id UUID of the campaign.
     * @param target The target [CampaignStatus].
     * @throws org.commonlink.exception.NotFoundException if the campaign does not exist.
     * @throws UnprocessableEntityException if the transition is invalid or REVERT_TO_DRAFT on funded campaign.
     */
    @Transactional
    fun adminTransition(id: UUID, target: CampaignStatus) {
        val campaign = campaignRepository.findById(id)
            .orElseThrow { NotFoundException("Campaign not found: $id") }
        if (target == CampaignStatus.DRAFT) {
            if (campaign.status != CampaignStatus.REVERT_REQUESTED) {
                throw UnprocessableEntityException("Campaign must be in REVERT_REQUESTED state to revert to draft (current: ${campaign.status})")
            }
            if (campaign.raised > BigDecimal.ZERO) {
                throw UnprocessableEntityException("Cannot revert to draft: campaign has raised ${campaign.raised}")
            }
        } else {
            validateStatusTransition(campaign.status, target)
        }
        campaign.status = target
        campaignRepository.save(campaign)
        logger.info("Admin transition: campaignId={}, newStatus={}", id, target)
    }

    /**
     * Returns all campaigns for the authenticated association, sorted by creation date descending.
     *
     * @param userId UUID of the authenticated association user.
     * @return List of [CampaignSummaryDto], possibly empty.
     * @throws UserNotFoundException if no association profile exists for this user.
     */
    fun listCampaigns(userId: UUID): List<CampaignSummaryDto> {
        val associationId = resolveAssociationId(userId)
        return campaignRepository.findAllWithMilestonesByAssociationIdOrderByCreatedAtDesc(associationId)
            .map { it.toSummaryDto() }
    }

    /**
     * Returns full campaign detail including budget sections (with items) and milestones.
     *
     * Uses an [org.springframework.data.jpa.repository.EntityGraph] to load all nested
     * collections in a single query — avoids LazyInitializationException outside a transaction.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign to retrieve.
     * @return [CampaignDto] with all nested data.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     */
    @Transactional(readOnly = true)
    fun getCampaign(userId: UUID, campaignId: UUID): CampaignDto {
        val associationId = resolveAssociationId(userId)
        return resolveCampaignWithDetails(campaignId, associationId).toDto()
    }

    /**
     * Creates a new campaign under the authenticated association.
     *
     * @param userId UUID of the authenticated association user.
     * @param req Creation request with campaign details.
     * @return [CampaignDto] of the persisted campaign.
     * @throws UserNotFoundException if no association profile exists for this user.
     */
    @Transactional
    fun createCampaign(userId: UUID, req: CreateCampaignRequest): CampaignDto {
        val association = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        val campaign = Campaign(
            association = association,
            name = req.name,
            emoji = req.emoji ?: "🌍",
            description = req.description,
            goal = req.goal ?: BigDecimal.ZERO,
            startDate = req.startDate,
            endDate = req.endDate
        )
        val saved = campaignRepository.save(campaign)
        logger.info("Campaign created: id={}, name={}, associationId={}", saved.id, saved.name, association.id)
        return saved.toDto()
    }

    /**
     * Partially updates a campaign — only non-null fields in [req] are applied.
     *
     * Status transitions are validated: DRAFT→LIVE and LIVE→ENDED are allowed;
     * backwards transitions (e.g. ENDED→LIVE) are rejected with a 422.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign to update.
     * @param req Update request; null fields are ignored.
     * @return Updated [CampaignDto].
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     * @throws UnprocessableEntityException if the requested status transition is invalid.
     */
    @Transactional
    fun updateCampaign(userId: UUID, campaignId: UUID, req: UpdateCampaignRequest): CampaignDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)

        if (req.name != null) campaign.name = req.name
        if (req.emoji != null) campaign.emoji = req.emoji
        if (req.description != null) campaign.description = req.description
        if (req.goal != null) campaign.goal = req.goal
        if (req.startDate != null) campaign.startDate = req.startDate
        if (req.endDate != null) campaign.endDate = req.endDate
        if (req.category != null) campaign.category = req.category
        if (req.reason != null) campaign.reason = req.reason
        if (req.impactGoals != null) campaign.impactGoals = req.impactGoals
        if (req.coverImage != null) campaign.coverImage = req.coverImage

        val effectiveStart = campaign.startDate
        val effectiveEnd = campaign.endDate
        if (effectiveStart != null && effectiveEnd != null && effectiveEnd < effectiveStart.plusDays(7)) {
            throw UnprocessableEntityException("End date must be at least 7 days after start date")
        }

        val previousStatus = campaign.status
        if (req.status != null) {
            validateStatusTransition(campaign.status, req.status)
            if (previousStatus == CampaignStatus.DRAFT && req.status == CampaignStatus.LIVE) {
                preparePublish(campaign, associationId)
            }
            if (req.status == CampaignStatus.REVERT_REQUESTED && campaign.raised > BigDecimal.ZERO) {
                throw UnprocessableEntityException("Cannot revert to draft: campaign has raised ${campaign.raised}")
            }
            campaign.status = req.status
        }
        campaign.updatedAt = Instant.now()
        campaignRepository.save(campaign)

        if (req.status != null && req.status != previousStatus) {
            enqueueForTransition(previousStatus, req.status, campaign)
        }

        logger.debug("Campaign updated: id={}, associationId={}", campaignId, associationId)
        return resolveCampaignWithDetails(campaignId, associationId).toDto()
    }

    /**
     * Deletes a campaign and all its related data (budget sections, items, milestones via cascade).
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign to delete.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     */
    @Transactional
    fun deleteCampaign(userId: UUID, campaignId: UUID) {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        campaignRepository.delete(campaign)
        logger.info("Campaign deleted: id={}, associationId={}", campaignId, associationId)
    }

    /**
     * Replaces the entire budget structure for a campaign in a single atomic operation.
     *
     * All existing sections and items are deleted before the new structure is persisted.
     * Sections and items are ordered according to the [sortOrder] fields in [req].
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign whose budget is being replaced.
     * @param req New budget structure (sections → items).
     * @return Updated [CampaignDto] with the new budget.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     */
    @Transactional
    fun saveBudget(userId: UUID, campaignId: UUID, req: SaveBudgetRequest): CampaignDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)

        // Clear the parent collection so orphanRemoval deletes all existing sections and items
        campaign.budgetSections.clear()
        campaignRepository.saveAndFlush(campaign)

        req.sections.forEach { sectionReq ->
            val section = CampaignBudgetSection(
                campaign = campaign,
                side = sectionReq.side,
                code = sectionReq.code,
                name = sectionReq.name,
                sortOrder = sectionReq.sortOrder
            )
            sectionReq.items.forEach { itemReq ->
                val item = CampaignBudgetItem(
                    section = section,
                    label = itemReq.label,
                    amount = itemReq.amount,
                    sortOrder = itemReq.sortOrder
                )
                section.items.add(item)
            }
            // Keep the parent collection in sync so orphanRemoval does not delete the new section
            campaign.budgetSections.add(section)
        }

        campaign.updatedAt = Instant.now()
        campaignRepository.saveAndFlush(campaign)

        val newHash = budgetHasher.hash(campaign)
        if (newHash != campaign.budgetHash) {
            campaign.budgetHash = newHash
            campaignRepository.save(campaign)
            if (campaign.status != CampaignStatus.DRAFT) {
                outbox.enqueue(
                    OnchainJobAction.UPDATE_CAMPAIGN_BUDGET,
                    UpdateCampaignBudgetPayload(campaign.id!!, newHash),
                    correlationKey = null,
                )
                logger.info("UPDATE_CAMPAIGN_BUDGET enqueued: campaignId={}", campaign.id)
            }
        }

        logger.debug("Budget saved for campaign: id={}, sections={}", campaignId, req.sections.size)
        return resolveCampaignWithDetails(campaignId, associationId).toDto()
    }

    /**
     * Adds a new milestone to a campaign.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign to add the milestone to.
     * @param req Milestone creation request.
     * @return [MilestoneDto] of the persisted milestone.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     */
    @Transactional
    fun addMilestone(userId: UUID, campaignId: UUID, req: CreateMilestoneRequest): MilestoneDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestone = CampaignMilestone(
            campaign = campaign,
            emoji = req.emoji ?: "🎯",
            title = req.title,
            description = req.description,
            transparencyCommitment = req.transparencyCommitment,
            targetAmount = req.targetAmount,
            sortOrder = req.sortOrder
        )
        val saved = campaignMilestoneRepository.save(milestone)
        logger.info("Milestone added: id={}, campaignId={}, title={}", saved.id, campaignId, saved.title)
        return saved.toDto()
    }

    /**
     * Partially updates a milestone — only non-null fields in [req] are applied.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign that owns the milestone.
     * @param milestoneId UUID of the milestone to update.
     * @param req Update request; null fields are ignored.
     * @return Updated [MilestoneDto].
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign or milestone is not found.
     */
    @Transactional
    fun updateMilestone(
        userId: UUID,
        campaignId: UUID,
        milestoneId: UUID,
        req: UpdateMilestoneRequest
    ): MilestoneDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestone = campaignMilestoneRepository.findByIdAndCampaignId(milestoneId, campaign.id!!)
            .orElseThrow { NotFoundException("Milestone not found") }

        if (req.title != null) milestone.title = req.title
        if (req.emoji != null) milestone.emoji = req.emoji
        if (req.description != null) milestone.description = req.description
        if (req.transparencyCommitment != null) milestone.transparencyCommitment = req.transparencyCommitment
        if (req.targetAmount != null) milestone.targetAmount = req.targetAmount
        if (req.status != null) milestone.status = req.status
        if (req.sortOrder != null) milestone.sortOrder = req.sortOrder

        val saved = campaignMilestoneRepository.save(milestone)
        logger.debug("Milestone updated: id={}, campaignId={}", milestoneId, campaignId)
        return saved.toDto()
    }

    /**
     * Deletes a milestone from a campaign.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign that owns the milestone.
     * @param milestoneId UUID of the milestone to delete.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign or milestone is not found.
     */
    @Transactional
    fun deleteMilestone(userId: UUID, campaignId: UUID, milestoneId: UUID) {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestone = campaignMilestoneRepository.findByIdAndCampaignId(milestoneId, campaign.id!!)
            .orElseThrow { NotFoundException("Milestone not found") }
        campaignMilestoneRepository.delete(milestone)
        logger.info("Milestone deleted: id={}, campaignId={}", milestoneId, campaignId)
    }

    /**
     * Marks a milestone as reached, sets its [CampaignMilestone.reachedAt] timestamp, and
     * enqueues a [OnchainJobAction.MARK_MILESTONE_REACHED] job with the keccak256 hash of [proofUrl].
     *
     * The milestone's [CampaignMilestone.sortOrder] is used as the 0-based on-chain index — it
     * must match the position the milestone occupied when [OnchainJobAction.CREATE_CAMPAIGN] was
     * dispatched.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign that owns the milestone.
     * @param milestoneId UUID of the milestone to mark as reached.
     * @param proofUrl URL of the proof document. Its keccak256 hash is stored on-chain.
     * @return Updated [MilestoneDto].
     * @throws NotFoundException if the campaign or milestone is not found.
     * @throws UnprocessableEntityException if the milestone is already reached.
     */
    @Transactional
    fun markMilestoneReached(userId: UUID, campaignId: UUID, milestoneId: UUID, proofUrl: String): MilestoneDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestone = campaignMilestoneRepository.findByIdAndCampaignId(milestoneId, campaign.id!!)
            .orElseThrow { NotFoundException("Milestone not found") }

        if (milestone.status == MilestoneStatus.REACHED) {
            throw UnprocessableEntityException("Milestone is already reached")
        }

        milestone.status = MilestoneStatus.REACHED
        milestone.reachedAt = Instant.now()
        campaignMilestoneRepository.save(milestone)

        val proofHashHex = Numeric.toHexString(Hash.sha3(proofUrl.toByteArray(Charsets.UTF_8)))
        outbox.enqueue(
            OnchainJobAction.MARK_MILESTONE_REACHED,
            MilestonePayload(campaignId, milestone.sortOrder, proofHashHex),
            correlationKey = "MILESTONE:${campaignId}:${milestone.sortOrder}",
        )
        logger.info("MARK_MILESTONE_REACHED enqueued: campaignId={}, index={}", campaignId, milestone.sortOrder)
        return milestone.toDto()
    }

    /**
     * Reorders milestones for a campaign by reassigning [CampaignMilestone.sortOrder] based on
     * the position of each milestone ID in [req.milestoneIds].
     *
     * The first ID in the list gets sortOrder=0, the second gets sortOrder=1, and so on.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign whose milestones are being reordered.
     * @param req Ordered list of milestone UUIDs.
     * @return Updated list of [MilestoneDto] in the new order.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign or any milestone is not found.
     */
    @Transactional
    fun reorderMilestones(userId: UUID, campaignId: UUID, req: ReorderMilestonesRequest): List<MilestoneDto> {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestones = campaignMilestoneRepository.findAllByCampaignIdOrderBySortOrder(campaign.id!!)
        val milestoneMap = milestones.associateBy { it.id }

        req.milestoneIds.forEachIndexed { index, milestoneId ->
            val milestone = milestoneMap[milestoneId]
                ?: throw NotFoundException("Milestone not found: $milestoneId")
            milestone.sortOrder = index
        }

        campaignMilestoneRepository.saveAll(milestones)
        logger.debug("Milestones reordered for campaign: id={}", campaignId)

        return campaignMilestoneRepository.findAllByCampaignIdOrderBySortOrder(campaign.id!!)
            .map { it.toDto() }
    }

    /**
     * Resolves the association profile id for the given user.
     *
     * @param userId UUID of the authenticated user.
     * @return UUID of the corresponding [org.commonlink.entity.AssociationProfile].
     * @throws UserNotFoundException if no profile exists for this user.
     */
    private fun resolveAssociationId(userId: UUID): UUID =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .id!!

    /**
     * Resolves a campaign by its id, verifying it belongs to the given association.
     * Collections are not eagerly loaded — use [resolveCampaignWithDetails] when DTO mapping is needed.
     *
     * @param campaignId UUID of the campaign.
     * @param associationId UUID of the owning association.
     * @return The [Campaign] entity.
     * @throws NotFoundException if the campaign does not exist or belongs to a different association.
     */
    private fun resolveCampaign(campaignId: UUID, associationId: UUID): Campaign =
        campaignRepository.findByIdAndAssociationId(campaignId, associationId)
            .orElseThrow { NotFoundException("Campaign not found") }

    /**
     * Resolves a campaign with all nested collections eagerly loaded via [org.springframework.data.jpa.repository.EntityGraph].
     *
     * Fetches budgetSections, budgetSections.items, and milestones in a single query.
     * Use this when you need to call [org.commonlink.dto.toDto] outside a transaction.
     *
     * @param campaignId UUID of the campaign.
     * @param associationId UUID of the owning association.
     * @return The [Campaign] entity with all collections initialised.
     * @throws NotFoundException if the campaign does not exist or belongs to a different association.
     */
    private fun resolveCampaignWithDetails(campaignId: UUID, associationId: UUID): Campaign =
        campaignRepository.findWithDetailsByIdAndAssociationId(campaignId, associationId)
            .orElseThrow { NotFoundException("Campaign not found") }

    /**
     * Validates that the requested status transition is allowed.
     *
     * @param current Current status of the campaign.
     * @param next Requested new status.
     * @throws UnprocessableEntityException if the transition is not allowed.
     */
    private fun validateStatusTransition(current: CampaignStatus, next: CampaignStatus) {
        val allowed = when (current) {
            CampaignStatus.DRAFT            -> next == CampaignStatus.LIVE || next == CampaignStatus.ENDED
            CampaignStatus.LIVE             -> next == CampaignStatus.PAUSED || next == CampaignStatus.CANCELLED ||
                                               next == CampaignStatus.COMPLETED || next == CampaignStatus.ENDED ||
                                               next == CampaignStatus.REVERT_REQUESTED
            CampaignStatus.PAUSED           -> next == CampaignStatus.LIVE || next == CampaignStatus.CANCELLED ||
                                               next == CampaignStatus.COMPLETED ||
                                               next == CampaignStatus.REVERT_REQUESTED
            CampaignStatus.REVERT_REQUESTED -> false  // terminal for association; only CURATOR can move it
            CampaignStatus.ENDED            -> false
            CampaignStatus.CANCELLED        -> false
            CampaignStatus.COMPLETED        -> false
        }
        if (!allowed) {
            throw UnprocessableEntityException("Invalid status transition from $current to $next")
        }
    }

    /**
     * Pre-save checks and preparation for the DRAFT→LIVE publish transition.
     * Sets [Campaign.budgetHash] on the campaign instance (persisted by the caller's save).
     */
    private fun preparePublish(campaign: Campaign, associationId: UUID) {
        if (campaign.goal <= BigDecimal.ZERO) {
            throw UnprocessableEntityException("Campaign goal must be greater than zero before publishing")
        }
        val walletAddress = moneriumConnectionRepository.findByAssociationId(associationId)?.walletAddress
            ?: throw UnprocessableEntityException("Association must connect Monerium before going live")
        campaign.budgetHash = budgetHasher.hash(campaign)
        logger.debug(
            "Publish prepared: campaignId={}, walletAddress={}, budgetHash={}",
            campaign.id, walletAddress, campaign.budgetHash,
        )
    }

    /**
     * Enqueues on-chain jobs for the given status transition.
     * Must be called after the campaign has been saved so [Campaign.id] is stable.
     */
    private fun enqueueForTransition(from: CampaignStatus, to: CampaignStatus, campaign: Campaign) {
        val id = campaign.id!!
        when {
            from == CampaignStatus.DRAFT && to == CampaignStatus.LIVE -> {
                val walletAddress = moneriumConnectionRepository.findByAssociationId(
                    campaign.association.id!!
                )!!.walletAddress!!
                outbox.enqueue(
                    OnchainJobAction.CREATE_CAMPAIGN,
                    CreateCampaignPayload(
                        campaignId     = id,
                        association    = walletAddress,
                        goalCents      = org.commonlink.onchain.OnchainCodec.eurToCents(campaign.goal),
                        milestoneCount = campaign.milestones.size,
                        budgetHashHex  = campaign.budgetHash!!,
                    ),
                    correlationKey = "CREATE_CAMPAIGN:$id",
                )
                outbox.enqueue(
                    OnchainJobAction.PUBLISH_CAMPAIGN,
                    CampaignIdPayload(id),
                    correlationKey = "PUBLISH_CAMPAIGN:$id",
                )
                logger.info("CREATE_CAMPAIGN + PUBLISH_CAMPAIGN enqueued: campaignId={}", id)
            }
            to == CampaignStatus.PAUSED ->
                outbox.enqueue(OnchainJobAction.PAUSE_CAMPAIGN, CampaignIdPayload(id), null)
            from == CampaignStatus.PAUSED && to == CampaignStatus.LIVE ->
                outbox.enqueue(OnchainJobAction.UNPAUSE_CAMPAIGN, CampaignIdPayload(id), null)
            to == CampaignStatus.CANCELLED ->
                outbox.enqueue(OnchainJobAction.CANCEL_CAMPAIGN, CampaignIdPayload(id), null)
            to == CampaignStatus.COMPLETED || to == CampaignStatus.ENDED ->
                outbox.enqueue(OnchainJobAction.COMPLETE_CAMPAIGN, CampaignIdPayload(id), null)
            to == CampaignStatus.REVERT_REQUESTED ->
                outbox.enqueue(OnchainJobAction.REVERT_CAMPAIGN_TO_DRAFT, CampaignIdPayload(id), "REVERT_CAMPAIGN_TO_DRAFT:$id")
        }
    }
}
