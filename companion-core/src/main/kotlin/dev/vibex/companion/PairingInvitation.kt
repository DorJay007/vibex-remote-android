package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue

data class ReachabilityTarget(
    val origin: HostOrigin,
    val kind: String,
)

data class PairingInvitation(
    val hostId: String?,
    val preset: String?,
    val expiresAt: String?,
    val pairingId: String?,
    val pairingToken: String,
    val reachability: List<ReachabilityTarget>,
) {
    fun origins(): List<HostOrigin> = OriginOrder.probeOrder(reachability, null).ifEmpty {
        error("Invitation has no reachable origin")
    }
}

object PairingInvitationParser {
    fun parse(raw: String): PairingInvitation {
        val trimmed = raw.trim()
        val payload = when {
            trimmed.startsWith("vibex-pairing:") -> trimmed.removePrefix("vibex-pairing:")
            trimmed.startsWith("{") -> trimmed
            else -> throw PairingException("这不是 VibeX 邀请")
        }
        val root = runCatching { JsonValue.parse(payload) as JsonValue.Object }
            .getOrElse { throw PairingException("这不是 VibeX 邀请") }
        val token = root.textOrNull("pairing_token")
            ?: throw PairingException("邀请缺少 pairing token")
        if (token.startsWith("vbx_device_") || token.length > 400 && !token.startsWith("vbx_pair_")) {
            // still allow official pairing tokens that do not use the prefix
        }
        val reachability = parseReachability(root).ifEmpty { parseHostUrls(root) }
        return PairingInvitation(
            hostId = root.textOrNull("host_id"),
            preset = root.textOrNull("preset"),
            expiresAt = root.textOrNull("expires_at"),
            pairingId = root.textOrNull("pairing_id"),
            pairingToken = token,
            reachability = reachability,
        )
    }

    fun parseManual(origin: String, pairingToken: String): PairingInvitation {
        val raw = pairingToken.trim()
        if (raw.isEmpty()) throw PairingException("需要连接码")
        if (raw.startsWith("vibex-pairing:") || raw.startsWith("{")) {
            return parse(raw)
        }
        if (raw.startsWith("vbx_device_", ignoreCase = true)) {
            throw PairingException("不要粘贴管理员或设备长期口令，请使用电脑上的邀请")
        }
        val token = raw.uppercase()
        return PairingInvitation(
            hostId = null,
            preset = "companion",
            expiresAt = null,
            pairingId = null,
            pairingToken = token,
            reachability = listOf(ReachabilityTarget(HostOrigin.parse(origin), "manual")),
        )
    }

    private fun parseReachability(root: JsonValue.Object): List<ReachabilityTarget> {
        val items = root.arrOrNull("reachability").orEmpty()
        val parsed = items.mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            val originRaw = obj.textOrNull("origin") ?: return@mapNotNull null
            val origin = runCatching { HostOrigin.parse(originRaw) }.getOrNull()
                ?: return@mapNotNull null
            if (origin.value.contains("127.0.0.1") || origin.value.contains("localhost")) {
                return@mapNotNull null
            }
            ReachabilityTarget(origin, obj.textOrNull("kind") ?: "unknown")
        }
        return parsed
    }

    private fun parseHostUrls(root: JsonValue.Object): List<ReachabilityTarget> {
        return root.arrOrNull("host_urls").orEmpty().mapNotNull { item ->
            val raw = item.asText() ?: return@mapNotNull null
            val origin = runCatching { HostOrigin.parse(raw) }.getOrNull() ?: return@mapNotNull null
            if (origin.value.contains("127.0.0.1") || origin.value.contains("localhost")) {
                return@mapNotNull null
            }
            ReachabilityTarget(origin, "lan")
        }
    }
}
