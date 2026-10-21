package com.spendtracker.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.R
import com.spendtracker.data.Transaction
import com.spendtracker.model.Category
import com.spendtracker.model.DateRanges
import com.spendtracker.model.ManualEntries
import com.spendtracker.model.ManualEntry
import com.spendtracker.model.PaymentMethod
import com.spendtracker.model.TransactionType
import com.spendtracker.ui.theme.AmountStyle
import com.spendtracker.ui.theme.SpendTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Everyday categories first, so the common ones fit before "More". */
private val EXPENSE_ORDER = listOf(
    Category.GROCERIES, Category.FOOD, Category.FUEL, Category.DAILY, Category.SHOPPING, Category.TRAVEL,
    Category.BILLS, Category.HEALTH, Category.RENT, Category.EMI, Category.ENTERTAINMENT, Category.INVESTMENTS,
    Category.TRANSFERS,
)
private const val FIRST_CHIPS = 8

private fun categoryOrder(type: TransactionType): List<Category> {
    val allowed = ManualEntries.categoriesFor(type)
    return if (type == TransactionType.DEBIT) EXPENSE_ORDER.filter { it in allowed } + allowed.filter { it !in EXPENSE_ORDER } else allowed
}

/** Add an expense or income by hand, or change one added earlier ([existing]). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntrySheet(
    existing: Transaction?,
    today: LocalDate,
    onSave: (ManualEntry) -> Unit,
    onDismiss: () -> Unit,
    initialType: TransactionType = TransactionType.DEBIT,
) {
    val colors = SpendTheme.colors
    val key = existing?.id ?: 0L
    val zone = ZoneId.systemDefault()
    var type by rememberSaveable(key) { mutableStateOf(existing?.type ?: initialType) }
    var amount by rememberSaveable(key) { mutableStateOf(existing?.let { ManualEntries.amountText(it.amountPaise) } ?: "") }
    var name by rememberSaveable(key) { mutableStateOf(existing?.counterparty ?: "") }
    var category by rememberSaveable(key) { mutableStateOf(existing?.category) }
    var method by rememberSaveable(key) {
        mutableStateOf(if (existing != null) existing.method else PaymentMethod.CASH.takeIf { initialType == TransactionType.DEBIT })
    }
    var epochDay by rememberSaveable(key) { mutableLongStateOf((existing?.let { localDate(it.timestamp) } ?: today).toEpochDay()) }
    var note by rememberSaveable(key) { mutableStateOf(existing?.note ?: "") }
    var showAll by rememberSaveable(key) { mutableStateOf(false) }
    var picking by rememberSaveable(key) { mutableStateOf(false) }
    var tried by rememberSaveable(key) { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(epochDay)
    val expense = type == TransactionType.DEBIT
    val paise = ManualEntries.parseAmount(amount)
    val amountFocus = remember { FocusRequester() }

    fun save() {
        tried = true
        val picked = category ?: return
        val value = paise ?: return
        val timestamp = when {
            existing != null && localDate(existing.timestamp) == date -> existing.timestamp
            date == today -> System.currentTimeMillis()
            else -> date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        }
        onSave(
            ManualEntry(
                type = type,
                amountPaise = value,
                category = picked,
                name = name,
                timestamp = timestamp,
                method = if (expense) method else null,
                note = note,
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            SegmentedToggle(
                options = listOf("Expense", "Income"),
                selected = if (expense) 0 else 1,
                onSelect = { index ->
                    val next = if (index == 0) TransactionType.DEBIT else TransactionType.CREDIT
                    if (next != type) {
                        type = next
                        if (category !in ManualEntries.categoriesFor(next)) category = null
                        method = if (next == TransactionType.DEBIT) method ?: PaymentMethod.CASH else null
                        showAll = false
                    }
                },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val amountStyle = AmountStyle.copy(
                    fontSize = 52.sp,
                    lineHeight = 60.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val fieldWidth = with(density) { measurer.measure(amount.ifEmpty { "0" }, amountStyle).size.width.toDp() } + 4.dp
                Text(
                    "₹",
                    style = AmountStyle.copy(fontSize = 36.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.muted,
                )
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = amount,
                    onValueChange = { typed -> ManualEntries.cleanAmountInput(typed)?.let { amount = it } },
                    textStyle = amountStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .width(fieldWidth)
                        .focusRequester(amountFocus)
                        .semantics { contentDescription = "Amount in rupees" },
                    decorationBox = { field ->
                        if (amount.isEmpty()) {
                            Text("0", style = amountStyle, color = colors.muted.copy(alpha = 0.6f))
                        }
                        field()
                    },
                )
            }
            LaunchedEffect(key) {
                if (existing == null) runCatching { amountFocus.requestFocus() }
            }
            if (tried && paise == null) {
                Text(
                    if (amount.isEmpty()) "Enter an amount" else "Enter an amount up to ₹10 crore",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel(if (expense) "Paid to" else "Received from")
            EntryField(
                value = name,
                onValueChange = { name = it.take(60) },
                placeholder = if (expense) "Shop or person (optional)" else "Person or company (optional)",
                capitalization = KeyboardCapitalization.Words,
            )

            SectionLabel("Category")
            val order = categoryOrder(type)
            val visible = if (showAll || order.size <= FIRST_CHIPS + 1 || order.indexOf(category) >= FIRST_CHIPS) order else order.take(FIRST_CHIPS)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { option ->
                    ChoiceChip(
                        label = option.label,
                        selected = category == option,
                        onClick = { category = option },
                        icon = categoryIcon(option),
                        iconTint = colors.category(option),
                    )
                }
                if (visible.size < order.size) {
                    ChoiceChip(label = "More…", selected = false, onClick = { showAll = true })
                }
            }
            if (tried && category == null) {
                Text(
                    "Pick a category",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }

            if (expense) {
                SectionLabel("Paid with")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.entries.forEach { option ->
                        ChoiceChip(label = option.label, selected = method == option, onClick = { method = option })
                    }
                }
                val hint = when (method) {
                    PaymentMethod.CASH -> "Cash spends are taken out of your ATM withdrawals, so the same money isn't counted twice."
                    PaymentMethod.UPI -> "UPI payments usually come in from your bank SMS by themselves. Add one here only if it's missing."
                    PaymentMethod.CARD -> "Card payments usually come in from your bank SMS by themselves. Add one here only if it's missing."
                    else -> null
                }
                if (hint != null) {
                    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp)) {
                        AppIcon(R.drawable.ic_info, contentDescription = null, size = 18.dp, tint = colors.muted)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .border(1.5.dp, colors.line, RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = "Change date", role = Role.Button) { picking = true }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(R.drawable.ic_calendar, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(dateText(date, today), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
                EntryField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    placeholder = "Note (optional)",
                    capitalization = KeyboardCapitalization.Sentences,
                    modifier = Modifier.weight(1f),
                    onDone = ::save,
                )
            }

            PrimaryButton(
                when {
                    existing != null -> "Save changes"
                    expense -> "Save expense"
                    else -> "Save income"
                },
                onClick = ::save,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }

    if (picking) {
        DayPickerDialog(
            initial = date,
            today = today,
            onPick = {
                epochDay = it.toEpochDay()
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private fun dateText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today, " + DateRanges.date(date, today)
    today.minusDays(1) -> "Yesterday, " + DateRanges.date(date, today)
    else -> DateRanges.date(date, today)
}

@Composable
private fun EntryField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    capitalization: KeyboardCapitalization,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    val colors = SpendTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, maxLines = 1) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = colors.card,
            focusedContainerColor = colors.card,
            unfocusedBorderColor = colors.line,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedPlaceholderColor = colors.muted,
            focusedPlaceholderColor = colors.muted,
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

/** A calendar for one day, up to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(initial: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayMillis = today.toUtcMillis()
    val selectable = remember(todayMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis

            override fun isSelectableYear(year: Int) = year <= today.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        yearRange = (today.year - 10)..today.year,
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
