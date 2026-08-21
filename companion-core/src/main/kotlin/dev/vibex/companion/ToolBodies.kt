package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue

data class ToolHit(
    val path: String? = null,
    val url: String? = null,
    val line: String? = null,
    val text: String = "",
    val directory: Boolean = false,
)

data class DirEntry(
    val name: String,
    val folder: Boolean,
)

data class DiffLine(
    val kind: Kind,
    val text: String,
) {
    enum class Kind { ADD, DEL, CONTEXT, HUNK, META }
}

sealed class ToolBodyModel {
    data class FileRead(val path: String, val content: String, val startLine: Int = 1) : ToolBodyModel()
    data class FileEdit(
        val path: String,
        val lines: List<DiffLine>,
        val additions: Int,
        val deletions: Int,
    ) : ToolBodyModel()
    data class SearchHits(val query: String, val items: List<ToolHit>) : ToolBodyModel()
    data class DirList(val path: String, val entries: List<DirEntry>) : ToolBodyModel()
    data class Terminal(val command: String, val output: String) : ToolBodyModel()
    data class Web(val url: String, val snippet: String) : ToolBodyModel()
    data class Todos(val items: List<Pair<String, String>>) : ToolBodyModel()
    data class Facts(val fields: List<Pair<String, String>>) : ToolBodyModel()
    data class Prose(val text: String) : ToolBodyModel()
}

enum class ToolFamily { READ, EDIT, SEARCH, LIST, EXECUTE, WEB, TODO, TASK, OTHER }

fun toolFamily(name: String, kind: String): ToolFamily {
    when (kind.lowercase()) {
        "read" -> return ToolFamily.READ
        "edit", "write", "delete", "move" -> return ToolFamily.EDIT
        "search" -> return ToolFamily.SEARCH
        "execute", "command", "bash", "shell", "terminal" -> return ToolFamily.EXECUTE
        "fetch", "web" -> return ToolFamily.WEB
    }
    val n = canonicalToolName(name)
    return when {
        n in listDirNames -> ToolFamily.LIST
        n in readNames -> ToolFamily.READ
        n in todoNames -> ToolFamily.TODO
        n in taskNames -> ToolFamily.TASK
        n in editNames -> ToolFamily.EDIT
        n in searchNames -> ToolFamily.SEARCH
        n in executeNames -> ToolFamily.EXECUTE
        n in webNames -> ToolFamily.WEB
        else -> ToolFamily.OTHER
    }
}

fun presentToolBody(name: String, kind: String, input: String, output: String): ToolBodyModel {
    val family = toolFamily(name, kind)
    val args = effectiveArgs(parseJson(input))
    val outValue = parseJson(output) ?: output.trim().takeIf { it.isNotEmpty() }?.let { JsonValue.Text(it) }
    return when (family) {
        ToolFamily.READ -> presentRead(args, outValue, output)
        ToolFamily.EDIT -> presentEdit(args, output)
        ToolFamily.SEARCH -> presentSearch(args, outValue, output)
        ToolFamily.LIST -> presentDir(args, outValue, output)
        ToolFamily.EXECUTE -> presentTerminal(args, input, output)
        ToolFamily.WEB -> presentWeb(args, outValue, output)
        ToolFamily.TODO -> presentTodos(args, input)
        ToolFamily.TASK, ToolFamily.OTHER -> presentGeneric(args, outValue, output, input)
    }
}

internal fun canonicalToolName(name: String): String {
    var n = name.lowercase()
    val idx = n.lastIndexOf("__")
    if (idx >= 0) n = n.substring(idx + 2)
    return n.replace(Regex("[\\s._-]"), "")
}

private val readNames = setOf("read", "readfile", "readtextfile", "cat", "view", "viewfile", "open", "openfile")
private val editNames = setOf(
    "edit", "streplace", "strreplace", "strreplaceeditor", "strreplacebasededittool",
    "applypatch", "patch", "multiedit", "editfile", "updatefile",
    "write", "writefile", "createfile", "notebookedit", "writetofile", "replaceinfile",
)
private val searchNames = setOf(
    "grep", "ripgrep", "grepsearch", "searchfiles", "search", "searchtext",
    "glob", "find", "filesearch", "listcodedefinitionnames",
)
private val listDirNames = setOf("ls", "listdir", "listfiles", "listdirectory", "readdir", "listfolder", "lsdir")
private val executeNames = setOf(
    "bash", "shell", "sh", "exec", "execcommand", "execute", "executecommand",
    "runcommand", "command", "runterminalcmd", "terminal", "writestdin", "localshell",
)
private val webNames = setOf("webfetch", "webfetchtool", "fetch", "websearch", "browser", "browseraction", "openurl")
private val todoNames = setOf("todowrite", "updatetodolist", "todos")
private val taskNames = setOf("task", "taskcreate", "taskupdate", "tasklist", "agent", "dispatchagent", "newtask", "subagent", "spawnagent")

