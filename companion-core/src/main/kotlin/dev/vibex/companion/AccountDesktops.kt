package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue
import java.util.Locale

/** A desktop VibeX install signed in to the user's account, keyed by its raw host id. */
data class AccountDesktop(
    val hostId: String,
    val hostName: String,
    val osType: String = "",
    val osArch: String = "",
    val lastSeenAt: String = "",
) {
    val platformBadge: String?
        get() {
            val osLabel = when (osType.trim().lowercase(Locale.ROOT)) {
                "macos", "darwin" -> "macOS"
                "windows" -> "Windows"
                "linux" -> "Linux"
                else -> osType.trim()
            }
            val arch = osArch.trim()
            return when {
                osLabel.isNotEmpty() && arch.isNotEmpty() -> "$osLabel · $arch"
                osLabel.isNotEmpty() -> osLabel
                arch.isNotEmpty() -> arch
                else -> null
            }
        }
}

/** The relay server the account site assigned to this user, if any. */
data class AssignedRelayServer(
    val publicOrigin: String = "",
    val relayHost: String = "",
    val relayPort: Int = 0,
)

object AccountDesktops {
    private const val HOST_ID_PREFIX = "host:"
    private const val DESKTOP_KIND = "local"

    fun rawHostId(hostId: String): String = hostId.trim().removePrefix(HOST_ID_PREFIX)

    /**
     * Desktops of the account from `/api/account/desktop/host-configs`: `hosts ∪ records`,
     * deduplicated by host id, `kind == "local"` only, most recently seen first.
     */
    fun parse(root: JsonValue.Object): List<AccountDesktop> {
        val byHostId = LinkedHashMap<String, AccountDesktop>()
        val entries = root.arrOrNull("hosts").orEmpty() + root.arrOrNull("records").orEmpty()
        for (entry in entries) {
            val obj = entry.asObject()?.let { it.objOrNull("snapshot") ?: it } ?: continue
            val kind = obj.textOrNull("kind")?.trim().orEmpty().ifEmpty { DESKTOP_KIND }
            if (kind != DESKTOP_KIND) continue
            val hostId = rawHostId(obj.textOrEmpty("hostId"))
     // `session:<id>` is the site's placeholder for a device login without a host
     // (phones included); there is no computer behind it to connect to.
     if (hostId.isEmpty() || hostId.startsWith("session:")) continue
            val seenAt = obj.textAny("lastSeenAt", "updatedAt").trim()
            val existing = byHostId[hostId]
            byHostId[hostId] = AccountDesktop(
                hostId = hostId,
                hostName = existing?.hostName
                    ?: obj.textOrEmpty("hostName").trim().ifEmpty { "VibeX 电脑" },
                osType = existing?.osType?.ifEmpty { null } ?: obj.textOrEmpty("osType").trim(),
                osArch = existing?.osArch?.ifEmpty { null } ?: obj.textOrEmpty("osArch").trim(),
                lastSeenAt = maxOf(existing?.lastSeenAt.orEmpty(), seenAt),
            )
        }
        return byHostId.values.sortedByDescending { it.lastSeenAt }
    }

    /** Base URL of the relay that fronts the account's desktops. */
    fun relayBase(server: AssignedRelayServer?, defaultBase: String): String {
        val publicOrigin = server?.publicOrigin?.trim()?.trimEnd('/').orEmpty()
        if (publicOrigin.isNotEmpty()) {
            return if (publicOrigin.contains("://")) publicOrigin else "https://$publicOrigin"
        }
        val host = server?.relayHost?.trim().orEmpty()
        if (host.isNotEmpty()) {
            val port = server?.relayPort ?: 0
            return if (port > 0 && port != 443) "https://$host:$port" else "https://$host"
        }
        return defaultBase.trim().trimEnd('/')
    }

    fun tenantOrigin(relayBase: String, hostId: String): String =
        "${relayBase.trimEnd('/')}/t/${rawHostId(hostId)}"
}
