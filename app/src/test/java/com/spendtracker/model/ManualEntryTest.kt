package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEntryTest {

    @Test
    fun amountFieldKeepsDigitsAndUpToTwoDecimals() {
        assertEquals("1200.75", ManualEntries.cleanAmountInput("1,200.75"))
        assertEquals("50", ManualEntries.cleanAmountInput(" 5 0 "))
        assertEquals("", ManualEntries.cleanAmountInput(""))
        assertEquals(".", ManualEntries.cleanAmountInput("."))
        assertNull(ManualEntries.cleanAmountInput("12.345"))
        assertNull(ManualEntries.cleanAmountInput("1.2.3"))
        assertNull(ManualEntries.cleanAmountInput("12a"))
        assertNull(ManualEntries.cleanAmountInput("-5"))
    }

    @Test
    fun parsesRupeesToPaise() {
        assertEquals(25_000L, ManualEntries.parseAmount("250"))
        assertEquals(9_950L, ManualEntries.parseAmount("99.5"))
        assertEquals(120_075L, ManualEntries.parseAmount("1,200.75"))
        assertEquals(50L, ManualEntries.parseAmount(".5"))
        assertEquals(1_000L, ManualEntries.parseAmount("10."))
        assertEquals(ManualEntries.MAX_PAISE, ManualEntries.parseAmount("100000000"))
    }

    @Test
    fun rejectsAmountsThatCannotBeSaved() {
        assertNull(ManualEntries.parseAmount(""))
        assertNull(ManualEntries.parseAmount("."))
        assertNull(ManualEntries.parseAmount("0"))
        assertNull(ManualEntries.parseAmount("0.00"))
        assertNull(ManualEntries.parseAmount("12.345"))
        assertNull(ManualEntries.parseAmount("100000000.01"))
        assertNull(ManualEntries.parseAmount("abc"))
    }

    @Test
    fun amountTextRoundTrips() {
        assertEquals("250", ManualEntries.amountText(25_000))
        assertEquals("99.50", ManualEntries.amountText(9_950))
        assertEquals("0.05", ManualEntries.amountText(5))
        for (text in listOf("250", "99.50", "0.05", "1200.75")) {
            assertEquals(text, ManualEntries.amountText(ManualEntries.parseAmount(text)!!))
        }
    }

    @Test
    fun categoriesOfferedByDirection() {
        val spend = ManualEntries.categoriesFor(TransactionType.DEBIT)
        assertTrue(Category.GROCERIES in spend)
        assertTrue(Category.RENT in spend)
        assertFalse(Category.CASH in spend)
        assertTrue(spend.all { it.kind == CategoryKind.SPEND })
        assertEquals(
            listOf(Category.SALARY, Category.REFUND, Category.OTHER_INCOME),
            ManualEntries.categoriesFor(TransactionType.CREDIT),
        )
    }

    @Test
    fun newEntryBecomesAManualTransaction() {
        val entry = ManualEntry(
            type = TransactionType.DEBIT,
            amountPaise = 25_000,
            category = Category.FOOD,
            name = "  Chai stall ",
            timestamp = istMillis(2026, 10, 5, 9),
            method = PaymentMethod.CASH,
            note = "   ",
        )
        val txn = ManualEntries.toTransaction(entry)
        assertEquals(0L, txn.id)
        assertEquals(EntrySource.MANUAL, txn.source)
        assertEquals(PaymentMethod.CASH, txn.method)
        assertEquals("Chai stall", txn.counterparty)
        assertNull(txn.note)
        assertEquals("", txn.counterpartyKey)
        assertNull(txn.bank)
        assertTrue(txn.dedupeKey.startsWith("manual:"))
        assertTrue(ManualEntries.toTransaction(entry).dedupeKey != txn.dedupeKey)
    }

    @Test
    fun editingKeepsIdAndKey() {
        val existing = manualTxn(100, Category.FOOD)
        val entry = ManualEntry(TransactionType.CREDIT, 50_000, Category.OTHER_INCOME, null, 5, null, "Gift")
        val txn = ManualEntries.toTransaction(entry, existing)
        assertEquals(existing.id, txn.id)
        assertEquals(existing.dedupeKey, txn.dedupeKey)
        assertEquals(TransactionType.CREDIT, txn.type)
        assertNull(txn.counterparty)
        assertEquals("Gift", txn.note)
    }
}

