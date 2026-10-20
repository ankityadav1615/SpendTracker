package com.spendtracker.sms

import android.content.Context
import android.provider.Telephony
import androidx.core.content.edit
import com.spendtracker.data.TransactionRepository
import com.spendtracker.parser.RawSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Reads bank alerts already sitting in the SMS inbox. */
class SmsInboxImporter(private val context: Context, private val repository: TransactionRepository) {

    private val prefs = context.getSharedPreferences("sms_import", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /**
     * Imports inbox messages. The first run (and [fullRescan]) looks back [HISTORY_DAYS] days; later runs
     * only re-read the last couple of days to catch anything the live receiver missed.
     * Duplicates are skipped, so running this often is safe. Returns the number of new transactions.
     */
    suspend fun import(fullRescan: Boolean): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!SmsPermission.granted(context)) return@withContext 0
            val now = System.currentTimeMillis()
            val lastImport = prefs.getLong(KEY_LAST_IMPORT, 0L)
            val since = if (fullRescan || lastImport == 0L) {
                now - TimeUnit.DAYS.toMillis(HISTORY_DAYS)
            } else {
                lastImport - TimeUnit.DAYS.toMillis(2)
            }
            val added = repository.ingest(read(since))
            prefs.edit { putLong(KEY_LAST_IMPORT, now) }
            added
        }
    }

    private fun read(sinceMillis: Long): List<RawSms> {
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return emptyList()
        return cursor.use {
            val address = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            buildList {
                while (it.moveToNext()) {
                    val text = it.getString(body) ?: continue
                    add(RawSms(it.getString(address).orEmpty(), text, it.getLong(date)))
                }
            }
        }
    }

    private companion object {
        const val KEY_LAST_IMPORT = "last_import"
        const val HISTORY_DAYS = 180L
    }
}
