package com.spendtracker.model

import com.spendtracker.data.Transaction
import java.math.BigDecimal
import java.util.UUID

/** Where a transaction came from. */
enum class EntrySource { SMS, MANUAL }

/** How an entry the user added was paid. */
enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    UPI("UPI"),
    CARD("Card"),
    OTHER("Other"),
}

/** What the add/edit sheet collects. */
data class ManualEntry(
    val type: TransactionType,
    val amountPaise: Long,
    val category: Category,
    val name: String?,
    val timestamp: Long,
    val method: PaymentMethod?,
    val note: String?,
    /** Absolute path to a receipt photo the user attached; null for none. */
    val receiptPath: String? = null,
)

object ManualEntries {

    /** ₹10 crore; anything larger is almost certainly a typo. */
    const val MAX_PAISE = 10_00_00_000_00L

    private val AMOUNT = Regex("""\d{0,10}(\.\d{0,2})?""")

    /** Categories offered when adding an entry by hand. */
    fun categoriesFor(type: TransactionType): List<Category> =
        Category.entries.filter {
            if (type == TransactionType.DEBIT) it.kind == CategoryKind.SPEND && it != Category.CASH else it.kind == CategoryKind.INCOME
        }

    /**
     * Keeps what a user may type into the amount field: digits, one decimal point and at most two decimals.
     * Commas and spaces are dropped. Returns null when the new text should be rejected.
     */
    fun cleanAmountInput(text: String): String? {
        val cleaned = text.filter { it != ',' && !it.isWhitespace() }
        if (cleaned.isEmpty()) return cleaned
        return cleaned.takeIf { it.matches(AMOUNT) }
    }

    /** Rupees as typed ("250", "99.5", "1,200.75") to paise; null when it isn't a usable amount. */
    fun parseAmount(text: String): Long? {
        val cleaned = cleanAmountInput(text.trim())?.takeIf { it.isNotEmpty() && it != "." } ?: return null
        val paise = BigDecimal(cleaned).movePointRight(2).toLong()
        return paise.takeIf { it in 1..MAX_PAISE }
    }

    /** Paise back to the text shown in the amount field when editing: "250", "99.50". */
    fun amountText(paise: Long): String {
        val rupees = paise / 100
        val fraction = paise % 100
        return if (fraction == 0L) rupees.toString() else "$rupees.${fraction.toString().padStart(2, '0')}"
    }

    fun toTransaction(entry: ManualEntry, existing: Transaction? = null): Transaction {
        val name = entry.name?.trim()?.takeIf { it.isNotEmpty() }
        val note = entry.note?.trim()?.takeIf { it.isNotEmpty() }
        return Transaction(
            id = existing?.id ?: 0,
            type = entry.type,
            amountPaise = entry.amountPaise,
            counterparty = name,
            counterpartyKey = "",
            vpa = null,
            accountTail = null,
            bank = null,
            refNo = null,
            balancePaise = null,
            timestamp = entry.timestamp,
            category = entry.category,
            sender = "",
            body = "",
            dedupeKey = existing?.dedupeKey ?: "manual:${UUID.randomUUID()}",
            source = EntrySource.MANUAL,
            method = entry.method,
            note = note,
            receiptPath = entry.receiptPath,
        )
    }
}

/**
 * Cash spends the user adds are paid from cash taken out earlier, and that withdrawal is already counted
 * as spending ("Cash withdrawal"). To count the money once, each cash spend uses up the most recent earlier
 * withdrawal(s) with cash left, within [LOOKBACK_MS]. The used part of a withdrawal is then no longer
 * counted; cash spends beyond what was withdrawn count as normal spending.
 */
object CashLedger {

    const val LOOKBACK_MS = 60L * 24 * 60 * 60 * 1000

    /** Paise of each withdrawal (by transaction id) already accounted for by cash spends added later. */
    fun covered(transactions: List<Transaction>): Map<Long, Long> {
        val withdrawals = transactions
            .filter { it.type == TransactionType.DEBIT && it.category == Category.CASH }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
        if (withdrawals.isEmpty()) return emptyMap()
        val spends = transactions
            .filter { it.isCashSpend() }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
        val remaining = withdrawals.associate { it.id to it.amountPaise }.toMutableMap()
        val used = mutableMapOf<Long, Long>()
        for (spend in spends) {
            var due = spend.amountPaise
            for (withdrawal in withdrawals.asReversed()) {
                if (due == 0L) break
                if (withdrawal.timestamp > spend.timestamp) continue
                if (spend.timestamp - withdrawal.timestamp > LOOKBACK_MS) break
                val left = remaining.getValue(withdrawal.id)
                if (left == 0L) continue
                val take = minOf(left, due)
                remaining[withdrawal.id] = left - take
                used[withdrawal.id] = (used[withdrawal.id] ?: 0L) + take
                due -= take
            }
        }
        return used
    }

    /** The part of [transaction] that still counts, after cash spends paid from it. */
    fun countedPaise(transaction: Transaction, covered: Map<Long, Long>): Long =
        transaction.amountPaise - (covered[transaction.id] ?: 0L)

    private fun Transaction.isCashSpend() =
        source == EntrySource.MANUAL && method == PaymentMethod.CASH && type == TransactionType.DEBIT &&
            category.kind == CategoryKind.SPEND
}
