package org.commonlink.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.PayoutKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.util.UUID

/**
 * The amount on the wire must be a number the ledger can actually store.
 *
 * `payouts.amount` is `NUMERIC(12,2)`. Without [jakarta.validation.constraints.Digits] the balance
 * check ran on the value as sent while the insert rounded it: `10.009` passed a check against
 * 10.00 € of available funds and landed as `10.01`. Fractions of a cent, but a guard that
 * validates a different number from the one it stores is not a guard, and every click here is
 * replayable — the form's `step="0.01"` binds nobody.
 */
class CreatePayoutRequestValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    private fun violationsOn(amount: BigDecimal): Set<String> =
        validator.validate(
            CreatePayoutRequest(
                payeeId = UUID.randomUUID(),
                payeeIbanId = UUID.randomUUID(),
                amount = amount,
                kind = PayoutKind.EXPENSE,
                typeCode = "60-mat",
                label = "Achat matériel pédagogique",
            )
        ).map { it.propertyPath.toString() }.toSet()

    @ParameterizedTest
    @ValueSource(strings = ["0.01", "10", "10.5", "10.50", "3.10", "9999999999.99"])
    fun `accepts an amount the column can hold exactly`(amount: String) {
        assertThat(violationsOn(BigDecimal(amount))).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["10.009", "0.001", "1.234"])
    fun `rejects an amount with more precision than the column keeps`(amount: String) {
        assertThat(violationsOn(BigDecimal(amount))).contains("amount")
    }

    @Test
    fun `rejects an integer part wider than the column`() {
        assertThat(violationsOn(BigDecimal("12345678901.00"))).contains("amount")
    }

    @Test
    fun `still rejects a non-positive amount`() {
        assertThat(violationsOn(BigDecimal("0.00"))).contains("amount")
    }
}
