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
import com.spendtracker.model.Money
import com.spendtracker.model.Recurring
import com.spendtracker.model.TransactionType
import kotlinx.coroutines.flow.first

/** Runs once a day; notifies when a detected recurring bill/EMI is due today or within [WINDOW_DAYS]. */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val app = applicationContext as SpendTrackerApplication
        val notifications = NotificationManagerCompat.from(applicationContext)
        if (!app.settings.current.billReminders || !NotificationPermission.granted(applicationContext)) {
            notifications.cancel(NOTIFICATION_ID)
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val due = Recurring.detect(app.repository.transactions.first())
            .filter { it.type == TransactionType.DEBIT && it.nextDueMillis in now..(now + WINDOW_DAYS * DAY_MILLIS) }

        if (due.isEmpty()) {
            notifications.cancel(NOTIFICATION_ID)
            return Result.success()
        }

        val title = if (due.size == 1) "${due.first().displayName} is due soon" else "${due.size} bills are due soon"
        val body = due.joinToString("\n") { "${it.displayName} • ${Money.format(it.lastAmountPaise)}" }

        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.BILL_REMINDERS)
            .setSmallIcon(R.drawable.ic_calendar)
            .setContentTitle(title)
            .setContentText(due.first().displayName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "bill_reminders"
        private const val NOTIFICATION_ID = 1001
        private const val WINDOW_DAYS = 2L
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
