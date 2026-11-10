package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ============================================================================
// MARK: - Kanban Board & Usage Dashboard Models (iOS BoardModels.swift Parity)
// ============================================================================

enum class KanbanSessionStatus(val rawValue: String, val title: String, val sortOrder: Int) {
    Todo("todo", "待开始", 0),
    InProgress("inprogress", "进行中", 1),
    InReview("inreview", "待检查", 2),
    Done("done", "已完成", 3),
    Archived("archived", "已归档", 4);

    companion object {
        val activeColumns = listOf(Todo, InProgress, InReview, Done)

        fun fromRaw(raw: String?): KanbanSessionStatus {
            return when (raw?.trim()?.lowercase()) {
                "inprogress", "in_progress", "running" -> InProgress
                "inreview", "in_review", "review" -> InReview
                "done", "completed", "complete" -> Done
                "archived", "archive" -> Archived
                else -> Todo
            }
        }
    }
}

enum class KanbanSortField(val label: String) {
    DefaultOrder("默认顺序"),
    Name("名称"),
    Time("时间"),
    Status("状态"),
    Agent("Agent"),
}

data class KanbanSessionCollaboration(
    val memberId: String? = null,
    val memberName: String? = null,
    val threadId: String? = null,
    val threadKind: String? = null,
    val threadName: String? = null,
)

data class KanbanSessionSummary(
    val id: String,
    val workspaceId: String,
    val taskId: String? = null,
    val name: String? = null,
    val displayName: String? = null,
    val status: KanbanSessionStatus = KanbanSessionStatus.Todo,
    val executor: String? = null,
    val agentId: String? = null,
    val workspaceName: String? = null,
    val workspaceBranch: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val firstPrompt: String? = null,
    val isRunning: Boolean = false,
    val pinnedAt: String? = null,
    val model: String? = null,
    val collaboration: KanbanSessionCollaboration? = null,
) {
    val isPinned: Boolean
        get() = !pinnedAt.isNullOrBlank()

    val isArchived: Boolean
        get() = status == KanbanSessionStatus.Archived

    val completesWhenViewed: Boolean
        get() = status == KanbanSessionStatus.InReview && !isRunning

    val resolvedTitle: String
        get() {
            val n = name?.trim()
            if (!n.isNullOrEmpty()) return n
            val p = firstPrompt?.replace(Regex("\\s+"), " ")?.trim()
            if (!p.isNullOrEmpty()) return p
            val d = displayName?.trim()
            if (!d.isNullOrEmpty()) return d
            return "新会话"
        }

    val effectiveAgentKey: String
        get() {
            val a = agentId?.trim()
            if (!a.isNullOrEmpty()) return a
            val e = executor?.trim()
            if (!e.isNullOrEmpty()) return e
            val m = model?.trim()
            if (!m.isNullOrEmpty()) return m
            return ""
        }

    val agentDisplayLabel: String
        get() = if (effectiveAgentKey.isBlank()) "未设置代理" else agentDisplayName(effectiveAgentKey)

    val workspaceDisplayLabel: String
        get() {
            val wn = workspaceName?.trim()
            if (!wn.isNullOrEmpty()) return wn
            val wb = workspaceBranch?.trim()
            if (!wb.isNullOrEmpty()) return wb
            return ""
        }

    val isForkSupported: Boolean
        get() = effectiveAgentKey.lowercase() in setOf(
            "claude_code", "claude-code", "codex", "deepseek_harness", "deepseek-harness",
        )

    val sessionInfoText: String
        get() {
            val ws = workspaceDisplayLabel.ifEmpty { "-" }
            return listOf(
                "会话名称: $resolvedTitle",
                "ID: $id",
                "Agent: $agentDisplayLabel",
                "工作区: $ws",
            ).joinToString("\n")
        }

    companion object {
        fun fromConversationSummary(item: ConversationSummary, branch: String? = null): KanbanSessionSummary =
            KanbanSessionSummary(
                id = item.id,
                workspaceId = item.workspaceId,
                name = item.title,
                displayName = item.title,
                status = KanbanSessionStatus.fromRaw(item.status),
                executor = item.agentId,
                agentId = item.agentId,
                workspaceBranch = branch,
                createdAt = item.updatedAt,
                updatedAt = item.updatedAt,
                isRunning = item.status.equals("running", ignoreCase = true),
                pinnedAt = if (item.pinned) item.updatedAt.ifBlank { "pinned" } else null,
            )
    }
}

enum class UsageDateRange(val rawValue: String, val label: String) {
    SevenDays("7d", "7天"),
    ThirtyDays("30d", "30天"),
    All("all", "全部"),
}

enum class UsageDashboardTab(val label: String) {
    Overview("概览"),
    Folders("目录"),
    Agents("Agent"),
    Models("模型"),
    Sessions("会话"),
    Plan("套餐"),
}

data class UsageTokenCounts(
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheWriteTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val totalTokens: Long = 0L,
) {
    val resolvedTotal: Long
        get() = if (totalTokens > 0L) totalTokens else inputTokens + outputTokens + cacheWriteTokens + cacheReadTokens
}

data class UsageSourcedTokens(
    val protocolTokens: UsageTokenCounts? = null,
    val vendorLog: UsageTokenCounts? = null,
    val sourcesDisagree: Boolean = false,
) {
    val preferred: UsageTokenCounts
        get() {
            if (vendorLog != null && vendorLog.resolvedTotal > 0L) return vendorLog
            if (protocolTokens != null && protocolTokens.resolvedTotal > 0L) return protocolTokens
            return vendorLog ?: protocolTokens ?: UsageTokenCounts()
        }
}

data class UsageDailyItem(
    val date: String,
    val sessions: Int,
    val tokens: UsageSourcedTokens,
    val cost: Double? = null,
    val modelsUsed: List<String> = emptyList(),
    val turns: Int? = null,
)

data class UsageModelItem(
    val model: String,
    val sessionCount: Int,
    val tokens: UsageSourcedTokens,
    val cost: Double? = null,
)

data class UsageFolderItem(
    val workspaceId: String,
    val folder: String? = null,
    val sessionCount: Int,
    val tokens: UsageSourcedTokens,
    val cost: Double? = null,
) {
    val displayName: String
        get() {
            if (!folder.isNullOrBlank()) {
                val last = folder.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
                if (last.isNotBlank()) return last
                return folder
            }
            return if (workspaceId.isBlank()) "默认工作区" else workspaceId.take(8)
        }
}

data class UsageAgentItem(
    val agentId: String,
    val sessionCount: Int,
    val tokens: UsageSourcedTokens,
    val cost: Double? = null,
) {
    val displayName: String
        get() = agentDisplayName(agentId)
}

data class UsageSessionItem(
    val sessionId: String,
    val workspaceId: String,
    val folder: String? = null,
    val agentId: String? = null,
    val timestamp: Long = 0L,
    val model: String? = null,
    val tokens: UsageSourcedTokens = UsageSourcedTokens(),
    val cost: Double? = null,
    val summary: String? = null,
    val turns: Int? = null,
) {
    val displayTitle: String
        get() = summary?.trim()?.takeIf { it.isNotEmpty() } ?: "会话 ${sessionId.take(8)}"
}

