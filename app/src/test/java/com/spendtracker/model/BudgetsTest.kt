package com.spendtracker.model

import com.spendtracker.data.CategoryBudget
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetsTest {

    @Test
    fun matchesBudgetAgainstSpendingAndSortsWorstOverspendFirst() {
        val totals = listOf(
            CategoryTotal(Category.FOOD, 6_000_00, 10),
            CategoryTotal(Category.GROCERIES, 4_000_00, 5),
            CategoryTotal(Category.FUEL, 1_000_00, 2),
        )
        val budgets = listOf(
            CategoryBudget(Category.FOOD, 5_000_00),
            CategoryBudget(Category.GROCERIES, 10_000_00),
            CategoryBudget(Category.TRAVEL, 2_000_00),
        )

        val statuses = Budgets.statuses(budgets, totals)

        assertEquals(listOf(Category.FOOD, Category.GROCERIES, Category.TRAVEL), statuses.map { it.category })
        assertEquals(true, statuses.first { it.category == Category.FOOD }.overBudget)
        assertEquals(1_000_00L, statuses.first { it.category == Category.FOOD }.remainingPaise.let { -it })
        assertEquals(0L, statuses.first { it.category == Category.TRAVEL }.spentPaise)
        assertEquals(0.4f, statuses.first { it.category == Category.GROCERIES }.share)
    }

    @Test
    fun categoryWithoutSpendingHasZeroSpentAndShare() {
        val statuses = Budgets.statuses(listOf(CategoryBudget(Category.ENTERTAINMENT, 1_500_00)), emptyList())

        val status = statuses.single()
        assertEquals(0L, status.spentPaise)
        assertEquals(0f, status.share)
        assertEquals(false, status.overBudget)
    }
}
