package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue
import dev.vibex.remote.v1.RemoteEvent

object EventFold {
    private val silentKinds = setOf(
        "available_commands_updated",
        "raw_diagnostic_recorded",
        "agent_connection_status_changed",
        "user_turn_started",
        "turn_completed",
        "conversation_created",
        "agent_binding_ready",
    )

    fun applyAll(session: TimelineSession, events: List<RemoteEvent>) {
        events.forEach { apply(session, it) }
    }

    fun applySnapshot(session: TimelineSession, payload: JsonValue) {
        val root = unwrapObject(payload) ?: return
        root.textAny("conversationId", "conversation_id").takeIf { it.isNotBlank() }?.let {
            session.conversationId = it
        }
        root.textAny("title").takeIf { it.isNotBlank() }?.let { session.title = it }
        val events = root.arrOrNull("events").orEmpty().mapNotNull { item ->
            parseRemoteEvent(item)
        }
        if (events.isNotEmpty()) {
            applyAll(session, events)
            return
        }
        val rows = root.arrOrNull("rows").orEmpty()
        if (rows.any { it.asObject()?.value?.containsKey("row") == true }) {
            applyTimelineRows(session, rows)
            return
        }
        rows.forEach { item ->
            val obj = item.asObject() ?: return@forEach
            val id = obj.textAny("id", "rowId", "row_id")
            if (id.isBlank()) return@forEach
            upsert(
                session,
                TimelineRow(
                    id = id,
                    kind = obj.textAny("kind", "type").ifBlank { "row" },
                    title = obj.textAny("title"),
                    body = obj.textAny("body", "text", "content"),
                    tone = toneFrom(obj.textAny("tone", "status")),
                    thinking = obj.textAny("thinking"),
                ),
            )
        }
    }

