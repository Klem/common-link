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

    @Transactional
    fun resendVerification(email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { AuthException("Aucun compte trouvé pour cet email") }

        if (user.emailVerified) return

        val rateLimitWindow = Instant.now().minus(10, ChronoUnit.MINUTES)
        if (emailVerificationTokenRepository.countByUserIdAndCreatedAtAfter(user.id!!, rateLimitWindow) >= 3) {
            throw RateLimitException()
        }

        sendVerificationEmail(user)
    }

    @Transactional
    fun signUpWithGoogle(idToken: String, role: UserRole): AuthResponseDto {
        val payload = verifyGoogleToken(idToken)
        val sub = payload.subject
        val email = payload["email"] as String
        val name = payload["name"] as? String
        val picture = payload["picture"] as? String

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

    @Transactional
    fun loginWithGoogle(idToken: String): AuthResponseDto {
        val payload = verifyGoogleToken(idToken)
        val sub = payload.subject
        val email = payload["email"] as String
        val name = payload["name"] as? String
        val picture = payload["picture"] as? String

        val user = userRepository.findByGoogleSub(sub).map { existing ->
            var changed = false
            if (name != null && name != existing.displayName) { existing.displayName = name; changed = true }
            if (picture != null && picture != existing.avatarUrl) { existing.avatarUrl = picture; changed = true }
            if (changed) { existing.updatedAt = Instant.now(); userRepository.save(existing) } else existing
        }.orElseGet {
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

    @Transactional
    fun sendMagicLink(email: String, role: UserRole?) {
        val rateLimitWindow = Instant.now().minus(10, ChronoUnit.MINUTES)
        if (magicLinkTokenRepository.countByEmailAndCreatedAtAfter(email, rateLimitWindow) >= 3) {
            throw RateLimitException()
        }

        val effectiveRole: UserRole = role
            ?: userRepository.findByEmail(email).map { it.role }.orElseThrow {
                AuthException("Rôle requis pour créer un compte via Magic Link")
            }

        val rawToken = tokenHashService.generateOpaqueToken()
        val tokenHash = tokenHashService.hashToken(rawToken)

        magicLinkTokenRepository.save(
            MagicLinkToken(
                email = email,
                tokenHash = tokenHash,
                role = effectiveRole,
                expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
            )
        )

        emailService.sendMagicLink(email, "$frontendUrl/auth/verify-token?token=$rawToken&role=${effectiveRole.name.lowercase()}")
    }

    @Transactional
    fun verifyMagicLink(rawToken: String): AuthResponseDto {
        val hash = tokenHashService.hashToken(rawToken)
        val token = magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull(hash)
            .orElseThrow { InvalidTokenException() }

        if (token.expiresAt.isBefore(Instant.now())) {
            throw TokenExpiredException()
        }

        // Mark token as used first to prevent concurrent verification
        token.usedAt = Instant.now()
        magicLinkTokenRepository.save(token)

        val user = userRepository.findByEmail(token.email).map { existing ->
            // Merge: just verify the email, no role/provider change
            existing.emailVerified = true
            existing.updatedAt = Instant.now()
            userRepository.save(existing)
        }.orElseGet {
            // New user: create account + profile
            val newUser = userRepository.save(
                User(
                    email = token.email,
                    role = token.role,
                    provider = AuthProvider.MAGIC_LINK,
                    emailVerified = true
                )
            )
            createProfile(newUser, token.role, null)
            newUser
        }

        return issueTokens(user)
    }

    fun loginWithEmail(email: String, password: String): AuthResponseDto {
        val user = userRepository.findByEmail(email)
            .orElseThrow { AuthException("Identifiants incorrects") }

        if (user.passwordHash == null) {
            throw PasswordNotSetException()
        }
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw AuthException("Identifiants incorrects")
        }
        return issueTokens(user)
    }

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

    @Transactional
    fun refreshAccessToken(rawRefreshToken: String): AuthResponseDto {
        val hash = tokenHashService.hashToken(rawRefreshToken)
        val token = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow { AuthException("Refresh token invalide") }

        if (token.revoked) {
            throw AuthException("Refresh token révoqué")
        }
        if (token.expiresAt.isBefore(Instant.now())) {
            throw TokenExpiredException()
        }

        token.revoked = true
        refreshTokenRepository.save(token)

        return issueTokens(token.user)
    }

    @Transactional
    fun logout(userId: UUID) {
        refreshTokenRepository.revokeAllByUserId(userId)
    }

    @Transactional
    fun upsertAssociationProfile(userId: UUID, dto: AssociationProfileUpsertDto) {
        val user = userRepository.findById(userId)
            .orElseThrow { AuthException("Utilisateur introuvable") }
        if (user.role != UserRole.ASSOCIATION) {
            throw AuthException("Réservé aux associations")
        }
        val existing = associationProfileRepository.findByUserId(userId)
        if (existing.isPresent) {
            val profile = existing.get()
            profile.city = dto.ville
            profile.postalCode = dto.codePostal
            profile.contactName = dto.contact
            profile.description = dto.description
            associationProfileRepository.save(profile)
        } else {
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
                            contactName = assocReq.contactName,
                            description = assocReq.description
                        )
                    )
                }
            }
        }
    }

    private fun verifyGoogleToken(idToken: String): com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload {
        val googleToken = try {
            googleIdTokenVerifier.verify(idToken)
        } catch (e: Exception) {
            throw AuthException("Token Google invalide")
        } ?: throw AuthException("Token Google invalide")
        return googleToken.payload
    }
}
