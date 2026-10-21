package com.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendtracker.R
import com.spendtracker.data.AppSettings
import com.spendtracker.model.IncomeType
import com.spendtracker.model.Money
import com.spendtracker.model.PeriodMode
import com.spendtracker.ui.theme.SpendTheme
import java.time.LocalDate

private enum class SettingsDialog { NONE, INCOME, PERIODS, BUDGET, GOAL, STOP_SMS, DELETE }

@Composable
fun SettingsScreen(
    settings: AppSettings,
    hasPermission: Boolean,
    importing: Boolean,
    contentPadding: PaddingValues,
    categoryBudgetCount: Int,
    categoryRuleCount: Int,
    onChooseIncomeType: (IncomeType) -> Unit,
    onSetPeriodMode: (PeriodMode, Int) -> Unit,
    onSetBudget: (Long?) -> Unit,
    onOpenCategoryBudgets: () -> Unit,
    onOpenCategoryRules: () -> Unit,
    onSetUpSms: () -> Unit,
    onRequestPermission: () -> Unit,
    onStopSms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRescan: () -> Unit,
    onSetBillReminders: (Boolean) -> Unit,
    onSetWeeklySummary: (Boolean) -> Unit,
    onSetSavingsGoal: (String, Long, Long?) -> Unit,
    onClearSavingsGoal: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicences: () -> Unit,
    onDeleteAll: () -> Unit,
    budgetRequested: Boolean,
    onBudgetRequestHandled: () -> Unit,
    savingsGoalRequested: Boolean,
    onSavingsGoalRequestHandled: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(SettingsDialog.NONE) }
    LaunchedEffect(budgetRequested) {
        if (budgetRequested) {
            dialog = SettingsDialog.BUDGET
            onBudgetRequestHandled()
        }
    }
    LaunchedEffect(savingsGoalRequested) {
        if (savingsGoalRequested) {
            dialog = SettingsDialog.GOAL
            onSavingsGoalRequestHandled()
        }
    }
    val smsOn = settings.smsConsent && hasPermission
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    LazyColumn(contentPadding = screenPadding(contentPadding)) {
        item { ScreenTitle("Settings") }
        item { GroupLabel("Your money") }
        item {
            SettingsGroup {
                SettingsRow("Money comes in as", settings.incomeType?.label ?: "Not chosen") { dialog = SettingsDialog.INCOME }
                RowDivider()
                SettingsRow("Periods follow", periodsText(settings)) { dialog = SettingsDialog.PERIODS }
                RowDivider()
                SettingsRow(
                    "Monthly budget",
                    settings.monthlyBudgetPaise?.let { Money.format(it) } ?: "Not set",
                ) { dialog = SettingsDialog.BUDGET }
                RowDivider()
                SettingsRow(
                    "Category budgets",
                    if (categoryBudgetCount == 0) "Not set" else "$categoryBudgetCount " + if (categoryBudgetCount == 1) "category" else "categories",
                    onClick = onOpenCategoryBudgets,
                )
                RowDivider()
                SettingsRow(
                    "Category rules",
                    if (categoryRuleCount == 0) "None remembered yet" else "$categoryRuleCount remembered",
                    onClick = onOpenCategoryRules,
                )
                RowDivider()
                SettingsRow(
                    "Savings goal",
                    settings.savingsGoalName?.let { "$it · ${Money.format(settings.savingsGoalTargetPaise ?: 0)}" } ?: "Not set",
                    onClick = { dialog = SettingsDialog.GOAL },
                )
            }
        }
        item { GroupLabel("Bank messages") }
        item {
            SettingsGroup {
                SettingsRow(
                    title = "SMS access",
                    value = when {
                        smsOn -> "Allowed"
                        settings.smsConsent -> "Not allowed in Android settings"
                        else -> "Off"
                    },
                    dotColor = if (smsOn) SpendTheme.colors.credit else null,
                ) {
                    when {
                        smsOn -> dialog = SettingsDialog.STOP_SMS
                        settings.smsConsent -> onRequestPermission()
                        else -> onSetUpSms()
                    }
                }
                RowDivider()
                SettingsRow(
                    title = "Scan inbox again",
                    value = when {
                        importing -> "Scanning…"
                        smsOn -> "Find bank messages you missed"
                        else -> "Turn on SMS access first"
                    },
                    trailing = R.drawable.ic_refresh,
                    trailingTint = if (smsOn) MaterialTheme.colorScheme.primary else SpendTheme.colors.muted,
                    enabled = smsOn && !importing,
                    onClick = onRescan,
                )
            }
        }
        item { GroupLabel("Reminders") }
        item {
            SettingsGroup {
                SwitchRow(
                    title = "Bill & EMI reminders",
                    value = "Nudge the day before a recurring payment is due",
                    checked = settings.billReminders,
                    onCheckedChange = onSetBillReminders,
                )
                RowDivider()
                SwitchRow(
                    title = "Weekly summary",
                    value = "A recap notification once a week",
                    checked = settings.weeklySummary,
                    onCheckedChange = onSetWeeklySummary,
                )
            }
        }
        item { GroupLabel("Privacy") }
        item {
            SettingsGroup {
                SettingsRow("Privacy policy", "What SpendTracker reads and keeps", onClick = onOpenPrivacy)
                RowDivider()
                SettingsRow("Open-source licences", "Fonts used in the app", onClick = onOpenLicences)
                RowDivider()
                SettingsRow(
                    title = "Delete all data",
                    value = "Removes every transaction, photo and setting from this phone",
                    trailing = R.drawable.ic_trash,
                    trailingTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                ) { dialog = SettingsDialog.DELETE }
            }
        }
        item {
            Text(
                listOfNotNull(version?.let { "SpendTracker $it" } ?: "SpendTracker", "works fully offline").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = SpendTheme.colors.muted,
                modifier = Modifier.padding(start = 8.dp, top = 18.dp),
            )
        }
    }

    val close = { dialog = SettingsDialog.NONE }
    when (dialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.INCOME -> IncomeTypeDialog(settings.incomeType, onDismiss = close) {
            onChooseIncomeType(it)
            close()
        }
        SettingsDialog.PERIODS -> PeriodModeDialog(settings, onDismiss = close) { mode, day ->
            onSetPeriodMode(mode, day)
            close()
        }
        SettingsDialog.BUDGET -> BudgetDialog(settings.monthlyBudgetPaise, onDismiss = close) {
            onSetBudget(it)
            close()
        }
        SettingsDialog.GOAL -> SavingsGoalDialog(
            name = settings.savingsGoalName,
            targetPaise = settings.savingsGoalTargetPaise,
            deadlineMillis = settings.savingsGoalDeadlineMillis,
            onDismiss = close,
            onSave = { name, target, deadline ->
                onSetSavingsGoal(name, target, deadline)
                close()
            },
            onClear = {
                onClearSavingsGoal()
                close()
            },
        )
        SettingsDialog.STOP_SMS -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Stop reading SMS?") },
            text = {
                Text(
                    "SpendTracker will stop reading bank messages. Transactions already saved stay until you delete them. " +
                        "To take back the permission itself, turn off SMS for SpendTracker in Android settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onStopSms()
                    close()
                }) { Text("Stop reading", color = SpendTheme.colors.actionText) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onOpenAppSettings()
                    close()
                }) { Text("Android settings", color = SpendTheme.colors.actionText) }
            },
        )
        SettingsDialog.DELETE -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This removes every transaction, receipt photo, category choice and setting from this phone, and can't be undone. " +
                        "Your SMS inbox isn't touched. SMS access stays on in Android settings until you turn it off there.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAll()
                    close()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = close) { Text("Cancel", color = SpendTheme.colors.actionText) } },
        )
    }
}

