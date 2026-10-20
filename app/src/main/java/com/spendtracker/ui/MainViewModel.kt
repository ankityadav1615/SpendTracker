package com.spendtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.SpendTrackerApplication
import com.spendtracker.data.AppSettings
import com.spendtracker.data.Transaction
import com.spendtracker.model.AccountBalance
import com.spendtracker.model.Category
import com.spendtracker.model.IncomeType
import com.spendtracker.model.MoneyLeft
import com.spendtracker.model.PeriodChoice
import com.spendtracker.model.PeriodMode
import com.spendtracker.model.PeriodOption
import com.spendtracker.model.PeriodResolver
import com.spendtracker.model.ResolvedPeriod
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything Home and Activity show for the selected period. */
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
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpendTrackerApplication
    private val repository = app.repository
    private val settingsStore = app.settings

    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val allTransactions: StateFlow<List<Transaction>?> = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What the user picked; null follows the default for their settings. */
    private val choice = MutableStateFlow<PeriodChoice?>(null)

    /** Refreshed when the app comes back, so "today" moves on with the date. */
    private val now = MutableStateFlow(System.currentTimeMillis())

    val money: StateFlow<MoneyState?> = combine(allTransactions, settings, choice, now) { txns, prefs, picked, time ->
        txns?.let { buildState(it, prefs, picked, time) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactionsById: StateFlow<Map<Long, Transaction>> = allTransactions
        .map { list -> list.orEmpty().associateBy { it.id } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
                    _message.value = when (added) {
                        0 -> "No new transactions found"
                        1 -> "Added 1 new transaction"
                        else -> "Added $added new transactions"
                    }
                }
            } catch (_: SecurityException) {
                _message.value = "SMS access is needed to read bank messages"
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
        _message.value = "SpendTracker stopped reading SMS"
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
        _message.value = "Hidden. It won't count anywhere."
    }

    private fun buildState(all: List<Transaction>, prefs: AppSettings, picked: PeriodChoice?, time: Long): MoneyState {
        val salaryTimestamps = all.filter { it.type == TransactionType.CREDIT && it.category == Category.SALARY }
            .map { it.timestamp }
        val resolver = PeriodResolver(salaryTimestamps, all.minOfOrNull { it.timestamp }, time, prefs.monthStartDay)
        val resolved = resolver.resolve(picked ?: resolver.defaultChoice(prefs.periodMode))
        val inPeriod = all.filter { it.timestamp in resolved.period }
        val summary = Summary.of(inPeriod)
        val budget = prefs.monthlyBudgetPaise.takeIf { resolved.budgetApplies }
        return MoneyState(
            period = resolved,
            options = resolver.options(),
            summary = summary,
            moneyLeft = MoneyLeft.of(summary, budget),
            transactions = inPeriod,
            balances = Summary.latestBalances(all),
            hasAnyTransactions = all.isNotEmpty(),
            today = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate(),
        )
    }
}
