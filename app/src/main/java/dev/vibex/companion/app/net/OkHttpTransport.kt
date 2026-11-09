package dev.vibex.companion.app.net

import dev.vibex.companion.HttpExchange
import dev.vibex.companion.HttpTransport
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
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val media = "application/json; charset=utf-8".toMediaType()
        val requestBody = body?.toRequestBody(media)
        builder.method(method, if (method == "GET" || method == "DELETE") null else requestBody ?: "".toRequestBody(media))
        if (method == "DELETE" && body != null) {
            builder.method("DELETE", body.toRequestBody(media))
        }
        val activeClient = if (isLocalOrPrivateLan(url)) fastLanClient else client
        activeClient.newCall(builder.build()).execute().use { response ->
            return HttpExchange(response.code, response.body?.string().orEmpty())
        }
    }
}
