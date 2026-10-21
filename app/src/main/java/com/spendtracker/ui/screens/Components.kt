package com.spendtracker.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.R
import com.spendtracker.data.Transaction
import com.spendtracker.data.displayName
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryKind
import com.spendtracker.model.DateRanges
import com.spendtracker.model.Money
import com.spendtracker.model.PeriodLabel
import com.spendtracker.model.TransactionType
import com.spendtracker.ui.theme.AmountStyle
import com.spendtracker.ui.theme.SpendTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun localDate(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(TIME).lowercase(Locale.ENGLISH)

/** "Today, 9:12 am", "Yesterday, 6:40 pm" or "17 Oct, 6:40 pm". */
fun formatWhen(timestamp: Long, today: LocalDate = LocalDate.now()): String {
    val day = when (val date = localDate(timestamp)) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateRanges.date(date, today)
    }
    return "$day, ${formatTime(timestamp)}"
}

/** "ICICI Bank" reads as "ICICI" where space is short. */
fun shortBank(bank: String): String = bank.removeSuffix(" Bank").removeSuffix(" bank").trim().ifEmpty { bank }

fun accountLabel(bank: String?, accountTail: String?): String? = when {
    bank != null && accountTail != null -> "${shortBank(bank)} ••$accountTail"
    bank != null -> shortBank(bank)
    accountTail != null -> "A/c ••$accountTail"
    else -> null
}

fun signedAmount(transaction: Transaction, withPaise: Boolean = false): String {
    val sign = if (transaction.type == TransactionType.CREDIT) "+" else "−"
    return sign + Money.format(transaction.amountPaise, withPaise)
}

/** Net amount of a day: "+₹24,800" or "−₹1,690". */
fun signedNet(paise: Long): String = (if (paise > 0) "+" else if (paise < 0) "−" else "") + Money.format(kotlin.math.abs(paise))

@DrawableRes
fun categoryIcon(category: Category): Int = when (category) {
    Category.EMI -> R.drawable.ic_cat_emi
    Category.RENT -> R.drawable.ic_cat_rent
    Category.GROCERIES -> R.drawable.ic_cat_groceries
    Category.FUEL -> R.drawable.ic_cat_fuel
    Category.FOOD -> R.drawable.ic_cat_food
    Category.BILLS -> R.drawable.ic_cat_bills
    Category.SHOPPING -> R.drawable.ic_cat_shopping
    Category.TRAVEL -> R.drawable.ic_cat_travel
    Category.HEALTH -> R.drawable.ic_cat_health
    Category.ENTERTAINMENT -> R.drawable.ic_cat_fun
    Category.INVESTMENTS -> R.drawable.ic_cat_invest
    Category.CASH -> R.drawable.ic_cat_cash
    Category.TRANSFERS -> R.drawable.ic_cat_person
    Category.DAILY -> R.drawable.ic_cat_daily
    Category.SALARY -> R.drawable.ic_cat_income
    Category.REFUND -> R.drawable.ic_cat_refund
    Category.OTHER_INCOME -> R.drawable.ic_cat_plus
    Category.SELF_TRANSFER -> R.drawable.ic_cat_transfer
    Category.CARD_BILL -> R.drawable.ic_cat_card
}

@Composable
fun AppIcon(@DrawableRes icon: Int, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = Color.Unspecified) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
    )
}

private val CardShape = RoundedCornerShape(24.dp)

/** The white, softly lifted card everything sits on. */
@Composable
fun SpendCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SpendTheme.colors
    val lifted = if (colors.isDark) {
        modifier
    } else {
        modifier.shadow(6.dp, CardShape, ambientColor = Color(0x1F5A3414), spotColor = Color(0x2E5A3414))
    }
    Column(
        lifted
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card)
            .border(1.dp, colors.cardBorder, CardShape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = SpendTheme.colors.cardBorder)
}

/** A 40dp rounded-square tile behind an icon or initials. */
@Composable
fun Tile(background: Color, size: Dp = 40.dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.35f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun CategoryTile(category: Category, size: Dp = 40.dp) {
    val colors = SpendTheme.colors
    Tile(colors.categoryTile(category), size) {
        AppIcon(categoryIcon(category), contentDescription = null, size = size * 0.6f, tint = colors.category(category))
    }
}

/** A list row: leading tile, name over meta, trailing amount (with an optional note under it). */
@Composable
fun ListRow(
    leading: @Composable () -> Unit,
    title: String,
    meta: String?,
    amount: String?,
    modifier: Modifier = Modifier,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    amountNote: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (amount != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, style = AmountStyle, color = amountColor, maxLines = 1)
                if (amountNote != null) {
                    Text(amountNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun amountColor(transaction: Transaction): Color = when {
    transaction.category.kind == CategoryKind.EXCLUDED -> MaterialTheme.colorScheme.onSurfaceVariant
    transaction.type == TransactionType.CREDIT -> SpendTheme.colors.credit
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun TransactionRow(transaction: Transaction, onClick: () -> Unit) {
    val meta = listOfNotNull(
        transaction.category.label,
        "UPI".takeIf { transaction.vpa != null },
        transaction.method?.label?.takeIf { transaction.vpa == null },
        accountLabel(transaction.bank, transaction.accountTail),
    ).joinToString(" · ")
    ListRow(
        leading = { CategoryTile(transaction.category) },
        title = transaction.displayName,
        meta = meta,
        amount = signedAmount(transaction),
        amountColor = amountColor(transaction),
        onClick = onClick,
    )
}

/** The rounded date pill that opens the period sheet. */
@Composable
fun DatePill(label: PeriodLabel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SpendTheme.colors
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, colors.line, CircleShape)
            .clickable(onClickLabel = "Change period", role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(R.drawable.ic_calendar, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildAnnotatedString {
                append(label.title)
                if (label.detail != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(" · ${label.detail}")
                    }
                }
            },
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        AppIcon(R.drawable.ic_expand, contentDescription = null, size = 18.dp)
    }
}

/** A 44dp round outlined icon button inside a 48dp touch target. */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = SpendTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                icon,
                contentDescription = contentDescription,
                size = 20.dp,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else colors.muted.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp))
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SpendTheme.colors.card,
            contentColor = SpendTheme.colors.actionText,
        ),
    ) {
        if (icon != null) {
            AppIcon(icon, contentDescription = null, size = 20.dp, tint = SpendTheme.colors.actionText)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp))
    }
}

/** Screen title row, 56dp tall, with optional actions on the right. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Header for a screen pushed over the tabs: a back arrow, then the title. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { AppIcon(R.drawable.ic_chevron_left, contentDescription = "Back") }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/** Centered message for empty lists. */
@Composable
fun EmptyNote(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
