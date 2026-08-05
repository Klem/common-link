package org.commonlink.repository

import org.commonlink.entity.AssociationLogo
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Persistence for association landing logo binaries, keyed by association id. */
interface AssociationLogoRepository : JpaRepository<AssociationLogo, UUID>
