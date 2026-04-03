package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.SireneSearchResultDto
import org.commonlink.service.SireneSearchService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller that proxies INSEE Sirene 3.11 search requests.
 *
 * Gated by `ROLE_ASSOCIATION` via Spring Security (see [org.commonlink.security.SecurityConfig]).
 * The [UserDetails] principal is extracted to enforce a valid JWT even though the user ID
 * is not forwarded to INSEE.
 */
@RestController
@RequestMapping("/api/association/sirene")
@Tag(name = "Sirene Search", description = "INSEE Sirene search proxy")
class SireneSearchController(
    private val sireneSearchService: SireneSearchService
) {

    /**
     * Searches the INSEE Sirene API for a SIREN (9 digits) or SIRET (14 digits) identifier.
     *
     * @param q Raw SIREN or SIRET query; non-digit characters are stripped server-side.
     * @param principal Injected JWT principal — ensures the request carries a valid association token.
     * @return 200 with a simplified [SireneSearchResultDto].
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search by SIREN or SIRET",
        description = "Queries the INSEE Sirene 3.11 API and returns a simplified DTO with key fields only. Accepts a 9-digit SIREN or a 14-digit SIRET."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Entity found",
            content = [Content(schema = Schema(implementation = SireneSearchResultDto::class))]
        ),
        ApiResponse(responseCode = "400", description = "Invalid identifier format (not 9 or 14 digits)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No establishment found for this identifier", content = [Content()]),
        ApiResponse(responseCode = "429", description = "INSEE API rate limit exceeded", content = [Content()]),
        ApiResponse(responseCode = "502", description = "INSEE API unreachable or authentication error", content = [Content()])
    )
    fun search(
        @RequestParam q: String,
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<SireneSearchResultDto> =
        ResponseEntity.ok(sireneSearchService.search(q))
}
