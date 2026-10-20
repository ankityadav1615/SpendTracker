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
        tail: String? = "1234",
        bank: String? = "HDFC Bank",
    ) = Transaction(
        id = nextId++,
        type = type,
        amountPaise = rupees * 100,
        counterparty = null,
        counterpartyKey = "",
        vpa = null,
        accountTail = tail,
        bank = bank,
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

    /** A balance SMS: [rupees] is the reported balance. */
    private fun balance(bank: String?, tail: String?, timestamp: Long, rupees: Long) =
        txn(TransactionType.DEBIT, 1, Category.DAILY, timestamp, rupees, tail, bank)

    private fun List<AccountBalance>.rows() = map { Triple(it.bank, it.accountTail, it.balancePaise / 100) }

    @Test
    fun balanceWithoutAccountNumberJoinsTheBanksOnlyAccount() {
        val balances = Summary.latestBalances(
            listOf(
                balance("ICICI Bank", "485", timestamp = 1, rupees = 100),
                balance("ICICI Bank", null, timestamp = 2, rupees = 80),
            ),
        )
        assertEquals(listOf(Triple("ICICI Bank", "485", 80L)), balances.rows())
        assertEquals(2L, balances.single().timestamp)
    }

    @Test
    fun shortAndLongAccountNumbersAreOneAccount() {
        val balances = Summary.latestBalances(
            listOf(
                balance("ICICI Bank", "85", timestamp = 3, rupees = 300),
                balance("ICICI Bank", "3485", timestamp = 2, rupees = 200),
                balance("ICICI Bank", "485", timestamp = 1, rupees = 100),
                balance("ICICI Bank", null, timestamp = 0, rupees = 50),
            ),
        )
        assertEquals(listOf(Triple("ICICI Bank", "3485", 300L)), balances.rows())
    }

    @Test
    fun bankNameVariantsAreOneBank() {
        val balances = Summary.latestBalances(
            listOf(
                balance("ICICI", "3485", timestamp = 1, rupees = 100),
                balance(" icici  BANK ", null, timestamp = 2, rupees = 200),
                balance("ICICI Bank", "485", timestamp = 3, rupees = 300),
            ),
        )
        assertEquals(listOf(Triple("ICICI Bank", "3485", 300L)), balances.rows())
    }

    @Test
    fun differentAccountsAtOneBankStaySeparate() {
        val balances = Summary.latestBalances(
            listOf(
                balance("HDFC Bank", "3485", timestamp = 1, rupees = 100),
                balance("HDFC Bank", "1234", timestamp = 2, rupees = 200),
                balance("HDFC Bank", "XX3485", timestamp = 3, rupees = 300),
            ),
        )
        assertEquals(
            listOf(Triple("HDFC Bank", "3485", 300L), Triple("HDFC Bank", "1234", 200L)),
            balances.rows(),
        )
    }

    @Test
    fun differentBanksNeverMerge() {
        val balances = Summary.latestBalances(
            listOf(
                balance("HDFC Bank", "1234", timestamp = 1, rupees = 100),
                balance("ICICI Bank", "1234", timestamp = 2, rupees = 200),
                balance("SBI", null, timestamp = 3, rupees = 300),
                balance("Bank of India", null, timestamp = 4, rupees = 400),
                balance("Indian Bank", null, timestamp = 5, rupees = 500),
                balance(null, "1234", timestamp = 6, rupees = 600),
            ),
        )
        assertEquals(
            listOf(
                Triple(null, "1234", 600L),
                Triple("Indian Bank", null, 500L),
                Triple("Bank of India", null, 400L),
                Triple("SBI", null, 300L),
                Triple("ICICI Bank", "1234", 200L),
                Triple("HDFC Bank", "1234", 100L),
            ),
            balances.rows(),
        )
    }

    @Test
    fun unattributableBalanceKeptOnlyWhileItMayBeCurrent() {
        val accounts = listOf(
            balance("HDFC Bank", "3485", timestamp = 10, rupees = 100),
            balance("HDFC Bank", "1285", timestamp = 20, rupees = 200),
        )

        // Newer than one account's balance: it may be that account's latest, so it gets its own row.
        val newer = Summary.latestBalances(accounts + balance("HDFC Bank", null, timestamp = 15, rupees = 150))
        assertEquals(
            listOf(Triple("HDFC Bank", "1285", 200L), Triple("HDFC Bank", null, 150L), Triple("HDFC Bank", "3485", 100L)),
            newer.rows(),
        )

        // Older than every account's balance: whichever account it was, it is outdated.
        val older = Summary.latestBalances(accounts + balance("HDFC Bank", null, timestamp = 5, rupees = 50))
        assertEquals(listOf("1285", "3485"), older.map { it.accountTail })

        // "85" fits both accounts, so it is treated the same way.
        val ambiguous = Summary.latestBalances(accounts + balance("HDFC Bank", "85", timestamp = 25, rupees = 250))
        assertEquals(listOf("85", "1285", "3485"), ambiguous.map { it.accountTail })
        val ambiguousOld = Summary.latestBalances(accounts + balance("HDFC Bank", "85", timestamp = 9, rupees = 90))
        assertEquals(listOf("1285", "3485"), ambiguousOld.map { it.accountTail })
    }
}
