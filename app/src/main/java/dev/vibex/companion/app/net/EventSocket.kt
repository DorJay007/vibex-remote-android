package dev.vibex.companion.app.net

import android.util.Base64
import dev.vibex.companion.E2eeSessionManager
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
        .socketFactory(TcpNoDelaySocketFactory)
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) {
    private var socket: WebSocket? = null
    private var generation = 0
    @Volatile
    private var activeE2eeSession: E2eeSessionManager.SessionState? = null
    @Volatile
    private var lastPingSentNs: Long = 0L

    fun connect(
        origin: String,
        token: String,
        onOpen: () -> Unit = {},
        onPongRttMs: (Long) -> Unit = {},
        onEvent: (String, RemoteEvent) -> Unit,
        onSnapshot: (String, JsonValue) -> Unit,
        onClosed: (String?) -> Unit,
    ) {
        val session = ++generation
        socket?.cancel()
        socket = null
        val ws = origin.replaceFirst(Regex("^http"), "ws") + "/api/v1/ws"
        val encoded = Base64.encodeToString(token.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val e2ee = E2eeSessionManager.session(origin)
        activeE2eeSession = e2ee
        val protocols = if (e2ee != null) {
            "vibex.v1, vibex.token.$encoded, vibex.e2ee.${e2ee.sessionId}"
        } else {
            "vibex.v1, vibex.token.$encoded"
        }
        val reqBuilder = Request.Builder()
            .url(ws)
            .header("Sec-WebSocket-Protocol", protocols)
            .header(E2eeSessionManager.HEADER_CLIENT_PUB, E2eeSessionManager.clientPublicKeyBase64)
        if (e2ee != null) {
            reqBuilder.header(E2eeSessionManager.HEADER_E2EE, E2eeSessionManager.VERSION)
            reqBuilder.header(E2eeSessionManager.HEADER_SESSION, e2ee.sessionId)
            runCatching {
                val encAuth = E2eeSessionManager.encryptString(
                    e2ee.aesKey,
                    E2eeSessionManager.AAD_AUTH,
                    "Bearer $token",
                )
                reqBuilder.header(E2eeSessionManager.HEADER_AUTH, encAuth)
            }
        }
        val request = reqBuilder.build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            private fun live(): Boolean = session == generation
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (live()) {
                    E2eeSessionManager.ingestHandshake(
                        origin,
                        response.header(E2eeSessionManager.HEADER_SESSION),
                        response.header(E2eeSessionManager.HEADER_SERVER_PUB),
                    )?.let { activeE2eeSession = it }
                    ping()
                    onOpen()
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!live()) return
                val outer = runCatching { JsonValue.parse(text) as JsonValue.Object }.getOrNull() ?: return
                val root = if (outer.value["type"]?.asText() == "e2ee") {
                    val cipherB64 = outer.value["d"]?.asText() ?: return
                    val currentE2ee = activeE2eeSession ?: E2eeSessionManager.session(origin) ?: return
                    val plain = runCatching {
                        E2eeSessionManager.decryptString(currentE2ee.aesKey, E2eeSessionManager.AAD_WS, cipherB64)
                    }.getOrNull() ?: return
                    runCatching { JsonValue.parse(plain) as JsonValue.Object }.getOrNull() ?: return
                } else {
                    outer
                }
                when (root.value["type"]?.asText()) {
                    "pong" -> {
                        val sentNs = lastPingSentNs
                        if (sentNs > 0L) {
                            val rttMs = ((System.nanoTime() - sentNs) / 1_000_000L).coerceAtLeast(1L)
                            onPongRttMs(rttMs)
                        }
                    }
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
                        if (code.equals("UNAUTHORIZED", ignoreCase = true) && live()) {
                            generation += 1
                            socket?.cancel()
                            socket = null
                            onClosed("auth")
                        }
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

    private fun sendFrame(plainJson: String) {
        val e2ee = activeE2eeSession
        if (e2ee != null) {
            val encrypted = runCatching {
                E2eeSessionManager.encryptString(e2ee.aesKey, E2eeSessionManager.AAD_WS, plainJson)
            }.getOrNull()
            if (encrypted != null) {
                socket?.send("""{"type":"e2ee","d":"$encrypted"}""")
                return
            }
        }
        socket?.send(plainJson)
    }

    fun attach(conversationId: String, afterSequence: Long): String {
        val id = UUID.randomUUID().toString()
        val frame = """{"type":"attach","request":{"subscription_id":"$id","resource":"conversation","conversation_id":"$conversationId","after_sequence":$afterSequence}}"""
        sendFrame(frame)
        return id
    }

    fun ping() {
        lastPingSentNs = System.nanoTime()
        sendFrame("""{"type":"ping"}""")
    }

    fun close() {
        generation += 1
        socket?.cancel()
        socket = null
    }
}
