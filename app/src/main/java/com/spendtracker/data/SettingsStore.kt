package com.spendtracker.data

import android.content.Context
import androidx.core.content.edit
import com.spendtracker.model.IncomeType
import com.spendtracker.model.PeriodMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** Null until the user answers the welcome question. */
    val incomeType: IncomeType? = null,
    val periodMode: PeriodMode = PeriodMode.MONTHLY,
    /** Day of the month that months start on (1..28), for [PeriodMode.MONTHLY]. */
    val monthStartDay: Int = 1,
    val monthlyBudgetPaise: Long? = null,
    /** The welcome and SMS explanation screens were seen (whatever the answer). */
    val introDone: Boolean = false,
    /** The user agreed to SMS reading on the in-app explanation, before Android's permission prompt. */
    val smsConsent: Boolean = false,
)

/** User choices, kept in preferences so they are readable synchronously (e.g. by the SMS receiver). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    val current: AppSettings get() = state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(state.value)
        prefs.edit {
            putString(KEY_INCOME_TYPE, updated.incomeType?.name)
            putString(KEY_PERIOD_MODE, updated.periodMode.name)
            putInt(KEY_MONTH_START_DAY, updated.monthStartDay)
            if (updated.monthlyBudgetPaise == null) remove(KEY_BUDGET) else putLong(KEY_BUDGET, updated.monthlyBudgetPaise)
            putBoolean(KEY_INTRO_DONE, updated.introDone)
            putBoolean(KEY_SMS_CONSENT, updated.smsConsent)
        }
        state.value = updated
    }

    /** Forgets every choice, so the app starts with the welcome screens again. */
    @Synchronized
    fun clear() {
        prefs.edit { clear() }
        state.value = AppSettings()
    }

    private fun read() = AppSettings(
        incomeType = IncomeType.fromName(prefs.getString(KEY_INCOME_TYPE, null)),
        periodMode = PeriodMode.entries.firstOrNull { it.name == prefs.getString(KEY_PERIOD_MODE, null) }
            ?: PeriodMode.MONTHLY,
        monthStartDay = prefs.getInt(KEY_MONTH_START_DAY, 1).coerceIn(1, 28),
        monthlyBudgetPaise = if (prefs.contains(KEY_BUDGET)) prefs.getLong(KEY_BUDGET, 0) else null,
        introDone = prefs.getBoolean(KEY_INTRO_DONE, false),
        smsConsent = prefs.getBoolean(KEY_SMS_CONSENT, false),
    )

    private companion object {
        const val KEY_INCOME_TYPE = "income_type"
        const val KEY_PERIOD_MODE = "period_mode"
        const val KEY_MONTH_START_DAY = "month_start_day"
        const val KEY_BUDGET = "monthly_budget_paise"
        const val KEY_INTRO_DONE = "intro_done"
        const val KEY_SMS_CONSENT = "sms_consent"
    }
}