    /**
     * Folds rows returned by `conversation_timeline_page` (ADR-0082 JSONL transcript authority)
     * into `session.rows`, preserving control-plane rows (permissions, questions, end notices,
     * and any optimistic user message not yet flushed to JSONL).
     */
    fun applyTimelineRows(session: TimelineSession, rawRows: List<JsonValue>) {
        if (rawRows.isEmpty()) return
        val order = mutableListOf<String>()
        val deduped = mutableMapOf<String, Pair<Long, JsonValue.Object>>()
        for (raw in rawRows) {
            val obj = raw.asObject() ?: continue
            val rowId = obj.textAny("row_id", "rowId", "id")
            if (rowId.isBlank()) continue
            val rev = obj.longOrNull("revision") ?: 0L
            val existing = deduped[rowId]
            if (existing != null && existing.first > rev) continue
            if (existing == null) order += rowId
            deduped[rowId] = rev to obj
        }
        if (order.isEmpty()) return

        val transcriptRows = mutableListOf<TimelineRow>()
        val totalRows = order.size
        order.forEachIndexed { rowIndex, rowId ->
            val obj = deduped[rowId]?.second ?: return@forEachIndexed
            val inner = obj.objOrNull("row") ?: obj
            when (inner.textAny("kind", "type")) {
                "file_change_summary" -> {
                    val summary = inner.objOrNull("summary") ?: inner
                    val files = summary.arrOrNull("files").orEmpty()
                    var additions = 0L
                    var deletions = 0L
                    val parsedFiles = mutableListOf<FileChangeItem>()
                    files.forEach { item ->
                        val f = item.asObject() ?: return@forEach
                        val add = f.longOrNull("additions") ?: 0L
                        val del = f.longOrNull("deletions") ?: 0L
                        additions += add
                        deletions += del
                        val path = f.textAny("path")
                        if (path.isNotBlank()) {
                            val hunksList = f.arrOrNull("hunks").orEmpty().mapNotNull { it.asText() }
                            val hunks = if (hunksList.isNotEmpty()) hunksList.joinToString("\n")
                            else f.textOrNull("diff") ?: f.textOrNull("patch")
                            parsedFiles += FileChangeItem(
                                path = path,
                                additions = add.toInt(),
                                deletions = del.toInt(),
                                hunks = hunks,
                            )
                        }
                    }
                    if (parsedFiles.isNotEmpty()) {
                        session.fileChanges.clear()
                        session.fileChanges.addAll(parsedFiles)
                    }
                    session.additions = additions
                    session.deletions = deletions
                    val count = files.size.toLong().takeIf { it > 0 }
                        ?: summary.longOrNull("changed")
                        ?: summary.longOrNull("files")
                    session.fileChangeLabel = when {
                        additions > 0 || deletions > 0 -> "+$additions −$deletions"
                        count != null -> "$count 个文件"
                        else -> session.fileChangeLabel
                    }
                }
                "turn_error" -> {
                    val err = inner.objOrNull("error")
                    val msg = err?.objOrNull("error")?.textAny("message")
                        ?: err?.textAny("message").orEmpty()
                    if (msg.isNotBlank()) {
                        transcriptRows += TimelineRow(
                            id = "tx:err:$rowId",
                            kind = "turn_failed",
                            title = "失败",
                            body = msg,
                            tone = TimelineTone.Stop,
                        )
                    }
                }
                "message_turn" -> {
                    val turn = inner.objOrNull("turn") ?: return@forEachIndexed
                    val turnId = turn.textAny("id").ifBlank { rowId }
                    val role = turn.textAny("role").lowercase()
                    val phase = inner.textAny("phase").lowercase()
                    val isLastTurn = rowIndex == totalRows - 1
                    if (isLastTurn && phase == "streaming" && session.inFlightTurnId == null) {
                        session.inFlightTurnId = turnId
                    }
                    val isStreaming = phase == "streaming" || (isLastTurn && session.inFlightTurnId != null)
                    val turnTone = if (isStreaming) TimelineTone.Live else TimelineTone.Quiet
                    val blocks = turn.arrOrNull("blocks").orEmpty()
                    val turnModel = turn.textAny("model", "model_id", "modelId").takeIf { it.isNotBlank() }
                    val turnDurationMs = (turn.longOrNull("duration_ms") ?: turn.longOrNull("durationMs"))?.toInt()

                    turn.objOrNull("usage")?.let { usage ->
                        val used = usage.value["context_used"]?.asNumber() ?: usage.value["used"]?.asNumber()
                        val max = usage.value["context_window_max"]?.asNumber() ?: usage.value["size"]?.asNumber()
                        if (used != null && max != null && max > 0) {
                            session.usageLabel = "${used.toLong()} / ${max.toLong()}"
                        }
                    }

                    if (role == "user") {
                        val userTexts = blocks.mapNotNull { blockItem ->
                            val b = blockItem.asObject() ?: return@mapNotNull null
                            if (b.textAny("type") == "text") {
                                b.textAny("text", "content").takeIf { it.isNotBlank() }
                            } else {
                                null
                            }
                        }
                        val combined = userTexts.joinToString("\n").ifBlank {
                            turn.textAny("text", "content")
                        }
                        if (combined.isNotBlank()) {
                            transcriptRows += TimelineRow(
                                id = "tx:user:$rowId",
                                kind = "user",
                                title = "",
                                body = combined,
                                tone = TimelineTone.Quiet,
                                turnId = turnId,
                            )
                        }
                    } else if (role == "assistant") {
                        val toolResultsById = mutableMapOf<String, JsonValue.Object>()
                        blocks.forEach { blockItem ->
                            val b = blockItem.asObject() ?: return@forEach
                            if (b.textAny("type") == "tool_result") {
                                val id = b.textAny("tool_use_id", "toolUseId", "id")
                                if (id.isNotBlank()) toolResultsById[id] = b
                            }
                        }

                        blocks.forEachIndexed { blockIdx, blockItem ->
                            val b = blockItem.asObject() ?: return@forEachIndexed
                            when (b.textAny("type")) {
                                "thinking" -> {
                                    val text = b.textAny("text")
                                    val durationMs = (b.longOrNull("duration_ms") ?: b.longOrNull("durationMs"))?.toInt()
                                        ?: turnDurationMs
                                    if (text.isNotBlank()) {
                                        val prev = transcriptRows.lastOrNull()
                                        if (prev != null && prev.kind == "reasoning" && prev.turnId == turnId) {
                                            transcriptRows[transcriptRows.lastIndex] = prev.copy(
                                                body = prev.body + "\n\n" + text,
                                                tone = turnTone,
                                                thinkingDurationMs = durationMs ?: prev.thinkingDurationMs,
                                                modelName = turnModel ?: prev.modelName,
                                            )
                                        } else {
                                            transcriptRows += TimelineRow(
                                                id = "tx:think:$rowId:$blockIdx",
                                                kind = "reasoning",
                                                title = "",
                                                body = text,
                                                tone = turnTone,
                                                thinkingDurationMs = durationMs,
                                                turnId = turnId,
                                                modelName = turnModel,
                                            )
                                        }
                                    }
                                }
                                "tool_use" -> {
                                    val toolUseId = b.textAny("tool_use_id", "toolUseId", "id").ifBlank { "$rowId:$blockIdx" }
                                    val toolName = b.textAny("tool_name", "toolName", "name", "title").ifBlank { "工具" }
                                    val resultObj = toolResultsById[toolUseId]
                                    val isErr = resultObj?.boolOrNull("is_error") == true || resultObj?.boolOrNull("isError") == true
                                    val status = b.textAny("status").ifBlank {
                                        when {
                                            isErr -> "failed"
                                            resultObj != null -> "completed"
                                            !isStreaming -> "completed"
                                            else -> "running"
                                        }
                                    }
                                    val toolKind = b.textAny("kind", "tool_kind", "toolKind")
                                    val inputPreview = jsonPayload(b, "input_preview", "inputPreview", "raw_input", "input")
                                    val outputPreview = resultObj?.textAny("output_preview", "outputPreview", "output", "content").orEmpty()
                                        .ifBlank { b.textAny("output_preview", "outputPreview", "output") }
                                    val failed = isErr || status.contains("fail", true) || status.contains("error", true)
                                    val completed = status.contains("complet", true) || status.contains("success", true) || status.contains("done", true) || resultObj != null || !isStreaming
                                    val tone = when {
                                        failed -> TimelineTone.Stop
                                        completed -> TimelineTone.Quiet
                                        else -> TimelineTone.Live
                                    }
                                    transcriptRows += TimelineRow(
                                        id = "tx:tool:$rowId:$toolUseId",
                                        kind = "tool",
                                        title = toolName,
                                        body = outputPreview,
                                        tone = tone,
                                        toolName = toolName,
                                        toolKind = toolKind,
                                        toolStatus = status,
                                        toolInput = inputPreview,
                                        turnId = turnId,
                                        modelName = turnModel,
                                    )
                                }
                                "text" -> {
                                    val text = b.textAny("text")
                                    if (text.isNotBlank()) {
                                        val prev = transcriptRows.lastOrNull()
                                        if (prev != null && prev.kind == "assistant" && prev.turnId == turnId) {
                                            val sep = if (prev.body.endsWith("\n") || text.startsWith("\n")) "" else "\n\n"
                                            transcriptRows[transcriptRows.lastIndex] = prev.copy(
                                                body = prev.body + sep + text,
                                                tone = turnTone,
                                                modelName = turnModel ?: prev.modelName,
                                            )
                                        } else {
                                            transcriptRows += TimelineRow(
                                                id = "tx:asst:$rowId:$blockIdx",
                                                kind = "assistant",
                                                title = "",
                                                body = text,
                                                tone = turnTone,
                                                turnId = turnId,
                                                modelName = turnModel,
                                            )
                                        }
                                    }
                                }
                                "plan" -> {
                                    val entries = b.arrOrNull("entries").orEmpty()
                                    if (entries.isNotEmpty()) {
                                        session.planLines.clear()
                                        session.planTasks.clear()
                                        entries.forEachIndexed { idx, entryItem ->
                                            val eo = entryItem.asObject() ?: return@forEachIndexed
                                            val status = eo.textOrNull("status")
                                            val content = eo.textOrNull("content") ?: eo.textOrNull("text").orEmpty()
                                            if (content.isNotBlank()) {
                                                session.planLines += listOfNotNull(status, content).joinToString(" · ")
                                                session.planTasks += PlanTask(
                                                    id = idx,
                                                    label = content,
                                                    status = parsePlanTaskStatus(status.orEmpty()),
                                                    priority = eo.textOrNull("priority"),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (transcriptRows.isEmpty()) return

        val prevTxUserCount = session.rows.count { it.id.startsWith("tx:user:") }
        val newTxUserCount = transcriptRows.count { it.kind == "user" }
        val preservedTailUser = if (newTxUserCount <= prevTxUserCount) {
            val lastTxIdx = session.rows.indexOfLast { it.id.startsWith("tx:") }
            session.rows.drop(if (lastTxIdx >= 0) lastTxIdx + 1 else 0)
                .filter { !it.id.startsWith("tx:") && it.kind == "user" }
                .filter { pendingUser ->
                    prevTxUserCount > 0 || transcriptRows.none { it.kind == "user" && it.body.trim() == pendingUser.body.trim() }
                }
        } else {
            emptyList()
        }

        val preservedControlRows = session.rows.filter { row ->
            row.pendingKind != null ||
                row.id.startsWith("end:") ||
                row.id.startsWith("bind-fail:") ||
                row.id.startsWith("blocked:") ||
                row.id.startsWith("del:") ||
                row.id.startsWith("rel:") ||
                row.id.startsWith("art:")
        }

        session.rows.clear()
        session.rows.addAll(transcriptRows)
        session.rows.addAll(preservedTailUser)
        session.rows.addAll(preservedControlRows)
    }

    fun applySessionControls(session: TimelineSession, payload: JsonValue?) {
        val root = unwrapObject(payload) ?: return
        val currentMode = root.fields("current_mode", "currentMode", "current_mode_id", "currentModeId")
        if (currentMode.isNotBlank() && session.currentModeId.isBlank()) {
            session.currentModeId = currentMode
        }
        if (session.sessionModes.isEmpty()) {
            root.arrOrNull("modes").orEmpty().forEach { item ->
                val obj = item.asObject() ?: return@forEach
                val id = obj.textAny("id", "mode_id", "modeId")
                if (id.isNotBlank()) {
                    session.sessionModes += SessionMode(id, obj.textAny("name", "label").ifBlank { id })
                }
            }
        }
        if (session.sessionConfig.isEmpty()) {
            val options = root.arrOrNull("config_options") ?: root.arrOrNull("configOptions") ?: root.arrOrNull("options")
            options.orEmpty().forEach { item ->
                parseConfigOption(item)?.let { session.sessionConfig += it }
            }
        }
    }

    fun apply(session: TimelineSession, event: RemoteEvent) {
        if (event.sequence <= session.lastSequence) return
        session.lastSequence = event.sequence
        val payload = unwrapObject(event.payload)
        when (event.kind) {
            "conversation_created" -> {
                payload?.textOrNull("title")?.let { session.title = it }
            }
            "conversation_input" -> applyInput(session, payload)
            "conversation_steering" -> upsert(
                session,
                TimelineRow(
                    id = "steer:${event.sequence}",
                    kind = event.kind,
                    title = "",
                    body = payload?.nestedText("event", "text")
                        ?: payload.textOrEmpty("text"),
                    tone = TimelineTone.Live,
                    turnId = payload.textOrEmpty("expected_turn_id"),
                ),
            )
            "conversation_relation_created" -> upsert(
                session,
                TimelineRow(
                    id = "rel:${payload.textOrEmpty("relation_id")}",
                    kind = event.kind,
                    title = "子会话",
                    body = payload.textOrEmpty("child_conversation_id"),
                    tone = TimelineTone.Quiet,
                    childConversationId = payload.textOrEmpty("child_conversation_id"),
                ),
            )
            "agent_binding_started" -> {
                val bound = payload.fields("agent_id", "agentId", "agent_type")
                if (bound.isNotBlank() && !bound.equals("agent", true)) {
                    session.agentId = bound
                }
                val workspace = payload.fields("workspace_id", "workspaceId")
                if (workspace.isNotBlank() && !workspace.startsWith("/")) {
                    session.workspaceId = workspace
                }
            }
            "agent_binding_ready" -> session.rows.removeAll { it.id == "bind" }
            "agent_binding_recovered" -> Unit
            "agent_binding_recovery_failed", "agent_binding_load_failed" -> upsert(
                session,
                TimelineRow(
                    id = "bind-fail:${event.sequence}",
                    kind = event.kind,
                    title = "Agent 未能启动",
                    body = payload.textOrEmpty("reason").ifBlank {
                        payload.nestedText("reason", "kind").orEmpty()
                    },
                    tone = TimelineTone.Stop,
                ),
            )
            "agent_connection_status_changed" -> {
                session.agentStatus = payload.textOrEmpty("status")
            }
            "user_turn_created" -> {
                val turnId = payload.fields("turn_id", "turnId").ifBlank { "turn:${event.sequence}" }
                session.inFlightTurnId = turnId
                session.notices.removeAll { it.id.startsWith("end:") }
                session.rows.removeAll { it.id.startsWith("end:") }
                val body = userText(payload)
                session.rows.removeAll { it.id.startsWith("local:") && it.body == body }
                val lastUserIdx = session.rows.indexOfLast { it.kind == "user" }
                val lastAsstIdx = session.rows.indexOfLast { it.kind == "assistant" }
                val alreadyInTranscriptTail = lastUserIdx >= 0 &&
                    lastUserIdx > lastAsstIdx &&
                    session.rows[lastUserIdx].id.startsWith("tx:user:") &&
                    session.rows[lastUserIdx].body.trim() == body.trim()
                if (!alreadyInTranscriptTail) {
                    upsert(
                        session,
                        TimelineRow(
                            id = "user:$turnId",
                            kind = "user",
                            title = "",
                            body = body,
                            tone = TimelineTone.Quiet,
                            turnId = turnId,
                        ),
                    )
                }
            }
            "user_turn_queued" -> Unit
            "user_turn_started" -> {
                session.inFlightTurnId = payload.fields("turn_id", "turnId").ifBlank { "running" }
                session.rows.removeAll { it.id.startsWith("queued-turn:") }
            }
            "assistant_text_delta" -> appendAssistant(session, payload, body = payload.textOrEmpty("text"))
            "assistant_reasoning_delta" -> appendAssistant(
                session,
                payload,
                thinking = payload.textOrEmpty("text"),
            )
            "assistant_content_appended" -> appendAssistant(
                session,
                payload,
                body = contentPreview(payload?.objOrNull("block")),
                thinking = thinkingPreview(payload?.objOrNull("block")),
            )
            "plan_updated" -> {
                session.planLines.clear()
                session.planTasks.clear()
                payload?.arrOrNull("entries").orEmpty().forEachIndexed { idx, item ->
                    val obj = item.asObject() ?: return@forEachIndexed
                    val status = obj.textOrNull("status")
                    val content = obj.textOrNull("content") ?: obj.textOrNull("text").orEmpty()
                    if (content.isNotBlank()) {
                        session.planLines += listOfNotNull(status, content).joinToString(" · ")
                        session.planTasks += PlanTask(
                            id = idx,
                            label = content,
                            status = parsePlanTaskStatus(status.orEmpty()),
                            priority = obj.textOrNull("priority"),
                        )
                    }
                }
            }
            "tool_call_upsert" -> upsert(session, toolRow(session, event, payload))
            "permission_requested" -> {
                PermissionPrompt.from(event.payload)?.let { session.permissionPrompt = it }
                pending(
                    session,
                    event,
                    payload,
                    PendingKind.Permission,
                    "权限",
                    payload?.objOrNull("request") ?: payload,
                )
            }
            "permission_responded" -> {
                val id = payload.fields("permission_id", "permissionId")
                if (id.isBlank() || session.permissionPrompt?.id == id) {
                    session.permissionPrompt = null
                }
                resolvePending(session, PendingKind.Permission, id)
            }
            "question_requested" -> {
                PendingQuestion.from(event.payload)?.let { session.pendingQuestion = it }
                pending(
                    session,
                    event,
                    payload,
                    PendingKind.Question,
                    "提问",
                    payload?.objOrNull("request") ?: payload,
                )
            }
            "question_responded" -> {
                val id = payload.fields("question_id", "questionId")
                if (id.isBlank() || session.pendingQuestion?.id == id) {
                    session.pendingQuestion = null
                }
                resolvePending(session, PendingKind.Question, id)
            }
            "feedback_requested" -> {
                PendingQuestion.from(event.payload)?.let { session.pendingQuestion = it }
                pending(
                    session,
                    event,
                    payload,
                    PendingKind.Question,
                    "反馈",
                    payload?.objOrNull("request") ?: payload,
                )
            }
            "feedback_submitted" -> {
                val id = payload.fields("feedback_id", "feedbackId")
                if (id.isBlank() || session.pendingQuestion?.id == id) {
                    session.pendingQuestion = null
                }
                resolvePending(session, PendingKind.Question, id)
            }
            "terminal_updated" -> upsert(
                session,
                TimelineRow(
                    id = "term:${event.sequence}",
                    kind = "terminal",
                    title = "终端",
                    body = payload.nestedText("terminal", "title")
                        ?: payload.textOrEmpty("output"),
                    tone = TimelineTone.Quiet,
                ),
            )
            "usage_updated" -> {
                val usage = payload?.objOrNull("usage") ?: payload
                val used = usage?.value?.get("used")?.asNumber()
                val size = usage?.value?.get("size")?.asNumber()
                session.usageLabel = if (used != null && size != null) {
                    "${used.toLong()} / ${size.toLong()}"
                } else {
                    null
                }
            }
            "file_change_summary_updated" -> {
                val summary = payload?.objOrNull("summary") ?: payload
                val files = summary?.arrOrNull("files").orEmpty()
                var additions = 0L
                var deletions = 0L
                val parsedFiles = mutableListOf<FileChangeItem>()
                files.forEach { item ->
                    val obj = item.asObject() ?: return@forEach
                    val add = obj.longOrNull("additions") ?: 0
                    val del = obj.longOrNull("deletions") ?: 0
                    additions += add
                    deletions += del
                    val path = obj.textAny("path")
                    if (path.isNotBlank()) {
                        val hunksList = obj.arrOrNull("hunks").orEmpty().mapNotNull { it.asText() }
                        val hunks = if (hunksList.isNotEmpty()) hunksList.joinToString("\n")
                        else obj.textOrNull("diff") ?: obj.textOrNull("patch")
                        parsedFiles += FileChangeItem(
                            path = path,
                            additions = add.toInt(),
                            deletions = del.toInt(),
                            hunks = hunks,
                        )
                    }
                }
                if (parsedFiles.isNotEmpty()) {
                    session.fileChanges.clear()
                    session.fileChanges.addAll(parsedFiles)
                }
                session.additions = additions
                session.deletions = deletions
                val count = files.size.toLong().takeIf { it > 0 }
                    ?: summary?.longOrNull("changed")
                    ?: summary?.longOrNull("files")
                session.fileChangeLabel = when {
                    additions > 0 || deletions > 0 -> "+$additions −$deletions"
                    count != null -> "$count 个文件"
                    else -> null
                }
            }
            "session_mode_updated" -> {
                val current = payload.fields("current_mode_id", "currentModeId", "mode_id", "modeId")
                if (current.isNotBlank()) session.currentModeId = current
                val modes = payload?.arrOrNull("modes").orEmpty()
                if (modes.isNotEmpty()) {
                    session.sessionModes.clear()
                    modes.forEach { item ->
                        val obj = item.asObject() ?: return@forEach
                        val id = obj.textAny("id", "mode_id", "modeId")
                        if (id.isBlank()) return@forEach
                        session.sessionModes += SessionMode(id, obj.textAny("name", "label").ifBlank { id })
                    }
                }
            }
            "session_config_options_updated" -> {
                val options = payload?.arrOrNull("options").orEmpty()
                if (options.isNotEmpty()) {
                    session.sessionConfig.clear()
                    options.forEach { item ->
                        parseConfigOption(item)?.let { session.sessionConfig += it }
                    }
                }
            }
            "artifact_revision_recorded" -> upsert(
                session,
                TimelineRow(
                    id = "art:${payload.nestedText("artifact", "artifact_id")}",
                    kind = event.kind,
                    title = "产物",
                    body = payload.nestedText("artifact", "relative_path").orEmpty(),
                    tone = TimelineTone.Quiet,
                ),
            )
            "turn_blocked" -> {
                val reason = payload?.objOrNull("reason")
                val kind = when (reason.textOrEmpty("kind")) {
                    "permission" -> PendingKind.Permission
                    "question" -> PendingKind.Question
                    else -> PendingKind.Blocked
                }
                upsert(
                    session,
                    TimelineRow(
                        id = "blocked:${event.sequence}",
                        kind = event.kind,
                        title = "等待你",
                        body = reason.textOrEmpty("message").ifBlank { kind.name },
                        tone = TimelineTone.Hold,
                        pendingKind = kind,
                        pendingId = reason.textOrEmpty("permission_id")
                            .ifBlank { reason.textOrEmpty("question_id") },
                    ),
                )
            }
            "turn_completed" -> {
                session.inFlightTurnId = null
                session.permissionPrompt = null
                session.pendingQuestion = null
                session.notices.removeAll { it.id.startsWith("end:") }
                session.rows.removeAll { it.kind == "turn_cancelled" }
            }
            "turn_failed" -> finishTurn(
                session,
                event,
                "失败",
                TimelineTone.Stop,
                payload.nestedText("error", "message") ?: payload.textOrEmpty("message"),
            )
            "turn_cancelled" -> finishTurn(session, event, "已取消", TimelineTone.Quiet)
            "turn_interrupted" -> finishTurn(
                session,
                event,
                "已中断",
                TimelineTone.Stop,
                "不会自动重发。点重试继续。",
            )
            "session_config_stale" -> if (payload?.boolOrNull("stale") == true) {
                upsertNotice(
                    session,
                    SessionNotice(
                        id = "stale",
                        severity = NoticeSeverity.Warning,
                        title = "默认未生效",
                        body = payload.textOrEmpty("reason"),
                    ),
                )
            }
            "prompt_capabilities_updated" -> {
                val caps = payload?.objOrNull("capabilities") ?: payload
                session.canSteer = caps?.boolOrNull("steer") == true ||
                    caps?.value?.containsKey("steering") == true
            }
            "available_commands_updated" -> {
                session.availableCommands.clear()
                val items = payload?.arrOrNull("commands")
                    ?: payload?.arrOrNull("availableCommands")
                    ?: payload?.objOrNull("event")?.arrOrNull("commands")
                    ?: payload?.objOrNull("event")?.arrOrNull("availableCommands")
                    ?: emptyList()
                items.forEach { item ->
                    val obj = item.asObject() ?: return@forEach
                    val name = obj.textAny("name", "command").removePrefix("/")
                    if (name.isNotBlank()) {
                        session.availableCommands += ComposerToken(
                            prefix = TokenPrefix.Slash,
                            name = name,
                            value = "/$name",
                            key = "native:$name:$name",
                            description = obj.textAny("description", "hint"),
                        )
                    }
                }
            }
            "agent_session_info_updated" -> {
                val title = payload?.objOrNull("patch")?.textOrNull("title")
                    ?: payload.textOrEmpty("title")
                if (title.isNotBlank()) session.title = title
            }
            "delegation_started" -> upsert(
                session,
                TimelineRow(
                    id = "del:${payload.nestedText("delegation", "delegation_id")}",
                    kind = event.kind,
                    title = "委派",
                    body = payload.nestedText("delegation", "task_preview").orEmpty(),
                    tone = TimelineTone.Live,
                    childConversationId = payload.nestedText("delegation", "child_conversation_id").orEmpty(),
                ),
            )
            "delegation_completed" -> upsert(
                session,
                TimelineRow(
                    id = "del:${payload.textOrEmpty("delegation_id")}",
                    kind = event.kind,
                    title = "委派结束",
                    body = payload.nestedText("result", "text_preview").orEmpty(),
                    tone = TimelineTone.Quiet,
                ),
            )
            "raw_diagnostic_recorded" -> Unit
            else -> if (event.kind !in silentKinds) {
                val text = extractVisibleText(payload)
                if (text.isNotBlank()) {
                    upsert(
                        session,
                        TimelineRow(
                            id = "note:${event.sequence}",
                            kind = event.kind,
                            title = "",
                            body = text,
                            tone = TimelineTone.Quiet,
                        ),
                    )
                }
            }
        }
    }

    fun pendingItems(session: TimelineSession): List<PendingItem> =
        session.rows.filter { it.pendingKind != null }.map { row ->
            PendingItem(
                conversationId = session.conversationId,
                conversationTitle = session.title.ifBlank { "会话" },
                kind = row.pendingKind!!,
                id = row.pendingId,
                title = row.title,
                body = row.body,
                options = row.options,
                rowId = row.id,
            )
        }

    private fun applyInput(session: TimelineSession, payload: JsonValue.Object?) {
        val event = payload?.objOrNull("event") ?: payload
        val id = event.fields("input_id", "inputId")
        val status = event.textOrEmpty("kind").ifBlank { event.fields("status") }.ifBlank { "submitted" }
        val text = event?.objOrNull("payload")?.textOrNull("text")
            ?: event?.objOrNull("payload")?.textOrNull("displayText")
            ?: event.textOrEmpty("text")
        if (id.isBlank()) return
        val revision = event?.longOrNull("revision") ?: 0L
        val sortKey = event?.longOrNull("sort_key") ?: event?.longOrNull("sortKey") ?: 0L
        if (status.contains("cancel", true) || status.contains("claimed", true) || status.contains("dispatch", true)) {
            session.queued.removeAll { it.id == id }
            return
        }
        val item = QueuedInput(id, text, status, revision, sortKey)
        val existing = session.queued.indexOfFirst { it.id == id }
        if (existing >= 0) session.queued[existing] = item else session.queued += item
        session.queued.sortBy { it.sortKey }
    }

    private fun appendAssistant(
        session: TimelineSession,
        payload: JsonValue.Object?,
        body: String = "",
        thinking: String = "",
    ) {
        val turnId = payload.fields("turn_id", "turnId").ifBlank { session.inFlightTurnId.orEmpty().ifBlank { "turn" } }
        val messageId = payload.fields("message_id", "messageId").ifBlank { "live" }
        if (thinking.isNotBlank()) {
            appendSegment(session, "think:$turnId:$messageId", "reasoning", thinking, turnId)
        }
        if (body.isNotBlank()) {
            appendSegment(session, "asst:$turnId:$messageId", "assistant", body, turnId)
        }
    }

    private fun appendSegment(
        session: TimelineSession,
        baseId: String,
        kind: String,
        text: String,
        turnId: String,
    ) {
        val last = session.rows.lastOrNull()
        if (last != null && last.kind == kind && last.turnId == turnId && (last.id == baseId || last.id.startsWith("$baseId#"))) {
            session.rows[session.rows.lastIndex] = last.copy(body = last.body + text, tone = TimelineTone.Live)
            return
        }
        val id = if (session.rows.any { it.id == baseId }) "$baseId#${session.rows.size}" else baseId
        session.rows += TimelineRow(
            id = id,
            kind = kind,
            title = "",
            body = text,
            tone = TimelineTone.Live,
            turnId = turnId,
        )
    }

    private fun toolRow(session: TimelineSession, event: RemoteEvent, payload: JsonValue.Object?): TimelineRow {
        val tool = payload?.objOrNull("tool_call") ?: payload
        val id = tool.fields("tool_call_id", "toolCallId", "id").ifBlank { "tool:${event.sequence}" }
        val rowId = "tool:$id"
        val existing = session.rows.find { it.id == rowId }
        val name = tool.fields("title", "name").ifBlank { existing?.title.orEmpty() }
        val kind = tool.fields("kind", "tool_kind", "toolKind").ifBlank { existing?.toolKind.orEmpty() }
        val status = tool.fields("status").ifBlank { existing?.toolStatus.orEmpty() }
        val input = jsonPayload(tool, "raw_input", "rawInput", "input", "input_preview", "inputPreview")
            .ifBlank { existing?.toolInput.orEmpty() }
        val output = toolPreview(tool).ifBlank { existing?.body.orEmpty() }
        val error = status.contains("fail", true) || status.contains("error", true)
        return TimelineRow(
            id = rowId,
            kind = "tool",
            title = name.ifBlank { "工具" },
            body = output,
            tone = when {
                error -> TimelineTone.Stop
                status.contains("complet", true) || status.contains("success", true) -> TimelineTone.Quiet
                else -> TimelineTone.Live
            },
            toolName = name.ifBlank { existing?.toolName.orEmpty() },
            toolKind = kind,
            toolStatus = status,
            toolInput = input,
            turnId = payload.fields("turn_id", "turnId").ifBlank { existing?.turnId.orEmpty().ifBlank { session.inFlightTurnId.orEmpty() } },
        )
    }

    private fun toolPreview(tool: JsonValue.Object?): String {
        if (tool == null) return ""
        val direct = tool.textAny("raw_output_append", "output", "output_preview", "content")
        if (direct.isNotBlank()) return direct.take(8000)
        val raw = tool.value["raw_output"] ?: tool.value["content"]
        return flattenText(raw).take(8000)
    }

    private fun jsonPayload(obj: JsonValue.Object?, vararg keys: String): String {
        if (obj == null) return ""
        for (key in keys) {
            val value = obj.value[key] ?: continue
            when (value) {
                is JsonValue.Text -> if (value.value.isNotBlank()) return value.value
                is JsonValue.Object, is JsonValue.Array -> return value.encode()
                else -> Unit
            }
        }
        return ""
    }

    private fun pending(
        session: TimelineSession,
        event: RemoteEvent,
        payload: JsonValue.Object?,
        kind: PendingKind,
        title: String,
        request: JsonValue.Object?,
    ) {
        val id = request.fields("id", "permission_id", "permissionId", "question_id", "questionId")
            .ifBlank { event.sequence.toString() }
        val options = request?.arrOrNull("options").orEmpty().mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            PendingOption(
                id = obj.textAny("option_id", "optionId", "id"),
                label = obj.textAny("label", "name"),
            )
        }
        upsert(
            session,
            TimelineRow(
                id = "${kind.name.lowercase()}:$id",
                kind = event.kind,
                title = title,
                body = request.fields("tool_name", "toolName", "prompt", "message").orEmpty(),
                tone = TimelineTone.Hold,
                pendingKind = kind,
                pendingId = id,
                options = options,
            ),
        )
    }

    private fun resolvePending(session: TimelineSession, kind: PendingKind, id: String) {
        session.rows.replaceAll { row ->
            if (row.pendingKind == kind && (id.isBlank() || row.pendingId == id)) {
                row.copy(pendingKind = null, tone = TimelineTone.Quiet)
            } else {
                row
            }
        }
        session.rows.removeAll { it.id.startsWith("blocked:") }
    }

    private fun finishTurn(
        session: TimelineSession,
        event: RemoteEvent,
        title: String,
        tone: TimelineTone,
        body: String = "",
    ) {
        session.inFlightTurnId = null
        session.permissionPrompt = null
        session.pendingQuestion = null
        val severity = if (tone == TimelineTone.Stop) NoticeSeverity.Error else NoticeSeverity.Info
        upsertNotice(
            session,
            SessionNotice(
                id = "end:${event.sequence}",
                severity = severity,
                title = title,
                body = body,
                action = if (event.kind == "turn_interrupted") "retry" else "",
            ),
        )
        upsert(
            session,
            TimelineRow(
                id = "end:${event.sequence}",
                kind = event.kind,
                title = title,
                body = body,
                tone = tone,
            ),
        )
    }

    private fun upsertNotice(session: TimelineSession, notice: SessionNotice) {
        val index = session.notices.indexOfFirst { it.id == notice.id }
        if (index >= 0) session.notices[index] = notice else session.notices += notice
    }

    internal fun parseConfigOption(item: JsonValue): SessionConfigOption? {
        val obj = item.asObject() ?: return null
        val key = obj.textAny("key", "id")
        if (key.isBlank()) return null
        val select = obj.objOrNull("kind")?.objOrNull("select")
        val boolean = obj.objOrNull("kind")?.objOrNull("boolean")
        val choices = buildList {
            addAll(parseConfigChoices(obj.value["choices"] ?: obj.value["options"] ?: obj.value["values"]))
            addAll(parseConfigChoices(select?.value?.get("options")))
            addAll(parseDependentChoices(obj.objOrNull("dependency")))
            if (boolean != null && isEmpty()) {
                add(SessionConfigChoice("false", "Off"))
                add(SessionConfigChoice("true", "On"))
            }
        }.distinctBy { it.value }
        val value = scalarText(obj, "value", "current", "currentValue", "current_value").ifBlank {
            select?.let { scalarText(it, "currentValue", "current_value", "value") }.orEmpty()
        }.ifBlank {
            boolean?.let { scalarText(it, "currentValue", "current_value", "value") }.orEmpty()
        }
        return SessionConfigOption(
            key = key,
            label = obj.textAny("label", "name").ifBlank { key },
            category = obj.textAny("category"),
            value = value,
            choices = choices,
        )
    }

    private fun parseDependentChoices(dependency: JsonValue.Object?): List<SessionConfigChoice> {
        val map = dependency?.objOrNull("choices_by_parent_value")
            ?: dependency?.objOrNull("choicesByParentValue")
            ?: return emptyList()
        return map.value.values.flatMap { parseConfigChoices(it) }.distinctBy { it.value }
    }

    private fun parseConfigChoices(node: JsonValue?): List<SessionConfigChoice> {
        if (node == null || node is JsonValue.Null) return emptyList()
        val items = when (node) {
            is JsonValue.Array -> node.value
            is JsonValue.Text -> {
                val parsed = runCatching { JsonValue.parse(node.value) }.getOrNull() ?: return emptyList()
                return parseConfigChoices(parsed)
            }
            is JsonValue.Object -> {
                val ungrouped = node.arrOrNull("ungrouped")
                val grouped = node.arrOrNull("grouped")
                when {
                    ungrouped != null -> ungrouped
                    grouped != null -> grouped.flatMap { group ->
                        group.asObject()?.arrOrNull("options").orEmpty()
                    }
                    else -> node.arrOrNull("options") ?: node.arrOrNull("choices") ?: emptyList()
                }
            }
            else -> emptyList()
        }
        return items.mapNotNull { parseConfigChoice(it) }
    }

    private fun parseConfigChoice(item: JsonValue): SessionConfigChoice? {
        return when (item) {
            is JsonValue.Text -> item.value.takeIf { it.isNotBlank() }?.let { SessionConfigChoice(it, it) }
            is JsonValue.Number -> SessionConfigChoice(item.value.toString(), item.value.toString())
            is JsonValue.Bool -> SessionConfigChoice(item.value.toString(), if (item.value) "On" else "Off")
            is JsonValue.Object -> {
                val value = scalarText(item, "value", "id").ifBlank { return null }
                SessionConfigChoice(value, item.textAny("label", "name").ifBlank { value })
            }
            else -> null
        }
    }

    private fun scalarText(obj: JsonValue.Object, vararg keys: String): String {
        keys.forEach { key ->
            when (val raw = obj.value[key]) {
                is JsonValue.Text -> if (raw.value.isNotBlank()) return raw.value
                is JsonValue.Number -> return raw.value.toString()
                is JsonValue.Bool -> return raw.value.toString()
                else -> Unit
            }
        }
        return ""
    }

    private fun upsert(session: TimelineSession, row: TimelineRow) {
        val index = session.rows.indexOfFirst { it.id == row.id }
        if (index >= 0) session.rows[index] = row else session.rows += row
    }

    private fun userText(payload: JsonValue.Object?): String {
        val blocks = payload?.arrOrNull("blocks").orEmpty()
        val texts = blocks.mapNotNull { block ->
            val obj = block.asObject() ?: return@mapNotNull null
            obj.textAny("text", "content").takeIf { it.isNotBlank() }
        }
        return texts.joinToString("\n").ifBlank { payload.textOrEmpty("text") }
    }

    private fun contentPreview(block: JsonValue.Object?): String {
        if (block == null) return ""
        if (block.textAny("type") == "thinking") return ""
        return block.textAny("text", "uri", "output_preview")
    }

    private fun thinkingPreview(block: JsonValue.Object?): String {
        if (block == null) return ""
        return if (block.textAny("type") == "thinking") block.textOrEmpty("text") else ""
    }

    private fun extractVisibleText(payload: JsonValue.Object?): String {
        if (payload == null) return ""
        return payload.fields("text", "message", "content", "body")
            .ifBlank { flattenText(payload.value["block"] ?: payload.value["delta"]) }
    }

    private fun flattenText(value: JsonValue?): String {
        return when (value) {
            is JsonValue.Text -> value.value
            is JsonValue.Object -> value.textAny("text", "content", "output")
            is JsonValue.Array -> value.value.joinToString("\n") { flattenText(it) }.trim()
            else -> ""
        }
    }

    private fun unwrapObject(value: JsonValue?): JsonValue.Object? {
        val direct = value?.asObject()
        if (direct != null) return direct
        val text = value?.asText() ?: return null
        return runCatching { JsonValue.parse(text).asObject() }.getOrNull()
    }

    private fun parseRemoteEvent(item: JsonValue): RemoteEvent? {
        val obj = item.asObject() ?: return null
        val sequence = obj.longOrNull("sequence") ?: return null
        val kind = obj.textAny("kind", "event_kind", "eventKind")
        if (kind.isBlank()) return null
        val payload = obj.value["payload"] ?: item
        return RemoteEvent(sequence, kind, payload)
    }

    private fun toneFrom(raw: String): TimelineTone = when (raw.lowercase()) {
        "live", "running", "inprogress" -> TimelineTone.Live
        "hold", "pending" -> TimelineTone.Hold
        "stop", "failed", "error" -> TimelineTone.Stop
        else -> TimelineTone.Quiet
    }

    private fun parsePlanTaskStatus(raw: String): PlanTask.Status {
        val s = raw.lowercase()
        return when {
            s == "completed" || s == "done" -> PlanTask.Status.Completed
            s == "in_progress" || s == "inprogress" || s == "running" -> PlanTask.Status.Running
            else -> PlanTask.Status.Pending
        }
    }

    private fun statusLabel(status: String): String = when (status.lowercase()) {
        "completed", "complete", "success" -> "已完成"
        "running", "in_progress", "inprogress" -> "进行中"
        "failed", "error" -> "失败"
        "pending" -> "等待"
        else -> status
    }

    private fun JsonValue.Object?.fields(vararg keys: String): String =
        this?.textAny(*keys).orEmpty()

    private fun JsonValue.Object?.textOrEmpty(key: String): String = this?.textOrNull(key).orEmpty()

    private fun JsonValue.Object?.nestedText(a: String, b: String): String? =
        this?.objOrNull(a)?.textOrNull(b)
}