class CashLedgerTest {

    private fun withdrawal(rupees: Long, day: Int, month: Int = 10) =
        testTxn(TransactionType.DEBIT, rupees, Category.CASH, istMillis(2026, month, day))

    @Test
    fun cashSpendUsesTheEarlierWithdrawal() {
        val atm = withdrawal(2_000, 1)
        val chai = manualTxn(500, Category.FOOD, istMillis(2026, 10, 2))
        val covered = CashLedger.covered(listOf(atm, chai))
        assertEquals(mapOf(atm.id to 50_000L), covered)
        assertEquals(150_000L, CashLedger.countedPaise(atm, covered))
    }

    @Test
    fun newestWithdrawalIsUsedFirst() {
        val older = withdrawal(1_000, 1)
        val newer = withdrawal(1_000, 5)
        val spend = manualTxn(1_500, Category.GROCERIES, istMillis(2026, 10, 6))
        assertEquals(mapOf(newer.id to 100_000L, older.id to 50_000L), CashLedger.covered(listOf(spend, older, newer)))
    }

    @Test
    fun spendBeyondWithdrawnCashCountsNormally() {
        val atm = withdrawal(500, 1)
        val spend = manualTxn(800, Category.DAILY, istMillis(2026, 10, 2))
        val all = listOf(atm, spend)
        val covered = CashLedger.covered(all)
        assertEquals(mapOf(atm.id to 50_000L), covered)

        val summary = Summary.of(all, covered)
        assertEquals(80_000L, summary.spentPaise)
        assertEquals(listOf(CategoryTotal(Category.DAILY, 80_000, 1)), summary.byCategory)
    }

    @Test
    fun withdrawalsAfterTheSpendOrTooOldAreIgnored() {
        val later = withdrawal(1_000, 10)
        val tooOld = withdrawal(1_000, 1, month = 7)
        val spend = manualTxn(300, Category.FOOD, istMillis(2026, 10, 5))
        assertEquals(emptyMap<Long, Long>(), CashLedger.covered(listOf(later, tooOld, spend)))
    }

    @Test
    fun onlyCashSpendsAddedByHandUseWithdrawals() {
        val atm = withdrawal(1_000, 1)
        val upi = manualTxn(200, Category.FOOD, istMillis(2026, 10, 2), method = PaymentMethod.UPI)
        val sms = testTxn(TransactionType.DEBIT, 200, Category.FOOD, istMillis(2026, 10, 2))
        val income = manualTxn(200, Category.OTHER_INCOME, istMillis(2026, 10, 2), type = TransactionType.CREDIT)
        assertEquals(emptyMap<Long, Long>(), CashLedger.covered(listOf(atm, upi, sms, income)))
        assertEquals(emptyMap<Long, Long>(), CashLedger.covered(listOf(manualTxn(200, Category.FOOD))))
    }

    @Test
    fun summaryCountsWithdrawnCashOnce() {
        val salary = testTxn(TransactionType.CREDIT, 30_000, Category.SALARY, istMillis(2026, 10, 1))
        val atm = withdrawal(2_000, 1)
        val veg = manualTxn(500, Category.GROCERIES, istMillis(2026, 10, 2))
        val auto = manualTxn(300, Category.TRAVEL, istMillis(2026, 10, 3))
        val all = listOf(salary, atm, veg, auto)
        val summary = Summary.of(all, CashLedger.covered(all))
        assertEquals(200_000L, summary.spentPaise)
        assertEquals(2_800_000L, summary.leftPaise)
        assertEquals(
            listOf(
                CategoryTotal(Category.CASH, 120_000, 1),
                CategoryTotal(Category.GROCERIES, 50_000, 1),
                CategoryTotal(Category.TRAVEL, 30_000, 1),
            ),
            summary.byCategory,
        )
    }

    @Test
    fun dayTotalCountsWithdrawnCashOnce() {
        val atm = testTxn(TransactionType.DEBIT, 2_000, Category.CASH, istMillis(2026, 10, 2, 9))
        val chai = manualTxn(500, Category.FOOD, istMillis(2026, 10, 2, 11))
        val all = listOf(chai, atm)
        val day = Activity.byDay(all, IST, CashLedger.covered(all)).single()
        assertEquals(-200_000L, day.netPaise)
    }
}
