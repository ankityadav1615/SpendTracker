package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ActivityTest {

    @Test
    fun filterKeysRoundTrip() {
        listOf(ActivityFilter.All, ActivityFilter.MoneyOut, ActivityFilter.MoneyIn, ActivityFilter.Of(Category.FUEL))
            .forEach { assertEquals(it, ActivityFilter.fromKey(it.key)) }
        assertEquals(ActivityFilter.All, ActivityFilter.fromKey(null))
        assertEquals(ActivityFilter.All, ActivityFilter.fromKey("category:NOT_A_CATEGORY"))
    }

    @Test
    fun categoryChipsAreOrderedByTotal() {
        val filters = Activity.filtersFor(
            listOf(
                testTxn(TransactionType.DEBIT, 2_500, Category.FUEL),
                testTxn(TransactionType.DEBIT, 800, Category.GROCERIES),
                testTxn(TransactionType.DEBIT, 15_000, Category.RENT),
                testTxn(TransactionType.DEBIT, 1_900, Category.GROCERIES),
            ),
        )
        assertEquals(
            listOf(
                ActivityFilter.All,
                ActivityFilter.MoneyOut,
                ActivityFilter.MoneyIn,
                ActivityFilter.Of(Category.RENT),
                ActivityFilter.Of(Category.GROCERIES),
                ActivityFilter.Of(Category.FUEL),
            ),
            filters,
        )
    }

    @Test
    fun searchMatchesEveryWordAnywhere() {
        val txn = testTxn(
            TransactionType.DEBIT,
            450,
            Category.FOOD,
            counterparty = "Swiggy",
            vpa = "swiggy@icici",
            bank = "ICICI Bank",
            tail = "3485",
        )
        assertTrue(Activity.matchesQuery(txn, ""))
        assertTrue(Activity.matchesQuery(txn, "  "))
        assertTrue(Activity.matchesQuery(txn, "SWIG"))
        assertTrue(Activity.matchesQuery(txn, "food icici"))
        assertTrue(Activity.matchesQuery(txn, "3485"))
        assertFalse(Activity.matchesQuery(txn, "zomato"))
        assertFalse(Activity.matchesQuery(txn, "swiggy hdfc"))
    }

    @Test
    fun daysKeepOrderAndLeaveOutOwnMoves() {
        val groups = Activity.byDay(
            listOf(
                testTxn(TransactionType.CREDIT, 1_000, Category.OTHER_INCOME, istMillis(2026, 10, 20, 18)),
                testTxn(TransactionType.DEBIT, 300, Category.FOOD, istMillis(2026, 10, 20, 9)),
                testTxn(TransactionType.DEBIT, 5_000, Category.SELF_TRANSFER, istMillis(2026, 10, 19, 23, 30)),
            ),
            IST,
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 19)), groups.map { it.date })
        assertEquals(700_00L, groups[0].netPaise)
        assertTrue(groups[0].hasCounted)
        assertEquals(2, groups[0].transactions.size)
        assertEquals(0L, groups[1].netPaise)
        assertFalse(groups[1].hasCounted)
    }

    @Test
    fun dayTitles() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals("Today, 20 Oct", Activity.dayTitle(today, today))
        assertEquals("Yesterday, 19 Oct", Activity.dayTitle(LocalDate.of(2026, 10, 19), today))
        assertEquals("Thu, 1 Oct", Activity.dayTitle(LocalDate.of(2026, 10, 1), today))
        assertEquals("Tue, 30 Dec 2025", Activity.dayTitle(LocalDate.of(2025, 12, 30), today))
    }
}
