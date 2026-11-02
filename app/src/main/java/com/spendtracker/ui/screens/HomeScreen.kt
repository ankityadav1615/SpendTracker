package com.spendtracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spendtracker.R
import com.spendtracker.data.AppSettings
import com.spendtracker.model.BudgetStatus
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryTotal
import com.spendtracker.model.CategorySpike
import com.spendtracker.model.Insight
import com.spendtracker.model.Money
import com.spendtracker.model.MoneyLeft
import com.spendtracker.model.OverBudget
import com.spendtracker.model.PeriodChoice
import com.spendtracker.model.SavingsGoalStatus
import com.spendtracker.model.UpcomingBill
import com.spendtracker.ui.MoneyState
import com.spendtracker.ui.theme.SpendTheme
import com.spendtracker.update.UpdateState
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: MoneyState?,
    settings: AppSettings,
    hasPermission: Boolean,
    importing: Boolean,
    contentPadding: PaddingValues,
    onOpenPeriods: () -> Unit,
    onSelectPeriod: (PeriodChoice) -> Unit,
    onSetUpSms: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSetBudget: () -> Unit,
    onOpenCategory: (Category, String) -> Unit,
    onDismissInsight: (String) -> Unit = {},
    onOpenSavingsGoal: () -> Unit = {},
    updateState: UpdateState = UpdateState.None,
    onUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
) {
    LazyColumn(
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state != null) {
            item { PeriodBar(state.period, onOpen = onOpenPeriods, onSelect = onSelectPeriod) }
        }
        if (updateState != UpdateState.None) {
            item(key = "update") { UpdateCard(updateState, onUpdate, onDismissUpdate, onInstallUpdate) }
        }
        if (!settings.smsConsent) {
            item { SetUpSmsCard(onSetUpSms) }
        } else if (!hasPermission) {
            item { PermissionCard(onRequestPermission, onOpenAppSettings) }
        }
        if (state == null) return@LazyColumn
        if (!state.hasAnyTransactions) {
            if (settings.smsConsent && hasPermission) item { NothingYetCard(importing) }
            return@LazyColumn
        }
        if (state.insights.isNotEmpty()) {
            item { InsightsCard(state.insights, onDismiss = onDismissInsight) }
        }
        item {
            MoneyCard(
                state = state,
                onSetBudget = onSetBudget.takeIf { state.period.budgetApplies && settings.monthlyBudgetPaise == null },
            )
        }
        if (state.budgetStatuses.isNotEmpty()) {
            item { BudgetsCard(state.budgetStatuses, onOpenCategory) }
        }
        if (state.savingsGoal != null) {
            item { SavingsGoalCard(state.savingsGoal, onClick = onOpenSavingsGoal) }
        }
        item { WhereItWentCard(state, onOpenCategory) }
    }
}

