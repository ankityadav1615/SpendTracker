package com.spendtracker.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What the user picked to look at. */
sealed interface PeriodChoice {
    /** A pay cycle (payday to payday); a null [start] follows the current one. */
    data class PayCycle(val start: Long? = null) : PeriodChoice

    /** A month starting on the configured day; a null [start] follows the current one. */
    data class Month(val start: Long? = null) : PeriodChoice

    /** Whole days from [first] to [last], from the calendar or a preset such as "Last 3 months". */
    data class Range(val first: LocalDate, val last: LocalDate, val name: String? = null) : PeriodChoice
}

data class PeriodLabel(val title: String, val detail: String?)

data class PeriodOption(val title: String, val detail: String, val choice: PeriodChoice)

data class ResolvedPeriod(
    /** The choice in its canonical form; compare it with [PeriodOption.choice] to find the selected option. */
    val choice: PeriodChoice,
    val period: Period,
    val label: PeriodLabel,
    val previous: PeriodChoice?,
    val next: PeriodChoice?,
    /** A monthly budget fits months and pay cycles, not arbitrary ranges. */
    val budgetApplies: Boolean,
    /** Ends "…that came in <since 1 Oct>". */
    val incomeWindow: String,
    /** Dates to preselect on the calendar, or null when the period has no fixed start. */
    val firstDate: LocalDate?,
    val lastDate: LocalDate,
)

