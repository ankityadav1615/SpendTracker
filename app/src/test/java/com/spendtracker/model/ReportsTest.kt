package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReportsTest {

    private fun spend(rupees: Long, category: Category, day: Int, month: Int = 10) =
        testTxn(TransactionType.DEBIT, rupees, category, istMillis(2026, month, day))

    private fun income(rupees: Long, category: Category, day: Int, month: Int = 10) =
        testTxn(TransactionType.CREDIT, rupees, category, istMillis(2026, month, day))

    @Test
    fun expenseLinesAreLargestFirstWithShares() {
        val txns = listOf(
            spend(1_000, Category.FOOD, 1),
            spend(3_000, Category.RENT, 2),
            spend(500, Category.FOOD, 3),
            spend(500, Category.FUEL, 4),
            spend(9_000, Category.CARD_BILL, 5),
            income(50_000, Category.SALARY, 1),
        )
        val lines = Reports.lines(ReportSide.EXPENSES, txns, emptyMap())
        assertEquals(listOf(Category.RENT, Category.FOOD, Category.FUEL), lines.map { it.category })
        assertEquals(listOf(300_000L, 150_000L, 50_000L), lines.map { it.amountPaise })
        assertEquals(listOf(1, 2, 1), lines.map { it.count })
        assertEquals(listOf(0.6f, 0.3f, 0.1f), lines.map { it.share })
        assertNull(lines.first().change)
    }

    @Test
    fun incomeLinesLeaveOutOwnTransfers() {
        val txns = listOf(
            income(40_000, Category.SALARY, 1),
            income(10_000, Category.OTHER_INCOME, 3),
            income(5_000, Category.SELF_TRANSFER, 4),
            spend(100, Category.FOOD, 2),
        )
        val report = Reports.build(
            ReportSide.INCOME, txns, emptyMap(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), zone = IST,
        )
        assertEquals(listOf(Category.SALARY, Category.OTHER_INCOME), report.lines.map { it.category })
        assertEquals(5_000_000L, report.totalPaise)
        assertEquals(2, report.count)
    }

    @Test
    fun withdrawnCashSpentByHandMovesToItsCategory() {
        val atm = spend(2_000, Category.CASH, 1)
        val veg = manualTxn(2_000, Category.GROCERIES, istMillis(2026, 10, 2))
        val all = listOf(atm, veg)
        val lines = Reports.lines(ReportSide.EXPENSES, all, CashLedger.covered(all))
        assertEquals(listOf(Category.GROCERIES), lines.map { it.category })
        assertEquals(1f, lines.single().share)
    }

    @Test
    fun changeAgainstThePreviousPeriod() {
        assertEquals(Change.Up(20), Reports.change(120, 100))
        assertEquals(Change.Down(20), Reports.change(80, 100))
        assertEquals(Change.Same, Reports.change(100, 100))
        assertEquals(Change.Same, Reports.change(1_001, 1_000))
        assertEquals(Change.New, Reports.change(5, 0))
        assertEquals(Change.Same, Reports.change(0, 0))
        assertEquals(Change.Down(100), Reports.change(0, 100))
    }

    @Test
    fun linesCompareWithThePreviousPeriod() {
        val now = listOf(spend(1_200, Category.FOOD, 5), spend(400, Category.FUEL, 6))
        val before = listOf(spend(1_000, Category.FOOD, 5, month = 9), spend(800, Category.RENT, 6, month = 9))
        val lines = Reports.lines(ReportSide.EXPENSES, now, emptyMap(), before)
        assertEquals(listOf(Change.Up(20), Change.New), lines.map { it.change })
    }

    @Test
    fun aMonthHasABarPerDay() {
        val txns = listOf(spend(100, Category.FOOD, 1), spend(50, Category.FOOD, 1), spend(300, Category.FUEL, 20))
        val trend = Reports.trend(
            ReportSide.EXPENSES, txns, emptyMap(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), IST,
        )
        assertEquals(TrendUnit.DAY, trend.unit)
        assertEquals(20, trend.days)
        assertEquals(20, trend.bars.size)
        assertEquals(15_000L, trend.bars.first().amountPaise)
        assertEquals(30_000L, trend.bars.last().amountPaise)
        assertEquals(LocalDate.of(2026, 10, 20), trend.bars.last().first)
    }

    @Test
    fun twoMonthsHaveABarPerWeek() {
        val first = LocalDate.of(2026, 8, 22)
        val last = LocalDate.of(2026, 10, 20)
        val txns = listOf(spend(100, Category.FOOD, 22, month = 8), spend(70, Category.FOOD, 20), spend(5, Category.FOOD, 21))
        val trend = Reports.trend(ReportSide.EXPENSES, txns, emptyMap(), first, last, IST)
        assertEquals(TrendUnit.WEEK, trend.unit)
        assertEquals(60, trend.days)
        assertEquals(9, trend.bars.size)
        assertEquals(first, trend.bars.first().first)
        assertEquals(first.plusDays(6), trend.bars.first().last)
        assertEquals(LocalDate.of(2026, 10, 17), trend.bars.last().first)
        assertEquals(last, trend.bars.last().last)
        assertEquals(10_000L, trend.bars.first().amountPaise)
        assertEquals(7_000L, trend.bars.last().amountPaise)
        assertEquals(17_000L, trend.bars.sumOf { it.amountPaise })
    }

    @Test
    fun longRangesHaveABarPerMonth() {
        val first = LocalDate.of(2026, 7, 15)
        val last = LocalDate.of(2026, 10, 20)
        val txns = listOf(income(500, Category.SALARY, 31, month = 7), income(700, Category.SALARY, 1, month = 8))
        val trend = Reports.trend(ReportSide.INCOME, txns, emptyMap(), first, last, IST)
        assertEquals(TrendUnit.MONTH, trend.unit)
        assertEquals(
            listOf(first to LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31)),
            trend.bars.take(2).map { it.first to it.last },
        )
        assertEquals(LocalDate.of(2026, 10, 1) to last, trend.bars.last().let { it.first to it.last })
        assertEquals(listOf(50_000L, 70_000L, 0L, 0L), trend.bars.map { it.amountPaise })
    }

    @Test
    fun runningMonthIsComparedWithTheSameDaysOfTheLastOne() {
        val resolver = PeriodResolver(emptyList(), istMillis(2026, 8, 10), istMillis(2026, 10, 20, 10), 1, IST)
        val thisMonth = resolver.resolve(PeriodChoice.Month())
        val comparison = Reports.comparison(thisMonth, resolver.resolve(thisMonth.previous!!), LocalDate.of(2026, 10, 20), IST)!!
        assertEquals(LocalDate.of(2026, 9, 1), comparison.first)
        assertEquals(LocalDate.of(2026, 9, 20), comparison.last)
        assertEquals(istMillis(2026, 9, 1, 0), comparison.period.start)
        assertEquals(istMillis(2026, 9, 21, 0), comparison.period.end)
    }

    @Test
    fun finishedMonthIsComparedWithAllOfTheOneBefore() {
        val resolver = PeriodResolver(emptyList(), istMillis(2026, 8, 10), istMillis(2026, 10, 20, 10), 1, IST)
        val september = resolver.resolve(resolver.resolve(PeriodChoice.Month()).previous!!)
        val comparison = Reports.comparison(september, resolver.resolve(september.previous!!), LocalDate.of(2026, 10, 20), IST)!!
        assertEquals(LocalDate.of(2026, 8, 1), comparison.first)
        assertEquals(LocalDate.of(2026, 8, 31), comparison.last)
        assertEquals(istMillis(2026, 9, 1, 0), comparison.period.end)
    }

    @Test
    fun nothingToCompareWithoutAnEarlierPeriod() {
        val resolver = PeriodResolver(emptyList(), istMillis(2026, 8, 10), istMillis(2026, 10, 20, 10), 1, IST)
        assertNull(Reports.comparison(resolver.resolve(PeriodChoice.Month()), null, LocalDate.of(2026, 10, 20), IST))
    }
}