private fun periodsText(settings: AppSettings): String = when (settings.periodMode) {
    PeriodMode.INCOME_CYCLE -> "Payday (pay cycle)"
    PeriodMode.MONTHLY -> if (settings.monthStartDay == 1) "Calendar month" else "Month from the ${ordinal(settings.monthStartDay)}"
}

private fun ordinal(day: Int): String {
    val suffix = if (day in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    SpendCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { content() }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    trailing: Int = R.drawable.ic_chevron_right,
    trailingTint: Color = SpendTheme.colors.muted,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    dotColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dotColor != null) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        AppIcon(trailing, contentDescription = null, size = 20.dp, tint = trailingTint)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun RadioRow(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = SpendTheme.colors.muted),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IncomeTypeDialog(current: IncomeType?, onDismiss: () -> Unit, onChoose: (IncomeType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Money comes in as") },
        text = {
            Column(
                Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "This also resets how periods are split.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                IncomeType.entries.forEach { type ->
                    RadioRow(type.label, type.hint, selected = type == current) { onChoose(type) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SpendTheme.colors.actionText) } },
    )
}

@Composable
private fun PeriodModeDialog(settings: AppSettings, onDismiss: () -> Unit, onSave: (PeriodMode, Int) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(settings.periodMode) }
    var day by rememberSaveable { mutableIntStateOf(settings.monthStartDay) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Periods follow") },
        text = {
            Column(Modifier.selectableGroup()) {
                RadioRow(
                    "Payday (pay cycle)",
                    "From one salary credit to the next. Uses months until a salary is found.",
                    selected = mode == PeriodMode.INCOME_CYCLE,
                ) { mode = PeriodMode.INCOME_CYCLE }
                RadioRow("Calendar month", "Starts on the same day every month", selected = mode == PeriodMode.MONTHLY) {
                    mode = PeriodMode.MONTHLY
                }
                if (mode == PeriodMode.MONTHLY) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 36.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Starts on the ${ordinal(day)}", style = MaterialTheme.typography.bodyLarge)
                        Row {
                            RoundIconButton(R.drawable.ic_chevron_left, "Earlier day", enabled = day > 1) { day-- }
                            RoundIconButton(R.drawable.ic_chevron_right, "Later day", enabled = day < 28) { day++ }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(mode, day) }) { Text("Save", color = SpendTheme.colors.actionText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SpendTheme.colors.actionText) } },
    )
}

