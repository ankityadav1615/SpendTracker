package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsGoalTest {

    @Test
    fun sumsInvestmentSpendsSinceTheGoalStartedAsProgress() {
        val start = istMillis(2024, 1, 1)
        val txns = listOf(
            testTxn(TransactionType.DEBIT, 5_000, Category.INVESTMENTS, istMillis(2023, 12, 20)), // before start
            testTxn(TransactionType.DEBIT, 3_000, Category.INVESTMENTS, istMillis(2024, 1, 10)),
            testTxn(TransactionType.DEBIT, 2_000, Category.INVESTMENTS, istMillis(2024, 1, 20)),
            testTxn(TransactionType.DEBIT, 1_000, Category.FOOD, istMillis(2024, 1, 15)), // wrong category
        )

        val status = SavingsGoals.status("Laptop", 10_000_00, null, start, txns)

        assertEquals(5_000_00L, status.savedPaise)
        assertEquals(50, status.percent)
        assertFalse(status.reached)
        assertEquals(5_000_00L, status.remainingPaise)
    }

    @Test
    fun ignoresHiddenTransactionsAndCapsPercentAt100() {
        val start = istMillis(2024, 1, 1)
        val txns = listOf(
            testTxn(TransactionType.DEBIT, 20_000, Category.INVESTMENTS, istMillis(2024, 1, 10)).copy(ignored = true),
            testTxn(TransactionType.DEBIT, 12_000, Category.INVESTMENTS, istMillis(2024, 1, 20)),
        )

        val status = SavingsGoals.status("Emergency fund", 10_000_00, null, start, txns)

        assertEquals(12_000_00L, status.savedPaise)
        assertEquals(100, status.percent)
        assertTrue(status.reached)
        assertEquals(0L, status.remainingPaise)
    }
}
