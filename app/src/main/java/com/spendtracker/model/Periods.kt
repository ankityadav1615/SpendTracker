package com.spendtracker.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PeriodKind { SALARY_CYCLE, BEFORE_SALARY, MONTH, CUSTOM }

/** How history is split into the periods the dashboard steps through. */
enum class PeriodMode {
    /** Calendar months, or months starting on a chosen day (e.g. the 5th to the 4th). */
    MONTHLY,

    /** From one pay credit (salary) to the next; months until a pay credit is found. */
    INCOME_CYCLE,
}

/** A time window [start, end) the dashboard summarizes. */
data class Period(val start: Long, val end: Long, val kind: PeriodKind, val title: String) {
    operator fun contains(timestamp: Long) = timestamp >= start && timestamp < end

    val subtitle: String
        get() = when (kind) {
            PeriodKind.SALARY_CYCLE -> if (end == Long.MAX_VALUE) "Current pay cycle" else "Pay cycle"
            PeriodKind.BEFORE_SALARY -> "Before the first pay credit"
            PeriodKind.MONTH -> "Month"
            PeriodKind.CUSTOM -> "Custom range"
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
    ): List<Period> = incomeCycles(salaryTimestamps, earliestTimestamp, now, startDay = 1, zone)

    /**
     * Periods for the chosen [mode], oldest first; the last one is open and contains [now].
     * [INCOME_CYCLE][PeriodMode.INCOME_CYCLE] falls back to months starting on [startDay] when no salary
     * credit is found.
     */
    fun forMode(
        mode: PeriodMode,
        salaryTimestamps: List<Long>,
        earliestTimestamp: Long?,
        now: Long,
        startDay: Int = 1,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Period> = when (mode) {
        PeriodMode.MONTHLY -> months(earliestTimestamp, now, startDay, zone)
        PeriodMode.INCOME_CYCLE -> incomeCycles(salaryTimestamps, earliestTimestamp, now, startDay, zone)
    }

    /**
     * Months starting on [startDay] (1..28; other values are clamped), e.g. 5 Mar – 4 Apr for day 5.
     * They cover [earliestTimestamp] (or just [now] without history) up to the open period containing [now],
     * oldest first. Titles are "March 2026" for calendar months, otherwise the date range.
     */
    fun months(
        earliestTimestamp: Long?,
        now: Long,
        startDay: Int = 1,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Period> {
        val day = startDay.coerceIn(1, 28)
        var month = monthContaining(minOf(earliestTimestamp ?: now, now), day, zone)
        val last = monthContaining(now, day, zone)
        val periods = mutableListOf<Period>()
        while (month <= last) {
            val start = month.atDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = if (month == last) {
                Long.MAX_VALUE
            } else {
                month.plusMonths(1).atDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            val title = if (day == 1) month.format(MONTH_YEAR) else rangeTitle(start, end, zone)
            periods += Period(start, end, PeriodKind.MONTH, title)
            month = month.plusMonths(1)
        }
        return periods
    }

    /** The whole days from [startDate] to [endDateInclusive]; the dates may be given in either order. */
    fun custom(
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Period {
        val reversed = endDateInclusive.isBefore(startDate)
        val first = if (reversed) endDateInclusive else startDate
        val last = if (reversed) startDate else endDateInclusive
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val title = if (first == last) first.format(DAY_MONTH_YEAR) else rangeTitle(start, end, zone)
        return Period(start, end, PeriodKind.CUSTOM, title)
    }

    fun salaryCycleStarts(salaryTimestamps: List<Long>): List<Long> {
        val starts = mutableListOf<Long>()
        for (timestamp in salaryTimestamps.sorted()) {
            if (starts.isEmpty() || timestamp - starts.last() >= SALARY_MERGE_WINDOW_MS) starts += timestamp
        }
        return starts
    }

    private fun incomeCycles(
        salaryTimestamps: List<Long>,
        earliestTimestamp: Long?,
        now: Long,
        startDay: Int,
        zone: ZoneId,
    ): List<Period> {
        val starts = salaryCycleStarts(salaryTimestamps)
        if (starts.isEmpty()) return months(earliestTimestamp, now, startDay, zone)

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

    /** The month whose period (starting on [startDay]) contains [timestamp]. */
    private fun monthContaining(timestamp: Long, startDay: Int, zone: ZoneId): YearMonth {
        val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val month = YearMonth.from(date)
        return if (date.dayOfMonth >= startDay) month else month.minusMonths(1)
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