data class ProjectUsageStatistics(
    val scope: String = "global",
    val projectId: String = "",
    val projectName: String = "全局",
    val totalSessions: Int = 0,
    val totalTokens: UsageSourcedTokens = UsageSourcedTokens(),
    val estimatedCost: Double? = null,
    val vendorEstimatedCost: Double? = null,
    val sessions: List<UsageSessionItem> = emptyList(),
    val dailyUsage: List<UsageDailyItem> = emptyList(),
    val byModel: List<UsageModelItem> = emptyList(),
    val byFolder: List<UsageFolderItem> = emptyList(),
    val byAgent: List<UsageAgentItem> = emptyList(),
    val unattributedVendorSessions: Int = 0,
    val lastUpdated: Long = 0L,
) {
    val resolvedCost: Double?
        get() {
            if (estimatedCost != null) return estimatedCost
            if (vendorEstimatedCost != null) return vendorEstimatedCost
            val sessionSum = sessions.mapNotNull { it.cost }.sum()
            if (sessionSum > 0.0) return sessionSum
            val modelSum = byModel.mapNotNull { it.cost }.sum()
            return if (modelSum > 0.0) modelSum else null
        }

    val cacheHitRatePercent: Double?
        get() {
            val counts = totalTokens.preferred
            val cacheRead = counts.cacheReadTokens.toDouble()
            val denom = cacheRead + counts.inputTokens.toDouble() + counts.cacheWriteTokens.toDouble()
            return if (denom > 0.0) (cacheRead / denom) * 100.0 else null
        }

    val activeDaysCount: Int
        get() = dailyUsage.count { it.sessions > 0 || it.tokens.preferred.resolvedTotal > 0L }

    val avgTokensPerSession: Long
        get() = if (totalSessions > 0) totalTokens.preferred.resolvedTotal / totalSessions.toLong() else 0L
}

data class PlanUsageWindow(
    val id: String,
    val usedPercent: Double? = null,
    val windowMinutes: Long? = null,
    val resetsAtMs: Long? = null,
) {
    val clampedUsedPercent: Int?
        get() = usedPercent?.roundToInt()?.coerceIn(0, 100)

    val remainingPercent: Int?
        get() = clampedUsedPercent?.let { (100 - it).coerceAtLeast(0) }

    val localizedTitle: String
        get() = when (id) {
            "five_hour" -> "5 小时窗口"
            "seven_day" -> "每周窗口（全部模型）"
            "seven_day_opus" -> "每周窗口（Opus）"
            "seven_day_sonnet" -> "每周窗口（Sonnet）"
            "extra_usage" -> "额外用量（本月）"
            "primary" -> "主限额窗口"
            "secondary" -> "次限额窗口"
            "weekly" -> "每周窗口"
            "monthly" -> "每月窗口"
            "plan_period" -> "滚动窗口"
            "cursor_models" -> "Cursor 模型"
            "other_models" -> "其它模型"
            "gemini_five_hour" -> "Gemini 5 小时"
            "gemini_weekly" -> "Gemini 每周"
            "claude_gpt_five_hour" -> "Claude/GPT 5 小时"
            "claude_gpt_weekly" -> "Claude/GPT 每周"
            "argon_five_hour" -> "Gemini 4 Argon 5 小时"
            "argon_weekly" -> "Gemini 4 Argon 每周"
            else -> {
                val mins = windowMinutes ?: 0L
                when {
                    mins > 0L && mins % 1440L == 0L -> "${mins / 1440L} 天窗口"
                    mins > 0L && mins % 60L == 0L -> "${mins / 60L} 小时窗口"
                    else -> id
                }
            }
        }

    fun resetLabel(nowMs: Long = System.currentTimeMillis()): String? {
        val target = resetsAtMs ?: return null
        val diffMs = target - nowMs
        if (diffMs <= 60_000L) return "即将重置"
        val minutes = ceil(diffMs / 60_000.0).toInt()
        if (minutes < 60) return "${minutes} 分钟后重置"
        val hours = ceil(minutes / 60.0).toInt()
        if (hours < 24) return "${hours} 小时后重置"
        val days = ceil(hours / 24.0).toInt()
        return "${days} 天后重置"
    }
}

data class PlanCredits(
    val balance: String? = null,
    val unlimited: Boolean = false,
)

data class AgentPlanUsage(
    val planType: String? = null,
    val windows: List<PlanUsageWindow> = emptyList(),
    val credits: PlanCredits? = null,
)

sealed class PlanUsageResponse {
    data class Ok(val usage: AgentPlanUsage) : PlanUsageResponse()
    data class Unavailable(val reason: String) : PlanUsageResponse() {
        val unavailableDescription: String
            get() = when (reason.uppercase()) {
                "UNSUPPORTED_AGENT" -> "该 Agent 不提供套餐额度信息"
                "CLI_NOT_FOUND" -> "未找到已安装的程序，请先在设置中安装并登录"
                "NOT_LOGGED_IN" -> "未检测到登录凭据，请先在设置中登录"
                "TOKEN_EXPIRED" -> "登录凭据已过期，请在设置中重新登录"
                else -> reason
            }
    }
    data class Error(val message: String) : PlanUsageResponse()
}

// ============================================================================
// MARK: - Collaboration Space & Bot Avatar Models (iOS CollaborationModels.swift Parity)
// ============================================================================

enum class BotAvatarShape(val rawValue: String, val label: String) {
    Circle("circle", "圆形"),
    Cloud("cloud", "云朵"),
    Squircle("squircle", "超椭圆"),
    Sparkle("sparkle", "星芒"),
    Clover("clover", "四叶草"),
    Heart("heart", "爱心"),
    Pebble("pebble", "鹅卵石"),
    Droplet("droplet", "水滴"),
    Capsule("capsule", "胶囊"),
    Triangle("triangle", "三角形"),
    Pentagon("pentagon", "五边形"),
    Star("star", "星章"),
    Hexagon("hexagon", "六边形");

