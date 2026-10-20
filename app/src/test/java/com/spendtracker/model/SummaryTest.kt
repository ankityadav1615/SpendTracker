package com.spendtracker.model

import com.spendtracker.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTest {

    private var nextId = 1L

    private fun txn(
        type: TransactionType,
        rupees: Long,
        category: Category,
        timestamp: Long = 0,
        balanceRupees: Long? = null,
        tail: String = "1234",
    ) = Transaction(
        id = nextId++,
        type = type,
        amountPaise = rupees * 100,
        counterparty = null,
        counterpartyKey = "",
        vpa = null,
        accountTail = tail,
        bank = "HDFC Bank",
        refNo = null,
        balancePaise = balanceRupees?.times(100),
        timestamp = timestamp,
        category = category,
        sender = "VM-HDFCBK",
        body = "",
        dedupeKey = "k$nextId",
    )

    @Test
    fun salaryMinusSpendingByCategory() {
        val summary = Summary.of(
            listOf(
                txn(TransactionType.CREDIT, 50_000, Category.SALARY),
                txn(TransactionType.CREDIT, 500, Category.REFUND),
                txn(TransactionType.DEBIT, 12_000, Category.EMI),
                txn(TransactionType.DEBIT, 15_000, Category.RENT),
                txn(TransactionType.DEBIT, 1_200, Category.GROCERIES),
                txn(TransactionType.DEBIT, 800, Category.GROCERIES),
                txn(TransactionType.DEBIT, 2_500, Category.FUEL),
                txn(TransactionType.DEBIT, 9_000, Category.CARD_BILL),
                txn(TransactionType.CREDIT, 3_000, Category.SELF_TRANSFER),
            ),
        )
        assertEquals(50_000_00L, summary.salaryPaise)
        assertEquals(500_00L, summary.otherIncomePaise)
        assertEquals(31_500_00L, summary.spentPaise)
        assertEquals(12_000_00L, summary.excludedPaise)
        assertEquals(19_000_00L, summary.leftPaise)
        assertEquals(9, summary.count)
        assertEquals(
            listOf(Category.RENT, Category.EMI, Category.FUEL, Category.GROCERIES),
            summary.byCategory.map { it.category },
        )
        val groceries = summary.byCategory.first { it.category == Category.GROCERIES }
        assertEquals(2_000_00L, groceries.amountPaise)
        assertEquals(2, groceries.count)
    }

    @Test
    fun overspendingGoesNegative() {
        val summary = Summary.of(
            listOf(
                txn(TransactionType.CREDIT, 1_000, Category.SALARY),
                txn(TransactionType.DEBIT, 1_500, Category.DAILY),
            ),
        )
        assertEquals(-500_00L, summary.leftPaise)
    }

    @Test
    fun latestBalancePerAccount() {
        val balances = Summary.latestBalances(
            listOf(
                txn(TransactionType.DEBIT, 1, Category.DAILY, timestamp = 1, balanceRupees = 100),
                txn(TransactionType.DEBIT, 1, Category.DAILY, timestamp = 3, balanceRupees = 90),
                txn(TransactionType.DEBIT, 1, Category.DAILY, timestamp = 2, balanceRupees = 500, tail = "9999"),
                txn(TransactionType.DEBIT, 1, Category.DAILY, timestamp = 4),
            ),
        )
        assertEquals(listOf("1234" to 90_00L, "9999" to 500_00L), balances.map { it.accountTail to it.balancePaise })
    }
}
