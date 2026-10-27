package com.spendtracker.export

import com.spendtracker.data.Transaction
import com.spendtracker.data.categoryLabel
import com.spendtracker.data.isManual
import com.spendtracker.model.CashLedger
import com.spendtracker.model.Category
import com.spendtracker.model.ReportLine
import com.spendtracker.model.ReportSide
import com.spendtracker.model.Reports
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ExportFormat(val mimeType: String, val extension: String, val label: String) {
    PDF("application/pdf", "pdf", "PDF"),
    CSV("text/csv", "csv", "CSV"),
}

/** How a transaction counts in a report. */
enum class Counted(val label: String) { EXPENSE("Expense"), INCOME("Income"), NOT_COUNTED("Not counted") }

/** Payments of one category in the report, or the "Not counted" group (no [category]). */
data class PaymentGroup(
    val title: String,
    val category: Category?,
    val counted: Counted,
    val totalPaise: Long,
    /** Oldest first. */
    val transactions: List<Transaction>,
)

/** Everything a downloaded report contains, worked out once for both file types. */
class ReportDocument private constructor(
    val first: LocalDate,
    val last: LocalDate,
    val madeOn: LocalDate,
    /** Null for all categories. */
    val categories: Set<Category>?,
    val moneyInPaise: Long,
    val moneyOutPaise: Long,
    val expenses: List<ReportLine>,
    val income: List<ReportLine>,
    val groups: List<PaymentGroup>,
    /** Every included transaction, oldest first. */
    val transactions: List<Transaction>,
    /** Withdrawn cash already counted through cash spends the user added. */
    val cashCoveredPaise: Long,
    private val counted: Map<Long, Counted>,
    private val zone: ZoneId,
) {
    val leftPaise: Long get() = moneyInPaise - moneyOutPaise

    val rangeText: String get() = fullRange(first, last)

    val categoriesText: String
        get() {
            val chosen = categories ?: return "All categories"
            val names = Category.entries.filter { it in chosen }.map { it.label }
            return if (names.size <= 3) names.joinToString(", ") else "${names.size} categories"
        }

    fun fileName(format: ExportFormat): String = fileName(first, last, format)

    fun countedAs(transaction: Transaction): Counted = counted[transaction.id] ?: Counted.NOT_COUNTED

    fun date(transaction: Transaction): LocalDate = Instant.ofEpochMilli(transaction.timestamp).atZone(zone).toLocalDate()

    fun time(transaction: Transaction): String =
        Instant.ofEpochMilli(transaction.timestamp).atZone(zone).toLocalTime().format(TIME)

    companion object {
        private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private val DAY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
        private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

        /**
         * The report of [all] transactions dated [first]..[last], limited to [categories] (null for all).
         * [cashCovered] comes from [CashLedger.covered] over all transactions, so the totals match the app.
         */
        fun build(
            all: List<Transaction>,
            first: LocalDate,
            last: LocalDate,
            categories: Set<Category>?,
            cashCovered: Map<Long, Long>,
            madeOn: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
        ): ReportDocument {
            val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val included = all
                .filter { it.timestamp in start until end && (categories == null || it.category in categories) }
                .sortedWith(compareBy({ it.timestamp }, { it.id }))
            val expenses = Reports.lines(ReportSide.EXPENSES, included, cashCovered)
            val income = Reports.lines(ReportSide.INCOME, included, cashCovered)
            val counted = included.associate { txn ->
                txn.id to when {
                    Reports.amountFor(ReportSide.EXPENSES, txn, cashCovered) > 0 -> Counted.EXPENSE
                    Reports.amountFor(ReportSide.INCOME, txn, cashCovered) > 0 -> Counted.INCOME
                    else -> Counted.NOT_COUNTED
                }
            }
            val groups = buildList {
                for (line in expenses) {
                    add(
                        group(
                            line,
                            Counted.EXPENSE,
                            included.filter {
                                it.category == line.category && it.categoryLabel == line.label && counted[it.id] == Counted.EXPENSE
                            },
                        ),
                    )
                }
                for (line in income) {
                    add(
                        group(
                            line,
                            Counted.INCOME,
                            included.filter {
                                it.category == line.category && it.categoryLabel == line.label && counted[it.id] == Counted.INCOME
                            },
                        ),
                    )
                }
                val notCounted = included.filter { counted[it.id] == Counted.NOT_COUNTED }
                if (notCounted.isNotEmpty()) {
                    add(PaymentGroup("Not counted", null, Counted.NOT_COUNTED, notCounted.sumOf { it.amountPaise }, notCounted))
                }
            }
            return ReportDocument(
                first = first,
                last = last,
                madeOn = madeOn,
                categories = categories,
                moneyInPaise = income.sumOf { it.amountPaise },
                moneyOutPaise = expenses.sumOf { it.amountPaise },
                expenses = expenses,
                income = income,
                groups = groups,
                transactions = included,
                cashCoveredPaise = included.sumOf { cashCovered[it.id] ?: 0L },
                counted = counted,
                zone = zone,
            )
        }

        private fun group(line: ReportLine, counted: Counted, transactions: List<Transaction>) =
            PaymentGroup(line.label, line.category, counted, line.amountPaise, transactions)

        /** "1 Oct – 20 Oct 2026", "28 Dec 2025 – 3 Jan 2026" or "5 Oct 2026". */
        fun fullRange(first: LocalDate, last: LocalDate): String = when {
            first == last -> first.format(DAY_MONTH_YEAR)
            first.year == last.year -> first.format(DAY_MONTH) + " – " + last.format(DAY_MONTH_YEAR)
            else -> first.format(DAY_MONTH_YEAR) + " – " + last.format(DAY_MONTH_YEAR)
        }

        /** "SpendTracker 1–20 Oct 2026.pdf" */
        fun fileName(first: LocalDate, last: LocalDate, format: ExportFormat): String =
            "SpendTracker ${fileRange(first, last)}.${format.extension}"

        /** For file names: "1–20 Oct 2026", "28 Sep – 3 Oct 2026". */
        fun fileRange(first: LocalDate, last: LocalDate): String = when {
            first == last -> first.format(DAY_MONTH_YEAR)
            first.year == last.year && first.month == last.month -> first.format(DAY) + "–" + last.format(DAY_MONTH_YEAR)
            first.year == last.year -> first.format(DAY_MONTH) + " – " + last.format(DAY_MONTH_YEAR)
            else -> first.format(DAY_MONTH_YEAR) + " – " + last.format(DAY_MONTH_YEAR)
        }

        /** Bank and account: "ICICI Bank ••3485", "Account ••3485", "HDFC Bank" or null. */
        fun account(transaction: Transaction): String? = when {
            transaction.bank != null && transaction.accountTail != null -> "${transaction.bank} ••${transaction.accountTail}"
            transaction.accountTail != null -> "Account ••${transaction.accountTail}"
            else -> transaction.bank
        }

        /** "Cash" for an added entry, "UPI" for SMS payments to a UPI ID, or null when unknown. */
        fun paidWith(transaction: Transaction): String? = when {
            transaction.isManual -> transaction.method?.label
            transaction.vpa != null -> "UPI"
            else -> null
        }

        /** The small grey text of a PDF row: "ICICI Bank ••3485 · UPI" or "Cash · added by you". */
        fun rowDetail(transaction: Transaction): String =
            if (transaction.isManual) {
                listOfNotNull(transaction.method?.label, "added by you").joinToString(" · ").replaceFirstChar { it.uppercase() }
            } else {
                listOfNotNull(account(transaction), paidWith(transaction)).joinToString(" · ")
            }
    }
}