@Composable
private fun UpdateCard(
    update: UpdateState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    val (title, body) = when (update) {
        is UpdateState.Available -> "New version available" to "Get the latest fixes. It downloads while you keep using the app."
        is UpdateState.Downloading -> "Downloading update" to "Keep using the app. We'll tell you when it's ready."
        UpdateState.ReadyToInstall -> "Update ready" to "Restart SpendTracker to finish. It only takes a few seconds."
        UpdateState.None -> return
    }
    SpendCard(contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Tile(MaterialTheme.colorScheme.primaryContainer) {
                val icon = if (update == UpdateState.ReadyToInstall) R.drawable.ic_check else R.drawable.ic_download
                AppIcon(icon, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (update is UpdateState.Available) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(R.drawable.ic_close, contentDescription = "Not now", size = 16.dp, tint = SpendTheme.colors.muted)
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
        Column(Modifier.padding(top = 16.dp, end = 8.dp)) {
            when (update) {
                is UpdateState.Available -> PrimaryButton("Update now", onClick = onUpdate)
                is UpdateState.Downloading -> {
                    val progress = update.progress
                    val barModifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .semantics { contentDescription = progress?.let { "Downloaded ${(it * 100).roundToInt()}%" } ?: "Downloading" }
                    if (progress == null) {
                        LinearProgressIndicator(barModifier, color = MaterialTheme.colorScheme.primary, trackColor = SpendTheme.colors.tint)
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = barModifier,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = SpendTheme.colors.tint,
                        )
                        Text(
                            "${(progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpendTheme.colors.muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                UpdateState.ReadyToInstall -> PrimaryButton("Restart now", onClick = onInstall)
                UpdateState.None -> Unit
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: List<Insight>, onDismiss: (String) -> Unit) {
    SpendCard(contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
        insights.forEachIndexed { index, insight ->
            if (index > 0) RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tile(insightTint(insight)) {
                    AppIcon(insightIcon(insight), contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(14.dp))
                Text(insight.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onDismiss(insight.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(R.drawable.ic_close, contentDescription = "Dismiss", size = 16.dp, tint = SpendTheme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun insightTint(insight: Insight) = when (insight) {
    is OverBudget -> MaterialTheme.colorScheme.errorContainer
    is UpcomingBill -> MaterialTheme.colorScheme.primaryContainer
    is CategorySpike -> SpendTheme.colors.tint
}

private fun insightIcon(insight: Insight): Int = when (insight) {
    is OverBudget -> R.drawable.ic_chart_donut
    is UpcomingBill -> R.drawable.ic_calendar
    is CategorySpike -> R.drawable.ic_chart_bars
}

@Composable
private fun BudgetsCard(statuses: List<BudgetStatus>, onOpenCategory: (Category, String) -> Unit) {
    SpendCard(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Text("Category budgets", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        statuses.forEachIndexed { index, status ->
            if (index > 0) RowDivider()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCategory(status.category, status.category.label) }
                    .padding(vertical = 10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(status.category.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${Money.format(status.spentPaise)} / ${Money.format(status.budgetPaise)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { status.share.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (status.overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = SpendTheme.colors.tint,
                )
            }
        }
    }
}

@Composable
private fun SavingsGoalCard(goal: SavingsGoalStatus, onClick: () -> Unit) {
    SpendCard(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(goal.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${goal.percent}%",
                style = MaterialTheme.typography.titleLarge,
                color = if (goal.reached) SpendTheme.colors.credit else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (goal.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
            color = if (goal.reached) SpendTheme.colors.credit else MaterialTheme.colorScheme.primary,
            trackColor = SpendTheme.colors.tint,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (goal.reached) {
                "Goal reached! ${Money.format(goal.savedPaise)} saved"
            } else {
                "${Money.format(goal.savedPaise)} of ${Money.format(goal.targetPaise)} saved"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Side margins, plus room for system bars and the navigation bar. */
@Composable
fun screenPadding(contentPadding: PaddingValues) = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = contentPadding.calculateTopPadding() + 4.dp,
    bottom = contentPadding.calculateBottomPadding() + 24.dp,
)

@Composable
private fun MoneyCard(state: MoneyState, onSetBudget: (() -> Unit)?) {
    val left = state.moneyLeft
    val summary = state.summary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val window = state.period.incomeWindow

    val (lead, amount) = when {
        left.basis == MoneyLeft.Basis.SPENT_ONLY -> "Spent so far" to summary.spentPaise
        left.overspent && left.basis == MoneyLeft.Basis.BUDGET -> "Over budget" to -left.leftPaise
        left.overspent -> "Over income" to -left.leftPaise
        left.basis == MoneyLeft.Basis.BUDGET -> "Budget left" to left.leftPaise
        else -> "Money left" to left.leftPaise
    }
    val explanation = when (left.basis) {
        MoneyLeft.Basis.BUDGET -> "Budget ${Money.format(left.basePaise)} · Spent ${Money.format(left.spentPaise)}"
        MoneyLeft.Basis.INCOME -> "Income ${Money.format(left.basePaise)} · Spent ${Money.format(left.spentPaise)} in $window"
        MoneyLeft.Basis.SPENT_ONLY -> "No income found in $window yet."
    }

    SpendCard {
        Text(lead, style = MaterialTheme.typography.bodyLarge, color = muted)
        Text(
            Money.format(amount),
            style = MaterialTheme.typography.displayMedium,
            color = if (left.overspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(explanation, style = MaterialTheme.typography.bodyLarge, color = muted, modifier = Modifier.padding(top = 10.dp))
        if (summary.spentPaise > 0 || left.leftShare > 0f) {
            Spacer(Modifier.height(18.dp))
            MoneyStrip(summary.byCategory, left)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LegendItem(Money.format(summary.spentPaise) + " spent", striped = false)
                if (left.basis != MoneyLeft.Basis.SPENT_ONLY && !left.overspent) {
                    LegendItem(Money.format(left.leftPaise) + " left", striped = true)
                }
            }
        }
        if (onSetBudget != null && left.basis == MoneyLeft.Basis.SPENT_ONLY) {
            TextButton(onClick = onSetBudget, modifier = Modifier.padding(top = 4.dp)) {
                Text("Set a monthly budget", style = MaterialTheme.typography.labelLarge, color = SpendTheme.colors.actionText)
            }
        }
    }
}

/** One bar for the period: a segment per category, then a striped end for what's left. */
@Composable
private fun MoneyStrip(categories: List<CategoryTotal>, left: MoneyLeft) {
    val colors = SpendTheme.colors
    val segments = categories.map { left.shareOf(it.amountPaise) to colors.category(it.category, it.customName) }
        .filter { it.first > 0f }
    val leftShare = left.leftShare
    val spentPercent = ((1f - leftShare) * 100).roundToInt()
    val description = if (left.basis == MoneyLeft.Basis.SPENT_ONLY) {
        "Spending split by category"
    } else {
        "$spentPercent% spent, ${100 - spentPercent}% left"
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .semantics { contentDescription = description },
    ) {
        val parts = segments.size + if (leftShare > 0f) 1 else 0
        if (parts == 0) return@Canvas
        val gap = 2.dp.toPx()
        val total = segments.sumOf { it.first.toDouble() } + leftShare
        val usable = size.width - gap * (parts - 1)
        var x = 0f
        for ((share, color) in segments) {
            val width = (usable * share / total).toFloat()
            drawRect(color, Offset(x, 0f), Size(width, size.height))
            x += width + gap
        }
        if (leftShare > 0f) {
            val width = size.width - x
            clipRect(left = x, right = size.width) {
                drawRect(colors.stripLeftB, Offset(x, 0f), Size(width, size.height))
                val step = 10.dp.toPx() / 1.4f
                var lineX = x - size.height
                while (lineX < size.width) {
                    drawLine(colors.stripLeftA, Offset(lineX, size.height), Offset(lineX + size.height, 0f), strokeWidth = step / 2)
                    lineX += step
                }
                drawRect(colors.line, Offset(x, 0f), Size(width, size.height), style = Stroke(1.dp.toPx()))
            }
        }
    }
}

@Composable
private fun LegendItem(text: String, striped: Boolean) {
    val colors = SpendTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (striped) colors.stripLeftB else MaterialTheme.colorScheme.onSurface)
                .then(if (striped) Modifier.border(1.dp, colors.line, RoundedCornerShape(3.dp)) else Modifier),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WhereItWentCard(state: MoneyState, onOpenCategory: (Category, String) -> Unit) {
    val summary = state.summary
    val payments = summary.byCategory.sumOf { it.count }
    SpendCard(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Where it went", style = MaterialTheme.typography.titleLarge)
            if (payments > 0) {
                Text(
                    if (payments == 1) "1 payment" else "$payments payments",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (summary.byCategory.isEmpty()) {
            Text(
                "Nothing went out in this period.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        summary.byCategory.forEachIndexed { index, total ->
            if (index > 0) RowDivider()
            ListRow(
                leading = { CategoryTile(total.category, customName = total.customName) },
                title = total.label,
                meta = if (total.count == 1) "1 payment" else "${total.count} payments",
                amount = Money.format(total.amountPaise),
                amountNote = percentOf(total.amountPaise, summary.spentPaise),
                onClick = { onOpenCategory(total.category, total.label) },
            )
        }
        if (summary.excludedPaise > 0) {
            RowDivider()
            Text(
                "Not counted as spending: ${Money.format(summary.excludedPaise)} moved between your own accounts or paid to a credit card bill.",
                style = MaterialTheme.typography.bodySmall,
                color = SpendTheme.colors.muted,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            )
        }
    }
}

private fun percentOf(part: Long, whole: Long): String? {
    if (whole <= 0) return null
    val percent = (part * 100.0 / whole).roundToInt()
    return if (percent == 0) "<1%" else "$percent%"
}

@Composable
private fun SetUpSmsCard(onSetUpSms: () -> Unit) {
    SpendCard {
        Tile(MaterialTheme.colorScheme.primaryContainer) {
            AppIcon(R.drawable.ic_sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text("Track spending automatically", style = MaterialTheme.typography.titleLarge)
        Text(
            "SpendTracker can read the bank and UPI SMS on this phone and fill everything in for you.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Set up SMS reading", onClick = onSetUpSms)
        Text(
            "Rather not? Tap + below to add what you spend yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = SpendTheme.colors.muted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit, onOpenAppSettings: () -> Unit) {
    SpendCard {
        Text("SMS access is off", style = MaterialTheme.typography.titleLarge)
        Text(
            "Android hasn't given SpendTracker access to SMS, so bank messages can't be read yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Allow SMS access", onClick = onRequestPermission)
        TextButton(onClick = onOpenAppSettings, modifier = Modifier.padding(top = 4.dp)) {
            Text("Open app settings", style = MaterialTheme.typography.labelLarge, color = SpendTheme.colors.actionText)
        }
        Text(
            "Installed the app yourself and SMS is greyed out? In app settings, tap ⋮ then \"Allow restricted settings\" first.",
            style = MaterialTheme.typography.bodySmall,
            color = SpendTheme.colors.muted,
        )
    }
}

@Composable
private fun NothingYetCard(importing: Boolean) {
    SpendCard {
        Text(
            if (importing) "Reading your bank messages…" else "No bank messages found yet",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Bank and UPI alerts from the last 6 months appear here, and new ones as they arrive. Tap + to add cash spends.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (importing) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = SpendTheme.colors.tint,
            )
        }
    }
}
