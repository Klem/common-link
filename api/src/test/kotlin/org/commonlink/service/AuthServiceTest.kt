package org.commonlink.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.dto.AssociationProfileRequestDto
import org.commonlink.dto.RegisterRequestDto
import org.commonlink.entity.*
import org.commonlink.exception.*
import org.commonlink.repository.*
import org.commonlink.security.JwtService
import org.commonlink.security.TokenHashService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class AuthServiceTest {

    private val userRepository: UserRepository = mockk()
    private val donorProfileRepository: DonorProfileRepository = mockk()
    private val associationProfileRepository: AssociationProfileRepository = mockk()
    private val magicLinkTokenRepository: MagicLinkTokenRepository = mockk()
    private val refreshTokenRepository: RefreshTokenRepository = mockk()
    private val jwtService: JwtService = mockk()
    private val tokenHashService: TokenHashService = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val emailService: EmailService = mockk()
    private val googleIdTokenVerifier: GoogleIdTokenVerifier = mockk()

    private val frontendUrl = "http://localhost:3000"

    private val authService = AuthService(
        userRepository, donorProfileRepository, associationProfileRepository,
        magicLinkTokenRepository, refreshTokenRepository, jwtService, tokenHashService,
        passwordEncoder, emailService, googleIdTokenVerifier, frontendUrl
    )

    private val donorUser = User(
        id = UUID.randomUUID(),
        email = "donor@example.com",
        role = UserRole.DONOR,
        provider = AuthProvider.EMAIL,
        passwordHash = "hashed",
        emailVerified = true
    )

    private val assocUser = User(
        id = UUID.randomUUID(),
        email = "asso@example.com",
        role = UserRole.ASSOCIATION,
        provider = AuthProvider.EMAIL,
        passwordHash = "hashed",
        emailVerified = true
    )

    @BeforeEach
    fun setupCommonMocks() {
        every { tokenHashService.generateOpaqueToken() } returns "rawtoken123"
        every { tokenHashService.hashToken(any()) } returns "hashedtoken"
        every { jwtService.generateAccessToken(any()) } returns "jwt.access.token"
        every { refreshTokenRepository.save(any()) } answers { firstArg() }
        every { userRepository.save(any()) } answers { firstArg() }
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    fun `register DONOR - happy path`() {
        every { userRepository.existsByEmail("donor@example.com") } returns false
        every { passwordEncoder.encode("password123") } returns "hashed"
        every { donorProfileRepository.save(any()) } answers { firstArg() }

        val req = RegisterRequestDto(
            email = "donor@example.com",
            password = "password123",
            role = UserRole.DONOR
        )
        val result = authService.register(req)

        assertEquals("jwt.access.token", result.accessToken)
        assertEquals("rawtoken123", result.refreshToken)
        verify { donorProfileRepository.save(any()) }
    }

    @Test
    fun `register ASSOCIATION - happy path`() {
        every { userRepository.existsByEmail("asso@example.com") } returns false
        every { passwordEncoder.encode("password123") } returns "hashed"
        every { associationProfileRepository.save(any()) } answers { firstArg() }

        val req = RegisterRequestDto(
            email = "asso@example.com",
            password = "password123",
            role = UserRole.ASSOCIATION,
            associationProfile = AssociationProfileRequestDto(name = "MyAsso", identifier = "123456789")
        )
        val result = authService.register(req)

        assertEquals("jwt.access.token", result.accessToken)
        verify { associationProfileRepository.save(any()) }
    }

    @Test
    fun `register - email already used throws ConflictException`() {
        every { userRepository.existsByEmail("donor@example.com") } returns true

        assertThrows<ConflictException> {
            authService.register(
                RegisterRequestDto(email = "donor@example.com", password = "password123", role = UserRole.DONOR)
            )
        }
    }

    // -------------------------------------------------------------------------
    // signUpWithGoogle
    // -------------------------------------------------------------------------

    @Test
    fun `signUpWithGoogle - happy path creates DONOR`() {
        val payload = buildGooglePayload(sub = "google-sub-123", email = "new@google.com", name = "John", picture = "http://pic")
        every { googleIdTokenVerifier.verify("valid-token") } returns buildGoogleToken(payload)
        every { userRepository.findByGoogleSub("google-sub-123") } returns Optional.empty()
        every { userRepository.existsByEmail("new@google.com") } returns false
        every { donorProfileRepository.save(any()) } answers { firstArg() }

        val result = authService.signUpWithGoogle("valid-token", UserRole.DONOR)

        assertEquals("jwt.access.token", result.accessToken)
        verify { donorProfileRepository.save(any()) }
    }

    @Test
    fun `signUpWithGoogle - google sub already exists throws ConflictException`() {
        val payload = buildGooglePayload(sub = "google-sub-123", email = "existing@google.com")
        every { googleIdTokenVerifier.verify("valid-token") } returns buildGoogleToken(payload)
        every { userRepository.findByGoogleSub("google-sub-123") } returns Optional.of(donorUser)

        assertThrows<ConflictException> {
            authService.signUpWithGoogle("valid-token", UserRole.DONOR)
        }
    }

    @Test
    fun `signUpWithGoogle - email already used throws ConflictException`() {
        val payload = buildGooglePayload(sub = "new-sub", email = "donor@example.com")
        every { googleIdTokenVerifier.verify("valid-token") } returns buildGoogleToken(payload)
        every { userRepository.findByGoogleSub("new-sub") } returns Optional.empty()
        every { userRepository.existsByEmail("donor@example.com") } returns true

        assertThrows<ConflictException> {
            authService.signUpWithGoogle("valid-token", UserRole.DONOR)
        }
    }

    @Test
    fun `signUpWithGoogle - invalid token throws AuthException`() {
        every { googleIdTokenVerifier.verify("bad-token") } returns null

        assertThrows<AuthException> {
            authService.signUpWithGoogle("bad-token", UserRole.DONOR)
        }
    }

    // -------------------------------------------------------------------------
    // loginWithGoogle
    // -------------------------------------------------------------------------

    @Test
    fun `loginWithGoogle - found by googleSub - happy path`() {
        val googleUser = User(
            id = donorUser.id,
            email = donorUser.email,
            role = UserRole.DONOR,
            provider = AuthProvider.GOOGLE,
            googleSub = "google-sub-123",
            emailVerified = true
        )
        val payload = buildGooglePayload(sub = "google-sub-123", email = "donor@example.com", name = "John", picture = "http://pic")
        every { googleIdTokenVerifier.verify("valid-token") } returns buildGoogleToken(payload)
        every { userRepository.findByGoogleSub("google-sub-123") } returns Optional.of(googleUser)

        val result = authService.loginWithGoogle("valid-token")
        assertEquals("jwt.access.token", result.accessToken)
    }

    @Test
    fun `loginWithGoogle - MERGE - email account exists, links googleSub`() {
        val emailUser = User(
            id = donorUser.id,
            email = donorUser.email,
            role = UserRole.DONOR,
            provider = AuthProvider.EMAIL,
            passwordHash = "hashed",
            emailVerified = true
        ) // no googleSub yet
        val payload = buildGooglePayload(sub = "new-google-sub", email = "donor@example.com")
        every { googleIdTokenVerifier.verify("valid-token") } returns buildGoogleToken(payload)
        every { userRepository.findByGoogleSub("new-google-sub") } returns Optional.empty()
        every { userRepository.findByEmail("donor@example.com") } returns Optional.of(emailUser)

        val result = authService.loginWithGoogle("valid-token")

        assertEquals("jwt.access.token", result.accessToken)
        assertEquals("new-google-sub", emailUser.googleSub)
        assertTrue(emailUser.emailVerified)
        verify { userRepository.save(emailUser) }
    }

    @Test
    fun `loginWithGoogle - no account found throws AuthException`() {
        val payload = buildGooglePayload(sub = "unknown-sub", email = "nobody@example.com")
        every { googleIdTokenVerifier.verify("valid-token") } returns buildGoogleToken(payload)
        every { userRepository.findByGoogleSub("unknown-sub") } returns Optional.empty()
        every { userRepository.findByEmail("nobody@example.com") } returns Optional.empty()

        assertThrows<AuthException> {
            authService.loginWithGoogle("valid-token")
        }
    }

    // -------------------------------------------------------------------------
    // sendMagicLink
    // -------------------------------------------------------------------------

    @Test
    fun `sendMagicLink - happy path for new user with role`() {
        every { magicLinkTokenRepository.countByEmailAndCreatedAtAfter("new@example.com", any()) } returns 0
        every { magicLinkTokenRepository.save(any()) } answers { firstArg() }
        justRun { emailService.sendMagicLink(any(), any()) }

        authService.sendMagicLink("new@example.com", UserRole.DONOR)

        verify { emailService.sendMagicLink("new@example.com", "$frontendUrl/auth/verify-token?token=rawtoken123&role=donor") }
    }

    @Test
    fun `sendMagicLink - happy path for existing user without role uses user role`() {
        every { magicLinkTokenRepository.countByEmailAndCreatedAtAfter("donor@example.com", any()) } returns 1
        every { userRepository.findByEmail("donor@example.com") } returns Optional.of(donorUser)
        every { magicLinkTokenRepository.save(any()) } answers { firstArg() }
        justRun { emailService.sendMagicLink(any(), any()) }

        authService.sendMagicLink("donor@example.com", null)

        verify { emailService.sendMagicLink("donor@example.com", any()) }
    }

    @Test
    fun `sendMagicLink - rate limit exceeded throws RateLimitException`() {
        every { magicLinkTokenRepository.countByEmailAndCreatedAtAfter("donor@example.com", any()) } returns 3

        assertThrows<RateLimitException> {
            authService.sendMagicLink("donor@example.com", UserRole.DONOR)
        }
    }

    @Test
    fun `sendMagicLink - null role and no existing user throws AuthException`() {
        every { magicLinkTokenRepository.countByEmailAndCreatedAtAfter("nobody@example.com", any()) } returns 0
        every { userRepository.findByEmail("nobody@example.com") } returns Optional.empty()

        assertThrows<AuthException> {
            authService.sendMagicLink("nobody@example.com", null)
        }
    }

    // -------------------------------------------------------------------------
    // verifyMagicLink
    // -------------------------------------------------------------------------

    @Test
    fun `verifyMagicLink - new user - creates account and profile`() {
        val token = MagicLinkToken(
            email = "new@example.com",
            tokenHash = "hashedtoken",
            role = UserRole.DONOR,
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
        )
        every { magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull("hashedtoken") } returns Optional.of(token)
        every { userRepository.findByEmail("new@example.com") } returns Optional.empty()
        every { donorProfileRepository.save(any()) } answers { firstArg() }
        every { magicLinkTokenRepository.save(any()) } answers { firstArg() }

        val result = authService.verifyMagicLink("rawtoken123")

        assertEquals("jwt.access.token", result.accessToken)
        assertNotNull(token.usedAt)
        verify { donorProfileRepository.save(any()) }
    }

    @Test
    fun `verifyMagicLink - MERGE - existing user (Google), just sets emailVerified`() {
        val googleUser = User(
            id = donorUser.id,
            email = donorUser.email,
            role = UserRole.DONOR,
            provider = AuthProvider.GOOGLE,
            emailVerified = false
        )
        val token = MagicLinkToken(
            email = googleUser.email,
            tokenHash = "hashedtoken",
            role = UserRole.DONOR,
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
        )
        every { magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull("hashedtoken") } returns Optional.of(token)
        every { userRepository.findByEmail(googleUser.email) } returns Optional.of(googleUser)
        every { magicLinkTokenRepository.save(any()) } answers { firstArg() }

        val result = authService.verifyMagicLink("rawtoken123")

        assertEquals("jwt.access.token", result.accessToken)
        assertTrue(googleUser.emailVerified)
        assertEquals(AuthProvider.GOOGLE, googleUser.provider) // provider unchanged
        verify(exactly = 0) { donorProfileRepository.save(any()) }
    }

    @Test
    fun `verifyMagicLink - token not found throws InvalidTokenException`() {
        every { magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull("hashedtoken") } returns Optional.empty()

        assertThrows<InvalidTokenException> {
            authService.verifyMagicLink("rawtoken123")
        }
    }

    @Test
    fun `verifyMagicLink - expired token throws TokenExpiredException`() {
        val expiredToken = MagicLinkToken(
            email = "old@example.com",
            tokenHash = "hashedtoken",
            role = UserRole.DONOR,
            expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES)
        )
        every { magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull("hashedtoken") } returns Optional.of(expiredToken)

        assertThrows<TokenExpiredException> {
            authService.verifyMagicLink("rawtoken123")
        }
    }

    // -------------------------------------------------------------------------
    // loginWithEmail
    // -------------------------------------------------------------------------

    @Test
    fun `loginWithEmail - happy path`() {
        every { userRepository.findByEmail("donor@example.com") } returns Optional.of(donorUser)
        every { passwordEncoder.matches("password123", "hashed") } returns true

        val result = authService.loginWithEmail("donor@example.com", "password123")
        assertEquals("jwt.access.token", result.accessToken)
    }

    @Test
    fun `loginWithEmail - user not found throws AuthException`() {
        every { userRepository.findByEmail("nobody@example.com") } returns Optional.empty()

        assertThrows<AuthException> {
            authService.loginWithEmail("nobody@example.com", "password123")
        }
    }

    @Test
    fun `loginWithEmail - no password set throws PasswordNotSetException`() {
        val noPasswordUser = User(
            id = donorUser.id,
            email = donorUser.email,
            role = UserRole.DONOR,
            provider = AuthProvider.EMAIL,
            passwordHash = null,
            emailVerified = true
        )
        every { userRepository.findByEmail("donor@example.com") } returns Optional.of(noPasswordUser)

        assertThrows<PasswordNotSetException> {
            authService.loginWithEmail("donor@example.com", "password123")
        }
    }

    @Test
    fun `loginWithEmail - wrong password throws AuthException`() {
        every { userRepository.findByEmail("donor@example.com") } returns Optional.of(donorUser)
        every { passwordEncoder.matches("wrongpass", "hashed") } returns false

        assertThrows<AuthException> {
            authService.loginWithEmail("donor@example.com", "wrongpass")
        }
    }

    // -------------------------------------------------------------------------
    // setPassword
    // -------------------------------------------------------------------------

    @Test
    fun `setPassword - happy path`() {
        every { userRepository.findById(donorUser.id!!) } returns Optional.of(donorUser)
        every { passwordEncoder.encode("newpass123") } returns "newhash"

        authService.setPassword(donorUser.id!!, "newpass123", "newpass123")

        assertEquals("newhash", donorUser.passwordHash)
        verify { userRepository.save(donorUser) }
    }

    @Test
    fun `setPassword - passwords mismatch throws AuthException`() {
        assertThrows<AuthException> {
            authService.setPassword(donorUser.id!!, "newpass123", "different")
        }
    }

    // -------------------------------------------------------------------------
    // refreshAccessToken
    // -------------------------------------------------------------------------

    @Test
    fun `refreshAccessToken - happy path rotates token`() {
        val refreshToken = RefreshToken(
            user = donorUser,
            tokenHash = "hashedtoken",
            expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
        )
        every { refreshTokenRepository.findByTokenHash("hashedtoken") } returns Optional.of(refreshToken)

        val result = authService.refreshAccessToken("rawtoken123")

        assertEquals("jwt.access.token", result.accessToken)
        assertEquals("rawtoken123", result.refreshToken) // nouveau refresh token émis
        assertTrue(refreshToken.revoked)                 // ancien révoqué
        verify(exactly = 2) { refreshTokenRepository.save(any()) } // révocation + nouveau
    }

    @Test
    fun `refreshAccessToken - token not found throws AuthException`() {
        every { refreshTokenRepository.findByTokenHash("hashedtoken") } returns Optional.empty()

        assertThrows<AuthException> {
            authService.refreshAccessToken("rawtoken123")
        }
    }

    @Test
    fun `refreshAccessToken - revoked token throws AuthException`() {
        val revokedToken = RefreshToken(
            user = donorUser,
            tokenHash = "hashedtoken",
            expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
            revoked = true
        )
        every { refreshTokenRepository.findByTokenHash("hashedtoken") } returns Optional.of(revokedToken)

        assertThrows<AuthException> {
            authService.refreshAccessToken("rawtoken123")
        }
    }

    @Test
    fun `refreshAccessToken - expired token throws TokenExpiredException`() {
        val expiredToken = RefreshToken(
            user = donorUser,
            tokenHash = "hashedtoken",
            expiresAt = Instant.now().minus(1, ChronoUnit.DAYS)
        )
        every { refreshTokenRepository.findByTokenHash("hashedtoken") } returns Optional.of(expiredToken)

        assertThrows<TokenExpiredException> {
            authService.refreshAccessToken("rawtoken123")
        }
    }

    // -------------------------------------------------------------------------
    // logout
    // -------------------------------------------------------------------------

    @Test
    fun `logout - revokes all refresh tokens`() {
        justRun { refreshTokenRepository.revokeAllByUserId(donorUser.id!!) }

        authService.logout(donorUser.id!!)

        verify { refreshTokenRepository.revokeAllByUserId(donorUser.id!!) }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildGooglePayload(
        sub: String,
        email: String,
        name: String? = null,
        picture: String? = null
    ): GoogleIdToken.Payload {
        val payload = GoogleIdToken.Payload()
        payload.subject = sub
        payload["email"] = email
        if (name != null) payload["name"] = name
        if (picture != null) payload["picture"] = picture
        return payload
    }

    private fun buildGoogleToken(payload: GoogleIdToken.Payload): GoogleIdToken {
        val token: GoogleIdToken = mockk()
        every { token.payload } returns payload
        return token
    }
}
