package com.spendtracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendtracker.R
import com.spendtracker.export.ExportFormat
import com.spendtracker.model.Category
import com.spendtracker.model.ReportSide
import com.spendtracker.ui.MoneyState
import com.spendtracker.ui.theme.SpendTheme
import java.time.LocalDate

/** The dates a download covers: the selected period, from its first transaction when it has no fixed start. */
fun exportRange(state: MoneyState): Pair<LocalDate, LocalDate> {
    val last = state.period.lastDate
    val first = state.period.firstDate
        ?: state.transactions.minOfOrNull { it.timestamp }?.let(::localDate)
        ?: last
    return minOf(first, last) to last
}

/** Choose dates, categories and PDF or CSV, then pick where to save. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(
    state: MoneyState,
    exporting: Boolean,
    onOpenPeriods: () -> Unit,
    onExport: (format: ExportFormat, categories: Set<Category>?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SpendTheme.colors
    var format by rememberSaveable { mutableStateOf(ExportFormat.PDF) }
    var pickedNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    val available = (state.reports[ReportSide.EXPENSES]?.lines.orEmpty() + state.reports[ReportSide.INCOME]?.lines.orEmpty())
        .map { it.category }
        .distinct()
    val picked = pickedNames.mapNotNull { name -> Category.entries.firstOrNull { it.name == name } }.filter { it in available }.toSet()

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
            Text("Download report", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))

            SectionLabel("Dates")
            DatePill(state.period.label, onClick = onOpenPeriods)

            SectionLabel("Categories")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(label = "All", selected = picked.isEmpty(), onClick = { pickedNames = emptyList() })
                available.forEach { category ->
                    val on = category in picked
                    ChoiceChip(
                        label = category.label,
                        selected = on,
                        onClick = {
                            pickedNames = (if (on) picked - category else picked + category).map { it.name }
                        },
                        icon = categoryIcon(category),
                        iconTint = colors.category(category),
                    )
                }
            }
            if (available.isEmpty()) {
                Text(
                    "Nothing was spent or received in these dates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }

            SectionLabel("File type")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionCard(
                    icon = R.drawable.ic_file,
                    iconBackground = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = colors.actionText,
                    title = "PDF report",
                    subtitle = "Chart, category totals and every payment",
                    selected = format == ExportFormat.PDF,
                    onClick = { format = ExportFormat.PDF },
                )
                OptionCard(
                    icon = R.drawable.ic_table,
                    iconBackground = colors.credit.copy(alpha = 0.14f),
                    iconTint = colors.credit,
                    title = "CSV spreadsheet",
                    subtitle = "Opens in Excel or Google Sheets",
                    selected = format == ExportFormat.CSV,
                    onClick = { format = ExportFormat.CSV },
                )
            }

            PrimaryButton(
                if (exporting) "Saving…" else "Download ${format.label}",
                onClick = { onExport(format, picked.takeIf { it.isNotEmpty() }) },
                enabled = !exporting,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "You choose where to save it. The file is made on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }
    }
}
