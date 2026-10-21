package com.spendtracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.SpendTrackerApplication
import com.spendtracker.data.AppSettings
import com.spendtracker.data.CategoryBudget
import com.spendtracker.data.Transaction
import com.spendtracker.export.CsvReport
import com.spendtracker.export.ExportFormat
import com.spendtracker.export.PdfReport
import com.spendtracker.export.ReportDocument
import com.spendtracker.model.AccountBalance
import com.spendtracker.model.Budgets
import com.spendtracker.model.BudgetStatus
import com.spendtracker.model.CashLedger
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryRuleInfo
import com.spendtracker.model.CategoryRules
import com.spendtracker.model.Comparison
import com.spendtracker.model.IncomeType
import com.spendtracker.model.Insight
import com.spendtracker.model.Insights
import com.spendtracker.model.ManualEntry
import com.spendtracker.model.MoneyLeft
import com.spendtracker.model.PeriodChoice
import com.spendtracker.model.PeriodMode
import com.spendtracker.model.PeriodOption
import com.spendtracker.model.PeriodResolver
import com.spendtracker.model.Recurring
import com.spendtracker.model.RecurringPayment
import com.spendtracker.model.Report
import com.spendtracker.model.ReportSide
import com.spendtracker.model.Reports
import com.spendtracker.model.ResolvedPeriod
import com.spendtracker.model.SavingsGoalStatus
import com.spendtracker.model.SavingsGoals
import com.spendtracker.model.Summary
import com.spendtracker.model.TransactionType
import com.spendtracker.sms.SmsPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything Home, Activity and Reports show for the selected period. */
data class MoneyState(
    val period: ResolvedPeriod,
    val options: List<PeriodOption>,
    val summary: Summary,
    val moneyLeft: MoneyLeft,
    /** Transactions of the period, newest first. */
    val transactions: List<Transaction>,
    val balances: List<AccountBalance>,
    val hasAnyTransactions: Boolean,
    val today: LocalDate,
    /** Cash already counted through added cash spends, per withdrawal id (see [CashLedger]). */
    val cashCovered: Map<Long, Long>,
    /** The earlier period the Reports screen compares with, or null when there is none to compare. */
    val comparison: Comparison?,
    val reports: Map<ReportSide, Report>,
    /** Per-category budgets matched against this period's spending, worst overspend first. */
    val budgetStatuses: List<BudgetStatus>,
    /** Payees whose transactions repeat on a steady cadence, soonest due first. */
    val recurringPayments: List<RecurringPayment>,
    /** Dismissible nudges built from budgets, recurring bills and category spikes. */
    val insights: List<Insight>,
    /** The user's savings goal progress, or null when none is set. */
    val savingsGoal: SavingsGoalStatus?,
)

/** A snackbar message, optionally with a button. */
data class UiMessage(val text: String, val action: Action? = null) {
    sealed interface Action {
        val label: String
    }

    /** Puts back a deleted entry. */
    data class Undo(val transaction: Transaction) : Action {
        override val label = "Undo"
    }

