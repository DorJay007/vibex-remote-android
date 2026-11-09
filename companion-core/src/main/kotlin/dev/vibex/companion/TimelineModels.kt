package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue

enum class TimelineTone { Live, Hold, Quiet, Stop }

enum class PendingKind { Permission, Question, Blocked }

enum class NoticeSeverity { Error, Warning, Info }

data class PermissionOption(
    val id: String,
    val label: String,
    val kind: String = "allow_once",
)

enum class PermissionAllowScope(val id: String, val label: String, val persistScope: String?) {
    Once("once", "允许一次", null),
    Session("session", "本会话允许", "session"),
    Always("always", "始终允许", "always");

    val preferredKinds: List<String>
        get() = when (this) {
            Once -> listOf("allow_once", "allow")
            Session -> listOf("allow_for_session", "allow_session", "allow_always", "allow_once", "allow")
            Always -> listOf("allow_always", "allow_for_session", "allow_once", "allow")
        }
}

data class PermissionDiffPreview(
    val path: String,
    val oldText: String?,
    val newText: String,
) {
    val id: String get() = path
}

enum class PermissionCategory(val label: String) {
    Terminal("终端命令"),
    File("文件读写"),
    Web("网络访问"),
    Tool("工具调用"),
}

data class PermissionPrompt(
    val id: String,
    val toolName: String,
    val kind: String? = null,
    val heading: String? = null,
    val subtitle: String? = null,
    val command: String? = null,
    val textBlocks: List<String> = emptyList(),
    val diffs: List<PermissionDiffPreview> = emptyList(),
    val locations: List<String> = emptyList(),
    val options: List<PermissionOption> = emptyList(),
) {
    val category: PermissionCategory
        get() {
            val k = (kind ?: toolName).lowercase()
            if (k.contains("bash") || k.contains("exec") || k.contains("command") || k.contains("shell") || k.contains("terminal") || command != null) {
                return PermissionCategory.Terminal
            }
            if (k.contains("edit") || k.contains("write") || k.contains("read") || k.contains("file") || diffs.isNotEmpty() || locations.isNotEmpty()) {
                return PermissionCategory.File
            }
            if (k.contains("fetch") || k.contains("web") || k.contains("http") || k.contains("search")) {
                return PermissionCategory.Web
            }
            return PermissionCategory.Tool
        }

    val displayHeading: String
        get() {
            val h = heading?.trim().orEmpty()
            if (h.isNotEmpty() && h.lowercase() != "tool") return h
            val t = toolName.trim()
            if (t.isNotEmpty() && t != "未知工具" && t.lowercase() != "tool") return t
            return "需要权限确认"
        }

    val displaySubtitle: String?
        get() {
            val s = subtitle?.trim().orEmpty()
            if (s.isEmpty() || s == displayHeading) return null
            return s
        }

    fun resolveAllowOption(scope: PermissionAllowScope): PermissionOption? {
        for (preferred in scope.preferredKinds) {
            options.firstOrNull { it.kind.lowercase() == preferred }?.let { return it }
        }
        return options.firstOrNull { it.kind.lowercase().startsWith("allow") } ?: options.firstOrNull()
    }

    val rejectOption: PermissionOption?
        get() = options.firstOrNull {
            val k = it.kind.lowercase()
            k.startsWith("reject") || k.startsWith("deny") || k == "cancel"
        }

    companion object {
        fun from(payload: JsonValue?): PermissionPrompt? {
            val root = payload?.asObject() ?: return null
            val eventObj = root.objOrNull("event") ?: root
            val wrapper = eventObj.objOrNull("request") ?: eventObj
            val id = wrapper.textAny("permission_id", "permissionId", "id")
            if (id.isBlank()) return null

            val req = wrapper.objOrNull("request") ?: wrapper
            val details = req.objOrNull("details")
                ?: wrapper.objOrNull("details")
                ?: req.objOrNull("tool_call")
                ?: req.objOrNull("toolCall")
                ?: wrapper.objOrNull("tool_call")
                ?: wrapper.objOrNull("toolCall")
            val fields = details?.objOrNull("fields") ?: details
            val rawInput = fields?.objOrNull("rawInput")
                ?: fields?.objOrNull("raw_input")
                ?: details?.objOrNull("rawInput")
                ?: details?.objOrNull("raw_input")

            val parsedOptions = req.arrOrNull("options")
                .orEmpty()
                .ifEmpty { wrapper.arrOrNull("options").orEmpty() }
                .mapNotNull { optRaw ->
                    val opt = optRaw.asObject() ?: return@mapNotNull null
                    val optId = opt.textAny("option_id", "optionId", "id")
                    if (optId.isBlank()) return@mapNotNull null
                    val label = opt.textAny("label", "name").ifBlank { optId }
                    val kind = opt.textAny("kind").ifBlank {
                        val lower = "$optId $label".lowercase()
                        when {
                            lower.contains("reject") || lower.contains("deny") || lower.contains("拒绝") -> "reject_once"
                            lower.contains("always") || lower.contains("始终") -> "allow_always"
                            lower.contains("session") || lower.contains("会话") -> "allow_for_session"
                            else -> "allow_once"
                        }
                    }
                    PermissionOption(id = optId, label = label, kind = kind)
                }
            val options = parsedOptions.ifEmpty {
                listOf(
                    PermissionOption(id = "allow_once", label = "允许", kind = "allow_once"),
                    PermissionOption(id = "reject_once", label = "拒绝", kind = "reject_once"),
                )
            }

            val rawTitle = req.textAny("title", "reason").trim().takeIf { it.isNotEmpty() }
                ?: wrapper.textAny("title", "reason").trim().takeIf { it.isNotEmpty() }
            val title = rawTitle ?: "权限请求"

            val command: String? = run {
                val cmdObj = rawInput ?: fields ?: req
                var extracted: String? = null
                for (key in listOf("command", "cmd", "script")) {
                    val cmdVal = cmdObj.value[key] ?: req.value[key]
                    val str = when (cmdVal) {
                        is JsonValue.Text -> cmdVal.value.trim().takeIf { it.isNotBlank() }
                        is JsonValue.Array -> cmdVal.value.mapNotNull { it.asText() }.joinToString(" ").trim().takeIf { it.isNotBlank() }
                        else -> null
                    }
                    if (str != null) {
                        extracted = str
                        break
                    }
                }
                extracted
            }

            fun extractShellCommandLabel(): String? {
                val src = rawInput ?: return null
                for (key in listOf("description", "title", "summary")) {
                    val label = src.textOrNull(key)?.trim()?.takeIf { it.isNotEmpty() && it != command }
                    if (label != null) return label
                }
                return null
            }

            val meta = details?.objOrNull("_meta") ?: fields?.objOrNull("_meta")
            val claudeCode = meta?.objOrNull("claudeCode")
            val air = meta?.objOrNull("jetbrains")?.objOrNull("air")
            val permMeta = meta?.objOrNull("permission") ?: air?.objOrNull("permission")
            val metaHeadingRaw = claudeCode?.textOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: air?.textOrNull("commandTitle")?.trim()?.takeIf { it.isNotEmpty() }
                ?: permMeta?.textOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }

            val metaHeading = if (metaHeadingRaw != null && command != null && metaHeadingRaw == command) {
                extractShellCommandLabel()
            } else {
                metaHeadingRaw
            }

            val heading: String?
            val subtitle: String?
            if (metaHeading != null) {
                heading = metaHeading
                subtitle = null
            } else {
                heading = null
                val toolCallTitle = fields?.textOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: details?.textOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }
                val fallbackCandidate = rawTitle ?: toolCallTitle
                    ?: req.textAny("description", "prompt", "message").trim().takeIf { it.isNotEmpty() }
                subtitle = if (fallbackCandidate != null && command != null && fallbackCandidate == command) {
                    extractShellCommandLabel()
                } else {
                    fallbackCandidate ?: extractShellCommandLabel()
                }
            }

            val textBlocks = mutableListOf<String>()
            val diffs = mutableListOf<PermissionDiffPreview>()
            fields?.arrOrNull("content").orEmpty().forEach { itemRaw ->
                val item = itemRaw.asObject() ?: return@forEach
                when (item.textAny("type")) {
                    "diff" -> {
                        val path = item.textAny("path").ifBlank { "(file)" }
                        val newText = item.textAny("new_text", "newText")
                        val oldText = item.textOrNull("old_text") ?: item.textOrNull("oldText").orEmpty()
                        diffs += PermissionDiffPreview(path = path, oldText = oldText, newText = newText)
                    }
                    else -> {
                        val text = (item.textOrNull("text") ?: item.objOrNull("content")?.textOrNull("text"))
                            ?.trim()
                            .orEmpty()
                        if (text.isNotEmpty()) textBlocks += text
                    }
                }
            }

            val locations = mutableSetOf<String>()
            fields?.arrOrNull("locations").orEmpty().forEach { locRaw ->
                val path = locRaw.asObject()?.textAny("path")?.trim().orEmpty()
                if (path.isNotBlank()) locations += path
            }
            listOf("file_path", "filePath", "path", "notebook_path", "url").forEach { key ->
                val str = rawInput?.textOrNull(key)?.trim().orEmpty()
                if (str.isNotBlank()) locations += str
            }

            val kind = fields?.textOrNull("kind")?.trim()?.takeIf { it.isNotEmpty() }
            val effectiveCommand = if (diffs.isEmpty() && textBlocks.isEmpty()) command else null

            val toolName = req.textAny("tool_name", "toolName")
                .ifBlank { title }

            return PermissionPrompt(
                id = id,
                toolName = toolName,
                kind = kind,
                heading = heading,
                subtitle = subtitle,
                command = effectiveCommand,
                textBlocks = textBlocks,
                diffs = diffs,
                locations = locations.sorted(),
                options = options,
            )
        }
    }
}

