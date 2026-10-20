package com.spendtracker.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PeriodKind { SALARY_CYCLE, BEFORE_SALARY, MONTH }

/** A time window [start, end) the dashboard summarizes. */
data class Period(val start: Long, val end: Long, val kind: PeriodKind, val title: String) {
    operator fun contains(timestamp: Long) = timestamp >= start && timestamp < end

    val subtitle: String
        get() = when (kind) {
            PeriodKind.SALARY_CYCLE -> if (end == Long.MAX_VALUE) "Current salary cycle" else "Salary cycle"
            PeriodKind.BEFORE_SALARY -> "Before the first salary found"
            PeriodKind.MONTH -> "No salary credit found yet, showing month"
        }
}

object Periods {

    /** Salary credits this close together (e.g. salary paid in two parts) start a single cycle. */
    const val SALARY_MERGE_WINDOW_MS = 10L * 24 * 60 * 60 * 1000

    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /**
     * Splits history into salary cycles (each salary credit until the next one). Without any salary
     * credit, calendar months are used instead. Periods are returned oldest first; the last one is open.
     */
    fun compute(
        salaryTimestamps: List<Long>,
        earliestTimestamp: Long?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Period> {
        val starts = salaryCycleStarts(salaryTimestamps)
        if (starts.isEmpty()) return months(minOf(earliestTimestamp ?: now, now), now, zone)

        val periods = mutableListOf<Period>()
        if (earliestTimestamp != null && earliestTimestamp < starts.first()) {
            val title = "Until " + format(starts.first() - 1, DAY_MONTH_YEAR, zone)
            periods += Period(Long.MIN_VALUE, starts.first(), PeriodKind.BEFORE_SALARY, title)
        }
        starts.forEachIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: Long.MAX_VALUE
            periods += Period(start, end, PeriodKind.SALARY_CYCLE, rangeTitle(start, end, zone))
        }
        return periods
    }

    fun salaryCycleStarts(salaryTimestamps: List<Long>): List<Long> {
        val starts = mutableListOf<Long>()
        for (timestamp in salaryTimestamps.sorted()) {
            if (starts.isEmpty() || timestamp - starts.last() >= SALARY_MERGE_WINDOW_MS) starts += timestamp
        }
        return starts
    }

    private fun months(from: Long, now: Long, zone: ZoneId): List<Period> {
        var month = YearMonth.from(Instant.ofEpochMilli(from).atZone(zone))
        val last = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val periods = mutableListOf<Period>()
        while (month <= last) {
            val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = if (month == last) {
                Long.MAX_VALUE
            } else {
                month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            periods += Period(start, end, PeriodKind.MONTH, month.format(MONTH_YEAR))
            month = month.plusMonths(1)
        }
        return periods
    }

    private fun rangeTitle(start: Long, end: Long, zone: ZoneId): String {
        if (end == Long.MAX_VALUE) return format(start, DAY_MONTH_YEAR, zone) + " – today"
        val sameYear = Instant.ofEpochMilli(start).atZone(zone).year == Instant.ofEpochMilli(end - 1).atZone(zone).year
        val from = format(start, if (sameYear) DAY_MONTH else DAY_MONTH_YEAR, zone)
        return from + " – " + format(end - 1, DAY_MONTH_YEAR, zone)
    }

    private fun format(timestamp: Long, formatter: DateTimeFormatter, zone: ZoneId) =
        Instant.ofEpochMilli(timestamp).atZone(zone).format(formatter)
}
