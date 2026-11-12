package dev.vibex.companion.app.data

import android.content.Context
import android.os.Build
import dev.vibex.companion.asArray
import dev.vibex.companion.asBool
import dev.vibex.companion.asNumber
import dev.vibex.companion.asObject
import dev.vibex.companion.asText
import dev.vibex.companion.encodeObject
import dev.vibex.remote.v1.JsonValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

data class VibeXAccountUser(
    val email: String = "",
    val username: String = "",
    val displayName: String = "",
    val tier: String = "regular",
    val createdAt: String = "",
) {
    val resolvedDisplayName: String
        get() = displayName.trim().ifEmpty {
            username.trim().ifEmpty {
                email.substringBefore("@").trim().ifEmpty { "VibeX User" }
            }
        }

    val avatarInitial: String
        get() = resolvedDisplayName.firstOrNull()?.uppercaseChar()?.toString() ?: "V"

    val accountIdentifier: String
        get() = username.trim().ifEmpty { email.trim() }
}

data class VibeXCreditGrant(
    val id: String,
    val source: String = "order",
    val orderNo: String = "",
    val note: String = "",
    val amountMicro: Long = 0L,
    val remainingMicro: Long = 0L,
    val createdAt: String = "",
    val expiresAt: String = "",
    val voided: Boolean = false,
) {
    fun isActive(nowIso: String): Boolean {
        if (voided) return false
        if (expiresAt.isNotBlank() && expiresAt <= nowIso) return false
        return remainingMicro > 0L
    }
}

data class VibeXModelUsage(
    val modelId: String,
    val costMicro: Long,
    val requests: Int,
    val tokens: Long,
)

data class VibeXUsage30d(
    val byModel: List<VibeXModelUsage> = emptyList(),
    val totalRecords: Int = 0,
    val modelNames: Map<String, String> = emptyMap(),
)

