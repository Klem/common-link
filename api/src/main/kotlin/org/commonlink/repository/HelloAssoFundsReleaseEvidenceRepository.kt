package org.commonlink.repository

import org.commonlink.entity.HelloAssoFundsReleaseEvidence
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Evidence of curator releases of HelloAsso donations; written only, read back by id. */
interface HelloAssoFundsReleaseEvidenceRepository : JpaRepository<HelloAssoFundsReleaseEvidence, UUID>
