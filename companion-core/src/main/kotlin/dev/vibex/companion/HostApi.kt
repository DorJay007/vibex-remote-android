package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue
import dev.vibex.remote.v1.RemoteEvent
import java.util.UUID

data class ConversationSummary(
    val id: String,
    val workspaceId: String,
    val title: String,
    val agentId: String,
    val status: String,
    val updatedAt: String,
    val pinned: Boolean = false,
)

data class CatalogProject(val id: String, val name: String, val path: String = "")
data class CatalogWorkspace(val id: String, val projectId: String, val name: String, val branch: String = "")
data class CatalogAgent(
    val id: String,
    val ready: Boolean,
    val displayName: String = "",
    val iconSvg: String = "",
    val currentModeId: String = "",
    val sessionConfig: List<SessionConfigOption> = emptyList(),
    val sessionModes: List<SessionMode> = emptyList(),
    val usable: Boolean = true,
    val lifecycle: String = "",
    val authentication: String = "",
)

data class CatalogTag(val id: String, val name: String, val content: String = "")
data class WorkspaceEntry(val name: String, val path: String, val directory: Boolean)

data class SessionCatalog(
    val projects: List<CatalogProject>,
    val workspaces: List<CatalogWorkspace>,
    val agents: List<CatalogAgent>,
    val tags: List<CatalogTag> = emptyList(),
)

