package dev.vibex.companion

fun agentAvailabilityLabel(lifecycle: String, authentication: String = ""): String? {
    val auth = authentication.lowercase().replace('-', '_')
    if (auth == "not_logged_in") return "未登录"
    if (auth == "multiple_unknown") return "状态未知"
    return when (lifecycle.lowercase().replace('-', '_')) {
        "" -> null
        "ready" -> "可用"
        "needs_auth" -> "未登录"
        "needs_repair" -> "需修复"
        "uninstalled" -> "未安装"
        "needs_config" -> "需配置"
        "queued", "installing", "updating", "repairing" -> "处理中"
        "platform_unsupported" -> "不支持"
        "retired" -> "已停用"
        else -> null
    }
}

fun agentIsUsable(ready: Boolean, lifecycle: String = "", authentication: String = ""): Boolean {
    if (!ready) return false
    val auth = authentication.lowercase().replace('-', '_')
    if (auth == "not_logged_in" || auth == "multiple_unknown") return false
    return when (lifecycle.lowercase().replace('-', '_')) {
        "", "ready" -> true
        else -> false
    }
}

fun configOptionCurrentLabel(option: SessionConfigOption, selected: String = option.value): String {
    val value = selected.ifBlank { option.value }
    return option.choices.find { it.value == value }?.label
        ?: value.ifBlank { option.choices.firstOrNull()?.label.orEmpty() }
}

fun applyCreateTimeSessionConfig(
    session: TimelineSession,
    modes: List<SessionMode>,
    currentModeId: String,
    options: List<SessionConfigOption>,
    selected: Map<String, String> = emptyMap(),
) {
    if (modes.isNotEmpty()) {
        session.sessionModes.clear()
        session.sessionModes += modes
    }
    if (currentModeId.isNotBlank()) session.currentModeId = currentModeId
    if (options.isEmpty()) return
    session.sessionConfig.clear()
    session.sessionConfig += options.map { option ->
        option.copy(value = selected[option.key].orEmpty().ifBlank { option.value })
    }
}

fun compactSessionConfigSummary(
    modes: List<SessionMode>,
    currentModeId: String,
    options: List<SessionConfigOption>,
): String {
    val parts = mutableListOf<String>()
    val mode = modes.find { it.id == currentModeId }?.name ?: currentModeId
    if (mode.isNotBlank() && !isGenericMode(mode)) parts += mode
    options.sortedBy { optionPriority(it) }.forEach { option ->
        if (isModeOption(option)) return@forEach
        val choice = option.choices.find { it.value == option.value }?.label
            ?: option.value.takeIf { it.isNotBlank() }
            ?: option.choices.firstOrNull()?.label
            ?: return@forEach
        val compact = when {
            isEffortOption(option) -> effortShort(option.value, choice)
            isModelOption(option) -> compactModelLabel(choice)
            isFastOption(option) && !isFastOn(option) -> return@forEach
            isFastOption(option) -> "Fast"
            else -> compactChoiceLabel(choice)
        }
        if (compact.isNotBlank()) parts += compact
    }
    return parts.distinct().joinToString(" · ")
}

fun effortShort(value: String, fallback: String): String {
    val chinese = Regex("极高|超高|高|中|低").find(fallback)?.value
    val normalized = "$value $fallback".lowercase().replace(Regex("[^a-z]"), "")
    return when {
        Regex("ultra|maximum|max|ultrathink").containsMatchIn(normalized) -> "Max"
        Regex("xhigh|extrahigh|veryhigh").containsMatchIn(normalized) -> "XHigh"
        normalized.contains("high") -> "High"
        Regex("medium|med|mid").containsMatchIn(normalized) -> "Med"
        Regex("minimal|min|low").containsMatchIn(normalized) -> "Low"
        chinese != null -> chinese
        else -> compactChoiceLabel(fallback.ifBlank { value })
    }
}

fun compactModelLabel(label: String): String =
    label.replace(Regex("(?i)\\s*model$"), "").replace(" ", "").trim()

fun compactChoiceLabel(label: String): String =
    label.replace(Regex("(?i)\\s*(effort|mode|level)$"), "").trim()