/** Turns [PeriodChoice]s into concrete periods, labels and the options offered in the period sheet. */
class PeriodResolver(
    salaryTimestamps: List<Long>,
    earliestTimestamp: Long?,
    private val now: Long,
    monthStartDay: Int = 1,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val today: LocalDate = date(now)
    private val startDay = monthStartDay.coerceIn(1, 28)
    private val cycles = Periods.forMode(PeriodMode.INCOME_CYCLE, salaryTimestamps, earliestTimestamp, now, startDay, zone)

    /** Months always reach back at least to "Last 3 months", even with little history. */
    private val months = Periods.months(
        minOf(earliestTimestamp ?: now, today.minusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli()),
        now,
        startDay,
        zone,
    )

    /** Pay cycles exist only once a salary credit has been found. */
    val hasPayCycles: Boolean = cycles.any { it.kind == PeriodKind.SALARY_CYCLE }

    fun defaultChoice(mode: PeriodMode): PeriodChoice =
        if (mode == PeriodMode.INCOME_CYCLE && hasPayCycles) PeriodChoice.PayCycle() else PeriodChoice.Month()

    fun resolve(choice: PeriodChoice): ResolvedPeriod = when (choice) {
        is PeriodChoice.PayCycle -> if (hasPayCycles) cycle(choice.start) else month(null)
        is PeriodChoice.Month -> month(choice.start)
        is PeriodChoice.Range -> range(choice)
    }

    fun options(): List<PeriodOption> = buildList {
        if (hasPayCycles) {
            val current = cycles.last()
            add(PeriodOption("This pay cycle", "Since your salary on ${DateRanges.date(date(current.start), today)}", PeriodChoice.PayCycle()))
            val previous = cycles.getOrNull(cycles.lastIndex - 1)
            if (previous != null && previous.kind == PeriodKind.SALARY_CYCLE) {
                add(PeriodOption("Last pay cycle", span(previous), PeriodChoice.PayCycle(previous.start)))
            }
        }
        val thisMonth = months.last()
        add(PeriodOption("This month", "${DateRanges.date(date(thisMonth.start), today)} – today", PeriodChoice.Month()))
        val lastMonth = months[months.lastIndex - 1]
        add(PeriodOption("Last month", monthDetail(lastMonth), PeriodChoice.Month(lastMonth.start)))
        val threeMonthsStart = date(months[months.lastIndex - 2].start)
        add(
            PeriodOption(
                LAST_3_MONTHS,
                "${DateRanges.date(threeMonthsStart, today)} – today",
                PeriodChoice.Range(threeMonthsStart, today, LAST_3_MONTHS),
            ),
        )
    }

    private fun cycle(start: Long?): ResolvedPeriod {
        val index = indexOf(cycles, start)
        val period = cycles[index]
        val label = when {
            period.kind == PeriodKind.BEFORE_SALARY ->
                PeriodLabel("Before first salary", "until ${DateRanges.date(lastDay(period), today)}")
            index == cycles.lastIndex -> PeriodLabel("This pay cycle", "since ${DateRanges.date(date(period.start), today)}")
            index == cycles.lastIndex - 1 -> PeriodLabel("Last pay cycle", span(period))
            else -> PeriodLabel("Pay cycle", span(period))
        }
        return ResolvedPeriod(
            choice = cycleChoice(index),
            period = period,
            label = label,
            previous = if (index > 0) cycleChoice(index - 1) else null,
            next = if (index < cycles.lastIndex) cycleChoice(index + 1) else null,
            budgetApplies = period.kind == PeriodKind.SALARY_CYCLE,
            incomeWindow = window(period),
            firstDate = if (period.start == Long.MIN_VALUE) null else date(period.start),
            lastDate = lastDay(period),
        )
    }

    private fun month(start: Long?): ResolvedPeriod {
        val index = indexOf(months, start)
        val period = months[index]
        val label = when (index) {
            months.lastIndex -> PeriodLabel("This month", DateRanges.range(date(period.start), today, today))
            months.lastIndex - 1 -> PeriodLabel("Last month", monthDetail(period))
            else -> if (startDay == 1) PeriodLabel(period.title, null) else PeriodLabel("Month", span(period))
        }
        return ResolvedPeriod(
            choice = monthChoice(index),
            period = period,
            label = label,
            previous = if (index > 0) monthChoice(index - 1) else null,
            next = if (index < months.lastIndex) monthChoice(index + 1) else null,
            budgetApplies = true,
            incomeWindow = window(period),
            firstDate = date(period.start),
            lastDate = lastDay(period),
        )
    }

    private fun range(choice: PeriodChoice.Range): ResolvedPeriod {
        val first = minOf(choice.first, choice.last)
        val last = maxOf(choice.first, choice.last)
        val period = Periods.custom(first, last, zone)
        val days = ChronoUnit.DAYS.between(first, last) + 1
        val lastText = if (last == today) "today" else DateRanges.date(last, today)
        val label = if (choice.name != null) {
            PeriodLabel(choice.name, "${DateRanges.date(first, today)} – $lastText")
        } else {
            PeriodLabel(DateRanges.range(first, last, today), null)
        }
        val next = if (last < today) {
            val nextLast = minOf(last.plusDays(days), today)
            PeriodChoice.Range(nextLast.minusDays(days - 1), nextLast)
        } else {
            null
        }
        return ResolvedPeriod(
            choice = PeriodChoice.Range(first, last, choice.name),
            period = period,
            label = label,
            previous = PeriodChoice.Range(first.minusDays(days), first.minusDays(1)),
            next = next,
            budgetApplies = false,
            incomeWindow = if (last >= today) {
                "since ${DateRanges.date(first, today)}"
            } else {
                "between ${DateRanges.date(first, today)} and ${DateRanges.date(last, today)}"
            },
            firstDate = first,
            lastDate = last,
        )
    }

    private fun cycleChoice(index: Int) =
        PeriodChoice.PayCycle(if (index == cycles.lastIndex) null else cycles[index].start)

    private fun monthChoice(index: Int) =
        PeriodChoice.Month(if (index == months.lastIndex) null else months[index].start)

    private fun indexOf(periods: List<Period>, start: Long?): Int =
        start?.let { s -> periods.indexOfFirst { it.start == s } }?.takeIf { it >= 0 } ?: periods.lastIndex

    private fun monthDetail(period: Period): String {
        if (startDay != 1) return span(period)
        val first = date(period.start)
        return first.format(if (first.year == today.year) MONTH else MONTH_YEAR)
    }

    /** Inclusive dates of a period, e.g. "1–30 Sep"; an open period ends today. */
    private fun span(period: Period): String = DateRanges.range(date(period.start), lastDay(period), today)

    private fun window(period: Period): String = when {
        period.start == Long.MIN_VALUE -> "before ${DateRanges.date(date(period.end), today)}"
        period.end == Long.MAX_VALUE -> "since ${DateRanges.date(date(period.start), today)}"
        else -> "between ${DateRanges.date(date(period.start), today)} and ${DateRanges.date(lastDay(period), today)}"
    }

    private fun lastDay(period: Period): LocalDate = if (period.end == Long.MAX_VALUE) today else date(period.end - 1)

    private fun date(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    companion object {
        const val LAST_3_MONTHS = "Last 3 months"
        private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
        private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    }
}
