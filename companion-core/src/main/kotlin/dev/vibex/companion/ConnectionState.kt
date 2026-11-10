package dev.vibex.companion

enum class ConnectionState {
    Connecting,
    Online,
    Recovering,
    HostSleeping,
    Offline,
    AuthRequired,
    Incompatible,
    ;

    val allowsWrites: Boolean get() = this == Online
}
