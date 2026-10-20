package com.spendtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtracker.R

/** A text document bundled in the app's assets. */
enum class AppDocument(val title: String, val assets: List<String>, val markdown: Boolean) {
    PRIVACY("Privacy policy", listOf("PRIVACY.md"), markdown = true),
    LICENCES("Open-source licences", listOf("licenses/baloo2-OFL.txt", "licenses/mukta-OFL.txt"), markdown = false),
}

private sealed interface Block {
    data class Heading(val text: String, val level: Int) : Block
    data class Bullet(val text: AnnotatedString) : Block
    data class Paragraph(val text: AnnotatedString) : Block
    data class Plain(val text: String) : Block
}

@Composable
fun DocumentScreen(document: AppDocument, onBack: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember(document) {
        document.assets.flatMap { path ->
            val text = runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")
            if (document.markdown) parseMarkdown(text) else listOf(Block.Heading(fontName(path), 2), Block.Plain(text.trim()))
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { AppIcon(R.drawable.ic_chevron_left, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
            Text(document.title, style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            items(blocks) { block -> BlockView(block) }
        }
    }
}

private fun fontName(path: String): String = when {
    "baloo" in path -> "Baloo 2 (SIL Open Font License 1.1)"
    "mukta" in path -> "Mukta (SIL Open Font License 1.1)"
    else -> path.substringAfterLast('/')
}

@Composable
private fun BlockView(block: Block) {
    when (block) {
        is Block.Heading -> Text(
            block.text,
            style = if (block.level == 1) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = if (block.level == 1) 8.dp else 22.dp, bottom = 6.dp),
        )
        is Block.Bullet -> Row(Modifier.padding(vertical = 3.dp)) {
            Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(18.dp))
            Text(block.text, style = MaterialTheme.typography.bodyLarge)
        }
        is Block.Paragraph -> Text(
            block.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        is Block.Plain -> Text(
            block.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Just enough Markdown for PRIVACY.md: headings, bullets, paragraphs, **bold** and <links>. */
private fun parseMarkdown(text: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += Block.Paragraph(inline(paragraph.toString().trim()))
        paragraph.clear()
    }
    for (raw in text.lines()) {
        val line = raw.trimEnd()
        when {
            line.isBlank() -> flush()
            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                blocks += Block.Heading(line.drop(level).trim(), level)
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                flush()
                blocks += Block.Bullet(inline(line.trimStart().drop(2).trim()))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
    }
    flush()
    return blocks
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            text[i] == '<' && text.indexOf('>', i) > i -> {
                val end = text.indexOf('>', i)
                append(text.substring(i + 1, end))
                i = end + 1
            }
            text[i] == '`' && text.indexOf('`', i + 1) > i -> {
                val end = text.indexOf('`', i + 1)
                append(text.substring(i + 1, end))
                i = end + 1
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
