package dev.vibex.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PairingInvitationTest {
    @Test
    fun parsesVibexPairingPayload() {
        val invitation = PairingInvitationParser.parse(
            """vibex-pairing:{"host_id":"h1","preset":"companion","pairing_token":"vbx_pair_abc","reachability":[{"origin":"http://192.168.1.8:3080","kind":"lan"},{"origin":"https://box.ts.net","kind":"tailscale"}]}""",
        )
        assertEquals("h1", invitation.hostId)
        assertEquals(2, invitation.reachability.size)
        assertEquals("https://box.ts.net", invitation.reachability[1].origin.value)
    }

    @Test
    fun dropsLoopbackOrigins() {
        val invitation = PairingInvitationParser.parse(
            """{"pairing_token":"tok","reachability":[{"origin":"http://127.0.0.1:3080","kind":"lan"},{"origin":"http://10.0.0.2:3080","kind":"lan"}]}""",
        )
        assertEquals(listOf("http://10.0.0.2:3080"), invitation.reachability.map { it.origin.value })
    }

    @Test
    fun parsesCurrentHostUrlsPayload() {
        val invitation = PairingInvitationParser.parse(
            """vibex-pairing:{"version":1,"pairing_token":"vbx_pair_x","preset":"companion","host_urls":["http://127.0.0.1:3080","http://192.168.1.20:3080"]}""",
        )
        assertEquals(listOf("http://192.168.1.20:3080"), invitation.reachability.map { it.origin.value })
    }

    @Test
    fun rejectsNonInvitation() {
        assertFailsWith<PairingException> { PairingInvitationParser.parse("http://192.168.1.1:3080") }
    }

    @Test
    fun manualRejectsDeviceToken() {
        assertFailsWith<PairingException> {
            PairingInvitationParser.parseManual("http://10.0.0.2:3080", "vbx_device_secret")
        }
    }

    @Test
    fun originOrderPrefersLastSuccessThenHttps() {
        val targets = listOf(
            ReachabilityTarget(HostOrigin.parse("http://10.0.0.2:3080"), "lan"),
            ReachabilityTarget(HostOrigin.parse("https://box.ts.net"), "tailscale"),
        )
        val order = OriginOrder.probeOrder(targets, "http://10.0.0.2:3080").map { it.value }
        assertEquals("http://10.0.0.2:3080", order.first())
        assertTrue(order.contains("https://box.ts.net"))
    }
}
