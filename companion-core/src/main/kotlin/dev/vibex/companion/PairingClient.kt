package dev.vibex.companion

import dev.vibex.remote.v1.DeviceCredential
import dev.vibex.remote.v1.JsonValue
import dev.vibex.remote.v1.RedeemPairingRequest
import dev.vibex.remote.v1.ServerCapabilities
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class HttpExchange(
    val status: Int,
    val body: String,
)

fun interface HttpTransport {
    fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): HttpExchange
}

class PairingException(message: String) : RuntimeException(message)

class MobileDeviceConflictException(val existingDeviceName: String) :
    RuntimeException("当前电脑已连接手机「$existingDeviceName」")

/** Why the host refused an account-linked connect request (`details.reason`). */
enum class AccountLinkFailure(val reason: String) {
    AccountMismatch("account_mismatch"),
    AccountTokenInvalid("account_token_invalid"),
    HostAccountSignedOut("host_account_signed_out"),
    VerificationUnavailable("account_verification_unavailable"),
    ;

    companion object {
        fun fromReason(reason: String?): AccountLinkFailure? = entries.firstOrNull { it.reason == reason }
    }
}

class AccountLinkException(val failure: AccountLinkFailure) : RuntimeException(failure.reason)

/** The desktop could not be reached, e.g. it is offline behind the cloud relay. */
class HostUnreachableException(status: Int) : RuntimeException("host unreachable ($status)")

data class CompanionSession(
    val origin: HostOrigin,
    val credential: DeviceCredential,
    val capabilities: ServerCapabilities,
)

data class DiscoveredLanHost(
    val origin: String,
    val hostId: String?,
    val name: String,
)

data class DeviceConnectRequestResult(
    val requestId: String,
    val hostId: String,
    val hostName: String?,
    val sasCode: String,
    val status: String,
    val credential: DeviceCredential?,
)

