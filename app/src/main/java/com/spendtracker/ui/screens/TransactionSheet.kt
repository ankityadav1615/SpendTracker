package com.spendtracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendtracker.data.Transaction
import com.spendtracker.data.displayName
import com.spendtracker.model.Category
import com.spendtracker.model.Money
import com.spendtracker.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionSheet(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onCategorySelected: (category: Category, applyToSimilar: Boolean) -> Unit,
    onHide: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canRemember = transaction.counterpartyKey.isNotEmpty()
    var applyToSimilar by rememberSaveable(transaction.id) { mutableStateOf(canRemember) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(transaction.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
                signedAmount(transaction, withPaise = true),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor(transaction),
            )
            Text(
                (if (transaction.type == TransactionType.DEBIT) "Paid · " else "Received · ") +
                    formatDateTime(transaction.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            DetailLine("Account", accountLabel(transaction.bank, transaction.accountTail))
            DetailLine("UPI ID", transaction.vpa)
            DetailLine("Reference", transaction.refNo)
            DetailLine("Balance after", transaction.balancePaise?.let { Money.format(it, withPaise = true) })

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.choicesFor(transaction.type).forEach { category ->
                    FilterChip(
                        selected = transaction.category == category,
                        onClick = { onCategorySelected(category, applyToSimilar) },
                        label = { Text(category.label) },
                        leadingIcon = { CategoryDot(category) },
                    )
                }
            }
            if (canRemember) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { applyToSimilar = !applyToSimilar }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = applyToSimilar, onCheckedChange = { applyToSimilar = it })
                    Text(
                        (if (transaction.type == TransactionType.DEBIT) "Remember for all payments to " else "Remember for all money from ") +
                            transaction.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Original SMS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(transaction.body, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "From ${transaction.sender}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onHide) { Text("Not a real transaction? Hide it") }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
