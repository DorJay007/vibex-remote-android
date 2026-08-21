package dev.vibex.companion.app.net

import dev.vibex.companion.HttpExchange
import dev.vibex.companion.HttpTransport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {
    override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): HttpExchange {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val media = "application/json; charset=utf-8".toMediaType()
        val requestBody = body?.toRequestBody(media)
        builder.method(method, if (method == "GET" || method == "DELETE") null else requestBody ?: "".toRequestBody(media))
        if (method == "DELETE" && body != null) {
            builder.method("DELETE", body.toRequestBody(media))
        }
        client.newCall(builder.build()).execute().use { response ->
            return HttpExchange(response.code, response.body?.string().orEmpty())
        }
    }
}
