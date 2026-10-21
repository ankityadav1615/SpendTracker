package com.spendtracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.R
import com.spendtracker.data.Transaction
import com.spendtracker.model.Activity
import com.spendtracker.model.ActivityFilter
import com.spendtracker.ui.MoneyState
import com.spendtracker.ui.theme.SpendTheme

@Composable
fun ActivityScreen(
    state: MoneyState?,
    filter: ActivityFilter,
    searching: Boolean,
    query: String,
    contentPadding: PaddingValues,
    onOpenPeriods: () -> Unit,
    onFilterChange: (ActivityFilter) -> Unit,
    onSearchingChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
) {
    val transactions = state?.transactions.orEmpty()
    val filters = remember(transactions, filter) {
        Activity.filtersFor(transactions).let { if (filter in it) it else it + filter }
    }
    val covered = state?.cashCovered.orEmpty()
    val groups = remember(transactions, filter, query, covered) {
        Activity.byDay(transactions.filter { filter.matches(it) && Activity.matchesQuery(it, query) }, cashCovered = covered)
    }

    LazyColumn(contentPadding = screenPadding(contentPadding)) {
        item {
            ScreenTitle("Activity") {
                IconButton(onClick = { onSearchingChange(!searching) }) {
                    AppIcon(
                        if (searching) R.drawable.ic_close else R.drawable.ic_search,
                        contentDescription = if (searching) "Close search" else "Search",
                    )
                }
            }
        }
        if (searching) {
            item { SearchField(query, onQueryChange) }
        }
        if (state != null) {
            item { DatePill(state.period.label, onClick = onOpenPeriods, modifier = Modifier.padding(top = 4.dp)) }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                items(filters, key = { it.key }) { chip -> FilterPill(chip, selected = chip == filter) { onFilterChange(chip) } }
            }
        }
        if (state != null && groups.isEmpty()) {
            item {
                EmptyNote(
                    title = "Nothing here",
                    body = when {
                        query.isNotBlank() -> "No transactions in this period match \"${query.trim()}\"."
                        filter != ActivityFilter.All -> "No transactions of this kind in this period."
                        else -> "No transactions in this period. Tap + to add one by hand."
                    },
                )
            }
        }
        groups.forEach { group ->
            item(key = "day-${group.date}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        Activity.dayTitle(group.date, state?.today ?: group.date),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (group.hasCounted) {
                        Text(
                            signedNet(group.netPaise),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "list-${group.date}") {
                SpendCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    group.transactions.forEachIndexed { index, txn ->
                        if (index > 0) RowDivider()
                        TransactionRow(txn) { onTransactionClick(txn) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .focusRequester(focus),
        placeholder = { Text("Payee, UPI ID, category or bank") },
        leadingIcon = { AppIcon(R.drawable.ic_search, contentDescription = null, size = 20.dp) },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SpendTheme.colors.card,
            unfocusedContainerColor = SpendTheme.colors.card,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = SpendTheme.colors.line,
        ),
    )
}

@Composable
private fun FilterPill(filter: ActivityFilter, selected: Boolean, onClick: () -> Unit) {
    val colors = SpendTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(filter.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.5.sp)) },
        leadingIcon = if (selected) {
            { AppIcon(R.drawable.ic_check, contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        } else {
            null
        },
        modifier = Modifier.height(36.dp),
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.card,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = if (selected) null else BorderStroke(1.dp, colors.line),
    )
}