class HostApi(
    private val transport: HttpTransport,
    private val protocolVersion: String = PairingClient.CLIENT_PROTOCOL_VERSION,
) {
    fun health(origin: HostOrigin): Boolean {
        val response = transport.execute("GET", origin.resolve("/health"), emptyMap(), null)
        return response.status in 200..299
    }

    fun capabilities(origin: HostOrigin, token: String) =
        parseServerCapabilities(
            request(origin, token, "GET", "/api/v1/capabilities", null),
        )

    fun revoke(origin: HostOrigin, token: String, deviceId: String) {
        request(origin, token, "DELETE", "/api/v1/auth/devices/$deviceId", null)
    }

    fun listRecent(
        origin: HostOrigin,
        token: String,
        sinceDays: Int = 3,
        projectId: String? = null,
    ): List<ConversationSummary> {
        val args = linkedMapOf<String, Any?>(
            "limit" to 100.0,
            "sinceDays" to sinceDays.toDouble(),
            "projectId" to projectId,
        )
        val data = call(origin, token, "conversation_list_recent", args)
        return parseSummaries(data)
    }

    fun listForWorkspace(origin: HostOrigin, token: String, workspaceId: String): List<ConversationSummary> {
        val data = call(origin, token, "conversation_list", mapOf("workspaceId" to workspaceId))
        return parseSummaries(data)
    }

    fun catalog(origin: HostOrigin, token: String): SessionCatalog {
        val data = call(origin, token, "conversation_catalog", emptyMap())
        val root = data.asObject() ?: return SessionCatalog(emptyList(), emptyList(), emptyList())
        return SessionCatalog(
            projects = root.arrOrNull("projects").orEmpty().mapNotNull { item ->
                val obj = item.asObject() ?: return@mapNotNull null
                CatalogProject(
                    obj.textAny("id"),
                    obj.textAny("name"),
                    obj.textAny("path"),
                )
            },
            workspaces = root.arrOrNull("workspaces").orEmpty().mapNotNull { item ->
                val obj = item.asObject() ?: return@mapNotNull null
                CatalogWorkspace(
                    obj.textAny("id"),
                    obj.textAny("projectId", "project_id"),
                    obj.textAny("name"),
                    obj.textAny("branch"),
                )
            },
            agents = root.arrOrNull("agents").orEmpty().mapNotNull { item ->
                val obj = item.asObject() ?: return@mapNotNull null
                val ready = obj.boolOrNull("ready") ?: true
                val lifecycle = obj.textAny("lifecycle")
                val authentication = obj.textAny("authentication")
                CatalogAgent(
                    id = obj.textAny("id"),
                    ready = ready,
                    displayName = obj.textAny("displayName", "display_name", "name"),
                    iconSvg = obj.textAny("iconSvg", "icon_svg"),
                    currentModeId = parseCurrentMode(obj),
                    sessionConfig = parseJsonOptions(
                        obj,
                        "sessionConfigJson",
                        "session_config_json",
                        "sessionConfig",
                        "session_config",
                        "configOptions",
                        "config_options",
                        "controls",
                    ),
                    sessionModes = parseJsonModes(
                        obj,
                        "sessionModesJson",
                        "session_modes_json",
                        "sessionModes",
                        "session_modes",
                    ).ifEmpty { extractModesFromControls(obj) },
                    usable = obj.boolOrNull("usable") ?: agentIsUsable(ready, lifecycle, authentication),
                    lifecycle = lifecycle,
                    authentication = authentication,
                )
            },
            tags = root.arrOrNull("tags").orEmpty().mapNotNull { item ->
                val obj = item.asObject() ?: return@mapNotNull null
                val name = obj.textAny("name", "tagName", "tag_name")
                if (name.isBlank()) null else CatalogTag(obj.textAny("id"), name, obj.textAny("content"))
            },
        )
    }

    fun listSlashCommands(
        origin: HostOrigin,
        token: String,
        agentId: String,
        workspaceId: String? = null,
    ): List<ComposerToken> {
        val args = linkedMapOf<String, Any?>(
            "agentId" to agentId,
            "workspaceId" to workspaceId?.takeIf { it.isNotBlank() },
        )
        val data = runCatching {
            call(origin, token, "conversation_slash_commands", args)
        }.getOrNull() ?: return emptyList()
        val items = data.asArray()?.value ?: data.asObject()?.arrOrNull("commands").orEmpty()
        return items.mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            val name = obj.textAny("name").removePrefix("/")
            if (name.isBlank()) return@mapNotNull null
            val sourceKind = obj.textAny("sourceKind", "source_kind").ifBlank { "skill" }
            val sourceId = obj.textAny("sourceId", "source_id").ifBlank { name }
            ComposerToken(
                prefix = TokenPrefix.Slash,
                name = name,
                value = obj.textAny("value").ifBlank {
                    if (sourceKind == "skill") "/skill:$sourceId:$name" else "/$name"
                },
                description = obj.textAny("description"),
                key = "$sourceKind:$sourceId:$name",
            )
        }
    }

    fun listWorkspaceEntries(origin: HostOrigin, token: String, workspaceId: String): List<WorkspaceEntry> {
        val data = runCatching {
            call(origin, token, "conversation_workspace_entries", mapOf("workspaceId" to workspaceId))
        }.getOrNull() ?: return emptyList()
        val items = data.asArray()?.value ?: data.asObject()?.arrOrNull("entries").orEmpty()
        return items.mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            val name = obj.textAny("name")
            if (name.isBlank()) null else WorkspaceEntry(
                name = name,
                path = obj.textAny("path").ifBlank { name },
                directory = obj.boolOrNull("directory") ?: false,
            )
        }
    }

    fun archiveConversation(origin: HostOrigin, token: String, conversationId: String) {
        call(origin, token, "conversation_archive", mapOf("conversationId" to conversationId))
    }

    fun setConversationPinned(origin: HostOrigin, token: String, conversationId: String, pinned: Boolean) {
        call(origin, token, "conversation_set_pinned", mapOf("conversationId" to conversationId, "pinned" to pinned))
    }

    fun deleteConversation(origin: HostOrigin, token: String, conversationId: String) {
        call(origin, token, "conversation_delete", mapOf("conversationId" to conversationId))
    }

    fun renameConversation(origin: HostOrigin, token: String, conversationId: String, title: String) {
        call(origin, token, "conversation_rename", mapOf("conversationId" to conversationId, "title" to title))
    }

    fun setConversationStatus(origin: HostOrigin, token: String, conversationId: String, status: String) {
        call(origin, token, "conversation_set_status", mapOf("conversationId" to conversationId, "status" to status))
    }

    fun listArtifacts(origin: HostOrigin, token: String, conversationId: String): List<String> {
        val data = runCatching {
            call(origin, token, "artifact_list", mapOf("conversationId" to conversationId, "limit" to 20.0))
        }.getOrNull() ?: return emptyList()
        val items = data.asArray()?.value ?: (data.asObject()?.arrOrNull("artifacts") ?: emptyList())
        return items.mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            obj.textAny("relativePath", "relative_path", "path", "name").takeIf { it.isNotBlank() }
        }
    }

    fun setSessionMode(origin: HostOrigin, token: String, conversationId: String, modeId: String) {
        call(
            origin,
            token,
            "conversation_set_session_mode",
            mapOf("conversationId" to conversationId, "modeId" to modeId),
        )
    }

    fun setSessionConfigOption(
        origin: HostOrigin,
        token: String,
        conversationId: String,
        key: String,
        value: String,
    ) {
        call(
            origin,
            token,
            "conversation_set_session_config_option",
            mapOf(
                "conversationId" to conversationId,
                "key" to key,
                "value" to value,
            ),
        )
    }

    fun createWorkspace(
        origin: HostOrigin,
        token: String,
        projectId: String,
        name: String,
        branch: String?,
    ): CatalogWorkspace {
        val args = linkedMapOf<String, Any?>(
            "projectId" to projectId,
            "name" to name,
            "branch" to branch,
        )
        val data = call(origin, token, "conversation_workspace_create", args).asObject()
            ?: error("workspace payload missing")
        return CatalogWorkspace(
            data.textAny("id"),
            data.textAny("projectId", "project_id"),
            data.textAny("name"),
            data.textAny("branch"),
        )
    }

    fun createConversation(
        origin: HostOrigin,
        token: String,
        workspaceId: String,
        agentId: String,
        title: String?,
        initialPrompt: String?,
    ): ConversationSummary {
        val args = linkedMapOf<String, Any?>(
            "workspaceId" to workspaceId,
            "agentId" to agentId,
            "title" to title,
            "initialPrompt" to initialPrompt,
        )
        return parseSummary(call(origin, token, "conversation_create", args))
    }

    fun submitInput(
        origin: HostOrigin,
        token: String,
        conversationId: String,
        agentId: String,
        workspaceId: String,
        text: String,
    ) {
        call(
            origin,
            token,
            "conversation_input_submit",
            mapOf(
                "request" to mapOf(
                    "conversationId" to conversationId,
                    "payload" to mapOf(
                        "agentId" to agentId,
                        "workspaceId" to workspaceId,
                        "text" to text,
                    ),
                ),
            ),
        )
    }

    fun steer(
        origin: HostOrigin,
        token: String,
        conversationId: String,
        expectedTurnId: String,
        text: String,
    ) {
        call(
            origin,
            token,
            "conversation_steer",
            mapOf(
                "request" to mapOf(
                    "conversationId" to conversationId,
                    "expectedTurnId" to expectedTurnId,
                    "text" to text,
                ),
            ),
        )
    }


    fun cancelConversationInput(
        origin: HostOrigin,
        token: String,
        conversationId: String,
        inputId: String,
        expectedRevision: Long,
    ) {
        call(
            origin,
            token,
            "conversation_input_cancel",
            mapOf(
                "request" to mapOf(
                    "conversationId" to conversationId,
                    "inputId" to inputId,
                    "expectedRevision" to expectedRevision.toDouble(),
                ),
            ),
        )
    }

    fun cancelTurn(origin: HostOrigin, token: String, conversationId: String) {
        call(
            origin,
            token,
            "conversation_cancel_turn",
            mapOf("request" to mapOf("conversationId" to conversationId)),
        )
    }

    fun respondPermission(
        origin: HostOrigin,
        token: String,
        conversationId: String,
        permissionId: String,
        optionId: String,
    ) {
        call(
            origin,
            token,
            "conversation_respond_permission",
            mapOf(
                "request" to mapOf(
                    "conversationId" to conversationId,
                    "permissionId" to permissionId,
                    "response" to mapOf(
                        "kind" to "selected",
                        "option_id" to optionId,
                    ),
                ),
            ),
        )
    }

    fun respondQuestion(
        origin: HostOrigin,
        token: String,
        conversationId: String,
        questionId: String,
        content: String,
    ) {
        call(
            origin,
            token,
            "conversation_respond_question",
            mapOf(
                "request" to mapOf(
                    "conversationId" to conversationId,
                    "questionId" to questionId,
                    "response" to mapOf(
                        "action" to "accept",
                        "content" to mapOf("text" to content),
                    ),
                ),
            ),
        )
    }

    fun offlineEvents(origin: HostOrigin, token: String, conversationId: String, after: Long): List<RemoteEvent> {
        val body = request(
            origin,
            token,
            "GET",
            "/api/v1/conversations/$conversationId/offline?after_sequence=$after",
            null,
        )
        val root = JsonValue.parse(body) as JsonValue.Object
        return root.arrOrNull("events").orEmpty().mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            val kind = obj.textAny("kind", "event_kind", "eventKind")
            if (kind.isBlank()) return@mapNotNull null
            RemoteEvent(
                sequence = obj.longOrNull("sequence") ?: return@mapNotNull null,
                kind = kind,
                payload = obj.value["payload"] ?: obj.value["normalized_json"] ?: JsonValue.Null,
            )
        }
    }

    fun notificationSummary(origin: HostOrigin, token: String, conversationId: String): String? {
        val body = runCatching {
            request(
                origin,
                token,
                "GET",
                "/api/v1/conversations/$conversationId/notification-summary",
                null,
            )
        }.getOrNull() ?: return null
        val root = JsonValue.parse(body) as? JsonValue.Object ?: return null
        val outcome = root.textOrNull("outcome") ?: return null
        val at = root.textOrNull("occurred_at").orEmpty()
        return "$outcome · $at"
    }

    fun call(origin: HostOrigin, token: String, command: String, args: Map<String, Any?>): JsonValue {
        val operationId = UUID.randomUUID().toString()
        val body = encodeObject(
            mapOf(
                "operation_id" to operationId,
                "args" to args,
            ),
        )
        val response = request(origin, token, "POST", "/api/v1/call/$command", body)
        val root = JsonValue.parse(response) as JsonValue.Object
        return root.value["data"] ?: JsonValue.Null
    }

    private fun request(
        origin: HostOrigin,
        token: String,
        method: String,
        path: String,
        body: String?,
    ): String {
        val headers = mutableMapOf(
            "authorization" to "Bearer $token",
            PairingClient.PROTOCOL_HEADER to protocolVersion,
        )
        if (body != null) headers["content-type"] = "application/json"
        val exchange = transport.execute(method, origin.resolve(path), headers, body)
        if (exchange.status == 401) throw AuthRequiredException()
        if (exchange.status !in 200..299) {
            val message = runCatching {
                (JsonValue.parse(exchange.body) as JsonValue.Object).textOrNull("message")
            }.getOrNull() ?: "Host returned ${exchange.status}"
            throw HostApiException(exchange.status, message)
        }
        return exchange.body
    }

    private fun parseSummaries(data: JsonValue): List<ConversationSummary> {
        val items = data.asArray()?.value ?: return emptyList()
        return items.mapNotNull { parseSummaryOrNull(it) }
    }

    private fun parseSummary(data: JsonValue): ConversationSummary =
        parseSummaryOrNull(data) ?: error("conversation payload missing")

    private fun parseSummaryOrNull(data: JsonValue): ConversationSummary? {
        val obj = data.asObject() ?: return null
        val id = obj.textOrNull("id") ?: return null
        return ConversationSummary(
            id = id,
            workspaceId = obj.textAny("workspaceId", "workspace_id"),
            title = obj.textAny("title", "name").ifBlank { "会话" },
            agentId = obj.textAny("agentId", "agent_id"),
            status = obj.textAny("status"),
            updatedAt = obj.textAny("updatedAt", "updated_at"),
            pinned = obj.boolOrNull("pinned") == true || obj.textAny("pinnedAt", "pinned_at").isNotBlank(),
        )
    }
}

