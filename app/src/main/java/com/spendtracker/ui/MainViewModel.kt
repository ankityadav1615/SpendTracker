package com.spendtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.SpendTrackerApplication
import com.spendtracker.data.Transaction
import com.spendtracker.model.AccountBalance
import com.spendtracker.model.Category
import com.spendtracker.model.Period
import com.spendtracker.model.Periods
import com.spendtracker.model.Summary
import com.spendtracker.model.TransactionType
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

data class DashboardState(
    val periods: List<Period>,
    val selectedIndex: Int,
    val summary: Summary,
    /** Transactions of the selected period, newest first. */
    val transactions: List<Transaction>,
    val balances: List<AccountBalance>,
    val hasAnyTransactions: Boolean,
) {
    val period: Period get() = periods[selectedIndex]
    val canGoBack: Boolean get() = selectedIndex > 0
    val canGoForward: Boolean get() = selectedIndex < periods.lastIndex
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpendTrackerApplication
    private val repository = app.repository

    private val allTransactions: StateFlow<List<Transaction>?> = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Start of the period the user picked; null follows the latest period. */
    private val selectedPeriodStart = MutableStateFlow<Long?>(null)

    val dashboard: StateFlow<DashboardState?> = combine(allTransactions, selectedPeriodStart) { txns, selected ->
        txns?.let { buildDashboard(it, selected) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactionsById: StateFlow<Map<Long, Transaction>> = allTransactions
        .map { list -> list.orEmpty().associateBy { it.id } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Reads the SMS inbox. [userRequested] scans the full history and reports the result. */
    fun importSms(userRequested: Boolean) {
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
            } catch (e: SecurityException) {
                _message.value = "SMS permission is needed to read bank messages"
            } finally {
                _importing.value = false
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    fun previousPeriod() = movePeriod(-1)

    fun nextPeriod() = movePeriod(1)

    private fun movePeriod(delta: Int) {
        val state = dashboard.value ?: return
        val target = (state.selectedIndex + delta).coerceIn(0, state.periods.lastIndex)
        selectedPeriodStart.value = if (target == state.periods.lastIndex) null else state.periods[target].start
    }

    fun updateCategory(transaction: Transaction, category: Category, applyToSimilar: Boolean) {
        viewModelScope.launch { repository.updateCategory(transaction, category, applyToSimilar) }
    }

    fun hide(transaction: Transaction) {
        viewModelScope.launch { repository.setIgnored(transaction, true) }
    }

    private fun buildDashboard(all: List<Transaction>, selectedStart: Long?): DashboardState {
        val salaryTimestamps = all.filter { it.type == TransactionType.CREDIT && it.category == Category.SALARY }
            .map { it.timestamp }
        val periods = Periods.compute(salaryTimestamps, all.minOfOrNull { it.timestamp }, System.currentTimeMillis())
        val index = selectedStart?.let { start -> periods.indexOfFirst { it.start == start } }
            ?.takeIf { it >= 0 }
            ?: periods.lastIndex
        val period = periods[index]
        val inPeriod = all.filter { it.timestamp in period }
        return DashboardState(
            periods = periods,
            selectedIndex = index,
            summary = Summary.of(inPeriod),
            transactions = inPeriod,
            balances = Summary.latestBalances(all),
            hasAnyTransactions = all.isNotEmpty(),
        )
    }
}
