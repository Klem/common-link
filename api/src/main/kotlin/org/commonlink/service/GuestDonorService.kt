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
 * Provisions guest donor accounts for the donation widget.
 *
 * A guest donor is a [User] with [AuthProvider.GUEST] and [User.guest] = true, backed by a
 * [DonorProfile]. The account is non-connectable: no password, no Google sub, unverified email.
 * On-chain behaviour is identical to a regular donor — the wallet address is derived lazily
 * by [org.commonlink.onchain.DonorAddressGenerator] on first donation confirmation.
 */
@Service
class GuestDonorService(
    private val userRepository: UserRepository,
    private val donorProfileRepository: DonorProfileRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns an existing [DonorProfile] for [email], or creates one if none exists.
     *
     * Idempotent: two calls with the same email always return the same [DonorProfile].
     * Email is normalised (trimmed + lowercased) before lookup and storage.
     *
     * @param email Donor's email address (normalised internally).
     * @param displayName Optional public display name set on the [DonorProfile].
     */
    @Transactional
    fun findOrCreateGuestDonor(email: String, displayName: String?): DonorProfile {
        val normalizedEmail = email.trim().lowercase()

        val user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet {
            logger.info("Creating guest user for email={}", normalizedEmail)
            userRepository.save(
                User(
                    email = normalizedEmail,
                    role = UserRole.DONOR,
                    provider = AuthProvider.GUEST,
                    guest = true,
                    emailVerified = false,
                )
            )
        }

        return donorProfileRepository.findByUserId(user.id!!).orElseGet {
            logger.info("Creating donor profile for guest user={}", user.id)
            donorProfileRepository.save(
                DonorProfile(
                    user = user,
                    displayName = displayName,
                    anonymous = true,
                )
            )
        }
    }
}
