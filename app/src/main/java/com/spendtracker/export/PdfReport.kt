package com.spendtracker.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.spendtracker.R
import com.spendtracker.data.Transaction
import com.spendtracker.data.displayName
import com.spendtracker.model.Money
import com.spendtracker.model.ReportLine
import com.spendtracker.model.TransactionType
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Draws a [ReportDocument] as an A4 PDF: totals, a ring chart with the category table, income, then every
 * payment grouped by category. Laid out twice, first to count pages for the "Page X of Y" footer.
 */
object PdfReport {

    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 40f
    private const val BOTTOM = HEIGHT - MARGIN - 18f
    private const val ROW = 17f

    private const val INK = 0xFF2B241F.toInt()
    private const val MUTED = 0xFF7C6F66.toInt()
    private const val LINE = 0xFFEEDFCC.toInt()
    private const val TINT = 0xFFFBF3EA.toInt()
    private const val CREDIT = 0xFF2F7A4B.toInt()
    private const val DANGER = 0xFFB3261E.toInt()
    private const val ACCENT = 0xFFCC4614.toInt()

    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun write(context: Context, document: ReportDocument, out: OutputStream) {
        val fonts = Fonts(context)
        val pages = Layout(document, fonts, pdf = null, totalPages = 0).run()
        val pdf = PdfDocument()
        try {
            Layout(document, fonts, pdf, pages).run()
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private class Fonts(context: Context) {
        val display: Typeface = load(context, R.font.baloo2_variable, Typeface.DEFAULT_BOLD)
        val body: Typeface = load(context, R.font.mukta_regular, Typeface.DEFAULT)
        val medium: Typeface = load(context, R.font.mukta_medium, Typeface.DEFAULT)
        val semibold: Typeface = load(context, R.font.mukta_semibold, Typeface.DEFAULT_BOLD)

        private fun load(context: Context, id: Int, fallback: Typeface): Typeface =
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback
    }

    /** One layout pass. Without [pdf] nothing is drawn; it only counts pages. */
    private class Layout(
        private val doc: ReportDocument,
        private val fonts: Fonts,
        private val pdf: PdfDocument?,
        private val totalPages: Int,
    ) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = MARGIN
        private val right = WIDTH - MARGIN

        fun run(): Int {
            newPage()
            header()
            stats()
            if (doc.expenses.isNotEmpty()) expenses()
            if (doc.income.isNotEmpty()) income()
            if (doc.cashCoveredPaise > 0) {
                note(
                    "${Money.format(doc.cashCoveredPaise)} of the cash withdrawals here was spent through cash expenses " +
                        "you added, so it is counted under those categories and not twice.",
                )
            }
            if (doc.transactions.isEmpty()) {
                y += 10f
                text("No transactions in these dates.", MARGIN, y + 12f, paint(fonts.body, 11f, MUTED))
            } else {
                payments()
            }
            finishPage()
            return pageNumber
        }

        private fun newPage() {
            finishPage()
            pageNumber++
            y = MARGIN
            if (pdf != null) {
                page = pdf.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNumber).create())
                canvas = page!!.canvas
            }
        }

        private fun finishPage() {
            val current = page ?: return
            val footer = paint(fonts.body, 8.5f, MUTED).apply { textAlign = Paint.Align.CENTER }
            canvas?.drawText("Page $pageNumber of $totalPages", WIDTH / 2f, HEIGHT - MARGIN + 8f, footer)
            val left = paint(fonts.body, 8.5f, MUTED)
            canvas?.drawText("SpendTracker · ${doc.rangeText}", MARGIN, HEIGHT - MARGIN + 8f, left)
            pdf!!.finishPage(current)
            page = null
            canvas = null
        }

        /** Starts a new page when [height] doesn't fit. Returns true when it did. */
        private fun ensure(height: Float): Boolean {
            if (y + height <= BOTTOM) return false
            newPage()
            return true
        }

        private fun header() {
            val title = paint(fonts.display, 20f, INK, bold = true)
            text("SpendTracker report", MARGIN, y + 18f, title)
            val made = paint(fonts.body, 9.5f, MUTED).apply { textAlign = Paint.Align.RIGHT }
            text("Made on ${doc.madeOn.format(DAY_MONTH_YEAR)}", right, y + 12f, made)
            text("${doc.rangeText} · ${doc.categoriesText}", MARGIN, y + 36f, paint(fonts.body, 10.5f, MUTED), right - MARGIN)
            y += 52f
        }