    companion object {
        fun fromRaw(raw: String?): BotAvatarShape? =
            entries.firstOrNull { it.rawValue.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class BotAvatarExpression(val rawValue: String) {
    Neutral("neutral"),
    Attentive("attentive"),
    Happy("happy"),
    Laughing("laughing"),
    Excited("excited"),
    Surprised("surprised"),
    Confused("confused"),
    Curious("curious"),
    Suspicious("suspicious"),
    Sleepy("sleepy"),
    Sad("sad"),
    Scared("scared"),
    Angry("angry"),
    Proud("proud"),
    Shy("shy"),
    Unimpressed("unimpressed");

    companion object {
        fun fromRaw(raw: String?): BotAvatarExpression? =
            entries.firstOrNull { it.rawValue.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class BotAvatarColorPreset(
    val id: String,
    val label: String,
    val hex: String,
)

data class CollabBotAvatarConfig(
    val color: String = "#1584ff",
    val shape: BotAvatarShape = BotAvatarShape.Circle,
    val expression: BotAvatarExpression = BotAvatarExpression.Neutral,
) {
    val jsonString: String
        get() = """{"color":"${normalizeHexColor(color)}","shape":"${shape.rawValue}","expression":"${expression.rawValue}"}"""

    companion object {
        val defaultMaster = CollabBotAvatarConfig("#000000", BotAvatarShape.Circle, BotAvatarExpression.Neutral)

        val colorPresets = listOf(
            BotAvatarColorPreset("ink", "墨黑", "#000000"),
            BotAvatarColorPreset("brown", "棕色", "#9a6433"),
            BotAvatarColorPreset("red", "红色", "#fa2d37"),
            BotAvatarColorPreset("orange", "橙色", "#ff6b0a"),
            BotAvatarColorPreset("amber", "琥珀", "#f99408"),
            BotAvatarColorPreset("green", "绿色", "#09b866"),
            BotAvatarColorPreset("turquoise", "青绿", "#0db49b"),
            BotAvatarColorPreset("blue", "蓝色", "#1584ff"),
            BotAvatarColorPreset("purple", "紫色", "#8b52ff"),
            BotAvatarColorPreset("pink", "粉色", "#ff2994"),
            BotAvatarColorPreset("grey", "灰色", "#8c8c8c"),
        )

        fun normalizeHexColor(value: String?, fallback: String = "#1584ff"): String {
            val trimmed = value?.trim().orEmpty()
            val raw = if (trimmed.startsWith("#")) trimmed.drop(1) else trimmed
            if (raw.length == 6 && raw.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                return "#${raw.lowercase()}"
            }
            if (raw.length == 3 && raw.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                val c = raw.lowercase()
                return "#${c[0]}${c[0]}${c[1]}${c[1]}${c[2]}${c[2]}"
            }
            return fallback
        }

        fun hashSeed(input: String): UInt {
            var hash = 2_166_136_261u
            for (ch in input) {
                hash = hash xor ch.code.toUInt()
                hash *= 16_777_619u
            }
            return hash
        }

        fun derive(seed: String): CollabBotAvatarConfig {
            val normalized = seed.trim().ifEmpty { "vibex-bot" }
            if (normalized.equals("master", ignoreCase = true)) return defaultMaster
            val h1 = hashSeed(normalized).toLong()
            val h2 = hashSeed("$normalized:shape").toLong()
            val shapes = BotAvatarShape.entries
            return CollabBotAvatarConfig(
                color = colorPresets[(h1 % colorPresets.size).toInt()].hex,
                shape = shapes[(h2 % shapes.size).toInt()],
                expression = BotAvatarExpression.Neutral,
            )
        }

        fun random(): CollabBotAvatarConfig {
            val color = colorPresets.random().hex
            val shape = BotAvatarShape.entries.random()
            return CollabBotAvatarConfig(color = color, shape = shape, expression = BotAvatarExpression.Neutral)
        }

        fun resolve(rawAvatar: String?, rawColor: String?, fallbackSeed: String): CollabBotAvatarConfig {
            val base = derive(fallbackSeed)
            var resolvedColor = if (!rawColor.isNullOrBlank()) normalizeHexColor(rawColor, base.color) else base.color
            var resolvedShape = base.shape
            var resolvedExpr = BotAvatarExpression.Neutral

            val trimmed = rawAvatar?.trim().orEmpty()
            if (trimmed.isNotEmpty()) {
                val directShape = BotAvatarShape.fromRaw(trimmed)
                if (directShape != null) {
                    resolvedShape = directShape
                } else if (trimmed.startsWith("{")) {
                    val obj = runCatching { JsonValue.parse(trimmed).asObject() }.getOrNull()
                    if (obj != null) {
                        val c = obj.textOrNull("color")
                        if (!c.isNullOrBlank()) resolvedColor = normalizeHexColor(c, resolvedColor)
                        BotAvatarShape.fromRaw(obj.textOrNull("shape"))?.let { resolvedShape = it }
                        BotAvatarExpression.fromRaw(obj.textOrNull("expression"))?.let { resolvedExpr = it }
                    }
                }
            }
            return CollabBotAvatarConfig(
                color = resolvedColor,
                shape = resolvedShape,
                expression = resolvedExpr,
            )
        }
    }
}

data class CollabSidebarMember(
    val memberId: String = "",
    val identityMemberId: String? = null,
    val name: String = "助手",
    val avatar: String? = null,
    val color: String? = null,
    val conversationId: String? = null,
    val agent: String? = null,
    val isSupervisor: Boolean = false,
) {
    val resolvedIdentityId: String
        get() = identityMemberId?.takeIf { it.isNotBlank() } ?: memberId

    val botAvatar: CollabBotAvatarConfig
        get() = CollabBotAvatarConfig.resolve(
            rawAvatar = avatar,
            rawColor = color,
            fallbackSeed = name.ifBlank { resolvedIdentityId },
        )
}

data class CollabLastMessage(
    val seq: Int? = null,
    val preview: String? = null,
    val origin: String? = null,
    val createdAt: String? = null,
)

data class CollabSidebarItem(
    val kind: String = "group",
    val threadId: String,
    val spaceId: String? = null,
    val name: String = "",
    val tagColor: String? = null,
    val lastMessage: CollabLastMessage? = null,
    val lastActivityAt: String? = null,
    val unreadCount: Int = 0,
    val membersPreview: List<CollabSidebarMember> = emptyList(),
    val memberCount: Int = 0,
    val member: CollabSidebarMember? = null,
    val groupId: String? = null,
) {
    val id: String get() = threadId
    val isGroup: Boolean get() = kind == "group"
    val isDirect: Boolean get() = kind == "dm"
    val targetSpaceId: String get() = if (isDirect) threadId else (spaceId?.takeIf { it.isNotBlank() } ?: threadId)

    val displayName: String
        get() {
            if (isDirect && !member?.name.isNullOrBlank()) return member!!.name
            return name.ifBlank { if (isGroup) "未命名群组" else "助手" }
        }
}

data class CollabContactGroup(
    val groupId: String,
    val workspaceId: String = "",
    val name: String,
    val sortOrder: Int = 0,
)

data class CollabSpaceSummary(
    val spaceId: String,
    val name: String,
    val leadMemberId: String? = null,
)

data class CollabSpaceMember(
    val memberId: String,
    val identityMemberId: String? = null,
    val nickname: String,
    val kind: String = "agent",
    val avatar: String? = null,
    val color: String? = null,
    val conversationId: String? = null,
    val isSupervisor: Boolean = false,
) {
    val id: String get() = memberId.ifBlank { conversationId ?: nickname }
    val isAgent: Boolean get() = kind.isBlank() || kind == "agent"
    val resolvedIdentityId: String get() = identityMemberId?.takeIf { it.isNotBlank() } ?: memberId
    val botAvatar: CollabBotAvatarConfig
        get() = CollabBotAvatarConfig.resolve(
            rawAvatar = avatar,
            rawColor = color,
            fallbackSeed = nickname.ifBlank { resolvedIdentityId },
        )
}

data class CollabDeliveryReceipt(
    val conversationId: String? = null,
    val nickname: String? = null,
    val delivered: String? = null,
    val state: String? = null,
    val turnId: String? = null,
) {
    val effectiveState: String
        get() = state?.takeIf { it.isNotBlank() } ?: delivered?.takeIf { it.isNotBlank() } ?: "queued"

    val stateLabel: String
        get() = when (effectiveState) {
            "queued", "dispatched" -> "排队中"
            "processing" -> "处理中"
            "replied" -> "已回复"
            "passed" -> "已略过"
            "timed_out" -> "超时"
            "cancelled" -> "已取消"
            "failed" -> "失败"
            "limit_reached" -> "达上限"
            else -> effectiveState
        }

    val isProcessing: Boolean
        get() = effectiveState == "processing" || effectiveState == "queued"
}

data class CollabReaction(
    val emoji: String,
    val actor: String,
)

data class GroupedReaction(
    val emoji: String,
    val count: Int,
    val byUser: Boolean,
)

data class CollabMessageEnvelope(
    val id: String? = null,
    val sender: String = "",
    val content: String = "",
    val summary: String? = null,
    val to: List<String> = emptyList(),
    val published: String? = null,
    val conversationId: String? = null,
    val reactions: List<CollabReaction> = emptyList(),
) {
    val isFromUser: Boolean
        get() {
            val lower = sender.trim().lowercase()
            return lower == "person" || lower == "user" || sender == "你" || sender == "用户"
        }

    val isSystem: Boolean
        get() = sender.trim().equals("system", ignoreCase = true)

    val displaySender: String
        get() = when {
            isFromUser -> "你"
            isSystem -> "系统"
            sender.isBlank() -> "助手"
            else -> sender
        }

    val recipientLabels: List<String>
        get() = to.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val lower = trimmed.lowercase()
            if (lower == "person" || lower == "user" || trimmed == "你" || trimmed == "用户") return@mapNotNull null
            trimmed.removePrefix("@")
        }
}

data class CollabSpaceMessage(
    val rawId: String? = null,
    val seq: Int = 0,
    val origin: String? = null,
    val inReplyTo: String? = null,
    val streaming: Boolean = false,
    val receipts: List<CollabDeliveryReceipt> = emptyList(),
    val envelope: CollabMessageEnvelope = CollabMessageEnvelope(),
) {
    val id: String
        get() {
            val candidate = (rawId ?: envelope.id.orEmpty()).replace("vibex://space-message/", "")
            return candidate.ifBlank { "seq-$seq" }
        }

    val isFromUser: Boolean
        get() = origin == "user" || envelope.isFromUser

    val groupedReactions: List<GroupedReaction>
        get() {
            val order = mutableListOf<String>()
            val counts = mutableMapOf<String, Int>()
            val byUser = mutableSetOf<String>()
            for (r in envelope.reactions) {
                if (counts[r.emoji] == null) order.add(r.emoji)
                counts[r.emoji] = (counts[r.emoji] ?: 0) + 1
                if (r.actor.equals("person", ignoreCase = true) || r.actor.equals("user", ignoreCase = true)) {
                    byUser.add(r.emoji)
                }
            }
            return order.map { GroupedReaction(it, counts[it] ?: 1, it in byUser) }
        }
}

data class CollabScheduledMessage(
    val id: String,
    val spaceId: String = "",
    val content: String = "",
    val deliverAt: String = "",
    val status: String? = null,
)

data class CollabCard(
    val id: String,
    val kind: String = "ask",
    val status: String = "pending",
    val action: String? = null,
    val command: String? = null,
    val reason: String? = null,
    val question: String? = null,
    val options: List<String> = emptyList(),
) {
    val isPending: Boolean get() = status == "pending"
}

data class CollabMessagesPage(
    val messages: List<CollabSpaceMessage> = emptyList(),
    val scheduled: List<CollabScheduledMessage> = emptyList(),
    val cards: List<CollabCard> = emptyList(),
    val conversationId: String? = null,
)

data class CollabMemoryFact(
    val id: Int,
    val content: String,
    val scope: String = "thread",
    val origin: String = "user",
) {
    val scopeLabel: String
        get() = when {
            scope == "global" -> "全局"
            scope == "private" -> "私聊"
            scope.startsWith("thread:") || scope == "thread" -> "本群"
            else -> scope
        }
}

data class CollabMemoryTopic(
    val topic: String,
    val title: String,
    val preview: String? = null,
    val content: String? = null,
)

data class CollabMemoryView(
    val facts: List<CollabMemoryFact> = emptyList(),
    val topics: List<CollabMemoryTopic> = emptyList(),
)

data class CollabCollectionItem(
    val id: String,
    val spaceId: String = "",
    val creatorNickname: String = "User",
    val kind: String = "text",
    val name: String = "未命名收藏",
    val note: String = "",
    val textContent: String? = null,
    val createdAt: String = "",
)

data class CollabMemberConfig(
    val agentId: String? = null,
    val modeId: String? = null,
    val personaPrompt: String? = null,
)

data class CollabTeam(
    val id: String,
    val name: String,
    val summary: String? = null,
)

// ============================================================================
// MARK: - HostApi Extensions for Kanban, Usage Dashboard & Collaboration
// ============================================================================

private fun JsonValue.Object.arrAny(vararg keys: String): List<JsonValue>? =
    keys.firstNotNullOfOrNull { arrOrNull(it) }

private fun JsonValue.Object.doubleAny(vararg keys: String): Double? =
    keys.firstNotNullOfOrNull { value[it]?.asNumber() }

private fun JsonValue.Object.longAny(vararg keys: String): Long? =
    keys.firstNotNullOfOrNull { longOrNull(it) }

private fun JsonValue.Object.boolAny(vararg keys: String): Boolean? =
    keys.firstNotNullOfOrNull { boolOrNull(it) }

private fun parseUsageTokenCounts(node: JsonValue?): UsageTokenCounts? {
    val obj = node?.asObject() ?: return null
    return UsageTokenCounts(
        inputTokens = obj.longAny("inputTokens", "input_tokens") ?: 0L,
        outputTokens = obj.longAny("outputTokens", "output_tokens") ?: 0L,
        cacheWriteTokens = obj.longAny("cacheWriteTokens", "cache_write_tokens") ?: 0L,
        cacheReadTokens = obj.longAny("cacheReadTokens", "cache_read_tokens") ?: 0L,
        totalTokens = obj.longAny("totalTokens", "total_tokens") ?: 0L,
    )
}

private fun parseUsageSourcedTokens(node: JsonValue?): UsageSourcedTokens {
    val obj = node?.asObject() ?: return UsageSourcedTokens()
    return UsageSourcedTokens(
        protocolTokens = parseUsageTokenCounts(obj.value["protocol"]),
        vendorLog = parseUsageTokenCounts(obj.value["vendorLog"] ?: obj.value["vendor_log"]),
        sourcesDisagree = obj.boolAny("sourcesDisagree", "sources_disagree") ?: false,
    )
}

fun HostApi.kanbanSessionSummaries(
    origin: HostOrigin,
    token: String,
    workspaceId: String,
): List<KanbanSessionSummary> {
    val data = call(origin, token, "get_session_summaries", mapOf("workspaceId" to workspaceId))
    val items = data.asArray()?.value ?: data.asObject()?.arrAny("sessions", "items").orEmpty()
    return items.mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val id = obj.textAny("id")
        if (id.isBlank()) return@mapNotNull null
        val collabObj = obj.objOrNull("collaboration")
        val collab = if (collabObj != null) {
            KanbanSessionCollaboration(
                memberId = collabObj.textAny("memberId", "member_id").ifBlank { null },
                memberName = collabObj.textAny("memberName", "member_name").ifBlank { null },
                threadId = collabObj.textAny("threadId", "thread_id").ifBlank { null },
                threadKind = collabObj.textAny("threadKind", "thread_kind").ifBlank { null },
                threadName = collabObj.textAny("threadName", "thread_name").ifBlank { null },
            )
        } else null
        val createdAt = obj.textAny("createdAt", "created_at")
        val updatedAt = obj.textAny("updatedAt", "updated_at").ifBlank { createdAt }
        KanbanSessionSummary(
            id = id,
            workspaceId = obj.textAny("workspaceId", "workspace_id").ifBlank { workspaceId },
            taskId = obj.textAny("taskId", "task_id").ifBlank { null },
            name = obj.textOrNull("name"),
            displayName = obj.textAny("displayName", "display_name").ifBlank { null },
            status = KanbanSessionStatus.fromRaw(obj.textOrNull("status")),
            executor = obj.textOrNull("executor"),
            agentId = obj.textAny("agentId", "agent_id").ifBlank { null },
            workspaceName = obj.textAny("workspaceName", "workspace_name").ifBlank { null },
            workspaceBranch = obj.textAny("workspaceBranch", "workspace_branch").ifBlank { null },
            createdAt = createdAt,
            updatedAt = updatedAt,
            firstPrompt = obj.textAny("firstPrompt", "first_prompt").ifBlank { null },
            isRunning = obj.boolAny("isRunning", "is_running") ?: false,
            pinnedAt = obj.textAny("pinnedAt", "pinned_at").ifBlank { null },
            model = obj.textOrNull("model")?.takeIf { it.isNotBlank() },
            collaboration = collab,
        )
    }
}

fun HostApi.kanbanAllSessions(
    origin: HostOrigin,
    token: String,
    workspaceIds: List<String>,
): List<KanbanSessionSummary> {
    val uniqueIds = workspaceIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val byId = linkedMapOf<String, KanbanSessionSummary>()
    for (wsId in uniqueIds) {
        val list = runCatching { kanbanSessionSummaries(origin, token, wsId) }.getOrNull().orEmpty()
        for (item in list) {
            byId[item.id] = item
        }
    }
    if (byId.isEmpty()) {
        val recent = runCatching { listRecent(origin, token, sinceDays = 30, projectId = null) }.getOrDefault(emptyList())
        val allowed = uniqueIds.map { it.lowercase() }.toSet()
        for (conv in recent) {
            if (allowed.isEmpty() || conv.workspaceId.lowercase() in allowed) {
                val summary = KanbanSessionSummary.fromConversationSummary(conv)
                byId[summary.id] = summary
            }
        }
    }
    return byId.values.sortedWith(
        compareByDescending<KanbanSessionSummary> { it.isPinned }
            .thenByDescending { it.updatedAt },
    )
}

fun HostApi.updateKanbanSessionStatus(
    origin: HostOrigin,
    token: String,
    sessionId: String,
    status: KanbanSessionStatus,
) {
    val ok = runCatching {
        call(origin, token, "update_session_status", mapOf("sessionId" to sessionId, "status" to status.rawValue))
    }.isSuccess
    if (!ok) {
        if (status == KanbanSessionStatus.Archived) {
            archiveConversation(origin, token, sessionId)
        } else {
            setConversationStatus(origin, token, sessionId, status.rawValue)
        }
    }
}

fun HostApi.renameKanbanSession(
    origin: HostOrigin,
    token: String,
    sessionId: String,
    name: String?,
) {
    val trimmed = name?.trim()?.takeIf { it.isNotEmpty() }
    val ok = runCatching {
        call(origin, token, "rename_session", mapOf("sessionId" to sessionId, "name" to trimmed))
    }.isSuccess
    if (!ok) {
        renameConversation(origin, token, sessionId, trimmed.orEmpty())
    }
}

fun HostApi.setKanbanSessionPinned(
    origin: HostOrigin,
    token: String,
    sessionId: String,
    pinned: Boolean,
) {
    val ok = runCatching {
        call(origin, token, "set_session_pinned", mapOf("sessionId" to sessionId, "pinned" to pinned))
    }.isSuccess
    if (!ok) {
        setConversationPinned(origin, token, sessionId, pinned)
    }
}

fun HostApi.markKanbanSessionViewed(origin: HostOrigin, token: String, sessionId: String) {
    runCatching {
        call(origin, token, "mark_session_viewed", mapOf("sessionId" to sessionId))
    }
}

fun HostApi.deleteKanbanSession(origin: HostOrigin, token: String, sessionId: String) {
    val ok = runCatching {
        call(origin, token, "delete_session", mapOf("sessionId" to sessionId))
    }.isSuccess
    if (!ok) {
        deleteConversation(origin, token, sessionId)
    }
}

fun HostApi.forkKanbanConversation(origin: HostOrigin, token: String, conversationId: String): String? {
    val res = call(origin, token, "conversation_fork", mapOf("conversationId" to conversationId)).asObject()
    return res?.textAny("conversationId", "conversation_id")?.takeIf { it.isNotBlank() }
}

fun HostApi.projectUsageStatistics(
    origin: HostOrigin,
    token: String,
    scope: String,
    projectId: String? = null,
    dateRange: UsageDateRange = UsageDateRange.SevenDays,
): ProjectUsageStatistics {
    val args = linkedMapOf<String, Any?>(
        "scope" to scope,
        "dateRange" to dateRange.rawValue,
        "projectId" to projectId?.takeIf { it.isNotBlank() },
    )
    val obj = call(origin, token, "get_project_usage_statistics", args).asObject()
        ?: return ProjectUsageStatistics(scope = scope, projectId = projectId.orEmpty())
    return ProjectUsageStatistics(
        scope = obj.textAny("scope").ifBlank { scope },
        projectId = obj.textAny("projectId", "project_id"),
        projectName = obj.textAny("projectName", "project_name").ifBlank { "全局" },
        totalSessions = (obj.longAny("totalSessions", "total_sessions") ?: 0L).toInt(),
        totalTokens = parseUsageSourcedTokens(obj.value["totalTokens"] ?: obj.value["total_tokens"]),
        estimatedCost = obj.doubleAny("estimatedCost", "estimated_cost"),
        vendorEstimatedCost = obj.doubleAny("vendorEstimatedCost", "vendor_estimated_cost"),
        sessions = obj.arrAny("sessions").orEmpty().mapNotNull { item ->
            val so = item.asObject() ?: return@mapNotNull null
            UsageSessionItem(
                sessionId = so.textAny("sessionId", "session_id").ifBlank { UUID.randomUUID().toString() },
                workspaceId = so.textAny("workspaceId", "workspace_id"),
                folder = so.textOrNull("folder")?.takeIf { it.isNotBlank() },
                agentId = so.textAny("agentId", "agent_id").ifBlank { null },
                timestamp = so.longAny("timestamp") ?: 0L,
                model = so.textOrNull("model")?.takeIf { it.isNotBlank() },
                tokens = parseUsageSourcedTokens(so.value["tokens"]),
                cost = so.doubleAny("cost"),
                summary = so.textOrNull("summary")?.takeIf { it.isNotBlank() },
                turns = so.longAny("turns")?.toInt(),
            )
        },
        dailyUsage = obj.arrAny("dailyUsage", "daily_usage").orEmpty().mapNotNull { item ->
            val d = item.asObject() ?: return@mapNotNull null
            UsageDailyItem(
                date = d.textAny("date"),
                sessions = (d.longAny("sessions") ?: 0L).toInt(),
                tokens = parseUsageSourcedTokens(d.value["tokens"]),
                cost = d.doubleAny("cost"),
                modelsUsed = d.arrAny("modelsUsed", "models_used").orEmpty().mapNotNull { it.asText() },
                turns = d.longAny("turns")?.toInt(),
            )
        },
        byModel = obj.arrAny("byModel", "by_model").orEmpty().mapNotNull { item ->
            val m = item.asObject() ?: return@mapNotNull null
            UsageModelItem(
                model = m.textAny("model").ifBlank { "未提供" },
                sessionCount = (m.longAny("sessionCount", "session_count") ?: 0L).toInt(),
                tokens = parseUsageSourcedTokens(m.value["tokens"]),
                cost = m.doubleAny("cost"),
            )
        },
        byFolder = obj.arrAny("byFolder", "by_folder").orEmpty().mapNotNull { item ->
            val f = item.asObject() ?: return@mapNotNull null
            UsageFolderItem(
                workspaceId = f.textAny("workspaceId", "workspace_id"),
                folder = f.textOrNull("folder")?.takeIf { it.isNotBlank() },
                sessionCount = (f.longAny("sessionCount", "session_count") ?: 0L).toInt(),
                tokens = parseUsageSourcedTokens(f.value["tokens"]),
                cost = f.doubleAny("cost"),
            )
        },
        byAgent = obj.arrAny("byAgent", "by_agent").orEmpty().mapNotNull { item ->
            val a = item.asObject() ?: return@mapNotNull null
            UsageAgentItem(
                agentId = a.textAny("agentId", "agent_id").ifBlank { "unprovided" },
                sessionCount = (a.longAny("sessionCount", "session_count") ?: 0L).toInt(),
                tokens = parseUsageSourcedTokens(a.value["tokens"]),
                cost = a.doubleAny("cost"),
            )
        },
        unattributedVendorSessions = (obj.longAny("unattributedVendorSessions", "unattributed_vendor_sessions") ?: 0L).toInt(),
        lastUpdated = obj.longAny("lastUpdated", "last_updated") ?: 0L,
    )
}

fun HostApi.agentPlanUsage(origin: HostOrigin, token: String, agentId: String): PlanUsageResponse {
    val obj = runCatching {
        call(origin, token, "agent_plan_usage", mapOf("agentId" to agentId)).asObject()
    }.getOrElse { err ->
        return PlanUsageResponse.Error(err.message ?: "读取失败")
    } ?: return PlanUsageResponse.Error("读取失败")

    return when (obj.textAny("type").uppercase()) {
        "OK" -> {
            val u = obj.objOrNull("usage") ?: return PlanUsageResponse.Ok(AgentPlanUsage())
            val windows = u.arrAny("windows").orEmpty().mapNotNull { w ->
                val wo = w.asObject() ?: return@mapNotNull null
                PlanUsageWindow(
                    id = wo.textAny("id").ifBlank { UUID.randomUUID().toString() },
                    usedPercent = wo.doubleAny("usedPercent", "used_percent"),
                    windowMinutes = wo.longAny("windowMinutes", "window_minutes"),
                    resetsAtMs = wo.longAny("resetsAtMs", "resets_at_ms"),
                )
            }
            val creditsObj = u.objOrNull("credits")
            val credits = creditsObj?.let {
                PlanCredits(
                    balance = it.textOrNull("balance"),
                    unlimited = it.boolAny("unlimited") ?: false,
                )
            }
            PlanUsageResponse.Ok(
                AgentPlanUsage(
                    planType = u.textAny("planType", "plan_type").ifBlank { null },
                    windows = windows,
                    credits = credits,
                ),
            )
        }
        "UNAVAILABLE" -> PlanUsageResponse.Unavailable(obj.textAny("reason").ifBlank { "UNSUPPORTED_AGENT" })
        else -> PlanUsageResponse.Error(obj.textAny("message").ifBlank { "读取失败" })
    }
}

private fun parseSidebarMember(obj: JsonValue.Object?): CollabSidebarMember? {
    if (obj == null) return null
    val rawAvatarNode = obj.value["avatar"]
    val avatarStr = when (rawAvatarNode) {
        is JsonValue.Text -> rawAvatarNode.value
        is JsonValue.Object -> rawAvatarNode.encode()
        else -> null
    }
    return CollabSidebarMember(
        memberId = obj.textAny("member_id", "memberId", "id"),
        identityMemberId = obj.textAny("identity_member_id", "identityMemberId").ifBlank { null },
        name = obj.textAny("name", "nickname", "displayName").ifBlank { "助手" },
        avatar = avatarStr,
        color = obj.textOrNull("color")?.takeIf { it.isNotBlank() },
        conversationId = obj.textAny("conversation_id", "conversationId").ifBlank { null },
        agent = obj.textAny("agent", "agent_id", "agentId").ifBlank { null },
        isSupervisor = obj.boolAny("is_supervisor", "isSupervisor") ?: false,
    )
}

fun HostApi.collabSidebar(origin: HostOrigin, token: String, workspaceId: String): List<CollabSidebarItem> {
    if (workspaceId.isBlank()) return emptyList()
    val root = collaboration(origin, token, "thread.listSidebar", mapOf("workspaceId" to workspaceId))
    val arr = root.asArray()?.value ?: root.asObject()?.arrAny("items", "threads").orEmpty()
    return arr.mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val threadId = obj.textAny("thread_id", "threadId", "id")
        if (threadId.isBlank()) return@mapNotNull null
        val lastMsgObj = obj.objOrNull("last_message") ?: obj.objOrNull("lastMessage")
        val lastMessage = lastMsgObj?.let {
            CollabLastMessage(
                seq = it.longAny("seq")?.toInt(),
                preview = it.textOrNull("preview"),
                origin = it.textOrNull("origin"),
                createdAt = it.textAny("created_at", "createdAt").ifBlank { null },
            )
        }
        val previewMembers = (obj.arrAny("members_preview", "membersPreview").orEmpty())
            .mapNotNull { parseSidebarMember(it.asObject()) }
        CollabSidebarItem(
            kind = obj.textAny("kind").ifBlank { if (threadId.startsWith("direct:")) "dm" else "group" },
            threadId = threadId,
            spaceId = obj.textAny("space_id", "spaceId").ifBlank { null },
            name = obj.textAny("name", "title"),
            tagColor = obj.textAny("tag_color", "tagColor").ifBlank { null },
            lastMessage = lastMessage,
            lastActivityAt = obj.textAny("last_activity_at", "lastActivityAt").ifBlank { null },
            unreadCount = (obj.longAny("unread_count", "unreadCount") ?: 0L).toInt(),
            membersPreview = previewMembers,
            memberCount = (obj.longAny("member_count", "memberCount") ?: previewMembers.size.toLong()).toInt(),
            member = parseSidebarMember(obj.objOrNull("member")),
            groupId = obj.textAny("group_id", "groupId").ifBlank { null },
        )
    }
}

fun HostApi.collabContactGroups(origin: HostOrigin, token: String, workspaceId: String): List<CollabContactGroup> {
    if (workspaceId.isBlank()) return emptyList()
    val root = runCatching {
        collaboration(origin, token, "contactGroup.list", mapOf("workspaceId" to workspaceId))
    }.getOrNull() ?: return emptyList()
    val arr = root.asArray()?.value ?: root.asObject()?.arrAny("groups", "items").orEmpty()
    return arr.mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val gid = obj.textAny("group_id", "groupId", "id")
        if (gid.isBlank()) return@mapNotNull null
        CollabContactGroup(
            groupId = gid,
            workspaceId = obj.textAny("workspace_id", "workspaceId").ifBlank { workspaceId },
            name = obj.textAny("name"),
            sortOrder = (obj.longAny("sort_order", "sortOrder") ?: 0L).toInt(),
        )
    }.sortedBy { it.sortOrder }
}

fun HostApi.collabSpaces(origin: HostOrigin, token: String, workspaceId: String): List<CollabSpaceSummary> {
    if (workspaceId.isBlank()) return emptyList()
    val root = runCatching {
        collaboration(origin, token, "space.list", mapOf("workspaceId" to workspaceId))
    }.getOrNull() ?: return emptyList()
    val arr = root.asArray()?.value ?: root.asObject()?.arrAny("spaces", "items").orEmpty()
    return arr.mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val sid = obj.textAny("space_id", "spaceId", "id")
        if (sid.isBlank()) return@mapNotNull null
        CollabSpaceSummary(
            spaceId = sid,
            name = obj.textAny("name"),
            leadMemberId = obj.textAny("lead_member_id", "leadMemberId").ifBlank { null },
        )
    }
}

fun HostApi.collabSpaceMembers(origin: HostOrigin, token: String, spaceId: String): List<CollabSpaceMember> {
    if (spaceId.isBlank()) return emptyList()
    val root = runCatching {
        collaboration(origin, token, "space.listMembers", mapOf("spaceId" to spaceId))
    }.getOrNull() ?: return emptyList()
    val arr = root.asArray()?.value ?: root.asObject()?.arrAny("members", "items").orEmpty()
    return arr.mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val rawAvatarNode = obj.value["avatar"]
        val avatarStr = when (rawAvatarNode) {
            is JsonValue.Text -> rawAvatarNode.value
            is JsonValue.Object -> rawAvatarNode.encode()
            else -> null
        }
        CollabSpaceMember(
            memberId = obj.textAny("member_id", "memberId", "id"),
            identityMemberId = obj.textAny("identity_member_id", "identityMemberId").ifBlank { null },
            nickname = obj.textAny("nickname", "name").ifBlank { "助手" },
            kind = obj.textAny("kind").ifBlank { "agent" },
            avatar = avatarStr,
            color = obj.textOrNull("color")?.takeIf { it.isNotBlank() },
            conversationId = obj.textAny("conversation_id", "conversationId").ifBlank { null },
            isSupervisor = obj.boolAny("is_supervisor", "isSupervisor") ?: false,
        )
    }
}

