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
    fun redeem(
        origin: HostOrigin,
        pairingToken: String,
        deviceName: String = "Android Companion",
    ): CompanionSession {
        require(pairingToken.isNotBlank()) { "Pairing token is required" }
        require(deviceName.isNotBlank()) { "Device name is required" }

        val redeemBody = RedeemPairingRequest(
            pairing_token = pairingToken.trim(),
            device_name = deviceName.trim(),
        ).encodeJson()
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
    ): DeviceConnectRequestResult {
        val fields = linkedMapOf<String, JsonValue>(
            "device_name" to JsonValue.Text(deviceName.trim().ifBlank { "Android Companion" }),
            "client_kind" to JsonValue.Text("mobile"),
        )
        publicKeyEd25519?.trim()?.takeIf { it.isNotEmpty() }?.let {
            fields["public_key_ed25519"] = JsonValue.Text(it)
        }
        sessionNonce?.trim()?.takeIf { it.isNotEmpty() }?.let {
            fields["session_nonce"] = JsonValue.Text(it)
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
        const val DEFAULT_OFFICIAL_RELAY_BASE = "https://vibex.xforever.xin"
        const val HAPPY_EYEBALLS_LAN_WINDOW_MS = 250L

        private val rememberedRelayBases = java.util.concurrent.CopyOnWriteArraySet<String>(
            listOf(DEFAULT_OFFICIAL_RELAY_BASE),
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
                val code = connection.responseCode
                if (code !in 200..299) return null
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

        fun discoverLanHosts(
            port: Int = 17891,
            extraRelayOrigins: Collection<String> = emptyList(),
        ): List<DiscoveredLanHost> {
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
            if (localFound.isNotEmpty()) {
                return localFound
            }
            return discoverSameExitPublicHosts(extraRelayOrigins = extraRelayOrigins)
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

        fun discoverSameExitPublicHosts(
            extraRelayOrigins: Collection<String> = emptyList(),
            timeoutMs: Int = 1800,
        ): List<DiscoveredLanHost> {
            val bases = knownRelayBases(extraRelayOrigins)
            if (bases.isEmpty()) return emptyList()
            val results = mutableListOf<DiscoveredLanHost>()
            for (base in bases) {
                runCatching {
                    val cleanBase = base.trimEnd('/')
                    val conn = URI("$cleanBase/v1/discover/same-net").toURL().openConnection() as HttpURLConnection
                    conn.connectTimeout = timeoutMs
                    conn.readTimeout = timeoutMs
                    conn.requestMethod = "GET"
                    if (conn.responseCode !in 200..299) return@runCatching
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = JsonValue.parse(body) as? JsonValue.Object ?: return@runCatching
                    val hosts = (root.value["hosts"] as? JsonValue.Array)?.value.orEmpty()
                    for (item in hosts) {
                        val obj = item as? JsonValue.Object ?: continue
                        val hostId = obj.optionalText("host_id") ?: continue
                        val name = obj.optionalText("name") ?: "VibeX Desktop"
                        val relayPath = obj.optionalText("relay_path") ?: "/t/$hostId"
                        val relayOrigin = "$cleanBase$relayPath"
                        results.add(
                            DiscoveredLanHost(
                                origin = relayOrigin,
                                hostId = hostId,
                                name = name,
                            ),
                        )
                    }
                }
            }
            return results.distinctBy { it.hostId ?: it.origin }
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
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.requestMethod = method
        connection.doInput = true
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
        }
        val status = connection.responseCode
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return HttpExchange(status, responseBody)
    }
}