        private fun stats() {
            val boxes = buildList {
                add(Triple("Money in", Money.format(doc.moneyInPaise), CREDIT))
                add(Triple("Money out", Money.format(doc.moneyOutPaise), INK))
                if (doc.categories == null) {
                    val left = doc.leftPaise
                    add(Triple(if (left < 0) "Over by" else "Left", Money.format(kotlin.math.abs(left)), if (left < 0) DANGER else INK))
                }
            }
            val gap = 10f
            val width = (right - MARGIN - gap * (boxes.size - 1)) / boxes.size
            val height = 50f
            boxes.forEachIndexed { index, (label, value, color) ->
                val x = MARGIN + index * (width + gap)
                val box = RectF(x, y, x + width, y + height)
                canvas?.drawRoundRect(box, 8f, 8f, fill(TINT))
                canvas?.drawRoundRect(box, 8f, 8f, stroke(LINE))
                text(label, x + 12f, y + 18f, paint(fonts.body, 9.5f, MUTED))
                text(value, x + 12f, y + 39f, paint(fonts.display, 16f, color, bold = true), width - 24f)
            }
            y += height + 18f
        }

        private fun expenses() {
            val lines = doc.expenses
            val ring = 120f
            ensure(22f + minOf(maxOf(ring, ROW * (lines.size + 1)), BOTTOM - MARGIN - 22f))
            sectionTitle("Where the money went")
            val top = y
            donut(lines, MARGIN, top, ring)
            y = top
            table(lines, MARGIN + ring + 24f)
            y = maxOf(y, top + ring) + 16f
        }

        private fun income() {
            ensure(22f + ROW * 2)
            sectionTitle("Where the money came from")
            table(doc.income, MARGIN)
            y += 16f
        }

        private fun sectionTitle(title: String) {
            text(title, MARGIN, y + 13f, paint(fonts.display, 13f, INK, bold = true))
            y += 22f
        }

