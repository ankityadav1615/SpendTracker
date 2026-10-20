package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodResolverTest {

    private val now = istMillis(2026, 10, 20, 10)
    private val today = LocalDate.of(2026, 10, 20)

    private fun resolver(salaries: List<Long> = emptyList(), earliest: Long? = istMillis(2026, 8, 10), startDay: Int = 1) =
        PeriodResolver(salaries, earliest, now, startDay, IST)

    @Test
    fun withoutSalaryEveryoneGetsMonths() {
        val resolver = resolver()
        assertFalse(resolver.hasPayCycles)
        assertEquals(PeriodChoice.Month(), resolver.defaultChoice(PeriodMode.INCOME_CYCLE))
        assertEquals(PeriodChoice.Month(), resolver.resolve(PeriodChoice.PayCycle()).choice)

        val thisMonth = resolver.resolve(PeriodChoice.Month())
        assertEquals(PeriodLabel("This month", "1–20 Oct"), thisMonth.label)
        assertEquals("since 1 Oct", thisMonth.incomeWindow)
        assertTrue(thisMonth.budgetApplies)
        assertNull(thisMonth.next)
        assertEquals(PeriodChoice.Month(istMillis(2026, 9, 1, 0)), thisMonth.previous)

        val lastMonth = resolver.resolve(thisMonth.previous!!)
        assertEquals(PeriodLabel("Last month", "September"), lastMonth.label)
        assertEquals("between 1 Sep and 30 Sep", lastMonth.incomeWindow)
        assertEquals(PeriodChoice.Month(), lastMonth.next)

        val august = resolver.resolve(lastMonth.previous!!)
        assertEquals(PeriodLabel("August 2026", null), august.label)
    }

    @Test
    fun monthOptionsReachBackThreeMonthsEvenWithLittleHistory() {
        val options = resolver(earliest = istMillis(2026, 10, 15)).options()
        assertEquals(listOf("This month", "Last month", PeriodResolver.LAST_3_MONTHS), options.map { it.title })
        val lastThree = options.last()
        assertEquals("1 Aug – today", lastThree.detail)
        assertEquals(
            PeriodChoice.Range(LocalDate.of(2026, 8, 1), today, PeriodResolver.LAST_3_MONTHS),
            lastThree.choice,
        )
        assertEquals(PeriodLabel(PeriodResolver.LAST_3_MONTHS, "1 Aug – today"), resolver().resolve(lastThree.choice).label)
    }

    @Test
    fun payCyclesFollowSalaryCredits() {
        val resolver = resolver(listOf(istMillis(2026, 8, 31), istMillis(2026, 9, 30)))
        assertTrue(resolver.hasPayCycles)
        assertEquals(PeriodChoice.PayCycle(), resolver.defaultChoice(PeriodMode.INCOME_CYCLE))
        assertEquals(PeriodChoice.Month(), resolver.defaultChoice(PeriodMode.MONTHLY))

        val current = resolver.resolve(PeriodChoice.PayCycle())
        assertEquals(PeriodLabel("This pay cycle", "since 30 Sep"), current.label)
        assertEquals(PeriodChoice.PayCycle(istMillis(2026, 8, 31)), current.previous)
        assertTrue(current.budgetApplies)

        val last = resolver.resolve(current.previous!!)
        assertEquals(PeriodLabel("Last pay cycle", "31 Aug – 30 Sep"), last.label)
        assertEquals(PeriodChoice.PayCycle(), last.next)

        val before = resolver.resolve(last.previous!!)
        assertEquals("Before first salary", before.label.title)
        assertFalse(before.budgetApplies)
        assertNull(before.firstDate)
        assertNull(before.previous)

        assertEquals(
            listOf("This pay cycle", "Last pay cycle", "This month", "Last month", PeriodResolver.LAST_3_MONTHS),
            resolver.options().map { it.title },
        )
        assertEquals("Since your salary on 30 Sep", resolver.options().first().detail)
    }

    @Test
    fun monthsCanStartOnAnotherDay() {
        val resolver = resolver(startDay = 5)
        val thisMonth = resolver.resolve(PeriodChoice.Month())
        assertEquals(PeriodLabel("This month", "5–20 Oct"), thisMonth.label)
        assertEquals(LocalDate.of(2026, 10, 5), thisMonth.firstDate)
        val lastMonth = resolver.resolve(thisMonth.previous!!)
        assertEquals(PeriodLabel("Last month", "5 Sep – 4 Oct"), lastMonth.label)
    }

    @Test
    fun unknownStartFallsBackToTheLatest() {
        assertEquals(PeriodChoice.Month(), resolver().resolve(PeriodChoice.Month(12_345L)).choice)
    }

    @Test
    fun calendarRangeStepsByItsOwnLength() {
        val range = resolver().resolve(PeriodChoice.Range(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1)))
        assertEquals(PeriodChoice.Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10)), range.choice)
        assertEquals(PeriodLabel("1–10 Oct", null), range.label)
        assertEquals(PeriodChoice.Range(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 30)), range.previous)
        assertEquals(PeriodChoice.Range(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 20)), range.next)
        assertFalse(range.budgetApplies)
        assertEquals("between 1 Oct and 10 Oct", range.incomeWindow)
        assertTrue(istMillis(2026, 10, 10, 23, 59) in range.period)
        assertFalse(istMillis(2026, 10, 11, 0, 0) in range.period)
    }

    @Test
    fun nextRangeStopsAtToday() {
        val resolver = resolver()
        val range = resolver.resolve(PeriodChoice.Range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 15)))
        assertEquals(PeriodChoice.Range(LocalDate.of(2026, 10, 10), today), range.next)

        val endsToday = resolver.resolve(PeriodChoice.Range(LocalDate.of(2026, 10, 1), today))
        assertNull(endsToday.next)
        assertEquals("since 1 Oct", endsToday.incomeWindow)
    }
}
