package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationRegistryCheck
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
        rnaBaseUrl = "https://rna.example",
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

    private val rechercheOk =
        """{"results":[{"siren":"123456789","identifiant_association":"W123456789","complements":{"est_association":true},"nature_juridique":"9220"}]}"""
    private val rechercheWithOfficers =
        """{"results":[{"siren":"123456789","identifiant_association":"W123456789","complements":{"est_association":true},"nature_juridique":"9220","dirigeants":[{"nom":"DUPONT","prenoms":"Jean","qualite":"Président"},{"nom":"MARTIN","prenoms":"Marie","qualite":"Trésorière"}]}]}"""
    private val rnaOk = """{"active":true}"""
    private val inseeOk =
        """{"uniteLegale":{"etatAdministratifUniteLegale":"A"}}"""
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

    private fun stubRna(body: String = rnaOk) {
        every { restTemplate.getForObject(match<String> { it.contains("rna.example") }, String::class.java) } returns body
    }

    @BeforeEach
    fun setup() {
        every { repository.findById(associationId) } returns Optional.of(profileWithRna)
        stubRna()
        // save() assigns an id, mirroring the DB default — returns a persisted clone.
        every { registryCheckRepository.save(any<AssociationRegistryCheck>()) } answers {
            val c = firstArg<AssociationRegistryCheck>()
            AssociationRegistryCheck(
                id = UUID.randomUUID(),
                associationId = c.associationId,
                associationExists = c.associationExists,
                siren = c.siren,
                rna = c.rna,
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
        assertThat(result.warnings).isEmpty()
        verify(exactly = 1) { registryCheckRepository.save(match<AssociationRegistryCheck> { it.checkedBy == curatorId }) }
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
    fun `scan persists rnaActive from RNA registry`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty
        stubRna("""{"active":true}""")

        val result = service.scan(associationId, curatorId)

        assertThat(result.rnaActive).isTrue()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `scan adds warning and continues when RNA fails`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<URI> { it.toString().contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<URI> { it.toString().contains("bodacc") }, String::class.java) } returns bodaccEmpty
        every { restTemplate.getForObject(match<String> { it.contains("rna.example") }, String::class.java) } throws RuntimeException("503 RNA unavailable")

        val result = service.scan(associationId, curatorId)

        assertThat(result.rnaActive).isNull()
        assertThat(result.warnings).anyMatch { it.startsWith("rna:") }
        assertThat(result.associationExists).isTrue()
    }

    @Test
    fun `officers and rnaActive stored in scan row are readable via latest()`() {
        val stored = AssociationRegistryCheck(
            id = UUID.randomUUID(),
            associationId = associationId,
            associationExists = true,
            siren = "123456789",
            rna = "W123456789",
            officers = listOf("Jean DUPONT", "Marie MARTIN"),
            rnaActive = true,
            checkedBy = curatorId,
            checkedAt = Instant.now(),
        )
        every { registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns stored

        val result = service.latest(associationId)!!

        assertThat(result.officers).containsExactlyInAnyOrder("Jean DUPONT", "Marie MARTIN")
        assertThat(result.rnaActive).isTrue()
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
}