fun HostApi.collabMessagesPage(origin: HostOrigin, token: String, spaceId: String, limit: Int = 200): CollabMessagesPage {
    val root = collaboration(
        origin,
        token,
        "space.listMessages",
        mapOf("spaceId" to spaceId, "latest" to true, "limit" to limit.toDouble()),
    ).asObject() ?: return CollabMessagesPage()

    val messages = root.arrAny("messages", "items").orEmpty().mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val envObj = obj.objOrNull("envelope") ?: obj
        val reactions = envObj.arrAny("reactions").orEmpty().mapNotNull { r ->
            val ro = r.asObject() ?: return@mapNotNull null
            val emoji = ro.textAny("emoji")
            if (emoji.isBlank()) null else CollabReaction(emoji = emoji, actor = ro.textAny("actor"))
        }
        val envelope = CollabMessageEnvelope(
            id = envObj.textOrNull("id"),
            sender = envObj.textAny("sender", "senderName", "sender_name"),
            content = envObj.textAny("content", "text", "body"),
            summary = envObj.textOrNull("summary"),
            to = envObj.arrAny("to").orEmpty().mapNotNull { it.asText() },
            published = envObj.textAny("published", "createdAt", "created_at").ifBlank { null },
            conversationId = envObj.textAny("conversation_id", "conversationId").ifBlank { null },
            reactions = reactions,
        )
        val receipts = obj.arrAny("receipts", "deliveries").orEmpty().mapNotNull { r ->
            val ro = r.asObject() ?: return@mapNotNull null
            CollabDeliveryReceipt(
                conversationId = ro.textAny("conversation_id", "conversationId").ifBlank { null },
                nickname = ro.textAny("nickname", "memberName", "member_name").ifBlank { null },
                delivered = ro.textOrNull("delivered"),
                state = ro.textAny("state", "status").ifBlank { null },
                turnId = ro.textAny("turn_id", "turnId").ifBlank { null },
            )
        }
        CollabSpaceMessage(
            rawId = obj.textOrNull("id"),
            seq = (obj.longAny("seq", "sequence") ?: 0L).toInt(),
            origin = obj.textOrNull("origin"),
            inReplyTo = obj.textAny("in_reply_to", "inReplyTo").ifBlank { null },
            streaming = obj.boolAny("streaming") ?: false,
            receipts = receipts,
            envelope = envelope,
        )
    }

    val scheduled = root.arrAny("scheduled").orEmpty().mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        CollabScheduledMessage(
            id = obj.textAny("id", "delayed_input_id").ifBlank { UUID.randomUUID().toString() },
            spaceId = obj.textAny("space_id", "spaceId").ifBlank { spaceId },
            content = obj.textAny("content"),
            deliverAt = obj.textAny("deliver_at", "deliverAt"),
            status = obj.textOrNull("status"),
        )
    }

    val cards = root.arrAny("cards").orEmpty().mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val payload = obj.objOrNull("payload")
        CollabCard(
            id = obj.textAny("id"),
            kind = obj.textAny("kind").ifBlank { "ask" },
            status = obj.textAny("status").ifBlank { "pending" },
            action = payload?.textOrNull("action"),
            command = payload?.textOrNull("command"),
            reason = payload?.textOrNull("reason"),
            question = payload?.textOrNull("question"),
            options = payload?.arrAny("options").orEmpty().mapNotNull { it.asText() },
        )
    }

    return CollabMessagesPage(
        messages = messages,
        scheduled = scheduled,
        cards = cards,
        conversationId = root.textAny("conversationId", "conversation_id").ifBlank { null },
    )
}

