package org.commonlink.service

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

/**
 * TARGET2 business days, the calendar of euro interbank transfers: every day but Saturdays, Sundays and the six
 * TARGET2 holidays (1 January, Good Friday, Easter Monday, 1 May, 25 and 26 December). Easter is computed, never
 * listed by hand.
 *
 * Dates are read in [ZONE] (`Europe/Paris`), the zone of the association's bank and of the receipt year
 * ([DonationReceiptService]): a HelloAsso payout emitted at 23:30 UTC on a Friday is a Saturday payout.
 */
object BusinessDayCalendar {

    /** Zone in which an instant is turned into a calendar day. */
    val ZONE: ZoneId = ZoneId.of("Europe/Paris")

    /** Whether [date] is a TARGET2 business day. */
    fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && !isHoliday(date)

    /**
     * The day that is [days] business days after [date]: [date] itself when [days] is 0, whether or not it is a
     * business day; otherwise each following business day counts one (a Saturday + 2 is the Tuesday).
     *
     * @throws IllegalArgumentException if [days] is negative.
     */
    fun addBusinessDays(date: LocalDate, days: Int): LocalDate {
        require(days >= 0) { "days must not be negative: $days" }
        var current = date
        var remaining = days
        while (remaining > 0) {
            current = current.plusDays(1)
            if (isBusinessDay(current)) remaining--
        }
        return current
    }

    /** The calendar day of [instant] in [ZONE]. */
    fun dayOf(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()

    /** Whether [date] is one of the six TARGET2 holidays. */
    fun isHoliday(date: LocalDate): Boolean {
        val easter = easterSunday(date.year)
        return (date.month == Month.JANUARY && date.dayOfMonth == 1) ||
            (date.month == Month.MAY && date.dayOfMonth == 1) ||
            (date.month == Month.DECEMBER && (date.dayOfMonth == 25 || date.dayOfMonth == 26)) ||
            date == easter.minusDays(2) ||
            date == easter.plusDays(1)
    }

    /** Easter Sunday of [year] (Gregorian calendar, anonymous algorithm of Meeus/Jones/Butcher). */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
