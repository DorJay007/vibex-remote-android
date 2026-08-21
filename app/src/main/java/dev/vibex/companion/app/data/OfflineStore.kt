package dev.vibex.companion.app.data

import android.content.Context
import dev.vibex.companion.asObject
import dev.vibex.companion.asArray
import dev.vibex.companion.asNumber
import dev.vibex.companion.asText
import dev.vibex.companion.encodeObject
import dev.vibex.remote.v1.JsonValue
import dev.vibex.remote.v1.RemoteEvent
import java.io.File

class OfflineStore(context: Context) {
    private val dir = File(context.filesDir, "offline").also { it.mkdirs() }

    fun load(conversationId: String): Pair<Long, List<RemoteEvent>> {
        val file = File(dir, "$conversationId.json")
        if (!file.exists()) return 0L to emptyList()
        val root = JsonValue.parse(file.readText()) as? JsonValue.Object ?: return 0L to emptyList()
        val seq = root.value["through"]?.asNumber()?.toLong() ?: 0L
        val events = root.value["events"]?.asArray()?.value.orEmpty().mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            RemoteEvent(
                sequence = obj.value["sequence"]?.asNumber()?.toLong() ?: return@mapNotNull null,
                kind = obj.value["kind"]?.asText() ?: return@mapNotNull null,
                payload = obj.value["payload"] ?: JsonValue.Null,
            )
        }
        return seq to events
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun save(conversationId: String, through: Long, events: List<RemoteEvent>) {
        val file = File(dir, "$conversationId.json")
        file.writeText(
            encodeObject(
                mapOf(
                    "through" to through,
                    "events" to events.map {
                        mapOf(
                            "sequence" to it.sequence,
                            "kind" to it.kind,
                            "payload" to it.payload,
                        )
                    },
                ),
            ),
        )
    }
}
