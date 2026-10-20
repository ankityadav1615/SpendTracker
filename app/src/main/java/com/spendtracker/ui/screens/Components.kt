package com.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendtracker.data.Transaction
import com.spendtracker.data.displayName
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryKind
import com.spendtracker.model.Money
import com.spendtracker.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
private val DAY_HEADER = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun formatDateTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DATE_TIME)

fun formatShortDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(SHORT_DATE)

fun localDate(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatDayHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DAY_HEADER)
    }
}

fun accountLabel(bank: String?, accountTail: String?): String? = when {
    bank != null && accountTail != null -> "$bank ••$accountTail"
    bank != null -> bank
    accountTail != null -> "A/c ••$accountTail"
    else -> null
}

fun signedAmount(transaction: Transaction, withPaise: Boolean = false): String {
    val sign = if (transaction.type == TransactionType.CREDIT) "+" else "−"
    return sign + Money.format(transaction.amountPaise, withPaise)
}

@Composable
fun amountColor(transaction: Transaction): Color = when {
    transaction.category.kind == CategoryKind.EXCLUDED -> MaterialTheme.colorScheme.onSurfaceVariant
    transaction.type == TransactionType.CREDIT -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun CategoryDot(category: Category, size: Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(category.color)),
    )
}

@Composable
fun TransactionRow(transaction: Transaction, onClick: () -> Unit) {
    val color = Color(transaction.category.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = transaction.displayName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "₹",
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = transaction.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${transaction.category.label} · ${formatDateTime(transaction.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = signedAmount(transaction),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = amountColor(transaction),
        )
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
