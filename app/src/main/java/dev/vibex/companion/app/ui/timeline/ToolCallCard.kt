package dev.vibex.companion.app.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.DiffLine
import dev.vibex.companion.DirEntry
import dev.vibex.companion.TimelineRow
import dev.vibex.companion.ToolBodyModel
import dev.vibex.companion.ToolFamily
import dev.vibex.companion.ToolHit
import dev.vibex.companion.presentToolBody
import dev.vibex.companion.toolFamily
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.TimelineTone
import dev.vibex.companion.app.ui.CodeBlock
import dev.vibex.companion.app.ui.MarkdownContent
import dev.vibex.companion.app.ui.theme.VibexTheme
import org.json.JSONArray
import org.json.JSONObject

enum class ToolCallState { RUNNING, DONE, ERROR }

enum class ToolKindBucket { READ, EDIT, SEARCH, LIST, EXECUTE, WEB, TODO, TASK, OTHER }

data class ToolCallVM(
    val id: String,
    val displayTitle: String,
    val rawName: String,
    val kind: String,
    val state: ToolCallState,
    val input: String,
    val output: String,
    val bucket: ToolKindBucket,
) {
    val hasOutput: Boolean get() = output.isNotBlank()
    val isError: Boolean get() = state == ToolCallState.ERROR
    val isCommand: Boolean get() = bucket == ToolKindBucket.EXECUTE
}

fun TimelineRow.toToolVM(): ToolCallVM {
    val name = toolName.ifBlank { title }
    val parsed = ToolDerive.parseJson(toolInput)
    val derived = ToolDerive.title(name, parsed)
    return ToolCallVM(
        id = id,
        displayTitle = if (parsed != null) derived else title.ifBlank { derived },
        rawName = name,
        kind = toolKind,
        state = when {
            tone == TimelineTone.Stop ||
                toolStatus.contains("fail", true) ||
                toolStatus.contains("error", true) -> ToolCallState.ERROR
            toolStatus.contains("complet", true) ||
                toolStatus.contains("success", true) ||
                (tone == TimelineTone.Quiet && (body.isNotBlank() || toolStatus.isNotBlank())) -> ToolCallState.DONE
            else -> ToolCallState.RUNNING
        },
        input = toolInput,
        output = body,
        bucket = toolFamily(name, toolKind).toBucket(),
    )
}

private fun ToolFamily.toBucket(): ToolKindBucket = when (this) {
    ToolFamily.READ -> ToolKindBucket.READ
    ToolFamily.EDIT -> ToolKindBucket.EDIT
    ToolFamily.SEARCH -> ToolKindBucket.SEARCH
    ToolFamily.LIST -> ToolKindBucket.LIST
    ToolFamily.EXECUTE -> ToolKindBucket.EXECUTE
    ToolFamily.WEB -> ToolKindBucket.WEB
    ToolFamily.TODO -> ToolKindBucket.TODO
    ToolFamily.TASK -> ToolKindBucket.TASK
    ToolFamily.OTHER -> ToolKindBucket.OTHER
}


fun bucketIcon(bucket: ToolKindBucket): ImageVector = when (bucket) {
    ToolKindBucket.READ -> Icons.Rounded.Description
    ToolKindBucket.EDIT -> Icons.Rounded.Edit
    ToolKindBucket.SEARCH -> Icons.Rounded.Search
    ToolKindBucket.LIST -> Icons.Rounded.Folder
    ToolKindBucket.EXECUTE -> Icons.Rounded.Terminal
    ToolKindBucket.WEB -> Icons.Rounded.Language
    ToolKindBucket.TODO -> Icons.Rounded.Checklist
    ToolKindBucket.TASK -> Icons.Rounded.Groups
    ToolKindBucket.OTHER -> Icons.Rounded.Build
}