        private fun note(message: String) {
            val paint = paint(fonts.body, 9.5f, MUTED)
            val words = message.split(' ')
            val lines = mutableListOf<String>()
            var current = ""
            for (word in words) {
                val next = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(next) > right - MARGIN) {
                    lines += current
                    current = word
                } else {
                    current = next
                }
            }
            if (current.isNotEmpty()) lines += current
            ensure(lines.size * 13f + 8f)
            lines.forEach {
                text(it, MARGIN, y + 10f, paint)
                y += 13f
            }
            y += 10f
        }

        private fun donut(lines: List<ReportLine>, x: Float, top: Float, size: Float) {
            val c = canvas ?: return
            val strokeWidth = 18f
            val inset = strokeWidth / 2
            val oval = RectF(x + inset, top + inset, x + size - inset, top + size - inset)
            val total = lines.sumOf { it.amountPaise }.toFloat()
            if (total <= 0f) return
            val gap = if (lines.size > 1) 1.6f else 0f
            var angle = -90f
            for (line in lines) {
                val sweep = line.amountPaise / total * 360f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    this.strokeWidth = strokeWidth
                    color = line.category.color.toInt()
                }
                c.drawArc(oval, angle + gap / 2, (sweep - gap).coerceAtLeast(0.6f), false, paint)
                angle += sweep
            }
            val center = paint(fonts.body, 8.5f, MUTED).apply { textAlign = Paint.Align.CENTER }
            c.drawText("Total spent", x + size / 2, top + size / 2 - 4f, center)
            val amount = paint(fonts.display, 13f, INK, bold = true).apply { textAlign = Paint.Align.CENTER }
            c.drawText(Money.format(doc.moneyOutPaise), x + size / 2, top + size / 2 + 12f, amount)
        }

        /** Category, payments, amount and share, one row per line. */
        private fun table(lines: List<ReportLine>, left: Float) {
            val amountRight = right - 48f
            val countRight = amountRight - 86f
            val head = paint(fonts.medium, 9f, MUTED)
            text("Category", left, y + 11f, head)
            text("Payments", countRight, y + 11f, head.alignRight())
            text("Amount", amountRight, y + 11f, head.alignRight())
            text("Share", right, y + 11f, head.alignRight())
            y += ROW
            canvas?.drawLine(left, y - 4f, right, y - 4f, stroke(LINE))
            val body = paint(fonts.body, 10f, INK)
            for (line in lines) {
                if (ensure(ROW)) y += 4f
                canvas?.drawCircle(left + 4f, y + 7.5f, 3.5f, fill(line.category.color.toInt()))
                text(line.category.label, left + 14f, y + 11f, body, countRight - 60f - left)
                text(line.count.toString(), countRight, y + 11f, body.alignRight())
                text(Money.format(line.amountPaise), amountRight, y + 11f, body.alignRight())
                text(percent(line.share), right, y + 11f, body.alignRight())
                y += ROW
            }
        }

        private fun payments() {
            ensure(22f + ROW * 3)
            sectionTitle("Payments by category")
            for (group in doc.groups) {
                ensure(ROW * 2 + 4f)
                groupHeader(group, continued = false)
                for (txn in group.transactions) {
                    if (ensure(ROW)) groupHeader(group, continued = true)
                    paymentRow(txn)
                }
                y += 8f
            }
        }

        private fun groupHeader(group: PaymentGroup, continued: Boolean) {
            val box = RectF(MARGIN, y, right, y + ROW + 2f)
            canvas?.drawRoundRect(box, 5f, 5f, fill(TINT))
            group.category?.let { canvas?.drawCircle(MARGIN + 9f, y + 9.5f, 3.5f, fill(it.color.toInt())) }
            val title = group.title + if (continued) " (continued)" else ""
            val titlePaint = paint(fonts.semibold, 10f, INK)
            text(title, MARGIN + if (group.category != null) 18f else 8f, y + 13f, titlePaint, right - MARGIN - 120f)
            if (!continued) {
                val total = when (group.counted) {
                    Counted.NOT_COUNTED -> "Not in totals"
                    else -> Money.format(group.totalPaise)
                }
                text(total, right - 8f, y + 13f, titlePaint.alignRight())
            }
            y += ROW + 4f
        }

        private fun paymentRow(txn: Transaction) {
            val date = doc.date(txn).format(DAY_MONTH)
            val body = paint(fonts.body, 10f, INK)
            val muted = paint(fonts.body, 9f, MUTED)
            val nameLeft = MARGIN + 50f
            val detailLeft = MARGIN + 220f
            val amountRight = right - 8f
            text(date, MARGIN + 8f, y + 11f, muted)
            text(txn.displayName, nameLeft, y + 11f, body, detailLeft - nameLeft - 10f)
            text(ReportDocument.rowDetail(txn), detailLeft, y + 11f, muted, amountRight - 80f - detailLeft)
            val signed = if (txn.type == TransactionType.CREDIT) "+" + Money.format(txn.amountPaise, withPaise = true) else Money.format(txn.amountPaise, withPaise = true)
            text(signed, amountRight, y + 11f, paint(fonts.body, 10f, if (txn.type == TransactionType.CREDIT) CREDIT else INK).alignRight())
            canvas?.drawLine(MARGIN + 8f, y + ROW - 1f, right - 8f, y + ROW - 1f, stroke(0x66EEDFCC))
            y += ROW
        }

        /** Draws [value] at baseline [baseline], cut with "…" when wider than [maxWidth]. */
        private fun text(value: String, x: Float, baseline: Float, paint: TextPaint, maxWidth: Float? = null) {
            val c = canvas ?: return
            val shown = if (maxWidth != null && maxWidth > 0) {
                TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString()
            } else {
                value
            }
            c.drawText(shown, x, baseline, paint)
        }

        private fun paint(typeface: Typeface, size: Float, color: Int, bold: Boolean = false) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = when {
                    !bold -> typeface
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> Typeface.create(typeface, 600, false)
                    else -> Typeface.create(typeface, Typeface.BOLD)
                }
                textSize = size
                this.color = color
            }

        private fun TextPaint.alignRight() = TextPaint(this).apply { textAlign = Paint.Align.RIGHT }

        private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            this.color = color
        }

        private fun percent(share: Float): String {
            val value = (share * 100).roundToInt()
            return if (value == 0 && share > 0f) "<1%" else "$value%"
        }
    }
}
