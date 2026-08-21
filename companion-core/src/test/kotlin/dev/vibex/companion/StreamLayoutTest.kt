package dev.vibex.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamLayoutTest {
    private fun row(
        id: String,
        kind: String,
        body: String = id,
        tone: TimelineTone = TimelineTone.Quiet,
        status: String = "completed",
    ) = TimelineRow(
        id = id,
        kind = kind,
        title = id,
        body = body,
        tone = tone,
        toolName = if (kind == "tool") id else "",
        toolStatus = if (kind == "tool") status else "",
    )

    @Test
    fun foldKeepsUserAndLastAssistantPerTurn() {
        val nodes = layoutStream(
            listOf(
                row("u1", "user"),
                row("t1", "tool"),
                row("t2", "tool"),
                row("r1", "reasoning"),
                row("a1", "assistant"),
                row("u2", "user"),
                row("t3", "tool"),
                row("a2", "assistant"),
            ),
            StreamLayoutOptions(thinkingHidden = false, foldProcess = true),
        )
        val kinds = nodes.map { it::class.simpleName }
        assertEquals(listOf("User", "Fold", "Assistant", "User", "Fold", "Assistant"), kinds)
        assertTrue(nodes.none { it is StreamNode.User && it.row.id.startsWith("t") })
        val firstFold = nodes[1] as StreamNode.Fold
        assertEquals(listOf("t1", "t2", "r1"), firstFold.rows.map { it.id })
        assertEquals("a1", (nodes[2] as StreamNode.Assistant).row.id)
        val secondFold = nodes[4] as StreamNode.Fold
        assertEquals(listOf("t3"), secondFold.rows.map { it.id })
    }

    @Test
    fun oneFoldHidesEarlierAssistantAndToolsUntilLastOutput() {
        val nodes = layoutStream(
            listOf(
                row("u1", "user"),
                row("t1", "tool"),
                row("a1", "assistant", body = "我先读文件"),
                row("t2", "tool"),
                row("a2", "assistant", body = "我再搜索"),
                row("t3", "tool"),
                row("a3", "assistant", body = "架构图画好了，文件在 docs/companion/architecture.drawio。"),
            ),
            StreamLayoutOptions(foldProcess = true),
        )
        val kinds = nodes.map { it::class.simpleName }
        assertEquals(listOf("User", "Fold", "Assistant"), kinds)
        val fold = nodes[1] as StreamNode.Fold
        assertEquals(listOf("t1", "a1", "t2", "a2", "t3"), fold.rows.map { it.id })
        assertEquals("a3", (nodes[2] as StreamNode.Assistant).row.id)
    }

    @Test
    fun hiddenThinkingGroupsToolsAcrossReasoning() {
        val nodes = layoutStream(
            listOf(
                row("t1", "tool"),
                row("r1", "reasoning"),
                row("t2", "tool"),
                row("a1", "assistant"),
            ),
            StreamLayoutOptions(thinkingHidden = true, foldProcess = false),
        )
        assertEquals(2, nodes.size)
        val tools = nodes[0] as StreamNode.Tools
        assertEquals(listOf("t1", "t2"), tools.rows.map { it.id })
        assertTrue(nodes[1] is StreamNode.Assistant)
    }

    @Test
    fun visibleThinkingSplitsToolGroups() {
        val nodes = layoutStream(
            listOf(
                row("t1", "tool"),
                row("r1", "reasoning"),
                row("t2", "tool"),
            ),
            StreamLayoutOptions(thinkingHidden = false, foldProcess = false),
        )
        assertEquals(listOf("Tools", "Reasoning", "Tools"), nodes.map { it::class.simpleName })
    }

    @Test
    fun runningToolsStayVisibleWhenNoAssistantYet() {
        val nodes = layoutStream(
            listOf(
                row("u1", "user"),
                row("t1", "tool", tone = TimelineTone.Live, status = "running"),
            ),
            StreamLayoutOptions(foldProcess = true),
        )
        assertEquals(listOf("User", "Tools"), nodes.map { it::class.simpleName })
    }

    @Test
    fun trailingRunningToolsStayAfterLastAssistant() {
        val nodes = layoutStream(
            listOf(
                row("u1", "user"),
                row("t1", "tool"),
                row("a1", "assistant", body = "开始处理"),
                row("t2", "tool", tone = TimelineTone.Live, status = "running"),
            ),
            StreamLayoutOptions(foldProcess = true),
        )
        assertEquals(listOf("User", "Fold", "Assistant", "Tools"), nodes.map { it::class.simpleName })
        assertEquals(listOf("t1"), (nodes[1] as StreamNode.Fold).rows.map { it.id })
        assertEquals("t2", (nodes[3] as StreamNode.Tools).rows.single().id)
    }

    @Test
    fun hiddenFailedToolsDoNotSplitGroups() {
        val nodes = layoutStream(
            listOf(
                row("t1", "tool"),
                row("fail", "tool", tone = TimelineTone.Stop, status = "failed"),
                row("t2", "tool"),
            ),
            StreamLayoutOptions(showFailedTools = false, foldProcess = false),
        )
        val tools = nodes.single() as StreamNode.Tools
        assertEquals(listOf("t1", "t2"), tools.rows.map { it.id })
    }

    @Test
    fun inFlightAfterUserShowsWaitingUntilAgentOutput() {
        val waiting = layoutStream(
            listOf(row("u1", "user")),
            StreamLayoutOptions(foldProcess = true, inFlight = true),
        )
        assertEquals(listOf("User", "Waiting"), waiting.map { it::class.simpleName })

        val afterPreviousTurn = layoutStream(
            listOf(row("u1", "user"), row("a1", "assistant", body = "done"), row("u2", "user")),
            StreamLayoutOptions(foldProcess = true, inFlight = true),
        )
        assertEquals(listOf("User", "Assistant", "User", "Waiting"), afterPreviousTurn.map { it::class.simpleName })

        val withTools = layoutStream(
            listOf(row("u1", "user"), row("t1", "tool", tone = TimelineTone.Live, status = "running")),
            StreamLayoutOptions(foldProcess = true, inFlight = true),
        )
        assertEquals(listOf("User", "Tools", "Waiting"), withTools.map { it::class.simpleName })
        assertEquals(true, (withTools.last() as StreamNode.Waiting).compact)
        assertEquals(false, (waiting.last() as StreamNode.Waiting).compact)
    }

    @Test
    fun inFlightKeepsStreamingStatusAfterAssistant() {
        val nodes = layoutStream(
            listOf(row("u1", "user"), row("a1", "assistant", body = "Hello", tone = TimelineTone.Live)),
            StreamLayoutOptions(foldProcess = true, inFlight = true),
        )
        assertEquals(listOf("User", "Assistant", "Waiting"), nodes.map { it::class.simpleName })
        assertEquals(true, (nodes.last() as StreamNode.Waiting).compact)
    }

    @Test
    fun lastAssistantStaysOutsideFold() {
        val nodes = layoutStream(
            listOf(
                row("t1", "tool"),
                row("a1", "assistant", body = "done"),
            ),
            StreamLayoutOptions(foldProcess = true),
        )
        assertEquals(listOf("Fold", "Assistant"), nodes.map { it::class.simpleName })
        assertEquals("done", (nodes[1] as StreamNode.Assistant).row.body)
    }
}