fun HostApi.collabPostMessage(
    origin: HostOrigin,
    token: String,
    spaceId: String,
    content: String,
    to: List<String> = emptyList(),
    inReplyTo: String? = null,
    delaySeconds: Int? = null,
) {
    val input = linkedMapOf<String, Any?>(
        "spaceId" to spaceId,
        "content" to content,
        "operationId" to UUID.randomUUID().toString().lowercase(),
    )
    if (to.isNotEmpty()) input["to"] = to
    if (!inReplyTo.isNullOrBlank()) input["inReplyTo"] = inReplyTo
    if (delaySeconds != null && delaySeconds > 0) {
        input["delaySeconds"] = delaySeconds
        collaboration(origin, token, "space.post", input)
    } else {
        collaboration(origin, token, "space.submit", input)
    }
}

fun HostApi.collabReact(origin: HostOrigin, token: String, spaceId: String, messageId: String, emoji: String) {
    collaboration(
        origin,
        token,
        "space.react",
        mapOf("spaceId" to spaceId, "messageId" to messageId, "emoji" to emoji),
    )
}

fun HostApi.collabMarkRead(origin: HostOrigin, token: String, threadId: String, seq: Int? = null) {
    val input = linkedMapOf<String, Any?>()
    if (threadId.startsWith("direct:")) {
        input["threadId"] = threadId
    } else {
        input["spaceId"] = threadId
    }
    if (seq != null) input["seq"] = seq
    collaboration(origin, token, "thread.markRead", input)
}

