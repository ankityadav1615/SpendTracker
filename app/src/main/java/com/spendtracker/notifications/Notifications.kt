package com.spendtracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime notification permission is only required from Android 13 (API 33) onward. */
object NotificationPermission {
    val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    fun granted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}

/** Channel ids used by the notification workers. */
object NotificationChannels {
    const val BILL_REMINDERS = "bill_reminders"
    const val WEEKLY_SUMMARY = "weekly_summary"
    const val APP_UPDATES = "app_updates"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                BILL_REMINDERS,
                "Bill & EMI reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Nudges the day before a recurring bill or EMI is expected" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WEEKLY_SUMMARY,
                "Weekly spend summary",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "A weekly recap of where your money went" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                APP_UPDATES,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Tells you when a new version of SpendTracker is on Google Play" },
        )
    }
}
