package com.spendtracker.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.sync.Mutex

/** How far the SMS inbox has been imported. Kept in preferences, next to the database. */
class SmsImportState(context: Context) {

    private val prefs = context.getSharedPreferences("sms_import", Context.MODE_PRIVATE)

    /** Held while importing the inbox or wiping data, so the two never interleave. */
    val lock = Mutex()

    /** When the inbox was last imported; 0 before the first import. */
    var lastImport: Long
        get() = prefs.getLong(KEY_LAST_IMPORT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_IMPORT, value) }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_LAST_IMPORT = "last_import"
    }
}
