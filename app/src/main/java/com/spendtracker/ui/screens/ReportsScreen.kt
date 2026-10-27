package com.spendtracker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.R
import com.spendtracker.model.Category
import com.spendtracker.model.DateRanges
import com.spendtracker.model.Money
import com.spendtracker.model.PeriodChoice
import com.spendtracker.model.Report
import com.spendtracker.model.ReportLine
import com.spendtracker.model.ReportSide
import com.spendtracker.model.Trend
import com.spendtracker.model.TrendBar
import com.spendtracker.model.TrendUnit
import com.spendtracker.ui.MoneyState
import com.spendtracker.ui.theme.AmountStyle
import com.spendtracker.ui.theme.SpendTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ReportChart { RING, BARS }

@Composable
fun ReportsScreen(
    state: MoneyState?,
    side: ReportSide,
    chart: ReportChart,
    contentPadding: PaddingValues,
    onSideChange: (ReportSide) -> Unit,
    onChartChange: (ReportChart) -> Unit,
    onOpenPeriods: () -> Unit,
    onSelectPeriod: (PeriodChoice) -> Unit,
    onDownload: () -> Unit,
    onOpenCategory: (Category, String) -> Unit,
    onAdd: () -> Unit,
) {
    val report = state?.reports?.get(side)
    LazyColumn(contentPadding = screenPadding(contentPadding)) {
        item {
            ScreenTitle("Reports") {
                RoundIconButton(
                    R.drawable.ic_download,
                    contentDescription = "Download report",
                    enabled = state?.hasAnyTransactions == true,
                    onClick = onDownload,
                )
            }
        }
        if (state == null || report == null) return@LazyColumn
        item { PeriodBar(state.period, onOpen = onOpenPeriods, onSelect = onSelectPeriod, modifier = Modifier.padding(top = 4.dp)) }
        item {
            SegmentedToggle(
                options = listOf("Expenses", "Income"),
                selected = side.ordinal,
                onSelect = { onSideChange(ReportSide.entries[it]) },
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        item {
            ChartCard(report, chart, state.today, onChartChange, Modifier.padding(top = 14.dp))
        }
        if (report.lines.isEmpty()) {
            item {
                EmptyNote(
                    title = if (side == ReportSide.EXPENSES) "No spending in this period" else "No money came in",
                    body = if (side == ReportSide.EXPENSES) {
                        "Payments from your bank SMS show up here, and so do the ones you add with +."
                    } else {
                        "Salary, refunds and other money received show up here. Add cash you received with +."
                    },
                )
            }
            item {
                OutlineButton(
                    if (side == ReportSide.EXPENSES) "Add an expense" else "Add income",
                    onClick = onAdd,
                    icon = R.drawable.ic_plus,
                )
            }
            return@LazyColumn
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    if (side == ReportSide.EXPENSES) "All expenses" else "All income",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.comparison?.let { comparison ->
                    Text(
                        "Compared with " + DateRanges.range(comparison.first, comparison.last, state.today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        items(report.lines, key = { "${side.name}-${it.category.name}-${it.label}" }) { line ->
            CategoryCard(line, side, onClick = { onOpenCategory(line.category, line.label) }, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun ChartCard(
    report: Report,
    chart: ReportChart,
    today: LocalDate,
    onChartChange: (ReportChart) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expenses = report.side == ReportSide.EXPENSES
    SpendCard(modifier, contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    chart == ReportChart.RING && expenses -> "Where it went"
                    chart == ReportChart.RING -> "Where it came from"
                    expenses -> "Spending by ${report.trend.unit.noun}"
                    else -> "Income by ${report.trend.unit.noun}"
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconToggle(
                icons = listOf(R.drawable.ic_chart_donut to "Ring chart", R.drawable.ic_chart_bars to "Bar chart"),
                selected = chart.ordinal,
                onSelect = { onChartChange(ReportChart.entries[it]) },
            )
        }
        when (chart) {
            ReportChart.RING -> RingChart(report, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
            ReportChart.BARS -> BarChart(report, today)
        }
    }
}

private fun paymentsText(count: Int) = if (count == 1) "1 payment" else "$count payments"

private fun percentText(share: Float): String {
    val percent = share * 100
    return when {
        percent > 0f && percent < 1f -> "<1%"
        else -> "${percent.roundToInt()}%"
    }
}

/** The ring of category shares; tap a slice to see it in the middle, tap again or the middle to go back. */
@Composable
private fun RingChart(report: Report, modifier: Modifier = Modifier) {
    val colors = SpendTheme.colors
    val lines = report.lines
    var selected by remember(report.side) { mutableStateOf<Pair<Category, String>?>(null) }
    val pick = lines.firstOrNull { (it.category to it.label) == selected }
    val reveal = remember(report.side) { Animatable(0f) }
    LaunchedEffect(report.side) { reveal.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val track = colors.tint
    val expenses = report.side == ReportSide.EXPENSES

    Box(modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = if (lines.isEmpty()) {
                        "Ring chart, empty"
                    } else {
                        "Ring chart: " + lines.joinToString(", ") { "${it.label} ${percentText(it.share)}" }
                    }
                }
                .pointerInput(lines) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val distance = hypot(tap.x - center.x, tap.y - center.y)
                        val outer = min(size.width, size.height) / 2f
                        if (distance < outer * 0.55f || distance > outer || lines.isEmpty()) {
                            selected = null
                            return@detectTapGestures
                        }
                        val degrees = (Math.toDegrees(atan2((tap.y - center.y).toDouble(), (tap.x - center.x).toDouble())) + 90 + 360) % 360
                        var start = 0.0
                        for (line in lines) {
                            val sweep = line.share * 360.0
                            if (degrees < start + sweep) {
                                val key = line.category to line.label
                                selected = if (selected == key) null else key
                                return@detectTapGestures
                            }
                            start += sweep
                        }
                    }
                },
        ) {
            val thin = 30.dp.toPx()
            val thick = 40.dp.toPx()
            val radius = (min(size.width, size.height) - thick) / 2f
            val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(thin))
            if (lines.isEmpty()) return@Canvas
            val gap = if (lines.size > 1) 1.6f else 0f
            var start = -90f
            val total = 360f * reveal.value
            for (line in lines) {
                val full = line.share * 360f
                val sweep = (min(full, max(0f, total - (start + 90f))) - gap).coerceAtLeast(0f)
                if (sweep > 0f) {
                    val on = pick == line
                    val color = colors.category(line.category, line.customName).copy(alpha = if (pick == null || on) 1f else 0.4f)
                    drawArc(
                        color,
                        startAngle = start + gap / 2f,
                        sweepAngle = max(sweep, 0.8f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(if (on) thick else thin),
                    )
                }
                start += full
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
            Text(
                pick?.label ?: if (expenses) "Total spent" else "Total received",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                Money.format(pick?.amountPaise ?: report.totalPaise),
                style = AmountStyle.copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                if (pick != null) "${percentText(pick.share)} · ${paymentsText(pick.count)}" else paymentsText(report.count),
                style = MaterialTheme.typography.bodySmall,
                color = SpendTheme.colors.muted,
            )
        }
    }
}

private val MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

private fun axisLabel(bar: TrendBar, unit: TrendUnit, today: LocalDate): String = when (unit) {
    TrendUnit.MONTH -> bar.first.format(if (bar.first.year == today.year) MONTH else MONTH_YEAR)
    else -> DateRanges.date(bar.first, today)
}

private fun barLabel(bar: TrendBar, today: LocalDate): String =
    DateRanges.range(bar.first, bar.last, today) + " · " + Money.format(bar.amountPaise)

/** Money over time; tap a bar to see its dates and amount. */
@Composable
private fun BarChart(report: Report, today: LocalDate) {
    val colors = SpendTheme.colors
    val trend: Trend = report.trend
    val bars = trend.bars
    var selected by remember(report.side, trend) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val tipText = MaterialTheme.colorScheme.surface
    val idle = if (colors.isDark) Color(0xFF6B5A4A) else Color(0xFFE2CDB7)
    val zero = if (colors.isDark) colors.tint else Color(0xFFF1E6D9)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = tipText)
    val grow = remember(report.side, trend) { Animatable(0f) }
    LaunchedEffect(report.side, trend) { grow.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }

    val average = if (trend.days > 0) report.totalPaise / trend.days else 0
    Text(
        buildString {
            append(Money.format(report.totalPaise))
            append(if (trend.days == 1) " in 1 day" else " in ${trend.days} days")
            if (trend.days > 1 && report.totalPaise > 0) append(" · about ${Money.format(average)} a day")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
    val peak = bars.maxByOrNull { it.amountPaise }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .padding(top = 6.dp)
            .semantics {
                contentDescription = if (peak == null || peak.amountPaise == 0L) {
                    "Bar chart, nothing in this period"
                } else {
                    "Bar chart by ${trend.unit.noun}. Highest: ${barLabel(peak, today)}"
                }
            }
            .pointerInput(bars) {
                detectTapGestures { tap ->
                    if (bars.isEmpty()) return@detectTapGestures
                    val index = (tap.x / (size.width / bars.size)).toInt().coerceIn(0, bars.lastIndex)
                    selected = if (selected == index) null else index
                }
            },
    ) {
        if (bars.isEmpty()) return@Canvas
        val tipRoom = 28.dp.toPx()
        val chartHeight = size.height - tipRoom
        val slot = size.width / bars.size
        val gap = min(4.dp.toPx(), slot * 0.3f)
        val width = min(slot - gap, 40.dp.toPx())
        val most = max(1L, bars.maxOf { it.amountPaise })
        val minHeight = 3.dp.toPx()
        val corner = CornerRadius(min(5.dp.toPx(), width / 2f))
        bars.forEachIndexed { index, bar ->
            val height = if (bar.amountPaise == 0L) minHeight else max(minHeight, chartHeight * bar.amountPaise / most * grow.value)
            val color = when {
                bar.amountPaise == 0L -> zero
                selected == index -> ink
                else -> idle
            }
            drawRoundRect(
                color,
                topLeft = Offset(index * slot + (slot - width) / 2f, size.height - height),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }
        val index = selected ?: return@Canvas
        val bar = bars.getOrNull(index) ?: return@Canvas
        val layout = measurer.measure(barLabel(bar, today), tipStyle)
        val padX = 9.dp.toPx()
        val tipWidth = layout.size.width + padX * 2
        val tipHeight = layout.size.height + 4.dp.toPx()
        val barTop = size.height - (if (bar.amountPaise == 0L) minHeight else max(minHeight, chartHeight * bar.amountPaise / most))
        val centerX = index * slot + slot / 2f
        val left = (centerX - tipWidth / 2f).coerceIn(0f, max(0f, size.width - tipWidth))
        val top = max(0f, barTop - tipHeight - 6.dp.toPx())
        drawRoundRect(ink, Offset(left, top), Size(tipWidth, tipHeight), CornerRadius(tipHeight / 2f))
        drawText(layout, topLeft = Offset(left + padX, top + 2.dp.toPx()))
    }
    if (bars.isNotEmpty()) {
        val labels = listOf(bars.first(), bars[bars.size / 2], bars.last()).distinct()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = if (labels.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
        ) {
            labels.forEach { bar ->
                Text(axisLabel(bar, trend.unit, today), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
    }
}

@Composable
private fun CategoryCard(line: ReportLine, side: ReportSide, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SpendTheme.colors
    val color = colors.category(line.category, line.customName)
    SpendCard(modifier, contentPadding = PaddingValues(0.dp)) {
        Column(
            Modifier
                .clickable(onClickLabel = "See payments", onClick = onClick)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(line.category, customName = line.customName)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        "${percentText(line.share)} of total · ${paymentsText(line.count)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(line.amountPaise), style = AmountStyle, maxLines = 1)
                    line.change?.let {
                        Spacer(Modifier.height(2.dp))
                        ChangeBadge(it, side)
                    }
                }
            }
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.tint),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(line.share.coerceIn(0.01f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
        }
    }
}