@Composable
fun ToolCallCard(vm: ToolCallVM, modifier: Modifier = Modifier, nested: Boolean = false) {
    val colors = VibexTheme.colors
    val autoExpand = vm.state == ToolCallState.RUNNING || vm.state == ToolCallState.ERROR
    var expanded by remember(vm.id) { mutableStateOf(autoExpand) }
    val hasBody = vm.input.isNotBlank() || vm.hasOutput
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (nested) androidx.compose.ui.graphics.Color.Transparent else colors.bgElevated.copy(alpha = 0.5f))
            .border(0.5.dp, colors.hairline, RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = hasBody) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolStateIcon(vm)
            Text(
                text = vm.displayTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (hasBody) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && hasBody,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolBody(vm)
            }
        }
    }
}

@Composable
fun ToolGroupCard(items: List<ToolCallVM>, modifier: Modifier = Modifier, streaming: Boolean = false) {
    val colors = VibexTheme.colors
    var expanded by remember(items.firstOrNull()?.id) { mutableStateOf(false) }
    val errorCount = items.count { it.state == ToolCallState.ERROR }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgElevated.copy(alpha = 0.5f))
            .border(0.5.dp, colors.hairline, RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Layers, null, tint = colors.accent, modifier = Modifier.size(15.dp))
            Text(
                text = groupSummary(items),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (streaming) {
                CircularProgressIndicator(Modifier.size(12.dp), colors.accent, 1.5.dp)
            } else if (errorCount > 0) {
                Text("$errorCount 失败", color = colors.danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (item in items) ToolCallCard(item, nested = true)
            }
        }
    }
}

@Composable
private fun ToolStateIcon(vm: ToolCallVM) {
    val colors = VibexTheme.colors
    when (vm.state) {
        ToolCallState.RUNNING ->
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent)
        ToolCallState.DONE ->
            Icon(bucketIcon(vm.bucket), null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
        ToolCallState.ERROR ->
            Icon(Icons.Rounded.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(15.dp))
    }
}

@Composable
fun ToolExpandedBody(vm: ToolCallVM, modifier: Modifier = Modifier) {
    val model = remember(vm.id, vm.input, vm.output, vm.rawName, vm.kind) {
        presentToolBody(vm.rawName, vm.kind, vm.input, vm.output)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (model) {
            is ToolBodyModel.FileRead -> FileReadBody(model)
            is ToolBodyModel.FileEdit -> FileEditBody(model)
            is ToolBodyModel.SearchHits -> SearchHitsBody(model.query, model.items)
            is ToolBodyModel.DirList -> DirListBody(model)
            is ToolBodyModel.Terminal -> TerminalBody(model)
            is ToolBodyModel.Web -> WebBody(model)
            is ToolBodyModel.Todos -> TodoListBody(model.items)
            is ToolBodyModel.Facts -> FactsBody(model.fields)
            is ToolBodyModel.Prose -> if (model.text.isNotBlank()) MarkdownContent(model.text)
        }
    }
}

@Composable
private fun ToolBody(vm: ToolCallVM) {
    ToolExpandedBody(vm)
}

