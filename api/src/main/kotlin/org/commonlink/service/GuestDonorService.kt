package org.commonlink.service

import org.commonlink.entity.AuthProvider
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Outcome of resolving the donor behind a widget donation.
 *
 * [ownedByGuest] is what tells the caller whether the request body may influence the profile.
 * It is `false` as soon as the e-mail belongs to a real account — the donation still attaches to
 * that account (its history and its fiscal receipts belong to the person), but nothing an
 * unauthenticated caller typed may be written onto it.
 *
 * @property profile Donor profile the donation must be attached to.
 * @property ownedByGuest `true` only when the backing [User] is a guest row this flow owns.
 */
data class ResolvedDonor(
    val profile: DonorProfile,
    val ownedByGuest: Boolean,
)

/**
 * Provisions guest donor accounts for the donation widget.
 *
 * A guest donor is a [User] with [AuthProvider.GUEST] and [User.guest] = true, backed by a
 * [DonorProfile]. The account is non-connectable: no password, no Google sub, unverified email.
 * On-chain behaviour is identical to a regular donor — the wallet address is derived lazily
 * by [org.commonlink.onchain.DonorAddressGenerator] on first donation confirmation.
 *
 * **Security boundary** — this service is reached from an unauthenticated endpoint, with an
 * arbitrary e-mail address in the body. It therefore never treats "an account exists for this
 * e-mail" as "the caller owns this account": see [ResolvedDonor.ownedByGuest]
 * (security audit 2026-08-20, M1).
 */
@Service
class GuestDonorService(
    private val userRepository: UserRepository,
    private val donorProfileRepository: DonorProfileRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Resolves the [DonorProfile] a widget donation must be attached to, creating a guest account
     * when the e-mail is unknown.
     *
     * Idempotent: two calls with the same email always resolve to the same [DonorProfile].
     * Email is normalised (trimmed + lowercased) before lookup and storage.
     *
     * [displayName] is honoured **only** when this call creates the guest account. When the e-mail
     * already belongs to someone — guest or real — the stored profile wins: an unauthenticated
     * caller must not be able to rename a donor, and a profile created for a real account (an
     * association making a donation, say) starts anonymous with no name rather than carrying
     * whatever the request body contained.
     *
     * @param email Donor's email address (normalised internally).
     * @param displayName Public display name, applied only to a freshly created guest profile.
     * @return The profile to attach the donation to, and whether this flow owns it.
     */
    @Transactional
    fun findOrCreateGuestDonor(email: String, displayName: String?): ResolvedDonor {
        val normalizedEmail = email.trim().lowercase()

        val existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null)
        if (existingUser == null) {
            logger.info("Creating guest user for email={}", normalizedEmail)
            val user = userRepository.save(
                User(
                    email = normalizedEmail,
                    role = UserRole.DONOR,
                    provider = AuthProvider.GUEST,
                    guest = true,
                    emailVerified = false,
                )
            )
            val profile = donorProfileRepository.save(
                DonorProfile(user = user, displayName = displayName, anonymous = true)
            )
            return ResolvedDonor(profile, ownedByGuest = true)
        }

        val ownedByGuest = existingUser.guest
        val profile = donorProfileRepository.findByUserId(existingUser.id!!).orElseGet {
            // A real account with no donor profile yet (e.g. an ASSOCIATION user donating).
            // Created with privacy-preserving defaults, never with caller-supplied values.
            logger.info("Creating donor profile for existing user={} (guest={})", existingUser.id, ownedByGuest)
            donorProfileRepository.save(
                DonorProfile(
                    user = existingUser,
                    displayName = if (ownedByGuest) displayName else null,
                    anonymous = true,
                )
            )
        }
        return ResolvedDonor(profile, ownedByGuest)
    }
}
