package dev.vibex.companion

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingCoordinatorTest {
    private fun invitation(vararg origins: String) = PairingInvitation(
        hostId = "host-1",
        preset = "companion",
        expiresAt = null,
        pairingId = null,
        pairingToken = "ABCD1234",
        reachability = origins.map { ReachabilityTarget(HostOrigin.parse(it), "lan") },
    )

    private fun coordinator(transport: HttpTransport) =
        PairingCoordinator(PairingClient(transport), HostApi(transport))

    private val offline = HttpTransport { _, url, _, _ -> throw IOException("unreachable $url") }

    @Test
    fun unreachableLanOnlyInvitationAsksForWifiOrCloud() {
        val error = assertFailsWith<LocalNetworkUnreachableException> {
            coordinator(offline).redeemInvitation(invitation("http://192.168.1.5:17891", "http://10.0.0.8:17891"), "Pixel")
        }
        assertEquals("电脑未开启云服务。请连接与电脑相同的 Wi-Fi，或在电脑上登录 VibeX 账号", error.message)
    }

    @Test
    fun unreachableCloudOriginKeepsTheGenericFailure() {
        val error = assertFailsWith<IOException> {
            coordinator(offline).redeemInvitation(
                invitation("http://192.168.1.5:17891", "https://vibex.xforever.xin/t/host-1"),
                "Pixel",
            )
        }
        assertTrue(error.message!!.startsWith("unreachable"))
    }

    @Test
    fun respondingLanHostKeepsItsOwnError() {
        val rejecting = HttpTransport { _, _, _, _ -> HttpExchange(401, "{}") }
        assertFailsWith<PairingException> {
            coordinator(rejecting).redeemInvitation(invitation("http://192.168.1.5:17891"), "Pixel")
        }
    }

    @Test
    fun localNetworkOrigins() {
        val local = listOf(
            "http://192.168.1.5:17891",
            "http://10.1.2.3:17891",
            "http://172.20.0.4:17891",
            "http://127.0.0.1:17891",
            "http://localhost:17891",
            "http://169.254.10.1:17891",
            "http://studio.local:17891",
            "http://[fd12:3456::1]:17891",
            "http://[fe80::1]:17891",
        )
        local.forEach { assertTrue(HostOrigin.parse(it).isLocalNetwork, it) }
        val public = listOf(
            "https://vibex.xforever.xin/t/host-1",
            "http://172.32.0.4:17891",
            "http://100.101.1.2:17891",
            "http://203.0.113.9:17891",
        )
        public.forEach { assertFalse(HostOrigin.parse(it).isLocalNetwork, it) }
    }
}
