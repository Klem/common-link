package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * One HelloAsso key check by an association ([org.commonlink.service.HelloAssoCredentialsService.verify]), successful
 * or not: what HelloAsso returned and why the link was refused, for the curators' statistics.
 *
 * **Append-only**: never updated nor deleted by the application (rows go with their association, ON DELETE CASCADE).
 * Never holds a secret nor a token; the `client_id` is masked.
 */
@Entity
@Table(name = "helloasso_link_attempts")
class HelloAssoLinkAttempt(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @Column(name = "association_id", nullable = false, updatable = false)
    val associationId: UUID,

    @Column(name = "attempted_at", nullable = false, updatable = false)
    val attemptedAt: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 32)
    val outcome: HelloAssoLinkAttemptOutcome,

    /** `abcd…wxyz` ([org.commonlink.service.HelloAssoLinkService.maskClientId]). */
    @Column(name = "client_id_masked", updatable = false, length = 32)
    val clientIdMasked: String? = null,

    /** Organizations the keys reach (`/users/me/organizations`); null when the check stopped before. */
    @Column(name = "organizations_count", updatable = false)
    val organizationsCount: Int? = null,

    @Column(name = "organization_slug", updatable = false, length = 255)
    val organizationSlug: String? = null,

    @Column(name = "organization_name", updatable = false, length = 255)
    val organizationName: String? = null,

    /** HelloAsso `OrganizationType`, as returned. */
    @Column(name = "organization_type", updatable = false, length = 64)
    val organizationType: String? = null,

    /** HelloAsso `rnaNumber`; null when blank. */
    @Column(name = "organization_rna", updatable = false, length = 20)
    val organizationRna: String? = null,

    /** The association's RNA as known by CommonLink at that moment; null when unknown. */
    @Column(name = "association_rna", updatable = false, length = 20)
    val associationRna: String? = null,
)
