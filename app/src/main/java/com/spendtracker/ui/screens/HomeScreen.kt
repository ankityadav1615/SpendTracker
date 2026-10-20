package com.spendtracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.R
import com.spendtracker.data.AppSettings
import com.spendtracker.model.AccountBalance
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryTotal
import com.spendtracker.model.Money
import com.spendtracker.model.MoneyLeft
import com.spendtracker.model.PeriodChoice
import com.spendtracker.ui.MoneyState
import com.spendtracker.ui.theme.SpendTheme
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
    onOpenCategory: (Category) -> Unit,
) {
    LazyColumn(
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state != null) {
            item { PeriodBar(state.period, onOpen = onOpenPeriods, onSelect = onSelectPeriod) }
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
        item {
            MoneyCard(
                state = state,
                onSetBudget = onSetBudget.takeIf { state.period.budgetApplies && settings.monthlyBudgetPaise == null },
            )
        }
        item { WhereItWentCard(state, onOpenCategory) }
        if (state.balances.isNotEmpty()) {
            item { BalancesCard(state.balances, state) }
        }
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
    val strong = SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    val window = state.period.incomeWindow

    val (lead, amount, unit) = when {
        left.basis == MoneyLeft.Basis.SPENT_ONLY -> Triple("You spent", summary.spentPaise, null)
        left.overspent -> Triple("You're", -left.leftPaise, "over")
        else -> Triple("You have", left.leftPaise, "left")
    }
    val explanation = buildAnnotatedString {
        when (left.basis) {
            MoneyLeft.Basis.BUDGET -> if (left.overspent) {
                append("You spent ")
                withStyle(strong) { append(Money.format(left.spentPaise)) }
                append(", more than your ")
                withStyle(strong) { append(Money.format(left.basePaise)) }
                append(" budget.")
            } else {
                append("of your ")
                withStyle(strong) { append(Money.format(left.basePaise)) }
                append(" budget, after spending ")
                withStyle(strong) { append(Money.format(left.spentPaise)) }
                append(".")
            }
            MoneyLeft.Basis.INCOME -> if (left.overspent) {
                withStyle(strong) { append(Money.format(left.spentPaise)) }
                append(" went out, more than the ")
                withStyle(strong) { append(Money.format(left.basePaise)) }
                append(" that came in $window.")
            } else {
                append("from ")
                withStyle(strong) { append(Money.format(left.basePaise)) }
                append(" that came in $window.")
            }
            MoneyLeft.Basis.SPENT_ONLY -> append("No money came in $window, so there's nothing to count it against yet.")
        }
    }

    SpendCard {
        Text(lead, style = MaterialTheme.typography.bodyLarge, color = muted)
        Text(
            buildAnnotatedString {
                append(Money.format(amount))
                if (unit != null) {
                    withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = muted, letterSpacing = 0.sp)) {
                        append(" $unit")
                    }
                }
            },
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
                LegendItem(Money.format(summary.spentPaise) + " went out", striped = false)
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
    val segments = categories.map { left.shareOf(it.amountPaise) to colors.category(it.category) }
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
private fun WhereItWentCard(state: MoneyState, onOpenCategory: (Category) -> Unit) {
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
                leading = { CategoryTile(total.category) },
                title = total.category.label,
                meta = if (total.count == 1) "1 payment" else "${total.count} payments",
                amount = Money.format(total.amountPaise),
                amountNote = percentOf(total.amountPaise, summary.spentPaise),
                onClick = { onOpenCategory(total.category) },
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
private fun BalancesCard(balances: List<AccountBalance>, state: MoneyState) {
    SpendCard(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Text("Bank balances", style = MaterialTheme.typography.titleLarge)
        Text(
            "Latest balance each bank sent you",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        balances.forEachIndexed { index, balance ->
            if (index > 0) RowDivider()
            ListRow(
                leading = {
                    Tile(SpendTheme.colors.tint) {
                        Text(bankInitials(balance.bank), style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                },
                title = balance.bank ?: balance.accountTail?.let { "Account ••$it" } ?: "Bank account",
                meta = listOfNotNull(
                    balance.accountTail?.takeIf { balance.bank != null }?.let { "••$it" },
                    formatWhen(balance.timestamp, state.today),
                ).joinToString(" · "),
                amount = Money.format(balance.balancePaise, withPaise = true),
            )
        }
    }
}

private fun bankInitials(bank: String?): String =
    bank?.filter { it.isLetter() }?.take(2)?.uppercase()?.ifEmpty { null } ?: "₹"

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
            "Bank and UPI alerts from the last 6 months appear here, and new ones as they arrive.",
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