class HostApiException(val status: Int, message: String) : RuntimeException(message)
class AuthRequiredException : RuntimeException("需要重新配对")

private fun parseJsonOptions(obj: JsonValue.Object, vararg keys: String): List<SessionConfigOption> {
    keys.forEach { key ->
        val node = obj.value[key] ?: return@forEach
        val parsed = sessionConfigItems(node).mapNotNull { EventFold.parseConfigOption(it) }
        if (parsed.isNotEmpty()) return parsed
    }
    return emptyList()
}

private fun parseJsonModes(obj: JsonValue.Object, vararg keys: String): List<SessionMode> {
    keys.forEach { key ->
        val node = obj.value[key] ?: return@forEach
        val parsed = sessionModeItems(node).mapNotNull { parseSessionMode(it) }
        if (parsed.isNotEmpty()) return parsed
    }
    return emptyList()
}

private fun extractModesFromControls(obj: JsonValue.Object): List<SessionMode> {
    listOf("sessionConfig", "session_config", "sessionConfigJson", "session_config_json", "controls").forEach { key ->
        val wrapper = unwrapObject(obj.value[key]) ?: return@forEach
        val parsed = parseJsonModes(wrapper, "modes", "sessionModes", "session_modes")
        if (parsed.isNotEmpty()) return parsed
    }
    return emptyList()
}

