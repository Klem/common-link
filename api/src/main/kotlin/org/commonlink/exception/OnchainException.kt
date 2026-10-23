package org.commonlink.exception

/**
 * The entity a registration job targets is already registered on-chain, but with other data (SIREN hash,
 * owning association or budget hash). Permanent: retrying can never succeed, so the worker fails the job at once.
 * Never reaches HTTP: it is raised and caught inside the on-chain worker.
 */
class OnchainRegistrationMismatchException(message: String) : IllegalStateException(message)