    /** Opens a saved report. */
    data class Open(val uri: Uri, val mimeType: String) : Action {
        override val label = "Open"
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpendTrackerApplication
    private val repository = app.repository
    private val settingsStore = app.settings

    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val allTransactions: StateFlow<List<Transaction>?> = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val categoryBudgets: StateFlow<List<CategoryBudget>> = repository.categoryBudgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Remembered payee -> category corrections, with a human-readable payee name, for the rules screen. */
    val categoryRules: StateFlow<List<CategoryRuleInfo>> = combine(repository.categoryRules, allTransactions) { rules, txns ->
        CategoryRules.infos(rules, txns.orEmpty())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What the user picked; null follows the default for their settings. */
    private val choice = MutableStateFlow<PeriodChoice?>(null)

    /** Refreshed when the app comes back, so "today" moves on with the date. */
    private val now = MutableStateFlow(System.currentTimeMillis())

    /** Insight ids the user dismissed this session; a nudge comes back once its underlying cause changes. */
    private val dismissedInsightIds = MutableStateFlow<Set<String>>(emptySet())

    val money: StateFlow<MoneyState?> = combine(allTransactions, settings, choice, now, categoryBudgets) { txns, prefs, picked, time, budgets ->
        txns?.let { buildState(it, prefs, picked, time, budgets) }
    }.combine(dismissedInsightIds) { state, dismissed ->
        state?.copy(insights = state.insights.filterNot { it.id in dismissed })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactionsById: StateFlow<Map<Long, Transaction>> = allTransactions
        .map { list -> list.orEmpty().associateBy { it.id } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    fun say(text: String, action: UiMessage.Action? = null) {
        _message.value = UiMessage(text, action)
    }

    fun onResume() {
        now.value = System.currentTimeMillis()
        importSms(userRequested = false)
    }

    /**
     * Reads the SMS inbox, only once the user agreed on the in-app explanation and Android granted access.
     * [userRequested] scans the full history and reports the result.
     */
    fun importSms(userRequested: Boolean) {
        if (!settingsStore.current.smsConsent || !SmsPermission.granted(app)) return
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                val added = app.inboxImporter.import(fullRescan = userRequested)
                if (userRequested || added > 0) {
                    say(
                        when (added) {
                            0 -> "No new transactions found"
                            1 -> "Added 1 new transaction"
                            else -> "Added $added new transactions"
                        },
                    )
                }
            } catch (_: SecurityException) {
                say("SMS access is needed to read bank messages")
            } finally {
                _importing.value = false
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    fun selectPeriod(picked: PeriodChoice) {
        choice.value = picked
    }

    fun chooseIncomeType(type: IncomeType) {
        settingsStore.update { it.copy(incomeType = type, periodMode = type.defaultMode) }
        choice.value = null
    }

    fun setPeriodMode(mode: PeriodMode, monthStartDay: Int) {
        settingsStore.update { it.copy(periodMode = mode, monthStartDay = monthStartDay.coerceIn(1, 28)) }
        choice.value = null
    }

    fun setBudget(paise: Long?) {
        settingsStore.update { it.copy(monthlyBudgetPaise = paise?.takeIf { p -> p > 0 }) }
    }

    fun setBillReminders(enabled: Boolean) {
        settingsStore.update { it.copy(billReminders = enabled) }
    }

    fun setWeeklySummary(enabled: Boolean) {
        settingsStore.update { it.copy(weeklySummary = enabled) }
    }

    /** Sets or edits the savings goal. Editing an existing goal keeps its original start date, so progress isn't lost. */
    fun setSavingsGoal(name: String, targetPaise: Long, deadlineMillis: Long?) {
        settingsStore.update {
            it.copy(
                savingsGoalName = name,
                savingsGoalTargetPaise = targetPaise,
                savingsGoalDeadlineMillis = deadlineMillis,
                savingsGoalStartedAtMillis = it.savingsGoalStartedAtMillis ?: System.currentTimeMillis(),
            )
        }
    }

    fun clearSavingsGoal() {
        settingsStore.update {
            it.copy(
                savingsGoalName = null,
                savingsGoalTargetPaise = null,
                savingsGoalDeadlineMillis = null,
                savingsGoalStartedAtMillis = null,
            )
        }
    }

    fun setCategoryBudget(category: Category, paise: Long?) {
        viewModelScope.launch { repository.setCategoryBudget(category, paise) }
    }

    fun deleteCategoryRule(key: String) {
        viewModelScope.launch { repository.deleteCategoryRule(key) }
    }

    fun dismissInsight(id: String) {
        dismissedInsightIds.value = dismissedInsightIds.value + id
    }

    /** "Allow SMS access" on the explanation screen; Android's own prompt follows. */
    fun acceptSms() {
        settingsStore.update { it.copy(smsConsent = true, introDone = true) }
    }

    fun skipSms() {
        settingsStore.update { it.copy(introDone = true) }
    }

    /** Stops reading SMS. Android keeps the permission until the user turns it off in system settings. */
    fun stopSms() {
        settingsStore.update { it.copy(smsConsent = false) }
        say("SpendTracker stopped reading SMS")
    }

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAllData()
            settingsStore.clear()
            choice.value = null
        }
    }

    fun updateCategory(transaction: Transaction, category: Category, applyToSimilar: Boolean) {
        viewModelScope.launch { repository.updateCategory(transaction, category, applyToSimilar) }
    }

    fun hide(transaction: Transaction) {
        viewModelScope.launch { repository.setIgnored(transaction, true) }
        say("Hidden. It won't count anywhere.")
    }

    /** Saves an entry from the add sheet, or the changes to [existing]. */
    fun saveEntry(entry: ManualEntry, existing: Transaction?) {
        viewModelScope.launch {
            if (existing == null) repository.addManual(entry) else repository.updateManual(existing, entry)
        }
        val kind = if (entry.type == TransactionType.DEBIT) "Expense" else "Income"
        say(if (existing == null) "$kind added" else "$kind updated")
    }

    fun deleteEntry(transaction: Transaction) {
        viewModelScope.launch { repository.deleteManual(transaction) }
        say("Deleted", UiMessage.Undo(transaction))
    }

    fun undo(action: UiMessage.Undo) {
        viewModelScope.launch { repository.restore(action.transaction) }
    }

    /** Writes the report for [first]..[last] and [categories] (null for all) into the file the user picked. */
    fun export(uri: Uri, format: ExportFormat, first: LocalDate, last: LocalDate, categories: Set<Category>?) {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val all = allTransactions.value.orEmpty()
                    val today = LocalDate.now()
                    val document = ReportDocument.build(all, first, last, categories, CashLedger.covered(all), today)
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
                    stream.use { out ->
                        when (format) {
                            ExportFormat.CSV -> out.write(CsvReport.write(document).toByteArray(Charsets.UTF_8))
                            ExportFormat.PDF -> PdfReport.write(app, document, out)
                        }
                    }
                }
                say("Report saved", UiMessage.Open(uri, format.mimeType))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                say("Couldn't save the report. Try another folder.")
            } finally {
                _exporting.value = false
            }
        }
    }

