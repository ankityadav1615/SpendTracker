package com.spendtracker.export

import com.spendtracker.data.displayName
import com.spendtracker.data.isManual
import com.spendtracker.model.Money
import com.spendtracker.model.ReportLine
import com.spendtracker.model.TransactionType
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Writes a [ReportDocument] as a spreadsheet: category totals first, then every transaction. */
object CsvReport {

    /** Lets Excel recognise the file as UTF-8, so "₹" and "–" show correctly. */
    const val BOM = "\uFEFF"

    private val ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    val COLUMNS = listOf(
        "Date", "Time", "Name", "Category", "Type", "Amount (₹)", "Paid with", "Account", "UPI ID", "Reference", "Source", "Note",
    )

    fun write(doc: ReportDocument): String {
        val out = StringBuilder(BOM)
        fun row(vararg cells: String) {
            cells.joinTo(out, ",")
            out.append("\r\n")
        }
        row(text("SpendTracker report"))
        row(text("Dates"), text(doc.rangeText))
        row(text("Categories"), text(doc.categoriesText))
        row(text("Made on"), doc.madeOn.format(ISO_DATE))
        row()
        row(text("Category"), text("Type"), text("Payments"), text("Amount (₹)"), text("Share"))
        summaryRows(doc.expenses, "Expense").forEach { row(*it) }
        summaryRows(doc.income, "Income").forEach { row(*it) }
        row(text("Money in"), "", "", amount(doc.moneyInPaise), "")
        row(text("Money out"), "", "", amount(doc.moneyOutPaise), "")
        if (doc.categories == null) row(text("Left"), "", "", amount(doc.leftPaise), "")
        if (doc.cashCoveredPaise > 0) {
            row(
                text("Note"),
                text(
                    "${Money.format(doc.cashCoveredPaise)} of the cash withdrawals here was spent through cash expenses " +
                        "you added, so it is counted under those categories and not twice.",
                ),
            )
        }
        row()
        row(*COLUMNS.map(::text).toTypedArray())
        for (txn in doc.transactions) {
            val signed = if (txn.type == TransactionType.DEBIT) -txn.amountPaise else txn.amountPaise
            row(
                doc.date(txn).format(ISO_DATE),
                doc.time(txn),
                text(txn.displayName),
                text(txn.category.label),
                text(doc.countedAs(txn).label),
                amount(signed),
                text(ReportDocument.paidWith(txn).orEmpty()),
                text(ReportDocument.account(txn).orEmpty()),
                text(txn.vpa.orEmpty()),
                text(txn.refNo.orEmpty()),
                text(if (txn.isManual) "You" else "SMS"),
                text(txn.note.orEmpty()),
            )
        }
        return out.toString()
    }

    private fun summaryRows(lines: List<ReportLine>, type: String): List<Array<String>> =
        lines.map { line ->
            arrayOf(
                text(line.category.label),
                text(type),
                line.count.toString(),
                amount(line.amountPaise),
                percent(line.share),
            )
        }

    /** Plain number with two decimals, e.g. "-1860.00", which spreadsheets read as a number. */
    fun amount(paise: Long): String = BigDecimal.valueOf(paise, 2).toPlainString()

    private fun percent(share: Float): String {
        val value = (share * 100).roundToInt()
        return if (value == 0 && share > 0f) "<1%" else "$value%"
    }

    /**
     * A text cell. Quoted when needed (RFC 4180), and text that a spreadsheet would run as a formula
     * (starting with =, +, -, @) is prefixed with an apostrophe, since names come from SMS senders.
     */
    fun text(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            safe.startsWith(' ') || safe.endsWith(' ')
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