private val outputTextKeys = listOf(
    "stdout", "stderr", "output", "text", "FileContent", "file_content",
    "fileContent", "content", "result", "value",
)
private val pathKeys = listOf(
    "file_path", "target_file", "targetFile", "path", "filename", "file",
    "abs_path", "filepath", "filePath", "dir", "directory",
)

internal fun parseJson(raw: String?): JsonValue? {
    if (raw.isNullOrBlank()) return null
    val t = raw.trim()
    if (!t.startsWith("{") && !t.startsWith("[")) return null
    return runCatching { JsonValue.parse(t) }.getOrNull()
}

internal fun effectiveArgs(parsed: JsonValue?): JsonValue.Object? {
    val obj = parsed?.asObject() ?: return null
    for (key in listOf("input", "arguments", "params", "payload")) {
        obj.objOrNull(key)?.let { return it }
    }
    return obj
}

internal fun jsonString(obj: JsonValue.Object?, vararg keys: String): String? {
    if (obj == null) return null
    for (key in keys) {
        val value = obj.value[key] ?: continue
        when (value) {
            is JsonValue.Text -> if (value.value.isNotBlank()) return value.value
            is JsonValue.Number -> return value.encode()
            is JsonValue.Bool -> return value.value.toString()
            else -> Unit
        }
    }
    return null
}

internal fun extractOutputText(value: JsonValue?, depth: Int = 0): String {
    if (depth > 6 || value == null || value is JsonValue.Null) return ""
    when (value) {
        is JsonValue.Text -> {
            val text = value.value
            val trimmed = text.trim()
            if (depth < 5 && (trimmed.startsWith("{") || trimmed.startsWith("["))) {
                parseJson(trimmed)?.let { nested ->
                    val inner = extractOutputText(nested, depth + 1)
                    if (inner.isNotBlank()) return inner
                }
            }
            return text
        }
        is JsonValue.Number, is JsonValue.Bool -> return value.encode()
        is JsonValue.Array -> return value.value.map { extractOutputText(it, depth + 1) }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        is JsonValue.Object -> {
            for (key in outputTextKeys) {
                val found = extractOutputText(value.value[key], depth + 1)
                if (found.isNotBlank()) return found
            }
        }
        else -> Unit
    }
    return ""
}

fun unwrapToolOutput(raw: String): String {
    if (raw.isBlank()) return raw
    val parsed = parseJson(raw)
    if (parsed != null) {
        val extracted = extractOutputText(parsed)
        if (extracted.isNotBlank() && extracted.trim() != raw.trim()) {
            return decodeJsonEscapes(extracted)
        }
        val obj = parsed.asObject()
        for (key in listOf("FileContent", "file_content", "fileContent")) {
            val nested = extractOutputText(obj?.value?.get(key))
            if (nested.isNotBlank()) return decodeJsonEscapes(nested)
        }
        if (extracted.isNotBlank()) return decodeJsonEscapes(extracted)
    }
    extractTruncatedJsonField(raw)?.let { return decodeJsonEscapes(it) }
    return decodeJsonEscapes(raw)
}

internal fun decodeJsonEscapes(value: String): String {
    if (!value.contains('\\')) return value
    if (value.contains('\n')) return value
    if ("\\n" !in value && "\\t" !in value && "\\\"" !in value) return value
    return buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    'r' -> append('\r')
                    '"' -> append('"')
                    '\\' -> append('\\')
                    else -> {
                        append(c)
                        append(value[i + 1])
                    }
                }
                i += 2
            } else {
                append(c)
                i += 1
            }
        }
    }
}

