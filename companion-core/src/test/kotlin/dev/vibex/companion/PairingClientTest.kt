package dev.vibex.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PairingClientTest {
    @Test
    fun connectRequestSendsAccountTokenWhenPresent() {
        val transport = ScriptedTransport(listOf(HttpExchange(201, CONNECT_PENDING)))
        val result = PairingClient(transport).requestConnect(
            HostOrigin.parse("https://relay.example.com/t/host-1"),
            deviceName = "Pixel",
            accountToken = "  account-secret  ",
        )
        assertEquals("req-1", result.requestId)
        assertEquals("4821", result.sasCode)
        val body = transport.requests.single().body!!
        assertTrue(body.contains("\"account_token\":\"account-secret\""))
        assertTrue(body.contains("\"replace_existing_mobile\":false"))
    }

    @Test
    fun connectRequestOmitsAccountTokenWhenAbsent() {
        for (token in listOf(null, "   ")) {
            val transport = ScriptedTransport(listOf(HttpExchange(201, CONNECT_PENDING)))
            PairingClient(transport).requestConnect(HostOrigin.parse("http://192.168.1.5:17891"), accountToken = token)
            assertFalse(transport.requests.single().body!!.contains("account_token"))
        }
    }

    @Test
    fun connectRequestMapsAccountLinkReasons() {
        val cases = mapOf(
            403 to AccountLinkFailure.AccountMismatch,
            401 to AccountLinkFailure.AccountTokenInvalid,
            409 to AccountLinkFailure.HostAccountSignedOut,
            503 to AccountLinkFailure.VerificationUnavailable,
        )
        for ((status, failure) in cases) {
            val transport = ScriptedTransport(listOf(HttpExchange(status, errorEnvelope("""{"reason":"${failure.reason}"}"""))))
            val error = assertFailsWith<AccountLinkException> {
                PairingClient(transport).requestConnect(HostOrigin.parse("http://192.168.1.5:17891"), accountToken = "t")
            }
            assertEquals(failure, error.failure)
        }
    }

    @Test
    fun connectRequestKeepsMobileConflictAndGenericFailures() {
        val conflict = ScriptedTransport(
            listOf(HttpExchange(409, errorEnvelope("""{"reason":"mobile_device_conflict","existing_device_name":"iPhone"}"""))),
        )
        val error = assertFailsWith<MobileDeviceConflictException> {
            PairingClient(conflict).requestConnect(HostOrigin.parse("http://192.168.1.5:17891"), accountToken = "t")
        }
        assertEquals("iPhone", error.existingDeviceName)

        val unknown = ScriptedTransport(listOf(HttpExchange(500, errorEnvelope("""{"reason":"something_else"}"""))))
        assertFailsWith<PairingException> {
            PairingClient(unknown).requestConnect(HostOrigin.parse("http://192.168.1.5:17891"))
        }
        assertEquals(null, AccountLinkFailure.fromReason("mobile_device_conflict"))
    }

    @Test
    fun pollConnectReturnsCredentialOnApproval() {
        val transport = ScriptedTransport(
            listOf(
                HttpExchange(
                    200,
                    """{"request_id":"req-1","host_id":"host-1","sas_code":"4821","status":"approved","expires_at":"2026-11-12T00:00:00Z","credential":{"device_id":"11111111-1111-1111-1111-111111111111","access_token":"device-token","scopes":["conversation.read"]}}""",
                ),
            ),
        )
        val result = PairingClient(transport).pollConnect(HostOrigin.parse("http://192.168.1.5:17891"), "req-1")
        assertEquals("approved", result.status)
        assertEquals("device-token", result.credential?.access_token)
        assertTrue(transport.requests.single().url.endsWith("/api/v1/auth/connect-requests/req-1"))
    }

    @Test
    fun redeemStoresCompanionCredentialAndNegotiatesV1() {
        val transport = ScriptedTransport(
            listOf(
                HttpExchange(
                    201,
                    """{"device_id":"11111111-1111-1111-1111-111111111111","access_token":"device-token","scopes":["conversation.read","conversation.write","offline.read"]}""",
                ),
                HttpExchange(
                    200,
                    """{"server_version":"0.1.3","protocol_version":"1.0","minimum_client_version":"1.0","capabilities":["conversation.attach"]}""",
                ),
            ),
        )
        val session = PairingClient(transport).redeem(
            HostOrigin.parse("http://127.0.0.1:3080"),
            "one-time-secret",
            "Pixel",
        )
        assertEquals("device-token", session.credential.access_token)
        assertEquals("1.0", session.capabilities.protocol_version)
        assertEquals(2, transport.requests.size)
        assertEquals("POST", transport.requests[0].method)
        assertTrue(transport.requests[0].url.endsWith("/api/v1/auth/pairings/redeem"))
        assertTrue(transport.requests[0].body!!.contains("\"pairing_token\":\"one-time-secret\""))
        assertEquals("GET", transport.requests[1].method)
        assertEquals("Bearer device-token", transport.requests[1].headers["authorization"])
    }

    @Test
    fun redeemRejectsHostAdminScopes() {
        val transport = ScriptedTransport(
            listOf(
                HttpExchange(
                    201,
                    """{"device_id":"11111111-1111-1111-1111-111111111111","access_token":"device-token","scopes":["conversation.read","host.admin"]}""",
                ),
            ),
        )
        val error = assertFailsWith<PairingException> {
            PairingClient(transport).redeem(
                HostOrigin.parse("https://host.example"),
                "token",
            )
        }
        assertTrue(error.message!!.contains("host.admin"))
    }

    @Test
    fun requestConnectReportsAnOfflineDesktopBehindTheRelay() {
 for (status in listOf(502, 503, 504)) {
     val transport = ScriptedTransport(listOf(HttpExchange(status, "")))
     assertFailsWith<HostUnreachableException> {
  PairingClient(transport).requestConnect(
      HostOrigin.parse("https://vibex.xforever.xin/t/host-1"),
      accountToken = "acct-token",
         )
     }
 }
    }

    @Test
    fun hostOriginRejectsCredentialsInUrl() {
        assertFailsWith<IllegalArgumentException> {
            HostOrigin.parse("http://user:secret@127.0.0.1:3080")
        }
    }

    @Test
    fun e2eeHandshakeAndAesGcmRoundTripSucceeds() {
        val origin = "https://relay.example.com/t/host-e2ee-android"
        val session = E2eeSessionManager.ingestHandshake(
            origin,
            "sess-android-01",
            E2eeSessionManager.clientPublicKeyBase64,
        )
        assertTrue(session != null)
        assertTrue(E2eeSessionManager.isE2eeActive(origin))

        val plain = """{"operation_id":"op-1","args":{"text":"hello e2ee"}}"""
        val cipherB64 = E2eeSessionManager.encryptString(session.aesKey, E2eeSessionManager.AAD_REQ, plain)
        val decrypted = E2eeSessionManager.decryptString(session.aesKey, E2eeSessionManager.AAD_REQ, cipherB64)
        assertEquals(plain, decrypted)
    }
}

private data class RecordedRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

private class ScriptedTransport(
    private val responses: List<HttpExchange>,
) : HttpTransport {
    val requests = mutableListOf<RecordedRequest>()
    private var index = 0

    override fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): HttpExchange {
        requests += RecordedRequest(method, url, headers, body)
        return responses[index++]
    }
}

private const val CONNECT_PENDING =
    """{"request_id":"req-1","host_id":"host-1","host_name":"Studio","sas_code":"4821","status":"pending","expires_at":"2026-11-12T00:00:00Z"}"""

private fun errorEnvelope(details: String): String =
    """{"code":"forbidden","message":"refused","retryable":false,"operation_id":"op-1","details":$details}"""
