package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.service.OnchainOutboxService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Admin access to the on-chain outbox: job status.
 *
 * Registry v2 has no campaign lifecycle on-chain, so the former campaign moderation endpoint
 * (`POST /campaigns/{id}/{action}`: pause / unpause / cancel / complete / revert-to-draft) was removed:
 * campaign statuses are a backend-only concern and are never recorded on-chain.
 */
@RestController
@RequestMapping("/api/admin/onchain")
@Tag(name = "Admin · On-chain", description = "CURATOR/ADMIN access to the on-chain outbox.")
@PreAuthorize("hasAnyRole('CURATOR','ADMIN')")
class AdminOnchainController(
    private val outbox: OnchainOutboxService,
) {

    /**
     * Returns the status of a previously enqueued on-chain job.
     *
     * @param id UUID of the job.
     * @return The job status, or 404 when unknown.
     */
    @GetMapping("/jobs/{id}")
    @Operation(summary = "Fetch the status of a previously enqueued on-chain job")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Job returned"),
        ApiResponse(responseCode = "404", description = "Job not found"),
    )
    fun job(@PathVariable id: UUID): ResponseEntity<JobStatusResponse> {
        val job = outbox.find(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            JobStatusResponse(job.id, job.action.name, job.status.name, job.txHash, job.blockNumber, job.attempts, job.lastError)
        )
    }

    /** Status of an on-chain job as exposed to admins. */
    data class JobStatusResponse(
        val jobId: UUID, val action: String, val status: String,
        val txHash: String?, val blockNumber: Long?, val attempts: Int, val lastError: String?,
    )
}
