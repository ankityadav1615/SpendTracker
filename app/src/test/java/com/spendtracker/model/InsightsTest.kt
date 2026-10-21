package com.spendtracker.model

import com.spendtracker.data.CategoryBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {

    private fun line(category: Category, change: Change?) =
        ReportLine(category = category, amountPaise = 1000, count = 1, share = 1f, change = change)

    private fun payment(dueInDays: Long, type: TransactionType = TransactionType.DEBIT) = RecurringPayment(
        counterpartyKey = "landlord",
        type = type,
        category = Category.RENT,
        displayName = "Landlord",
        lastAmountPaise = 15_000_00,
        occurrences = 3,
        intervalDays = 30,
        lastTimestamp = 0,
        nextDueMillis = dueInDays * 24 * 60 * 60 * 1000,
    )

    @Test
    fun flagsBillsDueSoonBeforeOtherNudges() {
        val insights = Insights.build(
            reportLines = emptyList(),
            budgetStatuses = emptyList(),
            recurring = listOf(payment(dueInDays = 2)),
            nowMillis = 0,
        )

        assertTrue(insights.single() is UpcomingBill)
    }

    @Test
    fun ignoresBillsFarInTheFuture() {
        val insights = Insights.build(
            reportLines = emptyList(),
            budgetStatuses = emptyList(),
            recurring = listOf(payment(dueInDays = 10)),
            nowMillis = 0,
        )

        assertTrue(insights.isEmpty())
    }

    @Test
    fun ignoresIncomingRecurringPayments() {
        val insights = Insights.build(
            reportLines = emptyList(),
            budgetStatuses = emptyList(),
            recurring = listOf(payment(dueInDays = 1, type = TransactionType.CREDIT)),
            nowMillis = 0,
        )

        assertTrue(insights.isEmpty())
    }

    @Test
    fun flagsOverBudgetCategories() {
        val statuses = Budgets.statuses(
            listOf(CategoryBudget(Category.FOOD, 1_000_00)),
            listOf(CategoryTotal(Category.FOOD, 1_500_00, 3)),
        )

        val insights = Insights.build(emptyList(), statuses, emptyList(), nowMillis = 0)

        val overBudget = insights.single() as OverBudget
        assertEquals(Category.FOOD, overBudget.category)
    }

    @Test
    fun flagsCategorySpikesOfThirtyPercentOrMore() {
        val insights = Insights.build(
            reportLines = listOf(line(Category.SHOPPING, Change.Up(45)), line(Category.FUEL, Change.Up(10))),
            budgetStatuses = emptyList(),
            recurring = emptyList(),
            nowMillis = 0,
        )

        val spike = insights.single() as CategorySpike
        assertEquals(Category.SHOPPING, spike.category)
        assertEquals(45, spike.percent)
    }
}
