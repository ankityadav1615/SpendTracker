package com.spendtracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.spendtracker.R
import com.spendtracker.model.DateRanges
import com.spendtracker.model.PeriodChoice
import com.spendtracker.model.PeriodOption
import com.spendtracker.model.ResolvedPeriod
import com.spendtracker.ui.theme.SpendTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Date pill with previous/next buttons, as on Home. */
@Composable
fun PeriodBar(period: ResolvedPeriod, onOpen: () -> Unit, onSelect: (PeriodChoice) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DatePill(
            period.label,
            onClick = onOpen,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp),
        )
        Row {
            RoundIconButton(R.drawable.ic_chevron_left, "Previous period", enabled = period.previous != null) {
                period.previous?.let(onSelect)
            }
            RoundIconButton(R.drawable.ic_chevron_right, "Next period", enabled = period.next != null) {
                period.next?.let(onSelect)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSheet(
    options: List<PeriodOption>,
    selected: PeriodChoice,
    onSelect: (PeriodChoice) -> Unit,
    onPickDates: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                "Show money for",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    val isSelected = option.choice == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option.choice) }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp))
                            Text(
                                option.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = SpendTheme.colors.muted,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlineButton("Pick dates on a calendar", onClick = onPickDates, icon = R.drawable.ic_calendar)
        }
    }
}

/** Full-screen calendar for choosing any past date range. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    initialFirst: LocalDate?,
    initialLast: LocalDate?,
    today: LocalDate,
    onSave: (first: LocalDate, last: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = today.toUtcMillis()
    val selectable = remember(todayMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis

            override fun isSelectableYear(year: Int) = year <= today.year
        }
    }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFirst?.toUtcMillis(),
        initialSelectedEndDateMillis = initialLast?.toUtcMillis(),
        initialDisplayedMonthMillis = (initialLast ?: today).toUtcMillis(),
        yearRange = (today.year - 10)..today.year,
        selectableDates = selectable,
    )
    val first = state.selectedStartDateMillis?.toLocalDate()
    val last = state.selectedEndDateMillis?.toLocalDate()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val lightBars = !SpendTheme.colors.isDark
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onDismiss) { AppIcon(R.drawable.ic_close, contentDescription = "Close") }
                    TextButton(onClick = { if (first != null) onSave(first, last ?: first) }, enabled = first != null) {
                        Text("Save", style = MaterialTheme.typography.labelLarge, color = SpendTheme.colors.actionText)
                    }
                }
                DateRangePicker(
                    state = state,
                    modifier = Modifier.weight(1f),
                    title = {
                        Text(
                            "Pick a start and end date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 4.dp),
                        )
                    },
                    headline = {
                        Text(
                            text = when {
                                first == null -> "Start date"
                                last == null -> DateRanges.date(first, today) + " – end date"
                                else -> DateRanges.range(first, last, today)
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                        )
                    },
                    colors = DatePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                        selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                        dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        todayDateBorderColor = MaterialTheme.colorScheme.onSurface,
                        todayContentColor = MaterialTheme.colorScheme.onSurface,
                        selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                        selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                        dividerColor = SpendTheme.colors.line,
                    ),
                )
                HorizontalDivider(color = SpendTheme.colors.line)
            }
        }
    }
}

internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Remembers which period panel is open: the option sheet or the calendar. */
enum class PeriodPanel { NONE, SHEET, CALENDAR }

/** Shows the option sheet or the calendar for [period], as the shared state of Home and Activity asks. */
@Composable
fun PeriodPanels(
    panel: PeriodPanel,
    period: ResolvedPeriod,
    options: List<PeriodOption>,
    today: LocalDate,
    onPanelChange: (PeriodPanel) -> Unit,
    onSelect: (PeriodChoice) -> Unit,
) {
    when (panel) {
        PeriodPanel.NONE -> Unit
        PeriodPanel.SHEET -> PeriodSheet(
            options = options,
            selected = period.choice,
            onSelect = {
                onSelect(it)
                onPanelChange(PeriodPanel.NONE)
            },
            onPickDates = { onPanelChange(PeriodPanel.CALENDAR) },
            onDismiss = { onPanelChange(PeriodPanel.NONE) },
        )
        PeriodPanel.CALENDAR -> DateRangeDialog(
            initialFirst = period.firstDate,
            initialLast = period.lastDate,
            today = today,
            onSave = { first, last ->
                onSelect(PeriodChoice.Range(first, last))
                onPanelChange(PeriodPanel.NONE)
            },
            onDismiss = { onPanelChange(PeriodPanel.NONE) },
        )
    }
}
