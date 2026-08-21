package dev.vibex.companion.app.data

import dev.vibex.companion.asObject
import dev.vibex.remote.v1.JsonValue

enum class ThinkingDisplay(val id: String) {
    All("all"),
    Collapsed("collapsed"),
    Hidden("hidden"),
    ;

    companion object {
        fun from(id: String?): ThinkingDisplay = entries.firstOrNull { it.id == id } ?: Hidden
    }
}

enum class CollapseDisplay(val id: String) {
    Expanded("expanded"),
    Collapsed("collapsed"),
    ;

    companion object {
        fun from(id: String?): CollapseDisplay = entries.firstOrNull { it.id == id } ?: Collapsed
    }
}

enum class MessageStreamDisplay(val id: String) {
    All("all"),
    Collapsed("collapsed"),
    ;

    companion object {
        fun from(id: String?): MessageStreamDisplay = entries.firstOrNull { it.id == id } ?: Collapsed
    }
}

data class StreamDisplayPrefs(
    val thinking: ThinkingDisplay = ThinkingDisplay.Hidden,
    val showFailedTools: Boolean = false,
    val failedTools: CollapseDisplay = CollapseDisplay.Collapsed,
    val tools: CollapseDisplay = CollapseDisplay.Collapsed,
    val messages: MessageStreamDisplay = MessageStreamDisplay.Collapsed,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "thinking" to thinking.id,
        "showFailedTools" to showFailedTools,
        "failedTools" to failedTools.id,
        "tools" to tools.id,
        "messages" to messages.id,
    )

    companion object {
        fun from(value: JsonValue?): StreamDisplayPrefs {
            val obj = value?.asObject() ?: return StreamDisplayPrefs()
            return StreamDisplayPrefs(
                thinking = ThinkingDisplay.from(obj.value["thinking"]?.let { (it as? JsonValue.Text)?.value }),
                showFailedTools = obj.value["showFailedTools"]?.let { (it as? JsonValue.Bool)?.value } ?: false,
                failedTools = CollapseDisplay.from(obj.value["failedTools"]?.let { (it as? JsonValue.Text)?.value }),
                tools = CollapseDisplay.from(obj.value["tools"]?.let { (it as? JsonValue.Text)?.value }),
                messages = MessageStreamDisplay.from(obj.value["messages"]?.let { (it as? JsonValue.Text)?.value }),
            )
        }
    }
}
