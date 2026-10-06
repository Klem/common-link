package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.exception.BadGatewayException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

/**
 * Parses payloads captured from the real geo.api.gouv.fr (2026-10-05), served by a local HTTP
 * server -- no network access.
 */
class GeoApiClientTest {

    private lateinit var server: HttpServer
    private lateinit var client: GeoApiClient

    private val routes = mapOf(
        "/communes/06155" to (200 to """{"nom":"Vallauris","code":"06155","centre":{"type":"Point","coordinates":[7.0604,43.5787]},"codeDepartement":"06"}"""),
        "/departements/06" to (200 to """{"nom":"Alpes-Maritimes","code":"06"}"""),
        "/communes" to (200 to """[{"nom":"Vallauris","code":"06155","centre":{"type":"Point","coordinates":[7.0604,43.5787]},"codeDepartement":"06"}]"""),
        "/communes/99999" to (404 to "Not Found"),
        "/communes/00000" to (200 to """{"unexpected":true}"""),
        "/departements/13" to (504 to "<html>504 Gateway Time-out</html>"),
    )

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            val (status, body) = routes[exchange.requestURI.path] ?: (404 to "Not Found")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        client = GeoApiClient("http://localhost:${server.address.port}", ObjectMapper())
    }

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `commune is parsed with geojson longitude-latitude order`() {
        val commune = client.findCommune("06155")!!

        assertThat(commune.name).isEqualTo("Vallauris")
        assertThat(commune.departmentCode).isEqualTo("06")
        assertThat(commune.latitude).isEqualTo(43.5787)
        assertThat(commune.longitude).isEqualTo(7.0604)
    }

    @Test
    fun `department and postal code lookups are parsed`() {
        assertThat(client.findDepartment("06")).isEqualTo(GeoDepartment("06", "Alpes-Maritimes"))
        assertThat(client.findCommunesByPostalCode("06220").map { it.code }).containsExactly("06155")
    }

    @Test
    fun `unknown code yields null`() {
        assertThat(client.findCommune("99999")).isNull()
    }

    @Test
    fun `unexpected payload and upstream error both surface as bad gateway`() {
        assertThatThrownBy { client.findCommune("00000") }.isInstanceOf(BadGatewayException::class.java)
        assertThatThrownBy { client.findDepartment("13") }.isInstanceOf(BadGatewayException::class.java)
    }
}
