package dev.vibex.companion

object OriginOrder {
    fun probeOrder(targets: List<ReachabilityTarget>, lastSuccess: String?): List<HostOrigin> {
        val unique = LinkedHashMap<String, HostOrigin>()
        targets.forEach { unique[it.origin.value] = it.origin }
        lastSuccess?.let { success ->
            runCatching { HostOrigin.parse(success) }.getOrNull()?.let { unique[it.value] = it }
        }
        val https = unique.values.filter { it.value.startsWith("https://") }
        val rest = unique.values.filterNot { it.value.startsWith("https://") }
        val last = lastSuccess?.let { runCatching { HostOrigin.parse(it) }.getOrNull() }
        val ordered = buildList {
            if (last != null) add(last)
            addAll(https.filter { it.value != last?.value })
            addAll(rest.filter { it.value != last?.value })
        }
        return ordered.distinctBy { it.value }
    }
}
