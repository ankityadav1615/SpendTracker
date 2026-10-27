package com.spendtracker.model

import com.spendtracker.data.Transaction
import com.spendtracker.data.categoryLabel
import com.spendtracker.data.customName
import com.spendtracker.data.displayName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The chips on the Activity screen. */
sealed interface ActivityFilter {
    fun matches(transaction: Transaction): Boolean

    /** Stable text form, for saving the selection across configuration changes. */
    val key: String
        get() = when (this) {
            All -> "all"
            MoneyOut -> "out"
            MoneyIn -> "in"
            is Of -> "category:" + category.name
            is Custom -> "custom:" + name
        }

    val label: String
        get() = when (this) {
            All -> "All"
            MoneyOut -> "Money out"
            MoneyIn -> "Money in"
            is Of -> category.label
            is Custom -> name
        }

    companion object {
        fun fromKey(key: String?): ActivityFilter = when {
            key == MoneyOut.key -> MoneyOut
            key == MoneyIn.key -> MoneyIn
            key != null && key.startsWith("category:") ->
                Category.entries.firstOrNull { it.name == key.removePrefix("category:") }?.let(::Of) ?: All
            key != null && key.startsWith("custom:") ->
                key.removePrefix("custom:").takeIf { it.isNotBlank() }?.let(::Custom) ?: All
            else -> All
        }

        /** The chip for a category row: [label] is the user's own category name when it differs from [category]'s. */
        fun forCategory(category: Category, label: String): ActivityFilter =
            if (label != category.label && label.isNotBlank()) Custom(label) else Of(category)
    }

    data object All : ActivityFilter {
        override fun matches(transaction: Transaction) = true
    }

    data object MoneyOut : ActivityFilter {
        override fun matches(transaction: Transaction) = transaction.type == TransactionType.DEBIT
    }

    data object MoneyIn : ActivityFilter {
        override fun matches(transaction: Transaction) = transaction.type == TransactionType.CREDIT
    }

    /** A built-in category; payments moved to one of the user's own categories are left out. */
    data class Of(val category: Category) : ActivityFilter {
        override fun matches(transaction: Transaction) = transaction.category == category && transaction.customName == null
    }

    /** One of the user's own categories. */
    data class Custom(val name: String) : ActivityFilter {
        override fun matches(transaction: Transaction) = transaction.customName == name
    }
}

data class DayGroup(
    val date: LocalDate,
    /** Money in minus money out for the day; own-account moves and card bills are left out. */
    val netPaise: Long,
    /** False when every entry of the day is an own-account move or card bill. */
    val hasCounted: Boolean,
    val transactions: List<Transaction>,
)

object Activity {

    private val WEEKDAY_DAY_MONTH = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val WEEKDAY_DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    /** The filter chips for a period: the fixed ones, then each category present, largest total first. */
    fun filtersFor(transactions: List<Transaction>): List<ActivityFilter> =
        listOf(ActivityFilter.All, ActivityFilter.MoneyOut, ActivityFilter.MoneyIn) +
            transactions.groupBy { txn -> txn.customName?.let { ActivityFilter.Custom(it) } ?: ActivityFilter.Of(txn.category) }
                .entries
                .sortedByDescending { (_, list) -> list.sumOf { it.amountPaise } }
                .map { it.key }

    /** Case-insensitive match against the payee, UPI ID, category, bank and account number. */
    fun matchesQuery(transaction: Transaction, query: String): Boolean {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = listOfNotNull(
            transaction.displayName,
            transaction.counterparty,
            transaction.vpa,
            transaction.categoryLabel,
            transaction.bank,
            transaction.accountTail,
            transaction.note,
        ).joinToString(" ").lowercase(Locale.ROOT)
        return words.all { it in haystack }
    }

    /** Groups transactions (newest first) by day, keeping the order. [cashCovered] is from [CashLedger.covered]. */
    fun byDay(
        transactions: List<Transaction>,
        zone: ZoneId = ZoneId.systemDefault(),
        cashCovered: Map<Long, Long> = emptyMap(),
    ): List<DayGroup> =
        transactions
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                val counted = list.filter { it.category.kind != CategoryKind.EXCLUDED }
                DayGroup(
                    date = date,
                    netPaise = counted.sumOf {
                        if (it.type == TransactionType.CREDIT) it.amountPaise else -CashLedger.countedPaise(it, cashCovered)
                    },
                    hasCounted = counted.isNotEmpty(),
                    transactions = list,
                )
            }

    /** "Today, 20 Oct", "Yesterday, 19 Oct", "Thu, 1 Oct" or, in another year, "Tue, 30 Dec 2025". */
    fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today, " + DateRanges.date(date, today)
        today.minusDays(1) -> "Yesterday, " + DateRanges.date(date, today)
        else -> date.format(if (date.year == today.year) WEEKDAY_DAY_MONTH else WEEKDAY_DAY_MONTH_YEAR)
    }
}