internal fun extractTruncatedJsonField(raw: String): String? {
    for (key in listOf("content", "FileContent", "file_content", "stdout", "output", "text")) {
        val needle = "\"$key\""
        val idx = raw.indexOf(needle)
        if (idx < 0) continue
        val colon = raw.indexOf(':', idx + needle.length)
        if (colon < 0) continue
        var rest = raw.substring(colon + 1).trimStart()
        if (rest.startsWith("{")) {
            extractTruncatedJsonField(rest)?.let { return it }
            continue
        }
        if (!rest.startsWith('"')) continue
        rest = rest.drop(1)
        val out = StringBuilder()
        var i = 0
        while (i < rest.length) {
            val c = rest[i]
            if (c == '\\' && i + 1 < rest.length) {
                out.append(c)
                out.append(rest[i + 1])
                i += 2
                continue
            }
            if (c == '"') break
            out.append(c)
            i += 1
        }
        if (out.isNotEmpty()) return out.toString()
    }
    return null
}

internal fun stripNumberedFileLines(content: String): Pair<String, Int> {
    val lines = content.split('\n')
    if (lines.isEmpty()) return content to 1
    val prefix = Regex("""^(?: {0,3}(\d+)(?:→|[|:]\s?|\t))""")
    val parsed = lines.map { line ->
        val match = prefix.find(line)
        if (match != null) match.groupValues[1].toInt() to line.substring(match.value.length) else null
    }
    val numbered = parsed.count { it != null }
    if (numbered == 0 || numbered < lines.size * 0.7) return content to 1
    val start = parsed.firstOrNull { it != null }?.first ?: 1
    val body = parsed.mapIndexed { i, item -> item?.second ?: lines[i] }.joinToString("\n")
    return body to start
}

internal fun parseDirectoryListing(value: JsonValue?): List<DirEntry> {
    if (value == null) return emptyList()
    when (value) {
        is JsonValue.Text -> {
            val trimmed = value.value.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                parseJson(trimmed)?.let { nested ->
                    val parsed = parseDirectoryListing(nested)
                    if (parsed.isNotEmpty()) return parsed
                }
            }
            return trimmed.split('\n').mapNotNull { line ->
                val cleaned = line.replace(Regex("""^[│├└─\s*|`\-]+"""), "").trim()
                if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return@mapNotNull null
                val isDir = Regex("""[\\/]\s*$""").containsMatchIn(cleaned)
                DirEntry(fileName(cleaned.replace(Regex("""[\\/]+$"""), "")), isDir)
            }
        }
        is JsonValue.Array -> return value.value.flatMap { parseDirectoryListing(it) }
        is JsonValue.Object -> {
            val dirs = value.arrOrNull("directories")
            val files = value.arrOrNull("files")
            if (dirs != null || files != null) {
                return (dirs.orEmpty().flatMap { parseDirectoryListing(it) }.map { it.copy(folder = true) }) +
                    files.orEmpty().flatMap { parseDirectoryListing(it) }
            }
            for (key in listOf("entries", "children", "items", "listing", "files", "value", "content")) {
                value.value[key]?.let {
                    val nested = parseDirectoryListing(it)
                    if (nested.isNotEmpty()) return nested
                }
            }
            val name = jsonString(value, "name", "path", "filename", "file") ?: return emptyList()
            val hint = jsonString(value, "type", "kind", "entry_type").orEmpty().lowercase()
            val flagged = value.value["is_dir"]?.asBool() == true || value.value["isDirectory"]?.asBool() == true
            val folder = flagged || hint == "dir" || hint == "directory" || hint == "folder" || name.endsWith("/")
            return listOf(DirEntry(fileName(name), folder))
        }
        else -> return emptyList()
    }
}

internal fun parseToolHits(value: JsonValue?): List<ToolHit> {
    if (value == null) return emptyList()
    when (value) {
        is JsonValue.Text -> {
            val trimmed = value.value.trim()
            parseJson(trimmed)?.let {
                val nested = parseToolHits(it)
                if (nested.isNotEmpty()) return nested
            }
            return hitsFromString(trimmed)
        }
        is JsonValue.Array -> {
            val fromRecords = value.value.mapNotNull { item ->
                when (item) {
                    is JsonValue.Text -> {
                        if (looksLikeHttp(item.value)) ToolHit(url = item.value, text = item.value)
                        else null
                    }
                    is JsonValue.Object -> hitFromRecord(item)
                    else -> null
                }
            }
            if (fromRecords.isNotEmpty()) return fromRecords
            return value.value.flatMap { parseToolHits(it) }
        }
        is JsonValue.Object -> {
            for (key in listOf("results", "items", "matches", "hits", "value", "action", "sources")) {
                value.value[key]?.let {
                    val nested = parseToolHits(it)
                    if (nested.isNotEmpty()) return nested
                }
            }
            return hitFromRecord(value)?.let { listOf(it) }.orEmpty()
        }
        else -> return emptyList()
    }
}

