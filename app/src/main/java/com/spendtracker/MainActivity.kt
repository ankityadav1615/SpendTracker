package com.spendtracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spendtracker.ui.SpendTrackerApp
import com.spendtracker.ui.theme.SpendTrackerTheme

class MainActivity : ComponentActivity() {
    /** Set by a launcher shortcut ("add_expense"/"add_income"); null means no pending shortcut action. */
    private var shortcutAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        shortcutAction.value = intent?.getStringExtra(EXTRA_SHORTCUT_ACTION)
        setContent {
            SpendTrackerTheme {
                val action by shortcutAction
                SpendTrackerApp(
                    shortcutAction = action,
                    onShortcutActionHandled = { shortcutAction.value = null },
                )
            }
        }
    }

    /** singleTask keeps a single instance; a shortcut tapped while the app is open arrives here instead. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shortcutAction.value = intent.getStringExtra(EXTRA_SHORTCUT_ACTION)
    }

    companion object {
        const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
    }
}
