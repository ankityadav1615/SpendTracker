package com.spendtracker.model

import com.spendtracker.data.Transaction
import com.spendtracker.data.categoryLabel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Which side of the money the Reports screen shows. */
enum class ReportSide { EXPENSES, INCOME }

/** How a category's total moved against the period it is compared with. */
sealed interface Change {
    data object Same : Change
    data object New : Change
    data class Up(val percent: Int) : Change
    data class Down(val percent: Int) : Change
}

data class ReportLine(
    val category: Category,
    val amountPaise: Long,
    val count: Int,
    /** Share of the side's total, 0..1. */
    val share: Float,
    /** Null when there is nothing to compare with. */
    val change: Change?,
    val label: String = category.label,
) {
    /** True for one of the user's own categories, stored on top of [category]. */
    val isCustom: Boolean get() = label != category.label

    val customName: String? get() = label.takeIf { isCustom }
}

enum class TrendUnit(val noun: String) { DAY("day"), WEEK("week"), MONTH("month") }

/** One bar of the trend chart: the money of [first]..[last] (inclusive). */
data class TrendBar(val first: LocalDate, val last: LocalDate, val amountPaise: Long)

data class Trend(val unit: TrendUnit, val bars: List<TrendBar>, val days: Int)

data class Report(
    val side: ReportSide,
    val totalPaise: Long,
    val count: Int,
    /** Largest first. */
    val lines: List<ReportLine>,
    val trend: Trend,
)

/** The period a report is compared with, cut to the same number of days while the current one is running. */
data class Comparison(val period: Period, val first: LocalDate, val last: LocalDate)

object Reports {

    /**
     * What [transaction] adds to [side]: the counted part of money out for expenses (see [CashLedger]), money
     * in for income. Own-account moves and card bills count on neither side.
     */
    fun amountFor(side: ReportSide, transaction: Transaction, cashCovered: Map<Long, Long>): Long = when {
        transaction.category.kind == CategoryKind.EXCLUDED -> 0
        side == ReportSide.EXPENSES && transaction.type == TransactionType.DEBIT ->
            CashLedger.countedPaise(transaction, cashCovered).coerceAtLeast(0)
        side == ReportSide.INCOME && transaction.type == TransactionType.CREDIT -> transaction.amountPaise
        else -> 0
    }

    /** Category totals of [transactions] for [side], largest first, with shares of the total. */
    fun lines(
        side: ReportSide,
        transactions: List<Transaction>,
        cashCovered: Map<Long, Long>,
        previous: List<Transaction>? = null,
    ): List<ReportLine> {
        val totals = totals(side, transactions, cashCovered)
        val total = totals.values.sumOf { it.first }
        val before = previous?.let { totals(side, it, cashCovered) }
        return totals.entries
            .sortedWith(
                compareByDescending<Map.Entry<Pair<Category, String>, Pair<Long, Int>>> { it.value.first }
                    .thenBy { it.key.second.lowercase() },
            )
            .map { (key, value) ->
                val category = key.first
                ReportLine(
                    category = category,
                    label = key.second,
                    amountPaise = value.first,
                    count = value.second,
                    share = if (total > 0) (value.first.toDouble() / total).toFloat() else 0f,
                    change = before?.let { change(value.first, it[key]?.first ?: 0L) },
                )
            }
    }

