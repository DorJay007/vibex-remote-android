package dev.vibex.companion

import dev.vibex.remote.v1.DeviceCredential
import dev.vibex.remote.v1.JsonValue
import dev.vibex.remote.v1.RedeemPairingRequest
import dev.vibex.remote.v1.ServerCapabilities

internal fun RedeemPairingRequest.encodeJson(): String =
    JsonValue.Object(
        mapOf(
            "pairing_token" to JsonValue.Text(pairing_token),
            "device_name" to JsonValue.Text(device_name),
        ),
    ).encode()

internal fun JsonValue.Object.text(key: String): String =
    (value[key] as? JsonValue.Text)?.value
        ?: error("missing string field $key")

internal fun JsonValue.Object.strings(key: String): List<String> {
    val array = value[key] as? JsonValue.Array ?: error("missing array field $key")
    return array.value.map { item ->
        (item as? JsonValue.Text)?.value ?: error("expected string in $key")
    }
}

internal fun parseServerCapabilities(body: String): ServerCapabilities {
    val root = JsonValue.parse(body) as JsonValue.Object
    return ServerCapabilities(
        server_version = root.text("server_version"),
        protocol_version = root.text("protocol_version"),
        minimum_client_version = root.text("minimum_client_version"),
        capabilities = root.strings("capabilities"),
    )
}

internal fun parseServerReachability(body: String): List<ReachabilityTarget> {
    val root = JsonValue.parse(body) as? JsonValue.Object ?: return emptyList()
    val array = root.value["reachability"] as? JsonValue.Array ?: return emptyList()
    return array.value.mapNotNull { item ->
        val obj = item as? JsonValue.Object ?: return@mapNotNull null
        val originStr = obj.optionalText("origin") ?: return@mapNotNull null
        val kind = obj.optionalText("kind") ?: "published"
        val origin = runCatching { HostOrigin.parse(originStr) }.getOrNull() ?: return@mapNotNull null
        ReachabilityTarget(origin, kind)
    }
}

internal fun parseDeviceCredential(body: String): DeviceCredential {
    val root = JsonValue.parse(body) as JsonValue.Object
    return parseDeviceCredentialObject(root)
}

internal fun parseDeviceCredentialObject(root: JsonValue.Object): DeviceCredential =
    DeviceCredential(
        device_id = root.text("device_id"),
        access_token = root.text("access_token"),
        scopes = root.strings("scopes"),
    )

internal fun JsonValue.Object.optionalText(key: String): String? =
    (value[key] as? JsonValue.Text)?.value?.takeIf { it.isNotBlank() }

internal fun protocolMajor(version: String): String = version.substringBefore('.')
