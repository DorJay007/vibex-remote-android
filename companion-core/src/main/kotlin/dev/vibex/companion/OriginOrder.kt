package dev.vibex.companion

object OriginOrder {
    fun probeOrder(targets: List<ReachabilityTarget>, lastSuccess: String?): List<HostOrigin> {
        val unique = LinkedHashMap<String, HostOrigin>()
        targets.forEach { unique[it.origin.value] = it.origin }
        lastSuccess?.let { success ->
            runCatching { HostOrigin.parse(success) }.getOrNull()?.let { unique[it.value] = it }
        }
        // Synthesize 127.0.0.1:<port> fallback for USB `adb reverse` debugging
        targets.firstOrNull()?.origin?.value?.let { firstUrl ->
            val portMatch = Regex(""":(\d+)$""").find(firstUrl)
            if (portMatch != null) {
                val loop = "http://127.0.0.1:${portMatch.groupValues[1]}"
                runCatching { HostOrigin.parse(loop) }.getOrNull()?.let {
                    if (!unique.containsKey(it.value)) unique[it.value] = it
                }
            }
        }
        val last = lastSuccess?.let { runCatching { HostOrigin.parse(it) }.getOrNull() }
        val sorted = unique.values.sortedBy { originPriority(it.value, last?.value) }
        return sorted.distinctBy { it.value }
    }

    private fun originPriority(url: String, lastSuccess: String?): Int {
        if (lastSuccess != null && url == lastSuccess) return 0
        val lower = url.lowercase()
        // Deprioritize IPv6 ULA (fd00::/8) and link-local (fe80::/10) which often fail across LAN/USB
        if (lower.contains("://[fd") || lower.contains("://[fe80")) return 50
        // Prioritize IPv4 private LAN addresses (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
        if (Regex("""^https?://(192\.168\.|10\.|172\.(1[6-9]|2\d|3[0-1])\.)""").containsMatchIn(lower)) return 10
        // Next try 127.0.0.1 (works immediately when `adb reverse` is active)
        if (lower.contains("://127.0.0.1") || lower.contains("://localhost")) return 20
        if (lower.startsWith("https://")) return 30
        return 40
    }
}
