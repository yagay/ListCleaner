package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/**
 * Per-domain Deep Link rules. The serialized field name is kept for backup compatibility.
 * Generic DEEP_LINK rules remain the fallback and are inherited by every configured host.
 */
@Serializable
data class BrowserLinkConfig(
    val hosts: Set<String> = emptySet(),
    val rules: Map<String, Set<String>> = emptyMap(),
    val priorities: Map<String, List<String>> = emptyMap()
) {
    fun validated(): BrowserLinkConfig {
        require(hosts.size <= MAX_HOSTS) { "too_many_browser_hosts" }
        val cleanHosts = hosts.map { requireNotNull(normalizeBrowserHost(it)) { "invalid_browser_host" } }.toSet()
        require(cleanHosts.size == hosts.size) { "duplicate_browser_host" }

        val cleanRules = rules.map { (rawHost, ids) ->
            val host = requireNotNull(normalizeBrowserHost(rawHost)) { "invalid_browser_rule_host" }
            require(host in cleanHosts) { "browser_rule_host_not_configured" }
            val migrated = ids.map { id ->
                val parsed = requireNotNull(ComponentRule.fromId(id)) { "invalid_browser_host_rules" }
                require(parsed.id == id && parsed.kind in setOf(IntentKind.BROWSER, IntentKind.DEEP_LINK)) {
                    "invalid_browser_host_rules"
                }
                if (parsed.kind == IntentKind.BROWSER) parsed.copy(kind = IntentKind.DEEP_LINK).id else parsed.id
            }.toSet()
            require(migrated.size <= 2_000) { "invalid_browser_host_rules" }
            host to migrated
        }.toMap()

        val cleanPriorities = priorities.map { (rawHost, packages) ->
            val host = requireNotNull(normalizeBrowserHost(rawHost)) { "invalid_browser_priority_host" }
            require(host in cleanHosts) { "browser_priority_host_not_configured" }
            require(packages.size <= 200 && packages.distinct().size == packages.size &&
                packages.all(PackageIdentity::valid)) { "invalid_browser_host_priorities" }
            host to packages.toList()
        }.toMap()

        return copy(hosts = cleanHosts, rules = cleanRules, priorities = cleanPriorities)
    }

    fun selectedRules(host: String): Set<ComponentRule> {
        val normalized = normalizeBrowserHost(host) ?: return emptySet()
        return rules[normalized].orEmpty().mapNotNull(ComponentRule::fromId).toSet()
    }

    fun priority(host: String): List<String> =
        normalizeBrowserHost(host)?.let { priorities[it].orEmpty() }.orEmpty()

    companion object {
        const val MAX_HOSTS = 64
    }
}

/** Accept a host or pasted http(s) URL and reduce it to a stable lower-case host. */
fun normalizeBrowserHost(value: String?): String? {
    var host = value?.trim()?.lowercase().orEmpty()
    if (host.isEmpty()) return null
    host = host.removePrefix("https://").removePrefix("http://")
        .substringBefore('/').substringBefore('?').substringBefore('#')
        .substringBefore(':').trimEnd('.')
    if (host.length !in 1..253 || host.startsWith('.') || host.endsWith('.') || ".." in host) return null
    val labels = host.split('.')
    if (labels.any { label ->
            label.isEmpty() || label.length > 63 || label.startsWith('-') || label.endsWith('-') ||
                label.any { ch -> !(ch.isLetterOrDigit() || ch == '-') }
        }) return null
    return host
}

fun ComponentCandidate.matchesBrowserHost(host: String): Boolean {
    val normalized = normalizeBrowserHost(host) ?: return false
    return rule.kind == IntentKind.DEEP_LINK && normalized in browserHosts
}
