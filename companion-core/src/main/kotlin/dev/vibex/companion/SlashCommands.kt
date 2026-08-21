package dev.vibex.companion

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class TokenPrefix(val symbol: Char) {
    Slash('/'),
    File('@'),
    Tag('#'),
    Agent('&'),
    Dollar('$'),
    ;

    companion object {
        fun from(symbol: Char): TokenPrefix? = entries.find { it.symbol == symbol }
    }
}

data class ComposerToken(
    val prefix: TokenPrefix,
    val name: String,
    val value: String = name,
    val description: String = "",
    val key: String = name,
) {
    val label: String get() = when (prefix) {
        TokenPrefix.File -> name
        else -> "${prefix.symbol}$name"
    }

    fun markup(): String = when (prefix) {
        TokenPrefix.Agent -> formatAgentMention(name, value.ifBlank { key })
        else -> formatComposerCommand(prefix.symbol, key, value)
    }
}

sealed class ComposerSegment {
    data class Text(val value: String) : ComposerSegment()
    data class Token(val token: ComposerToken, val raw: String) : ComposerSegment()
}

data class TokenDraft(
    val text: String = "",
) {
    fun materialize(): String = serializeComposerBackendMessage(text)
    val isBlank: Boolean get() = materialize().isBlank()
}

data class TokenQuery(
    val prefix: TokenPrefix,
    val query: String,
    val start: Int,
)

fun formatComposerCommand(type: Char, key: String, value: String): String =
    "[$type:${escapeComposerPart(key, ']')}](${escapeComposerPart(value, ')')})"

fun formatAgentMention(name: String, agentKind: String): String {
    val encoded = URLEncoder.encode(agentKind, StandardCharsets.UTF_8).replace("+", "%20")
    return "[&${escapeComposerPart(name, ']')}](vibex://agent/$encoded)"
}

fun escapeComposerPart(value: String, closer: Char): String = buildString {
    value.forEach { ch ->
        if (ch == '\\' || ch == closer) append('\\')
        append(ch)
    }
}

fun parseComposerSegments(source: String): List<ComposerSegment> {
    val out = mutableListOf<ComposerSegment>()
    var cursor = 0
    var scan = 0
    while (scan < source.length) {
        val mention = parseAgentMention(source, scan)
        if (mention != null) {
            if (scan > cursor) out += ComposerSegment.Text(source.substring(cursor, scan))
            out += ComposerSegment.Token(mention.first, source.substring(scan, mention.second))
            cursor = mention.second
            scan = mention.second
            continue
        }
        val explicit = parseExplicitToken(source, scan)
        if (explicit != null) {
            if (scan > cursor) out += ComposerSegment.Text(source.substring(cursor, scan))
            out += ComposerSegment.Token(explicit.first, source.substring(scan, explicit.second))
            cursor = explicit.second
            scan = explicit.second
            continue
        }
        scan += 1
    }
    if (cursor < source.length) out += ComposerSegment.Text(source.substring(cursor))
    if (out.isEmpty()) out += ComposerSegment.Text("")
    return out
}

fun joinComposerSegments(segments: List<ComposerSegment>): String = segments.joinToString("") { segment ->
    when (segment) {
        is ComposerSegment.Text -> segment.value
        is ComposerSegment.Token -> segment.raw
    }
}

fun serializeComposerBackendMessage(source: String): String =
    parseComposerSegments(source).joinToString("") { segment ->
        when (segment) {
            is ComposerSegment.Text -> segment.value
            is ComposerSegment.Token ->
                if (segment.token.prefix == TokenPrefix.Agent) segment.raw else segment.token.value
        }
    }

private fun parseAgentMention(source: String, start: Int): Pair<ComposerToken, Int>? {
    if (start + 2 > source.length || source[start] != '[' || source[start + 1] != '&') return null
    val namePart = readEscaped(source, start + 2, ']') ?: return null
    val hrefStart = namePart.second + 1
    val prefix = "(vibex://agent/"
    if (hrefStart + prefix.length > source.length) return null
    if (source.substring(hrefStart, hrefStart + prefix.length) != prefix) return null
    val kindStart = hrefStart + prefix.length
    val kindEnd = source.indexOf(')', kindStart)
    if (kindEnd <= kindStart) return null
    val kind = URLDecoder.decode(source.substring(kindStart, kindEnd), StandardCharsets.UTF_8)
    val name = namePart.first.ifBlank { kind }
    return ComposerToken(TokenPrefix.Agent, name, kind, key = kind) to (kindEnd + 1)
}