internal fun parseUnifiedDiff(diff: String): List<DiffLine> {
    if (diff.isBlank()) return emptyList()
    return diff.split('\n').map { line ->
        when {
            line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ") ->
                DiffLine(DiffLine.Kind.META, line)
            line.startsWith("@@") -> DiffLine(DiffLine.Kind.HUNK, line)
            line.startsWith("+") -> DiffLine(DiffLine.Kind.ADD, line)
            line.startsWith("-") -> DiffLine(DiffLine.Kind.DEL, line)
            else -> DiffLine(DiffLine.Kind.CONTEXT, line)
        }
    }
}

internal fun synthesizeReplaceDiff(old: String, new: String): String = buildString {
    append("@@ -1,${old.lineCount()} +1,${new.lineCount()} @@\n")
    old.split('\n').forEach { append("-$it\n") }
    new.split('\n').forEach { append("+$it\n") }
}

private fun String.lineCount(): Int = if (isEmpty()) 1 else split('\n').size

private fun presentRead(args: JsonValue.Object?, outValue: JsonValue?, rawOut: String): ToolBodyModel {
    val path = jsonString(args, *pathKeys.toTypedArray()).orEmpty()
    val extracted = unwrapToolOutput(rawOut).ifBlank { extractOutputText(outValue) }
    val (content, start) = stripNumberedFileLines(extracted)
    val offset = jsonString(args, "offset", "line_start", "start_line", "startLine", "line")?.toIntOrNull()
    return ToolBodyModel.FileRead(path, content, offset?.coerceAtLeast(1) ?: start)
}

private fun presentEdit(args: JsonValue.Object?, output: String): ToolBodyModel {
    val path = jsonString(args, *pathKeys.toTypedArray()).orEmpty()
    val diff = jsonString(args, "unified_diff", "diff", "patch", "output")
        ?: extractOutputText(parseJson(output)).takeIf { it.contains("@@") || it.startsWith("diff ") || it.startsWith("--- ") }
        ?: output.takeIf { it.contains("@@") || it.startsWith("diff ") }
    val old = jsonString(args, "old_string", "old_str", "oldString")
    val new = jsonString(args, "new_string", "new_str", "newString", "replacement")
    val content = jsonString(args, "contents", "content")
    val unified = when {
        !diff.isNullOrBlank() -> diff
        old != null && new != null -> synthesizeReplaceDiff(old, new)
        content != null -> synthesizeReplaceDiff("", content)
        else -> output
    }
    val lines = parseUnifiedDiff(unified)
    val additions = lines.count { it.kind == DiffLine.Kind.ADD }
    val deletions = lines.count { it.kind == DiffLine.Kind.DEL }
    return ToolBodyModel.FileEdit(path.ifBlank { fileNameFromDiff(unified) }, lines, additions, deletions)
}

private fun presentSearch(args: JsonValue.Object?, outValue: JsonValue?, rawOut: String): ToolBodyModel {
    val query = jsonString(args, "pattern", "query", "regex", "q", "glob").orEmpty()
    val items = parseToolHits(outValue).ifEmpty { parseToolHits(parseJson(rawOut) ?: JsonValue.Text(rawOut)) }
    return ToolBodyModel.SearchHits(query, items)
}

private fun presentDir(args: JsonValue.Object?, outValue: JsonValue?, rawOut: String): ToolBodyModel {
    val path = jsonString(args, *pathKeys.toTypedArray()).orEmpty()
    val entries = parseDirectoryListing(outValue).ifEmpty {
        parseDirectoryListing(parseJson(rawOut) ?: JsonValue.Text(rawOut))
    }
    return ToolBodyModel.DirList(path, entries)
}

private fun presentTerminal(args: JsonValue.Object?, input: String, output: String): ToolBodyModel {
    val fromArray = (args?.value?.get("command") as? JsonValue.Array)?.value
        ?.mapNotNull { it.asText() }
        ?.joinToString(" ")
        ?.takeIf { it.isNotBlank() }
    val command = jsonString(args, "command", "cmd", "script", "shell_command", "commandLine")
        ?: fromArray
        ?: input.trim().takeIf { !it.startsWith("{") }.orEmpty()
    val out = unwrapToolOutput(output).ifBlank { extractOutputText(parseJson(output)) }
    return ToolBodyModel.Terminal(command, out)
}

