package org.commonlink.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import org.commonlink.dto.AssociationProfileRequestDto
import org.commonlink.dto.AssociationProfileUpsertDto
import org.commonlink.dto.AuthResponseDto
import org.commonlink.dto.RegisterRequestDto
import org.commonlink.dto.toDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.EmailVerificationToken
import org.commonlink.entity.MagicLinkToken
import org.commonlink.entity.RefreshToken
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.exception.AuthException
import org.commonlink.exception.ConflictException
import org.commonlink.exception.EmailNotVerifiedException
import org.commonlink.exception.InvalidTokenException
import org.commonlink.exception.PasswordNotSetException
import org.commonlink.exception.RateLimitException
import org.commonlink.exception.SirenAlreadyRegisteredException
import org.commonlink.exception.TokenExpiredException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.onchain.AssociationAddressGenerator
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.EmailVerificationTokenRepository
import org.commonlink.repository.MagicLinkTokenRepository
import org.commonlink.repository.RefreshTokenRepository
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtService
import org.commonlink.security.TokenHashService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Central authentication service for the CommonLink platform.
 *
 * Handles all authentication flows:
 * - **Email/password registration** with email verification
 * - **Google OAuth** sign-up and login (with profile merge)
 * - **Magic-link** passwordless authentication (sign-up + login in one step)
 * - **Token lifecycle**: issue, rotate (refresh), and revoke tokens
 * - **Password management**: set password after magic-link / Google sign-up
 *
 * Token security model:
 * - Access tokens are short-lived JWTs (15 min), signed with HS256.
 * - Refresh tokens are opaque 256-bit random values; only their SHA-256 hash is persisted.
 * - Magic-link and email-verification tokens follow the same hash-only storage pattern.
 * - Refresh tokens are rotated on every use (old token revoked, new one issued).
 *
 * Rate limiting is applied in-process using a sliding 10-minute window stored in the DB:
 * - Magic-link requests: max 3 per email per 10 minutes.
 * - Verification email re-sends: max 3 per user per 10 minutes.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val donorProfileRepository: DonorProfileRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val associationAddressGenerator: AssociationAddressGenerator,
    private val magicLinkTokenRepository: MagicLinkTokenRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val jwtService: JwtService,
    private val tokenHashService: TokenHashService,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService,
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    @Value("\${app.frontend-url}") private val frontendUrl: String
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private companion object {
        /** A French SIREN is exactly 9 digits; an RNA is `W` followed by 9 digits. */
        val SIREN_PATTERN = Regex("^\\d{9}$")
    }

    /**
     * Registers a new user with email and password.
     *
     * Creates the [User] record, the appropriate role profile ([DonorProfile] or
     * [AssociationProfile]), and sends an email verification link. The account cannot be
     * used until the verification link is clicked.
     *
     * An address held only by a **guest row** — an account the donation widget created for an
     * arbitrary e-mail — does not block registration: it is claimed instead, keeping the donation
     * history attached. Without this, anyone could permanently deny an association the use of its own
     * address by donating once under it (security audit 2026-08-20, M1).
     *
     * The claim is deliberately limited to guest rows. Extending it to any unverified account would
     * let an attacker overwrite the password and role of a *pending real registration*: the victim's
     * own verification token stays valid, so clicking their original link would activate — and issue
     * tokens for — a row now carrying someone else's password. A guest row carries no such pending
     * token, which is exactly what makes claiming it safe.
     *
     * @param req Registration data including email, password, role, and optional association profile.
     * @throws ConflictException if the email address belongs to a non-guest account.
     * @throws UnprocessableEntityException if [RegisterRequestDto.role] is a back-office role.
     */
    @Transactional
    fun register(req: RegisterRequestDto) {
        requireSelfAssignable(req.role)
        val email = normalizeEmail(req.email)
        val existing = userRepository.findByEmailIgnoreCase(email).orElse(null)
        val user = when {
            existing == null -> userRepository.save(
                User(
                    email = email,
                    role = req.role,
                    provider = AuthProvider.EMAIL,
                    emailVerified = false,
                    passwordHash = passwordEncoder.encode(req.password)
                )
            )
            existing.guest -> {
                logger.info("Claiming guest account {} via registration as {}", existing.id, req.role)
                existing.role = req.role
                existing.provider = AuthProvider.EMAIL
                existing.guest = false
                existing.passwordHash = passwordEncoder.encode(req.password)
                existing.updatedAt = Instant.now()
                userRepository.save(existing)
            }
            else -> throw ConflictException("Email already in use")
        }
        createProfile(user, req.role, req.associationProfile)
        sendVerificationEmail(user)
    }

    /**
     * Verifies a user's email address using the token from the verification link.
     *
     * Marks the token as used, sets [User.emailVerified] to `true`, and immediately
     * issues access + refresh tokens so the user lands in the app without a second login step.
     *
     * @param rawToken The raw opaque token extracted from the verification URL.
     * @return [AuthResponseDto] with a fresh access token and refresh token.
     * @throws InvalidTokenException if no unused token matches the hash.
     * @throws TokenExpiredException if the token's 24-hour window has passed.
     */
    @Transactional
    fun verifyEmail(rawToken: String): AuthResponseDto {
        val hash = tokenHashService.hashToken(rawToken)
        val token = emailVerificationTokenRepository.findByTokenHashAndUsedAtIsNull(hash)
            .orElseThrow { InvalidTokenException() }

        if (token.expiresAt.isBefore(Instant.now())) {
            throw TokenExpiredException()
        }

        token.usedAt = Instant.now()
        emailVerificationTokenRepository.save(token)

        val user = token.user
        user.emailVerified = true
        user.updatedAt = Instant.now()
        userRepository.save(user)

        // A brand-new ASSOCIATION sign-up never has a DonorProfile (createProfile only builds one
        // for DONOR) — one present here means register() claimed a guest row donations hang off.
        // Safe to check unconditionally: this verification token can only ever be consumed once.
        val donorHistoryClaimed = user.role == UserRole.ASSOCIATION &&
            donorProfileRepository.findByUserId(user.id!!).isPresent

        return issueTokens(user, donorHistoryClaimed)
    }

    /**
     * Re-sends the email verification link for an unverified account.
     *
     * Silently returns without action if the account is already verified (idempotent).
     * Enforces a rate limit of 3 sends per 10-minute window to prevent email flooding.
     *
     * @param email The email address of the account to re-verify.
     * @throws AuthException if no account with this email exists.
     * @throws RateLimitException if more than 3 verification emails have been sent in the last 10 minutes.
     */
    @Transactional
    fun resendVerification(email: String) {
        // Silently return when no account found — prevents email enumeration.
        val user = userRepository.findByEmailIgnoreCase(email).orElse(null) ?: return

        // No-op if the email is already verified.
        if (user.emailVerified) return

        // Rate limit: max 3 sends per 10-minute sliding window.
        val rateLimitWindow = Instant.now().minus(10, ChronoUnit.MINUTES)
        if (emailVerificationTokenRepository.countByUserIdAndCreatedAtAfter(user.id!!, rateLimitWindow) >= 3) {
            throw RateLimitException()
        }

        sendVerificationEmail(user)
    }

    /**
     * Creates a new account using a Google ID token.
     *
     * Verifies the Google ID token against Google's public keys, then creates a new [User]
     * row with the Google profile data. Google accounts are considered email-verified by default.
     *
     * @param idToken The Google ID token received from the frontend after the Google sign-in flow.
     * @param role The role the user selected during sign-up (DONOR or ASSOCIATION).
     * @return [AuthResponseDto] with a fresh access token and refresh token.
     * @throws ConflictException if a Google account with this `sub` or this email already exists.
     * @throws AuthException if the Google ID token is invalid or cannot be verified.
     */
    @Transactional
    fun signUpWithGoogle(idToken: String, role: UserRole): AuthResponseDto {
        requireSelfAssignable(role)
        val payload = verifyGoogleToken(idToken)
        val sub = payload.subject
        val email = payload["email"] as String
        val name = payload["name"] as? String
        val picture = payload["picture"] as? String

        // Prevent duplicate accounts: reject if this Google account or email is already registered.
        if (userRepository.findByGoogleSub(sub).isPresent) {
            throw ConflictException("Account already exists")
        }
        val existing = userRepository.findByEmailIgnoreCase(email).orElse(null)
        val user = when {
            existing == null -> userRepository.save(
                User(
                    email = email,
                    role = role,
                    provider = AuthProvider.GOOGLE,
                    googleSub = sub,
                    displayName = name,
                    avatarUrl = picture,
                    emailVerified = true
                )
            )
            // Guest row created by the donation widget: the Google token proves ownership of the
            // address, so it is claimed rather than left blocking sign-up (audit 2026-08-20, M1).
            existing.guest -> {
                logger.info("Claiming guest account {} via Google sign-up as {}", existing.id, role)
                existing.guest = false
                existing.provider = AuthProvider.GOOGLE
                existing.role = role
                existing.googleSub = sub
                existing.displayName = name ?: existing.displayName
                existing.avatarUrl = picture ?: existing.avatarUrl
                existing.emailVerified = true
                existing.updatedAt = Instant.now()
                userRepository.save(existing)
            }
            else -> throw ConflictException("Email already in use")
        }
        createProfile(user, user.role, null)

        // See verifyEmail's identical check: a fresh ASSOCIATION sign-up never gets a DonorProfile,
        // so one present here means a guest row (with donations attached) was just claimed. Safe to
        // check unconditionally — signUpWithGoogle can only succeed once per account (a second call
        // finds the googleSub already taken and throws ConflictException above).
        val donorHistoryClaimed = user.role == UserRole.ASSOCIATION &&
            donorProfileRepository.findByUserId(user.id!!).isPresent

        return issueTokens(user, donorHistoryClaimed)
    }

    /**
     * Authenticates an existing user with a Google ID token.
     *
     * Two resolution paths:
     * 1. **Known Google account** (matched by `sub`): updates display name and avatar if changed, then issues tokens.
     * 2. **Email-only account** (no `googleSub` yet): links the Google identity to the existing account
     *    (sets `googleSub`, marks email as verified), then issues tokens. This handles the case where a user
     *    registered with email/password and is now signing in with Google for the first time.
     *
     * @param idToken The Google ID token from the frontend.
     * @return [AuthResponseDto] with a fresh access token and refresh token.
     * @throws AuthException if no account matches either the Google `sub` or the email address.
     * @throws AuthException if the Google ID token is invalid.
     */
    @Transactional
    fun loginWithGoogle(idToken: String): AuthResponseDto {
        val payload = verifyGoogleToken(idToken)
        val sub = payload.subject
        val email = payload["email"] as String
        val name = payload["name"] as? String
        val picture = payload["picture"] as? String

        val user = userRepository.findByGoogleSub(sub).map { existing ->
            // Path 1: known Google account — sync profile fields if they changed.
            var changed = false
            if (name != null && name != existing.displayName) { existing.displayName = name; changed = true }
            if (picture != null && picture != existing.avatarUrl) { existing.avatarUrl = picture; changed = true }
            if (changed) { existing.updatedAt = Instant.now(); userRepository.save(existing) } else existing
        }.orElseGet {
            // Path 2: no Google account found by sub — try to merge with an existing email account.
            // Auto-merge is safe: email ownership is Google-verified (enforced by verifyGoogleToken above).
            userRepository.findByEmailIgnoreCase(email).map { existing ->
                existing.googleSub = sub
                existing.emailVerified = true
                if (existing.guest) {
                    // Same claiming rule as sign-up: the Google token proves the address is theirs,
                    // so the guest row becomes a real account (audit 2026-08-20, M1). Role is left
                    // as-is (DONOR) — a login carries no role to apply.
                    logger.info("Claiming guest account {} via Google login", existing.id)
                    existing.guest = false
                    existing.provider = AuthProvider.GOOGLE
                }
                existing.updatedAt = Instant.now()
                userRepository.save(existing)
            }.orElseThrow {
                AuthException("No account found. Signup first")
            }
        }

        return issueTokens(user)
    }

    /**
     * Generates and emails a magic-link token to the given address.
     *
     * The [role] parameter is required for new accounts (sign-up flow). For existing accounts
     * (login flow), `role` may be null — the role is inferred from the existing user record.
     *
     * For association sign-ups, [associationProfile] carries the registration data that will be
     * used to create the [AssociationProfile] when the link is verified.
     *
     * Enforces a rate limit of 3 magic links per email per 10 minutes to prevent abuse.
     *
     * @param email The recipient email address.
     * @param role The intended role (required for sign-up; null for login of existing users).
     * @param associationProfile Optional association data for the ASSOCIATION sign-up flow.
     * @throws RateLimitException if more than 3 links have been sent to this email in the last 10 minutes.
     * @throws AuthException if [role] is null and no existing account is found for the email.
     * @throws ConflictException if [role] is supplied (sign-up) and the email already belongs to a
     *   non-guest account — same rule as [register], enforced here too so a magic-link sign-up
     *   cannot silently log the caller into an unrelated existing account under a different role.
     */
    @Transactional
    fun sendMagicLink(rawEmail: String, role: UserRole?, associationProfile: AssociationProfileRequestDto? = null) {
        // Normalised before the quota is counted: otherwise varying the capitalisation of the same
        // address yields a fresh bucket and the 3-per-window limit means nothing.
        val email = normalizeEmail(rawEmail)

        // Rate limit: max 3 magic links per email per 10-minute sliding window.
        val rateLimitWindow = Instant.now().minus(10, ChronoUnit.MINUTES)
        if (magicLinkTokenRepository.countByEmailAndCreatedAtAfter(email, rateLimitWindow) >= 3) {
            throw RateLimitException()
        }

        // A back-office role must never be writable to a magic-link token: verifyMagicLink reads the
        // role back from that row and would create the account with it (audit 2026-08-20, C1).
        // Checked after the quota so the guard cannot be probed for free.
        role?.let { requireSelfAssignable(it) }

        // A role means sign-up intent. Unlike login (role == null), a sign-up must not silently
        // resolve to someone else's existing account: without this, requesting a magic link for an
        // ASSOCIATION sign-up on an email that already has a real DONOR account sent no error, and
        // verifyMagicLink would later just log the caller into that DONOR account. Only a guest row
        // (never a real account) may still be claimed by a sign-up — same rule as [register].
        if (role != null) {
            userRepository.findByEmailIgnoreCase(email).ifPresent { existing ->
                if (!existing.guest) {
                    throw ConflictException("Email already in use")
                }
            }
        }

        // Surface a duplicate SIREN now rather than after the user has clicked the emailed link,
        // where profile creation would fail. No-op for the RNA sign-up flow.
        associationProfile?.let { guardSirenNotAlreadyRegistered(it.identifier) }

        // If role is not supplied, the caller expects an existing account (login flow).
        // Look up the existing user's role; fail if no account is found.
        // When no role is supplied (login flow) and no account exists, return silently — prevents email enumeration.
        val effectiveRole: UserRole = role
            ?: (userRepository.findByEmailIgnoreCase(email).map { it.role }.orElse(null) ?: return)

        val rawToken = tokenHashService.generateOpaqueToken()
        val tokenHash = tokenHashService.hashToken(rawToken)

        // Persist only the hash; the raw token travels exclusively via email.
        magicLinkTokenRepository.save(
            MagicLinkToken(
                email = email,
                tokenHash = tokenHash,
                role = effectiveRole,
                expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES),
                assocName = associationProfile?.name,
                assocIdentifier = associationProfile?.identifier,
                assocCity = associationProfile?.city,
                assocPostalCode = associationProfile?.postalCode
            )
        )

        emailService.sendMagicLink(email, "$frontendUrl/auth/verify-token?token=$rawToken&role=${effectiveRole.name.lowercase()}")
    }

    /**
     * Verifies a magic-link token and authenticates (or registers) the user.
     *
     * Two resolution paths:
     * 1. **Existing account** (matched by email): marks the email as verified and issues tokens.
     *    No role or provider fields are changed — this handles returning users who log in via magic link.
     * 2. **New account**: creates a [User] with `provider = MAGIC_LINK`, then creates the role
     *    profile. For associations, the profile data is read from the token's embedded fields.
     *
     * The token is marked as used *before* the user lookup/creation to prevent a race condition
     * where two concurrent requests with the same token both succeed.
     *
     * @param rawToken The raw opaque token from the magic-link URL.
     * @return [AuthResponseDto] with a fresh access token and refresh token.
     *   [AuthResponseDto.donorHistoryClaimed] is `true` exactly when this call just claimed a guest
     *   row into an ASSOCIATION account.
     * @throws InvalidTokenException if no unused token matches the hash.
     * @throws TokenExpiredException if the token's 15-minute window has passed.
     * @throws ConflictException if the token's role does not match a non-guest existing account's
     *   role — [sendMagicLink] already refuses to issue such a token, this covers one persisted
     *   before that guard existed.
     */
    @Transactional
    fun verifyMagicLink(rawToken: String): AuthResponseDto {
        val hash = tokenHashService.hashToken(rawToken)
        val token = magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull(hash)
            .orElseThrow { InvalidTokenException() }

        if (token.expiresAt.isBefore(Instant.now())) {
            throw TokenExpiredException()
        }

        // Mark token as used first to prevent concurrent verification (anti-replay).
        token.usedAt = Instant.now()
        magicLinkTokenRepository.save(token)

        // Last line of defence: the role comes from a row written at request time, so it is
        // re-checked here rather than trusted. Covers a token persisted before the guard in
        // sendMagicLink existed (audit 2026-08-20, C1).
        requireSelfAssignable(token.role)

        val assocReq = if (token.assocName != null && token.assocIdentifier != null) {
            AssociationProfileRequestDto(
                name = token.assocName,
                identifier = token.assocIdentifier,
                city = token.assocCity,
                postalCode = token.assocPostalCode
            )
        } else null

        // Set only inside the claim branch below — the one moment a guest row actually becomes an
        // ASSOCIATION account. A brand-new sign-up (Path 2) or a plain login never sets it, so the
        // one-time notice this backs cannot re-fire on a returning user's later magic-link logins.
        var donorHistoryClaimed = false

        val user = userRepository.findByEmailIgnoreCase(token.email).map { existing ->
            // Path 1: existing account — mark the email as verified (login flow).
            // No role, provider or profile change for a real account.
            val claimingGuest = existing.guest

            // A non-guest account whose role differs from the token's is not a login — it is a
            // sign-up token for a role that belongs to someone else's existing account. sendMagicLink
            // now refuses to issue that token in the first place; this only catches one issued before.
            if (!claimingGuest && existing.role != token.role) {
                throw ConflictException("Email already in use")
            }

            if (claimingGuest) {
                // A guest row provisioned by the donation widget. Clicking this link is the proof
                // of address ownership that row never had, so it is claimed rather than left
                // blocking the address (audit 2026-08-20, M1). Donation history stays attached.
                logger.info("Claiming guest account {} via magic link as {}", existing.id, token.role)
                existing.guest = false
                existing.provider = AuthProvider.MAGIC_LINK
                existing.role = token.role
                donorHistoryClaimed = token.role == UserRole.ASSOCIATION
            }
            existing.emailVerified = true
            existing.updatedAt = Instant.now()
            userRepository.save(existing).also {
                // Only a claimed guest may be missing the profile its new role needs; a guest is
                // always a DONOR, so a claim to ASSOCIATION has no association profile yet.
                if (claimingGuest) createProfile(it, it.role, assocReq)
            }
        }.orElseGet {
            // Path 2: no account yet — create user and profile (sign-up flow).
            val newUser = userRepository.save(
                User(
                    email = token.email,
                    role = token.role,
                    provider = AuthProvider.MAGIC_LINK,
                    emailVerified = true
                )
            )
            createProfile(newUser, token.role, assocReq)
            newUser
        }

        return issueTokens(user, donorHistoryClaimed)
    }

    /**
     * Authenticates a user with their email address and password.
     *
     * Uses a generic "wrong credentials" error message for both "user not found" and "wrong
     * password" cases to avoid user enumeration attacks.
     *
     * @param email The user's email address.
     * @param password The plaintext password to verify against the stored BCrypt hash.
     * @return [AuthResponseDto] with a fresh access token and refresh token.
     * @throws AuthException if no account is found or the password does not match.
     * @throws PasswordNotSetException if the account exists but has no password (Google or magic-link account).
     */
    fun loginWithEmail(email: String, password: String): AuthResponseDto {
        // Case-insensitive: accounts are stored lower-cased, so an exact match would lock out a user
        // who typed their address with different capitalisation than the row holds.
        val user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow { AuthException("Identifiants incorrects") }

        // Reject unverified accounts before the password check, with a specific error code
        // so the frontend can prompt the user to verify their email rather than mislabel it
        // as a wrong password.
        if (!user.emailVerified) {
            throw EmailNotVerifiedException()
        }
        if (user.passwordHash == null) {
            throw PasswordNotSetException()
        }
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw AuthException("Identifiants incorrects")
        }
        return issueTokens(user)
    }

    /**
     * Sets or replaces the password for an authenticated user.
     *
     * Two distinct situations:
     * - **Adding a first password** (Google or magic-link account, [User.passwordHash] null):
     *   [currentPassword] is irrelevant and ignored.
     * - **Replacing an existing password**: [currentPassword] is required and verified. Holding a
     *   valid access token is not enough — a stolen token lives 30 minutes, whereas a password set
     *   with it lasts until someone notices. Requiring the current secret is what keeps a token leak
     *   from becoming lasting account control (security audit 2026-08-20, M7).
     *
     * In both cases every refresh token is revoked and the holder is notified: a password change
     * must end the sessions that existed before it, otherwise "I changed my password" evicts nobody.
     * A single fresh refresh token is then issued and returned, so the caller's own session survives
     * while every other one dies — the caller has just proven who they are.
     *
     * @param userId UUID of the authenticated user.
     * @param password The new plaintext password.
     * @param confirmPassword Must match [password] exactly.
     * @param currentPassword Existing password; required only when the account already has one.
     * @return Raw refresh token replacing the revoked ones; the caller must return it as a cookie.
     * @throws AuthException if the passwords do not match, the user is not found, or
     *   [currentPassword] is missing or wrong on an account that already has a password.
     */
    @Transactional
    fun setPassword(userId: UUID, password: String, confirmPassword: String, currentPassword: String? = null): String {
        if (password != confirmPassword) {
            throw AuthException("Les mots de passe ne correspondent pas")
        }
        val user = userRepository.findById(userId)
            .orElseThrow { AuthException("Utilisateur introuvable") }

        val existingHash = user.passwordHash
        if (existingHash != null) {
            if (currentPassword.isNullOrBlank() || !passwordEncoder.matches(currentPassword, existingHash)) {
                logger.warn("Refused password change for user {} — current password missing or wrong", userId)
                throw AuthException("Mot de passe actuel incorrect")
            }
        }

        user.passwordHash = passwordEncoder.encode(password)
        user.provider = AuthProvider.EMAIL
        user.updatedAt = Instant.now()
        userRepository.save(user)

        // Sessions opened before the change must not survive it.
        refreshTokenRepository.revokeAllByUserId(userId)

        // …including this one, so a replacement is issued for the caller. Without it the user who
        // just set their first password would be silently logged out at the next refresh.
        val rawRefreshToken = tokenHashService.generateOpaqueToken()
        refreshTokenRepository.save(
            RefreshToken(
                user = user,
                tokenHash = tokenHashService.hashToken(rawRefreshToken),
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
            )
        )

        // Notification is a detection control, never a blocker: a mail outage must not roll back a
        // password change the user asked for.
        try {
            emailService.sendPasswordChanged(user.email)
        } catch (e: Exception) {
            logger.error("Failed to send password-change notification to {}: {}", user.email, e.message)
        }
        logger.info("Password changed for user {} — previous refresh tokens revoked", userId)
        return rawRefreshToken
    }

    /**
     * Exchanges a valid refresh token for a new access token + refresh token pair (token rotation).
     *
     * The incoming refresh token is revoked immediately upon use so it cannot be replayed.
     * A new refresh token is issued alongside the new access token.
     *
     * @param rawRefreshToken The raw refresh token received from the client.
     * @return [AuthResponseDto] containing a new access token and a new refresh token.
     * @throws AuthException if no token matches the hash or the token has been revoked.
     * @throws TokenExpiredException if the token's 30-day window has passed.
     */
    @Transactional
    fun refreshAccessToken(rawRefreshToken: String): AuthResponseDto {
        val hash = tokenHashService.hashToken(rawRefreshToken)
        val token = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow { AuthException("Refresh token invalide") }

        // Explicit revocation check: a revoked token may indicate token theft if presented again.
        if (token.revoked) {
            throw AuthException("Refresh token révoqué")
        }
        if (token.expiresAt.isBefore(Instant.now())) {
            throw TokenExpiredException()
        }

        // Revoke the current token before issuing a new one (token rotation).
        token.revoked = true
        refreshTokenRepository.save(token)

        return issueTokens(token.user)
    }

    /**
     * Logs out the user by revoking all their active refresh tokens.
     *
     * After this call, no refresh token for the user can be used to obtain a new access token.
     * Existing access tokens remain valid until they expire (max 15 minutes).
     *
     * @param userId UUID of the user to log out.
     */
    @Transactional
    fun logout(userId: UUID) {
        refreshTokenRepository.revokeAllByUserId(userId)
    }

    /**
     * Creates or updates the [AssociationProfile] for the authenticated user.
     *
     * If a profile already exists, all mutable fields are updated. If no profile exists,
     * a new one is created using [dto.nom] and [dto.identifier] as the immutable identity fields,
     * and a deterministic on-chain wallet address is derived and persisted for it.
     *
     * Only users with [UserRole.ASSOCIATION] may call this method.
     *
     * @param userId UUID of the authenticated association user.
     * @param dto Upsert payload with profile fields.
     * @throws AuthException if the user is not found or does not have the ASSOCIATION role.
     */
    @Transactional
    fun upsertAssociationProfile(userId: UUID, dto: AssociationProfileUpsertDto) {
        val user = userRepository.findById(userId)
            .orElseThrow { AuthException("Utilisateur introuvable") }
        // Guard: only ASSOCIATION users may have an association profile.
        if (user.role != UserRole.ASSOCIATION) {
            throw AuthException("Réservé aux associations")
        }
        val existing = associationProfileRepository.findByUserId(userId)
        if (existing.isPresent) {
            // Update path: profile already exists — overwrite mutable fields.
            val profile = existing.get()
            profile.city = dto.ville
            profile.postalCode = dto.codePostal
            profile.contactName = dto.contact
            profile.description = dto.description
            associationProfileRepository.save(profile)
        } else {
            // Create path: first-time profile setup using the immutable identity fields from the DTO.
            guardSirenNotAlreadyRegistered(dto.identifier)
            val saved = associationProfileRepository.save(
                AssociationProfile(
                    user = user,
                    name = dto.nom,
                    identifier = dto.identifier,
                    city = dto.ville,
                    postalCode = dto.codePostal,
                    contactName = dto.contact,
                    contactEmail = user.email,
                    description = dto.description,
                    siren = derivedSiren(dto.identifier)
                )
            )
            saved.walletAddress = associationAddressGenerator.generate(saved.id!!)
            associationProfileRepository.save(saved)
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Refuses a role a caller may not grant themselves.
     *
     * Server-side counterpart of [org.commonlink.validation.SelfAssignableRole]: the DTO constraint
     * covers the request body, this covers every path that reaches persistence — including the
     * magic-link flow, where the role is written to a token row and read back on verification.
     * Both layers are required because every click is replayable (project rule 8).
     *
     * @param role Role requested by the caller.
     * @throws UnprocessableEntityException if [role] is not in [UserRole.SELF_ASSIGNABLE].
     */
    /**
     * Normalises an address for storage: trimmed and lower-cased.
     *
     * Matches what [GuestDonorService] already does, so a registration and a widget donation on the
     * same address resolve to the same row instead of two. Reads are case-insensitive throughout for
     * the same reason — a claimed guest row is stored lower-cased whatever case the claimant typed.
     */
    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun requireSelfAssignable(role: UserRole) {
        if (role !in UserRole.SELF_ASSIGNABLE) {
            // Logged as a warning: a request carrying a back-office role is never a client mistake.
            logger.warn("Refused self-assignment of privileged role {}", role)
            throw UnprocessableEntityException(
                "role must be one of ${UserRole.SELF_ASSIGNABLE.joinToString(", ")}"
            )
        }
    }

    /**
     * Generates an [EmailVerificationToken], persists its hash, and sends the verification email.
     *
     * The raw token is embedded in the URL and is never stored. Tokens expire after 24 hours.
     */
    private fun sendVerificationEmail(user: User) {
        val rawToken = tokenHashService.generateOpaqueToken()
        emailVerificationTokenRepository.save(
            EmailVerificationToken(
                user = user,
                tokenHash = tokenHashService.hashToken(rawToken),
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
            )
        )
        emailService.sendEmailVerification(
            user.email,
            "$frontendUrl/auth/verify-email?token=$rawToken"
        )
    }

    /**
     * Issues a new access token + refresh token pair for the given user.
     *
     * Persists only the SHA-256 hash of the refresh token. The raw refresh token is returned
     * to the caller once and must be treated as a secret by the client.
     * Refresh tokens are valid for 30 days.
     *
     * @param donorHistoryClaimed See [AuthResponseDto.donorHistoryClaimed]. Only ever `true` from the
     *   call site that just performed the guest-to-ASSOCIATION claim itself.
     */
    private fun issueTokens(user: User, donorHistoryClaimed: Boolean = false): AuthResponseDto {
        val rawRefreshToken = tokenHashService.generateOpaqueToken()
        refreshTokenRepository.save(
            RefreshToken(
                user = user,
                tokenHash = tokenHashService.hashToken(rawRefreshToken),
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            )
        )
        return AuthResponseDto(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = rawRefreshToken,
            user = user.toDto(),
            donorHistoryClaimed = donorHistoryClaimed
        )
    }

    /**
     * Derives the secondary [AssociationProfile.siren] value from the primary identifier.
     *
     * Associations that hold a SIREN but no RNA sign up through the manual form, which stores the
     * SIREN in `identifier`. Several downstream consumers never read `identifier` and go straight
     * to [AssociationProfile.siren] — Mollie's `registrationNumber`, the Cerfa receipt and the
     * mandate PDF — so the value is copied over. No schema change is involved.
     *
     * An RNA identifier (`W` + digits) returns `null`, which leaves the RNA sign-up flow behaving
     * exactly as before.
     *
     * @param identifier Primary legal identifier captured at sign-up (RNA or SIREN).
     * @return The identifier itself when it is a 9-digit SIREN, `null` otherwise.
     */
    private fun derivedSiren(identifier: String): String? =
        identifier.trim().takeIf { SIREN_PATTERN.matches(it) }

    /**
     * Rejects a SIREN-based association sign-up when that SIREN is already registered.
     *
     * Both columns are checked. The SIREN sign-up path writes it to `identifier` *and* `siren`, but
     * an association onboarded through the RNA flow can also have filled `siren` afterwards via the
     * profile screen — checking `identifier` alone would let the same SIREN exist twice.
     *
     * Neither column carries a unique constraint in the schema, so uniqueness is enforced here.
     * Deliberately scoped to the SIREN path: an RNA identifier returns early and keeps the
     * historical permissive behaviour, leaving the original sign-up flow untouched.
     *
     * @param identifier Primary legal identifier captured at sign-up (RNA or SIREN).
     * @throws SirenAlreadyRegisteredException if an association profile already carries this SIREN.
     */
    private fun guardSirenNotAlreadyRegistered(identifier: String) {
        val siren = derivedSiren(identifier) ?: return
        if (associationProfileRepository.existsByIdentifier(siren) ||
            associationProfileRepository.existsBySiren(siren)
        ) {
            throw SirenAlreadyRegisteredException()
        }
    }

    /**
     * Creates the role-appropriate profile record for a newly registered user.
     *
     * For [UserRole.DONOR], a blank [DonorProfile] is created.
     * For [UserRole.ASSOCIATION], an [AssociationProfile] is created only when [assocReq] is
     * provided (magic-link and email/password sign-up), with a deterministic on-chain wallet
     * address derived and persisted for it. Google sign-ups for associations create the profile
     * in a separate step via [upsertAssociationProfile].
     *
     * **Idempotent**: an already-existing profile is left untouched. A claimed guest account
     * already carries a [DonorProfile] (and the donations attached to it), so creating a second
     * one would orphan its history.
     */
    private fun createProfile(user: User, role: UserRole, assocReq: AssociationProfileRequestDto?) {
        // A null id means the row was never persisted, so no profile can exist for it yet — the
        // lookup is skipped rather than dereferenced.
        val userId = user.id
        when (role) {
            UserRole.DONOR ->
                if (userId == null || donorProfileRepository.findByUserId(userId).isEmpty) {
                    donorProfileRepository.save(DonorProfile(user = user))
                }
            UserRole.ASSOCIATION -> {
                if (assocReq != null &&
                    (userId == null || associationProfileRepository.findByUserId(userId).isEmpty)
                ) {
                    guardSirenNotAlreadyRegistered(assocReq.identifier)
                    val saved = associationProfileRepository.save(
                        AssociationProfile(
                            user = user,
                            name = assocReq.name,
                            identifier = assocReq.identifier,
                            city = assocReq.city,
                            postalCode = assocReq.postalCode,
                            contactEmail = user.email,
                            description = assocReq.description,
                            siren = derivedSiren(assocReq.identifier)
                        )
                    )
                    saved.walletAddress = associationAddressGenerator.generate(saved.id!!)
                    associationProfileRepository.save(saved)
                }
            }
            UserRole.CURATOR -> { /* curator accounts have no associated profile */ }
            UserRole.COMPLIANCE_OFFICER -> { /* compliance officer accounts have no associated profile */ }
        }
    }

    /**
     * Verifies a Google ID token using the configured [GoogleIdTokenVerifier].
     *
     * Wraps any verification exception in an [AuthException] to avoid leaking
     * internal Google API details to callers.
     *
     * @param idToken The Google ID token string from the frontend.
     * @return The verified [com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload].
     * @throws AuthException if the token is null, expired, or has an invalid signature.
     */
    private fun verifyGoogleToken(idToken: String): com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload {
        val googleToken = try {
            googleIdTokenVerifier.verify(idToken)
        } catch (e: Exception) {
            throw AuthException("Token Google invalide")
        } ?: throw AuthException("Token Google invalide")
        if (googleToken.payload.emailVerified != true) {
            throw AuthException("Email Google non vérifié")
        }
        return googleToken.payload
    }
}
