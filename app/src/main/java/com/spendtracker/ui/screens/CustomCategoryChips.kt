package com.spendtracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.spendtracker.R
import com.spendtracker.model.CustomCategories
import com.spendtracker.model.CustomCategoryInfo
import com.spendtracker.model.TransactionType
import com.spendtracker.ui.theme.SpendTheme

/**
 * The user's own categories for [type], as chips under the built-in ones. The most used show first; the rest
 * fold behind "More" so a long list doesn't bury the built-in chips. "New" makes one and selects it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomCategoryChips(
    type: TransactionType,
    all: List<CustomCategoryInfo>,
    selected: String?,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpendTheme.colors
    val mine = all.filter { it.type == type }.let { list ->
        // A payment can still carry a name that's no longer in the list; keep it visible so it shows as selected.
        if (selected.isNullOrBlank() || list.any { it.name == selected }) list else list + CustomCategoryInfo(-1, selected, type, 0)
    }
    var expanded by rememberSaveable(type) { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) mine else CustomCategories.visible(mine, selected)

    Column(modifier) {
        Text(
            "Your categories",
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { option ->
                ChoiceChip(
                    label = option.name,
                    selected = selected == option.name,
                    onClick = { onSelect(option.name) },
                    dot = colors.custom(option.name),
                )
            }
            if (shown.size < mine.size) {
                ChoiceChip(label = "More (${mine.size - shown.size})", selected = false, onClick = { expanded = true })
            }
            ChoiceChip(
                label = "New",
                selected = false,
                onClick = { creating = true },
                icon = R.drawable.ic_plus,
                iconTint = colors.actionText,
            )
        }
        if (mine.isEmpty()) {
            Text(
                if (type == TransactionType.DEBIT) "Make your own, like Pets, Kids school or Gym." else "Make your own, like Rent from tenant or Side job.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
    }

    if (creating) {
        CategoryNameDialog(
            title = if (type == TransactionType.DEBIT) "New expense category" else "New income category",
            initial = "",
            existing = all.map { it.name },
            adding = true,
            confirmLabel = "Add",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
        )
    }
}

/** Asks for a category name, checking it with [CustomCategories.validate] as the user types. */
@Composable
fun CategoryNameDialog(
    title: String,
    initial: String,
    existing: List<String>,
    adding: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    extra: (@Composable () -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val others = existing.filterNot { it.equals(initial, ignoreCase = false) && initial.isNotEmpty() }
    val error = CustomCategories.validate(text, others, adding)
    val focus = remember { FocusRequester() }
    fun submit() {
        tried = true
        if (error == null) onConfirm(CustomCategories.normalize(text))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                extra?.invoke()
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(CustomCategories.MAX_NAME_LENGTH + 4) },
                    placeholder = { Text("Like Pets or Kids school") },
                    singleLine = true,
                    isError = tried && error != null,
                    supportingText = {
                        val shownError = error?.takeIf { tried || text.isNotBlank() }
                        Text(shownError ?: "${CustomCategories.normalize(text).length} / ${CustomCategories.MAX_NAME_LENGTH}")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) { Text(confirmLabel, color = SpendTheme.colors.actionText) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.size(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = SpendTheme.colors.actionText) }
            }
        },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
