package com.spendtracker

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.spendtracker.data.AppDatabase
import com.spendtracker.data.SettingsStore
import com.spendtracker.data.SmsImportState
import com.spendtracker.data.TransactionRepository
import com.spendtracker.notifications.BillReminderWorker
import com.spendtracker.notifications.NotificationChannels
import com.spendtracker.notifications.UpdateCheckWorker
import com.spendtracker.notifications.WeeklySummaryWorker
import com.spendtracker.sms.SmsInboxImporter
import com.spendtracker.update.AppUpdates
import com.spendtracker.widget.SpendWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SpendTrackerApplication : Application() {
    /** Outlives screens; used for work that must finish even if the UI goes away (e.g. SMS receiver). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.create(this) }
    private val importState by lazy { SmsImportState(this) }
    val repository by lazy { TransactionRepository(database, importState) }
    val inboxImporter by lazy { SmsInboxImporter(this, repository, importState) }
    val settings by lazy { SettingsStore(this) }
    val appUpdates by lazy { AppUpdates(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        scheduleNotificationWork()
        refreshWidgetOnDataChanges()
    }

    /** Keeps the home-screen widget in step with new SMS imports, edits and deletions while the app is alive. */
    private fun refreshWidgetOnDataChanges() {
        scope.launch {
            repository.transactions.collect { SpendWidget.updateAll(this@SpendTrackerApplication) }
        }
    }

    /**
     * Always scheduled; each worker checks its own settings toggle and permission before notifying, so
     * turning a feature on or off in Settings takes effect on the next run without re-scheduling work here.
     */
    private fun scheduleNotificationWork() {
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            BillReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            WeeklySummaryWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            UpdateCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

