package com.spendtracker.export

import com.spendtracker.model.CashLedger
import com.spendtracker.model.Category
import com.spendtracker.model.IST
import com.spendtracker.model.PaymentMethod
import com.spendtracker.model.TransactionType
import com.spendtracker.model.istMillis
import com.spendtracker.model.manualTxn
import com.spendtracker.model.testTxn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportDocumentTest {

    private val first = LocalDate.of(2026, 10, 1)
    private val last = LocalDate.of(2026, 10, 20)
    private val madeOn = LocalDate.of(2026, 10, 21)

    private val salary = testTxn(TransactionType.CREDIT, 50_000, Category.SALARY, istMillis(2026, 10, 1, 9))
    private val atm = testTxn(TransactionType.DEBIT, 2_000, Category.CASH, istMillis(2026, 10, 2, 9))
    private val veg = manualTxn(500, Category.GROCERIES, istMillis(2026, 10, 2, 18), counterparty = "Sabzi mandi")
    private val bigBasket = testTxn(
        TransactionType.DEBIT, 1_500, Category.GROCERIES, istMillis(2026, 10, 5),
        counterparty = "BigBasket", vpa = "bigbasket@icici",
    )
    private val rent = testTxn(TransactionType.DEBIT, 15_000, Category.RENT, istMillis(2026, 10, 3))
    private val cardBill = testTxn(TransactionType.DEBIT, 9_000, Category.CARD_BILL, istMillis(2026, 10, 10))
    private val lastDay = testTxn(TransactionType.DEBIT, 100, Category.FOOD, istMillis(2026, 10, 20, 23, 59))
    private val tooEarly = testTxn(TransactionType.DEBIT, 700, Category.FOOD, istMillis(2026, 9, 30, 23, 59))
    private val tooLate = testTxn(TransactionType.DEBIT, 700, Category.FOOD, istMillis(2026, 10, 21, 0, 0))
    private val all = listOf(salary, atm, veg, bigBasket, rent, cardBill, lastDay, tooEarly, tooLate)

    private fun build(categories: Set<Category>? = null) =
        ReportDocument.build(all, first, last, categories, CashLedger.covered(all), madeOn, IST)

    @Test
    fun includesEveryDayOfTheRange() {
        val doc = build()
        assertEquals(listOf(salary, atm, veg, rent, bigBasket, cardBill, lastDay), doc.transactions)
        assertEquals(5_000_000L, doc.moneyInPaise)
        // Rent 15,000 + groceries 2,000 + food 100 + the 1,500 of the withdrawal not spent by hand.
        assertEquals(1_860_000L, doc.moneyOutPaise)
        assertEquals(3_140_000L, doc.leftPaise)
        assertEquals(50_000L, doc.cashCoveredPaise)
        assertEquals("All categories", doc.categoriesText)
        assertEquals("1 Oct – 20 Oct 2026", doc.rangeText)
    }

    @Test
    fun groupsFollowTheCategoryTotals() {
        val doc = build()
        assertEquals(
            listOf("Rent", "Groceries", "Cash withdrawal", "Food & dining", "Salary", "Not counted"),
            doc.groups.map { it.title },
        )
        val groceries = doc.groups[1]
        assertEquals(200_000L, groceries.totalPaise)
        assertEquals(listOf(veg, bigBasket), groceries.transactions)
        assertEquals(Counted.EXPENSE, groceries.counted)
        val notCounted = doc.groups.last()
        assertEquals(listOf(cardBill), notCounted.transactions)
        assertEquals(Counted.NOT_COUNTED, doc.countedAs(cardBill))
        assertEquals(Counted.INCOME, doc.countedAs(salary))
        assertEquals(150_000L, doc.groups[2].totalPaise)
    }

    @Test
    fun limitsToChosenCategories() {
        val doc = build(setOf(Category.GROCERIES, Category.RENT))
        assertEquals(listOf(veg, rent, bigBasket), doc.transactions)
        assertEquals(listOf("Rent", "Groceries"), doc.groups.map { it.title })
        assertEquals(0L, doc.moneyInPaise)
        assertEquals(1_700_000L, doc.moneyOutPaise)
        assertEquals("Rent, Groceries", doc.categoriesText)
        assertEquals(
            "4 categories",
            build(setOf(Category.GROCERIES, Category.RENT, Category.FOOD, Category.SALARY)).categoriesText,
        )
    }

    @Test
    fun fileNamesReadNaturally() {
        assertEquals(
            "SpendTracker 1–20 Oct 2026.pdf",
            ReportDocument.fileName(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), ExportFormat.PDF),
        )
        assertEquals(
            "SpendTracker 28 Sep – 3 Oct 2026.csv",
            ReportDocument.fileName(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 3), ExportFormat.CSV),
        )
        assertEquals(
            "SpendTracker 28 Dec 2025 – 3 Jan 2026.csv",
            ReportDocument.fileName(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 3), ExportFormat.CSV),
        )
        assertEquals(
            "SpendTracker 5 Oct 2026.pdf",
            ReportDocument.fileName(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), ExportFormat.PDF),
        )
        assertEquals("SpendTracker 1–20 Oct 2026.pdf", build().fileName(ExportFormat.PDF))
    }

    @Test
    fun rowDetailsSayWhereMoneyWent() {
        assertEquals("HDFC Bank ••1234 · UPI", ReportDocument.rowDetail(bigBasket))
        assertEquals("HDFC Bank ••1234", ReportDocument.rowDetail(rent))
        assertEquals("Cash · added by you", ReportDocument.rowDetail(veg))
        assertEquals("Added by you", ReportDocument.rowDetail(manualTxn(10, Category.FOOD, method = null)))
        assertEquals("UPI", ReportDocument.paidWith(manualTxn(10, Category.FOOD, method = PaymentMethod.UPI)))
    }
}

