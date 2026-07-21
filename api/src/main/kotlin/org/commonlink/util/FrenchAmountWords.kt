package org.commonlink.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Converts a monetary amount to its French written form, as required by Cerfa receipts.
 *
 * Examples:
 *   1250.50 → "Mille deux cent cinquante euros et cinquante centimes"
 *   100.00  → "Cent euros"
 *   1.01    → "Un euro et un centime"
 */
object FrenchAmountWords {

    fun format(amount: BigDecimal): String {
        val scaled = amount.setScale(2, RoundingMode.HALF_UP)
        val totalCents = (scaled * BigDecimal(100)).toLong()
        val euros = (totalCents / 100).toInt()
        val cents = (totalCents % 100).toInt()

        return buildString {
            append(convertPositive(euros).replaceFirstChar { it.uppercase() })
            append(if (euros > 1) " euros" else " euro")
            if (cents > 0) {
                append(" et ")
                append(convertPositive(cents))
                append(if (cents > 1) " centimes" else " centime")
            }
        }
    }

    private val ONES = arrayOf(
        "", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
        "dix-sept", "dix-huit", "dix-neuf",
    )

    private val TENS = arrayOf("", "", "vingt", "trente", "quarante", "cinquante", "soixante")

    fun convertPositive(n: Int): String = when {
        n <= 0   -> "zéro"
        n < 20   -> ONES[n]
        n < 100  -> tens(n)
        n < 1000 -> hundreds(n)
        n < 2000 -> "mille" + suffix(n % 1000)
        n < 1_000_000 -> "${convertPositive(n / 1000)} mille" + suffix(n % 1000)
        else -> "${convertPositive(n / 1_000_000)} million${if (n / 1_000_000 > 1) "s" else ""}" + suffix(n % 1_000_000)
    }

    private fun suffix(rem: Int) = if (rem == 0) "" else " ${convertPositive(rem)}"

    private fun tens(n: Int): String {
        val one = n % 10
        return when (val ten = n / 10) {
            in 2..6 -> if (one == 0) TENS[ten] else "${TENS[ten]}${if (one == 1) " et " else "-"}${ONES[one]}"
            7       -> if (one == 0) "soixante-dix"
                       else if (one == 1) "soixante et onze"
                       else "soixante-${ONES[10 + one]}"
            8       -> if (one == 0) "quatre-vingts" else "quatre-vingt-${ONES[one]}"
            9       -> "quatre-vingt-${ONES[10 + one]}"
            else    -> ""
        }
    }

    private fun hundreds(n: Int): String {
        val h = n / 100
        val rem = n % 100
        val prefix = if (h == 1) "cent" else "${ONES[h]} cent"
        return when {
            rem == 0 && h > 1 -> "${ONES[h]} cents"
            rem == 0           -> "cent"
            else               -> "$prefix ${convertPositive(rem)}"
        }
    }
}
