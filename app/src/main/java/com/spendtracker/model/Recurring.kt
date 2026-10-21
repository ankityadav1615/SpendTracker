package com.spendtracker.model

import com.spendtracker.data.Transaction
import kotlin.math.abs

/** A payment that repeats for the same payee on a steady cadence and for a steady amount, e.g. rent or an EMI. */
data class RecurringPayment(
    val counterpartyKey: String,
    val type: TransactionType,
    val category: Category,
    val displayName: String,
    val lastAmountPaise: Long,
    val occurrences: Int,
    val intervalDays: Long,
    val lastTimestamp: Long,
    /** Projected date of the next occurrence, based on the median interval seen so far. */
    val nextDueMillis: Long,
)

object Recurring {
    private const val MIN_OCCURRENCES = 3

    /** Below this, two transactions days apart are probably coincidence, not a bill cycle. */
    private const val MIN_INTERVAL_DAYS = 6L

    /** Above this, the interval no longer reads as "regular" (loans are usually monthly at most). */
    private const val MAX_INTERVAL_DAYS = 40L
    private const val INTERVAL_TOLERANCE = 0.25
    private const val AMOUNT_TOLERANCE = 0.2
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** Groups [transactions] by payee and flags the ones that repeat on a steady cadence with a steady amount. */
    fun detect(transactions: List<Transaction>): List<RecurringPayment> =
        transactions
            .filter { !it.ignored && it.counterpartyKey.isNotEmpty() && it.category.kind != CategoryKind.EXCLUDED }
            .groupBy { it.type to it.counterpartyKey }
            .mapNotNull { (_, group) -> analyze(group) }
            .sortedBy { it.nextDueMillis }

    private fun analyze(group: List<Transaction>): RecurringPayment? {
        if (group.size < MIN_OCCURRENCES) return null
        val sorted = group.sortedBy { it.timestamp }
        val intervalsDays = sorted.zipWithNext { a, b -> (b.timestamp - a.timestamp) / DAY_MILLIS.toDouble() }
        if (intervalsDays.any { it <= 0.0 }) return null

        val medianInterval = intervalsDays.sorted()[intervalsDays.size / 2]
        if (medianInterval < MIN_INTERVAL_DAYS || medianInterval > MAX_INTERVAL_DAYS) return null
        if (intervalsDays.any { abs(it - medianInterval) > medianInterval * INTERVAL_TOLERANCE }) return null

        val amounts = sorted.map { it.amountPaise }
        val medianAmount = amounts.sorted()[amounts.size / 2]
        if (medianAmount <= 0) return null
        if (amounts.any { abs(it - medianAmount) > medianAmount * AMOUNT_TOLERANCE }) return null

        val latest = sorted.last()
        return RecurringPayment(
            counterpartyKey = latest.counterpartyKey,
            type = latest.type,
            category = latest.category,
            displayName = latest.counterparty?.takeIf { it.isNotBlank() } ?: latest.vpa ?: "Unknown payee",
            lastAmountPaise = latest.amountPaise,
            occurrences = sorted.size,
            intervalDays = medianInterval.toLong(),
            lastTimestamp = latest.timestamp,
            nextDueMillis = latest.timestamp + (medianInterval * DAY_MILLIS).toLong(),
        )
    }
}
