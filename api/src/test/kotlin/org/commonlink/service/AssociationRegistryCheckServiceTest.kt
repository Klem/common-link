package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationRegistryCheck
import org.commonlink.entity.ScopeVerdict
import org.commonlink.entity.User
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AssociationRegistryCheckServiceTest {

    private val repository: AssociationProfileRepository = mockk()
    private val registryCheckRepository: AssociationRegistryCheckRepository = mockk()
    private val restTemplate: RestTemplate = mockk()
    private val objectMapper = ObjectMapper()

    private val service = AssociationRegistryCheckService(
        associationProfileRepository = repository,
        registryCheckRepository = registryCheckRepository,
        restTemplate = restTemplate,
        objectMapper = objectMapper,
        inseeApiKey = "test-key",
        inseeBaseUrl = "https://api.insee.fr/api-sirene/3.11",
        joafeBaseUrl = "https://journal-officiel.example",
        bodaccBaseUrl = "https://bodacc.example",
        rechercheEntreprisesBaseUrl = "https://recherche-entreprises.api.gouv.fr",
    )

    private val associationId = UUID.randomUUID()
    private val curatorId = UUID.randomUUID()
    private val mockUser: User = mockk()
    private val profileWithRna = AssociationProfile(
        id = associationId,
        user = mockUser,
        name = "Test Association",
        identifier = "W123456789",
        siren = "123456789",
    )

    private val profileNoRna = AssociationProfile(
        id = associationId,
        user = mockUser,
        name = "Test Association",
        identifier = "123456789",
        siren = null,
    )

    /** RNA declared, no SIREN — the association may never have obtained one. */
    private val profileRnaOnly = AssociationProfile(
        id = associationId,
        user = mockUser,
        name = "Test Association",
        identifier = "W123456789",
        siren = null,
    )

    /** Same, but the SIREN column holds an empty string rather than NULL — the real shape in DB. */
    private val profileBlankSiren = AssociationProfile(
        id = associationId,
        user = mockUser,
        name = "Test Association",
        identifier = "W123456789",
        siren = "",
    )

    /** Neither a usable SIREN nor an RNA — the registry would reject a query this short. */
    private val profileUnusableIdentifier = AssociationProfile(
        id = associationId,
        user = mockUser,
        name = "Test Association",
        identifier = "42",
        siren = "",
    )

    /** `identifier` is NOT NULL in the schema, but nothing stops it holding an empty string. */
    private val profileNoIdentifier = AssociationProfile(
        id = associationId,
        user = mockUser,
        name = "Test Association",
        identifier = "",
        siren = null,
    )

    private val rechercheOk =
        """{"results":[{"siren":"123456789","identifiant_association":"W123456789","complements":{"est_association":true},"nature_juridique":"9220"}]}"""
    /** 5710 = SAS — a commercial company, genuinely outside the loi 1901 perimeter. */
    private val rechercheOutOfScope =
        """{"results":[{"siren":"123456789","identifiant_association":"W123456789","complements":{"est_association":false},"nature_juridique":"5710"}]}"""
    /** 9230 = association déclarée reconnue d'utilité publique — inside the perimeter. */
    private val rechercheArup =
        """{"results":[{"siren":"123456789","identifiant_association":"W123456789","complements":{"est_association":true},"nature_juridique":"9230"}]}"""
    /** 9260 = association de droit local (Alsace-Moselle) — inside the perimeter. */
    private val rechercheDroitLocal =
        """{"results":[{"siren":"123456789","identifiant_association":"W123456789","complements":{"est_association":true},"nature_juridique":"9260"}]}"""
    private val rechercheWithOfficers =
        """{"results":[{"siren":"123456789","identifiant_association":"W123456789","complements":{"est_association":true},"nature_juridique":"9220","dirigeants":[{"nom":"DUPONT","prenoms":"Jean","qualite":"Président"},{"nom":"MARTIN","prenoms":"Marie","qualite":"Trésorière"}]}]}"""
    /** Full-text search on a W-number: the exact match is not necessarily ranked first. */
    private val rechercheByRna =
        """{"results":[{"siren":"999999999","identifiant_association":"W999999999","complements":{"est_association":true},"nature_juridique":"9230","dirigeants":[{"nom":"WRONG","prenoms":"Person"}]},{"siren":"123456789","identifiant_association":"W123456789","complements":{"est_association":true},"nature_juridique":"9220","dirigeants":[{"nom":"DUPONT","prenoms":"Jean"}]}]}"""
    private val rechercheRnaMismatch =
        """{"results":[{"siren":"999999999","identifiant_association":"W999999999","complements":{"est_association":true},"nature_juridique":"9230","dirigeants":[{"nom":"WRONG","prenoms":"Person"}]}]}"""
    private val rechercheEmpty =
        """{"results":[]}"""
    private val inseeOk =
        """{"uniteLegale":{"etatAdministratifUniteLegale":"A"}}"""
    private val joafeEmpty =
        """{"records":[]}"""
    private val joafeCreation =
        """{"records":[{"record":{"fields":{"numero_rna":"W123456789","typeavis":"Création"}}}]}"""
    private val joafeDissolution =
        """{"records":[{"record":{"fields":{"numero_rna":"W123456789","typeavis":"Dissolution"}}}]}"""
    private val bodaccEmpty =
        """{"records":[]}"""
    private val bodaccProcedure =
        """{"records":[{"record":{"fields":{"familleavis":"pc"}}}]}"""

    private fun stubInsee(body: String = inseeOk) {
        every {
            restTemplate.exchange(
                match<String> { it.contains("api.insee.fr") },
                HttpMethod.GET,
                any<HttpEntity<Unit>>(),
                String::class.java,
            )
        } returns ResponseEntity.ok(body)
    }

    @BeforeEach
    fun setup() {
        every { repository.findById(associationId) } returns Optional.of(profileWithRna)
        // save() assigns an id, mirroring the DB default — returns a persisted clone.
        every { registryCheckRepository.save(any<AssociationRegistryCheck>()) } answers {
            val c = firstArg<AssociationRegistryCheck>()
            AssociationRegistryCheck(
                id = UUID.randomUUID(),
                associationId = c.associationId,
                associationExists = c.associationExists,
                siren = c.siren,
                rna = c.rna,
                legalCategory = c.legalCategory,
                etatAdministratif = c.etatAdministratif,
                joafeDeclarationFound = c.joafeDeclarationFound,
                dissolutionDetected = c.dissolutionDetected,
                bodaccProcedureFound = c.bodaccProcedureFound,
                warnings = c.warnings,
                officers = c.officers,
                rnaActive = c.rnaActive,
                checkedBy = c.checkedBy,
                checkedAt = c.checkedAt,
            )
        }
    }

    @Test
    fun `scan returns full result and persists a row when all sources respond`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.id).isNotNull()
        assertThat(result.associationExists).isTrue()
        assertThat(result.siren).isEqualTo("123456789")
        assertThat(result.rna).isEqualTo("W123456789")
        assertThat(result.etatAdministratif).isEqualTo("A")
        assertThat(result.joafeDeclarationFound).isTrue()
        assertThat(result.dissolutionDetected).isFalse()
        assertThat(result.bodaccProcedureFound).isFalse()
        assertThat(result.rnaActive).isTrue()
        assertThat(result.warnings).isEmpty()
        verify(exactly = 1) { registryCheckRepository.save(match<AssociationRegistryCheck> { it.checkedBy == curatorId }) }
    }

    @Test
    fun `scan maps est_association to rnaActive — false when out of scope`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOutOfScope
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.rnaActive).isFalse()
    }

    @Test
    fun `rnaActive is null when Recherche d'entreprises fails`() {
        // JOAFE reports a live association, but an outage of the primary source must stay visible as
        // "undetermined" instead of being papered over by the weaker JOAFE inference.
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } throws RuntimeException("timeout")
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.rnaActive).isNull()
    }

    @Test
    fun `scan detects dissolution in JOAFE`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeDissolution
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.dissolutionDetected).isTrue()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `scan detects BODACC procedure collective`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccProcedure

        val result = service.scan(associationId, curatorId)

        assertThat(result.bodaccProcedureFound).isTrue()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `scan adds warning and continues when Recherche d'entreprises fails`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } throws RuntimeException("connect timeout")
        // SIREN (profile.identifier) and RNA (profile.rna) checks still run independently — they don't depend on this call.
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.associationExists).isNull()
        assertThat(result.siren).isEqualTo("123456789")
        assertThat(result.etatAdministratif).isEqualTo("A")
        assertThat(result.bodaccProcedureFound).isFalse()
        assertThat(result.joafeDeclarationFound).isTrue()
        assertThat(result.warnings).anyMatch { it.startsWith("recherche-entreprises:") }
    }

    @Test
    fun `scan adds warning and continues when INSEE fails`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        every {
            restTemplate.exchange(
                match<String> { it.contains("api.insee.fr") },
                HttpMethod.GET,
                any<HttpEntity<Unit>>(),
                String::class.java,
            )
        } throws RuntimeException("401 Unauthorized")
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.etatAdministratif).isNull()
        assertThat(result.associationExists).isTrue()
        assertThat(result.warnings).anyMatch { it.startsWith("insee-sirene:") }
    }

    @Test
    fun `scan adds warning and continues when JOAFE fails`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } throws RuntimeException("503 Service Unavailable")
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.joafeDeclarationFound).isNull()
        assertThat(result.dissolutionDetected).isNull()
        assertThat(result.warnings).anyMatch { it.startsWith("joafe:") }
    }

    @Test
    fun `scan adds warning and continues when BODACC fails`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } throws RuntimeException("timeout")

        val result = service.scan(associationId, curatorId)

        assertThat(result.bodaccProcedureFound).isNull()
        assertThat(result.warnings).anyMatch { it.startsWith("bodacc:") }
    }

    @Test
    fun `scan runs INSEE and BODACC from profile SIREN and skips JOAFE when no RNA found`() {
        every { repository.findById(associationId) } returns Optional.of(profileNoRna)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns """{"results":[]}"""
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.associationExists).isFalse()
        assertThat(result.siren).isEqualTo("123456789")
        assertThat(result.etatAdministratif).isEqualTo("A")
        assertThat(result.joafeDeclarationFound).isNull()
        assertThat(result.bodaccProcedureFound).isFalse()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `scan cumulates SIREN and RNA checks when association has both`() {
        // profileWithRna has both identifier (SIREN) and rna set — INSEE/BODACC (SIREN) and JOAFE (RNA)
        // must all run, even if Recherche d'entreprises (which could otherwise gate them) fails.
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } throws RuntimeException("unavailable")
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccProcedure

        val result = service.scan(associationId, curatorId)

        assertThat(result.siren).isEqualTo("123456789")
        assertThat(result.rna).isEqualTo("W123456789")
        assertThat(result.etatAdministratif).isEqualTo("A")
        assertThat(result.joafeDeclarationFound).isTrue()
        assertThat(result.bodaccProcedureFound).isTrue()
    }

    @Test
    fun `scan throws 404 when association not found`() {
        every { repository.findById(associationId) } returns Optional.empty()

        assertThatThrownBy { service.scan(associationId, curatorId) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Association not found")
    }

    @Test
    fun `latest returns null when association was never scanned`() {
        every { registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null

        assertThat(service.latest(associationId)).isNull()
    }

    @Test
    fun `latest maps the most recent stored scan without any external call`() {
        val stored = AssociationRegistryCheck(
            id = UUID.randomUUID(),
            associationId = associationId,
            associationExists = true,
            siren = "123456789",
            rna = "W123456789",
            etatAdministratif = "A",
            joafeDeclarationFound = true,
            dissolutionDetected = false,
            bodaccProcedureFound = false,
            warnings = listOf("insee-sirene: timeout"),
            checkedBy = curatorId,
            checkedAt = Instant.now(),
        )
        every { registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns stored

        val result = service.latest(associationId)!!

        assertThat(result.id).isEqualTo(stored.id)
        assertThat(result.associationExists).isTrue()
        assertThat(result.siren).isEqualTo("123456789")
        assertThat(result.warnings).containsExactly("insee-sirene: timeout")
        // No restTemplate interaction is stubbed — a call would fail the test.
    }

    @Test
    fun `officers stored in scan row are readable via latest()`() {
        val stored = AssociationRegistryCheck(
            id = UUID.randomUUID(),
            associationId = associationId,
            associationExists = true,
            siren = "123456789",
            rna = "W123456789",
            officers = listOf("Jean DUPONT", "Marie MARTIN"),
            checkedBy = curatorId,
            checkedAt = Instant.now(),
        )
        every { registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns stored

        val result = service.latest(associationId)!!

        assertThat(result.officers).containsExactlyInAnyOrder("Jean DUPONT", "Marie MARTIN")
        // No restTemplate interaction is stubbed — a call would fail the test.
    }

    @Test
    fun `scan extracts officers from Recherche d'entreprises dirigeants`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheWithOfficers
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.officers).containsExactlyInAnyOrder("Jean DUPONT", "Marie MARTIN")
    }

    @Test
    fun `scan persists legalCategory 9220 and scopeVerdict IN_SCOPE for an in-scope association`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.legalCategory).isEqualTo("9220")
        assertThat(result.scopeVerdict).isEqualTo(ScopeVerdict.IN_SCOPE)
    }

    @Test
    fun `scan persists a non-association legalCategory and scopeVerdict OUT_OF_SCOPE for an out-of-scope entity`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOutOfScope
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.legalCategory).isEqualTo("5710")
        assertThat(result.scopeVerdict).isEqualTo(ScopeVerdict.OUT_OF_SCOPE)
    }

    @Test
    fun `scan places an association reconnue d'utilite publique (9230) IN_SCOPE`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheArup
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.legalCategory).isEqualTo("9230")
        assertThat(result.scopeVerdict).isEqualTo(ScopeVerdict.IN_SCOPE)
    }

    @Test
    fun `scan places an association de droit local (9260) IN_SCOPE`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheDroitLocal
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.legalCategory).isEqualTo("9260")
        assertThat(result.scopeVerdict).isEqualTo(ScopeVerdict.IN_SCOPE)
    }

    @Test
    fun `scan produces UNDETERMINED scopeVerdict when Recherche d'entreprises is unavailable`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } throws RuntimeException("503 unavailable")
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.legalCategory).isNull()
        assertThat(result.scopeVerdict).isEqualTo(ScopeVerdict.UNDETERMINED)
    }

    @Test
    fun `each scan creates a new append-only row`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result1 = service.scan(associationId, curatorId)
        val result2 = service.scan(associationId, curatorId)

        verify(exactly = 2) { registryCheckRepository.save(match<AssociationRegistryCheck> { it.id == null }) }
        assertThat(result1.id).isNotEqualTo(result2.id)
    }

    // ── RNA-only associations: no check may be gated on a declared SIREN ───────────────────────

    @Test
    fun `scan searches Recherche d'entreprises by RNA and cascades the resolved SIREN to INSEE and BODACC`() {
        every { repository.findById(associationId) } returns Optional.of(profileRnaOnly)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheByRna
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccProcedure

        val result = service.scan(associationId, curatorId)

        verify { restTemplate.getForObject(match<String> { it.contains("q=W123456789") }, String::class.java) }
        assertThat(result.rna).isEqualTo("W123456789")
        assertThat(result.siren).isEqualTo("123456789")
        assertThat(result.rnaActive).isTrue()
        assertThat(result.associationExists).isTrue()
        assertThat(result.legalCategory).isEqualTo("9220")
        assertThat(result.scopeVerdict).isEqualTo(ScopeVerdict.IN_SCOPE)
        assertThat(result.officers).containsExactly("Jean DUPONT")
        assertThat(result.etatAdministratif).isEqualTo("A")
        assertThat(result.bodaccProcedureFound).isTrue()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `scan ignores a Recherche d'entreprises record whose RNA does not match`() {
        every { repository.findById(associationId) } returns Optional.of(profileRnaOnly)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheRnaMismatch
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeEmpty

        val result = service.scan(associationId, curatorId)

        assertThat(result.siren).isNull()
        assertThat(result.legalCategory).isNull()
        assertThat(result.scopeVerdict).isEqualTo(ScopeVerdict.UNDETERMINED)
        assertThat(result.officers).isEmpty()
        assertThat(result.rnaActive).isNull()
        // INSEE and BODACC are unstubbed: any call would surface here as a named warning.
        assertThat(result.etatAdministratif).isNull()
        assertThat(result.bodaccProcedureFound).isNull()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `associationExists stays null when an RNA-only association is absent from Recherche d'entreprises`() {
        every { repository.findById(associationId) } returns Optional.of(profileRnaOnly)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheEmpty
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeEmpty

        val result = service.scan(associationId, curatorId)

        // Recherche d'entreprises only lists SIREN-bearing entities — absence there proves nothing.
        assertThat(result.associationExists).isNull()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `rnaActive falls back to JOAFE when an RNA-only association is absent from Recherche d'entreprises`() {
        every { repository.findById(associationId) } returns Optional.of(profileRnaOnly)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheEmpty
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation

        val result = service.scan(associationId, curatorId)

        assertThat(result.joafeDeclarationFound).isTrue()
        assertThat(result.dissolutionDetected).isFalse()
        assertThat(result.rnaActive).isTrue()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `rnaActive is false when JOAFE reports a dissolution for an RNA-only association`() {
        every { repository.findById(associationId) } returns Optional.of(profileRnaOnly)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheEmpty
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeDissolution

        val result = service.scan(associationId, curatorId)

        assertThat(result.dissolutionDetected).isTrue()
        assertThat(result.rnaActive).isFalse()
    }

    @Test
    fun `rnaActive stays null when JOAFE holds no publication for the RNA`() {
        every { repository.findById(associationId) } returns Optional.of(profileRnaOnly)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheEmpty
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeEmpty

        val result = service.scan(associationId, curatorId)

        // The dataset starts in 2000: no publication is not evidence of inactivity.
        assertThat(result.joafeDeclarationFound).isFalse()
        assertThat(result.rnaActive).isNull()
    }

    @Test
    fun `a blank SIREN column is read as absent and the RNA is used as search key`() {
        // Regression: `profile.siren ?: …` carried an empty string into the query, which the
        // registry rejects with 400 "3 caractères minimum pour les termes de la requête".
        every { repository.findById(associationId) } returns Optional.of(profileBlankSiren)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheByRna
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.scan(associationId, curatorId)

        verify { restTemplate.getForObject(match<String> { it.contains("q=W123456789") }, String::class.java) }
        verify(exactly = 0) { restTemplate.getForObject(match<String> { it.contains("q=&") }, String::class.java) }
        assertThat(result.siren).isEqualTo("123456789")
        assertThat(result.rnaActive).isTrue()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `an identifier too short to query the registry is recorded as a source failure`() {
        every { repository.findById(associationId) } returns Optional.of(profileUnusableIdentifier)
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation

        val result = service.scan(associationId, curatorId)

        // Unstubbed: calling Recherche d'entreprises at all would fail the test.
        verify(exactly = 0) { restTemplate.getForObject(any<String>(), String::class.java) }
        assertThat(result.warnings).anyMatch { it.startsWith("recherche-entreprises:") }
        assertThat(result.associationExists).isNull()
        // The primary source was never consulted — the JOAFE fallback must not assert activity.
        assertThat(result.rnaActive).isNull()
    }

    @Test
    fun `a profile with no usable identifier produces an inconclusive scan, not a silent empty one`() {
        every { repository.findById(associationId) } returns Optional.of(profileNoIdentifier)

        val result = service.scan(associationId, curatorId)

        // No registry can be queried at all — an empty result with no warning would read as
        // "nothing found", which is the reassuring interpretation of a broken dossier.
        assertThat(result.warnings).anyMatch { it.startsWith("recherche-entreprises:") }
        assertThat(result.siren).isNull()
        assertThat(result.rna).isNull()
        assertThat(result.associationExists).isNull()
        assertThat(result.rnaActive).isNull()
        assertThat(result.scopeVerdict).isEqualTo(ScopeVerdict.UNDETERMINED)
    }

    @Test
    fun `JOAFE is queried newest-first so a dissolution cannot fall outside the window`() {
        every { repository.findById(associationId) } returns Optional.of(profileRnaOnly)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheEmpty
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation

        service.scan(associationId, curatorId)

        verify {
            restTemplate.getForObject(
                match<URI> { it.toString().contains("order_by=dateparution%20desc") || it.toString().contains("order_by=dateparution+desc") },
                String::class.java,
            )
        }
    }
}
