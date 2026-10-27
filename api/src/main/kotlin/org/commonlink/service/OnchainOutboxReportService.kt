package org.commonlink.service

import org.commonlink.config.OnchainConfig
import org.commonlink.dto.OnchainFailedJobDto
import org.commonlink.dto.OnchainJobFeedDto
import org.commonlink.dto.OnchainJobFeedItemDto
import org.commonlink.dto.OnchainOutboxHealthDto
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.onchain.ExplorerLinkBuilder
import org.commonlink.repository.OnchainJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Read-only view of the on-chain outbox for the compliance back-office: how many registry writes wait, run or failed
 * on the chain the backend currently targets. Jobs of other chains (e.g. the simulated chain `0`) are never counted.
 * Nothing here changes a job: resetting a failed one is an operator decision.
 */
@Service
class OnchainOutboxReportService(
    private val cfg: OnchainConfig,
    private val jobs: OnchainJobRepository,
    private val explorer: ExplorerLinkBuilder,
) {

    /** Counts of the outbox for the current chain, and the age of its oldest pending job. */
    @Transactional(readOnly = true)
    fun health(): OnchainOutboxHealthDto {
        val chainId = cfg.currentChainId()
        return OnchainOutboxHealthDto(
            chainId = chainId,
            pending = jobs.countByChainIdAndStatus(chainId, OnchainJobStatus.PENDING),
            running = jobs.countByChainIdAndStatus(chainId, OnchainJobStatus.RUNNING),
            failed = jobs.countByChainIdAndStatus(chainId, OnchainJobStatus.FAILED),
            blockedByFailedDependency = jobs.countBlockedByFailedDependency(chainId),
            oldestPendingSince = jobs.findFirstByChainIdAndStatusOrderByCreatedAtAsc(chainId, OnchainJobStatus.PENDING)
                ?.createdAt,
        )
    }

    /**
     * Live feed: the fifty most recently queued writes of the current chain, every status, newest first. Works in
     * simulated mode too (flagged `simulated`, no explorer link), so the screen behaves the same before and after the
     * switch to a real chain.
     */
    @Transactional(readOnly = true)
    fun recentJobs(): OnchainJobFeedDto {
        val chainId = cfg.currentChainId()
        return OnchainJobFeedDto(
            chainId = chainId,
            simulated = cfg.mock,
            items = jobs.findTop50ByChainIdOrderByCreatedAtDesc(chainId).map {
                OnchainJobFeedItemDto(
                    id = it.id,
                    action = it.action.name,
                    status = it.status.name,
                    attempts = it.attempts,
                    txHash = it.txHash,
                    blockNumber = it.blockNumber,
                    explorerTxUrl = explorer.txUrl(it.txHash),
                    error = maskUrls(it.lastError),
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
        )
    }

    /** The hundred most recently queued failed jobs of the current chain, newest first, errors with URLs masked. */
    @Transactional(readOnly = true)
    fun failedJobs(): List<OnchainFailedJobDto> =
        jobs.findTop100ByChainIdAndStatusOrderByCreatedAtDesc(cfg.currentChainId(), OnchainJobStatus.FAILED).map {
            OnchainFailedJobDto(
                id = it.id,
                action = it.action.name,
                attempts = it.attempts,
                error = maskUrls(it.lastError),
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
            )
        }

    companion object {
        private val URL = Regex("""\b(?:https?|wss?)://\S+""", RegexOption.IGNORE_CASE)

        /**
         * Replaces every URL of [error] with `<url>`: a web3j message may quote the RPC URL, whose path or query often
         * carries a provider API key.
         *
         * @return the masked error, or null when [error] is null.
         */
        fun maskUrls(error: String?): String? = error?.replace(URL, "<url>")
    }
}
