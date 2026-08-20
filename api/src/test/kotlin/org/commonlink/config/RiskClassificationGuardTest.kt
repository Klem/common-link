package org.commonlink.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RiskClassificationGuardTest {

    private val validMeasures = mapOf(
        "LOW" to RiskClassificationProperties.VigilanceMeasures(
            description = "desc",
            reviewFrequency = "3 ans",
            requiredDocuments = listOf("doc1"),
        ),
        "STANDARD" to RiskClassificationProperties.VigilanceMeasures(
            description = "desc",
            reviewFrequency = "2 ans",
            requiredDocuments = listOf("doc1"),
        ),
        "HIGH" to RiskClassificationProperties.VigilanceMeasures(
            description = "desc",
            reviewFrequency = "1 an",
            requiredDocuments = listOf("doc1"),
        ),
    )

    private fun guard(version: String, measures: Map<String, RiskClassificationProperties.VigilanceMeasures>) =
        RiskClassificationGuard(RiskClassificationProperties(version = version, measures = measures))

    @Test
    fun `fails when version is empty`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            guard(version = "", measures = validMeasures).validate()
        }
        assertTrue(ex.message!!.contains("version"))
    }

    @Test
    fun `fails when HIGH has no entry`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            guard(version = "2026-08-v1", measures = validMeasures - "HIGH").validate()
        }
        assertTrue(ex.message!!.contains("HIGH"))
    }

    @Test
    fun `fails when LOW has no entry`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            guard(version = "2026-08-v1", measures = validMeasures - "LOW").validate()
        }
        assertTrue(ex.message!!.contains("LOW"))
    }

    @Test
    fun `succeeds and exposes version when configuration is complete`() {
        val g = guard(version = "2026-08-v1", measures = validMeasures)
        assertDoesNotThrow { g.validate() }
        assertEquals("2026-08-v1", g.currentVersion())
    }
}
