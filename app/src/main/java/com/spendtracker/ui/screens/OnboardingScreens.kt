package com.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.R
import com.spendtracker.model.Category
import com.spendtracker.model.IncomeType
import com.spendtracker.ui.theme.SpendTheme

private fun IncomeType.icon(): Int = when (this) {
    IncomeType.SALARY -> R.drawable.ic_cat_income
    IncomeType.BUSINESS -> R.drawable.ic_src_business
    IncomeType.FREELANCE -> R.drawable.ic_src_freelance
    IncomeType.ALLOWANCE -> R.drawable.ic_src_student
    IncomeType.MIXED -> R.drawable.ic_src_mix
}

/** Borrows a category's colour so each choice has its own tile colour. */
private fun IncomeType.colorOf(): Category = when (this) {
    IncomeType.SALARY -> Category.SALARY
    IncomeType.BUSINESS -> Category.RENT
    IncomeType.FREELANCE -> Category.FUEL
    IncomeType.ALLOWANCE -> Category.EMI
    IncomeType.MIXED -> Category.DAILY
}

@Composable
private fun OnboardingFrame(step: Int?, actions: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        ) {
            if (step != null) StepProgress(step)
            content()
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { actions() }
    }
}

@Composable
private fun StepProgress(step: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(2) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < step) MaterialTheme.colorScheme.onSurface else SpendTheme.colors.line),
            )
        }
    }
}

@Composable
fun WelcomeScreen(selected: IncomeType?, onSelect: (IncomeType) -> Unit, onContinue: () -> Unit) {
    OnboardingFrame(
        step = 1,
        actions = { PrimaryButton("Continue", onClick = onContinue, enabled = selected != null) },
    ) {
        Text("Where does your money come from?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "SpendTracker uses this to decide how to split your time. You can change it any time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(22.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IncomeType.entries.forEach { type ->
                IncomeOption(type, selected = type == selected) { onSelect(type) }
            }
        }
    }
}

@Composable
fun IncomeOption(type: IncomeType, selected: Boolean, onClick: () -> Unit) {
    val colors = SpendTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val tileColor = type.colorOf()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(colors.card)
            .border(
                if (selected) 2.dp else 1.5.dp,
                if (selected) MaterialTheme.colorScheme.primary else colors.line,
                shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tile(colors.categoryTile(tileColor)) {
            AppIcon(type.icon(), contentDescription = null, tint = colors.category(tileColor))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(type.label, style = MaterialTheme.typography.titleMedium)
            Text(type.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else colors.card)
                .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else colors.muted.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) AppIcon(R.drawable.ic_check, contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/**
 * The prominent disclosure Google Play requires before Android's SMS permission prompt: what is read,
 * what is kept, and how to undo it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisclosureScreen(showProgress: Boolean, onAllow: () -> Unit, onNotNow: () -> Unit, onPrivacy: () -> Unit) {
    val colors = SpendTheme.colors
    OnboardingFrame(
        step = if (showProgress) 2 else null,
        actions = {
            PrimaryButton("Allow SMS access", onClick = onAllow)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onNotNow) {
                    Text("Not now", style = MaterialTheme.typography.labelLarge, color = colors.actionText)
                }
                TextButton(onClick = onPrivacy) {
                    Text("Privacy policy", style = MaterialTheme.typography.labelLarge, color = colors.actionText)
                }
            }
        },
    ) {
        if (!showProgress) Spacer(Modifier.height(16.dp))
        Text("SpendTracker reads your bank SMS", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))
        SpendCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            val mark = SpanStyle(
                background = colors.card,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
            )
            Text(
                buildAnnotatedString {
                    append("Bank A/c ")
                    withStyle(mark) { append("XX3485") }
                    append(" debited for Rs ")
                    withStyle(mark) { append("450.00") }
                    append(" on ")
                    withStyle(mark) { append("20-Oct-26") }
                    append("; ")
                    withStyle(mark) { append("GREEN BASKET") }
                    append(" credited. UPI:629318840412.")
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp))
                    .background(colors.tint)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Text(
                "Becomes one entry:",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.padding(top = 10.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                listOf("−₹450", "Green Basket", "A/c ••3485", "20 Oct", "Groceries").forEach { part ->
                    Text(
                        part,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.card)
                            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DisclosurePoint(
                R.drawable.ic_message,
                "Only bank and UPI messages are used. Chats, OTPs and personal texts are skipped and never saved.",
            )
            DisclosurePoint(
                R.drawable.ic_phone,
                "Everything stays on this phone. The app has no internet access, no login and no ads.",
            )
            DisclosurePoint(
                R.drawable.ic_trash,
                "Changed your mind? Stop SMS reading in SpendTracker's Settings or Android settings, and delete all data from SpendTracker's Settings.",
            )
        }
    }
}

@Composable
private fun DisclosurePoint(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp))
    }
}