fun HostApi.collabMarkUnread(origin: HostOrigin, token: String, threadId: String) {
    val input = if (threadId.startsWith("direct:")) mapOf("threadId" to threadId) else mapOf("spaceId" to threadId)
    collaboration(origin, token, "thread.markUnread", input)
}

fun HostApi.collabCreateMember(
    origin: HostOrigin,
    token: String,
    workspaceId: String,
    name: String,
    agentId: String? = null,
    roleId: String? = null,
    personaPrompt: String? = null,
    avatar: CollabBotAvatarConfig? = null,
    color: String? = null,
    isSupervisor: Boolean = false,
): String? {
    val input = linkedMapOf<String, Any?>(
        "workspaceId" to workspaceId,
        "nickname" to name,
        "name" to name,
        "operationId" to UUID.randomUUID().toString().lowercase(),
        "isSupervisor" to isSupervisor,
    )
    if (!agentId.isNullOrBlank()) input["agentId"] = agentId
    if (!roleId.isNullOrBlank()) input["roleId"] = roleId
    if (!personaPrompt.isNullOrBlank()) input["personaPrompt"] = personaPrompt
    if (avatar != null) {
        input["avatar"] = avatar.jsonString
        input["color"] = avatar.color
    } else if (!color.isNullOrBlank()) {
        input["color"] = color
    }
    val res = collaboration(origin, token, "space.createMember", input).asObject()
    return res?.textAny("conversation_id", "conversationId")?.takeIf { it.isNotBlank() }
}