class PairingClient(
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val protocolVersion: String = CLIENT_PROTOCOL_VERSION,
) {
    private fun throwIfMobileConflict(status: Int, body: String) {
        if (status != 409) return
        val details = errorDetails(body) ?: return
        if (details.optionalText("reason") == "mobile_device_conflict") {
            val existingName = details.optionalText("existing_device_name")
                ?.takeIf { it.isNotBlank() }
                ?: "另一台手机"
            throw MobileDeviceConflictException(existingName)
        }
    }

    private fun throwIfAccountLinkFailure(body: String) {
        val failure = AccountLinkFailure.fromReason(errorDetails(body)?.optionalText("reason")) ?: return
        throw AccountLinkException(failure)
    }

    /** A relay answers 502/503/504 without an error envelope when the desktop is offline. */
    private fun throwIfRelayUnreachable(status: Int) {
        if (status in 502..504) throw HostUnreachableException(status)
    }

    private fun errorDetails(body: String): JsonValue.Object? {
        val root = runCatching { JsonValue.parse(body) as? JsonValue.Object }.getOrNull() ?: return null
        return root.value["details"] as? JsonValue.Object
    }

    fun redeem(
        origin: HostOrigin,
        pairingToken: String,
        deviceName: String = "Android Companion",
        replaceExistingMobile: Boolean = false,
    ): CompanionSession {
        require(pairingToken.isNotBlank()) { "Pairing token is required" }
        require(deviceName.isNotBlank()) { "Device name is required" }

        val redeemBody = JsonValue.Object(
            linkedMapOf(
                "pairing_token" to JsonValue.Text(pairingToken.trim()),
                "device_name" to JsonValue.Text(deviceName.trim()),
                "client_kind" to JsonValue.Text("mobile"),
                "replace_existing_mobile" to JsonValue.Bool(replaceExistingMobile),
            ),
        ).encode()
        val redeem = transport.execute(
            "POST",
            origin.resolve("/api/v1/auth/pairings/redeem"),
            mapOf(
                "content-type" to "application/json",
                PROTOCOL_HEADER to protocolVersion,
            ),
            redeemBody,
        )
        if (redeem.status !in 200..299) {
            throwIfMobileConflict(redeem.status, redeem.body)
            throw PairingException("Pairing redeem failed (${redeem.status})")
        }
        val credential = parseDeviceCredential(redeem.body)
        val extras = CompanionScopes.extras(credential.scopes)
        if (extras.isNotEmpty()) {
            throw PairingException(
                "Host granted non-companion scopes: ${extras.sorted().joinToString(", ")}",
            )
        }

        val capabilitiesResponse = transport.execute(
            "GET",
            origin.resolve("/api/v1/capabilities"),
            mapOf(
                "authorization" to "Bearer ${credential.access_token}",
                PROTOCOL_HEADER to protocolVersion,
            ),
            null,
        )
        if (capabilitiesResponse.status !in 200..299) {
            throw PairingException("Capabilities check failed (${capabilitiesResponse.status})")
        }
        val capabilities = parseServerCapabilities(capabilitiesResponse.body)
        if (protocolMajor(capabilities.protocol_version) != protocolMajor(protocolVersion)) {
            throw PairingException(
                "Protocol ${capabilities.protocol_version} is incompatible with $protocolVersion",
            )
        }
        return CompanionSession(origin, credential, capabilities)
    }

    fun requestConnect(
        origin: HostOrigin,
        deviceName: String = "Android Companion",
        publicKeyEd25519: String? = null,
        sessionNonce: String? = null,
        replaceExistingMobile: Boolean = false,
        accountToken: String? = null,
    ): DeviceConnectRequestResult {
        val fields = linkedMapOf<String, JsonValue>(
            "device_name" to JsonValue.Text(deviceName.trim().ifBlank { "Android Companion" }),
            "client_kind" to JsonValue.Text("mobile"),
            "replace_existing_mobile" to JsonValue.Bool(replaceExistingMobile),
        )
        publicKeyEd25519?.trim()?.takeIf { it.isNotEmpty() }?.let {
            fields["public_key_ed25519"] = JsonValue.Text(it)
        }
        sessionNonce?.trim()?.takeIf { it.isNotEmpty() }?.let {
            fields["session_nonce"] = JsonValue.Text(it)
        }
        accountToken?.trim()?.takeIf { it.isNotEmpty() }?.let {
            fields["account_token"] = JsonValue.Text(it)
        }
        val response = transport.execute(
            "POST",
            origin.resolve("/api/v1/auth/connect-requests"),
            mapOf(
                "content-type" to "application/json",
                PROTOCOL_HEADER to protocolVersion,
            ),
            JsonValue.Object(fields).encode(),
        )
        if (response.status !in 200..299) {
            throwIfMobileConflict(response.status, response.body)
            throwIfAccountLinkFailure(response.body)
     throwIfRelayUnreachable(response.status)
     throw PairingException("发起连接请求失败 (${response.status})")
        }
        return parseConnectRequestResult(response.body)
    }

    fun pollConnect(
        origin: HostOrigin,
        requestId: String,
    ): DeviceConnectRequestResult {
        val response = transport.execute(
            "GET",
            origin.resolve("/api/v1/auth/connect-requests/${requestId.trim()}"),
            mapOf(PROTOCOL_HEADER to protocolVersion),
            null,
        )
        if (response.status !in 200..299) {
     throwIfRelayUnreachable(response.status)
     throw PairingException("查询连接确认状态失败 (${response.status})")
        }
        return parseConnectRequestResult(response.body)
    }

    private fun parseConnectRequestResult(body: String): DeviceConnectRequestResult {
        val root = JsonValue.parse(body) as JsonValue.Object
        val credObj = root.value["credential"] as? JsonValue.Object
        return DeviceConnectRequestResult(
            requestId = root.text("request_id"),
            hostId = root.optionalText("host_id") ?: "",
            hostName = root.optionalText("host_name"),
            sasCode = root.text("sas_code"),
            status = root.text("status"),
            credential = credObj?.let { parseDeviceCredentialObject(it) },
        )
    }

    companion object {
        const val CLIENT_PROTOCOL_VERSION = "1.0"
        const val PROTOCOL_HEADER = "x-vibex-protocol-version"
        const val DEFAULT_OFFICIAL_RELAY_PORT = 443
        val DEFAULT_OFFICIAL_RELAY_BASE: String =
            System.getenv("VIBEX_RELAY_URL")?.trim()?.takeIf { it.isNotEmpty() } ?: "https://vibex.xforever.xin"
        const val HAPPY_EYEBALLS_LAN_WINDOW_MS = 250L

        private val rememberedRelayBases = java.util.concurrent.CopyOnWriteArraySet<String>(
            listOf(DEFAULT_OFFICIAL_RELAY_BASE).filter { it.isNotBlank() },
        )

        fun rememberRelayBase(originOrBaseUrl: String) {
            val trimmed = originOrBaseUrl.trim().trimEnd('/')
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return
            val base = if (trimmed.contains("/t/")) {
                trimmed.substringBefore("/t/")
            } else {
                val uri = runCatching { URI(trimmed) }.getOrNull() ?: return
                val host = uri.host ?: return
                val port = if (uri.port == -1) "" else ":${uri.port}"
                "${uri.scheme}://$host$port"
            }
            if (
                base.isNotBlank() &&
                !base.contains("127.0.0.1") &&
                !base.contains("10.0.2.2") &&
                !base.contains(":17890")
            ) {
                rememberedRelayBases.add(base)
            }
        }

        fun knownRelayBases(extraOrigins: Collection<String> = emptyList()): List<String> {
            for (origin in extraOrigins) {
                if (origin.contains("/t/")) {
                    rememberRelayBase(origin)
                }
            }
            val bases = LinkedHashSet<String>()
            System.getProperty("vibex.relay.url")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                rememberRelayBase(it)
            }
            bases.addAll(rememberedRelayBases)
            return bases.toList()
        }

        fun probeHostHealth(
            originUrl: String,
            timeoutMs: Int = 350,
            expectedHostId: String? = null,
        ): DiscoveredLanHost? {
            return runCatching {
                val origin = HostOrigin.parse(originUrl)
                val connection = URI(origin.resolve("/health")).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    E2eeSessionManager.HEADER_CLIENT_PUB,
                    E2eeSessionManager.clientPublicKeyBase64,
                )
                val code = connection.responseCode
                if (code !in 200..299) return null
                E2eeSessionManager.ingestHandshake(
                    origin.value,
                    connection.getHeaderField(E2eeSessionManager.HEADER_SESSION),
                    connection.getHeaderField(E2eeSessionManager.HEADER_SERVER_PUB),
                )
                val routedHostId = connection.getHeaderField("x-vibex-routed-host-id")?.trim()
                if (!expectedHostId.isNullOrBlank() && !routedHostId.isNullOrBlank() && routedHostId != expectedHostId) {
                    return null
                }
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JsonValue.parse(body) as? JsonValue.Object ?: return null
                if (root.optionalText("status") != "ok") return null
                val hostId = root.optionalText("host_id")
                if (!expectedHostId.isNullOrBlank() && !hostId.isNullOrBlank() && hostId != expectedHostId) {
                    return null
                }
                if (origin.value.contains("/t/")) {
                    rememberRelayBase(origin.value)
                }
                val name = root.optionalText("name") ?: origin.value.removePrefix("http://").removePrefix("https://")
                DiscoveredLanHost(
                    origin = origin.value,
                    hostId = hostId,
                    name = name,
                )
            }.getOrNull()
        }

        fun discoverLanHosts(port: Int = 17891): List<DiscoveredLanHost> {
            val candidates = LinkedHashSet<String>()
            val localPorts = (listOf(port, 17899) + (17891..17899)).distinct()
            for (localPort in localPorts) {
                candidates.add("http://127.0.0.1:$localPort")
                candidates.add("http://10.0.2.2:$localPort")
            }
            val lanPorts = listOf(port, 17899).distinct()

            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                for (iface in interfaces) {
                    val name = iface.name?.lowercase().orEmpty()
                    if (!iface.isUp || iface.isLoopback) continue
                    if (
                        name.startsWith("utun") ||
                        name.startsWith("tun") ||
                        name.startsWith("tap") ||
                        name.startsWith("ppp") ||
                        name.contains("clash") ||
                        name.contains("mihomo") ||
                        name.contains("surge")
                    ) {
                        continue
                    }
                    for (addr in iface.inetAddresses.toList()) {
                        if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                        val raw = addr.address
                        val b0 = raw[0].toInt() and 0xFF
                        val b1 = raw[1].toInt() and 0xFF
                        val b2 = raw[2].toInt() and 0xFF
                        val selfHost = raw[3].toInt() and 0xFF
                        if (b0 == 198 && (b1 == 18 || b1 == 19)) continue
                        val isPrivate =
                            b0 == 10 ||
                                (b0 == 172 && b1 in 16..31) ||
                                (b0 == 192 && b1 == 168)
                        if (!isPrivate) continue
                        val prefix = "$b0.$b1.$b2"
                        for (lanPort in lanPorts) {
                            for (host in 1..254) {
                                if (host == selfHost) continue
                                candidates.add("http://$prefix.$host:$lanPort")
                            }
                        }
                    }
                }
            }

            val pool = Executors.newFixedThreadPool(64)
            val localFound = try {
                val tasks = candidates.map { url ->
                    Callable { probeHostHealth(url, timeoutMs = 280) }
                }
                val futures = pool.invokeAll(tasks, 1500, TimeUnit.MILLISECONDS)
                val found = futures.mapNotNull { future ->
                    if (future.isDone && !future.isCancelled) {
                        runCatching { future.get() }.getOrNull()
                    } else {
                        null
                    }
                }
                found.distinctBy { it.hostId ?: it.origin }
            } finally {
                pool.shutdownNow()
            }
            return localFound
        }

        fun sha256Hex(input: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }

        fun resolveHostRendezvous(
            hostId: String,
            knownOrigins: Collection<String> = emptyList(),
            timeoutMs: Int = 2000,
        ): List<String> {
            val cleanId = hostId.trim()
            if (cleanId.isEmpty() || cleanId.startsWith("host:")) return emptyList()
            val bases = knownRelayBases(knownOrigins)
            if (bases.isEmpty()) return emptyList()

            val resolved = LinkedHashSet<String>()
            // 1. Deterministic multi-tenant path on every known domestic relay base (0ms)
            for (base in bases) {
                resolved.add("${base.trimEnd('/')}/t/$cleanId")
            }
            // 2. Domestic beacon lookup on each relay base
            val topicHash = sha256Hex("vibex-host-v1:$cleanId").take(32)
            val topic = "vbx-h-$topicHash"
            for (base in bases) {
                for (origin in queryDomesticBeaconOrigins(base, topic, expectedHostId = cleanId, timeoutMs = timeoutMs)) {
                    resolved.add(origin)
                }
            }
            return resolved.toList()
        }

        fun resolvePairingCodeRendezvous(
            connectionCode: String,
            knownOrigins: Collection<String> = emptyList(),
            timeoutMs: Int = 2000,
        ): List<String> {
            val cleanCode = connectionCode
                .trim()
                .replace("-", "")
                .replace(" ", "")
                .uppercase()
            if (cleanCode.length != 8) return emptyList()
            val bases = knownRelayBases(knownOrigins)
            if (bases.isEmpty()) return emptyList()
            val topicHash = sha256Hex("vibex-pair-code-v1:$cleanCode").take(32)
            val topic = "vbx-p-$topicHash"
            val resolved = LinkedHashSet<String>()
            for (base in bases) {
                for (origin in queryDomesticBeaconOrigins(base, topic, expectedHostId = null, timeoutMs = timeoutMs)) {
                    resolved.add(origin)
                }
            }
            return resolved.toList()
        }

        private fun queryDomesticBeaconOrigins(
            relayBase: String,
            topic: String,
            expectedHostId: String?,
            timeoutMs: Int,
        ): List<String> {
            return runCatching {
                val url = "${relayBase.trimEnd('/')}/v1/beacon/$topic"
                val conn = URI(url).toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.requestMethod = "GET"
                if (conn.responseCode !in 200..299) return emptyList()
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JsonValue.parse(body) as? JsonValue.Object ?: return emptyList()
                val entries = (root.value["entries"] as? JsonValue.Array)?.value.orEmpty()
                val origins = mutableListOf<String>()
                for (item in entries) {
                    val entryObj = item as? JsonValue.Object ?: continue
                    val payload = entryObj.value["payload"] as? JsonValue.Object ?: continue
                    val recordHostId = payload.optionalText("host_id")
                    if (expectedHostId != null && recordHostId != expectedHostId) continue
                    val origin = payload.optionalText("origin")?.trim().orEmpty()
                    if (origin.startsWith("http://") || origin.startsWith("https://")) {
                        origins.add(origin)
                    }
                }
                origins.distinct()
            }.getOrDefault(emptyList())
        }
    }
}

