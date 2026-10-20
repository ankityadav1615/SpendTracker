package com.spendtracker

import android.app.Application
import com.spendtracker.data.AppDatabase
import com.spendtracker.data.TransactionRepository
import com.spendtracker.sms.SmsInboxImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SpendTrackerApplication : Application() {
    /** Outlives screens; used for work that must finish even if the UI goes away (e.g. SMS receiver). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { TransactionRepository(database) }
    val inboxImporter by lazy { SmsInboxImporter(this, repository) }
}