fun optionPriority(option: SessionConfigOption): Int {
    return when {
        isModeOption(option) -> 0
        isModelOption(option) -> 10
        isEffortOption(option) -> 20
        isFastOption(option) -> 30
        else -> 40
    }
}

fun isEffortOption(option: SessionConfigOption): Boolean {
    val identity = optionIdentity(option)
    return identity.contains("thought") ||
        identity.contains("effort") ||
        identity.contains("reason") ||
        identity.contains("推理")
}

fun isModelOption(option: SessionConfigOption): Boolean {
    val identity = optionIdentity(option)
    return identity.contains("model") && !isFastOption(option) && !isEffortOption(option)
}

fun isModeOption(option: SessionConfigOption): Boolean {
    if (isModelOption(option) || isFastOption(option) || isEffortOption(option)) return false
    val identity = optionIdentity(option)
    return identity.split(Regex("\\s+")).any { it == "mode" }
}

fun isFastOption(option: SessionConfigOption): Boolean {
    val identity = optionIdentity(option)
    return identity.contains("fast")
}

fun isFastOn(option: SessionConfigOption): Boolean {
    val value = option.value.lowercase()
    return value == "on" || value == "true" || value == "1"
}

private fun optionIdentity(option: SessionConfigOption): String =
    "${option.category} ${option.key} ${option.label}".lowercase()

private fun isGenericMode(name: String): Boolean {
    val n = name.lowercase().replace(Regex("[\\s_-]"), "")
    return n == "agent" || n == "default" || n == "normal" || n == "auto" || n == "manual"
}

fun bundledAgentAsset(agentId: String, dark: Boolean): String? {
    val key = normalizeAgentKey(agentId)
    return when (key) {
        "claude_code" -> if (dark) "claude-dark.svg" else "claude-light.svg"
        "codex" -> if (dark) "codex-dark.svg" else "codex-light.svg"
        "gemini" -> if (dark) "gemini-dark.svg" else "gemini-light.svg"
        "openclaw" -> "openclaw.svg"
        "opencode" -> if (dark) "opencode-dark.svg" else "opencode-light.svg"
        "cline" -> "cline.svg"
        "codebuddy" -> "codebuddy.svg"
        "kimi" -> "kimi.svg"
        "pi" -> "pi.svg"
        "grok" -> "grok.svg"
        "cursor" -> if (dark) "cursor-dark.svg" else "cursor-light.svg"
        "deepseek_harness" -> if (dark) "deepseek-harness-dark.svg" else "deepseek-harness-light.svg"
        else -> null
    }
}

fun normalizeAgentKey(agentId: String): String {
    val n = agentId.lowercase().replace('-', '_').replace(' ', '_')
    return when {
        n.contains("claude") || n.contains("sonnet") || n.contains("opus") || n.contains("haiku") -> "claude_code"
        n.contains("codex") || n.contains("gpt") || n.startsWith("o1") || n.startsWith("o3") || n.startsWith("o4") -> "codex"
        n.contains("gemini") || n.contains("antigravity") -> "gemini"
        n.contains("openclaw") -> "openclaw"
        n.contains("opencode") || n == "open_code" || n.contains("qoder") -> "opencode"
        n.contains("cline") -> "cline"
        n.contains("codebuddy") || n.contains("code_buddy") -> "codebuddy"
        n.contains("kimi") || n.contains("moonshot") -> "kimi"
        n == "pi" || n.startsWith("pi_") -> "pi"
        n.contains("grok") -> "grok"
        n.contains("cursor") -> "cursor"
        n.contains("deepseek") -> "deepseek_harness"
        else -> n
    }
}

fun agentDisplayName(agentId: String, catalogName: String = ""): String {
    if (catalogName.isNotBlank()) return catalogName
    return when (normalizeAgentKey(agentId)) {
        "claude_code" -> "Claude Code"
        "codex" -> "Codex"
        "gemini" -> "Gemini CLI"
        "openclaw" -> "OpenClaw"
        "opencode" -> "OpenCode"
        "cline" -> "Cline"
        "codebuddy" -> "CodeBuddy"
        "kimi" -> "Kimi Code"
        "pi" -> "Pi"
        "grok" -> "Grok"
        "cursor" -> "Cursor"
        "deepseek_harness" -> "DeepSeek Harness"
        else -> agentId
    }
}
