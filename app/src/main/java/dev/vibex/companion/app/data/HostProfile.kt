package dev.vibex.companion.app.data

data class StoredReachability(
    val origin: String,
    val kind: String,
)

data class HostProfile(
    val hostId: String,
    val name: String,
    val deviceId: String,
    val reachability: List<StoredReachability>,
    val lastSuccessOrigin: String?,
    val lastSyncAt: Long?,
    val selectedProjectId: String? = null,
)
