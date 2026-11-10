package dev.vibex.companion

/**
 * Device scopes from ADR-0054 (`companion` + `workstation` presets).
 * Host administration (`host.admin`, `device.pair`) is never accepted on mobile.
 */
object CompanionScopes {
    val allowed: Set<String> = setOf(
        "conversation.read",
        "conversation.write",
        "conversation.attach",
        "conversation.permission",
        "conversation.question",
        "conversation.cancel",
        "conversation.steer",
        "application.call",
        "plugin.read",
        "plugin.write",
        "plugin.surface",
        "artifact.read",
        "artifact.preview",
        "workflow.read",
        "workflow.write",
        "workflow.run",
        "workflow.approve",
        "automation.read",
        "automation.write",
        "delegation.read",
        "delegation.cancel",
        "notification.summary",
        "offline.read",
        "workspace.terminal",
    )

    fun extras(scopes: Collection<String>): Set<String> =
        scopes.filterNot { it in allowed }.toSet()
}