data class PendingQuestion(
    val id: String,
    val prompt: String,
    val options: List<PermissionOption> = emptyList(),
    val schema: JsonValue? = null,
) {
    companion object {
        fun from(payload: JsonValue?): PendingQuestion? {
            val root = payload?.asObject() ?: return null
            val eventObj = root.objOrNull("event") ?: root
            val req = eventObj.objOrNull("request") ?: eventObj
            val id = req.textAny("question_id", "questionId", "feedback_id", "feedbackId", "id")
            if (id.isBlank()) return null
            val prompt = req.textAny("prompt", "question", "title", "message")
            val opts = req.arrOrNull("options").orEmpty().mapNotNull { o ->
                when (o) {
                    is JsonValue.Text -> {
                        val text = o.value.trim()
                        if (text.isBlank()) null
                        else PermissionOption(id = text, label = text, kind = "choice")
                    }
                    is JsonValue.Object -> {
                        val optId = o.textAny("option_id", "optionId", "id", "label", "name")
                        if (optId.isBlank()) null
                        else {
                            val label = o.textAny("label", "name").ifBlank { optId }
                            PermissionOption(id = optId, label = label, kind = o.textAny("kind").ifBlank { "choice" })
                        }
                    }
                    else -> null
                }
            }
            val rawSchema = req.value["schema"] ?: req.value["requested_schema"] ?: req.value["requestedSchema"]
            val schema = rawSchema?.takeUnless { it is JsonValue.Null }
            return PendingQuestion(
                id = id,
                prompt = prompt,
                options = opts,
                schema = schema,
            )
        }
    }
}

