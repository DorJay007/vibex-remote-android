package dev.vibex.companion

data class PairingResult(
    val hostId: String,
    val session: CompanionSession,
    val tried: List<String>,
)

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
        for (origin in OriginOrder.probeOrder(invitation.reachability, null)) {
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
        throw primaryError ?: lastError ?: PairingException("无法到达 Host")
    }
}
