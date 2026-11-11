package org.commonlink.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate

class BusinessDayCalendarTest {

    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun `Saturdays and Sundays are not business days`() {
        assertFalse(BusinessDayCalendar.isBusinessDay(d("2026-11-14")))
        assertFalse(BusinessDayCalendar.isBusinessDay(d("2026-11-15")))
        assertTrue(BusinessDayCalendar.isBusinessDay(d("2026-11-13")))
        assertTrue(BusinessDayCalendar.isBusinessDay(d("2026-11-16")))
    }

    @Test
    fun `each of the six TARGET2 holidays is closed`() {
        // 2026: Easter Sunday 5 April.
        listOf("2026-01-01", "2026-04-03", "2026-04-06", "2026-05-01", "2026-12-25", "2026-12-26").forEach {
            assertTrue(BusinessDayCalendar.isHoliday(d(it)), it)
            assertFalse(BusinessDayCalendar.isBusinessDay(d(it)), it)
        }
    }

    @Test
    fun `French public holidays that are not TARGET2 holidays stay open`() {
        // 8 May, Ascension, 14 July, 15 August, 1 and 11 November (all weekdays in 2026 but 15 August, a Saturday).
        listOf("2026-05-08", "2026-05-14", "2026-07-14", "2026-11-11").forEach {
            assertTrue(BusinessDayCalendar.isBusinessDay(d(it)), it)
        }
    }

    @Test
    fun `Easter is computed for any year`() {
        assertEquals(d("2025-04-20"), BusinessDayCalendar.easterSunday(2025))
        assertEquals(d("2026-04-05"), BusinessDayCalendar.easterSunday(2026))
        assertEquals(d("2027-03-28"), BusinessDayCalendar.easterSunday(2027))
        assertEquals(d("2038-04-25"), BusinessDayCalendar.easterSunday(2038))
        // Good Friday and Easter Monday follow it.
        assertTrue(BusinessDayCalendar.isHoliday(d("2027-03-26")))
        assertTrue(BusinessDayCalendar.isHoliday(d("2027-03-29")))
        assertTrue(BusinessDayCalendar.isHoliday(d("2025-04-18")))
        assertTrue(BusinessDayCalendar.isHoliday(d("2025-04-21")))
    }

    @Test
    fun `adding business days skips weekends and holidays`() {
        assertEquals(d("2026-11-17"), BusinessDayCalendar.addBusinessDays(d("2026-11-13"), 2)) // Fri + 2 = Tue
        assertEquals(d("2026-11-17"), BusinessDayCalendar.addBusinessDays(d("2026-11-14"), 2)) // Sat + 2 = Tue
        assertEquals(d("2026-04-08"), BusinessDayCalendar.addBusinessDays(d("2026-04-02"), 2)) // over Easter
        assertEquals(d("2026-12-29"), BusinessDayCalendar.addBusinessDays(d("2026-12-24"), 2)) // over Christmas
        assertEquals(d("2026-11-20"), BusinessDayCalendar.addBusinessDays(d("2026-11-13"), 5))
    }

    @Test
    fun `adding business days crosses the year`() {
        // Fri 31/12/2027, 1/1/2028 a Saturday: + 2 = Tue 4/1/2028.
        assertEquals(d("2028-01-04"), BusinessDayCalendar.addBusinessDays(d("2027-12-31"), 2))
        // Thu 31/12/2026, 1/1/2027 a holiday then a weekend: + 2 = Tue 5/1/2027.
        assertEquals(d("2027-01-05"), BusinessDayCalendar.addBusinessDays(d("2026-12-31"), 2))
    }

    @Test
    fun `adding zero business days returns the same day, a negative count is refused`() {
        assertEquals(d("2026-11-14"), BusinessDayCalendar.addBusinessDays(d("2026-11-14"), 0))
        assertEquals(d("2026-11-13"), BusinessDayCalendar.addBusinessDays(d("2026-11-13"), 0))
        assertThrows<IllegalArgumentException> { BusinessDayCalendar.addBusinessDays(d("2026-11-13"), -1) }
    }

    @Test
    fun `an instant is read in Paris time`() {
        // 23:30 UTC on Friday 13/11/2026 is 00:30 on Saturday in Paris.
        assertEquals(d("2026-11-14"), BusinessDayCalendar.dayOf(Instant.parse("2026-11-13T23:30:00Z")))
        assertEquals(d("2026-11-13"), BusinessDayCalendar.dayOf(Instant.parse("2026-11-13T22:30:00Z")))
    }
}