class UrlConnectionTransport : HttpTransport {
    override fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): HttpExchange {
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
        val isPrivateLan = lower.startsWith("http://192.168.") ||
            lower.startsWith("http://10.") ||
            lower.startsWith("http://172.") ||
            lower.startsWith("http://127.")

        val originKey = E2eeSessionManager.originKey(url)
        if (allowBootstrap && E2eeSessionManager.session(originKey) == null && !lower.endsWith("/health") && !lower.endsWith("/api/v1/e2ee/handshake")) {
            runCatching {
                val hsConn = URI("$originKey/api/v1/e2ee/handshake").toURL().openConnection() as HttpURLConnection
                hsConn.connectTimeout = if (isPrivateLan) 900 else 3000
                hsConn.readTimeout = if (isPrivateLan) 1500 else 3500
                hsConn.requestMethod = "GET"
                hsConn.setRequestProperty(E2eeSessionManager.HEADER_CLIENT_PUB, E2eeSessionManager.clientPublicKeyBase64)
                hsConn.responseCode
                E2eeSessionManager.ingestHandshake(
                    originKey,
                    hsConn.getHeaderField(E2eeSessionManager.HEADER_SESSION),
                    hsConn.getHeaderField(E2eeSessionManager.HEADER_SERVER_PUB),
                )
            }
        }

        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = if (isPrivateLan) 2_200 else 10_000
        connection.readTimeout = if (isPrivateLan) 4_000 else 20_000
        connection.requestMethod = method
        connection.doInput = true
        val mutableHeaders = LinkedHashMap(headers)
        mutableHeaders[E2eeSessionManager.HEADER_CLIENT_PUB] = E2eeSessionManager.clientPublicKeyBase64

        val activeSession = E2eeSessionManager.session(originKey)
        var wireBody = body
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
                    mutableHeaders["content-type"] = "text/plain; charset=utf-8"
                }
            }
        }

        mutableHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        if (wireBody != null) {
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(wireBody!!.toByteArray(Charsets.UTF_8))
            }
        }
        val status = connection.responseCode
        val updatedSession = E2eeSessionManager.ingestHandshake(
            originKey,
            connection.getHeaderField(E2eeSessionManager.HEADER_SESSION),
            connection.getHeaderField(E2eeSessionManager.HEADER_SERVER_PUB),
        ) ?: activeSession
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val respIsE2ee = connection.getHeaderField(E2eeSessionManager.HEADER_E2EE)
            ?.equals(E2eeSessionManager.VERSION, ignoreCase = true) == true
        if (respIsE2ee && responseBody.isNotEmpty() && updatedSession != null) {
            val decrypted = runCatching {
                E2eeSessionManager.decryptString(updatedSession.aesKey, E2eeSessionManager.AAD_RESP, responseBody)
            }.getOrNull()
            if (decrypted != null) {
                return HttpExchange(status, decrypted)
            }
        }
        return HttpExchange(status, responseBody)
    }
}
