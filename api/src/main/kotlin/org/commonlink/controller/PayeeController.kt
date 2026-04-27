package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.AddIbanRequest
import org.commonlink.dto.PayeeDto
import org.commonlink.dto.CreatePayeeRequest
import org.commonlink.dto.VopVerifyResponseDto
import org.commonlink.service.PayeeService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/association/payees")
@Tag(name = "Payee", description = "Payee management for associations")
class PayeeController(
    private val payeeService: PayeeService
) {

    /**
     * Returns all payees registered under the authenticated association.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @return 200 with the list of payees (may be empty).
     */
    @GetMapping
    @Operation(
        summary = "List payees",
        description = "Returns all payees registered under the authenticated association."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "List of payees returned",
            content = [Content(schema = Schema(implementation = Array<PayeeDto>::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun listPayees(
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<List<PayeeDto>> =
        ResponseEntity.ok(payeeService.listPayees(UUID.fromString(principal.username)))

    /**
     * Creates a new payee under the authenticated association.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param req Validated creation request.
     * @return 201 with the created payee DTO.
     */
    @PostMapping
    @Operation(
        summary = "Create payee",
        description = "Creates a new payee under the authenticated association. The SIREN (identifier1) must be unique per association."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201", description = "Payee created",
            content = [Content(schema = Schema(implementation = PayeeDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error (e.g. invalid SIREN format)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Payee with this SIREN already exists", content = [Content()])
    )
    fun createPayee(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: CreatePayeeRequest
    ): ResponseEntity<PayeeDto> =
        ResponseEntity.status(201).body(
            payeeService.createPayee(UUID.fromString(principal.username), req)
        )

    /**
     * Deletes a payee (and its IBANs via cascade) from the authenticated association.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the payee to delete.
     * @return 204 No Content on success.
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete payee",
        description = "Deletes a payee and all its associated IBANs."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Payee deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Payee not found", content = [Content()])
    )
    fun deletePayee(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        payeeService.deletePayee(UUID.fromString(principal.username), id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Adds an IBAN to an existing payee. The IBAN is normalized and validated server-side
     * using the mod-97 algorithm before being persisted.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the payee.
     * @param req Validated request carrying the IBAN string.
     * @return 201 with the updated payee DTO including the new IBAN.
     */
    @PostMapping("/{id}/ibans")
    @Operation(
        summary = "Add IBAN to payee",
        description = "Adds an IBAN to the given payee. The server validates the IBAN format via mod-97 and sets status to FORMAT_VALID or INVALID accordingly."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201", description = "IBAN added",
            content = [Content(schema = Schema(implementation = PayeeDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error (e.g. IBAN too long)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Payee not found", content = [Content()])
    )
    fun addIban(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: AddIbanRequest
    ): ResponseEntity<PayeeDto> =
        ResponseEntity.status(201).body(
            payeeService.addIban(UUID.fromString(principal.username), id, req)
        )

    /**
     * Removes an IBAN entry from a payee.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the payee.
     * @param ibanId UUID of the IBAN entry to remove.
     * @return 204 No Content on success.
     */
    @DeleteMapping("/{id}/ibans/{ibanId}")
    @Operation(
        summary = "Delete IBAN from payee",
        description = "Removes a specific IBAN entry from the given payee."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "IBAN deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Payee or IBAN not found", content = [Content()])
    )
    fun deleteIban(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @PathVariable ibanId: UUID
    ): ResponseEntity<Void> {
        payeeService.deleteIban(UUID.fromString(principal.username), id, ibanId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Triggers a VOP (Verification of Payee) check for a payee IBAN.
     *
     * The IBAN must be in FORMAT_VALID status. The check is performed synchronously and the
     * result is persisted immediately. In demo mode, a 500 ms simulated delay is applied.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param payeeId UUID of the payee that owns the IBAN.
     * @param ibanId UUID of the IBAN entry to verify.
     * @return 200 with [VopVerifyResponseDto] containing the updated status and VOP outcome.
     */
    @PostMapping("/{payeeId}/ibans/{ibanId}/verify-vop")
    @Operation(
        summary = "Verify IBAN via VOP",
        description = "Runs a Verification of Payee check on the given IBAN. The IBAN must be in FORMAT_VALID status. Result is persisted and returned immediately."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "VOP check completed",
            content = [Content(schema = Schema(implementation = VopVerifyResponseDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Payee or IBAN not found", content = [Content()]),
        ApiResponse(responseCode = "422", description = "IBAN is not in FORMAT_VALID status", content = [Content()])
    )
    fun verifyIbanVop(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable payeeId: UUID,
        @PathVariable ibanId: UUID
    ): ResponseEntity<VopVerifyResponseDto> =
        ResponseEntity.ok(
            payeeService.verifyIbanVop(UUID.fromString(principal.username), payeeId, ibanId)
        )
}
