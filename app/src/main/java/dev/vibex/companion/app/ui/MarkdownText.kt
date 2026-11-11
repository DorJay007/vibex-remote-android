package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.delay

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class NumberedList(val items: List<Pair<String, String>>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data object Rule : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
}

@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier) {
    MarkdownContent(source, modifier)
}

@Composable
fun MarkdownContent(raw: String, modifier: Modifier = Modifier, fillWidth: Boolean = true) {
    val blocks = remember(raw) { parseMarkdownBlocks(raw) }
    SelectionContainer {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (block in blocks) SingleBlockView(block, fillWidth = fillWidth)
        }
    }
}

@Composable
fun SingleBlockView(block: MarkdownBlock, modifier: Modifier = Modifier, fillWidth: Boolean = true) {
    val colors = VibexTheme.colors
    val widthMod = if (fillWidth) modifier.fillMaxWidth() else modifier
    when (block) {
        is MarkdownBlock.Paragraph -> InlineMarkdownText(
            block.text,
            fontSize = 14.sp,
            lineHeight = 26.sp,
            color = colors.textPrimary,
            modifier = widthMod,
        )
        is MarkdownBlock.Heading -> InlineMarkdownText(
            block.text,
            fontSize = when (block.level) {
                1 -> 20.sp
                2 -> 18.sp
                3 -> 16.sp
                else -> 14.5.sp
            },
            lineHeight = when (block.level) {
                1 -> 26.sp
                2 -> 24.sp
                else -> 22.sp
            },
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = modifier.fillMaxWidth().padding(top = 2.dp),
        )
        is MarkdownBlock.BulletList -> Column(modifier.fillMaxWidth().padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (item in block.items) ListRow("•", item)
        }
        is MarkdownBlock.NumberedList -> Column(modifier.fillMaxWidth().padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for ((marker, content) in block.items) ListRow(marker, content)
        }
        is MarkdownBlock.Quote -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.accent.copy(alpha = 0.5f)),
            )
            InlineMarkdownText(
                block.text,
                fontSize = 14.sp,
                lineHeight = 26.sp,
                fontStyle = FontStyle.Italic,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is MarkdownBlock.Code -> CodeBlock(code = block.code, modifier = modifier, language = block.language)
        MarkdownBlock.Rule -> Box(
            modifier.fillMaxWidth().height(0.5.dp).padding(vertical = 2.dp).background(colors.hairline),
        )
        is MarkdownBlock.Table -> MarkdownTable(block, modifier)
    }
}

