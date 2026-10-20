package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PeriodsTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 10) =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun salaryCyclesWithEarlierHistory() {
        val sep30 = at(2024, 9, 30)
        val oct31 = at(2024, 10, 31)
        val nov1 = at(2024, 11, 1) // second part of the same salary
        val periods = Periods.compute(listOf(oct31, sep30, nov1), at(2024, 9, 15), at(2024, 11, 15), zone)

        assertEquals(3, periods.size)
        val (before, october, current) = periods
        assertEquals(PeriodKind.BEFORE_SALARY, before.kind)
        assertEquals("Until 30 Sep 2024", before.title)
        assertEquals(Long.MIN_VALUE, before.start)
        assertEquals(sep30, before.end)

        assertEquals(PeriodKind.SALARY_CYCLE, october.kind)
        assertEquals(sep30, october.start)
        assertEquals(oct31, october.end)
        assertEquals("30 Sep – 31 Oct 2024", october.title)

        assertEquals(oct31, current.start)
        assertEquals(Long.MAX_VALUE, current.end)
        assertEquals("31 Oct 2024 – today", current.title)
        assertTrue(nov1 in current)
        assertFalse(oct31 in october)
    }

    @Test
    fun cycleTitleAcrossYears() {
        val periods = Periods.compute(listOf(at(2024, 12, 31), at(2025, 1, 31)), null, at(2025, 2, 10), zone)
        assertEquals(2, periods.size)
        assertEquals("31 Dec 2024 – 31 Jan 2025", periods[0].title)
    }

    @Test
    fun monthsWhenNoSalaryFound() {
        val periods = Periods.compute(emptyList(), at(2024, 9, 15), at(2024, 11, 15), zone)
        assertEquals(listOf("September 2024", "October 2024", "November 2024"), periods.map { it.title })
        assertTrue(periods.all { it.kind == PeriodKind.MONTH })
        assertEquals(LocalDateTime.of(2024, 10, 1, 0, 0).atZone(zone).toInstant().toEpochMilli(), periods[1].start)
        assertEquals(Long.MAX_VALUE, periods.last().end)
    }

    @Test
    fun singleMonthWhenNoHistory() {
        val periods = Periods.compute(emptyList(), null, at(2024, 11, 15), zone)
        assertEquals(listOf("November 2024"), periods.map { it.title })
    }

    @Test
    fun salariesWithinMergeWindowStartOneCycle() {
        val starts = Periods.salaryCycleStarts(listOf(at(2024, 10, 1), at(2024, 10, 5), at(2024, 11, 1)))
        assertEquals(listOf(at(2024, 10, 1), at(2024, 11, 1)), starts)
    }

    private fun midnight(year: Int, month: Int, day: Int) = at(year, month, day, hour = 0)

    @Test
    fun calendarMonths() {
        val periods = Periods.months(at(2026, 1, 15), at(2026, 3, 10), startDay = 1, zone = zone)
        assertEquals(listOf("January 2026", "February 2026", "March 2026"), periods.map { it.title })
        assertEquals(listOf(midnight(2026, 1, 1), midnight(2026, 2, 1), midnight(2026, 3, 1)), periods.map { it.start })
        assertEquals(midnight(2026, 2, 1), periods[0].end)
        assertEquals(Long.MAX_VALUE, periods.last().end)
        assertTrue(periods.all { it.kind == PeriodKind.MONTH && it.subtitle == "Month" })
    }

    @Test
    fun monthsStartingOnAChosenDay() {
        val periods = Periods.months(at(2026, 1, 2), at(2026, 3, 20), startDay = 5, zone = zone)
        assertEquals(
            listOf("5 Dec 2025 – 4 Jan 2026", "5 Jan – 4 Feb 2026", "5 Feb – 4 Mar 2026", "5 Mar 2026 – today"),
            periods.map { it.title },
        )
        assertEquals(midnight(2025, 12, 5), periods[0].start)
        assertEquals(midnight(2026, 1, 5), periods[0].end)
        assertEquals(periods[1].start, periods[0].end)
        assertTrue(at(2026, 2, 4, hour = 23) in periods[1])
        assertFalse(midnight(2026, 2, 5) in periods[1])
        assertTrue(midnight(2026, 2, 5) in periods[2])
        assertEquals(Long.MAX_VALUE, periods.last().end)
    }

    @Test
    fun currentMonthBeforeTheStartDayBeganLastMonth() {
        val periods = Periods.months(null, at(2026, 4, 3), startDay = 5, zone = zone)
        assertEquals(1, periods.size)
        assertEquals(midnight(2026, 3, 5), periods.single().start)
        assertEquals("5 Mar 2026 – today", periods.single().title)
        assertTrue(at(2026, 4, 3) in periods.single())
    }

    @Test
    fun startDayIsClampedTo28() {
        val periods = Periods.months(null, at(2026, 3, 30), startDay = 31, zone = zone)
        assertEquals(midnight(2026, 3, 28), periods.single().start)
    }

    @Test
    fun customRangeIncludesTheWholeEndDay() {
        val period = Periods.custom(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 18), zone)
        assertEquals(PeriodKind.CUSTOM, period.kind)
        assertEquals("Custom range", period.subtitle)
        assertEquals("3 Mar – 18 Mar 2026", period.title)
        assertEquals(midnight(2026, 3, 3), period.start)
        assertEquals(midnight(2026, 3, 19), period.end)
        assertFalse(period.start - 1 in period)
        assertTrue(period.start in period)
        assertTrue(period.end - 1 in period)
        assertFalse(period.end in period)
        assertEquals(period, Periods.custom(LocalDate.of(2026, 3, 18), LocalDate.of(2026, 3, 3), zone))
    }

    @Test
    fun customRangeTitles() {
        val day = Periods.custom(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 3), zone)
        assertEquals("3 Mar 2026", day.title)
        assertEquals(midnight(2026, 3, 4), day.end)
        val acrossYears = Periods.custom(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 3), zone)
        assertEquals("28 Dec 2025 – 3 Jan 2026", acrossYears.title)
    }

    @Test
    fun forModeChoosesCyclesOrMonths() {
        val salaries = listOf(at(2026, 1, 31), at(2026, 2, 28))
        val earliest = at(2026, 1, 10)
        val now = at(2026, 3, 10)

        val monthly = Periods.forMode(PeriodMode.MONTHLY, salaries, earliest, now, startDay = 1, zone = zone)
        assertEquals(listOf("January 2026", "February 2026", "March 2026"), monthly.map { it.title })

        val cycles = Periods.forMode(PeriodMode.INCOME_CYCLE, salaries, earliest, now, startDay = 5, zone = zone)
        assertEquals(Periods.compute(salaries, earliest, now, zone), cycles)
        assertEquals(
            listOf(PeriodKind.BEFORE_SALARY, PeriodKind.SALARY_CYCLE, PeriodKind.SALARY_CYCLE),
            cycles.map { it.kind },
        )
        assertEquals(
            listOf("Before the first pay credit", "Pay cycle", "Current pay cycle"),
            cycles.map { it.subtitle },
        )

        val noSalary = Periods.forMode(PeriodMode.INCOME_CYCLE, emptyList(), earliest, now, startDay = 5, zone = zone)
        assertEquals(Periods.months(earliest, now, startDay = 5, zone = zone), noSalary)
        assertEquals(listOf("5 Jan – 4 Feb 2026", "5 Feb – 4 Mar 2026", "5 Mar 2026 – today"), noSalary.map { it.title })
    }
}