private fun parseCurrentMode(obj: JsonValue.Object): String {
    obj.textAny("currentMode", "current_mode").takeIf { it.isNotBlank() }?.let { return it }
    listOf("sessionConfig", "session_config", "sessionConfigJson", "session_config_json", "controls").forEach { key ->
        val wrapper = unwrapObject(obj.value[key]) ?: return@forEach
        wrapper.textAny("currentMode", "current_mode").takeIf { it.isNotBlank() }?.let { return it }
    }
    return ""
}

private fun parseSessionMode(item: JsonValue): SessionMode? {
    val mode = item.asObject() ?: return null
    val id = mode.textAny("id")
    if (id.isBlank()) return null
    return SessionMode(id, mode.textAny("label", "name").ifBlank { id })
}

private fun sessionConfigItems(node: JsonValue): List<JsonValue> = when (node) {
    is JsonValue.Array -> node.value
    is JsonValue.Text -> {
        val parsed = runCatching { JsonValue.parse(node.value) }.getOrNull() ?: return emptyList()
        sessionConfigItems(parsed)
    }
    is JsonValue.Object -> {
        listOf("config_options", "configOptions", "sessionConfig", "session_config", "options", "choices", "value")
            .firstNotNullOfOrNull { key ->
                node.value[key]?.let { sessionConfigItems(it) }?.takeIf { it.isNotEmpty() }
            }
            .orEmpty()
    }
    else -> emptyList()
}