@Composable
private fun ListRow(marker: String, content: String) {
    val colors = VibexTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(marker, fontSize = 14.sp, color = colors.textTertiary)
        InlineMarkdownText(
            content,
            fontSize = 14.sp,
            lineHeight = 26.sp,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table, modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    Row(modifier.horizontalScroll(rememberScrollState())) {
        Column(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.codeSurface)
                .border(0.5.dp, colors.hairline, RoundedCornerShape(10.dp)),
        ) {
            TableRow(table.header, isHeader = true)
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.hairline))
            table.rows.forEachIndexed { idx, cells ->
                TableRow(cells, isHeader = false)
                if (idx < table.rows.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.stroke))
                }
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, isHeader: Boolean) {
    val colors = VibexTheme.colors
    Row {
        for (cell in cells) {
            InlineMarkdownText(
                cell,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isHeader) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.width(130.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun CodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String? = null,
    collapseAfter: Int = 20,
) {
    CodeBlockView(
        code = code,
        language = language.orEmpty(),
        modifier = modifier,
    )
}

@Composable
fun CopyIconButton(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val colors = VibexTheme.colors
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    IconButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }, modifier = modifier.size(32.dp)) {
        Icon(
            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            contentDescription = if (copied) "已复制" else "复制",
            tint = if (copied) Color(0xFF85D18F) else colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
fun InlineMarkdownText(
    raw: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 26.sp,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    color: Color = VibexTheme.colors.textPrimary,
) {
    val colors = VibexTheme.colors
    val parsed = remember(raw, colors.accent, colors.codeSurface, colors.textPrimary) {
        inlineMarkdown(raw, colors.accent, colors.codeSurface, colors.textPrimary)
    }
    Text(
        text = parsed,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun rememberInlineMarkdown(raw: String): AnnotatedString {
    val colors = VibexTheme.colors
    return remember(raw, colors.accent, colors.codeSurface, colors.textPrimary) {
        inlineMarkdown(raw, colors.accent, colors.codeSurface, colors.textPrimary)
    }
}

fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        val fence = fenceMarker(trimmed)
        if (fence != null) {
            val lang = trimmed.drop(fence.length).trim()
            val body = mutableListOf<String>()
            i += 1
            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.startsWith(fence) && t.all { it == fence[0] }) {
                    i += 1
                    break
                }
                body.add(lines[i]); i += 1
            }
            blocks.add(MarkdownBlock.Code(lang.ifEmpty { null }, body.joinToString("\n")))
            continue
        }
        if (trimmed.isEmpty()) { i += 1; continue }
        val head = heading(trimmed)
        if (head != null) {
            blocks.add(MarkdownBlock.Heading(head.first, head.second)); i += 1; continue
        }
        if (isRule(trimmed)) {
            blocks.add(MarkdownBlock.Rule); i += 1; continue
        }
        if (trimmed.startsWith(">")) {
            val quoted = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                if (!t.startsWith(">")) break
                quoted.add(t.drop(1).trim())
                i += 1
            }
            blocks.add(MarkdownBlock.Quote(quoted.joinToString("\n")))
            continue
        }
        if (i + 1 < lines.size && line.contains("|") && isTableSeparator(lines[i + 1])) {
            val header = tableCells(line)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].contains("|") && lines[i].trim().isNotEmpty()) {
                rows.add(tableCells(lines[i])); i += 1
            }
            blocks.add(MarkdownBlock.Table(header, rows))
            continue
        }
        if (bulletContent(trimmed) != null) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val c = bulletContent(lines[i].trim()) ?: break
                items.add(c); i += 1
            }
            blocks.add(MarkdownBlock.BulletList(items))
            continue
        }
        if (orderedContent(trimmed) != null) {
            val items = mutableListOf<Pair<String, String>>()
            while (i < lines.size) {
                val mc = orderedContent(lines[i].trim()) ?: break
                items.add(mc); i += 1
            }
            blocks.add(MarkdownBlock.NumberedList(items))
            continue
        }
        val para = mutableListOf<String>()
        while (i < lines.size) {
            val l = lines[i]
            val t = l.trim()
            if (t.isEmpty()) break
            if (fenceMarker(t) != null) break
            if (heading(t) != null) break
            if (isRule(t)) break
            if (t.startsWith(">")) break
            if (bulletContent(t) != null) break
            if (orderedContent(t) != null) break
            if (i + 1 < lines.size && l.contains("|") && isTableSeparator(lines[i + 1])) break
            para.add(l); i += 1
        }
        if (para.isNotEmpty()) blocks.add(MarkdownBlock.Paragraph(para.joinToString("\n")))
    }
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(raw)) }
}

private fun fenceMarker(trimmed: String): String? = when {
    trimmed.startsWith("```") -> "```"
    trimmed.startsWith("~~~") -> "~~~"
    else -> null
}

private fun heading(trimmed: String): Pair<Int, String>? {
    var n = 0
    for (ch in trimmed) if (ch == '#') n += 1 else break
    if (n !in 1..6) return null
    val rest = trimmed.drop(n)
    if (rest.isNotEmpty() && !rest.startsWith(" ")) return null
    return n to rest.trim()
}

private fun isRule(trimmed: String): Boolean {
    val s = trimmed.replace(" ", "")
    if (s.length < 3) return false
    return s.all { it == '-' } || s.all { it == '*' } || s.all { it == '_' }
}

private fun bulletContent(trimmed: String): String? {
    for (p in listOf("- ", "* ", "+ ")) if (trimmed.startsWith(p)) return trimmed.drop(2)
    return null
}

private fun orderedContent(trimmed: String): Pair<String, String>? {
    var idx = 0
    while (idx < trimmed.length && trimmed[idx].isDigit()) idx += 1
    if (idx == 0 || idx >= trimmed.length) return null
    val sep = trimmed[idx]
    if (sep != '.' && sep != ')') return null
    val after = idx + 1
    if (after >= trimmed.length || trimmed[after] != ' ') return null
    return "${trimmed.substring(0, idx)}." to trimmed.substring(after + 1)
}

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    if (!t.contains("-") || !t.contains("|")) return false
    return t.all { it == '|' || it == '-' || it == ':' || it == ' ' }
}

private fun tableCells(line: String): List<String> {
    var t = line.trim()
    if (t.startsWith("|")) t = t.drop(1)
    if (t.endsWith("|")) t = t.dropLast(1)
    return t.split("|").map { it.trim() }
}

private fun inlineMarkdown(raw: String, linkColor: Color, codeBg: Color, codeColor: Color): AnnotatedString =
    buildAnnotatedString { parseInline(this, raw, linkColor, codeBg, codeColor) }