private fun presentWeb(args: JsonValue.Object?, outValue: JsonValue?, rawOut: String): ToolBodyModel {
    val url = jsonString(args, "url", "href", "uri").orEmpty()
    val snippet = unwrapToolOutput(rawOut).ifBlank { extractOutputText(outValue) }
    return ToolBodyModel.Web(url, snippet)
}

private fun presentTodos(args: JsonValue.Object?, input: String): ToolBodyModel {
    val todos = args?.arrOrNull("todos") ?: args?.arrOrNull("todoList")
    if (todos.isNullOrEmpty()) {
        return presentGeneric(args, null, "", input)
    }
    val items = todos.mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val status = jsonString(obj, "status").orEmpty().ifBlank { "pending" }
        val text = jsonString(obj, "content", "title") ?: return@mapNotNull null
        status to text
    }
    return ToolBodyModel.Todos(items)
}

private fun presentGeneric(
    args: JsonValue.Object?,
    outValue: JsonValue?,
    rawOut: String,
    input: String,
): ToolBodyModel {
    val hits = parseToolHits(outValue)
    if (hits.isNotEmpty()) return ToolBodyModel.SearchHits("", hits)
    val prose = unwrapToolOutput(rawOut).ifBlank { extractOutputText(outValue) }
    if (prose.isNotBlank() && !prose.trimStart().startsWith("{") && !prose.trimStart().startsWith("[")) {
        return ToolBodyModel.Prose(prose)
    }
    val fields = factFields(args)
    if (fields.isNotEmpty()) return ToolBodyModel.Facts(fields)
    if (prose.isNotBlank()) return ToolBodyModel.Prose(prose)
    if (input.isNotBlank()) return ToolBodyModel.Facts(factFields(effectiveArgs(parseJson(input))))
    return ToolBodyModel.Prose("")
}

private fun factFields(obj: JsonValue.Object?): List<Pair<String, String>> {
    if (obj == null) return emptyList()
    val skip = setOf("id", "tool_use_id", "tool_call_id", "type")
    return obj.value.mapNotNull { (k, v) ->
        if (k.lowercase() in skip) return@mapNotNull null
        if (v is JsonValue.Null) return@mapNotNull null
        k to flattenFact(v)
    }
}

private fun flattenFact(value: JsonValue): String = when (value) {
    is JsonValue.Text -> value.value
    is JsonValue.Number, is JsonValue.Bool -> value.encode()
    is JsonValue.Array, is JsonValue.Object -> value.encode()
    JsonValue.Null -> ""
}

private fun hitFromRecord(obj: JsonValue.Object): ToolHit? {
    val url = jsonString(obj, "url", "uri", "href", "link")?.takeIf { looksLikeHttp(it) }
    val path = jsonString(obj, "path", "file", "file_path", "target_file", "filename")
        ?.takeIf { !looksLikeHttp(it) }
    val line = jsonString(obj, "line", "line_number", "lineNumber")
        ?: obj.value["line"]?.asNumber()?.toLong()?.toString()
    val text = jsonString(obj, "title", "text", "content", "match", "preview", "snippet", "description").orEmpty()
    if (url == null && path == null && text.isBlank()) return null
    return ToolHit(path = path, url = url, line = line, text = text, directory = path?.endsWith("/") == true)
}

private fun hitsFromString(value: String): List<ToolHit> {
    val urlPattern = Regex("""https?://[^\s)<>"']+""")
    return value.split('\n').mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        val url = urlPattern.find(trimmed)?.value
        if (url != null) {
            val title = trimmed.replace(url, "").replace(Regex("""^[-\d.)\s]+"""), "").trim()
            return@mapNotNull ToolHit(url = url, text = title.ifBlank { url })
        }
        val match = Regex("""^(.*):(\d+):\s?(.*)$""").matchEntire(trimmed)
        if (match != null) {
            return@mapNotNull ToolHit(
                path = match.groupValues[1],
                line = match.groupValues[2],
                text = match.groupValues[3],
            )
        }
        ToolHit(text = trimmed)
    }
}

private fun looksLikeHttp(value: String): Boolean = value.startsWith("http://") || value.startsWith("https://")

private fun fileName(path: String): String = path.split('/', '\\').lastOrNull { it.isNotEmpty() } ?: path

private fun fileNameFromDiff(diff: String): String {
    val match = Regex("""^(?:\+\+\+|---) [ab]/(.+)$""", RegexOption.MULTILINE).find(diff)
    return match?.groupValues?.get(1)?.trim().orEmpty()
}
