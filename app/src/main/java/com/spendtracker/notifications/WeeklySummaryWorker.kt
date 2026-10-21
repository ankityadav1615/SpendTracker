package com.spendtracker.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendtracker.MainActivity
import com.spendtracker.R
import com.spendtracker.SpendTrackerApplication
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryKind
import com.spendtracker.model.Money
import com.spendtracker.model.TransactionType
import kotlinx.coroutines.flow.first

/** Runs once a week; notifies with a recap of spend over the trailing 7 days. */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val app = applicationContext as SpendTrackerApplication
        val notifications = NotificationManagerCompat.from(applicationContext)
        if (!app.settings.current.weeklySummary || !NotificationPermission.granted(applicationContext)) {
            notifications.cancel(NOTIFICATION_ID)
            return Result.success()
        }

        val since = System.currentTimeMillis() - WINDOW_DAYS * DAY_MILLIS
        val spend = app.repository.transactions.first()
            .filter {
                !it.ignored && it.type == TransactionType.DEBIT &&
                    it.category.kind == CategoryKind.SPEND && it.timestamp >= since
            }
        if (spend.isEmpty()) return Result.success()

        val total = spend.sumOf { it.amountPaise }
        val topCategory: Pair<Category, Long>? = spend
            .groupBy { it.category }
            .mapValues { (_, lines) -> lines.sumOf { it.amountPaise } }
            .maxByOrNull { it.value }
            ?.toPair()

        val title = "You spent ${Money.format(total)} this week"
        val body = topCategory?.let { (category, amount) ->
            "Most of it went to ${category.label} (${Money.format(amount)})"
        } ?: "Open SpendTracker for the full breakdown"

        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.WEEKLY_SUMMARY)
            .setSmallIcon(R.drawable.ic_chart_bars)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "weekly_summary"
        private const val NOTIFICATION_ID = 1002
        private const val WINDOW_DAYS = 7L
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
