package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateRangesTest {

    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun yearOnlyWhenNotThisYear() {
        assertEquals("20 Oct", DateRanges.date(today, today))
        assertEquals("30 Dec 2025", DateRanges.date(LocalDate.of(2025, 12, 30), today))
    }

    @Test
    fun rangesWithinAMonth() {
        assertEquals("1–20 Oct", DateRanges.range(LocalDate.of(2026, 10, 1), today, today))
        assertEquals("1–31 Dec 2025", DateRanges.range(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31), today))
    }

    @Test
    fun rangesAcrossMonthsAndYears() {
        assertEquals("28 Sep – 3 Oct", DateRanges.range(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 3), today))
        assertEquals("5 Nov – 2 Dec 2025", DateRanges.range(LocalDate.of(2025, 11, 5), LocalDate.of(2025, 12, 2), today))
        assertEquals(
            "28 Dec 2025 – 3 Jan 2026",
            DateRanges.range(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 3), today),
        )
    }

    @Test
    fun singleDay() {
        assertEquals("5 Oct", DateRanges.range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), today))
    }
}
