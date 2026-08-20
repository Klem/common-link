package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.OnchainJobAction
import org.commonlink.service.CampaignService
import org.commonlink.service.CampaignIdPayload
import org.commonlink.service.OnchainOutboxService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/onchain")
@Tag(name = "Admin · On-chain moderation", description = "CURATOR-only manual moderation actions.")
@PreAuthorize("hasAnyRole('CURATOR','ADMIN')")
class AdminOnchainController(
    private val campaignService: CampaignService,
    private val outbox: OnchainOutboxService,
) {

    @PostMapping("/campaigns/{id}/{action}")
    @Operation(summary = "Trigger a CURATOR-level campaign action on-chain")
    fun campaignAction(
        @PathVariable id: UUID,
        @PathVariable action: String,
    ): ResponseEntity<JobEnqueuedResponse> {
        val parsedAction = runCatching { CampaignAdminAction.valueOf(action.uppercase()) }
            .getOrElse { return ResponseEntity.badRequest().build() }
        if (!campaignService.existsById(id)) return ResponseEntity.notFound().build()

        val target = when (parsedAction) {
            CampaignAdminAction.PAUSE           -> CampaignStatus.PAUSED
            CampaignAdminAction.UNPAUSE         -> CampaignStatus.LIVE
            CampaignAdminAction.CANCEL          -> CampaignStatus.CANCELLED
            CampaignAdminAction.COMPLETE        -> CampaignStatus.COMPLETED
            CampaignAdminAction.REVERT_TO_DRAFT -> CampaignStatus.DRAFT
        }
        val onchainAction = when (parsedAction) {
            CampaignAdminAction.PAUSE           -> OnchainJobAction.PAUSE_CAMPAIGN
            CampaignAdminAction.UNPAUSE         -> OnchainJobAction.UNPAUSE_CAMPAIGN
            CampaignAdminAction.CANCEL          -> OnchainJobAction.CANCEL_CAMPAIGN
            CampaignAdminAction.COMPLETE        -> OnchainJobAction.COMPLETE_CAMPAIGN
            CampaignAdminAction.REVERT_TO_DRAFT -> OnchainJobAction.REVERT_CAMPAIGN_TO_DRAFT
        }
        campaignService.adminTransition(id, target)
        // For REVERT_TO_DRAFT the association already enqueued the job; use the same correlation key
        // to return the existing job rather than creating a duplicate.
        val correlationKey = if (parsedAction == CampaignAdminAction.REVERT_TO_DRAFT)
            "REVERT_CAMPAIGN_TO_DRAFT:$id" else null
        val job = outbox.enqueue(onchainAction, CampaignIdPayload(id), correlationKey = correlationKey)
        return ResponseEntity.accepted().body(JobEnqueuedResponse(job.id, job.status.name))
    }

    @GetMapping("/jobs/{id}")
    @Operation(summary = "Fetch the status of a previously enqueued on-chain job")
    fun job(@PathVariable id: UUID): ResponseEntity<JobStatusResponse> {
        val job = outbox.find(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            JobStatusResponse(job.id, job.action.name, job.status.name, job.txHash, job.blockNumber, job.attempts, job.lastError)
        )
    }

    enum class CampaignAdminAction { PAUSE, UNPAUSE, CANCEL, COMPLETE, REVERT_TO_DRAFT }

    data class JobEnqueuedResponse(val jobId: UUID, val status: String)
    data class JobStatusResponse(
        val jobId: UUID, val action: String, val status: String,
        val txHash: String?, val blockNumber: Long?, val attempts: Int, val lastError: String?,
    )
}
