package com.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.R
import com.spendtracker.data.Transaction
import com.spendtracker.data.displayName
import com.spendtracker.data.isManual
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryKind
import com.spendtracker.model.ManualEntries
import com.spendtracker.model.Money
import com.spendtracker.model.TransactionType
import com.spendtracker.ui.theme.AmountStyle
import com.spendtracker.ui.theme.SpendTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionSheet(
    transaction: Transaction,
    today: LocalDate,
    onDismiss: () -> Unit,
    onCategorySelected: (category: Category, applyToSimilar: Boolean) -> Unit,
    onHide: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    /** For a cash withdrawal: how much of it added cash spends already account for. */
    coveredPaise: Long = 0,
) {
    val colors = SpendTheme.colors
    val manual = transaction.isManual
    val canRemember = !manual && transaction.counterpartyKey.isNotEmpty()
    var applyToSimilar by rememberSaveable(transaction.id) { mutableStateOf(canRemember) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(transaction.category, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(transaction.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        (if (transaction.type == TransactionType.DEBIT) "Paid · " else "Received · ") +
                            formatWhen(transaction.timestamp, today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                signedAmount(transaction, withPaise = true),
                style = AmountStyle.copy(fontSize = 36.sp, lineHeight = 42.sp),
                color = amountColor(transaction),
                modifier = Modifier.padding(top = 14.dp),
            )
            if (transaction.category.kind == CategoryKind.EXCLUDED) {
                Text(
                    "Not counted as spending or income",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (coveredPaise > 0) {
                val counted = (transaction.amountPaise - coveredPaise).coerceAtLeast(0)
                Text(
                    "Cash spends you added use ${Money.format(coveredPaise, withPaise = true)} of this, so " +
                        (if (counted == 0L) "it isn't counted again" else "only ${Money.format(counted, withPaise = true)} counts here"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            SpendCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                val lines = if (manual) {
                    listOfNotNull(
                        transaction.method?.let { (if (transaction.type == TransactionType.DEBIT) "Paid with" else "Received as") to it.label },
                        "Added" to "By you, not from an SMS",
                        transaction.note?.let { "Note" to it },
                    )
                } else {
                    listOfNotNull(
                        accountLabel(transaction.bank, transaction.accountTail)?.let { "Account" to it },
                        transaction.vpa?.let { "UPI ID" to it },
                        transaction.refNo?.let { "Reference" to it },
                        transaction.balancePaise?.let { "Balance after" to Money.format(it, withPaise = true) },
                        "From" to transaction.sender,
                    )
                }
                lines.forEachIndexed { index, (label, value) ->
                    if (index > 0) RowDivider()
                    DetailLine(label, value)
                }
            }

            transaction.receiptPath?.let { path ->
                SectionLabel("Receipt")
                ReceiptThumbnail(path)
            }

            SectionLabel("Category")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val choices = if (manual) {
                    (ManualEntries.categoriesFor(transaction.type) + transaction.category).distinct()
                } else {
                    Category.choicesFor(transaction.type)
                }
                choices.forEach { category ->
                    ChoiceChip(
                        label = category.label,
                        selected = transaction.category == category,
                        onClick = { onCategorySelected(category, applyToSimilar && canRemember) },
                        icon = categoryIcon(category),
                        iconTint = colors.category(category),
                    )
                }
            }
            if (canRemember) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(top = 6.dp)
                        .toggleable(value = applyToSimilar, role = Role.Checkbox, onValueChange = { applyToSimilar = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = applyToSimilar,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, uncheckedColor = colors.muted),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        (if (transaction.type == TransactionType.DEBIT) "Remember for every payment to " else "Remember for all money from ") +
                            transaction.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (manual) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlineButton("Edit", onClick = onEdit, modifier = Modifier.weight(1f), icon = R.drawable.ic_edit)
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        AppIcon(R.drawable.ic_trash, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete", style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp), color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Column
            }

            SectionLabel("Original SMS")
            Text(
                transaction.body,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp))
                    .background(colors.tint)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            TextButton(onClick = onHide, modifier = Modifier.padding(top = 10.dp)) {
                AppIcon(R.drawable.ic_eye_off, contentDescription = null, size = 20.dp, tint = colors.actionText)
                Spacer(Modifier.width(8.dp))
                Text("Hide this transaction", style = MaterialTheme.typography.labelLarge, color = colors.actionText)
            }
            Text(
                "For messages that aren't real payments, like a failed or reversed one.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
