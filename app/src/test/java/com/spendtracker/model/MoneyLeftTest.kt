package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyLeftTest {

    private val summary = Summary.of(
        listOf(
            testTxn(TransactionType.CREDIT, 50_000, Category.SALARY),
            testTxn(TransactionType.DEBIT, 12_000, Category.RENT),
            testTxn(TransactionType.DEBIT, 3_000, Category.GROCERIES),
            testTxn(TransactionType.DEBIT, 9_000, Category.CARD_BILL),
        ),
    )

    @Test
    fun budgetWinsOverIncome() {
        val left = MoneyLeft.of(summary, budgetPaise = 20_000_00)
        assertEquals(MoneyLeft.Basis.BUDGET, left.basis)
        assertEquals(20_000_00L, left.basePaise)
        assertEquals(5_000_00L, left.leftPaise)
        assertFalse(left.overspent)
    }

    @Test
    fun incomeIsTheBaseWithoutBudget() {
        val left = MoneyLeft.of(summary, budgetPaise = null)
        assertEquals(MoneyLeft.Basis.INCOME, left.basis)
        assertEquals(35_000_00L, left.leftPaise)
    }

    @Test
    fun zeroBudgetIsIgnored() {
        assertEquals(MoneyLeft.Basis.INCOME, MoneyLeft.of(summary, budgetPaise = 0).basis)
    }

    @Test
    fun onlySpendingKnownWithoutIncomeOrBudget() {
        val spendOnly = Summary.of(listOf(testTxn(TransactionType.DEBIT, 800, Category.FUEL)))
        val left = MoneyLeft.of(spendOnly, budgetPaise = null)
        assertEquals(MoneyLeft.Basis.SPENT_ONLY, left.basis)
        assertEquals(800_00L, left.spentPaise)
        assertFalse(left.overspent)
        assertEquals(0f, left.leftShare, 0f)
    }

    @Test
    fun sharesSplitTheBase() {
        val left = MoneyLeft(MoneyLeft.Basis.INCOME, basePaise = 50_000_00, spentPaise = 20_000_00)
        assertEquals(0.24f, left.shareOf(12_000_00), 0.0001f)
        assertEquals(0.6f, left.leftShare, 0.0001f)
    }

    @Test
    fun overspendingFillsTheWholeStrip() {
        val left = MoneyLeft(MoneyLeft.Basis.BUDGET, basePaise = 10_000_00, spentPaise = 12_500_00)
        assertTrue(left.overspent)
        assertEquals(-2_500_00L, left.leftPaise)
        assertEquals(1f, left.shareOf(12_500_00), 0.0001f)
        assertEquals(0.4f, left.shareOf(5_000_00), 0.0001f)
        assertEquals(0f, left.leftShare, 0f)
    }
}
