package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.User
import org.commonlink.repository.AssociationProfileRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class AssociationRegistryCheckServiceTest {

    private val repository: AssociationProfileRepository = mockk()
    private val restTemplate: RestTemplate = mockk()
    private val objectMapper = ObjectMapper()

    private val service = AssociationRegistryCheckService(
        associationProfileRepository = repository,
        restTemplate = restTemplate,
        objectMapper = objectMapper,
        inseeApiKey = "test-key",
        inseeBaseUrl = "https://api.insee.fr/api-sirene/3.11",
    )

    private val associationId = UUID.randomUUID()
    private val mockUser: User = mockk()
    private val profileWithRna = AssociationProfile(
        id = associationId,
        user = mockUser,
        name = "Test Association",
        identifier = "123456789",
        rna = "W123456789",
    )

    private val profileNoRna = AssociationProfile(
        id = associationId,
        user = mockUser,
        name = "Test Association",
        identifier = "123456789",
        rna = null,
    )

    private val rechercheOk =
        """{"results":[{"siren":"123456789","identifiant_association":"W123456789","complements":{"est_association":true},"nature_juridique":"9220"}]}"""
    private val inseeOk =
        """{"uniteLegale":{"etatAdministratifUniteLegale":"A"}}"""
    private val joafeCreation =
        """{"results":[{"typeavis":"Création"}]}"""
    private val joafeDissolution =
        """{"results":[{"typeavis":"Dissolution"}]}"""
    private val bodaccEmpty =
        """{"results":[]}"""
    private val bodaccProcedure =
        """{"results":[{"familleavis":"pc"}]}"""

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
    }

    @Test
    fun `check returns full result when all sources respond`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<String> { it.contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<String> { it.contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.check(associationId)

        assertThat(result.associationExists).isTrue()
        assertThat(result.siren).isEqualTo("123456789")
        assertThat(result.rna).isEqualTo("W123456789")
        assertThat(result.etatAdministratif).isEqualTo("A")
        assertThat(result.joafeDeclarationFound).isTrue()
        assertThat(result.dissolutionDetected).isFalse()
        assertThat(result.bodaccProcedureFound).isFalse()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `check detects dissolution in JOAFE`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<String> { it.contains("journal-officiel") }, String::class.java) } returns joafeDissolution
        every { restTemplate.getForObject(match<String> { it.contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.check(associationId)

        assertThat(result.dissolutionDetected).isTrue()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `check detects BODACC procedure collective`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<String> { it.contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<String> { it.contains("bodacc") }, String::class.java) } returns bodaccProcedure

        val result = service.check(associationId)

        assertThat(result.bodaccProcedureFound).isTrue()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `check adds warning and continues when Recherche d'entreprises fails`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } throws RuntimeException("connect timeout")
        // JOAFE still runs because profile.rna is set; INSEE and BODACC are skipped (no sirenFound)
        every { restTemplate.getForObject(match<String> { it.contains("journal-officiel") }, String::class.java) } returns joafeCreation

        val result = service.check(associationId)

        assertThat(result.associationExists).isNull()
        assertThat(result.siren).isNull()
        assertThat(result.etatAdministratif).isNull()
        assertThat(result.bodaccProcedureFound).isNull()
        assertThat(result.joafeDeclarationFound).isTrue()
        assertThat(result.warnings).anyMatch { it.startsWith("recherche-entreprises:") }
    }

    @Test
    fun `check adds warning and continues when INSEE fails`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        every {
            restTemplate.exchange(
                match<String> { it.contains("api.insee.fr") },
                HttpMethod.GET,
                any<HttpEntity<Unit>>(),
                String::class.java,
            )
        } throws RuntimeException("401 Unauthorized")
        every { restTemplate.getForObject(match<String> { it.contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<String> { it.contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.check(associationId)

        assertThat(result.etatAdministratif).isNull()
        assertThat(result.associationExists).isTrue()
        assertThat(result.warnings).anyMatch { it.startsWith("insee-sirene:") }
    }

    @Test
    fun `check adds warning and continues when JOAFE fails`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<String> { it.contains("journal-officiel") }, String::class.java) } throws RuntimeException("503 Service Unavailable")
        every { restTemplate.getForObject(match<String> { it.contains("bodacc") }, String::class.java) } returns bodaccEmpty

        val result = service.check(associationId)

        assertThat(result.joafeDeclarationFound).isNull()
        assertThat(result.dissolutionDetected).isNull()
        assertThat(result.warnings).anyMatch { it.startsWith("joafe:") }
    }

    @Test
    fun `check adds warning and continues when BODACC fails`() {
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns rechercheOk
        stubInsee()
        every { restTemplate.getForObject(match<String> { it.contains("journal-officiel") }, String::class.java) } returns joafeCreation
        every { restTemplate.getForObject(match<String> { it.contains("bodacc") }, String::class.java) } throws RuntimeException("timeout")

        val result = service.check(associationId)

        assertThat(result.bodaccProcedureFound).isNull()
        assertThat(result.warnings).anyMatch { it.startsWith("bodacc:") }
    }

    @Test
    fun `check skips INSEE and BODACC when no SIREN found`() {
        every { repository.findById(associationId) } returns Optional.of(profileNoRna)
        every { restTemplate.getForObject(match<String> { it.contains("recherche-entreprises") }, String::class.java) } returns """{"results":[]}"""

        val result = service.check(associationId)

        assertThat(result.associationExists).isFalse()
        assertThat(result.siren).isNull()
        assertThat(result.etatAdministratif).isNull()
        assertThat(result.joafeDeclarationFound).isNull()
        assertThat(result.bodaccProcedureFound).isNull()
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `check throws 404 when association not found`() {
        every { repository.findById(associationId) } returns Optional.empty()

        assertThatThrownBy { service.check(associationId) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Association not found")
    }
}
