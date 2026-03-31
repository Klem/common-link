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
import org.commonlink.exception.InvalidTokenException
import org.commonlink.exception.PasswordNotSetException
import org.commonlink.exception.RateLimitException
import org.commonlink.exception.TokenExpiredException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.EmailVerificationTokenRepository
import org.commonlink.repository.MagicLinkTokenRepository
import org.commonlink.repository.RefreshTokenRepository
import org.commonlink.repository.UserRepository
import org.commonlink.security.JwtService
import org.commonlink.security.TokenHashService
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

    /**
     * Registers a new user with email and password.
     *
     * Creates the [User] record, the appropriate role profile ([DonorProfile] or
     * [AssociationProfile]), and sends an email verification link. The account cannot be
     * used until the verification link is clicked.
     *
     * @param req Registration data including email, password, role, and optional association profile.
     * @throws ConflictException if the email address is already registered.
     */
    @Transactional
    fun register(req: RegisterRequestDto) {
        if (userRepository.existsByEmail(req.email)) {
            throw ConflictException("Email already in use")
        }
        val user = userRepository.save(
            User(
                email = req.email,
                role = req.role,
                provider = AuthProvider.EMAIL,
                emailVerified = false,
                passwordHash = passwordEncoder.encode(req.password)
            )
        )
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

        return issueTokens(user)
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
        val user = userRepository.findByEmail(email)
            .orElseThrow { AuthException("Aucun compte trouvé pour cet email") }

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
        val payload = verifyGoogleToken(idToken)
        val sub = payload.subject
        val email = payload["email"] as String
        val name = payload["name"] as? String
        val picture = payload["picture"] as? String

        // Prevent duplicate accounts: reject if this Google account or email is already registered.
        if (userRepository.findByGoogleSub(sub).isPresent) {
            throw ConflictException("Account already exists")
        }
        if (userRepository.existsByEmail(email)) {
            throw ConflictException("Email arelady in use")
        }
        val user = userRepository.save(
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
        createProfile(user, role, null)
        return issueTokens(user)
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
            // Merge: account exists by email
            userRepository.findByEmail(email).map { existing ->
                existing.googleSub = sub
                existing.emailVerified = true
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
     */
    @Transactional
    fun sendMagicLink(email: String, role: UserRole?, associationProfile: AssociationProfileRequestDto? = null) {
        // Rate limit: max 3 magic links per email per 10-minute sliding window.
        val rateLimitWindow = Instant.now().minus(10, ChronoUnit.MINUTES)
        if (magicLinkTokenRepository.countByEmailAndCreatedAtAfter(email, rateLimitWindow) >= 3) {
            throw RateLimitException()
        }

        // If role is not supplied, the caller expects an existing account (login flow).
        // Look up the existing user's role; fail if no account is found.
        val effectiveRole: UserRole = role
            ?: userRepository.findByEmail(email).map { it.role }.orElseThrow {
                AuthException("Rôle requis pour créer un compte via Magic Link")
            }

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
     * @throws InvalidTokenException if no unused token matches the hash.
     * @throws TokenExpiredException if the token's 15-minute window has passed.
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

        val user = userRepository.findByEmail(token.email).map { existing ->
            // Path 1: existing account — just mark the email as verified (login flow).
            // Merge: just verify the email, no role/provider change
            existing.emailVerified = true
            existing.updatedAt = Instant.now()
            userRepository.save(existing)
        }.orElseGet {
            // Path 2: no account yet — create user and profile (sign-up flow).
            // New user: create account + profile
            val assocReq = if (token.assocName != null && token.assocIdentifier != null) {
                AssociationProfileRequestDto(
                    name = token.assocName,
                    identifier = token.assocIdentifier,
                    city = token.assocCity,
                    postalCode = token.assocPostalCode
                )
            } else null
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

        return issueTokens(user)
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
        val user = userRepository.findByEmail(email)
            .orElseThrow { AuthException("Identifiants incorrects") }

        // Accounts created via Google or magic link have no password hash.
        // Return a specific error code so the frontend can guide the user.
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
     * Used after sign-up via magic-link or Google when the user wants to add a password
     * to their account. Validates that both inputs match before encoding and persisting.
     *
     * @param userId UUID of the authenticated user.
     * @param password The new plaintext password.
     * @param confirmPassword Must match [password] exactly.
     * @throws AuthException if the passwords do not match or the user is not found.
     */
    @Transactional
    fun setPassword(userId: UUID, password: String, confirmPassword: String) {
        if (password != confirmPassword) {
            throw AuthException("Les mots de passe ne correspondent pas")
        }
        val user = userRepository.findById(userId)
            .orElseThrow { AuthException("Utilisateur introuvable") }
        user.passwordHash = passwordEncoder.encode(password)
        user.updatedAt = Instant.now()
        userRepository.save(user)
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
     * a new one is created using [dto.nom] and [dto.siren] as the immutable identity fields.
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
            associationProfileRepository.save(
                AssociationProfile(
                    user = user,
                    name = dto.nom,
                    identifier = dto.siren,
                    city = dto.ville,
                    postalCode = dto.codePostal,
                    contactName = dto.contact,
                    description = dto.description
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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
     */
    private fun issueTokens(user: User): AuthResponseDto {
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
            user = user.toDto()
        )
    }

    /**
     * Creates the role-appropriate profile record for a newly registered user.
     *
     * For [UserRole.DONOR], a blank [DonorProfile] is always created.
     * For [UserRole.ASSOCIATION], an [AssociationProfile] is created only when [assocReq] is
     * provided (magic-link and email/password sign-up). Google sign-ups for associations
     * create the profile in a separate step via [upsertAssociationProfile].
     */
    private fun createProfile(user: User, role: UserRole, assocReq: AssociationProfileRequestDto?) {
        when (role) {
            UserRole.DONOR -> donorProfileRepository.save(DonorProfile(user = user))
            UserRole.ASSOCIATION -> {
                if (assocReq != null) {
                    associationProfileRepository.save(
                        AssociationProfile(
                            user = user,
                            name = assocReq.name,
                            identifier = assocReq.identifier,
                            city = assocReq.city,
                            postalCode = assocReq.postalCode,
                            contactName = user.email,
                            description = assocReq.description
                        )
                    )
                }
            }
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
        return googleToken.payload
    }
}
