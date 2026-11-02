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
    /** Notify the day before a detected recurring bill/EMI is expected to repeat. */
    val billReminders: Boolean = false,
    /** Notify once a week with a spend summary for the past 7 days. */
    val weeklySummary: Boolean = false,
    /** Notify when Google Play has a newer version of the app. */
    val updateAlerts: Boolean = true,
    /** What the user is saving towards, e.g. "New laptop". Null when no goal is set. */
    val savingsGoalName: String? = null,
    val savingsGoalTargetPaise: Long? = null,
    /** Optional target date; null means no deadline. */
    val savingsGoalDeadlineMillis: Long? = null,
    /** When the goal was first set; progress only counts savings/investments from this point on. */
    val savingsGoalStartedAtMillis: Long? = null,
)

/** User choices, kept in preferences so they are readable synchronously (e.g. by the SMS receiver). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    private val dismissedInsightsState = MutableStateFlow(readDismissedInsights())

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
            putBoolean(KEY_BILL_REMINDERS, updated.billReminders)
            putBoolean(KEY_WEEKLY_SUMMARY, updated.weeklySummary)
            putBoolean(KEY_UPDATE_ALERTS, updated.updateAlerts)
            if (updated.savingsGoalName == null) remove(KEY_GOAL_NAME) else putString(KEY_GOAL_NAME, updated.savingsGoalName)
            if (updated.savingsGoalTargetPaise == null) remove(KEY_GOAL_TARGET) else putLong(KEY_GOAL_TARGET, updated.savingsGoalTargetPaise)
            if (updated.savingsGoalDeadlineMillis == null) remove(KEY_GOAL_DEADLINE) else putLong(KEY_GOAL_DEADLINE, updated.savingsGoalDeadlineMillis)
            if (updated.savingsGoalStartedAtMillis == null) remove(KEY_GOAL_STARTED) else putLong(KEY_GOAL_STARTED, updated.savingsGoalStartedAtMillis)
        }
        state.value = updated
    }

    /** Forgets every choice, so the app starts with the welcome screens again. */
    @Synchronized
    fun clear() {
        prefs.edit { clear() }
        state.value = AppSettings()
        dismissedInsightsState.value = emptySet()
    }

    /** Insight ids the user dismissed, kept until their underlying cause changes (same id reappears). */
    val dismissedInsightIds: StateFlow<Set<String>> get() = dismissedInsightsState.asStateFlow()

    @Synchronized
    fun dismissInsight(id: String) {
        val updated = dismissedInsightsState.value + id
        prefs.edit { putStringSet(KEY_DISMISSED_INSIGHTS, updated) }
        dismissedInsightsState.value = updated
    }

    /** The newest Play version code already announced by notification, so each version is announced once. */
    var lastNotifiedUpdateVersion: Int
        get() = prefs.getInt(KEY_LAST_NOTIFIED_UPDATE, 0)
        set(value) = prefs.edit { putInt(KEY_LAST_NOTIFIED_UPDATE, value) }

    private fun read() = AppSettings(
        incomeType = IncomeType.fromName(prefs.getString(KEY_INCOME_TYPE, null)),
        periodMode = PeriodMode.entries.firstOrNull { it.name == prefs.getString(KEY_PERIOD_MODE, null) }
            ?: PeriodMode.MONTHLY,
        monthStartDay = prefs.getInt(KEY_MONTH_START_DAY, 1).coerceIn(1, 28),
        monthlyBudgetPaise = if (prefs.contains(KEY_BUDGET)) prefs.getLong(KEY_BUDGET, 0) else null,
        introDone = prefs.getBoolean(KEY_INTRO_DONE, false),
        smsConsent = prefs.getBoolean(KEY_SMS_CONSENT, false),
        billReminders = prefs.getBoolean(KEY_BILL_REMINDERS, false),
        weeklySummary = prefs.getBoolean(KEY_WEEKLY_SUMMARY, false),
        updateAlerts = prefs.getBoolean(KEY_UPDATE_ALERTS, true),
        savingsGoalName = prefs.getString(KEY_GOAL_NAME, null),
        savingsGoalTargetPaise = if (prefs.contains(KEY_GOAL_TARGET)) prefs.getLong(KEY_GOAL_TARGET, 0) else null,
        savingsGoalDeadlineMillis = if (prefs.contains(KEY_GOAL_DEADLINE)) prefs.getLong(KEY_GOAL_DEADLINE, 0) else null,
        savingsGoalStartedAtMillis = if (prefs.contains(KEY_GOAL_STARTED)) prefs.getLong(KEY_GOAL_STARTED, 0) else null,
    )

    private fun readDismissedInsights(): Set<String> =
        prefs.getStringSet(KEY_DISMISSED_INSIGHTS, emptySet())?.toSet() ?: emptySet()

    private companion object {
        const val KEY_INCOME_TYPE = "income_type"
        const val KEY_PERIOD_MODE = "period_mode"
        const val KEY_MONTH_START_DAY = "month_start_day"
        const val KEY_BUDGET = "monthly_budget_paise"
        const val KEY_INTRO_DONE = "intro_done"
        const val KEY_SMS_CONSENT = "sms_consent"
        const val KEY_BILL_REMINDERS = "bill_reminders"
        const val KEY_WEEKLY_SUMMARY = "weekly_summary"
        const val KEY_UPDATE_ALERTS = "update_alerts"
        const val KEY_LAST_NOTIFIED_UPDATE = "last_update_notified_version"
        const val KEY_GOAL_NAME = "savings_goal_name"
        const val KEY_GOAL_TARGET = "savings_goal_target_paise"
        const val KEY_GOAL_DEADLINE = "savings_goal_deadline_millis"
        const val KEY_GOAL_STARTED = "savings_goal_started_at_millis"
        const val KEY_DISMISSED_INSIGHTS = "dismissed_insight_ids"
    }
}
