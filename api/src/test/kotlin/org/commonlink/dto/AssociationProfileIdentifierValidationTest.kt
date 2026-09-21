package org.commonlink.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The sign-up screen refuses an identifier that is neither a RNA nor a SIREN; every click is
 * replayable, so the same rule has to hold on the wire.
 *
 * The case that motivates it: the JOAFE dataset reuses its RNA column for announcements predating
 * the registry, where it holds `ASS` + the announcement number within its issue. That number
 * restarts at every issue — `ASS01469` alone is carried by 806 announcements belonging to unrelated
 * associations — so it identifies no legal entity, and is long enough to be searched as a SIREN by
 * the registry check.
 */
class AssociationProfileIdentifierValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    private fun violationsOn(identifier: String): Set<String> =
        validator.validate(AssociationProfileRequestDto(name = "Test Association", identifier = identifier))
            .map { it.propertyPath.toString() }
            .toSet()

    private fun upsertViolationsOn(identifier: String): Set<String> =
        validator.validate(AssociationProfileUpsertDto(nom = "Test Association", identifier = identifier))
            .map { it.propertyPath.toString() }
            .toSet()

    @ParameterizedTest
    @ValueSource(strings = ["W123456789", "W2A1000001", "123456789"])
    fun `accepts a RNA or a SIREN`(identifier: String) {
        assertThat(violationsOn(identifier)).isEmpty()
        assertThat(upsertViolationsOn(identifier)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["ASS02290", "ASS01469", "W123", "12345678", "1234567890", "W12345678901", "42"])
    fun `rejects anything that identifies no legal entity`(identifier: String) {
        assertThat(violationsOn(identifier)).contains("identifier")
        assertThat(upsertViolationsOn(identifier)).contains("identifier")
    }

    @Test
    fun `rejects a blank identifier`() {
        assertThat(violationsOn("")).contains("identifier")
        assertThat(upsertViolationsOn("")).contains("identifier")
    }
}
