package com.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendtracker.R
import com.spendtracker.data.Transaction
import com.spendtracker.model.AccountBalance
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryTotal
import com.spendtracker.model.Money
import com.spendtracker.model.Summary
import com.spendtracker.ui.DashboardState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState?,
    hasPermission: Boolean,
    importing: Boolean,
    snackbarHostState: SnackbarHostState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onOpenTransactions: (Category?) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SpendTracker", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else if (hasPermission) {
                        IconButton(onClick = onRescan) {
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Scan SMS inbox again")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasPermission) {
                item { PermissionCard(onRequestPermission, onOpenSettings) }
            }
            when {
                state == null -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

                !state.hasAnyTransactions -> item { EmptyState(hasPermission, importing, onRescan) }

                else -> {
                    item { PeriodSelector(state, onPreviousPeriod, onNextPeriod) }
                    item { SummaryCard(state.summary) }
                    item { SectionHeader("Where your money went") }
                    item {
                        if (state.summary.byCategory.isEmpty()) {
                            Text(
                                "No spending in this period yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            CategoryBreakdown(state.summary, onCategoryClick = onOpenTransactions)
                        }
                    }
                    if (state.summary.excludedPaise > 0) {
                        item {
                            Text(
                                "${Money.format(state.summary.excludedPaise)} moved between your own accounts or paid " +
                                    "to credit cards is not counted as spending.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        SectionHeader(
                            title = "Recent transactions",
                            actionLabel = if (state.transactions.isEmpty()) null else "See all",
                            onAction = { onOpenTransactions(null) },
                        )
                    }
                    item {
                        if (state.transactions.isEmpty()) {
                            Text(
                                "No transactions in this period.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Card {
                                state.transactions.take(5).forEachIndexed { index, txn ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp))
                                    TransactionRow(txn) { onTransactionClick(txn) }
                                }
                            }
                        }
                    }
                    if (state.balances.isNotEmpty()) {
                        item { SectionHeader("Last balance reported by your bank") }
                        item { BalancesCard(state.balances) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit, onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Allow SMS access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "SpendTracker reads the alert SMS your bank sends for every UPI, card and account transaction, " +
                    "so you never have to type anything in. Messages are processed on this phone only and never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestPermission) { Text("Allow") }
                OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
            }
        }
    }
}

@Composable
private fun EmptyState(hasPermission: Boolean, importing: Boolean, onRescan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("No transactions yet", style = MaterialTheme.typography.titleLarge)
        Text(
            if (hasPermission) {
                "Bank and UPI alerts from the last 6 months are imported automatically, and every new one " +
                    "is added as it arrives."
            } else {
                "Allow SMS access above and your bank transactions will show up here automatically."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (hasPermission) {
            Button(onClick = onRescan, enabled = !importing) {
                Text(if (importing) "Scanning…" else "Scan SMS inbox")
            }
        }
    }
}

@Composable
private fun PeriodSelector(state: DashboardState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = state.canGoBack) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous period")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.period.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                state.period.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(onClick = onNext, enabled = state.canGoForward) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next period")
        }
    }
}

@Composable
private fun SummaryCard(summary: Summary) {
    val hasIncome = summary.incomePaise > 0
    val overspent = summary.leftPaise < 0
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                when {
                    !hasIncome -> "Spent"
                    overspent -> "Overspent"
                    else -> "Left to spend"
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                Money.format(if (hasIncome) kotlin.math.abs(summary.leftPaise) else summary.spentPaise),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (overspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (hasIncome) {
                val fraction = (summary.spentPaise.toDouble() / summary.incomePaise).toFloat()
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (overspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(fraction * 100).roundToInt()}% of the money that came in has been spent",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Metric("Salary", summary.salaryPaise, Modifier.weight(1f))
                Metric("Other income", summary.otherIncomePaise, Modifier.weight(1f))
                Metric("Spent", summary.spentPaise, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, paise: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(Money.format(paise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryBreakdown(summary: Summary, onCategoryClick: (Category) -> Unit) {
    Card {
        Row(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            summary.byCategory.forEach { total ->
                Box(
                    Modifier
                        .weight(total.amountPaise.toFloat())
                        .fillMaxHeight()
                        .background(Color(total.category.color)),
                )
            }
        }
        summary.byCategory.forEach { total ->
            CategoryRow(total, summary.spentPaise) { onCategoryClick(total.category) }
        }
    }
}

@Composable
private fun CategoryRow(total: CategoryTotal, spentPaise: Long, onClick: () -> Unit) {
    val share = if (spentPaise > 0) (total.amountPaise.toDouble() / spentPaise).toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDot(total.category, 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(total.category.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    Money.format(total.amountPaise),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "${total.count} ${if (total.count == 1) "payment" else "payments"} · ${(share * 100).roundToInt()}% of spending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BalancesCard(balances: List<AccountBalance>) {
    Card {
        balances.forEachIndexed { index, balance ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        accountLabel(balance.bank, balance.accountTail) ?: "Account",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "as of ${formatDateTime(balance.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    Money.format(balance.balancePaise),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
