package com.spendtracker.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.spendtracker.MainActivity
import com.spendtracker.R
import com.spendtracker.SpendTrackerApplication
import com.spendtracker.update.UpdateDecisions
import com.spendtracker.update.awaitResult
import kotlinx.coroutines.CancellationException

/** Runs once a day; notifies once for each new version that Google Play offers. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val app = applicationContext as SpendTrackerApplication
        if (!app.settings.current.updateAlerts || !NotificationPermission.granted(applicationContext)) {
            cancelNotification(applicationContext)
            return Result.success()
        }

        val info = try {
            AppUpdateManagerFactory.create(applicationContext).appUpdateInfo.awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Not installed from Google Play, or the Play Store couldn't be reached; try again tomorrow.
            return Result.success()
        }

        val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        if (!available) {
            cancelNotification(applicationContext)
            return Result.success()
        }
        val version = info.availableVersionCode()
        if (!UpdateDecisions.shouldNotify(available, version, app.settings.lastNotifiedUpdateVersion)) {
            return Result.success()
        }

        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val body = "A new version is ready on Google Play. Tap to update."
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.APP_UPDATES)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("SpendTracker update available")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        app.settings.lastNotifiedUpdateVersion = version
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "update_check"
        private const val NOTIFICATION_ID = 1003

        fun cancelNotification(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }
}
