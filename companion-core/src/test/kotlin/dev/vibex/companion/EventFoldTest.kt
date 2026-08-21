package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue
import dev.vibex.remote.v1.RemoteEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventFoldTest {
    @Test
    fun foldsTextDeltasIntoOneRow() {
        val session = TimelineSession().also { it.conversationId = "c1" }
        EventFold.apply(session, event(1, "user_turn_created", obj("blocks" to JsonValue.Array(listOf(obj("text" to JsonValue.Text("Hi")))))))
        EventFold.apply(session, event(2, "assistant_text_delta", obj("text" to JsonValue.Text("Hel"), "message_id" to JsonValue.Text("m1"))))
        EventFold.apply(session, event(3, "assistant_text_delta", obj("text" to JsonValue.Text("lo"), "message_id" to JsonValue.Text("m1"))))
        EventFold.apply(session, event(4, "turn_completed", obj()))
        val view = session.snapshot()
        assertEquals(1, view.rows.count { it.kind == "assistant" })
        assertEquals("Hello", view.rows.first { it.kind == "assistant" }.body)
        assertEquals(null, view.inFlightTurnId)
    }

    @Test
    fun ignoresDuplicateSequence() {
        val session = TimelineSession()
        EventFold.apply(session, event(1, "assistant_text_delta", obj("text" to JsonValue.Text("A"))))
        EventFold.apply(session, event(1, "assistant_text_delta", obj("text" to JsonValue.Text("B"))))
        assertEquals("A", session.rows.single().body)
        assertEquals(1, session.lastSequence)
    }

    @Test
    fun unknownKindWithoutTextIsHidden() {
        val session = TimelineSession()
        EventFold.apply(session, event(8, "session_config_options_updated", obj("x" to JsonValue.Text("1"))))
        assertTrue(session.rows.isEmpty())
    }

    @Test
    fun assistantTextSurvivesBindingReadyRemovingEarlierRow() {
        val session = TimelineSession()
        EventFold.apply(session, event(1, "agent_binding_started", obj("agent_id" to JsonValue.Text("grok"))))
        EventFold.apply(session, event(2, "user_turn_created", obj("blocks" to JsonValue.Array(listOf(obj("text" to JsonValue.Text("Hi")))))))
        EventFold.apply(session, event(3, "assistant_text_delta", obj("text" to JsonValue.Text("Hel"), "message_id" to JsonValue.Text("m1"))))
        EventFold.apply(session, event(4, "agent_binding_ready", obj()))
        EventFold.apply(session, event(5, "assistant_text_delta", obj("text" to JsonValue.Text("lo"), "message_id" to JsonValue.Text("m1"))))
        assertEquals("Hello", session.rows.first { it.kind == "assistant" }.body)
    }

    @Test
    fun toolCallUsesTitleNotBareCompleted() {
        val session = TimelineSession()
        EventFold.apply(
            session,
            event(
                2,
                "tool_call_upsert",
                obj(
                    "tool_call" to obj(
                        "tool_call_id" to JsonValue.Text("t1"),
                        "title" to JsonValue.Text("read SKILL.md"),
                        "status" to JsonValue.Text("completed"),
                        "raw_output_append" to JsonValue.Text("ok"),
                    ),
                ),
            ),
        )
        val row = session.rows.single()
        assertEquals("read SKILL.md", row.title)
        assertEquals("ok", row.body)
    }

    @Test
    fun turnCompletedDoesNotAddACard() {
        val session = TimelineSession()
        EventFold.apply(session, event(1, "assistant_text_delta", obj("text" to JsonValue.Text("Done"))))
        EventFold.apply(session, event(2, "turn_completed", obj()))
        assertEquals(listOf("assistant"), session.rows.map { it.kind })
        assertEquals(null, session.inFlightTurnId)
    }

    @Test
    fun snapshotEventsCarryConversationEventPayload() {
        val session = TimelineSession()
        EventFold.applySnapshot(
            session,
            obj(
                "events" to JsonValue.Array(
                    listOf(
                        obj(
                            "sequence" to JsonValue.Number(1.0),
                            "kind" to JsonValue.Text("assistant_text_delta"),
                            "payload" to obj(
                                "kind" to JsonValue.Text("assistant_text_delta"),
                                "text" to JsonValue.Text("Hello"),
                                "message_id" to JsonValue.Text("m1"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("Hello", session.rows.single().body)
    }

    @Test
    fun permissionBecomesPending() {
        val session = TimelineSession()
        EventFold.apply(
            session,
            event(
                2,
                "permission_requested",
                obj(
                    "request" to obj(
                        "id" to JsonValue.Text("p1"),
                        "tool_name" to JsonValue.Text("bash"),
                        "options" to JsonValue.Array(
                            listOf(obj("option_id" to JsonValue.Text("allow-once"), "label" to JsonValue.Text("允许一次"))),
                        ),
                    ),
                ),
            ),
        )
        val pending = EventFold.pendingItems(session)
        assertEquals(1, pending.size)
        assertEquals("p1", pending[0].id)
        assertTrue(pending[0].options.isNotEmpty())
    }


    @Test
    fun toolUpdateKeepsTitleAndInput() {
        val session = TimelineSession()
        EventFold.apply(
            session,
            event(
                1,
                "tool_call_upsert",
                obj(
                    "tool_call" to obj(
                        "tool_call_id" to JsonValue.Text("t1"),
                        "title" to JsonValue.Text("read SKILL.md"),
                        "kind" to JsonValue.Text("read"),
                        "status" to JsonValue.Text("running"),
                        "raw_input" to obj("path" to JsonValue.Text("SKILL.md")),
                    ),
                ),
            ),
        )
        EventFold.apply(
            session,
            event(
                2,
                "tool_call_upsert",
                obj(
                    "tool_call" to obj(
                        "tool_call_id" to JsonValue.Text("t1"),
                        "status" to JsonValue.Text("completed"),
                        "raw_output_append" to JsonValue.Text("ok"),
                    ),
                ),
            ),
        )
        val row = session.rows.single()
        assertEquals("read SKILL.md", row.title)
        assertEquals("ok", row.body)
        assertEquals("read", row.toolKind)
        assertEquals("completed", row.toolStatus)
        assertTrue(row.toolInput.contains("SKILL.md"))
    }


    @Test
    fun secondTurnAnswerFollowsSecondUserEvenIfMessageIdRepeats() {
        val session = TimelineSession()
        EventFold.apply(session, event(1, "user_turn_created", obj("turn_id" to JsonValue.Text("t1"), "text" to JsonValue.Text("A?"))))
        EventFold.apply(session, event(2, "assistant_text_delta", obj("turn_id" to JsonValue.Text("t1"), "message_id" to JsonValue.Text("m"), "text" to JsonValue.Text("A."))))
        EventFold.apply(session, event(3, "user_turn_created", obj("turn_id" to JsonValue.Text("t2"), "text" to JsonValue.Text("B?"))))
        EventFold.apply(session, event(4, "assistant_text_delta", obj("turn_id" to JsonValue.Text("t2"), "message_id" to JsonValue.Text("m"), "text" to JsonValue.Text("B."))))
        assertEquals(listOf("user", "assistant", "user", "assistant"), session.rows.map { it.kind })
        assertEquals("A?", session.rows[0].body)
        assertEquals("A.", session.rows[1].body)
        assertEquals("B?", session.rows[2].body)
        assertEquals("B.", session.rows[3].body)
    }

    @Test
    fun reasoningStaysASeparateRowFromAssistant() {
        val session = TimelineSession()
        EventFold.apply(session, event(1, "user_turn_created", obj("turn_id" to JsonValue.Text("t1"), "text" to JsonValue.Text("Hi"))))
        EventFold.apply(session, event(2, "assistant_reasoning_delta", obj("turn_id" to JsonValue.Text("t1"), "message_id" to JsonValue.Text("m1"), "text" to JsonValue.Text("think"))))
        EventFold.apply(session, event(3, "assistant_text_delta", obj("turn_id" to JsonValue.Text("t1"), "message_id" to JsonValue.Text("m1"), "text" to JsonValue.Text("hello"))))
        assertEquals(listOf("user", "reasoning", "assistant"), session.rows.map { it.kind })
        assertEquals("think", session.rows[1].body)
        assertEquals("hello", session.rows[2].body)
    }

    @Test
    fun queuesSubmittedInputAndDropsClaimed() {
        val session = TimelineSession()
        EventFold.apply(
            session,
            event(
                1,
                "conversation_input",
                obj(
                    "event" to obj(
                        "kind" to JsonValue.Text("submitted"),
                        "input_id" to JsonValue.Text("i1"),
                        "revision" to JsonValue.Number(1.0),
                        "sort_key" to JsonValue.Number(1.0),
                        "payload" to obj("text" to JsonValue.Text("follow up")),
                    ),
                ),
            ),
        )
        assertEquals(listOf("follow up"), session.queued.map { it.text })
        EventFold.apply(
            session,
            event(
                2,
                "conversation_input",
                obj(
                    "event" to obj(
                        "kind" to JsonValue.Text("claimed"),
                        "input_id" to JsonValue.Text("i1"),
                    ),
                ),
            ),
        )
        assertTrue(session.queued.isEmpty())
    }

    @Test
    fun userTurnReplacesLocalOptimisticRowAndClearsCancelledNotice() {
        val session = TimelineSession()
        session.rows += TimelineRow(
            id = "local:1",
            kind = "user",
            title = "",
            body = "你好",
            tone = TimelineTone.Quiet,
        )
        EventFold.apply(session, event(1, "turn_cancelled", obj()))
        assertEquals("已取消", session.notices.single().title)
        EventFold.apply(
            session,
            event(
                2,
                "user_turn_created",
                obj(
                    "turn_id" to JsonValue.Text("t1"),
                    "blocks" to JsonValue.Array(listOf(obj("text" to JsonValue.Text("你好")))),
                ),
            ),
        )
        assertEquals(listOf("user:t1"), session.rows.map { it.id })
        assertTrue(session.notices.none { it.title == "已取消" })
        assertEquals("t1", session.inFlightTurnId)
    }

    @Test
    fun sessionConfigOptionsUpdateFillsComposerSummary() {
        val session = TimelineSession()
        EventFold.apply(
            session,
            event(
                1,
                "session_config_options_updated",
                obj(
                    "options" to JsonValue.Array(
                        listOf(
                            obj(
                                "key" to JsonValue.Text("model"),
                                "label" to JsonValue.Text("Model"),
                                "category" to JsonValue.Text("model"),
                                "value" to JsonValue.Text("grok-4.6"),
                                "choices" to JsonValue.Array(
                                    listOf(obj("value" to JsonValue.Text("grok-4.6"), "label" to JsonValue.Text("Grok 4.6"))),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("Grok4.6", compactSessionConfigSummary(session.sessionModes, session.currentModeId, session.sessionConfig))
    }

    @Test
    fun liveAssistantDeltaChangesPublishedSnapshot() {
        val session = TimelineSession()
        EventFold.apply(session, event(1, "user_turn_created", obj("turn_id" to JsonValue.Text("t1"), "text" to JsonValue.Text("Hi"))))
        val first = session.snapshot()
        EventFold.apply(session, event(2, "assistant_text_delta", obj("turn_id" to JsonValue.Text("t1"), "message_id" to JsonValue.Text("m1"), "text" to JsonValue.Text("Hello"))))
        val second = session.snapshot()
        assertTrue(first != second)
        assertEquals("Hello", second.rows.first { it.kind == "assistant" }.body)
    }

    @Test
    fun toolsStaySeparateRows() {
        val session = TimelineSession()
        EventFold.apply(session, event(1, "tool_call_upsert", obj("tool_call" to obj("tool_call_id" to JsonValue.Text("a"), "title" to JsonValue.Text("Read a")))))
        EventFold.apply(session, event(2, "tool_call_upsert", obj("tool_call" to obj("tool_call_id" to JsonValue.Text("b"), "title" to JsonValue.Text("Read b")))))
        assertEquals(2, session.rows.size)
        assertEquals(listOf("tool", "tool"), session.rows.map { it.kind })
    }

    private fun event(sequence: Long, kind: String, payload: JsonValue) =
        RemoteEvent(sequence, kind, payload)

    private fun obj(vararg pairs: Pair<String, JsonValue>) = JsonValue.Object(mapOf(*pairs))
}
