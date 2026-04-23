package org.commonlink.repository

import org.commonlink.entity.PayeeIban
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface PayeeIbanRepository : JpaRepository<PayeeIban, UUID> {
    /**
     * Finds an IBAN entry by its own ID and the owning payee's ID.
     *
     * Used to verify ownership before updating status or VOP results — prevents cross-payee access.
     *
     * @param id the UUID of the [PayeeIban]
     * @param payeeId the UUID of the owning [org.commonlink.entity.Payee]
     */
    fun findByIdAndPayeeId(id: UUID, payeeId: UUID): Optional<PayeeIban>
}
