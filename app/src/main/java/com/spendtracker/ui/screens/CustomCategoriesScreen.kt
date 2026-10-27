package com.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendtracker.R
import com.spendtracker.model.CustomCategories
import com.spendtracker.model.CustomCategoryInfo
import com.spendtracker.model.TransactionType
import com.spendtracker.ui.theme.SpendTheme

private const val NONE = 0L

/** The user's own categories: add, rename and delete them, grouped by spending and income. */
@Composable
fun CustomCategoriesScreen(
    categories: List<CustomCategoryInfo>,
    onBack: () -> Unit,
    onAdd: (String, TransactionType) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val colors = SpendTheme.colors
    var adding by rememberSaveable { mutableStateOf<TransactionType?>(null) }
    var editingId by rememberSaveable { mutableLongStateOf(NONE) }
    var deletingId by rememberSaveable { mutableLongStateOf(NONE) }
    val names = categories.map { it.name }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        BackHeader("Your categories", onBack)
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text(
                    "Make categories that fit your life. They show up under the built-in ones when you add a payment or change one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${categories.size} of ${CustomCategories.MAX_COUNT} used",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (categories.size >= CustomCategories.MAX_COUNT) MaterialTheme.colorScheme.error else colors.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            listOf(TransactionType.DEBIT, TransactionType.CREDIT).forEach { type ->
                val mine = categories.filter { it.type == type }
                item(key = type.name) {
                    SectionLabel(if (type == TransactionType.DEBIT) "For spending" else "For money coming in")
                    if (mine.isNotEmpty()) {
                        SpendCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                            mine.forEachIndexed { index, info ->
                                if (index > 0) RowDivider()
                                ListRow(
                                    leading = { CategoryTile(CustomCategories.baseFor(type), customName = info.name) },
                                    title = info.name,
                                    meta = paymentsText(info.count),
                                    amount = null,
                                    onClick = { editingId = info.id },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlineButton(
                        if (type == TransactionType.DEBIT) "Add a spending category" else "Add an income category",
                        onClick = { adding = type },
                        modifier = Modifier.fillMaxWidth(),
                        icon = R.drawable.ic_plus,
                    )
                }
            }
        }
    }

    adding?.let { type ->
        CategoryNameDialog(
            title = if (type == TransactionType.DEBIT) "New spending category" else "New income category",
            initial = "",
            existing = names,
            adding = true,
            confirmLabel = "Add",
            onDismiss = { adding = null },
            onConfirm = { name ->
                adding = null
                onAdd(name, type)
            },
        )
    }

    categories.firstOrNull { it.id == editingId }?.let { info ->
        CategoryNameDialog(
            title = "Edit category",
            initial = info.name,
            existing = names,
            adding = false,
            confirmLabel = "Save",
            onDismiss = { editingId = NONE },
            onConfirm = { name ->
                editingId = NONE
                if (name != info.name) onRename(info.id, name)
            },
            onDelete = {
                editingId = NONE
                deletingId = info.id
            },
        )
    }

    categories.firstOrNull { it.id == deletingId }?.let { info ->
        val base = CustomCategories.baseFor(info.type).label
        AlertDialog(
            onDismissRequest = { deletingId = NONE },
            title = { Text("Delete ${info.name}?") },
            text = {
                Text(
                    if (info.count == 0) {
                        "Nothing is in it yet."
                    } else {
                        "${paymentsText(info.count)} move back to $base. Payees remembered for it are forgotten."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deletingId = NONE
                    onDelete(info.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingId = NONE }) { Text("Cancel", color = colors.actionText) } },
        )
    }
}

private fun paymentsText(count: Int): String = when (count) {
    0 -> "Not used yet"
    1 -> "1 payment"
    else -> "$count payments"
}
