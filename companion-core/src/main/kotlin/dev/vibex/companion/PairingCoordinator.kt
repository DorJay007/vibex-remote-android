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
    fun redeemInvitation(invitation: PairingInvitation, deviceName: String): PairingResult {
        val tried = mutableListOf<String>()
        var lastError: Exception? = null
        for (origin in OriginOrder.probeOrder(invitation.reachability, null)) {
            tried += origin.value
            try {
                val session = client.redeem(origin, invitation.pairingToken, deviceName)
                val hostId = invitation.hostId?.takeIf { it.isNotBlank() }
                    ?: "host:${origin.value}"
                return PairingResult(hostId, session, tried)
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: PairingException("无法到达 Host")
    }
}
