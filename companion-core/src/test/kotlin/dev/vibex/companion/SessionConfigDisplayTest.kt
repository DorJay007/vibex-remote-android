package dev.vibex.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionConfigDisplayTest {
    @Test
    fun compactSummaryUsesModelAndShortEffort() {
        val summary = compactSessionConfigSummary(
            modes = emptyList(),
            currentModeId = "",
            options = listOf(
                SessionConfigOption(
                    key = "model",
                    label = "Model",
                    category = "model",
                    value = "grok-4.6",
                    choices = listOf(SessionConfigChoice("grok-4.6", "Grok 4.6")),
                ),
                SessionConfigOption(
                    key = "reasoning_effort",
                    label = "推理强度",
                    category = "thought_level",
                    value = "high",
                    choices = listOf(SessionConfigChoice("high", "High Effort")),
                ),
            ),
        )
        assertEquals("Grok4.6 · High", summary)
    }

    @Test
    fun skipsGenericAgentModeAndCompactsGpt() {
        val summary = compactSessionConfigSummary(
            modes = listOf(SessionMode("agent", "Agent")),
            currentModeId = "agent",
            options = listOf(
                SessionConfigOption("mode", "Mode", "mode", "agent", listOf(SessionConfigChoice("agent", "Agent"))),
                SessionConfigOption("model", "Model", "model", "gpt-5.6-sol", listOf(SessionConfigChoice("gpt-5.6-sol", "GPT 5.6 Sol"))),
                SessionConfigOption("reasoning_effort", "推理强度", "thought_level", "high", listOf(SessionConfigChoice("high", "High"))),
            ),
        )
        assertEquals("GPT5.6Sol · High", summary)
    }

    @Test
    fun effortShortMapsEnglishAndChinese() {
        assertEquals("High", effortShort("high", "High Effort"))
        assertEquals("Med", effortShort("medium", "Medium"))
        assertEquals("Low", effortShort("low", "Low"))
        assertEquals("高", effortShort("x", "高"))
    }

    @Test
    fun effortUnderModelConfigDoesNotJoinModelRow() {
        val effort = SessionConfigOption(
            key = "reasoning_effort",
            label = "推理强度",
            category = "model_config",
            value = "low",
            choices = listOf(SessionConfigChoice("low", "Low Effort")),
        )
        assertEquals(false, isModelOption(effort))
        assertEquals(true, isEffortOption(effort))
        val summary = compactSessionConfigSummary(
            emptyList(),
            "",
            listOf(
                SessionConfigOption("model", "Model", "model", "grok-4.6", listOf(SessionConfigChoice("grok-4.6", "Grok 4.6"))),
                effort,
            ),
        )
        assertEquals("Grok4.6 · Low", summary)
    }

    @Test
    fun bundledGrokAssetIgnoresCatalogLetterFallback() {
        assertEquals("grok-light.svg", bundledAgentAsset("grok", dark = false))
        assertEquals("grok-dark.svg", bundledAgentAsset("Grok Code", dark = true))
        assertEquals("antigravity-dark.svg", bundledAgentAsset("antigravity", dark = true))
        assertEquals("gemini-dark.svg", bundledAgentAsset("gemini", dark = true))
        assertEquals("claude-dark.svg", bundledAgentAsset("claude_code", dark = true))
        assertEquals("claude-light.svg", bundledAgentAsset("Claude Code", dark = false))
        assertNull(bundledAgentAsset("workbuddy", dark = false))
    }

    @Test
    fun availabilityLabelUsesLifecycleAndLogin() {
        assertEquals("可用", agentAvailabilityLabel("ready"))
        assertEquals("未登录", agentAvailabilityLabel("needs_auth"))
        assertEquals("未登录", agentAvailabilityLabel("ready", "not_logged_in"))
        assertEquals("需修复", agentAvailabilityLabel("needs_repair"))
        assertEquals("未安装", agentAvailabilityLabel("uninstalled"))
        assertNull(agentAvailabilityLabel(""))
        assertEquals(true, agentIsUsable(true, "ready", "account"))
        assertEquals(false, agentIsUsable(true, "ready", "not_logged_in"))
        assertEquals(false, agentIsUsable(true, "needs_repair"))
        assertEquals(true, agentIsUsable(true, "", ""))
        assertEquals(false, agentIsUsable(false, "ready"))
    }

    @Test
    fun createTimeSeedShowsComposerSummaryWithoutHostEvents() {
        val session = TimelineSession()
        assertEquals("", compactSessionConfigSummary(session.sessionModes, session.currentModeId, session.sessionConfig))
        applyCreateTimeSessionConfig(
            session,
            modes = listOf(SessionMode("agent", "Agent")),
            currentModeId = "agent",
            options = listOf(
                SessionConfigOption(
                    key = "model",
                    label = "Model",
                    category = "model",
                    value = "grok-4.6",
                    choices = listOf(SessionConfigChoice("grok-4.6", "Grok 4.6")),
                ),
                SessionConfigOption(
                    key = "reasoning_effort",
                    label = "推理强度",
                    category = "thought_level",
                    value = "high",
                    choices = listOf(SessionConfigChoice("high", "High Effort")),
                ),
            ),
            selected = mapOf("reasoning_effort" to "low"),
        )
        assertEquals("Grok4.6 · Low", compactSessionConfigSummary(session.sessionModes, session.currentModeId, session.sessionConfig))
    }

    @Test
    fun modeOptionIsDistinctFromCodexModeChoices() {
        val mode = SessionConfigOption(
            key = "mode",
            label = "Mode",
            category = "mode",
            value = "agent",
            choices = listOf(
                SessionConfigChoice("read-only", "Read-only"),
                SessionConfigChoice("agent", "Agent"),
                SessionConfigChoice("full-access", "Agent (full access)"),
            ),
        )
        assertEquals(true, isModeOption(mode))
        assertEquals("Agent", configOptionCurrentLabel(mode, "agent"))
    }
}
