package org.commonlink.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "donor_profiles")
class DonorProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(name = "anonymous", nullable = false)
    var anonymous: Boolean = false
)