data class ToolEdit(
    val path: String,
    val oldText: String,
    val newText: String,
)

data class ToolStep(
    val id: String,
    val name: String,
    val label: String,
    val kind: String,
    val status: String,
    val preview: String,
    val detail: List<String> = emptyList(),
    val rawInput: String? = null,
    val edits: List<ToolEdit> = emptyList(),
    val questions: List<FormQuestion> = emptyList(),
) {
    val isFinished: Boolean
        get() {
            val s = status.lowercase()
            return s == "completed" || s == "failed" || s == "done" || s == "error" || s == "success"
        }

    val isAskQuestion: Boolean
        get() {
            val lower = "${name.lowercase()} ${kind.lowercase()}"
            return lower.contains("ask_question") ||
                lower.contains("askuserquestion") ||
                lower.contains("ask_user") ||
                questions.isNotEmpty()
        }
}

data class PlanTask(
    val id: Int,
    val label: String,
    val status: Status,
    val priority: String? = null,
) {
    enum class Status { Pending, Running, Completed }
}

data class FileChangeItem(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val hunks: String? = null,
) {
    val id: String get() = path
    val name: String
        get() = path.substringAfterLast('/').ifBlank { path }
}

sealed class TimelineSegment {
    abstract val id: String

    data class Thinking(
        override val id: String,
        val text: String,
        val durationMs: Int? = null,
    ) : TimelineSegment()

    data class Tools(
        override val id: String,
        val steps: List<ToolStep>,
    ) : TimelineSegment()

    data class Text(
        override val id: String,
        val text: String,
    ) : TimelineSegment()
}

data class TimelineItem(
    val id: String,
    val role: Role,
    val text: String = "",
    val thinking: String? = null,
    val thinkingDurationMs: Int? = null,
    val tools: List<ToolStep> = emptyList(),
    val changes: List<FileChangeItem> = emptyList(),
    val tasks: List<PlanTask> = emptyList(),
    val segments: List<TimelineSegment> = emptyList(),
    val streaming: Boolean = false,
    val modelName: String? = null,
) {
    enum class Role { User, Assistant }
}

