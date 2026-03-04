package org.commonlink.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "association_profiles")
class AssociationProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "identifier", nullable = false, length = 9)
    val identifier: String,

    @Column(name = "city")
    var city: String? = null,

    @Column(name = "postal_code")
    var postalCode: String? = null,

    @Column(name = "contact_name")
    var contactName: String? = null,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "verified", nullable = false)
    var verified: Boolean = false
)
