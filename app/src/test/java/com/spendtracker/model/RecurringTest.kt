package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTest {

    private val dayMillis = 24L * 60 * 60 * 1000

    @Test
    fun flagsSteadyMonthlyPaymentsAsRecurring() {
        val base = istMillis(2024, 1, 5)
        val txns = listOf(
            testTxn(TransactionType.DEBIT, 15_000, Category.RENT, base, counterparty = "Landlord"),
            testTxn(TransactionType.DEBIT, 15_000, Category.RENT, base + 30 * dayMillis, counterparty = "Landlord"),
            testTxn(TransactionType.DEBIT, 15_000, Category.RENT, base + 60 * dayMillis, counterparty = "Landlord"),
        ).map { it.copy(counterpartyKey = "landlord") }

        val recurring = Recurring.detect(txns)

        val payment = recurring.single()
        assertEquals("landlord", payment.counterpartyKey)
        assertEquals(3, payment.occurrences)
        assertEquals(30L, payment.intervalDays)
        assertEquals(base + 60 * dayMillis + 30 * dayMillis, payment.nextDueMillis)
    }

    @Test
    fun ignoresPayeesWithTooFewOccurrences() {
        val base = istMillis(2024, 1, 5)
        val txns = listOf(
            testTxn(TransactionType.DEBIT, 15_000, Category.RENT, base, counterparty = "Landlord"),
            testTxn(TransactionType.DEBIT, 15_000, Category.RENT, base + 30 * dayMillis, counterparty = "Landlord"),
        ).map { it.copy(counterpartyKey = "landlord") }

        assertTrue(Recurring.detect(txns).isEmpty())
    }

    @Test
    fun ignoresInconsistentAmounts() {
        val base = istMillis(2024, 1, 5)
        val txns = listOf(
            testTxn(TransactionType.DEBIT, 1_000, Category.SHOPPING, base, counterparty = "Store"),
            testTxn(TransactionType.DEBIT, 1_000, Category.SHOPPING, base + 30 * dayMillis, counterparty = "Store"),
            testTxn(TransactionType.DEBIT, 5_000, Category.SHOPPING, base + 60 * dayMillis, counterparty = "Store"),
        ).map { it.copy(counterpartyKey = "store") }

        assertTrue(Recurring.detect(txns).isEmpty())
    }

    @Test
    fun ignoresIrregularIntervals() {
        val base = istMillis(2024, 1, 5)
        val txns = listOf(
            testTxn(TransactionType.DEBIT, 1_000, Category.SHOPPING, base, counterparty = "Store"),
            testTxn(TransactionType.DEBIT, 1_000, Category.SHOPPING, base + 2 * dayMillis, counterparty = "Store"),
            testTxn(TransactionType.DEBIT, 1_000, Category.SHOPPING, base + 45 * dayMillis, counterparty = "Store"),
        ).map { it.copy(counterpartyKey = "store") }

        assertTrue(Recurring.detect(txns).isEmpty())
    }
}