private fun parseInline(
    builder: AnnotatedString.Builder,
    raw: String,
    linkColor: Color,
    codeBg: Color,
    codeColor: Color,
) {
    var i = 0
    val n = raw.length
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            builder.append(plain.toString())
            plain.clear()
        }
    }
    while (i < n) {
        val c = raw[i]
        when {
            c == '`' -> {
                val end = raw.indexOf('`', i + 1)
                if (end > i) {
                    flush()
                    val code = raw.substring(i + 1, end)
                    val badge = resourceBadgePrefix(code, code) ?: ""
                    val decorated = if (badge.isNotEmpty() && !code.startsWith(badge)) "$badge$code" else code
                    builder.withStyle(SpanStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, background = codeBg, color = codeColor)) {
                        append(" $decorated ")
                    }
                    i = end + 1
                } else {
                    plain.append(c); i++
                }
            }
            (c == '*' || c == '_') && i + 1 < n && raw[i + 1] == c -> {
                val marker = "$c$c"
                val end = raw.indexOf(marker, i + 2)
                if (end > i + 1) {
                    flush()
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        parseInline(this, raw.substring(i + 2, end), linkColor, codeBg, codeColor)
                    }
                    i = end + 2
                } else {
                    plain.append(c); i++
                }
            }
            raw.startsWith("~~", i) -> {
                val end = raw.indexOf("~~", i + 2)
                if (end > i + 1) {
                    flush()
                    builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        parseInline(this, raw.substring(i + 2, end), linkColor, codeBg, codeColor)
                    }
                    i = end + 2
                } else {
                    plain.append(c); i++
                }
            }
            (c == '*' || c == '_') -> {
                val end = raw.indexOf(c, i + 1)
                if (end > i) {
                    flush()
                    builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        parseInline(this, raw.substring(i + 1, end), linkColor, codeBg, codeColor)
                    }
                    i = end + 1
                } else {
                    plain.append(c); i++
                }
            }
            c == '[' -> {
                val close = raw.indexOf(']', i + 1)
                val openParen = if (close >= 0 && close + 1 < n && raw[close + 1] == '(') close + 1 else -1
                val closeParen = if (openParen >= 0) raw.indexOf(')', openParen + 1) else -1
                if (close > i && openParen >= 0 && closeParen > openParen) {
                    flush()
                    val label = raw.substring(i + 1, close)
                    val href = raw.substring(openParen + 1, closeParen)
                    if (looksLikeWorkspaceResource(label) || looksLikeWorkspaceResource(href)) {
                        val badge = resourceBadgePrefix(label, href) ?: ""
                        val decorated = if (badge.isNotEmpty() && !label.startsWith(badge)) "$badge$label" else label
                        builder.withStyle(SpanStyle(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, background = codeBg, color = codeColor)) {
                            append(" $decorated ")
                        }
                    } else {
                        builder.withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                            parseInline(this, label, linkColor, codeBg, codeColor)
                        }
                    }
                    i = closeParen + 1
                } else {
                    plain.append(c); i++
                }
            }
            else -> { plain.append(c); i++ }
        }
    }
    flush()
}

private val AdrRegex = Regex("^ADR-\\d+$", RegexOption.IGNORE_CASE)
private val ResourceExts = listOf(
    ".swift", ".ts", ".tsx", ".rs", ".kt", ".js", ".jsx",
    ".json", ".md", ".css", ".py", ".go", ".toml", ".yaml", ".yml", ".html",
)

private fun resourceBadgePrefix(label: String, href: String): String? {
    val cleanLabel = label.trim().removeSurrounding("`")
    val upper = cleanLabel.uppercase()
    val lowerHref = href.trim().lowercase()
    if (upper == "README" || upper.startsWith("README.") || lowerHref.endsWith("/readme.md") || lowerHref == "readme.md") {
        return "ⓘ "
    }
    if (AdrRegex.matches(upper) || cleanLabel.lowercase().endsWith(".md") || lowerHref.endsWith(".md")) {
        return "M↓ "
    }
    if (cleanLabel.endsWith("/") && !cleanLabel.contains(" ") && cleanLabel.length > 2) {
        return "📁 "
    }
    return null
}

private fun looksLikeWorkspaceResource(candidate: String): Boolean {
    val stripped = candidate
        .removePrefix("ⓘ ")
        .removePrefix("M↓ ")
        .removePrefix("📁 ")
        .trim()
        .removeSurrounding("`")
    if (stripped.isEmpty() || stripped.contains(" ")) return false
    val upper = stripped.uppercase()
    if (upper == "README" || AdrRegex.matches(upper)) return true
    if (stripped.endsWith("/") || stripped.startsWith("/") || stripped.startsWith("./") || stripped.contains("/")) {
        return !stripped.startsWith("http://") && !stripped.startsWith("https://")
    }
    val lower = stripped.lowercase()
    return ResourceExts.any { lower.endsWith(it) }
}