private fun parseExplicitToken(source: String, start: Int): Pair<ComposerToken, Int>? {
    if (start >= source.length || source[start] != '[') return null
    if (start + 2 >= source.length || source[start + 2] != ':') return null
    val prefix = TokenPrefix.from(source[start + 1]) ?: return null
    if (prefix == TokenPrefix.Agent) return null
    val keyPart = readEscaped(source, start + 3, ']') ?: return null
    if (keyPart.second + 1 >= source.length || source[keyPart.second + 1] != '(') return null
    val valuePart = readEscaped(source, keyPart.second + 2, ')') ?: return null
    val rawEnd = valuePart.second + 1
    val key = keyPart.first
    val value = valuePart.first
    val name = when (prefix) {
        TokenPrefix.Slash -> slashDisplayName(key, value)
        TokenPrefix.File -> key.substringAfterLast('/').ifBlank { key }
        TokenPrefix.Tag -> key.removePrefix("#")
        TokenPrefix.Dollar -> key
        TokenPrefix.Agent -> key
    }
    return ComposerToken(prefix, name, value.ifBlank { name }, key = key) to rawEnd
}


private fun slashDisplayName(key: String, value: String): String {
    val trimmed = value.trim()
    val native = Regex("^/(?!skill:)([^\\s/]+)").find(trimmed)?.groupValues?.get(1)
    return native ?: key.substringAfterLast(':').ifBlank { trimmed.removePrefix("/").substringBefore(' ') }
}

private fun readEscaped(source: String, from: Int, closer: Char): Pair<String, Int>? {
    val out = StringBuilder()
    var cursor = from
    while (cursor < source.length) {
        val ch = source[cursor]
        if (ch == '\\') {
            if (cursor + 1 >= source.length) return null
            out.append(source[cursor + 1])
            cursor += 2
            continue
        }
        if (ch == closer) return out.toString() to cursor
        out.append(ch)
        cursor += 1
    }
    return null
}

fun slashCatalog(
    agentId: String,
    host: List<ComposerToken> = emptyList(),
    skills: List<ComposerToken> = emptyList(),
): List<ComposerToken> {
    val builtin = when (normalizeAgentKey(agentId)) {
        "claude_code" -> listOf("compact", "goal", "init", "review", "context", "help", "plan")
        "codex" -> listOf("compact", "goal", "init", "plan", "review", "help")
        "opencode" -> listOf("compact", "init", "help")
        "grok" -> listOf("compact", "init", "review", "plan", "help", "context")
        else -> listOf("compact", "init", "review", "help")
    }.map { name ->
        ComposerToken(
            prefix = TokenPrefix.Slash,
            name = name,
            value = "/$name",
            key = "native:$name:$name",
            description = when (name) {
                "compact" -> "压缩当前会话上下文"
                "goal" -> "设置或查看长期目标"
                "init" -> "初始化仓库说明"
                "review" -> "审查代码"
                "context" -> "查看上下文占用"
                "plan" -> "切换到规划模式"
                "help" -> "查看可用命令"
                "status" -> "查看会话状态"
                else -> ""
            },
        )
    }
    val runtime = host.filter { it.prefix == TokenPrefix.Slash && !it.key.startsWith("skill:") }
    val native = LinkedHashMap<String, ComposerToken>()
    builtin.forEach { native[it.name.lowercase()] = it }
    runtime.forEach { native[it.name.lowercase()] = it }
    val skillTokens = (skills + host.filter { it.key.startsWith("skill:") })
        .filter { it.prefix == TokenPrefix.Slash }
        .distinctBy { it.key.ifBlank { it.value.ifBlank { it.name } }.lowercase() }
    return native.values.toList() + skillTokens
}

