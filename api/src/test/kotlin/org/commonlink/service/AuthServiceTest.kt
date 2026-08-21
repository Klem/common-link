package org.commonlink.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.dto.AssociationProfileRequestDto
import org.commonlink.dto.AssociationProfileUpsertDto
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
    private val associationAddressGenerator: org.commonlink.onchain.AssociationAddressGenerator = mockk()
    private val magicLinkTokenRepository: MagicLinkTokenRepository = mockk()
    private val refreshTokenRepository: RefreshTokenRepository = mockk()
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository = mockk()
    private val jwtService: JwtService = mockk()
    private val tokenHashService: TokenHashService = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val emailService: EmailService = mockk()
    private val googleIdTokenVerifier: GoogleIdTokenVerifier = mockk()

    private val frontendUrl = "http://localhost:3000"

    private val authService = AuthService(
        userRepository, donorProfileRepository, associationProfileRepository, associationAddressGenerator,
        magicLinkTokenRepository, refreshTokenRepository, emailVerificationTokenRepository, jwtService, tokenHashService,
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
        every { emailVerificationTokenRepository.save(any()) } answers { firstArg() }
        every { userRepository.save(any()) } answers { firstArg() }
        justRun { emailService.sendEmailVerification(any(), any()) }
        // Default: the SIREN uniqueness guard finds no existing profile. Overridden where a
        // duplicate is the subject of the test.
        every { associationProfileRepository.existsByIdentifier(any()) } returns false
        every { associationProfileRepository.existsBySiren(any()) } returns false
        every { associationAddressGenerator.generate(any()) } returns "0x1111111111111111111111111111111111111111"
        // Default: no profile exists yet. createProfile is idempotent since a claimed guest account
        // already carries a DonorProfile (audit 2026-08-20, M1), so it looks before writing.
        every { donorProfileRepository.findByUserId(any()) } returns Optional.empty()
        every { associationProfileRepository.findByUserId(any()) } returns Optional.empty()
        // A password change revokes the sessions that preceded it (audit 2026-08-20, M7).
        justRun { refreshTokenRepository.revokeAllByUserId(any()) }
        justRun { emailService.sendPasswordChanged(any()) }
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    fun `register DONOR - happy path`() {
        every { userRepository.findByEmailIgnoreCase("donor@example.com") } returns Optional.empty()
        every { passwordEncoder.encode("password123") } returns "hashed"
        every { donorProfileRepository.save(any()) } answers { firstArg() }

        val req = RegisterRequestDto(
            email = "donor@example.com",
            password = "password123",
            role = UserRole.DONOR
        )
        authService.register(req)

        verify { donorProfileRepository.save(any()) }
        verify { emailService.sendEmailVerification("donor@example.com", any()) }
    }

    @Test
    fun `register ASSOCIATION - happy path`() {
        every { userRepository.findByEmailIgnoreCase("asso@example.com") } returns Optional.empty()
        every { passwordEncoder.encode("password123") } returns "hashed"
        every { associationProfileRepository.save(any()) } answers { firstArg<AssociationProfile>().withGeneratedId() }

        val req = RegisterRequestDto(
            email = "asso@example.com",
            password = "password123",
            role = UserRole.ASSOCIATION,
            associationProfile = AssociationProfileRequestDto(name = "MyAsso", identifier = "123456789")
        )
        authService.register(req)

        verify { associationProfileRepository.save(any()) }
    }

    @Test
    fun `register ASSOCIATION without profile - user created, profile skipped`() {
        every { userRepository.findByEmailIgnoreCase("asso@example.com") } returns Optional.empty()
        every { passwordEncoder.encode("password123") } returns "hashed"

        val req = RegisterRequestDto(
            email = "asso@example.com",
            password = "password123",
            role = UserRole.ASSOCIATION
            // no associationProfile
        )
        authService.register(req)

        verify(exactly = 0) { associationProfileRepository.save(any()) }
    }

    @Test
    fun `register - email of a verified account throws ConflictException`() {
        every { userRepository.findByEmailIgnoreCase("donor@example.com") } returns Optional.of(donorUser)

        assertThrows<ConflictException> {
            authService.register(
                RegisterRequestDto(email = "donor@example.com", password = "password123", role = UserRole.DONOR)
            )
        }
    }

    /**
     * An address held only by a guest row (created by the donation widget for an arbitrary e-mail)
     * must stay claimable, otherwise one donation permanently denies an association the use of its
     * own address (audit 2026-08-20, M1). Nothing is accessible until the link is clicked:
     * `emailVerified` stays false.
     */
    @Test
    fun `register - claims a guest account instead of refusing the address`() {
        val guest = User(
            id = UUID.randomUUID(),
            email = "guest@example.com",
            role = UserRole.DONOR,
            provider = AuthProvider.GUEST,
            guest = true,
            emailVerified = false,
        )
        every { userRepository.findByEmailIgnoreCase("guest@example.com") } returns Optional.of(guest)
        every { passwordEncoder.encode("password123") } returns "hashed"
        // The guest row already carries the donor profile the donations hang off.
        every { donorProfileRepository.findByUserId(guest.id!!) } returns Optional.of(mockk(relaxed = true))

        authService.register(
            RegisterRequestDto(email = "guest@example.com", password = "password123", role = UserRole.ASSOCIATION)
        )

        assertEquals(UserRole.ASSOCIATION, guest.role, "Role is taken over by the claimant")
        assertEquals(AuthProvider.EMAIL, guest.provider)
        assertEquals(false, guest.guest, "No longer a guest row")
        assertEquals(false, guest.emailVerified, "Claiming grants nothing until the link is clicked")
        // The pre-existing donor profile must be reused, never duplicated: the donations hang off it.
        verify(exactly = 0) { donorProfileRepository.save(any()) }
    }

    /**
     * The claim path must NOT extend to a pending real registration.
     *
     * Otherwise: the victim registers and does not click yet; an attacker re-registers the same
     * address with their own password and a role of their choosing; the victim's original
     * verification token is still valid, so clicking their own link activates — and issues tokens
     * for — a row carrying the attacker's password. Only a guest row, which has no pending token, is
     * safe to claim.
     */
    @Test
    fun `register - an unverified real registration is not claimable`() {
        val pending = User(
            id = UUID.randomUUID(),
            email = "victim@example.com",
            role = UserRole.ASSOCIATION,
            provider = AuthProvider.EMAIL,
            passwordHash = "victim-hash",
            guest = false,
            emailVerified = false,
        )
        every { userRepository.findByEmailIgnoreCase("victim@example.com") } returns Optional.of(pending)

        assertThrows<ConflictException> {
            authService.register(
                RegisterRequestDto(email = "victim@example.com", password = "attacker123", role = UserRole.DONOR)
            )
        }

        assertEquals("victim-hash", pending.passwordHash, "Victim's credentials untouched")
        assertEquals(UserRole.ASSOCIATION, pending.role, "Victim's role untouched")
    }

    @Test
    fun `register - normalises the address so a widget donation and a sign-up share one row`() {
        every { userRepository.findByEmailIgnoreCase("mixed@example.com") } returns Optional.empty()
        every { passwordEncoder.encode("password123") } returns "hashed"
        every { donorProfileRepository.save(any()) } answers { firstArg() }
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { firstArg() }

        authService.register(
            RegisterRequestDto(email = "  Mixed@Example.COM  ", password = "password123", role = UserRole.DONOR)
        )

        assertEquals("mixed@example.com", saved.captured.email)
    }

    /**
     * A back-office role must not be reachable from a public sign-up payload
     * (audit 2026-08-20, C1). The DTO constraint is the first layer; this is the service-side one.
     */
    @Test
    fun `register - a back-office role is refused`() {
        listOf(UserRole.CURATOR, UserRole.COMPLIANCE_OFFICER).forEach { role ->
            assertThrows<UnprocessableEntityException> {
                authService.register(
                    RegisterRequestDto(email = "attacker@example.com", password = "password123", role = role)
                )
            }
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `sendMagicLink - a back-office role is refused`() {
        // The guard sits after the quota check, so the counter must answer for it to be reached.
        every { magicLinkTokenRepository.countByEmailAndCreatedAtAfter(any(), any()) } returns 0
        assertThrows<UnprocessableEntityException> {
            authService.sendMagicLink("attacker@example.com", UserRole.CURATOR)
        }
        verify(exactly = 0) { magicLinkTokenRepository.save(any()) }
    }

    @Test
    fun `verifyMagicLink - a token carrying a back-office role is refused`() {
        // Defence in depth: covers a token row persisted before the guard in sendMagicLink existed.
        val token = TestFixtures.magicLinkToken(
            email = "attacker@example.com",
            tokenHash = "hashedtoken",
            role = UserRole.COMPLIANCE_OFFICER,
        )
        every { magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull("hashedtoken") } returns Optional.of(token)
        every { magicLinkTokenRepository.save(any()) } answers { firstArg() }

        assertThrows<UnprocessableEntityException> { authService.verifyMagicLink("rawtoken123") }

        verify(exactly = 0) { userRepository.save(any()) }
    }

    // -------------------------------------------------------------------------
    // signUpWithGoogle
    // -------------------------------------------------------------------------

    @Test
    fun `signUpWithGoogle - happy path creates DONOR`() {
        val payload = buildGooglePayload(sub = "google-sub-123", email = "new@google.com", name = "John", picture = "http://pic")
        every { googleIdTokenVerifier.verify("valid-token") } returns buildGoogleToken(payload)
        every { userRepository.findByGoogleSub("google-sub-123") } returns Optional.empty()
        every { userRepository.findByEmailIgnoreCase("new@google.com") } returns Optional.empty()
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
    fun `signUpWithGoogle - email of a real account throws ConflictException`() {
        val payload = buildGooglePayload(sub = "new-sub", email = "donor@example.com")
        every { googleIdTokenVerifier.verify("valid-token") } returns buildGoogleToken(payload)
        every { userRepository.findByGoogleSub("new-sub") } returns Optional.empty()
        every { userRepository.findByEmailIgnoreCase("donor@example.com") } returns Optional.of(donorUser)

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
        every { userRepository.findByEmailIgnoreCase("donor@example.com") } returns Optional.of(emailUser)

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
        every { userRepository.findByEmailIgnoreCase("nobody@example.com") } returns Optional.empty()

        assertThrows<AuthException> {
            authService.loginWithGoogle("valid-token")
        }
    }

    // -------------------------------------------------------------------------
    // email_verified guard (Prompt 1 — security sprint)
    // -------------------------------------------------------------------------

    @Test
    fun `signUpWithGoogle - unverified email throws AuthException`() {
        val payload = buildGooglePayload(sub = "google-sub-unverified", email = "unverified@google.com", emailVerified = false)
        every { googleIdTokenVerifier.verify("unverified-token") } returns buildGoogleToken(payload)

        assertThrows<AuthException> {
            authService.signUpWithGoogle("unverified-token", UserRole.DONOR)
        }

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `loginWithGoogle - unverified email throws AuthException and does not mutate User`() {
        val payload = buildGooglePayload(sub = "google-sub-unverified", email = "unverified@google.com", emailVerified = false)
        every { googleIdTokenVerifier.verify("unverified-token") } returns buildGoogleToken(payload)

        assertThrows<AuthException> {
            authService.loginWithGoogle("unverified-token")
        }

        verify(exactly = 0) { userRepository.findByGoogleSub(any()) }
        verify(exactly = 0) { userRepository.findByEmailIgnoreCase(any()) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `loginWithGoogle - null emailVerified throws AuthException`() {
        val payload = buildGooglePayload(sub = "google-sub-null", email = "nullverified@google.com", emailVerified = null)
        every { googleIdTokenVerifier.verify("null-verified-token") } returns buildGoogleToken(payload)

        assertThrows<AuthException> {
            authService.loginWithGoogle("null-verified-token")
        }

        verify(exactly = 0) { userRepository.save(any()) }
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
        every { userRepository.findByEmailIgnoreCase("donor@example.com") } returns Optional.of(donorUser)
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
    fun `sendMagicLink - null role and no existing user - silently returns without throwing`() {
        every { magicLinkTokenRepository.countByEmailAndCreatedAtAfter("nobody@example.com", any()) } returns 0
        every { userRepository.findByEmailIgnoreCase("nobody@example.com") } returns Optional.empty()

        // No exception — silent no-op prevents email enumeration
        authService.sendMagicLink("nobody@example.com", null)
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
        every { userRepository.findByEmailIgnoreCase("new@example.com") } returns Optional.empty()
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
        every { userRepository.findByEmailIgnoreCase(googleUser.email) } returns Optional.of(googleUser)
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
        every { userRepository.findByEmailIgnoreCase("donor@example.com") } returns Optional.of(donorUser)
        every { passwordEncoder.matches("password123", "hashed") } returns true

        val result = authService.loginWithEmail("donor@example.com", "password123")
        assertEquals("jwt.access.token", result.accessToken)
    }

    @Test
    fun `loginWithEmail - user not found throws AuthException`() {
        every { userRepository.findByEmailIgnoreCase("nobody@example.com") } returns Optional.empty()

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
        every { userRepository.findByEmailIgnoreCase("donor@example.com") } returns Optional.of(noPasswordUser)

        assertThrows<PasswordNotSetException> {
            authService.loginWithEmail("donor@example.com", "password123")
        }
    }

    @Test
    fun `loginWithEmail - wrong password throws AuthException`() {
        every { userRepository.findByEmailIgnoreCase("donor@example.com") } returns Optional.of(donorUser)
        every { passwordEncoder.matches("wrongpass", "hashed") } returns false

        assertThrows<AuthException> {
            authService.loginWithEmail("donor@example.com", "wrongpass")
        }
    }

    @Test
    fun `loginWithEmail - unverified email throws EmailNotVerifiedException`() {
        val unverifiedUser = User(
            id = donorUser.id, email = "donor@example.com", role = UserRole.DONOR,
            provider = AuthProvider.EMAIL, passwordHash = "hashed", emailVerified = false
        )
        every { userRepository.findByEmailIgnoreCase("donor@example.com") } returns Optional.of(unverifiedUser)

        assertThrows<EmailNotVerifiedException> {
            authService.loginWithEmail("donor@example.com", "password123")
        }
    }

    @Test
    fun `resendVerification - no account found - silently returns without throwing`() {
        every { userRepository.findByEmailIgnoreCase("nobody@example.com") } returns Optional.empty()

        // No exception — silent no-op prevents email enumeration
        authService.resendVerification("nobody@example.com")
    }

    // -------------------------------------------------------------------------
    // setPassword
    // -------------------------------------------------------------------------

    @Test
    fun `setPassword - replacing an existing password requires the current one`() {
        // donorUser already has a password hash, so proving knowledge of it is required: a stolen
        // access token must not be enough to establish lasting credentials (audit 2026-08-20, M7).
        every { userRepository.findById(donorUser.id!!) } returns Optional.of(donorUser)
        every { passwordEncoder.encode("newpass123") } returns "newhash"
        every { passwordEncoder.matches("oldpass123", "hashed") } returns true

        val newRefreshToken = authService.setPassword(donorUser.id!!, "newpass123", "newpass123", "oldpass123")

        assertEquals("newhash", donorUser.passwordHash)
        assertEquals("rawtoken123", newRefreshToken, "A replacement session token is issued")
        verify { userRepository.save(donorUser) }
        // Sessions opened before the change must not survive it.
        verify { refreshTokenRepository.revokeAllByUserId(donorUser.id!!) }
        verify { emailService.sendPasswordChanged(donorUser.email) }
    }

    @Test
    fun `setPassword - refused when the current password is missing or wrong`() {
        every { userRepository.findById(donorUser.id!!) } returns Optional.of(donorUser)
        every { passwordEncoder.matches("wrong", "hashed") } returns false

        assertThrows<AuthException> {
            authService.setPassword(donorUser.id!!, "newpass123", "newpass123", null)
        }
        assertThrows<AuthException> {
            authService.setPassword(donorUser.id!!, "newpass123", "newpass123", "wrong")
        }

        assertEquals("hashed", donorUser.passwordHash, "Password left untouched")
        verify(exactly = 0) { refreshTokenRepository.revokeAllByUserId(any()) }
    }

    @Test
    fun `setPassword - adding a first password needs no current one`() {
        val googleUser = User(
            id = UUID.randomUUID(),
            email = "google@example.com",
            role = UserRole.DONOR,
            provider = AuthProvider.GOOGLE,
            passwordHash = null,
            emailVerified = true,
        )
        every { userRepository.findById(googleUser.id!!) } returns Optional.of(googleUser)
        every { passwordEncoder.encode("newpass123") } returns "newhash"

        authService.setPassword(googleUser.id!!, "newpass123", "newpass123")

        assertEquals("newhash", googleUser.passwordHash)
        assertEquals(AuthProvider.EMAIL, googleUser.provider)
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
    // upsertAssociationProfile
    // -------------------------------------------------------------------------

    @Test
    fun `upsertAssociationProfile - creates profile when none exists`() {
        every { userRepository.findById(assocUser.id!!) } returns Optional.of(assocUser)
        every { associationProfileRepository.findByUserId(assocUser.id!!) } returns Optional.empty()
        every { associationProfileRepository.save(any()) } answers { firstArg<AssociationProfile>().withGeneratedId() }

        val dto = AssociationProfileUpsertDto(
            nom = "MyAsso",
            identifier = "123456789",
            ville = "Paris",
            codePostal = "75001",
            contact = "contact@myasso.fr",
            description = "Description"
        )
        authService.upsertAssociationProfile(assocUser.id!!, dto)

        verify { associationProfileRepository.save(any()) }
    }

    @Test
    fun `upsertAssociationProfile - updates existing profile`() {
        val existingProfile = AssociationProfile(
            user = assocUser,
            name = "MyAsso",
            identifier = "123456789",
            city = "Lyon",
            postalCode = "69001"
        )
        every { userRepository.findById(assocUser.id!!) } returns Optional.of(assocUser)
        every { associationProfileRepository.findByUserId(assocUser.id!!) } returns Optional.of(existingProfile)
        every { associationProfileRepository.save(any()) } answers { firstArg<AssociationProfile>().withGeneratedId() }

        val dto = AssociationProfileUpsertDto(
            nom = "MyAsso",
            identifier = "123456789",
            ville = "Paris",
            codePostal = "75001",
            contact = "new@myasso.fr",
            description = "Updated"
        )
        authService.upsertAssociationProfile(assocUser.id!!, dto)

        assertEquals("Paris", existingProfile.city)
        assertEquals("75001", existingProfile.postalCode)
        assertEquals("new@myasso.fr", existingProfile.contactName)
        verify { associationProfileRepository.save(existingProfile) }
    }

    @Test
    fun `upsertAssociationProfile - non-ASSOCIATION user throws AuthException`() {
        every { userRepository.findById(donorUser.id!!) } returns Optional.of(donorUser)

        assertThrows<AuthException> {
            authService.upsertAssociationProfile(
                donorUser.id!!,
                AssociationProfileUpsertDto(nom = "X", identifier = "123456789")
            )
        }
    }

    // -------------------------------------------------------------------------
    // SIREN sign-up path (association with a SIREN but no RNA)
    // -------------------------------------------------------------------------

    @Test
    fun `register ASSOCIATION with a SIREN identifier - copies it into siren`() {
        every { userRepository.findByEmailIgnoreCase("asso@example.com") } returns Optional.empty()
        every { passwordEncoder.encode("password123") } returns "hashed"
        val saved = slot<AssociationProfile>()
        every { associationProfileRepository.save(capture(saved)) } answers { firstArg<AssociationProfile>().withGeneratedId() }

        authService.register(
            RegisterRequestDto(
                email = "asso@example.com",
                password = "password123",
                role = UserRole.ASSOCIATION,
                associationProfile = AssociationProfileRequestDto(name = "MyAsso", identifier = "123456789")
            )
        )

        assertEquals("123456789", saved.captured.identifier)
        assertEquals("123456789", saved.captured.siren)
    }

    @Test
    fun `register ASSOCIATION with an RNA identifier - leaves siren null`() {
        every { userRepository.findByEmailIgnoreCase("asso@example.com") } returns Optional.empty()
        every { passwordEncoder.encode("password123") } returns "hashed"
        val saved = slot<AssociationProfile>()
        every { associationProfileRepository.save(capture(saved)) } answers { firstArg<AssociationProfile>().withGeneratedId() }

        authService.register(
            RegisterRequestDto(
                email = "asso@example.com",
                password = "password123",
                role = UserRole.ASSOCIATION,
                associationProfile = AssociationProfileRequestDto(name = "MyAsso", identifier = "W123456789")
            )
        )

        assertEquals("W123456789", saved.captured.identifier)
        assertNull(saved.captured.siren)
    }

    @Test
    fun `register ASSOCIATION with an already registered SIREN is rejected`() {
        every { userRepository.findByEmailIgnoreCase("asso@example.com") } returns Optional.empty()
        every { passwordEncoder.encode("password123") } returns "hashed"
        every { associationProfileRepository.existsByIdentifier("123456789") } returns true

        assertThrows<SirenAlreadyRegisteredException> {
            authService.register(
                RegisterRequestDto(
                    email = "asso@example.com",
                    password = "password123",
                    role = UserRole.ASSOCIATION,
                    associationProfile = AssociationProfileRequestDto(name = "MyAsso", identifier = "123456789")
                )
            )
        }
    }

    @Test
    fun `register ASSOCIATION with an already registered RNA still succeeds - RNA flow untouched`() {
        every { userRepository.findByEmailIgnoreCase("asso@example.com") } returns Optional.empty()
        every { passwordEncoder.encode("password123") } returns "hashed"
        every { associationProfileRepository.save(any()) } answers { firstArg<AssociationProfile>().withGeneratedId() }
        every { associationProfileRepository.existsByIdentifier("W123456789") } returns true

        authService.register(
            RegisterRequestDto(
                email = "asso@example.com",
                password = "password123",
                role = UserRole.ASSOCIATION,
                associationProfile = AssociationProfileRequestDto(name = "MyAsso", identifier = "W123456789")
            )
        )

        verify { associationProfileRepository.save(any()) }
    }

    @Test
    fun `verifyMagicLink - association signed up with a SIREN - copies it into siren`() {
        val token = MagicLinkToken(
            email = "asso-new@example.com",
            tokenHash = "hashedtoken",
            role = UserRole.ASSOCIATION,
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES),
            assocName = "MyAsso",
            assocIdentifier = "123456789",
            assocCity = "Paris",
            assocPostalCode = "75001"
        )
        every { magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull("hashedtoken") } returns Optional.of(token)
        every { magicLinkTokenRepository.save(any()) } answers { firstArg() }
        every { userRepository.findByEmailIgnoreCase("asso-new@example.com") } returns Optional.empty()
        val saved = slot<AssociationProfile>()
        every { associationProfileRepository.save(capture(saved)) } answers { firstArg<AssociationProfile>().withGeneratedId() }

        authService.verifyMagicLink("rawtoken123")

        assertEquals("123456789", saved.captured.identifier)
        assertEquals("123456789", saved.captured.siren)
    }

    @Test
    fun `sendMagicLink - already registered SIREN is rejected before sending`() {
        every { magicLinkTokenRepository.countByEmailAndCreatedAtAfter(any(), any()) } returns 0
        every { associationProfileRepository.existsByIdentifier("123456789") } returns true

        assertThrows<SirenAlreadyRegisteredException> {
            authService.sendMagicLink(
                "asso-new@example.com",
                UserRole.ASSOCIATION,
                AssociationProfileRequestDto(name = "MyAsso", identifier = "123456789")
            )
        }

        verify(exactly = 0) { emailService.sendMagicLink(any(), any()) }
    }

    @Test
    fun `sendMagicLink - already registered RNA still sends - RNA flow untouched`() {
        every { magicLinkTokenRepository.countByEmailAndCreatedAtAfter(any(), any()) } returns 0
        every { magicLinkTokenRepository.save(any()) } answers { firstArg() }
        justRun { emailService.sendMagicLink(any(), any()) }
        every { associationProfileRepository.existsByIdentifier("W123456789") } returns true

        authService.sendMagicLink(
            "asso-new@example.com",
            UserRole.ASSOCIATION,
            AssociationProfileRequestDto(name = "MyAsso", identifier = "W123456789")
        )

        verify { emailService.sendMagicLink("asso-new@example.com", any()) }
    }

    @Test
    fun `register ASSOCIATION is rejected when the SIREN is held in the secondary siren column`() {
        // An association onboarded through the RNA flow may have filled `siren` afterwards via the
        // profile screen. Checking `identifier` alone would let the same SIREN exist twice.
        every { userRepository.findByEmailIgnoreCase("asso@example.com") } returns Optional.empty()
        every { passwordEncoder.encode("password123") } returns "hashed"
        every { associationProfileRepository.existsByIdentifier("123456789") } returns false
        every { associationProfileRepository.existsBySiren("123456789") } returns true

        assertThrows<SirenAlreadyRegisteredException> {
            authService.register(
                RegisterRequestDto(
                    email = "asso@example.com",
                    password = "password123",
                    role = UserRole.ASSOCIATION,
                    associationProfile = AssociationProfileRequestDto(name = "MyAsso", identifier = "123456789")
                )
            )
        }
    }

    @Test
    fun `upsertAssociationProfile - SIREN identifier is copied into siren on create`() {
        every { userRepository.findById(assocUser.id!!) } returns Optional.of(assocUser)
        every { associationProfileRepository.findByUserId(assocUser.id!!) } returns Optional.empty()
        val saved = slot<AssociationProfile>()
        every { associationProfileRepository.save(capture(saved)) } answers { firstArg<AssociationProfile>().withGeneratedId() }

        authService.upsertAssociationProfile(
            assocUser.id!!,
            AssociationProfileUpsertDto(nom = "MyAsso", identifier = "123456789", ville = "Paris", codePostal = "75001")
        )

        assertEquals("123456789", saved.captured.siren)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Mimics Hibernate assigning the @GeneratedValue id on persist, for save() mocks that need a non-null id back. */
    private fun AssociationProfile.withGeneratedId(): AssociationProfile = also {
        if (it.id == null) {
            it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, UUID.randomUUID())
        }
    }

    private fun buildGooglePayload(
        sub: String,
        email: String,
        name: String? = null,
        picture: String? = null,
        emailVerified: Boolean? = true
    ): GoogleIdToken.Payload {
        val payload = GoogleIdToken.Payload()
        payload.subject = sub
        payload["email"] = email
        if (name != null) payload["name"] = name
        if (picture != null) payload["picture"] = picture
        payload.emailVerified = emailVerified
        return payload
    }

    private fun buildGoogleToken(payload: GoogleIdToken.Payload): GoogleIdToken {
        val token: GoogleIdToken = mockk()
        every { token.payload } returns payload
        return token
    }
}
