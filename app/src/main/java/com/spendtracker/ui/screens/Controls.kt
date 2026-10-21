package com.spendtracker.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.R
import com.spendtracker.model.Change
import com.spendtracker.model.ReportSide
import com.spendtracker.ui.theme.SpendTheme

/** Background of segmented toggles: a touch darker than the warm tint. */
@Composable
private fun trackColor(): Color = if (SpendTheme.colors.isDark) SpendTheme.colors.tint else Color(0xFFF3E6D6)

/** Two or more equal segments, e.g. "Expenses / Income". */
@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = SpendTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(trackColor())
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (on) Modifier.shadow(1.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (on) colors.card else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.5.sp),
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A small icon-only toggle, e.g. ring chart / bar chart. */
@Composable
fun IconToggle(icons: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = SpendTheme.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(trackColor())
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        icons.forEachIndexed { index, (icon, description) ->
            val on = index == selected
            Box(
                Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .then(if (on) Modifier.shadow(1.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (on) colors.card else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    icon,
                    contentDescription = null,
                    size = 20.dp,
                    tint = if (on) colors.actionText else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A selectable chip; the selected one turns dark with a check mark. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    iconTint: Color = Color.Unspecified,
) {
    val colors = SpendTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.5.sp)) },
        leadingIcon = when {
            selected -> {
                { AppIcon(R.drawable.ic_check, contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            icon != null -> {
                { AppIcon(icon, contentDescription = null, size = 18.dp, tint = iconTint) }
            }
            else -> null
        },
        modifier = Modifier.heightIn(min = 40.dp),
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.card,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = if (selected) null else BorderStroke(1.dp, colors.line),
    )
}

/** "↑ 12%", "↓ 3%", "Same" or "New". More spending is the warning colour; more income is green. */
@Composable
fun ChangeBadge(change: Change, side: ReportSide) {
    val colors = SpendTheme.colors
    val warm = if (colors.isDark) Color(0xFF4A2A22) to Color(0xFFFFB4A3) else Color(0xFFFBE4DF) to Color(0xFFA1361F)
    val good = if (colors.isDark) Color(0xFF213A2A) to Color(0xFF8FD3A6) else Color(0xFFE3F0E6) to Color(0xFF2F7A4B)
    val neutral = colors.tint to MaterialTheme.colorScheme.onSurfaceVariant
    val more = if (side == ReportSide.EXPENSES) warm else good
    val less = if (side == ReportSide.EXPENSES) good else warm
    val (text, palette, spoken) = when (change) {
        Change.Same -> Triple("Same", neutral, "about the same as before")
        Change.New -> Triple("New", neutral, "nothing in the earlier period")
        is Change.Up -> Triple("↑ ${change.percent}%", more, "up ${change.percent} percent")
        is Change.Down -> Triple("↓ ${change.percent}%", less, "down ${change.percent} percent")
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
        color = palette.second,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.first)
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .semantics { contentDescription = spoken },
    )
}

/** A large selectable card with an icon, title and line of help, and a round check on the right. */
@Composable
fun OptionCard(
    @DrawableRes icon: Int,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = SpendTheme.colors
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(colors.card)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) accent else colors.line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tile(iconBackground) { AppIcon(icon, contentDescription = null, size = 22.dp, tint = iconTint) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) accent else Color.Transparent)
                .border(1.5.dp, if (selected) accent else colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) AppIcon(R.drawable.ic_check, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
fun DetailLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