    fun build(
        side: ReportSide,
        transactions: List<Transaction>,
        cashCovered: Map<Long, Long>,
        first: LocalDate,
        last: LocalDate,
        previous: List<Transaction>? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Report {
        val lines = lines(side, transactions, cashCovered, previous)
        return Report(
            side = side,
            totalPaise = lines.sumOf { it.amountPaise },
            count = lines.sumOf { it.count },
            lines = lines,
            trend = trend(side, transactions, cashCovered, first, last, zone),
        )
    }

    /** "Same" within half a percent, "New" when nothing was there before. */
    fun change(current: Long, previous: Long): Change {
        if (previous <= 0) return if (current > 0) Change.New else Change.Same
        val percent = ((current - previous) * 100.0 / previous).roundToInt()
        return when {
            percent > 0 -> Change.Up(percent)
            percent < 0 -> Change.Down(-percent)
            else -> Change.Same
        }
    }

    /**
     * The period before [current]. While [current] is still running, the previous one is cut to the same
     * number of days, so "1–20 Oct" is compared with "1–20 Sep". Null when there is no earlier period with a
     * fixed start.
     */
    fun comparison(
        current: ResolvedPeriod,
        previous: ResolvedPeriod?,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Comparison? {
        val currentFirst = current.firstDate ?: return null
        val previousFirst = previous?.firstDate ?: return null
        if (previous.period.start == Long.MIN_VALUE || previousFirst >= currentFirst) return null
        if (current.period.end != Long.MAX_VALUE) {
            return Comparison(previous.period, previousFirst, previous.lastDate)
        }
        val days = ChronoUnit.DAYS.between(currentFirst, today) + 1
        val last = minOf(previous.lastDate, previousFirst.plusDays(days - 1))
        val end = minOf(previous.period.end, last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        return Comparison(previous.period.copy(end = end), previousFirst, last)
    }

    /**
     * Money over time: a bar per day for up to a month, per 7 days (counted from [first]) for up to 3
     * months, per calendar month beyond that.
     */
    fun trend(
        side: ReportSide,
        transactions: List<Transaction>,
        cashCovered: Map<Long, Long>,
        first: LocalDate,
        last: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Trend {
        val start = minOf(first, last)
        val end = maxOf(first, last)
        val days = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        val unit = when {
            days <= 31 -> TrendUnit.DAY
            days <= 91 -> TrendUnit.WEEK
            else -> TrendUnit.MONTH
        }
        val buckets: List<Pair<LocalDate, LocalDate>> = when (unit) {
            TrendUnit.DAY -> (0 until days).map { start.plusDays(it.toLong()).let { d -> d to d } }
            TrendUnit.WEEK -> (0 until days step 7).map { offset ->
                start.plusDays(offset.toLong()) to minOf(end, start.plusDays(offset + 6L))
            }
            TrendUnit.MONTH -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)).toInt() + 1
                (0 until months).map { index ->
                    val month = YearMonth.from(start).plusMonths(index.toLong())
                    maxOf(start, month.atDay(1)) to minOf(end, month.atEndOfMonth())
                }
            }
        }
        val amounts = LongArray(buckets.size)
        for (txn in transactions) {
            val amount = amountFor(side, txn, cashCovered)
            if (amount == 0L) continue
            val date = Instant.ofEpochMilli(txn.timestamp).atZone(zone).toLocalDate()
            if (date < start || date > end) continue
            val index = when (unit) {
                TrendUnit.DAY -> ChronoUnit.DAYS.between(start, date).toInt()
                TrendUnit.WEEK -> ChronoUnit.DAYS.between(start, date).toInt() / 7
                TrendUnit.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(date)).toInt()
            }
            amounts[index] += amount
        }
        return Trend(unit, buckets.mapIndexed { i, (a, b) -> TrendBar(a, b, amounts[i]) }, days)
    }

    private fun totals(
        side: ReportSide,
        transactions: List<Transaction>,
        cashCovered: Map<Long, Long>,
    ): Map<Pair<Category, String>, Pair<Long, Int>> {
        val totals = linkedMapOf<Pair<Category, String>, Pair<Long, Int>>()
        for (txn in transactions) {
            val amount = amountFor(side, txn, cashCovered)
            if (amount <= 0) continue
            val key = txn.category to txn.categoryLabel
            val (sum, count) = totals[key] ?: (0L to 0)
            totals[key] = (sum + amount) to (count + 1)
        }
        return totals
    }
}
