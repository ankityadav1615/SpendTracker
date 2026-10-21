package com.spendtracker.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.spendtracker.MainActivity
import com.spendtracker.SpendTrackerApplication
import com.spendtracker.data.Transaction
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryKind
import com.spendtracker.model.Money
import com.spendtracker.model.TransactionType
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Calendar-month spend/income summary, independent of the app's own period setting (kept simple for glanceability). */
object SpendWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SpendTrackerApplication
        val transactions = app.repository.transactions.first()
        val summary = monthSummary(transactions)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(16.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        "This month",
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        Money.format(summary.spentPaise),
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text("spent", style = TextStyle(color = GlanceTheme.colors.onBackground))
                    if (summary.topCategory != null) {
                        Text(
                            "Top: ${summary.topCategory.label}",
                            style = TextStyle(color = GlanceTheme.colors.onBackground),
                        )
                    }
                    Text(
                        "Income ${Money.format(summary.incomePaise)}",
                        style = TextStyle(color = GlanceTheme.colors.onBackground),
                    )
                }
            }
        }
    }

    private data class MonthSummary(val spentPaise: Long, val incomePaise: Long, val topCategory: Category?)

    private fun monthSummary(transactions: List<Transaction>): MonthSummary {
        val zone = ZoneId.systemDefault()
        val today = Instant.now().atZone(zone).toLocalDate()
        val monthStart = today.with(TemporalAdjusters.firstDayOfMonth())
        val startMillis = monthStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val inMonth = transactions.filter { !it.ignored && it.timestamp >= startMillis }
        val spentPaise = inMonth
            .filter { it.type == TransactionType.DEBIT && it.category.kind == CategoryKind.SPEND }
            .sumOf { it.amountPaise }
        val incomePaise = inMonth
            .filter { it.type == TransactionType.CREDIT && it.category.kind == CategoryKind.INCOME }
            .sumOf { it.amountPaise }
        val topCategory = inMonth
            .filter { it.type == TransactionType.DEBIT && it.category.kind == CategoryKind.SPEND }
            .groupBy { it.category }
            .maxByOrNull { (_, items) -> items.sumOf { it.amountPaise } }
            ?.key
        return MonthSummary(spentPaise, incomePaise, topCategory)
    }
}

/** Registers the widget and refreshes it whenever system widget update broadcasts arrive. */
class SpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendWidget
}