object ToolInput {
    private val pathKeys = listOf(
        "file_path", "filepath", "path", "target_file", "file",
        "filename", "notebook_path", "uri", "source", "destination",
    )
    private val commandKeys = listOf("command", "cmd", "script")
    private val queryKeys = listOf("pattern", "query", "glob", "search", "regex", "url", "prompt", "description")
    private val skipDetailKeys = (pathKeys + listOf(
        "content", "new_string", "old_string", "new_source", "edits", "patch", "diff", "text", "questions",
    )).toSet()

    fun parse(raw: JsonValue?): ParsedToolInput {
        if (raw == null || raw is JsonValue.Null) return ParsedToolInput("", emptyList(), null, emptyList(), emptyList())
        return when (raw) {
            is JsonValue.Text -> {
                val trimmed = raw.value.trim()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    val decoded = runCatching { JsonValue.parse(trimmed) }.getOrNull()
                    if (decoded != null && decoded !is JsonValue.Text) {
                        return parse(decoded)
                    }
                }
                val detailLines = trimmed.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(4)
                    .map { if (it.length > 80) it.take(80) + "…" else it }
                    .toList()
                ParsedToolInput(cleanTarget(trimmed), detailLines, trimmed, emptyList(), emptyList())
            }
            is JsonValue.Object -> {
                val dict = raw.value
                var target = ""
                for (key in pathKeys) {
                    val s = dict[key]?.asText()?.takeIf { it.isNotBlank() }
                    if (s != null) {
                        target = cleanPath(s)
                        break
                    }
                }
                if (target.isEmpty()) {
                    for (key in commandKeys) {
                        val s = dict[key]?.asText()?.takeIf { it.isNotBlank() }
                        if (s != null) {
                            target = cleanCommand(s)
                            break
                        }
                    }
                }
                if (target.isEmpty()) {
                    for (key in queryKeys) {
                        val s = dict[key]?.asText()?.takeIf { it.isNotBlank() }
                        if (s != null) {
                            target = cleanTarget(s)
                            break
                        }
                    }
                }

                val detail = mutableListOf<String>()
                for (key in dict.keys.sorted()) {
                    if (skipDetailKeys.contains(key.lowercase())) continue
                    val value = dict[key] ?: continue
                    val rendered = scalarSummary(value) ?: continue
                    if (rendered.isEmpty() || rendered == target) continue
                    detail += "$key: $rendered"
                    if (detail.size >= 4) break
                }

                val edits = extractEdits(dict)
                val questions = extractQuestions(dict)
                if (target.isEmpty() && questions.isNotEmpty()) {
                    target = questions.first().header ?: questions.first().title
                }
                val rawString = raw.encode()
                ParsedToolInput(target, detail, rawString, edits, questions)
            }
            else -> ParsedToolInput("", emptyList(), null, emptyList(), emptyList())
        }
    }

    data class ParsedToolInput(
        val target: String,
        val detail: List<String>,
        val raw: String?,
        val edits: List<ToolEdit>,
        val questions: List<FormQuestion>,
    )

    private fun extractQuestions(dict: Map<String, JsonValue>): List<FormQuestion> {
        val list = dict["questions"]?.asArray()?.value
        if (!list.isNullOrEmpty()) {
            val wrapped = JsonValue.Object(mapOf("x-vibex-questions" to JsonValue.Array(list)))
            val parsed = QuestionForm.parseQuestions(wrapped)
            if (parsed.isNotEmpty()) return parsed
        }
        val singleQuestion = dict["question"]?.asText()?.trim().orEmpty()
        if (singleQuestion.isNotEmpty()) {
            val choices = dict["options"]?.asArray()?.value.orEmpty().mapNotNull { item ->
                when (item) {
                    is JsonValue.Text -> {
                        val (clean, rec) = QuestionForm.stripRecommendedPrefix(item.value)
                        QuestionChoice(value = clean, label = clean, recommended = rec)
                    }
                    is JsonValue.Object -> {
                        val rawLabel = item.textAny("label", "title", "value").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val (clean, rec) = QuestionForm.stripRecommendedPrefix(rawLabel)
                        val valStr = item.textAny("value").ifBlank { clean }
                        QuestionChoice(
                            value = valStr,
                            label = clean,
                            description = item.textOrNull("description"),
                            recommended = (item.boolOrNull("recommended") == true) || rec,
                        )
                    }
                    else -> null
                }
            }
            return listOf(
                FormQuestion(
                    id = "q_0",
                    header = dict["header"]?.asText(),
                    title = singleQuestion,
                    description = dict["description"]?.asText(),
                    options = choices,
                    multiSelect = dict["multi_select"]?.asBool() ?: (dict["multiSelect"]?.asBool() ?: false),
                ),
            )
        }
        return emptyList()
    }

    private fun extractEdits(dict: Map<String, JsonValue>): List<ToolEdit> {
        var filePath = ""
        for (key in pathKeys) {
            val s = dict[key]?.asText()?.takeIf { it.isNotBlank() }
            if (s != null) {
                filePath = s
                break
            }
        }
        if (filePath.isEmpty()) return emptyList()

        val oldStr = dict["old_string"]?.asText() ?: dict["old_text"]?.asText() ?: dict["oldText"]?.asText()
        val newStr = dict["new_string"]?.asText() ?: dict["new_text"]?.asText() ?: dict["newText"]?.asText()
        if (oldStr != null || newStr != null) {
            return listOf(ToolEdit(path = filePath, oldText = oldStr.orEmpty(), newText = newStr.orEmpty()))
        }
        val content = dict["content"]?.asText() ?: dict["new_source"]?.asText() ?: dict["file_text"]?.asText()
        if (!content.isNullOrEmpty()) {
            return listOf(ToolEdit(path = filePath, oldText = "", newText = content))
        }
        val editsArray = dict["edits"]?.asArray()?.value
        if (!editsArray.isNullOrEmpty()) {
            return editsArray.mapNotNull { item ->
                val obj = item.asObject()?.value ?: return@mapNotNull null
                val oldPart = obj["old_string"]?.asText() ?: obj["old_text"]?.asText() ?: obj["oldText"]?.asText() ?: ""
                val newPart = obj["new_string"]?.asText() ?: obj["new_text"]?.asText() ?: obj["newText"]?.asText() ?: ""
                if (oldPart.isEmpty() && newPart.isEmpty()) null
                else ToolEdit(path = filePath, oldText = oldPart, newText = newPart)
            }
        }
        return emptyList()
    }

    fun label(name: String, target: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Tool"
        if (!target.isEmpty() && trimmed.endsWith(target) && trimmed.length > target.length) {
            val prefix = trimmed.dropLast(target.length).trim()
            if (prefix.isNotEmpty()) return prefix
        }
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        if (parts.size == 2) {
            val head = parts[0]
            val tail = parts[1]
            if (tail.contains("/") || tail.contains(".") || tail == target) {
                return head
            }
        }
        return trimmed
    }

    private fun cleanPath(raw: String): String {
        val trimmed = raw.trim()
        val parts = trimmed.split('/').filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> parts.takeLast(2).joinToString("/")
            parts.size == 1 -> parts[0]
            else -> trimmed
        }
    }

    private fun cleanCommand(raw: String): String {
        val firstLine = raw.lineSequence().firstOrNull()?.trim().orEmpty()
        return if (firstLine.length > 56) firstLine.take(56) + "…" else firstLine
    }

    private fun cleanTarget(raw: String): String {
        val firstLine = raw.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.contains("/") && !firstLine.contains(" ")) {
            return cleanPath(firstLine)
        }
        return if (firstLine.length > 48) firstLine.take(48) + "…" else firstLine
    }

    private fun scalarSummary(value: JsonValue): String? = when (value) {
        is JsonValue.Text -> {
            val s = value.value.trim()
            if (s.isEmpty()) null else if (s.length > 60) s.take(60) + "…" else s
        }
        is JsonValue.Number -> {
            val n = value.value
            if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
        }
        is JsonValue.Bool -> if (value.value) "true" else "false"
        is JsonValue.Array -> if (value.value.isEmpty()) null else "${value.value.size} items"
        is JsonValue.Object, is JsonValue.Null -> null
    }
}

