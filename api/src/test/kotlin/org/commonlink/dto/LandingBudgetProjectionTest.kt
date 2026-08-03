package org.commonlink.dto

import io.mockk.every
import io.mockk.mockk
import org.commonlink.entity.BudgetSide
import org.commonlink.entity.CampaignBudgetItem
import org.commonlink.entity.CampaignBudgetSection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LandingBudgetProjectionTest {

    private fun section(side: BudgetSide, vararg items: Pair<String, String>): CampaignBudgetSection {
        val section = mockk<CampaignBudgetSection>()
        every { section.side } returns side
        val itemList = items.map { (label, amount) ->
            mockk<CampaignBudgetItem>().also {
                every { it.label } returns label
                every { it.amount } returns BigDecimal(amount)
            }
        }
        every { section.items } returns itemList.toMutableList()
        return section
    }

    @Test
    fun `nominal case - EXPENSE items projected with correct percentages sorted by amount desc`() {
        val sections = listOf(
            section(BudgetSide.EXPENSE, "Salaires" to "600.00", "Loyer" to "300.00", "Fournitures" to "100.00"),
            section(BudgetSide.REVENUE, "Subvention" to "500.00"),
        )
        val result = buildBudgetProjection(sections)
        assertEquals(3, result.size)
        assertEquals("Salaires", result[0].label)
        assertEquals(60, result[0].percentage)
        assertEquals("Loyer", result[1].label)
        assertEquals(30, result[1].percentage)
        assertEquals("Fournitures", result[2].label)
        assertEquals(10, result[2].percentage)
    }

    @Test
    fun `total zero - returns empty list`() {
        val sections = listOf(
            section(BudgetSide.EXPENSE, "Item" to "0.00"),
        )
        assertTrue(buildBudgetProjection(sections).isEmpty())
    }

    @Test
    fun `rounding HALF_UP - two thirds rounds to 67, one third rounds to 33`() {
        val sections = listOf(
            section(BudgetSide.EXPENSE, "Major" to "2", "Minor" to "1"),
        )
        val result = buildBudgetProjection(sections)
        assertEquals(2, result.size)
        // 2/3 = 66.66... → HALF_UP → 67
        assertEquals(67, result[0].percentage)
        // 1/3 = 33.33... → HALF_UP → 33
        assertEquals(33, result[1].percentage)
    }

    @Test
    fun `sort order - items sorted by amount descending regardless of input order`() {
        val sections = listOf(
            section(BudgetSide.EXPENSE, "Small" to "10", "Big" to "90"),
        )
        val result = buildBudgetProjection(sections)
        assertEquals("Big", result[0].label)
        assertEquals("Small", result[1].label)
    }
}
