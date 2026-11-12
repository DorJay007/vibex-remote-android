package dev.vibex.companion

import java.io.IOException

data class PairingResult(
    val hostId: String,
    val session: CompanionSession,
    val tried: List<String>,
)

/** Every invitation origin is on the computer's LAN and none answered, e.g. the phone is on cellular data. */
class LocalNetworkUnreachableException :
    RuntimeException("电脑未开启云服务。请连接与电脑相同的 Wi-Fi，或在电脑上登录 VibeX 账号")

class PairingCoordinator(
    private val client: PairingClient = PairingClient(),
    private val api: HostApi = HostApi(UrlConnectionTransport()),
) {
    fun redeemInvitation(
        invitation: PairingInvitation,
        deviceName: String,
        replaceExistingMobile: Boolean = false,
    ): PairingResult {
        val tried = mutableListOf<String>()
        var primaryError: Exception? = null
        var lastError: Exception? = null
        var anyHostResponded = false
        val origins = OriginOrder.probeOrder(invitation.reachability, null)
        for (origin in origins) {
            tried += origin.value
            try {
                val session = client.redeem(
                    origin = origin,
                    pairingToken = invitation.pairingToken,
                    deviceName = deviceName,
                    replaceExistingMobile = replaceExistingMobile,
                )
                val hostId = invitation.hostId?.takeIf { it.isNotBlank() }
                    ?: "host:${origin.value}"
                return PairingResult(hostId, session, tried)
            } catch (conflict: MobileDeviceConflictException) {
                throw conflict
            } catch (error: Exception) {
                if (error !is IOException) anyHostResponded = true
                if (
                    primaryError == null &&
                    !origin.value.contains("[fd", ignoreCase = true) &&
                    !origin.value.contains("127.0.0.1") &&
                    !origin.value.contains("localhost", ignoreCase = true)
                ) {
                    primaryError = error
                }
                lastError = error
            }
        }
        if (!anyHostResponded && origins.isNotEmpty() && origins.all { it.isLocalNetwork }) {
            throw LocalNetworkUnreachableException()
        }
        throw primaryError ?: lastError ?: PairingException("无法到达 Host")
    }
}