data class TimelineRow(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val tone: TimelineTone,
    val thinking: String = "",
    val thinkingExpanded: Boolean = false,
    val thinkingDurationMs: Int? = null,
    val toolName: String = "",
    val pendingKind: PendingKind? = null,
    val pendingId: String = "",
    val conversationId: String = "",
    val turnId: String = "",
    val childConversationId: String = "",
    val options: List<PendingOption> = emptyList(),
    val toolKind: String = "",
    val toolStatus: String = "",
    val toolInput: String = "",
    val modelName: String? = null,
)

data class PendingOption(
    val id: String,
    val label: String,
)

data class PendingItem(
    val conversationId: String,
    val conversationTitle: String,
    val kind: PendingKind,
    val id: String,
    val title: String,
    val body: String,
    val options: List<PendingOption>,
    val rowId: String,
)

data class QueuedInput(
    val id: String,
    val text: String,
    val status: String,
    val revision: Long = 0,
    val sortKey: Long = 0,
)

data class SessionNotice(
    val id: String,
    val severity: NoticeSeverity,
    val title: String,
    val body: String = "",
    val action: String = "",
)

data class SessionConfigChoice(
    val value: String,
    val label: String,
)

data class SessionConfigOption(
    val key: String,
    val label: String,
    val category: String = "",
    val value: String = "",
    val choices: List<SessionConfigChoice> = emptyList(),
)

