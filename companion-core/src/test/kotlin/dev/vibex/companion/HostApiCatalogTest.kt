package dev.vibex.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostApiCatalogTest {
    @Test
    fun catalogReadsCamelCaseAndSnakeCase() {
        val body = """
            {"data":{
              "projects":[{"id":"p1","name":"VibeX","path":"/Users/mac/Projects/VibeX"}],
              "workspaces":[{"id":"w1","projectId":"p1","name":"main","branch":"master"}],
              "agents":[{"id":"codex","ready":true}]
            }}
        """.trimIndent()
        val catalog = HostApi(OneShotTransport(200, body)).catalog(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
        )
        assertEquals("VibeX", catalog.projects.single().name)
        assertEquals("/Users/mac/Projects/VibeX", catalog.projects.single().path)
        assertEquals("p1", catalog.workspaces.single().projectId)
        assertEquals("master", catalog.workspaces.single().branch)

        val snake = """
            {"data":{"projects":[{"id":"p2","name":"App"}],
              "workspaces":[{"id":"w2","project_id":"p2","name":"dev"}],
              "agents":[]}}
        """.trimIndent()
        val legacy = HostApi(OneShotTransport(200, snake)).catalog(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
        )
        assertEquals("p2", legacy.workspaces.single().projectId)
    }

    @Test
    fun listRecentSendsProjectAndWindowAndParsesSummaries() {
        val transport = OneShotTransport(
            200,
            """{"data":[{"id":"c1","workspace_id":"w1","title":"修配对","agent_id":"codex","status":"inprogress","updated_at":"2026-08-18T01:00:00Z","pinned_at":"2026-08-18T01:00:00Z"}]}""",
        )
        val listed = HostApi(transport).listRecent(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
            3,
            "p1",
        )
        assertEquals("修配对", listed.single().title)
        assertEquals("inprogress", listed.single().status)
        assertEquals("codex", listed.single().agentId)
        assertEquals(true, listed.single().pinned)
        assertTrue(transport.lastBody!!.contains("\"sinceDays\":3"))
        assertTrue(transport.lastBody!!.contains("\"projectId\":\"p1\""))
    }

    @Test
    fun catalogParsesSessionConfigFromJsonStringAndArray() {
        val inner = """[{"key":"model","label":"Model","value":"Grok 4.6","choices":[{"value":"Grok 4.6","label":"Grok 4.6"}]},{"key":"effort","label":"Effort","value":"high","choices":[{"value":"high","label":"High"}]}]"""
        val encoded = inner.replace("\"", "\\\"")
        val body = """{"data":{"projects":[],"workspaces":[],"agents":[{"id":"grok","ready":true,"displayName":"Grok","currentMode":"auto","sessionConfigJson":"$encoded"}]}}"""
        val catalog = HostApi(OneShotTransport(200, body)).catalog(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
        )
        val agent = catalog.agents.single()
        assertEquals("Grok 4.6", agent.sessionConfig.find { it.key == "model" }?.value)
        assertEquals("Grok4.6 · High", compactSessionConfigSummary(agent.sessionModes, agent.currentModeId, agent.sessionConfig))

        val arrayBody = """{"data":{"projects":[],"workspaces":[],"agents":[{"id":"codex","ready":true,"sessionConfig":[{"key":"model","label":"Model","value":"GPT 5.6 Sol"}]}]}}"""
        val fromArray = HostApi(OneShotTransport(200, arrayBody)).catalog(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
        )
        assertEquals("GPT 5.6 Sol", fromArray.agents.single().sessionConfig.single().value)
    }

    @Test
    fun catalogReadsCursorControlsSnapshotNotJustGrokShapedArrays() {
        val body = """{"data":{"projects":[],"workspaces":[],"agents":[{
          "id":"cursor",
          "ready":true,
          "displayName":"Cursor",
          "sessionConfig":{
            "modes":[{"id":"agent","label":"Agent"}],
            "current_mode":"agent",
            "config_options":[{
              "key":"model",
              "label":"Model",
              "category":"model",
              "value":"composer-1.5",
              "choices":[
                {"value":"composer-1.5","label":"Composer 1.5"},
                {"value":"gpt-5","label":"GPT-5"}
              ]
            }]
          }
        }]}}"""
        val catalog = HostApi(OneShotTransport(200, body)).catalog(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
        )
        val agent = catalog.agents.single()
        assertEquals("cursor", agent.id)
        assertEquals("agent", agent.currentModeId)
        assertEquals("composer-1.5", agent.sessionConfig.single().value)
        assertEquals("Composer 1.5", agent.sessionConfig.single().choices.first().label)
        assertEquals("Agent", agent.sessionModes.single().name)
    }

    @Test
    fun catalogPrefersPopulatedSessionConfigOverEmptyJsonString() {
        val body = """{"data":{"projects":[],"workspaces":[],"agents":[{
          "id":"cursor",
          "ready":true,
          "sessionConfigJson":"[]",
          "sessionConfig":[{"key":"model","label":"Model","value":"Composer 1.5","choices":[{"value":"Composer 1.5","label":"Composer 1.5"}]}]
        }]}}"""
        val catalog = HostApi(OneShotTransport(200, body)).catalog(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
        )
        assertEquals("Composer 1.5", catalog.agents.single().sessionConfig.single().value)
    }

    @Test
    fun catalogReadsAcpGroupedCursorModelOption() {
        val body = """{"data":{"projects":[],"workspaces":[],"agents":[{
          "id":"cursor",
          "ready":true,
          "sessionConfig":[{
            "id":"model",
            "name":"Model",
            "category":"model",
            "kind":{"select":{
              "currentValue":"composer-1.5",
              "options":{"grouped":[{"name":"Cursor","options":[
                {"value":"composer-1.5","name":"Composer 1.5"},
                {"value":"gpt-5","name":"GPT-5"}
              ]}]}
            }}
          }]
        }]}}"""
        val catalog = HostApi(OneShotTransport(200, body)).catalog(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
        )
        val option = catalog.agents.single().sessionConfig.single()
        assertEquals("model", option.key)
        assertEquals("composer-1.5", option.value)
        assertEquals(listOf("Composer 1.5", "GPT-5"), option.choices.map { it.label })
    }

    @Test
    fun catalogParsesUsableAndLifecycle() {
        val body = """{"data":{"projects":[],"workspaces":[],"agents":[
          {"id":"grok","ready":true,"usable":true,"lifecycle":"ready","authentication":"account"},
          {"id":"claude_code","ready":true,"usable":false,"lifecycle":"needs_auth","authentication":"not_logged_in"}
        ]}}"""
        val catalog = HostApi(OneShotTransport(200, body)).catalog(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
        )
        assertEquals(true, catalog.agents[0].usable)
        assertEquals("ready", catalog.agents[0].lifecycle)
        assertEquals(false, catalog.agents[1].usable)
        assertEquals("needs_auth", catalog.agents[1].lifecycle)
        assertEquals("未登录", agentAvailabilityLabel(catalog.agents[1].lifecycle, catalog.agents[1].authentication))
    }

    @Test
    fun createConversationReadsAgentIdFromSnakeCase() {
        val transport = OneShotTransport(
            200,
            """{"data":{"id":"c9","workspace_id":"w1","title":"demo","agent_id":"grok","status":"todo"}}""",
        )
        val created = HostApi(transport).createConversation(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
            "w1",
            "grok",
            "demo",
            null,
        )
        assertEquals("c9", created.id)
        assertEquals("grok", created.agentId)
        assertEquals("todo", created.status)
        assertTrue(transport.lastBody!!.contains("\"agentId\":\"grok\""))
        assertTrue(transport.lastBody!!.contains("\"workspaceId\":\"w1\""))
    }

    @Test
    fun submitInputWrapsConversationPayload() {
        val transport = OneShotTransport(200, """{"data":null}""")
        HostApi(transport).submitInput(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
            "c1",
            "grok",
            "w1",
            "你好",
        )
        val body = transport.lastBody!!
        assertTrue(body.contains("\"conversationId\":\"c1\""))
        assertTrue(body.contains("\"agentId\":\"grok\""))
        assertTrue(body.contains("\"workspaceId\":\"w1\""))
        assertTrue(body.contains("\"text\":\"你好\""))
    }

    @Test
    fun listSlashCommandsParsesSkillPayload() {
        val body = """{"data":[{"name":"office-xlsx","description":"Excel","kind":"skill","sourceKind":"skill","sourceId":"/tmp/office-xlsx","value":"/skill:/tmp/office-xlsx:office-xlsx"}]}"""
        val commands = HostApi(OneShotTransport(200, body)).listSlashCommands(
            HostOrigin.parse("http://127.0.0.1:17891"),
            "token",
            "grok",
            "w1",
        )
        assertEquals("office-xlsx", commands.single().name)
        assertEquals("skill:/tmp/office-xlsx:office-xlsx", commands.single().key)
        assertEquals("/skill:/tmp/office-xlsx:office-xlsx", commands.single().value)
    }
}

private class OneShotTransport(
    private val status: Int,
    private val body: String,
) : HttpTransport {
    var lastBody: String? = null

    override fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): HttpExchange {
        lastBody = body
        return HttpExchange(status, this.body)
    }
}