fun lastTokenQuery(text: String): TokenQuery? {
    val lastText = parseComposerSegments(text).lastOrNull() as? ComposerSegment.Text ?: ComposerSegment.Text(text)
    val local = lastText.value
    val match = Regex("""(^|\s)([/@#&$])([^\s\[]*)$""").find(local) ?: return null
    val prefix = TokenPrefix.from(match.groupValues[2].first()) ?: return null
    val localStart = match.range.first + match.groupValues[1].length
    val globalStart = text.length - local.length + localStart
    return TokenQuery(prefix, match.groupValues[3], globalStart)
}

fun matchingTokens(
    text: String,
    slash: List<ComposerToken>,
    files: List<ComposerToken>,
    agents: List<ComposerToken>,
    tags: List<ComposerToken> = emptyList(),
): List<ComposerToken>? {
    val query = lastTokenQuery(text) ?: return null
    val pool = when (query.prefix) {
        TokenPrefix.Slash -> slash
        TokenPrefix.File -> files
        TokenPrefix.Agent -> agents
        TokenPrefix.Tag -> tags
        TokenPrefix.Dollar -> emptyList()
    }
    if (query.query.isEmpty()) return pool
    return pool.filter {
        it.name.contains(query.query, true) ||
            it.value.contains(query.query, true) ||
            it.label.contains(query.query, true) ||
            it.description.contains(query.query, true)
    }
}

fun insertToken(draft: TokenDraft, token: ComposerToken): TokenDraft {
    val query = lastTokenQuery(draft.text)
    val kept = if (query != null) draft.text.take(query.start) else draft.text
    val spacer = if (kept.isNotEmpty() && !kept.last().isWhitespace()) " " else ""
    return TokenDraft(kept + spacer + token.markup() + " ")
}

fun removeComposerSegment(text: String, index: Int): String {
    val next = parseComposerSegments(text).filterIndexed { i, _ -> i != index }
    return joinComposerSegments(next)
}

const val COMPOSER_TOKEN_SENTINEL = "\u200B"

fun deleteComposerTokenBehind(text: String): String {
    val segments = parseComposerSegments(text)
    val lastToken = segments.indexOfLast { it is ComposerSegment.Token }
    if (lastToken < 0) return text.dropLast(1)
    val trailing = segments.lastOrNull() as? ComposerSegment.Text
    val trailingText = trailing?.value.orEmpty()
    val hasVisibleText = trailingText.any { !it.isWhitespace() && it != COMPOSER_TOKEN_SENTINEL.single() }
    if (hasVisibleText) {
        val nextText = trailingText.dropLast(1)
        val head = segments.dropLast(1)
        return joinComposerSegments(head + ComposerSegment.Text(nextText))
    }
    return joinComposerSegments(segments.filterIndexed { index, _ -> index != lastToken })
}

fun applyTextChange(
    draft: TokenDraft,
    next: String,
    slash: List<ComposerToken>,
    files: List<ComposerToken> = emptyList(),
    agents: List<ComposerToken> = emptyList(),
    tags: List<ComposerToken> = emptyList(),
): TokenDraft {
    val committed = next.endsWith(' ')
    val source = if (committed) next.dropLast(1) else next
    val query = lastTokenQuery(source) ?: return TokenDraft(next)
    if (query.prefix == TokenPrefix.Slash) {
        val taken = slash.find { source.endsWith("/${it.name}") }
        if (taken != null && (committed || next == "/${taken.name}")) {
            return insertToken(TokenDraft(source), taken)
        }
    }
    if (!committed || query.query.isBlank()) return TokenDraft(next)
    val matches = matchingTokens(source, slash, files, agents, tags).orEmpty()
    val exact = matches.find { it.name.equals(query.query, true) || it.value.equals(query.query, true) }
    val token = exact ?: when (query.prefix) {
        TokenPrefix.Tag -> ComposerToken(TokenPrefix.Tag, query.query, query.query, key = query.query)
        TokenPrefix.File -> files.find { it.name.equals(query.query, true) || it.value.endsWith(query.query) }
        else -> null
    }
    return if (token != null) insertToken(TokenDraft(source), token) else TokenDraft(next)
}