data class SessionMode(
    val id: String,
    val name: String,
)

data class ConversationView(
    val conversationId: String,
    val title: String,
    val agentId: String,
    val workspaceId: String,
    val rows: List<TimelineRow>,
    val items: List<TimelineItem> = emptyList(),
    val planTasks: List<PlanTask> = emptyList(),
    val fileChanges: List<FileChangeItem> = emptyList(),
    val permissionPrompt: PermissionPrompt? = null,
    val pendingQuestion: PendingQuestion? = null,
    val queued: List<QueuedInput>,
    val inFlightTurnId: String?,
    val canSteer: Boolean,
    val lastSequence: Long,
    val usageLabel: String?,
    val planLines: List<String>,
    val fileChangeLabel: String?,
    val additions: Long = 0,
    val deletions: Long = 0,
    val currentModeId: String = "",
    val sessionModes: List<SessionMode> = emptyList(),
    val sessionConfig: List<SessionConfigOption> = emptyList(),
    val notices: List<SessionNotice> = emptyList(),
    val availableCommands: List<ComposerToken> = emptyList(),
    val skillCommands: List<ComposerToken> = emptyList(),
)

class TimelineSession {
    var conversationId: String = ""
    var title: String = ""
    var agentId: String = ""
    var workspaceId: String = ""
    var lastSequence: Long = 0
    var inFlightTurnId: String? = null
    var canSteer: Boolean = false
    var usageLabel: String? = null
    var fileChangeLabel: String? = null
    var agentStatus: String = ""
    var currentModeId: String = ""
    var additions: Long = 0
    var deletions: Long = 0
    var permissionPrompt: PermissionPrompt? = null
    var pendingQuestion: PendingQuestion? = null
    val planLines: MutableList<String> = mutableListOf()
    val planTasks: MutableList<PlanTask> = mutableListOf()
    val fileChanges: MutableList<FileChangeItem> = mutableListOf()
    val rows: MutableList<TimelineRow> = mutableListOf()
    val queued: MutableList<QueuedInput> = mutableListOf()
    val sessionModes: MutableList<SessionMode> = mutableListOf()
    val sessionConfig: MutableList<SessionConfigOption> = mutableListOf()
    val notices: MutableList<SessionNotice> = mutableListOf()
    val availableCommands: MutableList<ComposerToken> = mutableListOf()
    val skillCommands: MutableList<ComposerToken> = mutableListOf()

    fun snapshot(): ConversationView {
        val builtItems = TimelineBuilder.buildItems(this)
        return ConversationView(
            conversationId = conversationId,
            title = title.ifBlank { "会话" },
            agentId = agentId,
            workspaceId = workspaceId,
            rows = rows.toList(),
            items = builtItems,
            planTasks = planTasks.toList(),
            fileChanges = fileChanges.toList(),
            permissionPrompt = permissionPrompt,
            pendingQuestion = pendingQuestion,
            queued = queued.toList(),
            inFlightTurnId = inFlightTurnId,
            canSteer = canSteer,
            lastSequence = lastSequence,
            usageLabel = usageLabel,
            planLines = planLines.toList(),
            fileChangeLabel = fileChangeLabel,
            additions = additions,
            deletions = deletions,
            currentModeId = currentModeId,
            sessionModes = sessionModes.toList(),
            sessionConfig = sessionConfig.toList(),
            notices = notices.toList(),
            availableCommands = availableCommands.toList(),
            skillCommands = skillCommands.toList(),
        )
    }
}