@Composable
private fun FileReadBody(model: ToolBodyModel.FileRead) {
    val colors = VibexTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.codeSurface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (model.path.isNotBlank()) {
            Text(model.path, fontSize = 11.sp, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val lines = remember(model.content) { model.content.split('\n') }
        var expanded by remember(model.content) { mutableStateOf(false) }
        val shown = if (!expanded && lines.size > 20) lines.take(20) else lines
        Column {
            shown.forEachIndexed { i, line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        (model.startLine + i).toString(),
                        fontFamily = PlexMono,
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(line.ifEmpty { " " }, fontFamily = PlexMono, fontSize = 12.sp, color = colors.textPrimary)
                }
            }
        }
        if (lines.size > 20) {
            Text(
                if (expanded) "收起" else "展开其余 ${lines.size - 20} 行",
                fontSize = 12.sp,
                color = colors.accent,
                modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun FileEditBody(model: ToolBodyModel.FileEdit) {
    val colors = VibexTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.codeSurface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (model.path.isNotBlank()) {
                Text(model.path, fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (model.additions > 0) Text("+${model.additions}", fontSize = 11.sp, color = SuccessGreen)
            if (model.deletions > 0) Text("-${model.deletions}", fontSize = 11.sp, color = colors.danger)
        }
        model.lines.take(80).forEach { line ->
            val (color, bg) = when (line.kind) {
                DiffLine.Kind.ADD -> SuccessGreen to SuccessGreen.copy(0.12f)
                DiffLine.Kind.DEL -> colors.danger to colors.danger.copy(0.10f)
                DiffLine.Kind.HUNK -> colors.accent to colors.accent.copy(0.08f)
                DiffLine.Kind.META -> colors.textTertiary to androidx.compose.ui.graphics.Color.Transparent
                DiffLine.Kind.CONTEXT -> colors.textPrimary to androidx.compose.ui.graphics.Color.Transparent
            }
            Text(
                line.text.ifEmpty { " " },
                fontFamily = PlexMono,
                fontSize = 12.sp,
                color = color,
                modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (model.lines.size > 80) {
            Text("其余 ${model.lines.size - 80} 行", fontSize = 11.sp, color = colors.textTertiary)
        }
    }
}

@Composable
private fun SearchHitsBody(query: String, items: List<ToolHit>) {
    val colors = VibexTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (query.isNotBlank()) Text(query, fontSize = 12.sp, color = colors.textSecondary)
        if (items.isEmpty()) {
            Text("没有匹配", fontSize = 12.sp, color = colors.textTertiary)
            return
        }
        items.take(40).forEach { hit ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.codeSurface).padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    when {
                        hit.url != null -> Icons.Rounded.Language
                        hit.directory -> Icons.Rounded.Folder
                        else -> Icons.Rounded.Description
                    },
                    null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val loc = buildString {
                        append(hit.url ?: hit.path.orEmpty())
                        if (!hit.line.isNullOrBlank()) append(":${hit.line}")
                    }
                    if (loc.isNotBlank()) Text(loc, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                    if (hit.text.isNotBlank() && hit.text != loc) Text(hit.text, fontSize = 12.sp, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun DirListBody(model: ToolBodyModel.DirList) {
    val colors = VibexTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.codeSurface).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (model.path.isNotBlank()) Text(model.path, fontSize = 11.sp, color = colors.textTertiary)
        if (model.entries.isEmpty()) {
            Text("空目录", fontSize = 12.sp, color = colors.textTertiary)
            return
        }
        model.entries.take(80).forEach { entry ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (entry.folder) Icons.Rounded.Folder else Icons.Rounded.Description,
                    null,
                    tint = if (entry.folder) colors.hold else colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(entry.name, fontSize = 13.sp, color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun TerminalBody(model: ToolBodyModel.Terminal) {
    val colors = VibexTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.codeSurface),
    ) {
        if (model.command.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$", fontFamily = PlexMono, fontSize = 12.sp, color = colors.accent)
                Text(model.command, fontFamily = PlexMono, fontSize = 12.sp, color = colors.textPrimary)
            }
        }
        if (model.output.isNotBlank()) {
            CodeBlock(code = model.output, language = "console", collapseAfter = 16)
        }
    }
}

@Composable
private fun WebBody(model: ToolBodyModel.Web) {
    val colors = VibexTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (model.url.isNotBlank()) Text(model.url, fontSize = 12.sp, color = colors.accent)
        if (model.snippet.isNotBlank()) MarkdownContent(model.snippet)
    }
}

@Composable
private fun TodoListBody(items: List<Pair<String, String>>) {
    val colors = VibexTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { (status, text) ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    when (status) {
                        "completed" -> Icons.Rounded.CheckCircle
                        "in_progress", "inprogress" -> Icons.Rounded.Adjust
                        else -> Icons.Rounded.RadioButtonUnchecked
                    },
                    null,
                    tint = when (status) {
                        "completed" -> SuccessGreen
                        "in_progress", "inprogress" -> colors.accent
                        else -> colors.textTertiary
                    },
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text,
                    fontSize = 12.sp,
                    color = if (status == "completed") colors.textTertiary else colors.textSecondary,
                    textDecoration = if (status == "completed") TextDecoration.LineThrough else null,
                )
            }
        }
    }
}

@Composable
private fun FactsBody(fields: List<Pair<String, String>>) {
    val colors = VibexTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        fields.forEach { (key, value) ->
            Column {
                Text(key, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.textTertiary)
                if (value.length > 80 || value.contains('\n')) {
                    CodeBlock(code = value, language = null, collapseAfter = 12)
                } else {
                    Text(value, fontSize = 12.sp, color = colors.textSecondary)
                }
            }
        }
    }
}

private fun groupSummary(items: List<ToolCallVM>): String {
    val counts = items.groupingBy { it.bucket }.eachCount()
    val order = listOf(
        ToolKindBucket.READ, ToolKindBucket.EDIT, ToolKindBucket.SEARCH, ToolKindBucket.LIST, ToolKindBucket.EXECUTE,
        ToolKindBucket.WEB, ToolKindBucket.TODO, ToolKindBucket.TASK, ToolKindBucket.OTHER,
    )
    val phrases = order.mapNotNull { b ->
        val c = counts[b] ?: return@mapNotNull null
        if (c == 0) return@mapNotNull null
        when (b) {
            ToolKindBucket.READ -> "读了 $c 个文件"
            ToolKindBucket.EDIT -> "编辑了 $c 个文件"
            ToolKindBucket.SEARCH -> "搜索 $c 次"
            ToolKindBucket.LIST -> "目录 $c 次"
            ToolKindBucket.EXECUTE -> "运行了 $c 条命令"
            ToolKindBucket.WEB -> "网页 $c 次"
            ToolKindBucket.TODO -> "任务"
            ToolKindBucket.TASK -> "委派 $c 次"
            ToolKindBucket.OTHER -> "$c 个工具"
        }
    }
    return phrases.joinToString(" · ").ifBlank { "${items.size} 个工具" }
}

private fun prettyJson(raw: String): String? = runCatching {
    when {
        raw.trimStart().startsWith("[") -> JSONArray(raw).toString(2)
        else -> JSONObject(raw).toString(2)
    }
}.getOrNull()

private fun JSONObject?.str(key: String): String? {
    if (this == null || !has(key) || isNull(key)) return null
    val v = optString(key)
    return v.takeIf { it.isNotEmpty() }
}

object ToolDerive {
    private val readNames = setOf("read", "read_file", "readfile", "read_text_file", "cat", "view")
    private val editNames = setOf(
        "edit", "str_replace", "str_replace_editor", "str_replace_based_edit_tool",
        "apply_patch", "applypatch", "patch", "multiedit", "edit_file", "update_file",
        "write", "write_file", "writefile", "create_file", "notebookedit", "notebook_edit",
        "write_to_file", "replace_in_file",
    )
    private val searchNames = setOf(
        "grep", "ripgrep", "grep_search", "search_files", "search", "searchtext", "search_text",
        "glob", "find", "file_search", "ls", "list_dir", "list_files", "list_code_definition_names",
    )
    private val executeNames = setOf(
        "bash", "shell", "sh", "exec", "exec_command", "execute", "execute_command",
        "run_command", "command", "run_terminal_cmd", "terminal", "write_stdin",
    )
    private val webNames = setOf(
        "webfetch", "web_fetch", "fetch", "websearch", "web_search", "browser", "browser_action",
    )
    private val todoNames = setOf("todowrite", "todo_write", "update_todo_list", "todos")
    private val taskNames = setOf(
        "task", "taskcreate", "taskupdate", "tasklist", "agent", "dispatch_agent", "new_task",
        "delegate_to_agent", "delegate_task", "subagent", "spawn_agent",
    )

    fun parseJson(s: String?): JSONObject? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        if (!t.startsWith("{")) return null
        return runCatching { JSONObject(t) }.getOrNull()
    }

    fun effectiveArgs(parsed: JSONObject?): JSONObject? {
        if (parsed == null) return null
        for (key in listOf("input", "arguments", "params", "payload")) {
            parsed.optJSONObject(key)?.let { return it }
        }
        return parsed
    }

    fun bucket(name: String, kind: String): ToolKindBucket {
        when (kind.lowercase()) {
            "read" -> return ToolKindBucket.READ
            "edit", "write", "delete", "move" -> return ToolKindBucket.EDIT
            "search" -> return ToolKindBucket.SEARCH
            "execute", "command", "bash", "shell", "terminal" -> return ToolKindBucket.EXECUTE
            "fetch", "web" -> return ToolKindBucket.WEB
        }
        val n = canonical(name)
        return when {
            n in readNames -> ToolKindBucket.READ
            n in todoNames -> ToolKindBucket.TODO
            n in taskNames -> ToolKindBucket.TASK
            n in editNames -> ToolKindBucket.EDIT
            n in searchNames -> ToolKindBucket.SEARCH
            n in executeNames -> ToolKindBucket.EXECUTE
            n in webNames -> ToolKindBucket.WEB
            else -> ToolKindBucket.OTHER
        }
    }

    fun title(name: String, parsed: JSONObject?): String {
        val n = canonical(name)
        val args = effectiveArgs(parsed)
        fun str(keys: List<String>): String? {
            if (args == null) return null
            for (k in keys) {
                val v = args.optString(k)
                if (v.isNotEmpty()) return v
            }
            return null
        }
        return when (n) {
            "read", "read_file", "readfile", "cat", "view" ->
                str(listOf("file_path", "path", "filename", "file", "target_file"))?.let { "Read ${shortPath(it)}" }
                    ?: display(name)
            "edit", "str_replace", "str_replace_editor", "str_replace_based_edit_tool",
            "apply_patch", "applypatch", "patch", "multiedit", "edit_file" ->
                str(listOf("file_path", "path", "filename", "file", "target_file"))?.let { "Edit ${shortPath(it)}" } ?: "Edit"
            "write", "write_file", "writefile", "create_file", "notebookedit", "notebook_edit" ->
                str(listOf("file_path", "path", "filename", "file", "target_file"))?.let { "Write ${shortPath(it)}" } ?: "Write"
            "bash", "shell", "exec", "exec_command", "execute", "run_command", "command", "run_terminal_cmd", "terminal" ->
                str(listOf("description")) ?: str(listOf("command", "cmd", "script"))?.let { truncate(it, 80) } ?: "Bash"
            "grep", "ripgrep", "grep_search", "search_files", "search" ->
                str(listOf("pattern", "query", "regex", "q"))?.let { "Grep ${truncate(it, 50)}" } ?: "Grep"
            "glob", "find", "file_search" ->
                str(listOf("pattern", "glob", "path", "query"))?.let { "Glob ${truncate(it, 50)}" } ?: "Glob"
            "ls", "list_dir", "list_files" ->
                str(listOf("path", "dir", "directory"))?.let { "List ${shortPath(it)}" } ?: "List"
            "webfetch", "web_fetch", "fetch" ->
                str(listOf("url"))?.let { "WebFetch ${truncate(it, 40)}" } ?: "WebFetch"
            "websearch", "web_search" ->
                str(listOf("query", "q"))?.let { "WebSearch: ${truncate(it, 50)}" } ?: "WebSearch"
            "todowrite", "todo_write", "update_todo_list", "todos" -> "Todos"
            else -> display(name)
        }
    }

    fun fields(input: String): List<Pair<String, String>> {
        val obj = effectiveArgs(parseJson(input)) ?: return emptyList()
        val skip = setOf("id", "tool_use_id", "tool_call_id", "type")
        val out = mutableListOf<Pair<String, String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.lowercase() in skip) continue
            val v = obj.opt(k) ?: continue
            if (v == JSONObject.NULL) continue
            out += k to when (v) {
                is JSONObject -> v.toString(2)
                is JSONArray -> v.toString(2)
                else -> v.toString()
            }
        }
        return out
    }

    private fun canonical(name: String): String {
        var n = name.lowercase()
        val idx = n.lastIndexOf("__")
        if (idx >= 0) n = n.substring(idx + 2)
        return n
    }

    private fun display(name: String): String {
        var n = name
        val idx = n.lastIndexOf("__")
        if (idx >= 0) n = n.substring(idx + 2)
        return n.ifBlank { "工具" }
    }

    private fun shortPath(path: String): String {
        val comps = path.trim().split("/").filter { it.isNotEmpty() }
        return if (comps.size <= 2) comps.joinToString("/") else comps.takeLast(2).joinToString("/")
    }

    private fun truncate(s: String, n: Int): String {
        val t = s.trim().substringBefore('\n')
        return if (t.length > n) t.take(n) + "…" else t
    }
}
