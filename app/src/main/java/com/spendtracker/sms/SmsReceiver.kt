package com.spendtracker.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.spendtracker.SpendTrackerApplication
import com.spendtracker.parser.RawSms
import kotlinx.coroutines.launch

/** Captures bank alerts the moment they arrive, even when the app is not open. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as SpendTrackerApplication
        // Android may still grant the permission after the user stopped SMS reading inside the app.
        if (!app.settings.current.smsConsent) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return

        val receivedAt = System.currentTimeMillis()
        // A long SMS arrives as several parts; join them back per sender.
        val messages = parts
            .groupBy { it.displayOriginatingAddress.orEmpty() }
            .map { (sender, list) -> RawSms(sender, list.joinToString("") { it.displayMessageBody.orEmpty() }, receivedAt) }

        val pending = goAsync()
        app.scope.launch {
            try {
                app.repository.ingest(messages)
            } finally {
                pending.finish()
            }
        }
    }
}

object SmsPermission {
    val permissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    fun granted(context: Context): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
