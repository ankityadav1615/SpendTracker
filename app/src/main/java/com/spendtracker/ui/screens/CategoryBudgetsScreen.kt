package com.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryKind
import com.spendtracker.model.Money
import com.spendtracker.ui.theme.SpendTheme

/** Lets the user set or remove a monthly limit per spending category. */
@Composable
fun CategoryBudgetsScreen(
    budgets: Map<Category, Long>,
    onBack: () -> Unit,
    onSetBudget: (Category, Long?) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<Category?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        BackHeader("Category budgets", onBack)
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text(
                    "Set a monthly limit for the categories you want to watch. Home flags any category that goes over.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
            }
            item {
                SpendCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    val spendCategories = Category.entries.filter { it.kind == CategoryKind.SPEND }
                    spendCategories.forEachIndexed { index, category ->
                        if (index > 0) RowDivider()
                        ListRow(
                            leading = { CategoryTile(category) },
                            title = category.label,
                            meta = null,
                            amount = budgets[category]?.let { Money.format(it) } ?: "Not set",
                            onClick = { editing = category },
                        )
                    }
                }
            }
        }
    }

    val category = editing
    if (category != null) {
        CategoryBudgetDialog(
            category = category,
            current = budgets[category],
            onDismiss = { editing = null },
            onSave = { paise ->
                onSetBudget(category, paise)
                editing = null
            },
        )
    }
}

@Composable
private fun CategoryBudgetDialog(category: Category, current: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by rememberSaveable(category) { mutableStateOf(current?.let { (it / 100).toString() }.orEmpty()) }
    val rupees = text.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${category.label} budget") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { it.isDigit() }.take(9) },
                prefix = { Text("₹") },
                placeholder = { Text("5000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
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
