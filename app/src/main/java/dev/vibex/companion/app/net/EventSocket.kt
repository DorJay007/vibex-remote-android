package dev.vibex.companion.app.net

import android.util.Base64
import dev.vibex.companion.asObject
import dev.vibex.companion.asText
import dev.vibex.companion.longOrNull
import dev.vibex.remote.v1.JsonValue
import dev.vibex.remote.v1.RemoteEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

class EventSocket(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) {
    private var socket: WebSocket? = null
    private var generation = 0

    fun connect(
        origin: String,
        token: String,
        onOpen: () -> Unit = {},
        onEvent: (String, RemoteEvent) -> Unit,
        onSnapshot: (String, JsonValue) -> Unit,
        onClosed: (String?) -> Unit,
    ) {
        val session = ++generation
        socket?.cancel()
        socket = null
        val ws = origin.replaceFirst(Regex("^http"), "ws") + "/api/v1/ws"
        val encoded = Base64.encodeToString(token.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val request = Request.Builder()
            .url(ws)
            .header("Sec-WebSocket-Protocol", "vibex.v1, vibex.token.$encoded")
            .build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            private fun live(): Boolean = session == generation
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (live()) onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!live()) return
                val root = runCatching { JsonValue.parse(text) as JsonValue.Object }.getOrNull() ?: return
                when (root.value["type"]?.asText()) {
                    "event" -> {
                        val sub = root.value["subscription_id"]?.asText() ?: return
                        val ev = root.value["event"]?.asObject() ?: return
                        val sequence = ev.longOrNull("sequence") ?: return
                        val kind = ev.value["kind"]?.asText() ?: return
                        onEvent(sub, RemoteEvent(sequence, kind, ev.value["payload"] ?: JsonValue.Null))
                    }
                    "snapshot" -> {
                        val sub = root.value["subscription_id"]?.asText() ?: return
                        val snap = root.value["snapshot"]?.asObject() ?: return
                        onSnapshot(sub, snap.value["payload"] ?: JsonValue.Null)
                    }
                    "error" -> {
                        val err = root.value["error"]?.asObject()
                        val code = err?.value?.get("code")?.asText()
                        if (code == "UNAUTHORIZED" && live()) onClosed("auth")
                    }
                    "detached" -> if (live()) onClosed("detached")
                    else -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (live()) onClosed(t.message)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (live()) onClosed(reason)
            }
        })
    }

    fun attach(conversationId: String, afterSequence: Long): String {
        val id = UUID.randomUUID().toString()
        val frame = """{"type":"attach","request":{"subscription_id":"$id","resource":"conversation","conversation_id":"$conversationId","after_sequence":$afterSequence}}"""
        socket?.send(frame)
        return id
    }

    fun ping() {
        socket?.send("""{"type":"ping"}""")
    }

    fun close() {
        generation += 1
        socket?.cancel()
        socket = null
    }
}
