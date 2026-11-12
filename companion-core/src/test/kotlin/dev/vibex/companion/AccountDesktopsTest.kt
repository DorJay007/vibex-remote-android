package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountDesktopsTest {
    @Test
    fun parsesLocalDesktopsFromHostsAndRecords() {
        val root = JsonValue.parse(
            """
            {
                "hosts": [
                    {"hostId":"host:alpha","hostName":"Alpha","kind":"local","osType":"macos","osArch":"arm64","agents":[],"updatedAt":"2026-11-10T08:00:00Z"},
                    {"hostId":"host:cloud-box","hostName":"Cloud","kind":"remote","osType":"linux","osArch":"x86_64","agents":[],"updatedAt":"2026-11-12T08:00:00Z"},
                    {"hostId":"host:ssh-box","hostName":"SSH","kind":"ssh","osType":"linux","osArch":"x86_64","agents":[],"updatedAt":"2026-11-12T08:00:00Z"}
                ],
                "records": [
                    {"hostId":"host:alpha","hostName":"Alpha Record","kind":"local","osType":"macos","osArch":"arm64","lastSeenAt":"2026-11-11T09:00:00Z","configSynced":true},
                    {"hostId":"beta","hostName":"Beta","kind":"local","osType":"windows","osArch":"x86_64","lastSeenAt":"2026-11-12T07:00:00Z","configSynced":false},
                    {"hostId":"host:web","hostName":"Web","kind":"web","osType":"","osArch":"","lastSeenAt":"2026-11-12T09:00:00Z","configSynced":false},
                    {"hostId":"host:","hostName":"Empty","kind":"local","lastSeenAt":"2026-11-12T10:00:00Z"}
                ]
            }
            """.trimIndent(),
        ) as JsonValue.Object

        val desktops = AccountDesktops.parse(root)

        assertEquals(listOf("beta", "alpha"), desktops.map { it.hostId })
        val alpha = desktops.last()
        assertEquals("Alpha", alpha.hostName)
        assertEquals("2026-11-11T09:00:00Z", alpha.lastSeenAt)
        assertEquals("macOS · arm64", alpha.platformBadge)
        assertEquals("Windows · x86_64", desktops.first().platformBadge)
    }

    @Test
    fun skipsDeviceLoginsWithoutAHost() {
        val root = JsonValue.parse(
     """
     {"records": [
  {"hostId":"session:42","hostName":"iPhone","kind":"local","lastSeenAt":"2026-11-12T11:00:00Z","configSynced":false},
         {"hostId":"host:mac","hostName":"Mac","kind":"local","lastSeenAt":"2026-11-12T10:00:00Z","configSynced":true}
     ]}
     """.trimIndent(),
        ) as JsonValue.Object
 assertEquals(listOf("mac"), AccountDesktops.parse(root).map { it.hostId })
    }

    @Test
    fun parseToleratesMissingLists() {
        val empty = JsonValue.Object(linkedMapOf())
        assertEquals(emptyList(), AccountDesktops.parse(empty))
    }

    @Test
    fun tenantOriginUsesAssignedRelayOrDefault() {
        val fallback = "https://vibex.xforever.xin"
        assertEquals(
            "https://vibex.xforever.xin/t/alpha",
            AccountDesktops.tenantOrigin(AccountDesktops.relayBase(null, fallback), "host:alpha"),
        )
        assertEquals(
            "https://relay.cn.example/t/alpha",
            AccountDesktops.tenantOrigin(
                AccountDesktops.relayBase(AssignedRelayServer(publicOrigin = "https://relay.cn.example/"), fallback),
                "alpha",
            ),
        )
        assertEquals(
            "https://relay.cn.example:8443",
            AccountDesktops.relayBase(AssignedRelayServer(relayHost = "relay.cn.example", relayPort = 8443), fallback),
        )
        assertEquals(
            "https://relay.cn.example",
            AccountDesktops.relayBase(AssignedRelayServer(relayHost = "relay.cn.example", relayPort = 443), fallback),
        )
    }
}