    private fun buildState(
        all: List<Transaction>,
        prefs: AppSettings,
        picked: PeriodChoice?,
        time: Long,
        budgets: List<CategoryBudget>,
    ): MoneyState {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        val salaryTimestamps = all.filter { it.type == TransactionType.CREDIT && it.category == Category.SALARY }
            .map { it.timestamp }
        val resolver = PeriodResolver(salaryTimestamps, all.minOfOrNull { it.timestamp }, time, prefs.monthStartDay)
        val resolved = resolver.resolve(picked ?: resolver.defaultChoice(prefs.periodMode))
        val inPeriod = all.filter { it.timestamp in resolved.period }
        val covered = CashLedger.covered(all)
        val summary = Summary.of(inPeriod, covered)
        val budget = prefs.monthlyBudgetPaise.takeIf { resolved.budgetApplies }

        val comparison = Reports.comparison(resolved, resolved.previous?.let(resolver::resolve), today, zone)
        val previous = comparison?.let { c -> all.filter { it.timestamp in c.period } }?.takeIf { it.isNotEmpty() }
        val first = resolved.firstDate
            ?: inPeriod.minOfOrNull { it.timestamp }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: resolved.lastDate
        val reports = ReportSide.entries.associateWith { side ->
            Reports.build(side, inPeriod, covered, first, resolved.lastDate, previous, zone)
        }
        val budgetStatuses = Budgets.statuses(budgets, summary.byCategory)
        val recurring = Recurring.detect(all)
        val insights = Insights.build(reports[ReportSide.EXPENSES]?.lines.orEmpty(), budgetStatuses, recurring, time)
        val goalName = prefs.savingsGoalName
        val goalTarget = prefs.savingsGoalTargetPaise
        val savingsGoal = if (goalName != null && goalTarget != null) {
            SavingsGoals.status(goalName, goalTarget, prefs.savingsGoalDeadlineMillis, prefs.savingsGoalStartedAtMillis ?: time, all)
        } else {
            null
        }
        return MoneyState(
            period = resolved,
            options = resolver.options(),
            summary = summary,
            moneyLeft = MoneyLeft.of(summary, budget),
            transactions = inPeriod,
            balances = Summary.latestBalances(all),
            hasAnyTransactions = all.isNotEmpty(),
            today = today,
            cashCovered = covered,
            comparison = comparison.takeIf { previous != null },
            reports = reports,
            budgetStatuses = budgetStatuses,
            recurringPayments = recurring,
            insights = insights,
            savingsGoal = savingsGoal,
        )
    }
}