@Composable
private fun BudgetDialog(current: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.let { (it / 100).toString() }.orEmpty()) }
    val rupees = text.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget") },
        text = {
            Column {
                Text(
                    "Home shows what's left of this in each month or pay cycle, instead of what came in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter { it.isDigit() }.take(9) },
                    prefix = { Text("₹") },
                    placeholder = { Text("20000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(rupees?.times(100)) }, enabled = rupees != null && rupees > 0) {
                Text("Save", color = SpendTheme.colors.actionText)
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = SpendTheme.colors.actionText) }
            }
        },
    )
}

@Composable
private fun SavingsGoalDialog(
    name: String?,
    targetPaise: Long?,
    deadlineMillis: Long?,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long?) -> Unit,
    onClear: () -> Unit,
) {
    val today = LocalDate.now()
    var nameText by rememberSaveable { mutableStateOf(name.orEmpty()) }
    var amountText by rememberSaveable { mutableStateOf(targetPaise?.let { (it / 100).toString() }.orEmpty()) }
    var deadlineEpochDay by rememberSaveable {
        mutableStateOf(deadlineMillis?.toLocalDate()?.toEpochDay() ?: NO_DEADLINE)
    }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val deadline = deadlineEpochDay.takeIf { it != NO_DEADLINE }?.let { LocalDate.ofEpochDay(it) }
    val rupees = amountText.toLongOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Savings goal") },
        text = {
            Column {
                Text(
                    "Progress counts what you save or invest from today onward.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it.take(40) },
                    placeholder = { Text("New laptop") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value -> amountText = value.filter { it.isDigit() }.take(9) },
                    prefix = { Text("₹") },
                    placeholder = { Text("50000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pickingDate = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        deadline?.let { "By ${ordinal(it.dayOfMonth)} ${it.month.name.lowercase().replaceFirstChar(Char::uppercase)} ${it.year}" }
                            ?: "No deadline (optional)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    AppIcon(R.drawable.ic_calendar, contentDescription = null, size = 20.dp, tint = SpendTheme.colors.muted)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(nameText.trim(), (rupees ?: 0) * 100, deadline?.toUtcMillis()) },
                enabled = nameText.isNotBlank() && rupees != null && rupees > 0,
            ) {
                Text("Save", color = SpendTheme.colors.actionText)
            }
        },
        dismissButton = {
            Row {
                if (name != null) {
                    TextButton(onClick = onClear) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = SpendTheme.colors.actionText) }
            }
        },
    )

    if (pickingDate) {
        GoalDeadlinePickerDialog(
            initial = deadline ?: today,
            today = today,
            onPick = {
                deadlineEpochDay = it.toEpochDay()
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** A goal's deadline must be today or later. */
@Composable
private fun GoalDeadlinePickerDialog(initial: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayMillis = today.toUtcMillis()
    val selectable = remember(todayMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis

            override fun isSelectableYear(year: Int) = year >= today.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        yearRange = today.year..(today.year + 20),
        selectableDates = selectable,
    )
    val pickerColors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.background,
        selectedDayContainerColor = MaterialTheme.colorScheme.primary,
        selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
        todayDateBorderColor = MaterialTheme.colorScheme.onSurface,
        todayContentColor = MaterialTheme.colorScheme.onSurface,
        selectedYearContainerColor = MaterialTheme.colorScheme.primary,
        selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
        dividerColor = SpendTheme.colors.line,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.toLocalDate()) } }, enabled = state.selectedDateMillis != null) {
                Text("OK", style = MaterialTheme.typography.labelLarge, color = SpendTheme.colors.actionText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = SpendTheme.colors.actionText)
            }
        },
        colors = pickerColors,
    ) {
        DatePicker(state = state, colors = pickerColors, showModeToggle = false)
    }
}

private const val NO_DEADLINE = Long.MIN_VALUE
