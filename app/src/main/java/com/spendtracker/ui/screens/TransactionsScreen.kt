package com.spendtracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendtracker.R
import com.spendtracker.data.Transaction
import com.spendtracker.model.Category
import com.spendtracker.model.Money
import com.spendtracker.model.Summary
import com.spendtracker.ui.DashboardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: DashboardState?,
    filter: Category?,
    snackbarHostState: SnackbarHostState,
    onFilterChange: (Category?) -> Unit,
    onBack: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
) {
    val all = state?.transactions.orEmpty()
    val categories = remember(all, filter) {
        (all.map { it.category } + listOfNotNull(filter)).distinct().sortedBy { it.ordinal }
    }
    val shown = remember(all, filter) { if (filter == null) all else all.filter { it.category == filter } }
    val byDay = remember(shown) { shown.groupBy { localDate(it.timestamp) } }
    val totals = remember(shown) { Summary.of(shown) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Transactions", fontWeight = FontWeight.SemiBold)
                        if (state != null) {
                            Text(
                                state.period.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = filter == null, onClick = { onFilterChange(null) }, label = { Text("All") })
                    }
                    items(categories, key = { it.name }) { category ->
                        FilterChip(
                            selected = filter == category,
                            onClick = { onFilterChange(if (filter == category) null else category) },
                            label = { Text(category.label) },
                            leadingIcon = { CategoryDot(category) },
                        )
                    }
                }
            }
            item {
                val parts = buildList {
                    add("${shown.size} ${if (shown.size == 1) "transaction" else "transactions"}")
                    if (totals.spentPaise > 0) add("spent ${Money.format(totals.spentPaise)}")
                    if (totals.incomePaise > 0) add("received ${Money.format(totals.incomePaise)}")
                }
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            byDay.forEach { (day, transactions) ->
                item(key = "day-$day") {
                    Text(
                        formatDayHeader(day),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(transactions, key = { it.id }) { txn ->
                    TransactionRow(txn) { onTransactionClick(txn) }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        "Nothing here for this period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