fun HostApi.collabAddMemberToSpace(
    origin: HostOrigin,
    token: String,
    spaceId: String,
    conversationId: String,
    nickname: String,
) {
    collaboration(
        origin,
        token,
        "space.addMember",
        mapOf(
            "spaceId" to spaceId,
            "conversationId" to conversationId,
            "nickname" to nickname,
            "operationId" to UUID.randomUUID().toString().lowercase(),
        ),
    )
}

fun HostApi.collabResolveSession(origin: HostOrigin, token: String, memberId: String, threadId: String): String? {
    val res = runCatching {
        collaboration(origin, token, "member.session.resolve", mapOf("memberId" to memberId, "threadId" to threadId))
    }.getOrNull()?.asObject()
    return res?.textAny("conversationId", "conversation_id")?.takeIf { it.isNotBlank() }
}

fun HostApi.collabResolveCard(
    origin: HostOrigin,
    token: String,
    cardId: String,
    decision: String? = null,
    answer: String? = null,
    declined: Boolean = false,
) {
    val input = linkedMapOf<String, Any?>(
        "cardId" to cardId,
        "declined" to declined,
    )
    if (decision != null) input["decision"] = decision
    if (answer != null) input["answer"] = answer
    collaboration(origin, token, "card.resolve", input)
}

