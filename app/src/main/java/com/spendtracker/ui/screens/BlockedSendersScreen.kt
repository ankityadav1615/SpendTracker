package com.spendtracker.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendtracker.R
import com.spendtracker.data.BlockedSender
import com.spendtracker.ui.theme.SpendTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BLOCKED_ON = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Senders the user marked as spam. Their messages are skipped until unblocked. */
@Composable
fun BlockedSendersScreen(senders: List<BlockedSender>, onBack: () -> Unit, onUnblock: (String) -> Unit) {
    val colors = SpendTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        BackHeader("Blocked senders", onBack)
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text(
                    "Messages from these senders are ignored, so offers, reward points and spam don't count as money.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (senders.isEmpty()) {
                item {
                    EmptyNote(
                        "No blocked senders",
                        "Open a payment that isn't real, tap \"Not a real payment\" and tick \"Also ignore every message\".",
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                item {
                    SpendCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        senders.forEachIndexed { index, sender ->
                            if (index > 0) RowDivider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Tile(colors.tint) {
                                    AppIcon(R.drawable.ic_eye_off, contentDescription = null, size = 20.dp, tint = colors.muted)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(sender.key, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(sender.example, "since ${blockedOn(sender.blockedAt)}").joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { onUnblock(sender.key) }) {
                                    Text("Unblock", style = MaterialTheme.typography.labelLarge, color = colors.actionText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun blockedOn(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(BLOCKED_ON)
