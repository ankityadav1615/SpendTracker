package com.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendtracker.R
import com.spendtracker.model.CategoryRuleInfo
import com.spendtracker.model.TransactionType
import com.spendtracker.ui.theme.SpendTheme

/** Lists remembered payee -> category corrections, so the user can forget one that's no longer right. */
@Composable
fun CategoryRulesScreen(
    rules: List<CategoryRuleInfo>,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var toDelete by rememberSaveable { mutableStateOf<CategoryRuleInfo?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        BackHeader("Category rules", onBack)
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text(
                    "When you change a payee's category \"for all\", SpendTracker remembers it here for next time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (rules.isEmpty()) {
                item {
                    EmptyNote(
                        "No rules yet",
                        "Change a transaction's category and choose \"apply to similar\" to remember it here.",
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                item {
                    SpendCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        rules.forEachIndexed { index, rule ->
                            if (index > 0) RowDivider()
                            RuleRow(rule, onDelete = { toDelete = rule })
                        }
                    }
                }
            }
        }
    }

    val rule = toDelete
    if (rule != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Forget this rule?") },
            text = {
                Text("${rule.displayName}'s transactions keep their current category, but future ones won't be categorized automatically.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(rule.key)
                    toDelete = null
                }) { Text("Forget", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel", color = SpendTheme.colors.actionText) } },
        )
    }
}

@Composable
private fun RuleRow(rule: CategoryRuleInfo, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(rule.category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(rule.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${if (rule.type == TransactionType.DEBIT) "Spend" else "Income"} · ${rule.category.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        AppIcon(
            R.drawable.ic_trash,
            contentDescription = "Forget rule",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.clickable(onClick = onDelete),
        )
    }
}