data class VibeXAccountOverview(
    val user: VibeXAccountUser,
    val balanceMicro: Long = 0L,
    val serverNow: String = "",
    val grants: List<VibeXCreditGrant> = emptyList(),
    val usage30d: VibeXUsage30d = VibeXUsage30d(),
) {
    val activeGrants: List<VibeXCreditGrant>
        get() {
            val now = serverNow.ifBlank { Instant.now().toString() }
            return grants.filter { it.isActive(now) }
        }

    val activeTotalMicro: Long
        get() = activeGrants.sumOf { it.amountMicro }

    val activeRemainingMicro: Long
        get() = activeGrants.sumOf { it.remainingMicro }

    val remainingRatio: Float
        get() {
            if (activeTotalMicro > 0L) {
                return (activeRemainingMicro.toDouble() / activeTotalMicro.toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            }
            return if (balanceMicro > 0L) 1f else 0f
        }

    val hasActiveCreditsOrTier: Boolean
        get() = user.tier != "regular" || balanceMicro > 0L || activeGrants.isNotEmpty()

    val planDisplayName: String
        get() = when (user.tier) {
            "subscriber" -> "VibeX 订阅会员"
            "sponsor" -> "VibeX 赞助会员"
            "beta" -> "VibeX Beta 计划"
            "admin" -> "VibeX 管理员"
            else -> if (hasActiveCreditsOrTier) "按量付费 · 已激活" else "VibeX 免费版"
        }
}

data class VibeXDeviceLogin(
    val requestId: String,
    val verifier: String,
    val userCode: String,
    val verifyUrl: String,
    val expiresIn: Int,
    val interval: Int,
)

data class VibeXSyncableHostProfile(
    val syncKey: String = "",
    val profileId: String,
    val hostId: String = "",
    val name: String,
    val origin: String,
    val provisionKind: String = "http",
    val deviceId: String = "",
    val accessToken: String = "",
    val lastConnectedAt: String = "",
    val updatedAt: String = "",
)

data class VibeXAccountHostConfigItem(
    val hostId: String,
    val hostName: String,
    val kind: String = "local",
    val osType: String = "",
    val osArch: String = "",
    val enabledAgentsCount: Int = 0,
) {
    val platformBadge: String?
        get() {
            val rawOs = osType.trim()
            val rawArch = osArch.trim()
            if (rawOs.isEmpty() && rawArch.isEmpty()) return null
            val osLabel = when (rawOs.lowercase(Locale.ROOT)) {
                "macos", "darwin" -> "macOS"
                "windows" -> "Windows"
                "linux" -> "Linux"
                else -> rawOs
            }
            return if (osLabel.isNotEmpty() && rawArch.isNotEmpty()) {
                "$osLabel · $rawArch"
            } else {
                osLabel.ifEmpty { rawArch }
            }
        }
}

class VibeXAccountException(val code: String) : Exception(code)

class VibeXAccountRepository(context: Context) {
    companion object {
        const val SITE_BASE_URL = "https://vibex.xforever.xin"
        private const val PREF_TOKEN_KEY = "vibex.account.token"

        fun formatCredits(micro: Long): String {
            return String.format(Locale.US, "%.2f", micro.toDouble() / 1_000_000.0)
        }

        fun formatCompact(value: Long): String {
            return when {
                value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
                value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
                value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
                else -> value.toString()
            }
        }
    }

    private val prefs = context.getSharedPreferences("vibex_account_prefs", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getToken(): String? = prefs.getString(PREF_TOKEN_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setToken(token: String?) {
        val clean = token?.trim().orEmpty()
        if (clean.isEmpty()) {
            prefs.edit().remove(PREF_TOKEN_KEY).apply()
        } else {
            prefs.edit().putString(PREF_TOKEN_KEY, clean).apply()
        }
    }

    suspend fun startLogin(): VibeXDeviceLogin = withContext(Dispatchers.IO) {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "VibeX Android" }
        val payload = encodeObject(
            mapOf(
                "deviceName" to deviceName,
                "hostId" to "",
            ),
        )
        val obj = requestObject("/api/auth/desktop/start", "POST", payload, token = null)
        VibeXDeviceLogin(
            requestId = obj.value["requestId"]?.asText().orEmpty(),
            verifier = obj.value["verifier"]?.asText().orEmpty(),
            userCode = obj.value["userCode"]?.asText().orEmpty(),
            verifyUrl = obj.value["verifyUrl"]?.asText().orEmpty(),
            expiresIn = obj.value["expiresIn"]?.asNumber()?.toInt() ?: 300,
            interval = (obj.value["interval"]?.asNumber()?.toInt() ?: 3).coerceAtLeast(2),
        )
    }

    suspend fun pollLogin(login: VibeXDeviceLogin): Boolean = withContext(Dispatchers.IO) {
        val payload = encodeObject(
            mapOf(
                "requestId" to login.requestId,
                "verifier" to login.verifier,
            ),
        )
        val obj = requestObject("/api/auth/desktop/token", "POST", payload, token = null)
        val status = obj.value["status"]?.asText().orEmpty()
        val token = obj.value["token"]?.asText()?.trim().orEmpty()
        if (status == "approved" && token.isNotEmpty()) {
            setToken(token)
            return@withContext true
        }
        if (status == "pending") {
            return@withContext false
        }
        throw VibeXAccountException(status.ifEmpty { "failed" })
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val old = getToken()
        setToken(null)
        if (!old.isNullOrEmpty()) {
            runCatching {
                requestObject("/api/auth/logout", "POST", encodeObject(emptyMap()), token = old)
            }
        }
    }

    suspend fun fetchOverview(): VibeXAccountOverview = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw VibeXAccountException("signed_out")
        val obj = requestObject("/api/modelrouter/desktop", "GET", null, token = token)
        val userObj = obj.value["user"]?.asObject()
        val user = VibeXAccountUser(
            email = userObj?.value?.get("email")?.asText().orEmpty(),
            username = userObj?.value?.get("username")?.asText().orEmpty(),
            displayName = userObj?.value?.get("displayName")?.asText().orEmpty(),
            tier = userObj?.value?.get("tier")?.asText()?.ifBlank { "regular" } ?: "regular",
            createdAt = userObj?.value?.get("createdAt")?.asText().orEmpty(),
        )
        val balanceMicro = obj.value["balanceMicro"]?.asNumber()?.toLong() ?: 0L
        val serverNow = obj.value["serverNow"]?.asText().orEmpty()
        val grants = obj.value["grants"]?.asArray()?.value.orEmpty().mapNotNull { item ->
            val g = item.asObject() ?: return@mapNotNull null
            val id = g.value["id"]?.asText() ?: return@mapNotNull null
            VibeXCreditGrant(
                id = id,
                source = g.value["source"]?.asText().orEmpty(),
                orderNo = g.value["orderNo"]?.asText().orEmpty(),
                note = g.value["note"]?.asText().orEmpty(),
                amountMicro = g.value["amountMicro"]?.asNumber()?.toLong() ?: 0L,
                remainingMicro = g.value["remainingMicro"]?.asNumber()?.toLong() ?: 0L,
                createdAt = g.value["createdAt"]?.asText().orEmpty(),
                expiresAt = g.value["expiresAt"]?.asText().orEmpty(),
                voided = g.value["voided"]?.asBool() ?: false,
            )
        }
        val usageObj = obj.value["usage30d"]?.asObject()
        val byModel = usageObj?.value?.get("byModel")?.asArray()?.value.orEmpty().mapNotNull { item ->
            val m = item.asObject() ?: return@mapNotNull null
            val modelId = m.value["modelId"]?.asText() ?: return@mapNotNull null
            VibeXModelUsage(
                modelId = modelId,
                costMicro = m.value["costMicro"]?.asNumber()?.toLong() ?: 0L,
                requests = m.value["requests"]?.asNumber()?.toInt() ?: 0,
                tokens = m.value["tokens"]?.asNumber()?.toLong() ?: 0L,
            )
        }
        val modelNames = mutableMapOf<String, String>()
        usageObj?.value?.get("modelNames")?.asObject()?.value?.forEach { (k, v) ->
            v.asText()?.let { modelNames[k] = it }
        }
        obj.value["models"]?.asArray()?.value.orEmpty().forEach { item ->
            val m = item.asObject() ?: return@forEach
            val id = m.value["id"]?.asText() ?: return@forEach
            val name = m.value["name"]?.asText() ?: return@forEach
            if (!modelNames.containsKey(id)) {
                modelNames[id] = name
            }
        }
        VibeXAccountOverview(
            user = user,
            balanceMicro = balanceMicro,
            serverNow = serverNow,
            grants = grants,
            usage30d = VibeXUsage30d(
                byModel = byModel,
                totalRecords = usageObj?.value?.get("totalRecords")?.asNumber()?.toInt() ?: 0,
                modelNames = modelNames,
            ),
        )
    }

    suspend fun fetchHostConfigs(): List<VibeXAccountHostConfigItem> = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw VibeXAccountException("signed_out")
        val obj = requestObject("/api/account/desktop/host-configs", "GET", null, token = token)
        val list = obj.value["hosts"]?.asArray()?.value
            ?: obj.value["records"]?.asArray()?.value
            ?: emptyList()
        val results = mutableListOf<VibeXAccountHostConfigItem>()
        for (entry in list) {
            val rawObj = entry.asObject() ?: continue
            val snap = rawObj.value["snapshot"]?.asObject() ?: rawObj
            val hostId = snap.value["hostId"]?.asText()?.trim().orEmpty()
            if (hostId.isEmpty() || results.any { it.hostId == hostId }) continue
            val hostName = snap.value["hostName"]?.asText()?.trim().orEmpty().ifEmpty { "VibeX Desktop" }
            val kind = snap.value["kind"]?.asText()?.trim().orEmpty().ifEmpty { "local" }
            val osType = snap.value["osType"]?.asText()?.trim().orEmpty()
            val osArch = snap.value["osArch"]?.asText()?.trim().orEmpty()
            val enabledAgentsCount = snap.value["agents"]?.asArray()?.value.orEmpty().count { ag ->
                ag.asObject()?.value?.get("enabled")?.asBool() == true
            }
            results += VibeXAccountHostConfigItem(
                hostId = hostId,
                hostName = hostName,
                kind = kind,
                osType = osType,
                osArch = osArch,
                enabledAgentsCount = enabledAgentsCount,
            )
        }
        results
    }

    suspend fun fetchSavedHosts(): List<VibeXSyncableHostProfile> = withContext(Dispatchers.IO) {
        val token = getToken() ?: throw VibeXAccountException("signed_out")
        val obj = requestObject("/api/account/desktop/saved-hosts", "GET", null, token = token)
        parseSavedHostsList(obj.value["savedHosts"])
    }

    suspend fun putSavedHosts(profiles: List<VibeXSyncableHostProfile>): List<VibeXSyncableHostProfile> =
        withContext(Dispatchers.IO) {
            val token = getToken() ?: throw VibeXAccountException("signed_out")
            val body = encodeObject(
                mapOf(
                    "profiles" to profiles.map { p ->
                        val map = mutableMapOf<String, Any?>(
                            "profileId" to p.profileId,
                            "name" to p.name,
                            "origin" to p.origin,
                            "provisionKind" to p.provisionKind,
                        )
                        if (p.syncKey.isNotBlank()) map["syncKey"] = p.syncKey
                        if (p.hostId.isNotBlank()) map["hostId"] = p.hostId
                        if (p.accessToken.isNotBlank()) {
                            map["credential"] = mapOf(
                                "deviceId" to p.deviceId.ifBlank { p.profileId },
                                "accessToken" to p.accessToken,
                            )
                        }
                        if (p.lastConnectedAt.isNotBlank()) map["lastConnectedAt"] = p.lastConnectedAt
                        if (p.updatedAt.isNotBlank()) map["updatedAt"] = p.updatedAt
                        map
                    },
                ),
            )
            val obj = requestObject("/api/account/desktop/saved-hosts", "PUT", body, token = token)
            parseSavedHostsList(obj.value["savedHosts"]).ifEmpty { profiles }
        }

    suspend fun deleteSavedHost(profileId: String, hostId: String?, origin: String) =
        withContext(Dispatchers.IO) {
            val token = getToken() ?: return@withContext
            val body = encodeObject(
                buildMap {
                    put("profileId", profileId)
                    put("origin", origin)
                    if (!hostId.isNullOrBlank()) put("hostId", hostId)
                },
            )
            runCatching {
                requestObject("/api/account/desktop/saved-hosts", "DELETE", body, token = token)
            }
        }

    private fun parseSavedHostsList(node: JsonValue?): List<VibeXSyncableHostProfile> {
        return node?.asArray()?.value.orEmpty().mapNotNull { item ->
            val o = item.asObject() ?: return@mapNotNull null
            val profileId = o.value["profileId"]?.asText()?.trim().orEmpty()
            val origin = o.value["origin"]?.asText()?.trim().orEmpty()
            if (profileId.isEmpty() && origin.isEmpty()) return@mapNotNull null
            val cred = o.value["credential"]?.asObject()
            VibeXSyncableHostProfile(
                syncKey = o.value["syncKey"]?.asText()?.trim().orEmpty(),
                profileId = profileId.ifEmpty { origin },
                hostId = o.value["hostId"]?.asText()?.trim().orEmpty(),
                name = o.value["name"]?.asText()?.trim().orEmpty().ifEmpty { "VibeX 电脑" },
                origin = origin,
                provisionKind = o.value["provisionKind"]?.asText()?.trim().orEmpty().ifEmpty { "http" },
                deviceId = cred?.value?.get("deviceId")?.asText()?.trim().orEmpty(),
                accessToken = cred?.value?.get("accessToken")?.asText()?.trim().orEmpty(),
                lastConnectedAt = o.value["lastConnectedAt"]?.asText()?.trim().orEmpty(),
                updatedAt = o.value["updatedAt"]?.asText()?.trim().orEmpty(),
            )
        }
    }

    private fun requestObject(
        path: String,
        method: String,
        jsonBody: String?,
        token: String?,
    ): JsonValue.Object {
        val url = "$SITE_BASE_URL${if (path.startsWith("/")) path else "/$path"}"
        val builder = Request.Builder().url(url)
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((jsonBody ?: "{}").toRequestBody(mediaType))
            "PUT" -> builder.put((jsonBody ?: "{}").toRequestBody(mediaType))
            "DELETE" -> {
                if (jsonBody != null) {
                    builder.delete(jsonBody.toRequestBody(mediaType))
                } else {
                    builder.delete()
                }
            }
        }
        val response = try {
            http.newCall(builder.build()).execute()
        } catch (_: Throwable) {
            throw VibeXAccountException("network")
        }
        response.use { resp ->
            if (resp.code == 401) {
                if (!token.isNullOrBlank()) setToken(null)
                throw VibeXAccountException("signed_out")
            }
            if (resp.code == 404 || resp.code == 405 || resp.code == 501) {
                throw VibeXAccountException("unsupported")
            }
            val raw = resp.body?.string().orEmpty()
            val parsed = runCatching { JsonValue.parse(raw) as? JsonValue.Object }.getOrNull()
            if (!resp.isSuccessful || parsed == null) {
                val code = parsed?.value?.get("error")?.asText().orEmpty().ifEmpty { "http_${resp.code}" }
                throw VibeXAccountException(code)
            }
            return parsed
        }
    }
}
