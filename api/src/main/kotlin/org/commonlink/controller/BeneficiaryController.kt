package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.AddIbanRequest
import org.commonlink.dto.BeneficiaryDto
import org.commonlink.dto.CreateBeneficiaryRequest
import org.commonlink.service.BeneficiaryService
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
@RequestMapping("/api/association/beneficiaries")
@Tag(name = "Beneficiary", description = "Beneficiary management for associations")
class BeneficiaryController(
    private val beneficiaryService: BeneficiaryService
) {

    /**
     * Returns all beneficiaries registered under the authenticated association.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @return 200 with the list of beneficiaries (may be empty).
     */
    @GetMapping
    @Operation(
        summary = "List beneficiaries",
        description = "Returns all beneficiaries registered under the authenticated association."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "List of beneficiaries returned",
            content = [Content(schema = Schema(implementation = Array<BeneficiaryDto>::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun listBeneficiaries(
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<List<BeneficiaryDto>> =
        ResponseEntity.ok(beneficiaryService.listBeneficiaries(UUID.fromString(principal.username)))

    /**
     * Creates a new beneficiary under the authenticated association.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param req Validated creation request.
     * @return 201 with the created beneficiary DTO.
     */
    @PostMapping
    @Operation(
        summary = "Create beneficiary",
        description = "Creates a new beneficiary under the authenticated association. The SIREN (identifier1) must be unique per association."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201", description = "Beneficiary created",
            content = [Content(schema = Schema(implementation = BeneficiaryDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error (e.g. invalid SIREN format)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Beneficiary with this SIREN already exists", content = [Content()])
    )
    fun createBeneficiary(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: CreateBeneficiaryRequest
    ): ResponseEntity<BeneficiaryDto> =
        ResponseEntity.status(201).body(
            beneficiaryService.createBeneficiary(UUID.fromString(principal.username), req)
        )

    /**
     * Deletes a beneficiary (and its IBANs via cascade) from the authenticated association.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the beneficiary to delete.
     * @return 204 No Content on success.
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete beneficiary",
        description = "Deletes a beneficiary and all its associated IBANs."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Beneficiary deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Beneficiary not found", content = [Content()])
    )
    fun deleteBeneficiary(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        beneficiaryService.deleteBeneficiary(UUID.fromString(principal.username), id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Adds an IBAN to an existing beneficiary. The IBAN is normalized and validated server-side
     * using the mod-97 algorithm before being persisted.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the beneficiary.
     * @param req Validated request carrying the IBAN string.
     * @return 201 with the updated beneficiary DTO including the new IBAN.
     */
    @PostMapping("/{id}/ibans")
    @Operation(
        summary = "Add IBAN to beneficiary",
        description = "Adds an IBAN to the given beneficiary. The server validates the IBAN format via mod-97 and sets status to FORMAT_VALID or INVALID accordingly."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201", description = "IBAN added",
            content = [Content(schema = Schema(implementation = BeneficiaryDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Validation error (e.g. IBAN too long)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Beneficiary not found", content = [Content()])
    )
    fun addIban(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: AddIbanRequest
    ): ResponseEntity<BeneficiaryDto> =
        ResponseEntity.status(201).body(
            beneficiaryService.addIban(UUID.fromString(principal.username), id, req)
        )

    /**
     * Removes an IBAN entry from a beneficiary.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param id UUID of the beneficiary.
     * @param ibanId UUID of the IBAN entry to remove.
     * @return 204 No Content on success.
     */
    @DeleteMapping("/{id}/ibans/{ibanId}")
    @Operation(
        summary = "Delete IBAN from beneficiary",
        description = "Removes a specific IBAN entry from the given beneficiary."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "IBAN deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Beneficiary or IBAN not found", content = [Content()])
    )
    fun deleteIban(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @PathVariable ibanId: UUID
    ): ResponseEntity<Void> {
        beneficiaryService.deleteIban(UUID.fromString(principal.username), id, ibanId)
        return ResponseEntity.noContent().build()
    }
}
