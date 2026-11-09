package dev.vibex.companion.app.net

import dev.vibex.companion.E2eeSessionManager
import dev.vibex.companion.HttpExchange
import dev.vibex.companion.HttpTransport
import dev.vibex.companion.PairingClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

object TcpNoDelaySocketFactory : SocketFactory() {
    private val delegate = getDefault()
    private fun configure(socket: Socket): Socket {
        runCatching { socket.tcpNoDelay = true }
        return socket
    }
    override fun createSocket(): Socket = configure(delegate.createSocket())
    override fun createSocket(host: String?, port: Int): Socket = configure(delegate.createSocket(host, port))
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        configure(delegate.createSocket(host, port, localHost, localPort))
    override fun createSocket(host: InetAddress?, port: Int): Socket = configure(delegate.createSocket(host, port))
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        configure(delegate.createSocket(address, port, localAddress, localPort))
}

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .socketFactory(TcpNoDelaySocketFactory)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {
    private val fastLanClient: OkHttpClient = client.newBuilder()
        .connectTimeout(900, TimeUnit.MILLISECONDS)
        .build()

    private fun isLocalOrPrivateLan(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://127.") ||
            lower.startsWith("http://localhost") ||
            lower.startsWith("http://192.168.") ||
            lower.startsWith("http://10.") ||
            Regex("""^http://172\.(1[6-9]|2\d|3[0-1])\.""").containsMatchIn(lower)
    }

    override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): HttpExchange {
        val first = executeOnce(method, url, headers, body, allowBootstrap = true)
        if (first.status == 412) {
            return executeOnce(method, url, headers, body, allowBootstrap = false)
        }
        return first
    }

    private fun executeOnce(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        allowBootstrap: Boolean,
    ): HttpExchange {
        val lower = url.lowercase()
        val isPrivateLan = isLocalOrPrivateLan(url)
        val originKey = E2eeSessionManager.originKey(url)
        if (allowBootstrap && E2eeSessionManager.session(originKey) == null && !lower.endsWith("/health") && !lower.endsWith("/api/v1/e2ee/handshake")) {
            runCatching {
                val hsReq = Request.Builder()
                    .url("$originKey/api/v1/e2ee/handshake")
                    .header(E2eeSessionManager.HEADER_CLIENT_PUB, E2eeSessionManager.clientPublicKeyBase64)
                    .get()
                    .build()
                (if (isPrivateLan) fastLanClient else client).newCall(hsReq).execute().use { hsResp ->
                    E2eeSessionManager.ingestHandshake(
                        originKey,
                        hsResp.header(E2eeSessionManager.HEADER_SESSION),
                        hsResp.header(E2eeSessionManager.HEADER_SERVER_PUB),
                    )
                }
            }
        }

        val mutableHeaders = LinkedHashMap(headers)
        mutableHeaders[E2eeSessionManager.HEADER_CLIENT_PUB] = E2eeSessionManager.clientPublicKeyBase64

        val activeSession = E2eeSessionManager.session(originKey)
        var wireBody = body
        var contentType = "application/json; charset=utf-8"

        if (activeSession != null) {
            mutableHeaders[E2eeSessionManager.HEADER_E2EE] = E2eeSessionManager.VERSION
            mutableHeaders[E2eeSessionManager.HEADER_SESSION] = activeSession.sessionId
            val authEntry = mutableHeaders.entries.firstOrNull { it.key.equals("authorization", ignoreCase = true) }
            if (authEntry != null) {
                runCatching {
                    val encAuth = E2eeSessionManager.encryptString(
                        activeSession.aesKey,
                        E2eeSessionManager.AAD_AUTH,
                        authEntry.value,
                    )
                    mutableHeaders[E2eeSessionManager.HEADER_AUTH] = encAuth
                    mutableHeaders[authEntry.key] = "Bearer e2ee"
                }
            }
            if (!body.isNullOrEmpty()) {
                runCatching {
                    wireBody = E2eeSessionManager.encryptString(
                        activeSession.aesKey,
                        E2eeSessionManager.AAD_REQ,
                        body,
                    )
                    contentType = "text/plain; charset=utf-8"
                }
            }
        }

        val builder = Request.Builder().url(url)
        mutableHeaders.forEach { (k, v) -> builder.header(k, v) }
        val media = contentType.toMediaType()
        val requestBody = wireBody?.toRequestBody(media)
        builder.method(method, if (method == "GET" || method == "DELETE") null else requestBody ?: "".toRequestBody(media))
        if (method == "DELETE" && wireBody != null) {
            builder.method("DELETE", wireBody!!.toRequestBody(media))
        }
        val activeClient = if (isPrivateLan) fastLanClient else client
        activeClient.newCall(builder.build()).execute().use { response ->
            val updatedSession = E2eeSessionManager.ingestHandshake(
                originKey,
                response.header(E2eeSessionManager.HEADER_SESSION),
                response.header(E2eeSessionManager.HEADER_SERVER_PUB),
            ) ?: activeSession
            val rawRespBody = response.body?.string().orEmpty()
            val respIsE2ee = response.header(E2eeSessionManager.HEADER_E2EE)
                ?.equals(E2eeSessionManager.VERSION, ignoreCase = true) == true
            if (respIsE2ee && rawRespBody.isNotEmpty() && updatedSession != null) {
                val decrypted = runCatching {
                    E2eeSessionManager.decryptString(updatedSession.aesKey, E2eeSessionManager.AAD_RESP, rawRespBody)
                }.getOrNull()
                if (decrypted != null) {
                    return HttpExchange(response.code, decrypted)
                }
            }
            return HttpExchange(response.code, rawRespBody)
        }
    }
}
