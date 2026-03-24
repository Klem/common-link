package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "magic_link_tokens")
class MagicLinkToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @Column(name = "email", nullable = false)
    val email: String,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    val role: UserRole,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "assoc_name")
    val assocName: String? = null,

    @Column(name = "assoc_identifier", length = 36)
    val assocIdentifier: String? = null,

    @Column(name = "assoc_city")
    val assocCity: String? = null,

    @Column(name = "assoc_postal_code", length = 16)
    val assocPostalCode: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
