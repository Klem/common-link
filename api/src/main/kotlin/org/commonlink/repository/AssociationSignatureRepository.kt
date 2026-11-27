package org.commonlink.repository

import org.commonlink.entity.AssociationSignature
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Persistence for the receipt signature of an association, keyed by association profile id
 * (primary key lookups only — `findById` / `existsById` use the PK index).
 */
interface AssociationSignatureRepository : JpaRepository<AssociationSignature, UUID>
