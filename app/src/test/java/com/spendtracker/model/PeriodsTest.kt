package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
