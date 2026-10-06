package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.entity.ActionPlaceType
import org.commonlink.entity.CampaignScope
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.UnprocessableEntityException
import org.junit.jupiter.api.Test

class ActionPlaceServiceTest {

    private val geo = mockk<GeoApiClient>()
    private val service = ActionPlaceService(geo)

    private val vallauris = GeoCommune("06155", "Vallauris", "06", 43.58, 7.05)

    @Test
    fun `commune is resolved from geo api with label and coordinates`() {
        every { geo.findCommune("06155") } returns vallauris

        val place = service.resolve(ActionPlaceType.COMMUNE, " 06155 ")

        assertThat(place.code).isEqualTo("06155")
        assertThat(place.label).isEqualTo("Vallauris (06)")
        assertThat(place.latitude).isEqualTo(43.58)
        assertThat(place.longitude).isEqualTo(7.05)
        assertThat(place.type.scope).isEqualTo(CampaignScope.LOCALE)
    }

    @Test
    fun `malformed commune code is rejected without calling geo api`() {
        assertThatThrownBy { service.resolve(ActionPlaceType.COMMUNE, "06155; DROP") }
            .isInstanceOf(UnprocessableEntityException::class.java)
        verify(exactly = 0) { geo.findCommune(any()) }
    }

    @Test
    fun `unknown commune code is rejected`() {
        every { geo.findCommune("99999") } returns null

        assertThatThrownBy { service.resolve(ActionPlaceType.COMMUNE, "99999") }
            .isInstanceOf(UnprocessableEntityException::class.java)
    }

    @Test
    fun `geo api outage surfaces as bad gateway on explicit resolve`() {
        every { geo.findDepartment("06") } throws BadGatewayException("down")

        assertThatThrownBy { service.resolve(ActionPlaceType.DEPARTEMENT, "06") }
            .isInstanceOf(BadGatewayException::class.java)
    }

    @Test
    fun `corsican department code is accepted`() {
        every { geo.findDepartment("2A") } returns GeoDepartment("2A", "Corse-du-Sud")

        val place = service.resolve(ActionPlaceType.DEPARTEMENT, "2a")

        assertThat(place.label).isEqualTo("Corse-du-Sud (2A)")
        assertThat(place.latitude).isNull()
    }

    @Test
    fun `france needs no code and is national`() {
        val place = service.resolve(ActionPlaceType.FRANCE, "ignored")

        assertThat(place.code).isNull()
        assertThat(place.label).isEqualTo("France entière")
        assertThat(place.type.scope).isEqualTo(CampaignScope.NATIONALE)
    }

    @Test
    fun `foreign country is international with french label`() {
        val place = service.resolve(ActionPlaceType.PAYS, "sn")

        assertThat(place.code).isEqualTo("SN")
        assertThat(place.label).isEqualTo("Sénégal")
        assertThat(place.type.scope).isEqualTo(CampaignScope.INTERNATIONALE)
    }

    @Test
    fun `france, overseas department or unknown iso code is rejected as a country`() {
        assertThatThrownBy { service.resolve(ActionPlaceType.PAYS, "FR") }
            .isInstanceOf(UnprocessableEntityException::class.java)
        assertThatThrownBy { service.resolve(ActionPlaceType.PAYS, "ZZ") }
            .isInstanceOf(UnprocessableEntityException::class.java)
        assertThatThrownBy { service.resolve(ActionPlaceType.PAYS, "RE") }
            .isInstanceOf(UnprocessableEntityException::class.java)
    }

    @Test
    fun `association address picks the commune matching the city among shared postal code`() {
        every { geo.findCommunesByPostalCode("06220") } returns listOf(
            GeoCommune("06155", "Vallauris", "06", 43.58, 7.05),
            GeoCommune("06999", "Golfe-Juan", "06", 43.57, 7.07),
        )

        val place = service.fromAssociationAddress("GOLFE JUAN", "06220")

        assertThat(place?.code).isEqualTo("06999")
    }

    @Test
    fun `association address is null when ambiguous, malformed or upstream is down`() {
        every { geo.findCommunesByPostalCode("06220") } returns listOf(
            GeoCommune("06155", "Vallauris", "06", 43.58, 7.05),
            GeoCommune("06999", "Golfe-Juan", "06", 43.57, 7.07),
        )
        every { geo.findCommunesByPostalCode("75001") } throws BadGatewayException("down")

        assertThat(service.fromAssociationAddress("Antibes", "06220")).isNull()
        assertThat(service.fromAssociationAddress("Paris", "75001")).isNull()
        assertThat(service.fromAssociationAddress("Paris", "750")).isNull()
        assertThat(service.fromAssociationAddress("Paris", null)).isNull()
    }

    @Test
    fun `single commune for postal code is used even when the city does not match`() {
        every { geo.findCommunesByPostalCode("06220") } returns listOf(vallauris)

        assertThat(service.fromAssociationAddress("Valauris", "06220")?.code).isEqualTo("06155")
    }
}
