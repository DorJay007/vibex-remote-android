package dev.vibex.companion

val STREAMING_ACTIVITY_VERBS = listOf(
    "Cooking",
    "Creating",
    "Driving",
    "Swimming",
    "Thinking",
    "Flying",
    "Researching",
    "Brewing",
    "Crafting",
    "Pondering",
    "Computing",
    "Exploring",
    "Building",
    "Sketching",
    "Mapping",
    "Sculpting",
    "Weaving",
    "Composing",
    "Distilling",
    "Forging",
    "Hatching",
    "Noodling",
    "Percolating",
    "Ruminating",
    "Synthesizing",
)

const val STREAMING_ACTIVITY_INTERVAL_MS = 1400L

fun nextStreamingActivityVerb(
    verbs: List<String> = STREAMING_ACTIVITY_VERBS,
    current: String? = null,
    random: () -> Double = { Math.random() },
): String {
    if (verbs.isEmpty()) return "Thinking"
    val pool = if (current.isNullOrBlank()) verbs else verbs.filter { it != current }
    if (pool.isEmpty()) return current ?: verbs.first()
    val index = (random() * pool.size).toInt().coerceIn(0, pool.lastIndex)
    return pool[index]
}

fun streamingActivityLabel(verb: String): String = "$verb…"