private fun sessionModeItems(node: JsonValue): List<JsonValue> = when (node) {
    is JsonValue.Array -> node.value
    is JsonValue.Text -> {
        val parsed = runCatching { JsonValue.parse(node.value) }.getOrNull() ?: return emptyList()
        sessionModeItems(parsed)
    }
    is JsonValue.Object -> {
        val nested = node.value["modes"] ?: node.value["sessionModes"] ?: node.value["session_modes"]
        if (nested != null) sessionModeItems(nested) else emptyList()
    }
    else -> emptyList()
}

private fun unwrapObject(node: JsonValue?): JsonValue.Object? = when (node) {
    is JsonValue.Object -> node
    is JsonValue.Text -> runCatching { JsonValue.parse(node.value) }.getOrNull()?.asObject()
    else -> null
}

fun encodeObject(map: Map<String, Any?>): String = encodeValue(map).encode()

fun encodeValue(value: Any?): JsonValue = when (value) {
    null -> JsonValue.Null
    is JsonValue -> value
    is String -> JsonValue.Text(value)
    is Boolean -> JsonValue.Bool(value)
    is Int -> JsonValue.Number(value.toDouble())
    is Long -> JsonValue.Number(value.toDouble())
    is Double -> JsonValue.Number(value)
    is Map<*, *> -> JsonValue.Object(
        value.entries.associate { (k, v) -> k.toString() to encodeValue(v) },
    )
    is List<*> -> JsonValue.Array(value.map { encodeValue(it) })
    else -> JsonValue.Text(value.toString())
}
