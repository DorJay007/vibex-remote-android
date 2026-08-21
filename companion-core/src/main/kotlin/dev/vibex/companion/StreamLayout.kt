package dev.vibex.companion

data class StreamLayoutOptions(
    val thinkingHidden: Boolean = true,
    val showFailedTools: Boolean = false,
    val foldProcess: Boolean = true,
    val inFlight: Boolean = false,
)

sealed class StreamNode {
    abstract val id: String

    data class Fold(val rows: List<TimelineRow>) : StreamNode() {
        val count: Int get() = rows.size
        override val id: String = "fold:${rows.first().id}"
    }

    data class User(val row: TimelineRow) : StreamNode() {
        override val id: String get() = row.id
    }

    data class Assistant(val row: TimelineRow) : StreamNode() {
        override val id: String get() = row.id
    }

    data class Reasoning(val row: TimelineRow) : StreamNode() {
        override val id: String get() = row.id
    }

    data class Tools(val rows: List<TimelineRow>) : StreamNode() {
        override val id: String get() = "tools:${rows.first().id}"
    }

    data class Error(val row: TimelineRow) : StreamNode() {
        override val id: String get() = row.id
    }

    data class System(val row: TimelineRow) : StreamNode() {
        override val id: String get() = row.id
    }

    data class Waiting(val compact: Boolean = false) : StreamNode() {
        override val id: String = "waiting"
    }
}

fun layoutStream(
    rows: List<TimelineRow>,
    options: StreamLayoutOptions,
): List<StreamNode> {
    val visible = rows.filter { row ->
        if (row.pendingKind != null) return@filter false
        if (options.thinkingHidden && row.isReasoning()) return@filter false
        if (!options.showFailedTools && row.isFailedTool()) return@filter false
        true
    }
    val nodes = if (!options.foldProcess) groupVisible(visible) else foldTurns(visible)
    return appendWaiting(nodes, options.inFlight)
}

private fun appendWaiting(nodes: List<StreamNode>, inFlight: Boolean): List<StreamNode> {
    if (!inFlight) return nodes
    val lastUser = nodes.indexOfLast { it is StreamNode.User }
    val tail = if (lastUser >= 0) nodes.subList(lastUser + 1, nodes.size) else nodes
    if (tail.any { it is StreamNode.Error || it is StreamNode.Waiting }) return nodes
    val compact = tail.any {
        it is StreamNode.Assistant || it is StreamNode.Tools || it is StreamNode.Reasoning || it is StreamNode.Fold
    }
    return nodes + StreamNode.Waiting(compact)
}

internal fun TimelineRow.isUserMessage(): Boolean =
    kind == "user" || kind.startsWith("user_")

internal fun TimelineRow.isAssistantMessage(): Boolean = kind == "assistant"

internal fun TimelineRow.isReasoning(): Boolean =
    kind == "reasoning" || kind == "thinking"

internal fun TimelineRow.isTool(): Boolean = kind == "tool"

internal fun TimelineRow.isProcess(): Boolean = isTool() || isReasoning()

internal fun TimelineRow.isFailedTool(): Boolean {
    if (!isTool()) return false
    val status = toolStatus.lowercase()
    return tone == TimelineTone.Stop ||
        status.contains("fail") ||
        status.contains("error")
}

internal fun TimelineRow.isRunningTool(): Boolean =
    isTool() && tone == TimelineTone.Live &&
        !toolStatus.contains("complet", true) &&
        !toolStatus.contains("success", true)

private fun foldTurns(rows: List<TimelineRow>): List<StreamNode> {
    val out = mutableListOf<StreamNode>()
    var i = 0
    while (i < rows.size) {
        if (rows[i].isUserMessage()) {
            out += StreamNode.User(rows[i])
            i++
            continue
        }
        var j = i
        while (j < rows.size && !rows[j].isUserMessage()) j++
        out += layoutAgentSegment(rows.subList(i, j))
        i = j
    }
    return out
}

/**
 * Desktop MessageTurnView: keep the last assistant markdown, fold everything
 * before it in the same turn (tools, thinking, earlier assistant fragments).
 */
private fun layoutAgentSegment(rows: List<TimelineRow>): List<StreamNode> {
    if (rows.isEmpty()) return emptyList()
    val lastAssistant = rows.indexOfLast { it.isAssistantMessage() }
    if (lastAssistant < 0) return groupVisible(rows)
    val prelude = rows.subList(0, lastAssistant)
    val rest = rows.subList(lastAssistant + 1, rows.size)
    val out = mutableListOf<StreamNode>()
    if (prelude.isNotEmpty()) out += StreamNode.Fold(prelude.toList())
    out += StreamNode.Assistant(rows[lastAssistant])
    if (rest.isNotEmpty()) out += groupVisible(rest)
    return out
}

internal fun groupVisible(rows: List<TimelineRow>): List<StreamNode> {
    val out = mutableListOf<StreamNode>()
    var i = 0
    while (i < rows.size) {
        val row = rows[i]
        when {
            row.isUserMessage() -> {
                out += StreamNode.User(row)
                i++
            }
            row.isAssistantMessage() -> {
                out += StreamNode.Assistant(row)
                i++
            }
            row.isReasoning() -> {
                out += StreamNode.Reasoning(row)
                i++
            }
            row.isTool() -> {
                var j = i
                while (j < rows.size && rows[j].isTool()) j++
                out += StreamNode.Tools(rows.subList(i, j).toList())
                i = j
            }
            row.tone == TimelineTone.Stop ||
                row.kind.contains("fail") ||
                row.kind.contains("interrupt") -> {
                out += StreamNode.Error(row)
                i++
            }
            else -> {
                out += StreamNode.System(row)
                i++
            }
        }
    }
    return out
}
