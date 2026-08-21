package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue

fun JsonValue.asObject(): JsonValue.Object? = this as? JsonValue.Object
fun JsonValue.asArray(): JsonValue.Array? = this as? JsonValue.Array
fun JsonValue.asText(): String? = (this as? JsonValue.Text)?.value
fun JsonValue.asNumber(): Double? = (this as? JsonValue.Number)?.value
fun JsonValue.asBool(): Boolean? = (this as? JsonValue.Bool)?.value
fun JsonValue.asLong(): Long? = asNumber()?.toLong()

fun JsonValue.Object.textOrNull(key: String): String? = value[key]?.asText()
fun JsonValue.Object.longOrNull(key: String): Long? = value[key]?.asLong()
fun JsonValue.Object.boolOrNull(key: String): Boolean? = value[key]?.asBool()
fun JsonValue.Object.objOrNull(key: String): JsonValue.Object? = value[key]?.asObject()
fun JsonValue.Object.arrOrNull(key: String): List<JsonValue>? = value[key]?.asArray()?.value

fun JsonValue.Object.textOrEmpty(key: String): String = textOrNull(key).orEmpty()

fun JsonValue.Object.textAny(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key -> textOrNull(key)?.takeIf { it.isNotBlank() } }.orEmpty()