class CsvReportTest {

    @Test
    fun textCellsAreQuotedAndFormulasDefused() {
        assertEquals("Swiggy", CsvReport.text("Swiggy"))
        assertEquals("\"Food, dining\"", CsvReport.text("Food, dining"))
        assertEquals("\"say \"\"hi\"\"\"", CsvReport.text("say \"hi\""))
        assertEquals("\"two\nlines\"", CsvReport.text("two\nlines"))
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, CsvReport.text("=HYPERLINK(\"x\")"))
        assertEquals("'+91 98765", CsvReport.text("+91 98765"))
        assertEquals("'-5", CsvReport.text("-5"))
        assertEquals("'@cmd", CsvReport.text("@cmd"))
        assertEquals("\" padded \"", CsvReport.text(" padded "))
    }

    @Test
    fun amountsArePlainNumbers() {
        assertEquals("-1860.00", CsvReport.amount(-186_000))
        assertEquals("0.05", CsvReport.amount(5))
        assertEquals("100000000.00", CsvReport.amount(10_000_000_000))
    }

    @Test
    fun writesSummaryThenEveryTransaction() {
        val atm = testTxn(TransactionType.DEBIT, 1_000, Category.CASH, istMillis(2026, 10, 2, 9))
        val tea = manualTxn(250, Category.FOOD, istMillis(2026, 10, 2, 17, 5), counterparty = "=cmd|' /C calc'!A0", note = "with, friends")
        val pay = testTxn(TransactionType.CREDIT, 40_000, Category.SALARY, istMillis(2026, 10, 1), counterparty = "ACME")
        val all = listOf(atm, tea, pay)
        val doc = ReportDocument.build(
            all, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), null, CashLedger.covered(all), LocalDate.of(2026, 10, 21), IST,
        )
        val csv = CsvReport.write(doc)
        assertTrue(csv.startsWith(CsvReport.BOM + "SpendTracker report\r\n"))
        val lines = csv.removePrefix(CsvReport.BOM).split("\r\n")
        assertTrue("Dates,1 Oct – 20 Oct 2026" in lines)
        assertTrue("Categories,All categories" in lines)
        assertTrue("Made on,2026-10-21" in lines)
        assertTrue("Cash withdrawal,Expense,1,750.00,75%" in lines)
        assertTrue("Food & dining,Expense,1,250.00,25%" in lines)
        assertTrue("Salary,Income,1,40000.00,100%" in lines)
        assertTrue("Money in,,,40000.00," in lines)
        assertTrue("Money out,,,1000.00," in lines)
        assertTrue("Left,,,39000.00," in lines)
        assertTrue(lines.any { it.startsWith("Note,\"₹250 of the cash withdrawals here") })
        assertTrue(CsvReport.COLUMNS.joinToString(",") in lines)
        assertTrue("2026-10-02,17:05,'=cmd|' /C calc'!A0,Food & dining,Expense,-250.00,Cash,,,,You,\"with, friends\"" in lines)
        assertTrue("2026-10-01,10:00,ACME,Salary,Income,40000.00,,HDFC Bank ••1234,,,SMS," in lines)
    }

    @Test
    fun noLeftRowForSomeCategories() {
        val food = testTxn(TransactionType.DEBIT, 100, Category.FOOD, istMillis(2026, 10, 2))
        val doc = ReportDocument.build(
            listOf(food), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), setOf(Category.FOOD), emptyMap(),
            LocalDate.of(2026, 10, 21), IST,
        )
        val lines = CsvReport.write(doc).split("\r\n")
        assertFalse(lines.any { it.startsWith("Left,") })
        assertTrue("Categories,Food & dining" in lines)
    }
}
