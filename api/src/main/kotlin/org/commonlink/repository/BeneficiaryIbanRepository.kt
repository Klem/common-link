package org.commonlink.repository

import org.commonlink.entity.BeneficiaryIban
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface BeneficiaryIbanRepository : JpaRepository<BeneficiaryIban, UUID> {
    /**
     * Finds an IBAN entry by its own ID and the owning beneficiary's ID.
     *
     * Used to verify ownership before updating status or VOP results — prevents cross-beneficiary access.
     *
     * @param id the UUID of the [BeneficiaryIban]
     * @param beneficiaryId the UUID of the owning [org.commonlink.entity.Beneficiary]
     */
    fun findByIdAndBeneficiaryId(id: UUID, beneficiaryId: UUID): Optional<BeneficiaryIban>
}
