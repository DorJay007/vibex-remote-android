package dev.vibex.companion

enum class TimelineTone { Live, Hold, Quiet, Stop }

enum class PendingKind { Permission, Question, Blocked }

enum class NoticeSeverity { Error, Warning, Info }

data class TimelineRow(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val tone: TimelineTone,
    val thinking: String = "",
    val thinkingExpanded: Boolean = false,
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
    val planLines: MutableList<String> = mutableListOf()
    val rows: MutableList<TimelineRow> = mutableListOf()
    val queued: MutableList<QueuedInput> = mutableListOf()
    val sessionModes: MutableList<SessionMode> = mutableListOf()
    val sessionConfig: MutableList<SessionConfigOption> = mutableListOf()
    val notices: MutableList<SessionNotice> = mutableListOf()
    val availableCommands: MutableList<ComposerToken> = mutableListOf()
    val skillCommands: MutableList<ComposerToken> = mutableListOf()

    fun snapshot(): ConversationView = ConversationView(
        conversationId = conversationId,
        title = title.ifBlank { "会话" },
        agentId = agentId,
        workspaceId = workspaceId,
        rows = rows.toList(),
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
