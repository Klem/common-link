package org.commonlink.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Enables Spring's `@Scheduled` support application-wide.
 *
 * Extracted from [Web3jConfig] — do not re-inline it there: `Web3jConfig` is now conditional on
 * `onchain.mock=false`, so hosting `@EnableScheduling` in it would silently kill every scheduler
 * (`OnchainJobWorker`, `MollieTokenRefreshScheduler`, `SanctionSyncScheduler`,
 * `DonationReceiptService`) whenever mock mode is on.
 */
@Configuration
@EnableScheduling
class SchedulingConfig
