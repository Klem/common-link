package org.commonlink.repository

import jakarta.persistence.EntityManager
import org.commonlink.entity.HelloAssoDonationFunds
import java.util.UUID

/**
 * Row-locked read of several [HelloAssoDonationFunds], mixed into [HelloAssoDonationFundsRepository] as a Spring Data
 * fragment (implemented by [HelloAssoDonationFundsLockingImpl]).
 */
interface HelloAssoDonationFundsLocking {

    /**
     * The rows of [donationIds] with a row lock (`SELECT … FOR UPDATE`) held until the transaction ends, for the
     * writers that move several rows at once (transfer receipt confirmation, curator release). Locks are taken one by
     * one **in ascending donation id order**, whatever the order given, so that two such writers never deadlock each
     * other nor the tracking pass. Missing ids are skipped. Must run in a transaction.
     *
     * Each returned row is refreshed after its lock: when it is already managed in the persistence context (read
     * unlocked earlier in the same request, open-in-view), the state returned is the one read under the lock, never
     * a stale one.
     */
    fun lockAllForUpdate(donationIds: Collection<UUID>): List<HelloAssoDonationFunds>
}

/** Implementation of [HelloAssoDonationFundsLocking]: native `FOR UPDATE` per row in id order, then a refresh. */
class HelloAssoDonationFundsLockingImpl(private val entityManager: EntityManager) : HelloAssoDonationFundsLocking {

    /** See [HelloAssoDonationFundsLocking.lockAllForUpdate]. */
    override fun lockAllForUpdate(donationIds: Collection<UUID>): List<HelloAssoDonationFunds> =
        donationIds.distinct().sorted().mapNotNull { donationId ->
            val row = entityManager
                .createNativeQuery(
                    "SELECT * FROM helloasso_donation_funds WHERE donation_id = :donationId FOR UPDATE",
                    HelloAssoDonationFunds::class.java,
                )
                .setParameter("donationId", donationId)
                .resultList
                .firstOrNull() as? HelloAssoDonationFunds
            row?.also { entityManager.refresh(it) }
        }
}
