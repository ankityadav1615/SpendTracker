package com.spendtracker

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.spendtracker.data.AppDatabase
import com.spendtracker.data.SettingsStore
import com.spendtracker.data.SmsImportState
import com.spendtracker.data.TransactionRepository
import com.spendtracker.notifications.BillReminderWorker
import com.spendtracker.notifications.NotificationChannels
import com.spendtracker.notifications.WeeklySummaryWorker
import com.spendtracker.sms.SmsInboxImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class SpendTrackerApplication : Application() {
    /** Outlives screens; used for work that must finish even if the UI goes away (e.g. SMS receiver). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.create(this) }
    private val importState by lazy { SmsImportState(this) }
    val repository by lazy { TransactionRepository(database, importState) }
    val inboxImporter by lazy { SmsInboxImporter(this, repository, importState) }
    val settings by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        scheduleNotificationWork()
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
    }
}