object TimelineBuilder {
    fun buildItems(session: TimelineSession): List<TimelineItem> {
        val result = mutableListOf<TimelineItem>()
        var currentAssistant: TimelineItem? = null

        fun flushAssistant() {
            val item = currentAssistant ?: return
            val hydrated = if (item.changes.isEmpty()) {
                val rebuilt = rebuildChangesFromTools(item.tools)
                if (rebuilt.isNotEmpty()) item.copy(changes = rebuilt) else item
            } else {
                item
            }
            if (hydrated.segments.isNotEmpty() || hydrated.text.isNotBlank() || hydrated.thinking != null || hydrated.tools.isNotEmpty() || hydrated.changes.isNotEmpty()) {
                result += hydrated
            }
            currentAssistant = null
        }

        fun ensureAssistant(turnId: String, streaming: Boolean, modelName: String?): TimelineItem {
            val existing = currentAssistant
            if (existing != null) {
                return existing.copy(
                    streaming = existing.streaming || streaming,
                    modelName = modelName ?: existing.modelName,
                ).also { currentAssistant = it }
            }
            val created = TimelineItem(
                id = "assistant:${turnId.ifBlank { result.size.toString() }}:${result.size}",
                role = TimelineItem.Role.Assistant,
                streaming = streaming,
                modelName = modelName,
            )
            currentAssistant = created
            return created
        }

        for (row in session.rows) {
            if (row.pendingKind != null) continue
            when (row.kind) {
                "user" -> {
                    flushAssistant()
                    if (row.body.isNotBlank()) {
                        result += TimelineItem(
                            id = row.id,
                            role = TimelineItem.Role.User,
                            text = row.body,
                        )
                    }
                }
                "reasoning" -> {
                    if (row.body.isBlank()) continue
                    val asst = ensureAssistant(row.turnId, row.tone == TimelineTone.Live, row.modelName)
                    val segments = asst.segments.toMutableList()
                    val last = segments.lastOrNull()
                    if (last is TimelineSegment.Thinking) {
                        segments[segments.lastIndex] = TimelineSegment.Thinking(
                            id = last.id,
                            text = last.text + "\n\n" + row.body,
                            durationMs = row.thinkingDurationMs ?: last.durationMs,
                        )
                    } else {
                        segments += TimelineSegment.Thinking(
                            id = row.id,
                            text = row.body,
                            durationMs = row.thinkingDurationMs,
                        )
                    }
                    val combinedThinking = if (asst.thinking.isNullOrBlank()) row.body else "${asst.thinking}\n\n${row.body}"
                    currentAssistant = asst.copy(
                        thinking = combinedThinking,
                        thinkingDurationMs = row.thinkingDurationMs ?: asst.thinkingDurationMs,
                        segments = segments,
                    )
                }
                "tool" -> {
                    val asst = ensureAssistant(row.turnId, row.tone == TimelineTone.Live, row.modelName)
                    val parsedInput = runCatching {
                        if (row.toolInput.isNotBlank()) ToolInput.parse(JsonValue.parse(row.toolInput))
                        else ToolInput.parse(null)
                    }.getOrElse { ToolInput.parse(JsonValue.Text(row.toolInput)) }
                    val rawName = row.toolName.ifBlank { row.title }.ifBlank { "Tool" }
                    val label = ToolInput.label(rawName, parsedInput.target)
                    val detailLines = buildList {
                        addAll(parsedInput.detail)
                        if (row.body.isNotBlank() && size < 4) {
                            row.body.lineSequence()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .take(4 - size)
                                .forEach { add(it) }
                        }
                    }
                    val step = ToolStep(
                        id = row.id,
                        name = rawName,
                        label = label,
                        kind = row.toolKind.ifBlank { rawName },
                        status = row.toolStatus.ifBlank {
                            when (row.tone) {
                                TimelineTone.Live -> "running"
                                TimelineTone.Stop -> "failed"
                                else -> "completed"
                            }
                        },
                        preview = parsedInput.target,
                        detail = detailLines,
                        rawInput = parsedInput.raw ?: row.toolInput.takeIf { it.isNotBlank() },
                        edits = parsedInput.edits,
                        questions = parsedInput.questions,
                    )
                    val tools = asst.tools.toMutableList()
                    val existingToolIdx = tools.indexOfFirst { it.id == step.id }
                    if (existingToolIdx >= 0) tools[existingToolIdx] = step else tools += step

                    val segments = asst.segments.toMutableList()
                    val last = segments.lastOrNull()
                    if (last is TimelineSegment.Tools) {
                        val updatedSteps = last.steps.toMutableList()
                        val idx = updatedSteps.indexOfFirst { it.id == step.id }
                        if (idx >= 0) updatedSteps[idx] = step else updatedSteps += step
                        segments[segments.lastIndex] = TimelineSegment.Tools(id = last.id, steps = updatedSteps)
                    } else {
                        segments += TimelineSegment.Tools(id = "tools:${step.id}", steps = listOf(step))
                    }
                    currentAssistant = asst.copy(tools = tools, segments = segments)
                }
                "assistant" -> {
                    if (row.body.isBlank()) continue
                    val asst = ensureAssistant(row.turnId, row.tone == TimelineTone.Live, row.modelName)
                    val segments = asst.segments.toMutableList()
                    val last = segments.lastOrNull()
                    if (last is TimelineSegment.Text) {
                        val sep = if (last.text.endsWith("\n") || row.body.startsWith("\n")) "" else "\n\n"
                        segments[segments.lastIndex] = TimelineSegment.Text(
                            id = last.id,
                            text = last.text + sep + row.body,
                        )
                    } else {
                        segments += TimelineSegment.Text(id = row.id, text = row.body)
                    }
                    val combinedText = if (asst.text.isBlank()) row.body else "${asst.text}\n\n${row.body}"
                    currentAssistant = asst.copy(text = combinedText, segments = segments)
                }
                "turn_failed", "agent_binding_recovery_failed", "agent_binding_load_failed" -> {
                    if (row.body.isBlank()) continue
                    val asst = ensureAssistant(row.turnId, false, row.modelName)
                    val errText = "⚠️ ${row.body}"
                    val segments = asst.segments + TimelineSegment.Text(id = row.id, text = errText)
                    val combinedText = if (asst.text.isBlank()) errText else "${asst.text}\n\n$errText"
                    currentAssistant = asst.copy(text = combinedText, segments = segments)
                }
            }
        }
        flushAssistant()

        if (session.fileChanges.isNotEmpty() && result.isNotEmpty()) {
            val lastAsstIdx = result.indexOfLast { it.role == TimelineItem.Role.Assistant }
            if (lastAsstIdx >= 0) {
                val target = result[lastAsstIdx]
                val rebuiltByPath = rebuildChangesFromTools(target.tools).associateBy { it.path }
                val mergedChanges = session.fileChanges.map { change ->
                    if (!change.hunks.isNullOrBlank()) change
                    else {
                        val rebuilt = rebuiltByPath[change.path]
                            ?: rebuiltByPath.entries.firstOrNull { (k, _) ->
                                k.endsWith("/${change.name}") || change.path.endsWith("/$k")
                            }?.value
                        if (rebuilt != null && !rebuilt.hunks.isNullOrBlank()) {
                            change.copy(hunks = rebuilt.hunks)
                        } else {
                            change
                        }
                    }
                }
                result[lastAsstIdx] = target.copy(changes = mergedChanges)
            }
        }

        if (session.inFlightTurnId != null && result.isNotEmpty()) {
            val lastIdx = result.lastIndex
            if (result[lastIdx].role == TimelineItem.Role.Assistant) {
                result[lastIdx] = result[lastIdx].copy(streaming = true)
            }
        }

        return result
    }