fun HostApi.collabCancelSpaceTurn(origin: HostOrigin, token: String, spaceId: String, memberId: String) {
    collaboration(origin, token, "space.cancelTurn", mapOf("spaceId" to spaceId, "memberId" to memberId))
}

fun HostApi.collabMemoryView(origin: HostOrigin, token: String, spaceId: String, memberId: String? = null): CollabMemoryView {
    val input = linkedMapOf<String, Any?>("spaceId" to spaceId)
    if (!memberId.isNullOrBlank()) input["memberId"] = memberId
    val obj = runCatching {
        collaboration(origin, token, "space.memory.view", input).asObject()
    }.getOrNull() ?: return CollabMemoryView()
    val facts = obj.arrAny("facts").orEmpty().mapNotNull { item ->
        val fo = item.asObject() ?: return@mapNotNull null
        CollabMemoryFact(
            id = (fo.longAny("id") ?: 0L).toInt(),
            content = fo.textAny("content"),
            scope = fo.textAny("scope").ifBlank { "thread" },
            origin = fo.textAny("origin").ifBlank { "user" },
        )
    }
    val topics = obj.arrAny("topics").orEmpty().mapNotNull { item ->
        val to = item.asObject() ?: return@mapNotNull null
        val slug = to.textAny("topic")
        if (slug.isBlank()) return@mapNotNull null
        CollabMemoryTopic(
            topic = slug,
            title = to.textAny("title").ifBlank { slug },
            preview = to.textAny("preview", "summary").ifBlank { null },
            content = to.textOrNull("content"),
        )
    }
    return CollabMemoryView(facts = facts, topics = topics)
}

fun HostApi.collabCollectionItems(origin: HostOrigin, token: String, spaceId: String): List<CollabCollectionItem> {
    val obj = runCatching {
        collaboration(origin, token, "space.listCollection", mapOf("spaceId" to spaceId)).asObject()
    }.getOrNull() ?: return emptyList()
    return obj.arrAny("items").orEmpty().mapNotNull { item ->
        val co = item.asObject() ?: return@mapNotNull null
        val id = co.textAny("id")
        if (id.isBlank()) return@mapNotNull null
        CollabCollectionItem(
            id = id,
            spaceId = co.textAny("space_id", "spaceId").ifBlank { spaceId },
            creatorNickname = co.textAny("creator_nickname", "creatorNickname").ifBlank { "User" },
            kind = co.textAny("item_kind", "kind").ifBlank { "text" },
            name = co.textAny("name", "file_name").ifBlank { "未命名收藏" },
            note = co.textAny("note"),
            textContent = co.textAny("text_content", "textContent").ifBlank { null },
            createdAt = co.textAny("created_at", "createdAt"),
        )
    }
}

fun HostApi.collabMemberConfig(origin: HostOrigin, token: String, memberId: String): CollabMemberConfig? {
    val obj = runCatching {
        collaboration(origin, token, "member.config.get", mapOf("memberId" to memberId)).asObject()
    }.getOrNull() ?: return null
    val cfg = obj.objOrNull("config") ?: obj
    return CollabMemberConfig(
        agentId = cfg.textAny("agent_id", "agentId").ifBlank { null },
        modeId = cfg.textAny("mode_id", "modeId").ifBlank { null },
        personaPrompt = cfg.textAny("persona_prompt", "personaPrompt").ifBlank { null },
    )
}

fun HostApi.collabTeamsList(origin: HostOrigin, token: String, workspaceId: String = ""): List<CollabTeam> {
    val input = if (workspaceId.isNotBlank()) mapOf("workspaceId" to workspaceId) else emptyMap()
    val obj = runCatching { collaboration(origin, token, "team.list", input) }.getOrNull() ?: return emptyList()
    val arr = obj.asArray()?.value ?: obj.asObject()?.arrAny("teams", "items").orEmpty()
    return arr.mapNotNull { item ->
        val to = item.asObject() ?: return@mapNotNull null
        val id = to.textAny("id", "teamId")
        val name = to.textAny("name", "title")
        if (id.isBlank() || name.isBlank()) return@mapNotNull null
        CollabTeam(
            id = id,
            name = name,
            summary = to.textOrNull("summary")?.takeIf { it.isNotBlank() },
        )
    }
}