    fun rebuildChangesFromTools(tools: List<ToolStep>): List<FileChangeItem> {
        val order = mutableListOf<String>()
        val byPath = mutableMapOf<String, Pair<Int, Int>>()
        val hunksByPath = mutableMapOf<String, MutableList<String>>()
        for (step in tools) {
            for (edit in step.edits) {
                val oldLines = if (edit.oldText.isEmpty()) emptyList() else edit.oldText.split("\n")
                val newLines = if (edit.newText.isEmpty()) emptyList() else edit.newText.split("\n")
                val add = newLines.size
                val del = oldLines.size
                val existing = byPath[edit.path]
                if (existing != null) {
                    byPath[edit.path] = (existing.first + add) to (existing.second + del)
                } else {
                    order += edit.path
                    byPath[edit.path] = add to del
                }
                val hunkLines = mutableListOf("@@ -1,$del +1,$add @@")
                oldLines.forEach { hunkLines += "-$it" }
                newLines.forEach { hunkLines += "+$it" }
                hunksByPath.getOrPut(edit.path) { mutableListOf() } += hunkLines.joinToString("\n")
            }
        }
        return order.mapNotNull { path ->
            val counts = byPath[path] ?: return@mapNotNull null
            val hunks = hunksByPath[path]?.joinToString("\n")
            FileChangeItem(path = path, additions = counts.first, deletions = counts.second, hunks = hunks)
        }
    }
}
